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


/**
 * Lists all mappings of methods to their interceptors in {@code EventListener}.
 *
 * @author Egor Pasko
 */
public class Interceptors {

  static void init(MethodMapping map) {
    map.registerAfter("java/lang/System", "arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V",
                       "jlSystemArrayCopy");
    map.registerBefore("java/lang/Object", "wait()V", "jlObjectWait");
    map.registerBefore("java/lang/Object", "notify()V", "jlObjectNotify");
    map.registerBefore("java/lang/Object", "notifyAll()V", "jlObjectNotifyAll");
    map.registerBefore("java/lang/Thread", "start()V", "jlThreadStart");
    map.registerAfter("java/lang/Thread", "join()V", "jlThreadJoin");
    map.registerAfter("java/lang/Object", "wait()V", "jlObjectWaitAfter");

    // java.util.concurrent.CountDownLatch is supported by AbstractQueuedSynchronizer:
    // countDown()V is supported by releaseShared(...);
    // await()V is supported by acquireSharedInterruptibly(...);
    // await(JLjava/util/concurrent/TimeUnit;)Z is supported by tryAcquireSharedNanos(...).

    // java.util.concurrent.Semaphore is supported by
    // release()V is supported by releaseShared(...);
    // acquire()V is supported by acquireSharedInterruptibly(...).
    // TODO(vors): Check what other methods are supported
    // by intercepting AbstractQueuedSynchronizer.

    // java.util.concurrent.locks.AbstractQueuedSynchronizer
    map.registerBefore("java/util/concurrent/locks/AbstractQueuedSynchronizer",
                       "releaseShared(I)Z", "juclAQS_releaseShared");
    map.registerAfter("java/util/concurrent/locks/AbstractQueuedSynchronizer",
                      "acquireSharedInterruptibly(I)V", "juclAQS_acquireSharedInterruptibly");
    map.registerAfter("java/util/concurrent/locks/AbstractQueuedSynchronizer",
                      "acquireShared(I)V", "juclAQS_acquireShared");
    map.registerAfter("java/util/concurrent/locks/AbstractQueuedSynchronizer",
                      "tryAcquireSharedNanos(IJ)Z", "juclAQS_tryAcquireSharedNanos");

    // java.util.concurrent.locks.ReentrantReadWriteLock
    map.registerAfter("java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock",
                      "lock()V", "jucRRWL_ReadLock_lock");
    map.registerBefore("java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock",
                       "unlock()V", "jucRRWL_ReadLock_unlock");
    map.registerAfter("java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock",
                      "lock()V", "jucRRWL_WriteLock_lock");
    map.registerBefore("java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock",
                       "unlock()V", "jucRRWL_WriteLock_unlock");
    map.registerAfter("java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock",
                      "tryLock()Z", "jucRRWL_ReadLock_tryLock");
    map.registerAfter("java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock",
                      "tryLock(JLjava/util/concurrent/TimeUnit;)Z",
                      "jucRRWL_ReadLock_tryLock2");
    map.registerAfter("java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock",
                      "tryLock()Z", "jucRRWL_WriteLock_tryLock");
    map.registerAfter("java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock",
                      "tryLock(JLjava/util/concurrent/TimeUnit;)Z",
                      "jucRRWL_WriteLock_tryLock2");

    map.registerBeforeExact("java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock",
                            "<init>(Ljava/util/concurrent/locks/ReentrantReadWriteLock;)V",
                            "juclReadLockConstructor");

    map.registerBeforeExact("java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock",
                            "<init>(Ljava/util/concurrent/locks/ReentrantReadWriteLock;)V",
                            "juclWriteLockConstructor");

    // java.util.concurrent.locks.ReentrantLock
    map.registerAfter("java/util/concurrent/locks/ReentrantLock", "lock()V", "jucRL_lock");
    map.registerBefore("java/util/concurrent/locks/ReentrantLock", "unlock()V", "jucRL_unlock");
    map.registerAfter("java/util/concurrent/locks/ReentrantLock", "tryLock()Z", "jucRL_tryLock");
    map.registerAfter("java/util/concurrent/locks/ReentrantLock",
                      "tryLock(JLjava/util/concurrent/TimeUnit;)Z", "jucRL_tryLock2");

    // java.util.concurrent.locks.Lock
    map.registerAfter("java/util/concurrent/locks/Lock",
                      "newCondition()Ljava/util/concurrent/locks/Condition;",
                      "juclLock_newCondition");
    map.registerAfter("java/util/concurrent/locks/Lock",
                      "lockInterruptibly()V",
                      "juclLock_lockInterruptibly");

    // java.util.concurrent.locks.Condition
    map.registerBefore("java/util/concurrent/locks/Condition", "await()V",
                       "juclCondition_awaitBefore");
    map.registerAfter("java/util/concurrent/locks/Condition", "await()V",
                      "juclCondition_awaitAfter");
    map.registerException("java/util/concurrent/locks/Condition", "await()V",
                          "juclCondition_awaitException");

    map.registerBefore("java/util/concurrent/locks/Condition", "awaitNanos(J)J",
                       "juclCondition_awaitNanosBefore");
    map.registerAfter("java/util/concurrent/locks/Condition", "awaitNanos(J)J",
                      "juclCondition_awaitNanosAfter");

    map.registerBefore("java/util/concurrent/locks/Condition",
                       "await(JLjava/util/concurrent/TimeUnit;)Z",
                       "juclCondition_await2Before");
    map.registerAfter("java/util/concurrent/locks/Condition",
                      "await(JLjava/util/concurrent/TimeUnit;)Z",
                      "juclCondition_await2After");

    map.registerBefore("java/util/concurrent/locks/Condition", "awaitUninterruptibly()V",
                       "juclCondition_awaitUninterruptiblyBefore");
    map.registerAfter("java/util/concurrent/locks/Condition", "awaitUninterruptibly()V",
                      "juclCondition_awaitUninterruptiblyAfter");

    map.registerBefore("java/util/concurrent/locks/Condition",
                       "awaitUntil(Ljava/util/Date;)Z",                                              
                       "juclCondition_awaitUntilBefore");
    map.registerAfter("java/util/concurrent/locks/Condition",
                      "awaitUntil(Ljava/util/Date;)Z",
                      "juclCondition_awaitUntilAfter");

    map.registerBefore("java/util/concurrent/locks/Condition", "signalAll()V",
                       "juclCondition_signalAll");
    map.registerBefore("java/util/concurrent/locks/Condition", "signal()V",
                       "juclCondition_signal");

    // java.util.concurrent.locks.LockSupport
    map.registerAfter("java/util/concurrent/locks/LockSupport", "park()V",
                      "juclLockSupport_park");
    map.registerAfter("java/util/concurrent/locks/LockSupport", "park(Ljava/lang/Object;)V",
                      "juclLockSupport_park2");
    map.registerAfter("java/util/concurrent/locks/LockSupport", "parkNanos(J)V",
                      "juclLockSupport_parkNanos");
    map.registerAfter("java/util/concurrent/locks/LockSupport", "parkNanos(Ljava/lang/Object;J)V",
                      "juclLockSupport_parkNanos2");
    map.registerAfter("java/util/concurrent/locks/LockSupport", "parkUntil(J)V",
                      "juclLockSupport_parkUntil");
    map.registerAfter("java/util/concurrent/locks/LockSupport", "parkUntil(Ljava/lang/Object;J)V",
                      "juclLockSupport_parkUntil2");
    map.registerBefore("java/util/concurrent/locks/LockSupport", "unpark(Ljava/lang/Thread;)V",
                      "juclLockSupport_unpark");

    // org.jtsan.RaceDetectorApi. Put exact matching to eliminate the cost of extra checks.
    map.registerBeforeExact("org/jtsan/RaceDetectorApi",
                            "noOp(Ljava/lang/Object;)V", "rdaApiNoOp");
    map.registerBeforeExact("org/jtsan/RaceDetectorApi",
                            "expectRaceBegin()V", "rdaApiExpectRaceBegin");
    map.registerBeforeExact("org/jtsan/RaceDetectorApi",
                            "expectRaceEnd()V", "rdaApiExpectRaceEnd");
    map.registerBeforeExact("org/jtsan/RaceDetectorApi",
                            "printStackTrace()V", "rdaApiPrintStackTrace");
    map.registerBeforeExact("org/jtsan/RaceDetectorApi",
                            "print(Ljava/lang/String;)V", "rdaApiPrint");

    // Benign expectRace spots in system classes.
    map.benignRaceField("java/util/concurrent/locks/ReentrantReadWriteLock$Sync",
                        "cachedHoldCounter");
  }
}
