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
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.Vector;

/**
 * Generates bytecode instructions to save method parameters according to their type.
 *
 * @author Egor Pasko
 */
public class LocalVarsSaver {
  private final MethodVisitor mv;
  private final LocalVariablesSorter lvs;
  private Vector<Type> types;
  private Vector<Integer> vars;

  public LocalVarsSaver(MethodVisitor mv, LocalVariablesSorter lvs) {
    this.mv = mv;
    this.lvs = lvs;
  }

  public void initFromMethodDesc(String fullMethodName) {
    types = parseMethodName(fullMethodName);
    vars = newLocalVarsFromTypes(types, lvs);
  }

  public void initFromTypeDesc(String desc) {
    char c = desc.charAt(0);
    types = new Vector<Type>();
    if (c == 'L' || c == '[') {
      types.add(Type.getObjectType("java/lang/Object"));
    } else {
      types.add(typeByChar(c));
    }
    vars = newLocalVarsFromTypes(types, lvs);
  }

  private static boolean inParams(int i, String methodName) {
    return i < methodName.length() && methodName.charAt(i) != ')';
  }

  private static Vector<Integer> newLocalVarsFromTypes(
      Vector<Type> types, LocalVariablesSorter lvs) {
    Vector<Integer> vars = new Vector<Integer>();
    for (Type type : types) {
      vars.add(new Integer(lvs.newLocal(type)));
    }
    return vars;
  }

  private static Type typeByChar(char ch) {
    switch (ch) {
      case 'I':
        return Type.INT_TYPE;
      case 'J':
        return Type.LONG_TYPE;
      case 'F':
        return Type.FLOAT_TYPE;
      case 'D':
        return Type.DOUBLE_TYPE;
      case 'B':
        return Type.BYTE_TYPE;
      case 'C':
        return Type.CHAR_TYPE;
      case 'S':
        return Type.SHORT_TYPE;
      case 'Z':
        return Type.BOOLEAN_TYPE;
      default:
        throw new UnsupportedOperationException(
            "saving/loading type " + ch + " is not supported");
    }
  }

  private static Vector<Type> parseMethodName(String meth) {
    Vector<Type> types = new Vector<Type>();
    for (int i = meth.indexOf('(') + 1; inParams(i, meth); i++) {
      char ch = meth.charAt(i);
      Type type;
      if (ch != 'L') {
        type = typeByChar(ch);
      } else {
        StringBuilder sb = new StringBuilder();
        for (int j = i + 1; inParams(j, meth); j++) {
          char och = meth.charAt(j);
          if (och == ';') {
            i = j;
            break;
          }
          sb.append(och);
        }
        type = Type.getObjectType(sb.toString());
      }
      types.add(type);
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
}
