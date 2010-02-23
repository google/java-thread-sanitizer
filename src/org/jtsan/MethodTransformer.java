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

import java.util.List;
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

  private static final int[] storeOpcodes =
    {IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE};

  private static final int[] loadOpcodes =
    {IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD};

  private LocalVariablesSorter localVarsSorter;

  private AnalyzerAdapter stackAnalyzer;

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
    lazyDescr = new DescrCallback();
  }

  public void setLocalVarsSorter(LocalVariablesSorter lvs) {
    localVarsSorter = lvs;
  }

  public void setStackAnalyzer(AnalyzerAdapter analyzer) {
    stackAnalyzer = analyzer;
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
    if ((methodAccess & Opcodes.ACC_SYNCHRONIZED) != 0) {
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
      push(genCodePosition());
      captureMonitorEnter();
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
    this.line = line;
    codePos.line(line, lazyDescr);
  }

  private void onFinally() {
    if ((methodAccess & Opcodes.ACC_SYNCHRONIZED) != 0) {
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

  private void visitObjectFieldAccess(String name, boolean isWrite, boolean isVolatile) {
    long pc = genCodePosition();
    if (!isWrite) {
      dup();
      push(0);
      push(name);
      push(pc);
      push(isVolatile);
      visitObjectFieldAccessCall();
    } else {
      List stack = stackAnalyzer.stack;
      int size = stack.size();
      if (stack.get(size - 2) == Opcodes.UNINITIALIZED_THIS) {
        // Do not instrument writes to members of 'this' in <init>. I kindly ask you to not to write
        // to uninitialized 'this' from more than one thread, please.
        return;
      }
      Object slot = stack.get(size - 1);
      if (slot == Opcodes.INTEGER) {
        int storedVar = localVarsSorter.newLocal(Type.INT_TYPE);
        mv.visitVarInsn(ISTORE, storedVar);
        dup();
        push(1);
        push(name);
        push(pc);
        push(isVolatile);
        visitObjectFieldAccessCall();
        mv.visitVarInsn(ILOAD, storedVar);
      } else if (slot instanceof String) {
        // All objrefs are represented as their string names.
        String obj = (String)slot;
        int storedVar = localVarsSorter.newLocal(Type.getObjectType(obj));
        mv.visitVarInsn(ASTORE, storedVar);
        dup();
        push(1);
        push(name);
        push(pc);
        push(isVolatile);
        visitObjectFieldAccessCall();
        mv.visitVarInsn(ALOAD, storedVar);
      }
      // TODO: instrument other types of slot writes:
      //   Opcodes.FLOAT,
      //   Opcodes.LONG,
      //   Opcodes.DOUBLE
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
    if (isStatic) {
      visitStaticFieldAccess(owner + "." + name, isWrite);
    } else {
      visitObjectFieldAccess(name, isWrite, isVolatileField(owner + "." + name));
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
    visitListenerCall("methodEnter", "(J)V");
  }

  private void captureMethodExit() {
    visitListenerCall("methodExit", "(J)V");
    if (methodName.equals("run")) {
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
    visitListenerCall("arrayLoad", "(Ljava/lang/Object;I)V");
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
    visitListenerCall("arrayStore", "(Ljava/lang/Object;I)V");
    mv.visitVarInsn(ILOAD, indexVar);
    mv.visitVarInsn(slotType.getOpcode(ILOAD), valueVar);
  }

  private static String addClassAsFirstArgument(String className, String desc) {
    return desc.replace("(", "(L" + className + ";");
  }

  public void visitMethodInsn(int opcode, String owner, String name, String desc) {
    String fullMethodName = name + desc;
    String beforeTarget =
      methods.getTargetFor(owner, fullMethodName, MethodMapping.E_BEFORE_METHOD);
    String afterTarget =
      methods.getTargetFor(owner, fullMethodName, MethodMapping.E_AFTER_METHOD);

    // Capture code position on the call.
    push(genCodePosition());
    visitListenerCall("beforeCall", "(J)V");

    // Proceed an ordinary call without extra instrumentation.
    if (opcode == Opcodes.INVOKESPECIAL) {
      mv.visitMethodInsn(opcode, owner, name, desc);
      return;
    }

    // Capture special (=registered) calls with their parameters.
    InstrumentCallsGenerator callsGen = new InstrumentCallsGenerator(opcode, owner, name, desc);
    callsGen.initSaveStack(fullMethodName, beforeTarget, afterTarget);
    if (opcode == Opcodes.INVOKESTATIC) {
      callsGen.generateCall(desc.replace(")", "J)"));
    } else {
      if (afterTarget != null) {
        dup(); // Dup 'this' reference for the after-call instrumentation.
      }
      if (beforeTarget != null) {
        dup(); // Dup 'this' reference for the before-call instrumentation.
      }
      callsGen.generateCall(addClassAsFirstArgument(owner, desc).replace(")", "J)"));
    }
  }

  private void visitListenerCall(String method, String descr) {
    mv.visitMethodInsn(INVOKESTATIC, "org/jtsan/EventListener", method, descr);
  }

  /**
   * Generates a call with proper before- and after- instrumentations including
   * PC generation and loading local variables to stack via LocalVarsSaver.
   */
  private class InstrumentCallsGenerator {
    private final int opcode;
    private final String owner;
    private final String name;
    private final String desc;
    private String beforeTarget;
    private String afterTarget;

    // TODO: should be renamed to MethodParamsSaver.
    private LocalVarsSaver saver;

    public InstrumentCallsGenerator(int opcode, String owner, String name, String desc) {
      this.opcode = opcode;
      this.owner = owner;
      this.name = name;
      this.desc = desc;
    }

    public void initSaveStack(String fullMethodName, String beforeTarget, String afterTarget) {
      this.beforeTarget = beforeTarget;
      this.afterTarget = afterTarget;
      if (beforeTarget != null || afterTarget != null) {
        saver = new LocalVarsSaver(mv, fullMethodName, localVarsSorter);
        saver.saveStack();
      }
    }

    public void generateCall(String listenDesc) {
      if (beforeTarget != null) {
        saver.loadStack();
        push(genCodePosition());
        visitListenerCall(beforeTarget, listenDesc);
        saver.loadStack();
      }
      mv.visitMethodInsn(opcode, owner, name, desc);
      if (afterTarget != null) {
        saver.loadStack();
        push(genCodePosition());
        visitListenerCall(afterTarget, listenDesc);
      }
    }
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
