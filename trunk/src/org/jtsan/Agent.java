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

/**
 * Instruments all method bodies to intercept events: method entry, method exit, memory accesses,
 * synchronization/threading events.
 *
 * @author Egor Pasko
 */
public class Agent implements ClassFileTransformer {

  // Ignore list to eliminate endless recursion.
  private static String[] ignore = new String[] { "java", "sun/", "org/jtsan" };

  // System methods to intercept.
  private static MethodMapping syncMethods = null;

  private int pc;

  private String lastDescr;

  public static void premain(String arg, Instrumentation instrumentation) {
    Agent agent = new Agent();
    instrumentation.addTransformer(agent, true);
    syncMethods = new MethodMapping();
    initSyncMethodMappings(syncMethods);
    // TODO:
    //   load all java/lang and java/util classes that the instrumenter will be using
    //     (no transforming)
    //   retransform all loaded java/lang and java/util classes
    String fname = "jtsan.log";
    try {
      EventListener.out = new PrintWriter(
          new FileWriter(fname, false /* append */), true /* auto flush */);
      System.err.println("Java Agent: appending threading events to file: " + fname);
    } catch (IOException e) {
      System.err.println("Exception while opening file: " + fname + ", reason: " + e);
      System.exit(5);
    }
  }

  private boolean inIgnoreList(String className) {
    for (int i = 0; i < ignore.length; i++) {
      if (className.startsWith(ignore[i])) {
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
      ClassWriter cw = new ClassWriter(cr,
          ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
      ca = newMethodTransformAdapter(this, cw, className);

      cr.accept(ca, ClassReader.EXPAND_FRAMES);
      byte[] res = cw.toByteArray();
      if (className.startsWith("Hello")) {
        //printTransformedClass(res);
      }
      return res;
    } catch (Exception e) {
      System.out.println(
          "Exception occured during transformation. Transformed bytes are discarded.");
      e.printStackTrace();
      return bytes;
    }
  }

  private static void initSyncMethodMappings(MethodMapping map) {
    map.register("java/lang/System",
                 "arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V",
                 "jlSystemArrayCopy");
    map.register("java/lang/Object", "wait()V", "jlObjectWait");
    map.register("java/lang/Object", "notify()V", "jlObjectNotify");
    map.register("java/lang/Thread", "start()V", "jlThreadStart");
    map.register("java/lang/Thread", "join()V", "jlThreadJoin");
  }

  private ClassAdapter newMethodTransformAdapter(
      final Agent myself, ClassWriter cw, final String className) {
    return new ClassAdapter(cw) {
      private String source;

      /*
       * Compose a chain of visitors:
       *   AnalyzerAdapter -> MethodTransformer -> LocalVariablesSorter -> MethodVisitor
       *   (operator overloading would be nice, huh?)
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
            myself, sorter, access, name, fullMethodName, desc, source, syncMethods);
        AnalyzerAdapter aa = new AnalyzerAdapter(className, access, name, desc, transformer);
        transformer.setLocalVarsSorter(sorter);
        transformer.setStackAnalyzer(aa);
        return aa;
      }

      @Override
      public void visitSource(String source, String debug) {
        this.source = source;
      }
    };
  }

  private void printTransformedClass(byte[] b) {
      ClassReader cr = new ClassReader(b);
      cr.accept(new TraceClassVisitor(new PrintWriter(System.out)),
                TraceClassVisitor.getDefaultAttributes(),
                0);
  }

  public synchronized long incPC(String descr) {
    if (descr.equals(lastDescr)) {
      return pc;
    }else{
      lastDescr = descr;
      EventListener.codePosition(pc++, descr);
      return pc;
    }
  }
}
