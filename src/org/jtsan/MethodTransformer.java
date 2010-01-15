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

import java.util.HashSet;
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

  private static final int[] storeOpcodes =
    {IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE};

  private static final int[] loadOpcodes =
    {IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD};

  private LocalVariablesSorter localVarsSorter;

  private AnalyzerAdapter stackAnalyzer;

  private int line;

  public MethodTransformer(Agent agent, MethodVisitor mv,
      int acc, String name, String fullName, String desc, String src, MethodMapping methods) {
    super(mv, acc, name, desc);
    this.agent = agent;
    this.fullName = fullName;
    this.methods = methods;
    this.srcFile = src;
    this.methodName = name;
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
      push(genCodePosition());
      captureMethodEnter();
    }
    if ((methodAccess & Opcodes.ACC_SYNCHRONIZED) != 0) {
      loadThis();
      push(genCodePosition());
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

  private long genCodePosition() {
    return agent.incPC(fullName + " " + srcFile + " " + line);
  }

  private void visitObjectFieldAccess(String name, boolean isWrite) {
    long pc = genCodePosition();
    if (!isWrite) {
      dup();
      push(0);
      push(name);
      push(pc);
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
    mv.visitMethodInsn(INVOKESTATIC,
        "org/jtsan/EventListener",
        "objectFieldAccess",
        "(Ljava/lang/Object;ZLjava/lang/String;J)V");
  }

  private void visitStaticFieldAccess(String fullName, boolean isWrite) {
    // We pass the full name of the field access to the event listener. In presence of many
    // classloaders some distinct fields of non-related classes may appear to have the same name.
    // This may result in some false positives, but very unlikely, since classes from different
    // classloaders barely share stuff.
    push(fullName);
    push(isWrite);
    push(genCodePosition());
    mv.visitMethodInsn(INVOKESTATIC,
        "org/jtsan/EventListener",
        "staticFieldAccess",
        "(Ljava/lang/String;ZJ)V");
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
      visitObjectFieldAccess(name, isWrite);
    }
    super.visitFieldInsn(opcode, owner, name, desc);
  }

  private void captureMonitorExit() {
    mv.visitMethodInsn(INVOKESTATIC,
        "org/jtsan/EventListener",
        "monitorExit",
        "(Ljava/lang/Object;J)V");
  }

  private void captureMonitorEnter() {
    mv.visitMethodInsn(INVOKESTATIC,
        "org/jtsan/EventListener",
        "monitorEnter",
        "(Ljava/lang/Object;J)V");
  }

  private void captureMethodEnter() {
    mv.visitMethodInsn(INVOKESTATIC,
        "org/jtsan/EventListener",
        "methodEnter",
        "(J)V");
  }

  private void captureMethodExit() {
    mv.visitMethodInsn(INVOKESTATIC,
        "org/jtsan/EventListener",
        "methodExit",
        "(J)V");
    if (methodName.equals("run")) {
      loadThis();
      push(genCodePosition());
      mv.visitMethodInsn(INVOKESTATIC,
          "org/jtsan/EventListener",
          "runMethodExit",
          "(Ljava/lang/Object;J)V");
    }
  }

  private void captureArrayLoad(int opcode) {
    int indexVar = localVarsSorter.newLocal(Type.INT_TYPE);
    mv.visitVarInsn(ISTORE, indexVar);
    dup();
    mv.visitVarInsn(ILOAD, indexVar);
    mv.visitMethodInsn(INVOKESTATIC,
        "org/jtsan/EventListener",
        "arrayLoad",
        "(Ljava/lang/Object;I)V");
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
    mv.visitMethodInsn(INVOKESTATIC,
                       "org/jtsan/EventListener",
                       "arrayStore",
                       "(Ljava/lang/Object;I)V");
    mv.visitVarInsn(ILOAD, indexVar);
    mv.visitVarInsn(slotType.getOpcode(ILOAD), valueVar);
  }

  private static String addClassAsFirstArgument(String className, String desc) {
    return desc.replace("(", "(L" + className + ";");
  }

  public void visitMethodInsn(int opcode, String owner, String name, String desc) {
    String fullMethodName = name + desc;
    String targetName = methods.getTargetFor(owner, fullMethodName);
    // Capture code position on the call.
    push(genCodePosition());
    mv.visitMethodInsn(INVOKESTATIC,
                       "org/jtsan/EventListener",
                       "beforeCall",
                       "(J)V");

    // Proceed an ordinary call without extra instrumentation.
    if (targetName == null || opcode == Opcodes.INVOKESPECIAL) {
      mv.visitMethodInsn(opcode, owner, name, desc);
      return;
    }

    // Capture special (=registered) calls with their parameters.
    // TODO: should be renamed to MethodParamsSaver.
    LocalVarsSaver saver = new LocalVarsSaver(mv, fullMethodName, localVarsSorter);
    if (opcode == Opcodes.INVOKESTATIC) {
      saver.saveAndLoadStack();
      push(genCodePosition());
      mv.visitMethodInsn(INVOKESTATIC,
                         "org/jtsan/EventListener",
                         targetName,
                         desc.replace(")", "J)"));
      saver.loadStack();
      mv.visitMethodInsn(opcode, owner, name, desc);
    } else {
      saver.saveStack();
      dup(); // Dup 'this' reference for the call.
      saver.loadStack();
      push(genCodePosition());
      mv.visitMethodInsn(INVOKESTATIC,
                         "org/jtsan/EventListener",
                         targetName,
                         addClassAsFirstArgument(owner, desc).replace(")", "J)"));
      saver.loadStack();
      mv.visitMethodInsn(opcode, owner, name, desc);
    }
  }

  private Type getSourceSlotType(int opcode) {
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
