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

package org.jtsan.writers;

import org.jtsan.EventType;

import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Write to OutputStream tsan events in String format.
 *
 * @author Sergey Vorobyev
 */
public class StringEventWriter implements EventWriter {

  private PrintWriter out;

  public void setOutputStream(OutputStream outputStream) {
    out = new PrintWriter(outputStream, true);
  }

  public void writeEvent(EventType type, long tid, long pc, long address, long extra) {
    out.println(type + " " + tid + " " + pc + " " + address + " " + extra);
  }

  public void writeCodePosition(long pc, String descr) {
    out.println("#PC " + pc + " java " + descr);
  }

  public void writeComment(String str, long pc) {
    out.println("#> " + str);
  }
}
