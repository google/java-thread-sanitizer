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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * Performs actions on intercepted events.
 *
 * @author Egor Pasko
 */
public class EventListener {
  private static PrintWriter out;

  static class ReadLockMap extends
      ConcurrentHashMap<ReentrantReadWriteLock.ReadLock, ReentrantReadWriteLock> {
    public ReadLockMap() { super(10); }
  }

  static class WriteLockMap extends
      ConcurrentHashMap<ReentrantReadWriteLock.WriteLock, ReentrantReadWriteLock> {
    public WriteLockMap() { super(10); }
  }

  private static ReadLockMap readLockMap = new ReadLockMap();

  private static WriteLockMap writeLockMap = new WriteLockMap();

  public static void setPrinter(PrintWriter w) {
    out = w;
  }

  public static long tid() {
    return Thread.currentThread().getId() - 1;
  }

  static void signalOnObject(Object obj, long pc){
    out.println("SIGNAL " + tid() + " " + pc + " " +
        System.identityHashCode(obj) + " 0");
  }
  static void waitOnObject(Object obj, long pc){
    out.println("WAIT " + tid() + " " + pc + " " +
        System.identityHashCode(obj) + " 0");
  }

  static void writeLock(Object obj, long pc) {
    out.println("WRITER_LOCK " + tid() + " " + pc + " " +
        System.identityHashCode(obj) + " 0");
  }
  static void readLock(Object obj, long pc) {
    out.println("READER_LOCK " + tid() + " " + pc + " " +
        System.identityHashCode(obj) + " 0");
  }
  static void unlock(Object obj, long pc) {
    out.println("UNLOCK " + tid() + " " + pc + " " +
        System.identityHashCode(obj) + " 0");
  }

  public static void codePosition(long pc, String descr) {
    out.println("#PC " + pc + " java " + descr);
  }

  public static void beforeCall(long pc) {
    out.println("SBLOCK_ENTER " + tid() + " " + pc + " 0 0");
  }

  public static void methodEnter(long pc) {
    out.println("RTN_CALL " + tid() + " 0 0 0");
    out.println("SBLOCK_ENTER " + tid() + " " + pc + " 0 0");
  }

  public static void methodExit(long pc) {
    out.println("RTN_EXIT " + tid() + " " + pc + " 0 0");
  }

  public static void runMethodEnter(Object obj, long pc) {
    if (obj instanceof Thread) {
      Thread thr = (Thread) obj;
      long child_tid = thr.getId() - 1;
      //out.println("THR_START " + child_tid + " " + pc + " 0 0");
      //out.println("THR_FIRST_INSN " + child_tid + " " + pc + " 0 0");
    }
  }

  public static void runMethodExit(Object obj, long pc) {
    if (obj instanceof Thread) {
      // out.println("THR_END " + tid() + " " + pc + " 0 0");
    }
  }

  public static void reportFieldAccess(
      boolean isWrite, long tid, long pc, long id, boolean isVolatile) {
    String strTid = Long.toString(tid);
    if (isVolatile) {
      if (isWrite) {
        out.println("SIGNAL " + strTid + " " + pc + " " + id + " 0");
      } else {
        out.println("WAIT_BEFORE " + strTid + " " + pc + " " + id + " 0");
        out.println("WAIT_AFTER " + strTid + " " + pc + " " + "0 0");
      }
    } else {
      String acc = isWrite ? "WRITE " : "READ ";
      out.println(acc + strTid + " " + pc + " " + id + " 1");
    }
  }

  public static void ofa(Object obj) {}

  public static void objectFieldAccess(Object obj, boolean isWrite,
      String fieldName, long pc, boolean isVolatile) {
    // TODO: make uniqueId a 64 bit value: higher half to be object ID, lower
    // half to be field ID.
    int uniqueId = System.identityHashCode(obj) + System.identityHashCode(fieldName.intern());
    reportFieldAccess(isWrite,
                      tid(),
                      pc,
                      uniqueId,
                      isVolatile);
  }

  public static void staticFieldAccess(
      String fieldName, boolean isWrite, long pc, boolean isVolatile) {
    // Instead of taking 'unique' id of the class, take the id of the string representing it.
    // This is very dirty.
    reportFieldAccess(isWrite,
                      tid(),
                      pc,
                      System.identityHashCode(fieldName.intern()),
                      isVolatile);
  }

  public static void monitorEnter(Object obj, long pc) {
    writeLock(obj, pc);
  }

  public static void monitorExit(Object obj, long pc) {
    unlock(obj, pc);
  }

  public static void arrayLoad(Object array, int index) {
    //out.println("arrayLoad idx:  " + index + ", hash: " + System.identityHashCode(array));
  }

  public static void arrayStore(Object array, int index) {
    //out.println("arrayStore idx: " + index + ", hash: " + System.identityHashCode(array));
  }

  public static void jlObjectWait(Object obj, long pc) {
    out.println("WAIT_BEFORE "  + tid() + " " + pc + " " +
        System.identityHashCode(obj) + " 0");
  }

  public static void jlObjectWaitAfter(Object obj, long pc) {
    out.println("WAIT_AFTER "  + tid() + " " + pc + " 0 0");
  }

  public static void jlObjectNotify(Object obj, long pc) {
    signalOnObject(obj, pc);
  }

  public static void jlSystemArrayCopy(
      Object src, int srcPos, Object dest, int destPos, int length, long pc) {
    /*
    out.printf("System.arraycopy(src %d, srcPos %d, dest %d, destPos %d, length %d)\n",
                      System.identityHashCode(src),
                      srcPos,
                      System.identityHashCode(dest),
                      destPos,
                      length);
                      */
  }

  public static void threadsInit() {
    out.println("THR_START 0 0 0 0");
    out.println("THR_FIRST_INSN 0 0 0 0");
  }

  public static void jlThreadStart(Thread thr, long pc) {
    long parent_tid = tid();
    long child_tid = thr.getId() - 1;
    out.println("THR_START " + child_tid + " " + pc + " 0 " + parent_tid);
    out.println("THR_FIRST_INSN " + child_tid + " " + pc + " 0 0");
  }

  public static void jlThreadJoin(Thread thr, long pc) {
    long parent_tid = tid();
    long child_tid = thr.getId() - 1;
    out.println("THR_END " + child_tid + " " + pc + " 0 0");
    out.println("THR_JOIN_AFTER " + parent_tid + " " + pc + " " + child_tid + " 0");
  }

  public static void jucCountDownLatch_countDown(CountDownLatch latch, long pc){
    signalOnObject(latch, pc);
  }

  public static void jucCountDownLatch_await(CountDownLatch latch, long pc){
    waitOnObject(latch, pc);
  }

  public static void jucSemaphore_release(Semaphore sem, long pc){
    signalOnObject(sem, pc);
  }

  public static void jucSemaphore_acquire(Semaphore sem, long pc){
    waitOnObject(sem, pc);
  }

  public static void jucRRWL_ReadLock_lock(ReentrantReadWriteLock.ReadLock lock, long pc){
    readLock(readLockMap.get(lock), pc);
  }

  public static void jucRRWL_ReadLock_unlock(ReentrantReadWriteLock.ReadLock lock, long pc){
    unlock(readLockMap.get(lock), pc);
  }

  public static void jucRRWL_WriteLock_lock(ReentrantReadWriteLock.WriteLock lock, long pc){
    writeLock(writeLockMap.get(lock), pc);
  }

  public static void jucRRWL_WriteLock_unlock(ReentrantReadWriteLock.WriteLock lock, long pc){
    unlock(writeLockMap.get(lock), pc);
  }

  public static void jucRL_lock(ReentrantLock lock, long pc){
    writeLock(lock, pc);
  }

  public static void jucRL_tryLock(ReentrantLock lck, boolean returned, long pc) {
    if (returned) {
      writeLock(lck, pc);
    }
  }

  public static void jucRL_tryLock2(
      ReentrantLock lck, long timeout, TimeUnit unit, boolean returned, long pc) {
    if (returned) {
      writeLock(lck, pc);
    }
  }

  public static void jucRL_unlock(ReentrantLock lock, long pc){
    unlock(lock, pc);
  }

  public static void rdaApiNoOp(Object obj, long pc) {
    // out.println("T" + tid() + " API_NO_OP " + pc);
  }

  public static void rdaApiExpectRaceBegin(long pc) {
    out.println("EXPECT_RACE_BEGIN " + tid() + " " + pc + " 0 0");
  }

  public static void rdaApiExpectRaceEnd(long pc) {
    out.println("EXPECT_RACE_END " + tid() + " " + pc + " 0 0");
  }

  public static void rdaApiPrintStackTrace(long pc) {
    out.println("STACK_TRACE " + tid() + " " + pc + " 0 0");
  }

  public static void rdaApiPrint(String str, long pc) {
    out.println("#>" + str);
  }

  public static void juclReadLockConstructor(ReentrantReadWriteLock.ReadLock readLock,
      ReentrantReadWriteLock outerLock, long pc) {
    readLockMap.put(readLock, outerLock);
  }

  public static void juclWriteLockConstructor(ReentrantReadWriteLock.WriteLock writeLock,
      ReentrantReadWriteLock outerLock, long pc) {
    writeLockMap.put(writeLock, outerLock);
  }

}
