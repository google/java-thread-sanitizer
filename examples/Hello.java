/* Copyright (c) 2009 Google Inc.
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

public class Hello {
  private static int count = 0;

  private static Object lock = new Object();

  private static int[] array = new int[10];
  private static Object[] oarr = new Object[10];
  private static long[] larr = new long[10];

  private int i;

  public void foo() {
    synchronized(lock) {
      System.out.printf("%d in foo()\n", count++);
    }
  }

  public synchronized void bar() {
    System.out.printf("%d in bar()\n", count++);
  }

  public void arr() {
    array[0] = 1;
    larr[1] = 1;
    oarr[2] = this;
    System.out.println("array.length=" + array.length + ", array[5]=" + array[5]);
  }

  public void thr() {
    final Object lk = lock;
    Thread thr1 = new Thread() {
      @Override
      public void run() {
        synchronized(lk) {
          try {
            System.out.println("before wait");
            lk.wait();
            System.out.println("after wait");
          } catch (InterruptedException e) {
            System.out.println("interrupted");
            return;
          }
        }
      }
    };

    thr1.start();
    try {
      Thread.sleep(5000); // Allow thr1 to wake up.
    } catch (InterruptedException e) {
      System.out.println("interrupted while sleeping");
    }
    synchronized(lock) {
      lock.notify();
    }
    try {
      thr1.join();
    } catch (InterruptedException e) {
      System.out.println("interrupted while joining");
    }
  }

  private class IncThread extends Thread {
    @Override
    public void run() {
      System.out.println("count is: " + count);
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        return;
      }
      count++;
    }
  }
  public void race() {
    i++;
    Thread thr1 = new IncThread();
    Thread thr2 = new IncThread();
    thr1.start();
    thr2.start();
    try {
      thr1.join();
      thr2.join();
    } catch (InterruptedException e) {
      System.out.println("We were interrupted in the main thread. Exiting.");
    }
  }

  public void arrcopy() {
    int array2[] = new int[10];
    System.arraycopy(array2, 0, array, 0, 9);
  }

  public static void main(String[] args) {
    Hello hello = new Hello();
    hello.race();
  }
}
