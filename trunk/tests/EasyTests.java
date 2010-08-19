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

import org.jtsan.RaceDetectorApi;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * @author Sergey Vorobyev
 */
public class EasyTests {

  //------------------ Positive tests ---------------------

  @RaceTest(expectRace = true,
      description = "Two writes to shared int without synchronization")
  public void noLockWWInt() {
    new ThreadRunner(2) {
      public void thread1() {
        sharedVar = 1;
      }

      public void thread2() {
        sharedVar = 2;
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two writes to shared short without synchronization")
  public void noLockWWShort() {
    new ThreadRunner(2) {
      short sharedShort;

      public void thread1() {
        sharedShort = (short) 1;
      }

      public void thread2() {
        sharedShort = (short) 2;
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two writes to shared char without synchronization")
  public void noLockWWChar() {
    new ThreadRunner(2) {
      char sharedChar;

      public void thread1() {
        sharedChar = '1';
      }

      public void thread2() {
        sharedChar = '2';
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two writes to shared long without synchronization")
  public void noLockWWLong() {
    new ThreadRunner(2) {
      long sharedLong;

      public void thread1() {
        sharedLong = 1L;
      }

      public void thread2() {
        sharedLong = 2L;
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two writes to shared float without synchronization")
  public void noLockWWFloat() {
    new ThreadRunner(2) {
      float sharedFloat;

      public void thread1() {
        sharedFloat = 1.0f;
      }

      public void thread2() {
        sharedFloat = 2.0f;
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two writes to shared double without synchronization")
  public void noLockWWDouble() {
    new ThreadRunner(2) {
      double sharedDouble;

      public void thread1() {
        sharedDouble = 1.0;
      }

      public void thread2() {
        sharedDouble = 2.0;
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two writes to shared Object without synchronization")
  public void noLockWWObject() {
    new ThreadRunner(2) {
      public void thread1() {
        sharedObject = 1L;
      }

      public void thread2() {
        sharedObject = 2.0;
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "One locked and one no locked write")
  public void lockedVsNoLockedWW() {
    new ThreadRunner(2) {
      public void thread1() {
        sharedVar = 1;
      }

      public void thread2() {
        synchronized (this) {
          sharedVar = 2;
        }
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "One locked and one no locked increment")
  public void lockedVsNoLockedWW2() {
    new ThreadRunner(2) {
      public synchronized void thread1() {
        sharedVar++;
      }

      public void thread2() {
        sharedVar++;
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two writes locked with different locks")
  public void differentLocksWW() {
    new ThreadRunner(2) {

      public void thread1() {
        synchronized (monitor) {
          sharedVar++;
        }
      }

      public void thread2() {
        synchronized (this) {
          sharedVar++;
        }
      }
    };
  }

  @ExcludedTest(reason = "HashSet load before instrumenting start")
  @RaceTest(expectRace = true,
      description = "Two no locked writes to a HashSet")
  public void hashSetAccessNoLocks() {
    final Set<Integer> sharedHashSet = new HashSet<Integer>();
    new ThreadRunner(2) {

      public void thread1() {
        sharedHashSet.add(1);
      }

      public void thread2() {
        sharedHashSet.add(2);
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two no locked writes to a TreeMap")
  public void treeMapAccessNoLocks() {
    final Map<Integer, Integer> sharedMap = new TreeMap<Integer, Integer>();
    new ThreadRunner(2) {

      public void thread1() {
        sharedMap.put(1, 2);
      }

      public void thread2() {
        sharedMap.put(2, 1);
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two no locked writes to an array")
  public void arrayAccessNoLocks() {
    final int[] sharedArray = new int[100];
    new ThreadRunner(2) {

      public void thread1() {
        sharedArray[42] = 1;
      }

      public void thread2() {
        sharedArray[42] = 2;
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two writes locked with different locks")
  public void differentLocksWW2() {
    final ReentrantLock lock = new ReentrantLock();
    new ThreadRunner(2) {

      public void thread1() {
        lock.lock();
        sharedVar++;
        lock.unlock();
      }

      public void thread2() {
        synchronized (this) {
          sharedVar++;
        }
      }
    };
  }

  @ExcludedTest(reason = "Tsan find inexact happens-before arc")
  @RaceTest(expectRace = true,
      description = "Two unlocked writes, critcal sections between them")
  public void lockInBetween() {
    new ThreadRunner(2) {
      public void thread1() {
        sharedVar = 1;
        synchronized (this) {
        }
      }

      public void thread2() {
        longSleep();
        synchronized (this) {
        }
        sharedVar = 2;
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Three threads writing under a reader lock, one under a writing lock")
  public void writingUnderReaderLock() {
    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    new ThreadRunner(4) {
      public void thread1() {
        lock.readLock().lock();
        int t = sharedVar;
        shortSleep();  // Put this sleep here so that the expectRace may actually happen.
        sharedVar = t + 1;
        lock.readLock().unlock();
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread1();
      }

      public void thread4() {
        lock.writeLock().lock();
        sharedVar++;
        lock.writeLock().unlock();
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Two no locked writes to same object field")
  public void recursiveObjectWW() {
    new ThreadRunner(2) {
      class RecursiveObject {
        RecursiveObject next;
        int value;
      }

      RecursiveObject o;

      public void setUp() {
        o = new RecursiveObject();
        o.next = new RecursiveObject();
        o.next.next = new RecursiveObject();
        o.next.next.next = o;
      }

      public void thread1() {
        o.next.next.next.value = 1;
      }

      public void thread2() {
        o.value = 2;
      }
    };
  }


  //------------------ Negative tests ---------------------

  @RaceTest(expectRace = false,
      description = "Simple no-operation test")
  public void noOperation() {
    new ThreadRunner(2) {
      public void thread1() {
        sharedVar = 1;
      }

      public void thread2() {
        longSleep();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "No operations")
  public void noOperation2() {
    new ThreadRunner(4) {
      public void setUp() {
        sharedObject = 1L;
      }

      public void thread1() {
        RaceDetectorApi.noOp(sharedObject);
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread2();
      }

      public void thread4() {
        thread3();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "two locked increments")
  public void lockedInc() {
    new ThreadRunner(2) {
      public void thread1() {
        synchronized (this) {
          sharedVar++;
          longSleep();
        }
      }

      public void thread2() {
        thread1();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "two locked  increments")
  public void lockedInc2() {
    new ThreadRunner(2) {
      public synchronized void thread1() {
        sharedVar++;
        longSleep();
      }

      public void thread2() {
        thread1();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Distinct fields of the same object are written")
  public void distinctFields() {
    new ThreadRunner(2) {
      int field1
          ,
          field2;

      public void thread1() {
        field1 = 1;
      }

      public void thread2() {
        field2 = 2;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Two accesses to a local volatile boolean")
  public void localVolatileBoolean() {
    new ThreadRunner(2) {
      volatile boolean volatileBoolean;

      public void thread1() {
        volatileBoolean = true;
      }

      public void thread2() {
        while (!volatileBoolean) ;
      }
    };
  }

  @ExcludedTest(reason = "We handle volatile fields in super classes incorrectly")
  @RaceTest(expectRace = false,
      description = "Two accesses to a static volatile boolean in super class")
  public void staticVolatileBoolean() {
    new ThreadRunner(2) {
      public void thread1() {
        staticVolatileBoolean = true;
      }

      public void thread2() {
        while (!staticVolatileBoolean) ;
      }
    };
  }

  @ExcludedTest(reason = "We handle volatile fields in super classes incorrectly")
  @RaceTest(expectRace = false,
      description = "Two accesses to a volatile var in super class")
  public void superClassVolatile() {
    new ThreadRunner(2) {
      public void thread1() {
        sharedVolatile = 1;
      }

      public void thread2() {
        sharedVolatile = 2;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Volatile boolean is used as a synchronization")
  public void syncWithLocalVolatile() {
    new ThreadRunner(2) {
      volatile boolean volatileBoolean;

      public void setUp() {
        volatileBoolean = false;
      }

      public void thread1() {
        sharedVar = 1;
        volatileBoolean = true;
      }

      public void thread2() {
        while (!volatileBoolean) ;
        sharedVar = 2;
      }
    };
  }

  @ExcludedTest(reason = "We handle volatile fields in super classes incorrectly")
  @RaceTest(expectRace = false,
      description = "Static volatile boolean is used as a synchronization")
  public void syncWithStaticVolatile() {
    new ThreadRunner(2) {
      public void setUp() {
        staticVolatileBoolean = false;
      }

      public void thread1() {
        sharedVar = 1;
        staticVolatileBoolean = true;
      }

      public void thread2() {
        while (!staticVolatileBoolean);
        sharedVar = 2;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Sending a message via a locked object")
  public void messageViaLockedObject() {
    new ThreadRunner(2) {
      Integer locked_object;

      public void thread1() {
        Integer message = 42;
        synchronized (this) {
          locked_object = message;
        }
      }

      public void thread2() {
        Integer message;
        while (true) {
          synchronized (this) {
            message = locked_object;
            if (message != null) break;
          }
          shortSleep();
        }
        message++;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Passing ownership via a locked boolean")
  public void passingViaLockedBoolean() {
    new ThreadRunner(2) {
      private boolean signal;

      public void thread1() {
        sharedVar = 1;
        longSleep();
        synchronized (this) {
          signal = true;
        }
      }

      public void thread2() {
        while (true) {
          synchronized (this) {
            if (signal) break;
          }
          shortSleep();
        }
        sharedVar = 2;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Passing ownership via a locked map")
  public void passingViaLockedMap() {
    new ThreadRunner(2) {
      private Map<Integer, Object> map;

      public void setUp() {
        map = new TreeMap<Integer, Object>();
        sharedObject = 0L;
      }

      public void thread1() {
        sharedObject = 42;
        synchronized (this) {
          map.put(1, sharedObject);
        }
      }

      public void thread2() {
        Integer message;
        while (true) {
          synchronized (this) {
            message = (Integer) map.get(1);
            if (message != null) break;
          }
          shortSleep();
        }
        message++;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Passing object ownership via a locked boolean; 4 threads")
  public void passingViaLockedBoolean2() {
    new ThreadRunner(4) {
      Object lock;
      int counter;

      public void setUp() {
        sharedObject = 0.0f;
        lock = new Object();
        counter = 2;
      }

      public void thread1() {
        synchronized (lock) {
          sharedObject = (Float) sharedObject + 1;
        }
        synchronized (this) {
          counter--;
        }
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        Integer message;
        while (true) {
          synchronized (this) {
            if (counter == 0) break;
          }
          shortSleep();
        }
      }

      public void thread4() {
        thread3();
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Accessing different fields of object by different threads")
  public void differentFields() {
    new ThreadRunner(4) {
      Object lock;
      int sharedInt;

      public void setUp() {
        sharedInt = 0;
        lock = new Object();
      }

      public void thread1() {
        synchronized (lock) {
          sharedInt++;
        }
        synchronized (this) {
          sharedVar--;
        }
      }

      public void thread2() {
        thread1();
      }

      public void thread3() {
        thread2();
      }

      public void thread4() {
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Accessing different fields of object by different threads")
  public void differentFields2() {
    new ThreadRunner(4) {
      int a, b;
      Integer c, d;

      public void setUp() {
        a = b = c = d = 117;
      }

      public void thread1() {
        a++;
      }

      public void thread2() {
        b--;
      }

      public void thread3() {
        c++;
      }

      public void thread4() {
        d--;
      }
    };
  }


  @RaceTest(expectRace = false,
      description = "Two no locked writes to an array at different offsets")
  public void arrayDifferentOffsets() {
    final int[] sharedArray = new int[100];
    new ThreadRunner(2) {

      public void thread1() {
        sharedArray[42] = 1;
      }

      public void thread2() {
        sharedArray[43] = 2;
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Write under same lock, but two different methods")
  public void lockedWW() {
    new ThreadRunner(2) {
      public synchronized void thread1() {
        sharedVar = 1;
      }

      public void thread2() {
        synchronized (this) {
          sharedVar = 2;
        }
      }
    };
  }


  @ExcludedTest(reason = "Incorrect handle join() in Agent")
  @RaceTest(expectRace = false,
      description = "Join threads without start them")
  public void joinWithoutStart() {
    class MyThread extends Thread {
      public void run() {
        throw new RuntimeException("This code is never running");
      }
    }
    Thread[] t = new Thread[4];
    for (int i = 0; i < 4; i++) {
      t[i] = new MyThread();
    }
    try {
      for (int i = 0; i < 4; i++) {
        t[i].join();
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("InterruptedException in joinWithoutStart() method ", e);
    }
  }

}
