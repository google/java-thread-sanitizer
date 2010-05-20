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
    map.registerBefore("java/lang/System",
                 "arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V",
                 "jlSystemArrayCopy");
    map.registerBefore("java/lang/Object", "wait()V", "jlObjectWait");
    map.registerBefore("java/lang/Object", "notify()V", "jlObjectNotify");
    map.registerBefore("java/lang/Thread", "start()V", "jlThreadStart");
    map.registerAfter("java/lang/Thread", "join()V", "jlThreadJoin");
    map.registerAfter("java/lang/Object", "wait()V", "jlObjectWaitAfter");

    // java.util.concurrent.CountDownLatch
    map.registerBefore("java/util/concurrent/CountDownLatch",
                       "countDown()V", "jucCountDownLatch_countDown");
    map.registerAfter("java/util/concurrent/CountDownLatch",
                      "await()V", "jucCountDownLatch_await");

    // java.util.concurrent.Semaphore
    map.registerBefore("java/util/concurrent/Semaphore",
                       "release()V", "jucSemaphore_release");
    map.registerAfter("java/util/concurrent/Semaphore",
                      "acquire()V", "jucSemaphore_acquire");

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
    // TODO(egor): tryLock(JLjava/util/concurrent/TimeUnit;)Z
    map.registerAfter("java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock",
                      "tryLock()Z", "jucRRWL_WriteLock_tryLock");
    // TODO(egor): tryLock(JLjava/util/concurrent/TimeUnit;)Z

    map.registerBeforeExact("java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock",
                            "<init>(Ljava/util/concurrent/locks/ReentrantReadWriteLock;)V",
                            "juclReadLockConstructor");

    map.registerBeforeExact("java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock",
                            "<init>(Ljava/util/concurrent/locks/ReentrantReadWriteLock;)V",
                            "juclWriteLockConstructor");

    // java.util.concurrent.locks.ReentrantLock
    map.registerAfter("java/util/concurrent/locks/ReentrantLock",
                      "lock()V", "jucRL_lock");
    map.registerBefore("java/util/concurrent/locks/ReentrantLock",
                      "unlock()V", "jucRL_unlock");
    map.registerAfter("java/util/concurrent/locks/ReentrantLock",
                      "tryLock()Z", "jucRL_tryLock");
    map.registerAfter("java/util/concurrent/locks/ReentrantLock",
                      "tryLock(JLjava/util/concurrent/TimeUnit;)Z", "jucRL_tryLock2");

    // RaceDetectorApi. Put exact matching to eliminate the cost of extra checks.
    // TODO(egor): methods must be named starting with lowercase letter.
    map.registerBeforeExact("RaceDetectorApi", "NoOp(Ljava/lang/Object;)V", "rdaApiNoOp");
    map.registerBeforeExact("RaceDetectorApi", "ExpectRaceBegin()V", "rdaApiExpectRaceBegin");
    map.registerBeforeExact("RaceDetectorApi", "ExpectRaceEnd()V", "rdaApiExpectRaceEnd");
    map.registerBeforeExact("RaceDetectorApi", "PrintStackTrace()V", "rdaApiPrintStackTrace");
    map.registerBeforeExact("RaceDetectorApi", "Print(Ljava/lang/String;)V", "rdaApiPrint");
  }
}
