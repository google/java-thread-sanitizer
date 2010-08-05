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
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    this.in = new DataInputStream(new BufferedInputStream(in));
  }

  public void setOut(OutputStream out) {
    this.out = new PrintWriter(out, true);
  }

  private DataInputStream in;
  private PrintWriter out;

  public static void main(String[] args) throws IOException {

    BinaryEventDecoder decoder = new BinaryEventDecoder();

    if (args.length > 0) {
      decoder.setIn(new FileInputStream(args[0]));
    } else {
      decoder.setIn(System.in);
    }

    if (args.length > 1) {
      decoder.setOut(new FileOutputStream(args[1]));
    } else {
      decoder.setOut(System.out);
    }
    decoder.decode();
  }


  public void decode() {
    try {
      while (true) {

        int typeOrd = in.readUnsignedByte();
        final EventType type = EventType.values()[typeOrd];

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

    } catch (EOFException e) {
      System.err.println("Read EOF");
      e.printStackTrace();
    } catch (IOException e) {
      throw new RuntimeException("Error during reading events", e);
    } finally {
      out.close();
    }
  }


  private void processCodePosition() throws IOException {
    int pc = in.readInt();
    String descr = in.readUTF();
    out.println("#PC " + pc + " java " + descr);
  }

  private void processComment() throws IOException {
    String str = in.readUTF();
    out.println("#> " + str);
  }

  private void processEvent(EventType type) throws IOException {
    int tid = 0;
    int pc = 0;
    long address = 0;
    int extra = 0;

    switch (type) {
      case THR_START:
        extra = in.readUnsignedShort();
      case READ:
      case READER_LOCK:
      case SIGNAL:
      case THR_JOIN_AFTER:
      case UNLOCK:
      case WAIT:
      case WRITE:
      case WRITER_LOCK:
        address = in.readLong();
      case EXPECT_RACE_BEGIN:
      case EXPECT_RACE_END:
      case RTN_EXIT:
      case SBLOCK_ENTER:
      case STACK_TRACE:
      case THR_END:
      case THR_FIRST_INSN:
        pc = in.readInt();
      case RTN_CALL:
        tid = in.readUnsignedShort();
    }
    if (type == EventType.READ || type == EventType.WRITE) {
      extra = 1;
    }

    out.println(type + " " + tid + " " + pc + " " + address + " " + extra);
  }

}
