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

  // Ignore list to eliminate endless recursion.
  private static String[] ignore = new String[] { "java", "sun/", "org/jtsan" };

  // System methods to intercept.
  private static MethodMapping syncMethods = null;

  private final CodePos codePos = new CodePos();

  private final Set<String> volatileFields = new HashSet<String>();

  private String debugClassPrefix;

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
      }
    }
    try {
      if (fname.equals("-")) {
        EventListener.out = new PrintWriter(System.out, true /* auto flush */);
      } else {
        EventListener.out = new PrintWriter(
            new FileWriter(fname, false /* append */), true /* auto flush */);
      }
      EventListener.out.println("THR_START 0 0 0 0");
      EventListener.out.println("THR_FIRST_INSN 0 0 0 0");
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

      byte[] res;
      // Allow no more than a single instrumentation at a time to making code
      // positions sequential and non-conflicting.
      synchronized(this) {
        ca = newMethodTransformAdapter(this, cw, className, codePos, volatileFields);
        cr.accept(ca, ClassReader.EXPAND_FRAMES);
        res = cw.toByteArray();
        if (debugClassPrefix != null && className.startsWith(debugClassPrefix)) {
          printTransformedClass(res);
        }
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
    map.registerBefore("java/lang/System",
                 "arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V",
                 "jlSystemArrayCopy");
    map.registerBefore("java/lang/Object", "wait()V", "jlObjectWait");
    map.registerBefore("java/lang/Object", "notify()V", "jlObjectNotify");
    map.registerBefore("java/lang/Thread", "start()V", "jlThreadStart");
    map.registerAfter("java/lang/Thread", "join()V", "jlThreadJoin");
    map.registerAfter("java/lang/Object", "wait()V", "jlObjectWaitAfter");

    // java.util.concurrent.CountDownLatch
    map.registerBefore("java/util/concurrent/CountDownLatch",
                       "countDown()V", "jucCountDownLatch_countDown");
    map.registerAfter("java/util/concurrent/CountDownLatch",
                      "await()V", "jucCountDownLatch_await");

    // java.util.concurrent.Semaphore
    map.registerBefore("java/util/concurrent/Semaphore",
                       "release()V", "jucSemaphore_release");
    map.registerAfter("java/util/concurrent/Semaphore",
                      "acquire()V", "jucSemaphore_acquire");

    // java.util.concurrent.locks.ReentrantReadWriteLock
    // TODO(kcc): support tryLock().
    // TODO(kcc): send events based on the enclosing object (ReentrantReadWriteLock)
    // instead of the ReadLock/WriteLock object.
    map.registerAfter("java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock",
                      "lock()V", "jucRRWL_ReadLock_lock");
    map.registerBefore("java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock",
                       "unlock()V", "jucRRWL_ReadLock_unlock");
    map.registerAfter("java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock",
                      "lock()V", "jucRRWL_WriteLock_lock");
    map.registerBefore("java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock",
                       "unlock()V", "jucRRWL_WriteLock_unlock");

    // java.util.concurrent.locks.ReentrantLock
    // TODO(kcc): support tryLock().
    map.registerAfter("java/util/concurrent/locks/ReentrantLock",
                      "lock()V", "jucRL_lock");
    map.registerBefore("java/util/concurrent/locks/ReentrantLock",
                      "unlock()V", "jucRL_unlock");

    // RaceDetectorApi. Put exact matching to eliminate the cost of extra checks.
    // TODO(egor): methods must be named starting with lowercase letter.
    map.registerBeforeExact("RaceDetectorApi", "NoOp(Ljava/lang/Object;)V", "rdaApiNoOp");
    map.registerBeforeExact("RaceDetectorApi", "ExpectRaceBegin()V", "rdaApiExpectRaceBegin");
    map.registerBeforeExact("RaceDetectorApi", "ExpectRaceEnd()V", "rdaApiExpectRaceEnd");
    map.registerBeforeExact("RaceDetectorApi", "PrintStackTrace()V", "rdaApiPrintStackTrace");
    map.registerBeforeExact("RaceDetectorApi", "Print(Ljava/lang/String;)V", "rdaApiPrint");
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
            myself, sorter, access, name, fullMethodName, desc, source, syncMethods, codePos,
            volatileFields);
        AnalyzerAdapter aa = new AnalyzerAdapter(className, access, name, desc, transformer);
        transformer.setLocalVarsSorter(sorter);
        transformer.setStackAnalyzer(aa);
        return aa;
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
