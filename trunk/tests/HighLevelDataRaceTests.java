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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Sergey Vorobyev
 */
public class HighLevelDataRaceTests {

  @RaceTest(description = "The simplest atomicity violation. Store sharedVar at local var." +
      " All accesses to sharedVar are synchronized.")
  public void localCopy() {
    new ThreadRunner(2) {
      public void thread1() {
        int local;
        synchronized(this) {
          local = sharedVar;
        }
        local++;
        synchronized(this) {
          sharedVar = local;
        }
      }
      public void thread2() {
        thread1();
      }
    };
  }


  @RaceTest(description = "Stale-value error from paper \"Finding stale-value errors " +
      "in concurrent programs\" by M.Burrows and K.R.M.Leino")
  public void badCyclicQueue() {
    new ThreadRunner(2) {
      private final int C = 100;
      private int n, ukr;
      private int[] array;

      private synchronized void consume(int[] a) {
        try {
          while (n == 0) {
            System.out.println("Consumer array length before wait = " + a.length);
            this.wait();
            System.out.println("Consumer array length after wait = " + a.length);
          }
        } catch (InterruptedException e) {
          throw new RuntimeException("InterruptedException in badCyclicQueue", e);
        }
        // BAD BAD BAD! a and array is not same.
        int x = a[ukr];
        ukr = (ukr + 1) % a.length;
        n--;
      }

      private synchronized void produce() {
        if (n == array.length) {
          System.out.println("Producer array length before x2 = " + array.length);
          int[] newArray = new int[array.length * 2];
          System.arraycopy(array, ukr, newArray, 0, array.length - ukr);
          System.arraycopy(array, 0, newArray, array.length - ukr, ukr);
          array = newArray;
          ukr = 0;
          System.out.println("Producer array length after x2 = " + array.length);
        }
        int x = (ukr + n) % array.length;
        array[x] = x;
        n++;
        this.notify();
      }

      public void setUp() {
        array = new int[2];
      }

      public void thread1() {
        for (int i = 0; i < C; i++) {
          consume(array);
        }
      }

      public void thread2() {
        for (int i = 0; i < C; i++) {
          produce();
        }
      }

    };
  }

  @RaceTest(description = "First motivation example. Calculate predicate under lock. " +
      "Acquire lock. Do something based on predicate")
  public void arraySize() {
    new ThreadRunner(2) {
      private int uk;
      private int[] stack;

      public void setUp() {
        stack = new int[20];
      }

      synchronized int getSize() {
        return uk;
      }

      synchronized void push(int x) {
        stack[uk++] = x;
      }

      synchronized int pop() {
        return stack[--uk];
      }

      public void thread1() {
        for (int i = 0; i < 10; i++) {
          push(i);
        }
      }

      public void thread2() {
        while (getSize() > 0) {
          // BAD BAD BAD!
          pop();
        }
      }
    };
  }

  @RaceTest(description = "Inconsistent state error")
  public void pointInconsistentState() {
    new ThreadRunner(2) {
      class Point {
        private int x, y;

        public Point() {
          x = 0;
          y = 0;
        }

        public synchronized int getX() {
          return x;
        }

        public synchronized void setX(int nx) {
          x = nx;
        }

        public synchronized int getY() {
          return y;
        }

        public synchronized void setY(int ny) {
          y = ny;
        }

        public synchronized int[] getXY() {
          return new int[]{x, y};
        }

        public synchronized void setXY(int[] xy) {
          x = xy[0];
          y = xy[1];
        }
      }

      Point point;

      public void setUp() {
        point = new Point();
      }

      public void thread1() {
        point.setX(100);
        // BAD BAD BAD!
        point.setY(100);
      }

      public void thread2() {
        Point localPoint = new Point();
        int[] xy = point.getXY();
        localPoint.setXY(xy);
      }
    };
  }

  @RaceTest(description = "Multi-variable access correlation. Based on real bug in Mozilla-0.8")
  public void mozilla08CacheBug() {
    new ThreadRunner(4) {
      class JSCache {
        Object[] table;
        boolean empty;
        int index;

        JSCache(int capacity) {
          table = new Object[capacity];
          empty = true;
        }
      }

      JSCache cache;

      public void setUp() {
        cache = new JSCache(10);
      }

      private void flushCache() {
        synchronized (this) {
          Arrays.fill(cache.table, null);
          cache.index = 0;
        }
        // Do something.
        shortSleep();
        // BAD BAD BAD!
        synchronized (this) {
          cache.empty = true;
        }
      }

      private void cacheFill(Object o) {
        synchronized (this) {
          cache.table[cache.index++] = o;
        }
        // Do something.
        longSleep();

        synchronized (this) {
          cache.empty = false;
        }
      }

      public void thread1() {
        flushCache();
      }

      public void thread2() {
        cacheFill(new Object());
      }

      public void thread3() {
        cacheFill(new Object());
      }

      public void thread4() {
        cacheFill(new Object());
      }
    };
  }

  @RaceTest(description = "Multi-variable access correlation. Based on real bug in Mozilla-0.9")
  public void mozilla09StringLengthBug() {
    new ThreadRunner(2) {
      class JSRuntime {
        AtomicInteger strCount;
        double lengthSum;

        JSRuntime() {
          strCount = new AtomicInteger();
        }
      }

      private JSRuntime rt;

      public void setUp() {
        rt = new JSRuntime();
      }

      private void newString(String s) {
        rt.strCount.incrementAndGet();
        // Do something.
        shortSleep();
        // BAD BAD BAD!
        synchronized (this) {
          rt.lengthSum += s.length();
        }
      }

      private double getMeanStrLength() {
        synchronized (this) {
          return rt.lengthSum / rt.strCount.get();
        }
      }

      public void thread1() {
        newString("blablabla");
      }

      public void thread2() {
        System.out.println("Mean strings length = " + getMeanStrLength());
      }

    };
  }


}
