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

/**
 * Increments PC counters when a string description non-identical to the
 * previous one is encountered. Reports newly coming PCs via
 * {@code EventListener}. Reports method enter PCs lazily to allow a method
 * enter event know nothing about the line number of the first instruction in
 * the method.
 *
 * @author Egor Pasko
 */
class CodePos {
  private String lastDescr;

  private int pc;

  private boolean methodEntered;

  public long incPC(MethodTransformer.DescrCallback cb) {
    String descr = cb.getDescr();
    if (descr.equals(lastDescr)) {
      return pc;
    }else{
      lastDescr = descr;
      EventListener.codePosition(pc, descr);
      return pc++;
    }
  }

  public long incMethodEnterPC() {
    methodEntered = true;
    return pc;
  }

  public void line(int line, MethodTransformer.DescrCallback cb) {
    if (methodEntered) {
      EventListener.codePosition(pc, cb.getDescr());
      pc++;
      methodEntered = false;
    }
  }
}
