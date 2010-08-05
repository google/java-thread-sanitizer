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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

/**
 * Executed class, convert binary events output to string format.
 * <p/>
 * Usage: If exist first arg, use it as name of input binary-events file.
 * Otherwise use standard input.
 * If exist second arg, use it as name of output string-events file.
 * Otherwise use standard output.
 *
 * @author: Sergey Vorobyev
 */

public class BinaryEventDecoder {

  public void setIn(InputStream in) {
    this.in = in;
  }

  public void setOut(PrintWriter out) {
    this.out = out;
  }

  private InputStream in;
  private PrintWriter out;

  private byte[] byteType = new byte[BinaryEventWriter.TYPE_BYTES];
  private byte[] bytePC = new byte[BinaryEventWriter.PC_BYTES];
  private byte[] byteStrSize = new byte[BinaryEventWriter.STRING_SIZE_BYTES];
  private byte[] byteTid = new byte[BinaryEventWriter.TID_BYTES];
  private byte[] byteAddress = new byte[BinaryEventWriter.ADDRESS_BYTES];
  private byte[] byteExtra = new byte[BinaryEventWriter.EXTRA_BYTES];

  public static void main(String[] args) throws IOException {

    BinaryEventDecoder decoder = new BinaryEventDecoder();

    if (args.length > 0) {
      decoder.setIn(new BufferedInputStream(new FileInputStream(args[0])));
    } else {
      decoder.setIn(new BufferedInputStream(System.in));
    }

    if (args.length > 1) {
      decoder.setOut(new PrintWriter(new FileWriter(args[1]), true));
    } else {
      decoder.setOut(new PrintWriter(System.out, true));
    }

  }


  public void decode() {
    try {
      while (in.read(byteType) != -1) {
        EventType type = EventType.values()[(int) getLong(byteType)];
        switch (type) {
          case CODE_POSITION:
            processCodePosition();
            break;
          case COMMENT:
            processComment();
            break;
          default:
            processEvent(type);
            break;
        }
      }
      out.close();
    } catch (IOException e) {
      throw new RuntimeException("Error during reading events", e);
    }
  }

  private long getLong(byte[] src) {
    long res = 0;
    for (int i = 0; i < src.length; i++) {
      res <<= 8;
      if (src[i] < 0) {
        res += (1 << 8) + src[i];
      } else {
        res += src[i];
      }
    }
    return res;
  }

  private void check(boolean b) {
    if (!b) {
      throw new RuntimeException(new AssertionError("Assert"));
    }
  }

  private void processCodePosition() throws IOException {
    check(in.read(bytePC) == BinaryEventWriter.PC_BYTES);
    long pc = getLong(bytePC);
    check(in.read(byteStrSize) == BinaryEventWriter.STRING_SIZE_BYTES);
    int length = (int) getLong(byteStrSize);
    byte[] buf = new byte[length];
    check(in.read(buf) == length);
    String descr = new String(buf);
    out.println("#PC " + pc + " java " + descr);
  }

  private void processComment() throws IOException {
    check(in.read(byteStrSize) == BinaryEventWriter.STRING_SIZE_BYTES);
    int length = (int) getLong(byteStrSize);
    byte[] buf = new byte[length];
    check(in.read(buf) == length);
    String str = new String(buf);
    out.println("#> " + str);
  }

  private void processEvent(EventType type) throws IOException {
    check(in.read(byteTid) == BinaryEventWriter.TID_BYTES);
    long tid = getLong(byteTid);
    check(in.read(bytePC) == BinaryEventWriter.PC_BYTES);
    long pc = getLong(bytePC);
    check(in.read(byteAddress) == BinaryEventWriter.ADDRESS_BYTES);
    long address = getLong(byteAddress);
    check(in.read(byteExtra) == BinaryEventWriter.EXTRA_BYTES);
    long extra = getLong(byteExtra);
    out.println(type + " " + tid + " " + pc + " " + address + " " + extra);
  }

}
