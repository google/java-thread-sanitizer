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
 *
 * @author: Sergey Vorobyev
 */

public class BinToStrDecoder {

  /**
   * Usage: If exist first arg, use it as name of input binary-events file.
   * Otherwise use standard input.
   * If exist second arg, use it as name of output string-events file.
   * Otherwise use standard output.
   */

  private static InputStream in;
  private static PrintWriter out;

  private static byte[] byteType = new byte[BinaryEventWriter.TYPE_BYTES];
  private static byte[] bytePC = new byte[BinaryEventWriter.PC_BYTES];
  private static byte[] byteStrSize = new byte[BinaryEventWriter.STRING_SIZE_BYTES];
  private static byte[] byteTid = new byte[BinaryEventWriter.TID_BYTES];
  private static byte[] byteAddress = new byte[BinaryEventWriter.ADDRESS_BYTES];
  private static byte[] byteExtra = new byte[BinaryEventWriter.EXTRA_BYTES];

  public static void main(String[] args) throws IOException {

    if (args.length > 0) {
      in = new BufferedInputStream(new FileInputStream(args[0]));
    } else {
      in = new BufferedInputStream(System.in);
    }

    if (args.length > 1) {
      out = new PrintWriter(new FileWriter(args[1]), true);
    } else {
      out = new PrintWriter(System.out, true);
    }

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

  }

  private static long getLong(byte[] src) {
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

  private static void processCodePosition() throws IOException {
    assert (in.read(bytePC) == BinaryEventWriter.PC_BYTES);
    long pc = getLong(bytePC);
    assert (in.read(byteStrSize) == BinaryEventWriter.STRING_SIZE_BYTES);
    int length = (int) getLong(byteStrSize);
    byte[] buf = new byte[length];
    assert (in.read(buf) == length);
    String descr = new String(buf);
    out.println("#PC " + pc + " java " + descr);
  }

  private static void processComment() throws IOException {
    assert (in.read(byteStrSize) == BinaryEventWriter.STRING_SIZE_BYTES);
    int length = (int) getLong(byteStrSize);
    byte[] buf = new byte[length];
    assert (in.read(buf) == length);
    String str = new String(buf);
    out.println("#> " + str);
  }

  private static void processEvent(EventType type) throws IOException {
    assert (in.read(byteTid) == BinaryEventWriter.TID_BYTES);
    long tid = getLong(byteTid);
    assert (in.read(bytePC) == BinaryEventWriter.PC_BYTES);
    long pc = getLong(bytePC);
    assert (in.read(byteAddress) == BinaryEventWriter.ADDRESS_BYTES);
    long address = getLong(byteAddress);
    assert (in.read(byteExtra) == BinaryEventWriter.EXTRA_BYTES);
    long extra = getLong(byteExtra);
    out.println(type + " " + tid + " " + pc + " " + address + " " + extra);
  }

}
