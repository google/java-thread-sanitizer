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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Convert binary events output to string format.
 * Usage:
 * java -cp /path/to/agent.jar org.jtsan.writers.BinaryEventDecoder [Input file] [Output file]
 *
 * @author Sergey Vorobyev
 */

public class BinaryEventDecoder {

  private final DataInputStream in;
  private final PrintWriter out;

  public BinaryEventDecoder(InputStream in, OutputStream out) {
    this.in = new DataInputStream(new BufferedInputStream(in));
    this.out = new PrintWriter(out, false /* auto-flush */);
  }

  public static void main(String[] args) {
    InputStream in;
    OutputStream out;

    try {
      if (args.length > 0) {
        in = new FileInputStream(args[0]);
      } else {
        in = System.in;
      }

      if (args.length > 1) {
        out = new FileOutputStream(args[1]);
      } else {
        out = System.out;
      }
    } catch (IOException e) {
      System.err.println("Error: " + BinaryEventDecoder.class +
          ": Could not open input or output stream.");
      System.err.println("Stack trace:");
      e.printStackTrace(System.err);
      System.err.println("Usage: java -cp /path/to/agent.jar " +
          "org.jtsan.writers.BinaryEventDecoder [Input file] [Output file]");
      return;
    }

    BinaryEventDecoder decoder = new BinaryEventDecoder(in, out);
    decoder.decode();
  }


  public void decode() {
    long count = 0;
    try {
      while (true) {
        int typeOrd = in.readUnsignedByte();
        final EventType type = EventType.values()[typeOrd];
        switch (type) {
          case PC_DESCRIPTION:
            processCodePosition();
            break;
          case PRINT_MESSAGE:
            processComment();
            break;
          default:
            processEvent(type);
            break;
        }
        count++;
      }
    } catch (EOFException e) {
      System.err.println("INFO: " + count + " lines decoded.");
    } catch (IOException e) {
      throw new RuntimeException("IO error happened while decoding.", e);
    } finally {
      out.close();
    }
  }

  private void processCodePosition() throws IOException {
    int pc = in.readInt();
    String descr = in.readUTF();
    out.println("#PC " + Integer.toHexString(pc) + " java " + descr);
  }

  private void processComment() throws IOException {
    String str = in.readUTF();
    out.println("#> " + str);
  }
  
  @SuppressWarnings("fallthrough")
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

    out.println(type + " " + Integer.toHexString(tid) + " " + Integer.toHexString(pc) + " "
        + Long.toHexString(address) + " " + Integer.toHexString(extra));
  }

}
