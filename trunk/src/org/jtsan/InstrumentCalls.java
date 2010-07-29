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
  private LocalVarsSaver saver;

  public InstrumentCalls(MethodTransformer.GenerationCallback cb,
      GeneratorAdapter gen, int opcode, String owner, String desc) {
    this.cb = cb;
    this.gen = gen;
    this.opcode = opcode;
    this.owner = owner;
    this.desc = desc;
  }

  public void setBeforeTargets(List<MethodMapping.HandlerInfo> targets) {
    beforeTargets = targets;
  }

  public void setAfterTargets(List<MethodMapping.HandlerInfo> targets) {
    afterTargets = targets;
  }

  public void generateCall() {
    int beforeListeners = countListenerCalls(beforeTargets);
    int afterListeners = countListenerCalls(afterTargets);
    int listeners = beforeListeners + afterListeners;
    if (listeners > 0) {
      saver = cb.createLocalVarsSaver();
      saver.saveStack();
    }
    if (opcode != Opcodes.INVOKESTATIC) {
      // 'Dup' as many times as it would require to make listener calls.
      for (int i = 0; i < listeners; i++) {
        gen.dup();
      }
    }
    if (beforeListeners > 0) {
      genListenerCalls(beforeTargets, false /* saveRet */);
    }
    if (listeners > 0) {
      saver.loadStack();
    }
    cb.visitMethodInsn();
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
