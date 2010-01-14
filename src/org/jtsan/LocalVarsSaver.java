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
  private final Vector<Type> types;
  private final Vector<Integer> vars;
  private final MethodVisitor mv;
  private final LocalVariablesSorter lvs;

  public LocalVarsSaver(
      MethodVisitor mv, String fullMethodName, LocalVariablesSorter lvs) {
    this.mv = mv;
    this.lvs = lvs;
    this.types = parseMethodName(fullMethodName);
    this.vars = newLocalVarsFromTypes(types, lvs);
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

  private static Vector<Type> parseMethodName(String meth) {
    Vector<Type> types = new Vector<Type>();
    for (int i = meth.indexOf('('); inParams(i, meth); i++) {
      char ch = meth.charAt(i);
      Type type;
      switch (ch) {
        case '(':
          continue;
        case 'I':
          type = Type.INT_TYPE;
          break;
        case 'J':
          type = Type.LONG_TYPE;
          break;
        case 'L':
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
          break;
        case 'F':
          type = Type.FLOAT_TYPE;
          break;
        case 'D':
          type = Type.DOUBLE_TYPE;
          break;
        default:
          // Currently not supported types are: [, B, C, S, Z.
          throw new UnsupportedOperationException(
              "saving/loading type " + ch + " is not supported");
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
