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

import java.io.IOException;
import java.io.OutputStream;

/**
 * Write to OutputStream tsan events in binary format.
 *
 * @author Sergey Vorobyev
 */
public class BinaryEventWriter implements EventWriter {

  public static final int TYPE_BYTES = 1;
  public static final int TID_BYTES = 2;
  public static final int PC_BYTES = 2;
  public static final int ADDRESS_BYTES = 4;
  public static final int EXTRA_BYTES = 2;

  public static final int STRING_SIZE_BYTES = 2;

  private OutputStream out;

  public void setOutputStream(OutputStream outputStream) {
    out = outputStream;
  }

  public void writeEvent(EventType type, long tid, long pc, long address, long extra) {
    byte[] b = new byte[TYPE_BYTES + TID_BYTES + PC_BYTES + ADDRESS_BYTES + EXTRA_BYTES];
    int uk = 0;
    setBytes(type.ordinal(), b, uk, TYPE_BYTES);
    uk += TYPE_BYTES;
    setBytes(tid, b, uk, TID_BYTES);
    uk += TID_BYTES;
    setBytes(pc, b, uk, PC_BYTES);
    uk += PC_BYTES;
    setBytes(address, b, uk, ADDRESS_BYTES);
    uk += ADDRESS_BYTES;
    setBytes(extra, b, uk, EXTRA_BYTES);
    try {
      out.write(b);
    } catch (IOException e) {
      e.printStackTrace(System.err);
    }
  }

  public void writeCodePosition(long pc, String descr) {
    byte[] str = descr.getBytes();
    byte[] b = new byte[TYPE_BYTES + PC_BYTES + STRING_SIZE_BYTES + str.length];
    int uk = 0;
    setBytes(EventType.CODE_POSITION.ordinal(), b, uk, TYPE_BYTES);
    uk += TYPE_BYTES;
    setBytes(pc, b, uk, PC_BYTES);
    uk += PC_BYTES;
    setBytes(str.length, b, uk, STRING_SIZE_BYTES);
    uk += STRING_SIZE_BYTES;
    System.arraycopy(str, 0, b, uk, str.length);
    try {
      out.write(b);
    } catch (IOException e) {
      e.printStackTrace(System.err);
    }
  }

  public void writeComment(String descr, long pc) {
    byte[] str = descr.getBytes();
    byte[] b = new byte[TYPE_BYTES + STRING_SIZE_BYTES + str.length];
    int uk = 0;
    setBytes(EventType.COMMENT.ordinal(), b, uk, TYPE_BYTES);
    uk += TYPE_BYTES;
    setBytes(str.length, b, uk, STRING_SIZE_BYTES);
    uk += STRING_SIZE_BYTES;
    System.arraycopy(str, 0, b, uk, str.length);
    try {
      out.write(b);
    } catch (IOException e) {
      e.printStackTrace(System.err);
    }
  }

  private void setBytes(long src, byte[] des, int pos, int length) {
    for (int i = 0; i < length; i++) {
      // This line looks ugly but it's simple.
      des[pos + length - i - 1] = (byte) ((src & (0xFF << (i << 3))) >> (i << 3));
    }
  }

}
