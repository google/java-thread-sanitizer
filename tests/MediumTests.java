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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Konstantin Serebryany
 */
public class MediumTests {

  //------------------ Positive tests ---------------------

  @RaceTest(expectRace = true,
      description = "Concurrent access after correct CyclicBarrier")
  public void cyclicBarrierWrong() {
    new ThreadRunner(4) {
      CyclicBarrier barrier;

      public void setUp() {
        barrier = new CyclicBarrier(4);
        sharedVar = 0;
      }

      public void thread1() {
        synchronized (this) {
          sharedVar++;  
        }
        try {
          barrier.await();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        if (sharedVar == 4) {
          sharedVar = 5;
        }
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        thread1();
      }
    };
  }

  //------------------ Negative tests ---------------------

  @RaceTest(expectRace = false,
      description = "notify/wait")
  public void notifyWait() {
    new ThreadRunner(2) {
      boolean done;

      public void setUp() {
        done = false;
      }

      public synchronized void send() {
        done = true;
        notify();
      }

      public synchronized void receive() {
        while (!done) {
          try {
            wait();
          } catch (Exception e) {
            throw new RuntimeException("Exception in test notifyWait", e);
          }
        }
      }

      public void thread1() {
        shortSleep();
        sharedVar = 1;
        send();
      }

      public void thread2() {
        receive();
        sharedVar++;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "notify/wait; 4 threads")
  public void notifyWait2() {
    new ThreadRunner(4) {
      int counter;
      Object lock1;
      Object lock2;

      public void setUp() {
        counter = 2;
        lock1 = new Object();
        lock2 = new Object();
      }

      public synchronized void send() {
        counter--;
        notifyAll();
      }

      public synchronized void receive() {
        while (counter != 0) {
          try {
            wait();
          } catch (Exception e) {
            throw new RuntimeException("Exception in test notifyWait2", e);
          }
        }
      }

      public void thread1() {
        shortSleep();
        synchronized (lock1) {
          sharedVar = 1;
        }
        send();
      }

      public void thread2() {
        receive();
        synchronized (lock2) {
          sharedVar++;
        }
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        thread2();
      }
    };
  }


  @RaceTest(expectRace = false,
      description = "Two writes to different deep object field")
  public void deepField() {
    new ThreadRunner(2) {
      class DeepObject {
        DeepObject next;
        int value1;
        int value2;
      }

      DeepObject o;

      public void setUp() {
        o = new DeepObject();
        o.next = new DeepObject();
        o.next.next = new DeepObject();
        o.next.next.next = new DeepObject();
      }

      public void thread1() {
        o.next.next.next.value1 = 1;
      }

      public void thread2() {
        o.next.next.next.value2 = 2;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "CountDownLatch")
  public void countDownLatch() {
    new ThreadRunner(4) {
      CountDownLatch latch;

      public void setUp() {
        latch = new CountDownLatch(3);
        sharedVar = 0;
      }

      public void thread1() {
        try {
          latch.await();
        } catch (InterruptedException e) {
          throw new RuntimeException("Exception in test CountDownLatch", e);
        }
        if (sharedVar == 3) {
          sharedVar = 4;
        } else {
          System.err.println("CountDownLatch assert");
          System.exit(1);
        }
      }

      public void thread2() {
        synchronized (this) {
          sharedVar++;
        }
        latch.countDown();
      }

      public void thread3() {
        thread2();
      }

      public void thread4() {
        thread2();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "After CyclicBarrier only one thread increments shared int")
  public void cyclicBarrier() {
    new ThreadRunner(4) {
      CyclicBarrier barrier;

      public void setUp() {
        barrier = new CyclicBarrier(4);
        sharedVar = 0;
      }

      public void thread1() {
        synchronized (this) {
          sharedVar++;
          shortSleep();
        }
        try {
          barrier.await();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        thread1();
        sharedVar++;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Semaphore")
  public void semaphore() {
    final Semaphore semaphore = new Semaphore(0);
    new ThreadRunner(2) {

      public void thread1() {
        longSleep();
        sharedVar = 1;
        semaphore.release();
      }

      public void thread2() {
        try {
          semaphore.acquire();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        sharedVar = 2;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "ReadWriteLock: write locks only")
  public void writeLocksOnly() {
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    new ThreadRunner(2) {
      public void thread1() {
        lock.writeLock().lock();
        int v = sharedVar;
        lock.writeLock().unlock();
      }

      public void thread2() {
        lock.writeLock().lock();
        sharedVar++;
        lock.writeLock().unlock();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "ReadWriteLock: both read and write locks")
  public void readAndWriteLocks() {
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    new ThreadRunner(4) {
      public void thread1() {
        lock.readLock().lock();
        int v = sharedVar;
        lock.readLock().unlock();
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        lock.writeLock().lock();
        int v = sharedVar;
        lock.writeLock().unlock();
      }

      public void thread4() {
        lock.writeLock().lock();
        sharedVar++;
        lock.writeLock().unlock();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "ReentrantReadWriteLock: tryLock")
  public void tryLock() {
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    new ThreadRunner(4) {
      public void thread1() {
        while (!lock.readLock().tryLock()) {
          shortSleep();
        }
        int v = sharedVar;
        lock.readLock().unlock();
      }

      public void thread2() {
        try {
          while (!lock.readLock().tryLock(1, TimeUnit.MILLISECONDS)) {
            shortSleep();
          }
          int v = sharedVar;
          lock.readLock().unlock();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      public void thread3() {
        while (!lock.writeLock().tryLock()) {
          shortSleep();
        }
        sharedVar++;
        lock.writeLock().unlock();
      }

      public void thread4() {
        try {
          while (!lock.writeLock().tryLock(1, TimeUnit.MILLISECONDS)) {
            shortSleep();
          }
          sharedVar++;
          lock.writeLock().unlock();
        } catch (InterruptedException e) {
          throw new RuntimeException("Exception in test tryLock", e);
        }
      }
    };
  }

  @RaceTest(expectRace = false,
    description = "ReentrantLock: simple access")
  public void reentrantLockSimple() {
    final ReentrantLock lock = new ReentrantLock();
    new ThreadRunner(2) {
      public void thread1() {
        lock.lock();
        sharedVar++;
        lock.unlock();
      }

      public void thread2() {
        thread1();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "ReentrantLock: tryLocks")
  public void tryLock2() {
    final ReentrantLock lock = new ReentrantLock();
    new ThreadRunner(3) {
      public void thread1() {
        while (!lock.tryLock()) {
          shortSleep();
        }
        sharedVar++;
        lock.unlock();
      }

      public void thread2() {
        try {
          while (!lock.tryLock(1, TimeUnit.MILLISECONDS)) {
            shortSleep();
          }
          sharedVar++;
          lock.unlock();
        } catch (InterruptedException e) {
          throw new RuntimeException("Exception in test tryLock2", e);
        }
      }

      public void thread3() {
        lock.lock();
        sharedVar++;
        lock.unlock();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "AtomicInteger increment")
  public void atomicInteger() {
    final AtomicInteger i = new AtomicInteger();
    new ThreadRunner(4) {

      public void thread1() {
        i.incrementAndGet();
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        thread1();
      }
    };
  }  

}
