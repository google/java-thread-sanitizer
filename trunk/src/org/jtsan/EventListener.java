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

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Performs actions on intercepted events.
 * Handlers signature:
 *  - for reqistered before:
 *    [invokedObject, methodParams ..., pc]
 *  - for registered after:
 *    [invokedObject, methodParams, returnValue, pc]
 *  - for exceptions:
 *    [invokedObject, throwable, pc]
 *    TODO(vors): add params to exception handlers
 *
 * @author Egor Pasko
 */
public class EventListener {

  private static final long MONITOR_C = 42L;

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

  public static void afterCall(long pc) {
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
    long uniqueId = ((long)System.identityHashCode(obj) << 32L) +
        (long) System.identityHashCode(fieldName.intern());
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
    // TODO(vors): make uniqueId a 64 bit value: higher half to be class ID, lower
    // half to be field ID.
    reportFieldAccess(isWrite,
                      tid(),
                      pc,
                      System.identityHashCode(fieldName.intern()),
                      isVolatile);
  }

  public static void monitorEnter(Object obj, long pc) {
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, calcMonitorId(obj), 0);
  }

  public static void monitorExit(Object obj, long pc) {
    writer.writeEvent(EventType.UNLOCK, tid(), pc, calcMonitorId(obj), 0);
  }

  public static void arrayAccess(Object array, int index, boolean isWrite, long pc) {
    reportFieldAccess(isWrite,
                      tid(),
                      pc,
                      calcArrayId(array, index),
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
    writer.writeEvent(EventType.UNLOCK, tid(), pc, calcMonitorId(obj), 0);
  }

  public static void jlObjectWaitAfter(Object obj, long pc) {
    writer.writeEvent(EventType.WAIT, tid(), pc, calcMonitorId(obj), 0);
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, calcMonitorId(obj), 0);
  }

  public static void jlObjectNotify(Object obj, long pc) {
    writer.writeEvent(EventType.SIGNAL, tid(), pc, calcMonitorId(obj), 0);
  }

  public static void jlObjectNotifyAll(Object obj, long pc) {
    writer.writeEvent(EventType.SIGNAL, tid(), pc, calcMonitorId(obj), 0);
  }

  public static void jlSystemArrayCopy(
      Object src, int srcPos, Object dest, int destPos, int length, long pc) {
    // TODO(vors): We handle NullPointerException, IndexOutOfBoundsException,
    // ArrayStoreException case 1 (see javadoc), but don't handle
    // ArrayStoreException case 2 (see javadoc), when part of values are stored and
    // part are discarded (It's very tricky case).
    for (int i = 0; i < length; i++) {
      long srcId = ((long)System.identityHashCode(src) << 32L) + (long)(i + srcPos);
            reportFieldAccess(false,   // isWrite
                              tid(),
                              pc,
                              srcId,
                              false); // isVolatile
      long destId = ((long)System.identityHashCode(dest) << 32L) + (long)(i + destPos);
            reportFieldAccess(true,   // isWrite
                              tid(),
                              pc,
                              destId,
                              false); // isVolatile
    }
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

  // java.util.concurrent.locks.AbstractQueuedSynchronizer

  public static void juclAQS_releaseShared(AbstractQueuedSynchronizer owner, int arg, long pc) {
    writer.writeEvent(EventType.SIGNAL, tid(), pc, System.identityHashCode(owner), 0);
  }

  public static void juclAQS_acquireSharedInterruptibly(AbstractQueuedSynchronizer owner,
                                                        int arg, long pc){
    writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(owner), 0);
  }

  public static void juclAQS_acquireShared(AbstractQueuedSynchronizer owner, int arg, long pc){
    writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(owner), 0);
  }

  public static void juclAQS_tryAcquireSharedNanos(AbstractQueuedSynchronizer owner,
                                                   int arg, long nanos, boolean result, long pc) {
    if (result) {
      writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(owner), 0);
    }
  }
    
  // java.util.concurrent.locks.ReentrantReadWriteLock

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

  // java.util.concurrent.locks.Lock

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

  public static void juclLock_lockInterruptibly(Lock lock, long pc) {
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(lock), 0);
  }

  // java.util.concurrent.locks.ReadLock & java.util.concurrent.locks.WriteLock

  public static void juclReadLockConstructor(ReentrantReadWriteLock.ReadLock readLock,
      ReentrantReadWriteLock outerLock, long pc) {
    readLockMap.put(readLock, outerLock);
  }

  public static void juclWriteLockConstructor(ReentrantReadWriteLock.WriteLock writeLock,
      ReentrantReadWriteLock outerLock, long pc) {
    writeLockMap.put(writeLock, outerLock);
  }

  // java.util.concurrent.Condition

  public static void juclCondition_awaitBefore(Condition condition, long pc) {
    writer.writeEvent(EventType.UNLOCK, tid(), pc,
                      System.identityHashCode(conditionMap.get(condition)), 0);
  }

  public static void juclCondition_awaitAfter(Condition condition, long pc) {
    Lock lock = conditionMap.get(condition);
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(lock), 0);
    writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(lock), 0);
  }

  public static void juclCondition_await2Before(Condition condition, long time,
                                                TimeUnit unit, boolean returned, long pc) {
    writer.writeEvent(EventType.UNLOCK, tid(), pc,
                      System.identityHashCode(conditionMap.get(condition)), 0);
  }

  public static void juclCondition_await2After(Condition condition, long time,
                                                TimeUnit unit, boolean returned, long pc) {
    Lock lock = conditionMap.get(condition);
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(lock), 0);
    writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(lock), 0);
  }

  public static void juclCondition_awaitNanosBefore(Condition condition,
                                                    long nanosTimeout, long nanos, long pc) {
    writer.writeEvent(EventType.UNLOCK, tid(), pc,
                      System.identityHashCode(conditionMap.get(condition)), 0);
  }

  public static void juclCondition_awaitNanosAfter(Condition condition,
                                                   long nanosTimeout, long nanos, long pc) {
    Lock lock = conditionMap.get(condition);
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(lock), 0);
    writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(lock), 0);
  }

  public static void juclCondition_awaitUninterruptiblyBefore(Condition condition, long pc) {
    writer.writeEvent(EventType.UNLOCK, tid(), pc,
                      System.identityHashCode(conditionMap.get(condition)), 0);
  }

  public static void juclCondition_awaitUninterruptiblyAfter(Condition condition, long pc) {
    Lock lock = conditionMap.get(condition);
    writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(lock), 0);
    writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(lock), 0);
  }

  public static void juclCondition_awaitUntilBefore(Condition condition,
                                                    Date deadline, boolean returner, long pc) {
    writer.writeEvent(EventType.UNLOCK, tid(), pc,
                      System.identityHashCode(conditionMap.get(condition)), 0);
  }

  public static void juclCondition_awaitUntilAfter(Condition condition,
                                                   Date deadline, boolean returner, long pc) {
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

  public static void juclCondition_awaitException(Condition c, Throwable e, long pc) {
    if (e instanceof InterruptedException) {
      Lock lock = conditionMap.get(c);
      writer.writeEvent(EventType.WRITER_LOCK, tid(), pc, System.identityHashCode(lock), 0);
      writer.writeEvent(EventType.WAIT, tid(), pc, System.identityHashCode(lock), 0);
    }
  }

  // java.util.concurrent.locks.LockSupport

  public static void juclLockSupport_park(long pc) {
    writer.writeEvent(EventType.WAIT, tid(), pc,
                      System.identityHashCode(Thread.currentThread()), 0);
  }

  public static void juclLockSupport_park2(Object blocker, long pc) {
    writer.writeEvent(EventType.WAIT, tid(), pc,
                      System.identityHashCode(Thread.currentThread()), 0);
  }

  public static void juclLockSupport_parkNanos(long nanos, long pc) {
    writer.writeEvent(EventType.WAIT, tid(), pc,
                      System.identityHashCode(Thread.currentThread()), 0);
  }

  public static void juclLockSupport_parkNanos2(Object blocker, long nanos, long pc) {
    writer.writeEvent(EventType.WAIT, tid(), pc,
                      System.identityHashCode(Thread.currentThread()), 0);
  }

  public static void juclLockSupport_parkUntil(long deadline, long pc) {
    writer.writeEvent(EventType.WAIT, tid(), pc,
                      System.identityHashCode(Thread.currentThread()), 0);
  }

  public static void juclLockSupport_parkUntil2(Object blocker, long deadline, long pc) {
    writer.writeEvent(EventType.WAIT, tid(), pc,
                      System.identityHashCode(Thread.currentThread()), 0);
  }

  public static void juclLockSupport_unpark(Thread thread, long pc) {
    writer.writeEvent(EventType.SIGNAL, tid(), pc,
                      System.identityHashCode(thread), 0);
  }
  
  // Private methods.

  private static long calcMonitorId(Object obj) {
    // Use low 32bit for object hash and high 32bit for the magic constant.
    return (MONITOR_C << 32L) + (long)System.identityHashCode(obj);
  }

  private static long calcArrayId(Object array, int index) {
    return ((long)System.identityHashCode(array) << 32L) + (long)index;
  }

  public static void popAndPrint(Object o) {
    System.out.println("POP AND PRINT: " + o);
  }

}
