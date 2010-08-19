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

import org.jtsan.writers.BinAndStrEventWriter;
import org.jtsan.writers.BinaryEventWriter;
import org.jtsan.writers.EventWriter;
import org.jtsan.writers.NoneEventWriter;
import org.jtsan.writers.StringEventWriter;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
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

  private static final String WRITER_PREFIX = "writer=";
  // Possible values are:
  private static final String WRITER_TYPE_STRING = "str"; //(default)
  private static final String WRITER_TYPE_NULL = "none";
  private static final String WRITER_TYPE_BINARY = "bin";
  private static final String WRITER_TYPE_BINSTRDEBUG = "binstr";

  // Default events file name.
  private static final String DEFAULT_EVENTS_FILE = "jtsan.events";

  // Ignore list to eliminate endless recursion.
  private static String[] ignore = new String[]{
      "org/jtsan/",
      "sun",
      
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
      "java/lang/Thread",
      "java/lang/ref/",
      "java/lang/reflect/",
      "java/nio/",
      "java/util/Arrays",

      // Exclude some internals of java.util.concurrent to avoid false report.
      "java/util/concurrent/TimeUnit",
      "java/util/concurrent/locks/ReentrantReadWriteLock",
      "java/util/concurrent/locks/AbstractQueuedSynchronizer",
      "java/util/concurrent/locks/LockSupport",
      "java/util/concurrent/CyclicBarrier",
  };

  // A list of exceptions for the ignore list.
  private static String[] noignore = new String[]{};

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
    String fname = DEFAULT_EVENTS_FILE;
    boolean retransformSystem = false;
    // The events are written in string form by default.
    EventWriter eventWriter = new StringEventWriter();
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
        idx = args[i].lastIndexOf(WRITER_PREFIX);
        if (idx != -1) {
          String writerName = args[i].substring(idx + WRITER_PREFIX.length());
          if (writerName.equals(WRITER_TYPE_STRING)) {
            eventWriter = new StringEventWriter();
          } else if (writerName.equals(WRITER_TYPE_NULL)) {
            eventWriter = new NoneEventWriter();
          } else if (writerName.equals(WRITER_TYPE_BINARY)) {
            eventWriter = new BinaryEventWriter();
          } else if (writerName.equals(WRITER_TYPE_BINSTRDEBUG)) {
            eventWriter = new BinAndStrEventWriter();
          }
        }
      }
    }

    // Initialize output stream for interceptors.
    EventListener.setEventWriter(eventWriter);
    try {
      if (fname.equals("-")) {
        eventWriter.setOutputStream(System.out);
      } else {
        eventWriter.setOutputStream(new FileOutputStream(fname, false /* append */));
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
    for (String anIgnore : ignore) {
      if (className.startsWith(anIgnore)) {
        for (String aNoignore : noignore) {
          if (className.startsWith(aNoignore)) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  public byte[] transform(ClassLoader loader, String className,
                          Class clazz, java.security.ProtectionDomain domain, byte[] bytes) {
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
      synchronized (this) {
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
