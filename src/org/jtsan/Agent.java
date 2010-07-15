/* Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jtsan;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.util.*;

import java.lang.instrument.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * Instruments all method bodies to intercept events: method entry, method exit, memory accesses,
 * synchronization/threading events.
 *
 * @author Egor Pasko
 */
public class Agent implements ClassFileTransformer {

  private static final String LOGFILE_PREFIX = "logfile=";

  private static final String DEBUG_CLASS_PREFIX = "cls=";

  // Option that enables retranslation of system classes.
  private static final String ENABLE_SYS_PREFIX = "sys=";

  // Ignore list to eliminate endless recursion.
  private static String[] ignore = new String[] {
    "RaceDetectorApi",
    "org/jtsan",
    "sun/",

    // Classes required by EventListener itself. Triggering events in these will
    // cause endless recursion.
    "java/io/PrintWriter",
    "java/lang/AbstractStringBuilder",
    "java/lang/Boolean",
    "java/lang/Class",
    "java/lang/Long",
    "java/lang/String",
    "java/lang/StringBuilder",
    "java/lang/System",

    // Exclusions to workaround HotSpot internal failures.
    "java/io/",
    "java/lang/Math",
    "java/lang/Thread",
    "java/lang/ref/",
    "java/lang/reflect/",
    "java/nio/",
    "java/util/Arrays",
  };

  // A list of exceptions for the ignore list.
  private static String[] noignore = new String[] { };

  // System methods to intercept.
  private static MethodMapping syncMethods = null;

  private final CodePos codePos = new CodePos();

  private final Set<String> volatileFields = new HashSet<String>();

  private String debugClassPrefix;

  public static void premain(String arg, Instrumentation instrumentation) {
    Agent agent = new Agent();
    syncMethods = new MethodMapping();
    Interceptors.init(syncMethods);

    // Parse Agent arguments.
    String fname = "jtsan.log";
    boolean retransformSystem = false;
    if (arg != null) {
      String[] args = arg.split(":");
      for (int i = 0; i < args.length; i++) {
        int idx = args[i].lastIndexOf(LOGFILE_PREFIX);
        if (idx != -1) {
          fname = args[i].substring(idx + LOGFILE_PREFIX.length());
        }
        idx = args[i].lastIndexOf(DEBUG_CLASS_PREFIX);
        if (idx != -1) {
          agent.debugClassPrefix = args[i].substring(idx + DEBUG_CLASS_PREFIX.length());
        }
        idx = args[i].lastIndexOf(ENABLE_SYS_PREFIX);
        if (idx != -1) {
          retransformSystem = "1".equals(args[i].substring(idx + ENABLE_SYS_PREFIX.length()));
        }
      }
    }

    // Initialize output stream for interceptors.
    try {
      if (fname.equals("-")) {
        EventListener.setPrinter(new PrintWriter(System.out, true /* auto flush */));
      } else {
        EventListener.setPrinter(new PrintWriter(
            new FileWriter(fname, false /* append */), true /* auto flush */));
      }
      System.err.println("Java Agent: appending threading events to file: " + fname);
    } catch (IOException e) {
      System.err.println("Exception while opening file: " + fname + ", reason: " + e);
      System.exit(5);
    }

    // Enable the class transformation.
    EventListener.threadsInit();
    instrumentation.addTransformer(agent, true);

    // Retransform most of the currently loaded system classes.
    if (retransformSystem) {
      for (Class c : instrumentation.getAllLoadedClasses()) {
        if (!c.isInterface() && instrumentation.isModifiableClass(c)) {
          String cs = c.toString();
          try {
            instrumentation.retransformClasses(c);
          } catch (UnmodifiableClassException e) {
            System.err.println("Cannot retransform class. Exception: " + e);
            System.exit(1);
          }
        }
      }
    }
  }

  private boolean inIgnoreList(String className) {
    for (int i = 0; i < ignore.length; i++) {
      if (className.startsWith(ignore[i])) {
        for (int j = 0; j < noignore.length; j++) {
          if (className.startsWith(noignore[j])) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public byte[] transform(ClassLoader loader, String className,
      Class clazz, java.security.ProtectionDomain domain,
      byte[] bytes) {
    try {
      if (inIgnoreList(className)) {
        return bytes;
      }

      ClassAdapter ca;
      ClassReader cr = new ClassReader(bytes);
      ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

      byte[] res;
      // Allow no more than a single instrumentation at a time to making code
      // positions sequential and non-conflicting.
      synchronized(this) {
        ca = newMethodTransformAdapter(this, cw, className, codePos, volatileFields);
        cr.accept(ca, ClassReader.SKIP_FRAMES);
        res = cw.toByteArray();
        if (debugClassPrefix != null && className.startsWith(debugClassPrefix)) {
          printTransformedClass(res);
        }
      }
      return res;
    } catch (Exception e) {
      System.out.println(
          "Exception occured during transformation of class " + className +
          ". Transformed bytes are discarded.");
      e.printStackTrace();
      return bytes;
    }
  }

  private ClassAdapter newMethodTransformAdapter(final Agent myself,
                                                 ClassWriter cw,
                                                 final String className,
                                                 final CodePos codePos,
                                                 final Set<String> volatiles) {
    return new ClassAdapter(cw) {
      private String source;

      private final Set<String> volatileFields = volatiles;

      /*
       * Compose a chain of visitors:
       *   MethodTransformer -> LocalVariablesSorter -> MethodVisitor
       */
      public MethodVisitor visitMethod(
          int access, String name, String desc,
          String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(
            access, name, desc, signature, exceptions);
        String signatureStr = (null == signature ? "" : signature);
        String fullMethodName = className + "." + name + signatureStr;
        LocalVariablesSorter sorter = new LocalVariablesSorter(access, desc, mv);
        MethodTransformer transformer = new MethodTransformer(
            myself, sorter, access, name, fullMethodName, desc, source, syncMethods, codePos,
            volatileFields);
        transformer.setLocalVarsSorter(sorter);
        return transformer;
      }

      @Override
      public void visitSource(String source, String debug) {
        this.source = source;
      }

      @Override
      public FieldVisitor visitField(
          int access, String name, String desc, String signature, Object value) {
        if ((access & Opcodes.ACC_VOLATILE) != 0) {
          volatileFields.add(className + "." + name);
        }
        return super.visitField(access, name, desc, signature, value);
      }
    };
  }

  private void printTransformedClass(byte[] b) {
      ClassReader cr = new ClassReader(b);
      cr.accept(new TraceClassVisitor(new PrintWriter(System.out)),
                TraceClassVisitor.getDefaultAttributes(),
                0);
  }
}
