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

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.List;

/**
 * Arranges instrumentation code around calls, both static and virtual.
 *
 * Inserts runtime class checking for all targets given. Delegates complex
 * visitor functionality to {@code MethodTransformer.GenerationCallback}, such
 * as:
 *  * PC generation,
 *  * LocalVarsSaver creation,
 *  * bytecode generation,
 *  * {@code EventListener} invocation,
 *  * etc.
 *
 * @author Egor Pasko
 */
public class InstrumentCalls {
  private final int opcode;
  private final String owner;
  private final String desc;
  private final GeneratorAdapter gen;
  private final MethodTransformer.GenerationCallback cb;

  private List<MethodMapping.HandlerInfo> beforeTargets;
  private List<MethodMapping.HandlerInfo> afterTargets;
  private List<MethodMapping.HandlerInfo> exceptionTargets;
  private LocalVarsSaver saver;
  private LocalVarsSaver saverThis;

  public InstrumentCalls(MethodTransformer.GenerationCallback cb,
      GeneratorAdapter gen, int opcode, String owner, String desc) {
    this.cb = cb;
    this.gen = gen;
    this.opcode = opcode;
    this.owner = owner;
    this.desc = desc;
  }

  public void setBeforeTargets(List<MethodMapping.HandlerInfo> beforeTargets) {
    this.beforeTargets = beforeTargets;
  }

  public void setAfterTargets(List<MethodMapping.HandlerInfo> afterTargets) {
    this.afterTargets = afterTargets;
  }

  public void setExceptionTargets(List<MethodMapping.HandlerInfo> exceptionTargets) {
    this.exceptionTargets = exceptionTargets;
  }

  public void generateCall() {
    // Test staticExceptionCorrectness fails by two reasons:
    // 1. There is no proper class test prior to interceptor call for static method
    // 2. The signature of the interceptor is incorrect
    // TODO(vors): Fix instrumentation of static methods.

    int beforeListeners = countListenerCalls(beforeTargets);
    int afterListeners = countListenerCalls(afterTargets);
    int exceptionListeners = countListenerCalls(exceptionTargets);
    int listeners = beforeListeners + afterListeners + exceptionListeners;
    if (listeners > 0) {
      saver = cb.createLocalVarsSaver();
      saver.saveStack();
      if (opcode != Opcodes.INVOKESTATIC) {
        // Store the object in a local variable.
        saverThis = cb.createObjSaver();
        saverThis.saveAndLoadStack();
      }
    }
    if (beforeListeners > 0) {
      genListenerCalls(beforeTargets, false /* saveRet */);
    }

    if (listeners > 0) {
      saver.loadStack();
    }

    if (exceptionListeners == 0) {
      // Invoke instrumenting method without interceptor.
      cb.visitMethodInsn();
    } else {
      // We support zero or one exception handler for each class.
      if (exceptionListeners > 1) {
        throw new RuntimeException("Too many exceptionTargets to " + cb.getMethodName());
      }

      MethodMapping.HandlerInfo exceptionInfo = exceptionTargets.get(0);

      Label startExceptionRegion = new Label();
      Label endExceptionRegion = new Label();

      gen.visitLabel(startExceptionRegion);
      cb.visitMethodInsn();
      gen.visitLabel(endExceptionRegion);

      genTryCatchBlock(exceptionInfo, startExceptionRegion, endExceptionRegion);
    }

    if (afterListeners > 0) {
      genListenerCalls(afterTargets, true /* saveRet */);
    }
  }

  private static String addClassAsFirstArgument(String className, String desc) {
    return desc.replace("(", "(L" + className + ";");
  }

  private int countListenerCalls(List<MethodMapping.HandlerInfo> targets) {
    int ret = 0;
    if (targets == null) {
        return 0;
    }
    for (MethodMapping.HandlerInfo target : targets) {
      if (!target.isExact() || target.getWatchedClass().equals(owner)) {
        ret++;
      }
    }
    return ret;
  }

  private void genTryCatchBlock(MethodMapping.HandlerInfo target, Label startExceptionRegion,
                                Label endExceptionRegion) {
    Label startCatchRegion = new Label();
    Label labelSkip = new Label();
    Label labelAfter = new Label();

    boolean isStatic = (opcode == Opcodes.INVOKESTATIC);
    
    gen.visitJumpInsn(Opcodes.GOTO, labelAfter);

    gen.visitLabel(startCatchRegion);

    // TODO(vors): Handle static method exceptions incorrectly: we don't check class
    if (!isStatic) {
      // Skip the event if 'this' is not a child of the base class.
      saverThis.loadStack();
      gen.instanceOf(Type.getObjectType(target.getWatchedClass()));
      gen.visitJumpInsn(Opcodes.IFEQ, labelSkip);
    }

    // Restore stack to invoke exception handler.
    // dup() exception object.
    gen.dup();
    if (!isStatic) {
      saverThis.loadStack();
      gen.swap();
    }
    gen.push(cb.codePosition());

    cb.listenerCall(target.getHandler(),
        (isStatic ? "(" : ("(L" + target.getWatchedClass()) + ";") + "Ljava/lang/Throwable;J)V");

    gen.visitLabel(labelSkip);
    // throw exception object to next handler in exception table.
    gen.throwException();

    gen.visitLabel(labelAfter);

    // Mark try catch region with highest priority to all Exceptions.
    cb.topVisitTryCatchBlock(startExceptionRegion, endExceptionRegion, startCatchRegion,
            "java/lang/Throwable");

  }

  private void genListenerCalls(
      List<MethodMapping.HandlerInfo> targets, boolean saveRet) {
    boolean returns = false;
    if (saveRet && saver.hasReturnValue()) {
      returns = true;
    }
    if (returns) {
      saver.saveReturnValue();
    }
    for (MethodMapping.HandlerInfo target : targets) {
      Label labelSkip = new Label();
      Label labelAfter = new Label();
      boolean exact = target.isExact();
      boolean callGenerated = false;
      boolean listenStatic = (opcode == Opcodes.INVOKESTATIC);
      if (!listenStatic) {
        saverThis.loadStack();
      }
      // Shape the listener method's descriptor.
      String tailReplacement = "J)V";
      int idx = desc.indexOf(")");
      if (returns) {
        String retType = desc.substring(idx + 1, desc.length());
        tailReplacement = retType + tailReplacement;
      }
      String actualDesc = desc.substring(0, idx) + tailReplacement;
      if (!listenStatic) {
        actualDesc = addClassAsFirstArgument(target.getWatchedClass(), actualDesc);
      }

      // Insert type match checking and the listener call.
      if (!exact && !listenStatic) {
        // Skip the event if 'this' is not a child of the base class.
        gen.dup();
        gen.instanceOf(Type.getObjectType(target.getWatchedClass()));
        gen.visitJumpInsn(Opcodes.IFEQ, labelSkip);
        gen.checkCast(Type.getObjectType(target.getWatchedClass()));
      }
      if (!exact || target.getWatchedClass().equals(owner)) {
        saver.loadStack();
        if (returns) {
          saver.loadReturnValue();
        }
        gen.push(cb.codePosition());
        cb.listenerCall(target.getHandler(), actualDesc);
        callGenerated = true;
      }
      if (callGenerated && !listenStatic) {
        gen.visitJumpInsn(Opcodes.GOTO, labelAfter);
        gen.visitLabel(labelSkip);
        gen.pop();
        gen.visitLabel(labelAfter);
      }
    }
    if (returns) {
      saver.loadReturnValue();
    }
  }
}
