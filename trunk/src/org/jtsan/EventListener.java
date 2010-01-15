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

import java.io.PrintWriter;

/**
 * Performs actions on intercepted events.
 *
 * @author Egor Pasko
 */
public class EventListener {
  public static PrintWriter out;

  public static void codePosition(long pc, String descr) {
    out.println("PC_DESCR " + pc + " " + descr);
  }

  public static void beforeCall(long pc) {
    out.println("RTN_CALL " + Thread.currentThread().getId() + " " + pc + " 0 0");
  }

  public static void methodEnter(long pc) {
    out.println("RTN_ENTER " + Thread.currentThread().getId() + " " + pc + " 0 0");
  }

  public static void methodExit(long pc) {
    out.println("RTN_EXIT " + Thread.currentThread().getId() + " " + pc + " 0 0");
  }

  public static void runMethodExit(Object obj) {
    if (obj instanceof Thread) {
      out.println("THR_END " + Thread.currentThread().getId() + " " + "pc" + " 0 0");
    }
  }

  public static void reportFieldAccess(boolean isWrite, long tid, long pc, int id) {
    String acc = isWrite ? "WRITE " : "READ ";
    String strTid = Long.toString(tid);
    out.println(acc + strTid + " " + pc + " " + id + " 1");
  }

  // TODO: deprecate fieldName.
  public static void objectFieldAccess(Object obj, boolean isWrite,
      String fieldName, long pc) {
    reportFieldAccess(isWrite,
                      Thread.currentThread().getId(),
                      pc,
                      System.identityHashCode(obj));
  }

  public static void staticFieldAccess(String fieldName, boolean isWrite, long pc) {
    // Instead of taking 'unique' id of the class, take the id of the string representing it.
    // This is very dirty.
    reportFieldAccess(isWrite,
                      Thread.currentThread().getId(),
                      pc,
                      System.identityHashCode(fieldName.intern()));
  }

  public static void monitorEnter(Object obj) {
    out.println("LOCK " + Thread.currentThread().getId() + " " + "pc" + " " +
        System.identityHashCode(obj) + " 0");
  }

  public static void monitorExit(Object obj) {
    out.println("UNLOCK " + Thread.currentThread().getId() + " " + "pc" + " " +
        System.identityHashCode(obj) + " 0");
  }

  public static void arrayLoad(Object array, int index) {
    //out.println("arrayLoad idx:  " + index + ", hash: " + System.identityHashCode(array));
  }

  public static void arrayStore(Object array, int index) {
    //out.println("arrayStore idx: " + index + ", hash: " + System.identityHashCode(array));
  }

  public static void jlObjectWait(Object obj) {
    out.println("WAIT "  + Thread.currentThread().getId() + " " + "pc" + " " +
        System.identityHashCode(obj) + " 0");
  }

  public static void jlObjectNotify(Object obj) {
    out.println("SIGNAL " + Thread.currentThread().getId() + " " + "pc" + " " +
        System.identityHashCode(obj) + " 0");
  }

  public static void jlSystemArrayCopy(
      Object src, int srcPos, Object dest, int destPos, int length) {
    /*
    out.printf("System.arraycopy(src %d, srcPos %d, dest %d, destPos %d, length %d)\n",
                      System.identityHashCode(src),
                      srcPos,
                      System.identityHashCode(dest),
                      destPos,
                      length);
                      */
  }

  public static void jlThreadStart(Thread thr) {
    long tid = Thread.currentThread().getId();
    out.println("THR_CREATE " + tid + " " + "pc" + " " + thr.getId() + " 0");
    out.println("THR_START " + thr.getId() + " " + "pc" + " 0 0");
  }

  public static void jlThreadJoin(Thread thr) {
    out.println(
        "THR_JOIN " + Thread.currentThread().getId() + " pc " + thr.getId() + " 0");
  }
}
