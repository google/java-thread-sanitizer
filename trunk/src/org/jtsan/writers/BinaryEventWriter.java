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

  private static class WritePos {
    public int getPos() {
      return pos;
    }

    public void inc(int x) {
      this.pos += x;
    }

    private int pos;

  }

  public void setOutputStream(OutputStream outputStream) {
    out = outputStream;
  }

  public void writeEvent(EventType type, long tid, long pc, long address, long extra) {
    byte[] b = new byte[TYPE_BYTES + TID_BYTES + PC_BYTES + ADDRESS_BYTES + EXTRA_BYTES];
    WritePos pos = new WritePos();
    long[] src = new long[]{type.ordinal(), tid, pc, address, extra};
    int[] sizes = new int[]{TYPE_BYTES, TID_BYTES, PC_BYTES, ADDRESS_BYTES, EXTRA_BYTES};
    for (int i = 0; i < src.length; i++) {
      setBytes(src[i], b, pos, sizes[i]);
    }
    synchronized (this) {
      try {
        out.write(b);
      } catch (IOException e) {
        e.printStackTrace(System.err);
      }
    }
  }

  public void writeCodePosition(long pc, String descr) {
    byte[] str = descr.getBytes();
    byte[] b = new byte[TYPE_BYTES + PC_BYTES + STRING_SIZE_BYTES + str.length];
    WritePos pos = new WritePos();
    long[] src = new long[]{EventType.CODE_POSITION.ordinal(), pc, str.length};
    int[] sizes = new int[]{TYPE_BYTES, PC_BYTES, STRING_SIZE_BYTES};
    for (int i = 0; i < src.length; i++) {
      setBytes(src[i], b, pos, sizes[i]);
    }
    System.arraycopy(str, 0, b, pos.getPos(), str.length);
    synchronized (this) {
      try {
        out.write(b);
      } catch (IOException e) {
        e.printStackTrace(System.err);
      }
    }
  }

  public void writeComment(String descr, long pc) {
    byte[] str = descr.getBytes();
    byte[] b = new byte[TYPE_BYTES + STRING_SIZE_BYTES + str.length];
    WritePos pos = new WritePos();
    long[] src = new long[]{EventType.COMMENT.ordinal(), str.length};
    int[] sizes = new int[]{TYPE_BYTES, STRING_SIZE_BYTES};
    for (int i = 0; i < src.length; i++) {
      setBytes(src[i], b, pos, sizes[i]);
    }
    System.arraycopy(str, 0, b, pos.getPos(), str.length);
    synchronized (this) {
      try {
        out.write(b);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void setBytes(long src, byte[] des, WritePos pos, int size) {
    for (int i = 0; i < size; i++) {
      des[pos.getPos() + size - i - 1] = (byte) ((src & (0xFF << (i << 3))) >> (i << 3));
    }
    pos.inc(size);
  }

}
