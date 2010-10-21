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

/**
 * Class contains easy tests for jtsan.
 * Test is easy if
 * 1) Test doesn't use java primitives outside java.lang package.
 * 2) Test doesn't use non-trivial synchronized idioms.
 *
 * @author Konstantin Serebryany
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

  @RaceTest(expectRace = true,
      description = "Use racey System.arraycopy()")
  public void systemArrayCopy() {
    new ThreadRunner(2) {
      int[] sharedArray;

      public void setUp() {
        sharedArray = new int[117];
      }

      public void thread1() {
        System.arraycopy(new int[100], 0, sharedArray, 10, 100);
      }

      public void thread2() {
        System.arraycopy(sharedArray, 0, new int[30], 0, 20);
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Use racey System.arraycopy()")
  public void systemArrayCopy2() {
    new ThreadRunner(2) {
      int[] sharedArray;

      public void setUp() {
        sharedArray = new int[117];
      }

      public void thread1() {
        System.arraycopy(new int[100], 0, sharedArray, 10, 100);
      }

      public void thread2() {
        int x = sharedArray[42];
      }
    };
  }

  @RaceTest(expectRace = true,
      description = "Use racey System.arraycopy()")
  public void systemArrayCopy3() {
    new ThreadRunner(2) {
      int[] sharedArray;

      public void setUp() {
        sharedArray = new int[117];
      }

      public void thread1() {
        sharedArray[42] = 117;
      }

      public void thread2() {
        System.arraycopy(sharedArray, 30, new int[30], 0, 20);
      }
    };
  }

  @ExcludedTest(reason = "We can not distinguish two types of ArrayStoreException in native code " +
      "occurred in System.arraycopy")
  @RaceTest(expectRace = true,
      description = "Use racey System.arraycopy(). ArrayStoreException case 2 occurred " +
          "in the middle of copy process")
  public void systemArrayCopyException() {
    new ThreadRunner(2) {
      Object[] sharedArray;

      public void setUp() {
        sharedArray = new Object[117];
      }

      public void thread1() {
        for (int i=0; i<10; i++) {
          sharedArray[i] = Integer.valueOf(5);
        }
        for (int i=10; i<20; i++) {
          sharedArray[i] = Double.valueOf(1.1);
        }
      }

      public void thread2() {
        shortSleep();
        System.arraycopy(sharedArray, 0, new Integer[30], 0, 20);
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
        while (!staticVolatileBoolean) ;
        sharedVar = 2;
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


  @ExcludedTest(reason = "Incorrect handling of join() in Agent")
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

  @RaceTest(expectRace = false,
      description = "Use System.arraycopy(). Different scopes.")
  public void systemArrayCopyDiff() {
    new ThreadRunner(2) {
      int[] sharedArray;

      public void setUp() {
        sharedArray = new int[117];
      }

      public void thread1() {
        System.arraycopy(new int[100], 0, sharedArray, 10, 100);
      }

      public void thread2() {
        System.arraycopy(sharedArray, 0, new int[30], 0, 6);
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Use racey System.arraycopy(). Synchronized on the array monitor.")
  public void systemArrayCopySync() {
    new ThreadRunner(2) {
      int[] sharedArray;

      public void setUp() {
        sharedArray = new int[117];
      }

      public void thread1() {
        synchronized (sharedArray) {
          System.arraycopy(new int[100], 0, sharedArray, 10, 100);
        }
      }

      public void thread2() {
        synchronized (sharedArray) {
          System.arraycopy(sharedArray, 0, new int[30], 0, 20);
        }
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Arraycopy while reading one of the written elements")
  public void systemArrayCopyDiff2() {
    new ThreadRunner(2) {
      int[] sharedArray;

      public void setUp() {
        sharedArray = new int[117];
      }

      public void thread1() {
        System.arraycopy(new int[100], 0, sharedArray, 10, 100);
      }

      public void thread2() {
        int x = sharedArray[3];
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Arraycopy while written one of the reading elements")
  public void systemArrayCopyDiff3() {
    new ThreadRunner(2) {
      int[] sharedArray;

      public void setUp() {
        sharedArray = new int[117];
      }

      public void thread1() {
        sharedArray[27] = 11;
      }

      public void thread2() {
        System.arraycopy(sharedArray, 0, new int[30], 0, 20);
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Use racey System.arraycopy(). IndexOutOfBoundException occurred")
  public void systemArrayCopyIndexOutOfBoundsException() {
    new ThreadRunner(2) {
      int[] sharedArray;

      public void setUp() {
        sharedArray = new int[117];
      }

      public void thread1() {
        sharedArray[27] = 11;
      }

      public void thread2() {
        System.arraycopy(sharedArray, 0, new int[30], 0, 200);
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Use racey System.arraycopy(). NullPointerException occurred")
  public void systemArrayCopyNullPointerException() {
    new ThreadRunner(2) {
      int[] sharedArray;

      public void setUp() {
        sharedArray = new int[117];
      }

      public void thread1() {
        sharedArray[27] = 11;
      }

      public void thread2() {
        System.arraycopy(sharedArray, 0, null, 0, 20);
      }
    };
  }

  @RaceTest(expectRace = false,
      description = "Use racey System.arraycopy(). ArrayStoreException case 1 occurred")
  public void systemArrayCopyArrayStoreException() {
    new ThreadRunner(2) {
      int[] sharedArray;

      public void setUp() {
        sharedArray = new int[117];
      }

      public void thread1() {
        sharedArray[27] = 11;
      }

      public void thread2() {
        System.arraycopy(sharedArray, 0, new Object(), 0, 20);
      }
    };
  }

}
