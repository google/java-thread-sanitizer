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

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.ArrayList;

/**
 * Generates bytecode instructions to save method parameters according to their type.
 *
 * @author Egor Pasko
 */
public class LocalVarsSaver {
  private final MethodVisitor mv;
  private final LocalVariablesSorter lvs;
  private ArrayList<Type> types;
  private ArrayList<Integer> vars;
  private boolean hasRetValue;
  private Type returnType;
  private int returnVar;

  public LocalVarsSaver(MethodVisitor mv, LocalVariablesSorter lvs) {
    this.mv = mv;
    this.lvs = lvs;
  }

  public void initFromMethodDesc(String meth) {
    types = parseMethodName(meth);
    vars = newLocalVarsFromTypes(types, lvs);
    if (meth.charAt(meth.length() - 1) != 'V') {
      returnType = Type.getType(meth.substring(meth.indexOf(')') + 1));
      returnVar = lvs.newLocal(returnType);
    }
  }

  public void initFromTypeDesc(String desc) {
    types = new ArrayList<Type>();
    types.add(Type.getType(desc));
    vars = newLocalVarsFromTypes(types, lvs);
  }

  private static ArrayList<Integer> newLocalVarsFromTypes(
      ArrayList<Type> types, LocalVariablesSorter lvs) {
    ArrayList<Integer> vars = new ArrayList<Integer>();
    for (Type type : types) {
      vars.add(lvs.newLocal(type));
    }
    return vars;
  }

  private static ArrayList<Type> parseMethodName(String meth) {
    ArrayList<Type> types = new ArrayList<Type>();
    int i = meth.indexOf('(') + 1;
    int descEnd = meth.indexOf(')');
    while (i < descEnd) {
      int l = i;
      while (meth.charAt(l) == '[') {
        l++;
      }
      if (meth.charAt(l) == 'L') {
        while (meth.charAt(l) != ';') {
          l++;
        }
      }
      types.add(Type.getType(meth.substring(i, l+1)));
      i = l+1;
    }
    return types;
  }

  public void saveStack() {
    for (int i = types.size() - 1; i >= 0; i--) {
      mv.visitVarInsn(types.get(i).getOpcode(Opcodes.ISTORE), vars.get(i));
    }
  }

  public void loadStack() {
    for (int i = 0; i < types.size(); i++) {
      mv.visitVarInsn(types.get(i).getOpcode(Opcodes.ILOAD), vars.get(i));
    }
  }

  public void saveAndLoadStack() {
    saveStack();
    loadStack();
  }

  public boolean hasReturnValue() {
    return returnType != null;
  }

  public void saveReturnValue() {
    if (!hasReturnValue()) {
      throw new RuntimeException("Return value type is not porperly initialized to be saved.");
    }
    mv.visitVarInsn(returnType.getOpcode(Opcodes.ISTORE), returnVar);
  }

  public void loadReturnValue() {
    if (!hasReturnValue()) {
      throw new RuntimeException("Return value type is not porperly initialized to be loaded.");
    }
    mv.visitVarInsn(returnType.getOpcode(Opcodes.ILOAD), returnVar);
  }
}
