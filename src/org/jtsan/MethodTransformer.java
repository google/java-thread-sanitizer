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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.Set;

/**
 * Transforms all method bodies.
 *
 * @author Egor Pasko
 */
public class MethodTransformer extends AdviceAdapter {
  private final Agent agent;
  private final String fullName;
  private final Label startFinally = new Label();
  private final MethodMapping methods;
  private final String srcFile;
  private final String methodName;
  private final CodePos codePos;
  private final DescrCallback lazyDescr;
  private final Set<String> volatileFields;
  private final boolean methodIsStatic;

  private static final int[] storeOpcodes =
    {IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE};

  private static final int[] loadOpcodes =
    {IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD};

  private LocalVariablesSorter localVarsSorter;

  private int line;

  public MethodTransformer(Agent agent, MethodVisitor mv,
      int acc, String name, String fullName, String desc, String src, MethodMapping methods,
      CodePos codePos, Set<String> volatileFields) {
    super(mv, acc, name, desc);
    this.agent = agent;
    this.fullName = fullName;
    this.methods = methods;
    this.srcFile = src;
    this.methodName = name;
    this.codePos = codePos;
    this.volatileFields = volatileFields;
    this.methodIsStatic = ((acc & Opcodes.ACC_STATIC) != 0);
    lazyDescr = new DescrCallback();
  }

  public void setLocalVarsSorter(LocalVariablesSorter lvs) {
    localVarsSorter = lvs;
  }

  private static boolean isArrayStore(int opcode) {
    return contains(storeOpcodes, opcode);
  }

  private static boolean isArrayLoad(int opcode) {
    return contains(loadOpcodes, opcode);
  }

  private static boolean contains(int[] list, int value) {
    for (int i = 0; i < list.length; i++) {
      if (list[i] == value) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void onMethodEnter() {
    // TODO: this is a workaround to having not all <init>s in onMethodEnter(), needs fixing.
    if (!methodName.equals("<init>")) {
      push(codePos.incMethodEnterPC());
      captureMethodEnter();
    }
    if ((methodAccess & Opcodes.ACC_SYNCHRONIZED) != 0 && !methodIsStatic) {
      loadThis();
      push(codePos.incMethodEnterPC());
      captureMonitorEnter();
    }
  }

  @Override
  protected void onMethodExit(int opcode) {
    if (opcode != ATHROW) {
      onFinally();
    }
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    Label endFinally = new Label();
    mv.visitTryCatchBlock(startFinally, endFinally, endFinally, null);
    mv.visitLabel(endFinally);
    onFinally();
    mv.visitInsn(ATHROW);
    mv.visitMaxs(maxStack + 3, maxLocals);
  }

  @Override
  public void visitInsn(final int opcode) {
    if (opcode == MONITORENTER) {
      dup();
      super.visitInsn(opcode);
      push(genCodePosition());
      captureMonitorEnter();
      return;
    } else if (opcode == MONITOREXIT) {
      dup();
      push(genCodePosition());
      captureMonitorExit();
    } else if (isArrayStore(opcode)) {
      captureArrayStore(opcode);
    } else if (isArrayLoad(opcode)) {
      captureArrayLoad(opcode);
    }
    super.visitInsn(opcode);
  }

  @Override
  public void visitCode() {
    super.visitCode();
    mv.visitLabel(startFinally);
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    super.visitLineNumber(line, start);
    this.line = line;
    codePos.line(line, lazyDescr);
  }

  private void onFinally() {
    if ((methodAccess & Opcodes.ACC_SYNCHRONIZED) != 0 && !methodIsStatic) {
      loadThis();
      push(genCodePosition());
      captureMonitorExit();
    }
    // TODO: this is a workaround to having not all <init>s in onMethodEnter(), needs fixing.
    if (!methodName.equals("<init>")) {
      push(genCodePosition());
      captureMethodExit();
    }
  }

  class DescrCallback {
    public String getDescr() { return fullName + " " + srcFile + " " + line; }
  }

  private long genCodePosition() {
    return codePos.incPC(lazyDescr);
  }

  private boolean isVolatileField(String fname) {
    return volatileFields.contains(fname);
  }

  private void visitObjectFieldAccess(
      String name, String desc, boolean isWrite, boolean isVolatile) {
    long pc = genCodePosition();
    LocalVarsSaver saver = new LocalVarsSaver(mv, localVarsSorter);
    if (isWrite) {
      saver.initFromTypeDesc(desc);
      saver.saveStack();
      dup();
      push(1);
    } else {
      dup();
      push(0);
    }
    push(name);
    push(pc);
    push(isVolatile);
    visitObjectFieldAccessCall();
    if (isWrite) {
      saver.loadStack();
    }
  }

  private void visitObjectFieldAccessCall() {
    visitListenerCall("objectFieldAccess", "(Ljava/lang/Object;ZLjava/lang/String;JZ)V");
  }

  private void visitStaticFieldAccess(String fullName, boolean isWrite) {
    // We pass the full name of the field access to the event listener. In presence of many
    // classloaders some distinct fields of non-related classes may appear to have the same name.
    // This may result in some false positives, but very unlikely, since classes from different
    // classloaders barely share stuff.
    push(fullName);
    push(isWrite);
    push(genCodePosition());
    push(isVolatileField(fullName));
    visitListenerCall("staticFieldAccess", "(Ljava/lang/String;ZJZ)V");
  }

  @Override
  public void visitFieldInsn(
      int opcode, String owner, String name, String desc) {
    String param = "";
    boolean isStatic = false;
    boolean isWrite = false;
    if (opcode == GETSTATIC) {
      isStatic = true;
      isWrite = false;
    } else if (opcode == PUTSTATIC) {
      isStatic = true;
      isWrite = true;
    } else if (opcode == GETFIELD) {
      isStatic = false;
      isWrite = false;
    } else if (opcode == PUTFIELD) {
      isStatic = false;
      isWrite = true;
    }
    if (!"<init>".equals(methodName) && !"<clinit>".equals(methodName)) {
      // The method <init> may save values to fields of an uninitialized object.
      // We cannot pass an 'ininitialized this' to an interceptor without
      // causing a VerifyError. 'Uninitialized this' can be detected using
      // StackAnalyzer with precomputed stack frame info by ClassWriter. Skip
      // this process for simplicity.
      //
      // The method <clinit> may save values to static fields of a class,
      // but JLS guarantees correctness. 
      if (isStatic) {
        visitStaticFieldAccess(owner + "." + name, isWrite);
      } else {
        if (!methods.isBenignRaceField(owner, name)) {
          visitObjectFieldAccess(name, desc, isWrite, isVolatileField(owner + "." + name));
        }
      }
    }
    super.visitFieldInsn(opcode, owner, name, desc);
  }

  private void captureMonitorExit() {
    visitListenerCall("monitorExit", "(Ljava/lang/Object;J)V");
  }

  private void captureMonitorEnter() {
    visitListenerCall("monitorEnter", "(Ljava/lang/Object;J)V");
  }

  private void captureMethodEnter() {
    if (methodName.equals("run") && !methodIsStatic) {
      loadThis();
      push(genCodePosition());
      visitListenerCall("runMethodEnter", "(Ljava/lang/Object;J)V");
    }
    visitListenerCall("methodEnter", "(J)V");
  }

  private void captureMethodExit() {
    visitListenerCall("methodExit", "(J)V");
    if (methodName.equals("run") && !methodIsStatic) {
      loadThis();
      push(genCodePosition());
      visitListenerCall("runMethodExit", "(Ljava/lang/Object;J)V");
    }
  }

  private void captureArrayLoad(int opcode) {
    int indexVar = localVarsSorter.newLocal(Type.INT_TYPE);
    mv.visitVarInsn(ISTORE, indexVar);
    dup();
    mv.visitVarInsn(ILOAD, indexVar);
    push(0);
    push(genCodePosition());
    visitListenerCall("arrayAccess", "(Ljava/lang/Object;IZJ)V");
    mv.visitVarInsn(ILOAD, indexVar);
  }

  private void captureArrayStore(int opcode) {
    Type slotType = getSourceSlotType(opcode);

    int valueVar = localVarsSorter.newLocal(slotType);
    int indexVar = localVarsSorter.newLocal(Type.INT_TYPE);

    mv.visitVarInsn(slotType.getOpcode(ISTORE), valueVar);
    mv.visitVarInsn(ISTORE, indexVar);
    dup();
    mv.visitVarInsn(ILOAD, indexVar);
    push(1);
    push(genCodePosition());
    visitListenerCall("arrayAccess", "(Ljava/lang/Object;IZJ)V");
    mv.visitVarInsn(ILOAD, indexVar);
    mv.visitVarInsn(slotType.getOpcode(ILOAD), valueVar);
  }

  /**
   * A simple generation interface for using in {@code InstrumentCalls}.
   */
  public class GenerationCallback {
    private final int opcode;
    private final String owner;
    private final String name;
    private final String desc;

    public GenerationCallback(int opcode, String owner, String name, String desc) {
      this.opcode = opcode;
      this.owner = owner;
      this.name = name;
      this.desc = desc;
    }

    public LocalVarsSaver createLocalVarsSaver() {
      LocalVarsSaver saver = new LocalVarsSaver(mv, localVarsSorter);
      saver.initFromMethodDesc(desc);
      return saver;
    }

    public void visitMethodInsn() {
      superVisitMethodInsn(opcode, owner, name, desc);
    }

    public long codePosition() {
      return genCodePosition();
    }

    public void listenerCall(String meth, String listenerDesc) {
      visitListenerCall(meth, listenerDesc);
    }
  }

  public void visitMethodInsn(int opcode, String owner, String name, String desc) {
    // Capture code position on the call.
    push(genCodePosition());
    visitListenerCall("beforeCall", "(J)V");

    // Capture special (=registered) calls with their parameters.
    InstrumentCalls callsGen =
        new InstrumentCalls(new GenerationCallback(opcode, owner, name, desc),
                            this, opcode, owner, desc);
    callsGen.setBeforeTargets(methods.getTargetsFor(name + desc, MethodMapping.E_BEFORE_METHOD));
    callsGen.setAfterTargets(methods.getTargetsFor(name + desc, MethodMapping.E_AFTER_METHOD));
    callsGen.generateCall();

    // Capture code position after the call.
    push(genCodePosition());
    visitListenerCall("afterCall", "(J)V");
  }

  public void superVisitMethodInsn(int opcode, String owner, String name, String desc) {
    super.visitMethodInsn(opcode, owner, name, desc);
  }

  private void visitListenerCall(String method, String descr) {
    mv.visitMethodInsn(INVOKESTATIC, "org/jtsan/EventListener", method, descr);
  }

  private static Type getSourceSlotType(int opcode) {
    switch (opcode) {
      case IASTORE: return Type.INT_TYPE;
      case LASTORE: return Type.LONG_TYPE;
      case FASTORE: return Type.FLOAT_TYPE;
      case DASTORE: return Type.DOUBLE_TYPE;
      case AASTORE: return Type.getType(Object.class);
      case BASTORE: return Type.INT_TYPE;
      case CASTORE: return Type.INT_TYPE;
      case SASTORE: return Type.INT_TYPE;
      default:
        throw new Error("unsupported array opcode: " + opcode);
    }
  }
}
