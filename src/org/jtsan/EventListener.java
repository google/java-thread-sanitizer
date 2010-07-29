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

import org.jtsan.writers.EventWriter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Performs actions on intercepted events.
 *
 * @author Egor Pasko
 */
public class EventListener {
  private static EventWriter writer;

  // Typedef replacement.
  static class ReadLockMap extends
      ConcurrentHashMap<ReentrantReadWriteLock.ReadLock, ReentrantReadWriteLock> {
    public ReadLockMap() { super(10); }
    private static final long serialVersionUID = 0L; // Avoid javac warning.
  }

  // Typedef replacement.
  static class WriteLockMap extends
      ConcurrentHashMap<ReentrantReadWriteLock.WriteLock, ReentrantReadWriteLock> {
    public WriteLockMap() { super(10); }
    private static final long serialVersionUID = 0L; // Avoid javac warning.
  }

  // Typedef replacement.
  static class ConditionMap extends
      ConcurrentHashMap<Condition, Lock> {
    public ConditionMap() { super(10); }
    private static final long serialVersionUID = 0L; // Avoid javac warning.
  }

  private static ReadLockMap readLockMap = new ReadLockMap();

  private static WriteLockMap writeLockMap = new WriteLockMap();

  private static ConditionMap conditionMap = new ConditionMap();

  public static void setEventWriter(EventWriter w) {
    writer = w;
  }

  public static long tid() {
    return Thread.currentThread().getId() - 1;
  }

  public static void codePosition(long pc, String descr) {
    writer.writeCodePosition(pc, descr);
  }

  public static void threadsInit() {
    writer.writeEvent(EventType.THR_START, 0, 0, 0, 0);
    writer.writeEvent(EventType.THR_FIRST_INSN, 0, 0, 0, 0);
  }
  
  // MethodTransformer hooks.
  public static void beforeCall(long pc) {
    writer.writeEvent(EventType.SBLOCK_ENTER, tid(), pc, 0, 0);
  }

  public static void methodEnter(long pc) {
    writer.writeEvent(EventType.RTN_CALL, tid(), 0, 0, 0);
    writer.writeEvent(EventType.SBLOCK_ENTER, tid(), pc, 0, 0);
  }

  public static void methodExit(long pc) {
    writer.writeEvent(EventType.RTN_EXIT, tid(), pc, 0, 0);
  }

  public static void runMethodEnter(Object obj, long pc) {
    // Calls to this methods are emitted by the instrumentation process.
  }

  public static void runMethodExit(Object obj, long pc) {
    // Calls to this methods are emitted by the instrumentation process.
  }

  public static void reportFieldAccess(
      boolean isWrite, long tid, long pc, long id, boolean isVolatile) {
    if (isVolatile) {
      writer.writeEvent(isWrite ? EventType.SIGNAL : EventType.WAIT, tid, pc, id, 0);
    } else {
      writer.writeEvent(isWrite ? EventType.WRITE : EventType.READ, tid, pc, id, 1);
    }
  }

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
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(obj), 0);
  }

  public static void monitorExit(Object obj, long pc) {
    writer.writeEvent(EventType.UNLOCK, tid(), pc, System.identityHashCode(obj), 0);
  }

  public static void arrayAccess(Object array, int index, boolean isWrite, long pc) {
    // TODO(egor): improve uniqueness for 64bit offline detector.
    int id = System.identityHashCode(array) + index;
    reportFieldAccess(isWrite,
                      tid(),
                      pc,
                      id,
                      false); // isVolatile
  }

  // Race detector API hooks.
  public static void rdaApiNoOp(Object obj, long pc) {
    // writer.writeEvent("T" + tid() + " API_NO_OP " + pc);
  }

  public static void rdaApiExpectRaceBegin(long pc) {
    writer.writeEvent(EventType.EXPECT_RACE_BEGIN, tid(), pc, 0, 0);
  }

  public static void rdaApiExpectRaceEnd(long pc) {
    writer.writeEvent(EventType.EXPECT_RACE_END, tid(), pc, 0, 0);
  }

  public static void rdaApiPrintStackTrace(long pc) {
    writer.writeEvent(EventType.STACK_TRACE, tid(), pc, 0, 0);
  }

  public static void rdaApiPrint(String str, long pc) {
    writer.writeComment(str, pc);
  }

  // Interceptors hooks.

  public static void jlObjectWait(Object obj, long pc) {
    writer.writeEvent(EventType.UNLOCK, tid(), pc, System.identityHashCode(obj), 0);
  }

  public static void jlObjectWaitAfter(Object obj, long pc) {
    writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(obj), 0);
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(obj), 0);
  }

  public static void jlObjectNotify(Object obj, long pc) {
    writer.writeEvent(EventType.SIGNAL, tid(), pc, System.identityHashCode(obj), 0);
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

  public static void jlThreadStart(Thread thr, long pc) {
    long parent_tid = tid();
    long child_tid = thr.getId() - 1;
    writer.writeEvent(EventType.THR_START, child_tid, pc, 0, parent_tid);
    writer.writeEvent(EventType.THR_FIRST_INSN, child_tid, pc, 0, 0);
  }

  public static void jlThreadJoin(Thread thr, long pc) {
    long parent_tid = tid();
    long child_tid = thr.getId() - 1;
    writer.writeEvent(EventType.THR_END, child_tid, pc, 0, 0);
    writer.writeEvent(EventType.THR_JOIN_AFTER, parent_tid, pc, child_tid, 0);
  }

  public static void jucCountDownLatch_countDown(CountDownLatch latch, long pc){
    writer.writeEvent(EventType.SIGNAL, tid(), pc, System.identityHashCode(latch), 0);
  }

  public static void jucCountDownLatch_await(CountDownLatch latch, long pc){
    writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(latch), 0);
  }

  public static void jucSemaphore_release(Semaphore sem, long pc){
    writer.writeEvent(EventType.SIGNAL, tid(), pc, System.identityHashCode(sem), 0);
  }

  public static void jucSemaphore_acquire(Semaphore sem, long pc){
    writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(sem), 0);
  }

  public static void jucRRWL_ReadLock_lock(ReentrantReadWriteLock.ReadLock lock, long pc){
    writer.writeEvent(EventType.READER_LOCK, tid(), pc,
                      System.identityHashCode(readLockMap.get(lock)), 0);
  }

  public static void jucRRWL_ReadLock_tryLock(
      ReentrantReadWriteLock.ReadLock lock, boolean succeeded, long pc){
    if (succeeded) {
      writer.writeEvent(EventType.READER_LOCK, tid(), pc,
                        System.identityHashCode(readLockMap.get(lock)), 0);
    }
  }

  public static void jucRRWL_ReadLock_tryLock2(ReentrantReadWriteLock.ReadLock lock,
                                               long unued1,
                                               TimeUnit unused2,
                                               boolean succeeded, long pc){
    if (succeeded) {
      writer.writeEvent(EventType.READER_LOCK, tid(), pc,
                        System.identityHashCode(readLockMap.get(lock)), 0);
    }
  }

  public static void jucRRWL_ReadLock_unlock(ReentrantReadWriteLock.ReadLock lock, long pc){
    writer.writeEvent(EventType.UNLOCK, tid(), pc,
                      System.identityHashCode(readLockMap.get(lock)), 0);
  }

  public static void jucRRWL_WriteLock_lock(ReentrantReadWriteLock.WriteLock lock, long pc){
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc,
                      System.identityHashCode(writeLockMap.get(lock)), 0);
  }

  public static void jucRRWL_WriteLock_tryLock(
      ReentrantReadWriteLock.WriteLock lock, boolean succeeded, long pc){
    if (succeeded) {
      writer.writeEvent(EventType.WRITER_LOCK, tid(), pc,
                        System.identityHashCode(writeLockMap.get(lock)), 0);
    }
  }

  public static void jucRRWL_WriteLock_tryLock2(ReentrantReadWriteLock.WriteLock lock,
                                                long unused1,
                                                TimeUnit unused2,
                                                boolean succeeded, long pc){
    if (succeeded) {
      writer.writeEvent(EventType.WRITER_LOCK, tid(), pc,
                        System.identityHashCode(writeLockMap.get(lock)), 0);
    }
  }

  public static void jucRRWL_WriteLock_unlock(ReentrantReadWriteLock.WriteLock lock, long pc){
    writer.writeEvent(EventType.UNLOCK, tid(), pc,
                      System.identityHashCode(writeLockMap.get(lock)), 0);
  }

  public static void jucRL_lock(ReentrantLock lock, long pc){
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(lock), 0);
  }

  public static void jucRL_tryLock(ReentrantLock lock, boolean returned, long pc) {
    if (returned) {
      writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(lock), 0);
    }
  }

  public static void jucRL_tryLock2(
      ReentrantLock lock, long timeout, TimeUnit unit, boolean returned, long pc) {
    if (returned) {
      writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(lock), 0);
    }
  }

  public static void jucRL_unlock(ReentrantLock lock, long pc){
    writer.writeEvent(EventType.UNLOCK, tid(), pc, System.identityHashCode(lock), 0);
  }

  public static void juclReadLockConstructor(ReentrantReadWriteLock.ReadLock readLock,
      ReentrantReadWriteLock outerLock, long pc) {
    readLockMap.put(readLock, outerLock);
  }

  public static void juclWriteLockConstructor(ReentrantReadWriteLock.WriteLock writeLock,
      ReentrantReadWriteLock outerLock, long pc) {
    writeLockMap.put(writeLock, outerLock);
  }
  public static void juclCondition_awaitBefore(Condition condition, long pc) {
    writer.writeEvent(EventType.UNLOCK, tid(), pc,
                      System.identityHashCode(conditionMap.get(condition)), 0);
  }

  public static void juclCondition_awaitAfter(Condition condition, long pc) {
    Lock lock = conditionMap.get(condition);
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(lock), 0);
    writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(lock), 0);
  }

  public static void juclLock_newCondition(Lock lock, Condition condition, long pc) {
    conditionMap.put(condition, lock);
  }

  public static void juclCondition_signalAll(Condition condition, long pc) {
    writer.writeEvent(EventType.SIGNAL, tid(), pc,
                      System.identityHashCode(conditionMap.get(condition)), 0);
  }

  public static void juclCondition_signal(Condition condition, long pc) {
    writer.writeEvent(EventType.SIGNAL, tid(), pc,
                      System.identityHashCode(conditionMap.get(condition)), 0);
  }
}
