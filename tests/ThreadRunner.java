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

/**
 * @author Sergey Vorobyev
 */
public class ThreadRunner {

  private static final int SHORT_SLEEP_MILLIS = 10;
  private static final int LONG_SLEEP_MILLIS = 100;

  // Shared vars, useful in tests.
  protected int sharedVar = 0;
  protected volatile int sharedVolatile = 0;
  protected Object sharedObject;
  protected final Object monitor;
  protected static volatile boolean staticVolatileBoolean;

  // Virtual functions. Override some of them in your test.
  public void thread1() {
    throw new RuntimeException("thread1() don't override");
  }

  public void thread2() {
    throw new RuntimeException("thread2() don't override");
  }

  public void thread3() {
    throw new RuntimeException("thread3() don't override");
  }

  public void thread4() {
    throw new RuntimeException("thread4() don't override");
  }

  public void setUp() {
  }

  public void tearDown() {
  }

  public void shortSleep() {
    try {
      Thread.sleep(SHORT_SLEEP_MILLIS);
    } catch (Exception e) {
    }
  }

  public void longSleep() {
    try {
      Thread.sleep(LONG_SLEEP_MILLIS);
    } catch (Exception e) {
    }
  }

  // Run n separate threads, then join them all.

  public ThreadRunner(int n) {

    monitor = new Object();

    if (n > 4) {
      throw new IllegalArgumentException(this.getClass() + " don't support " + n + " threads. "
          + "Please add support to source.");
    }

    class ThreadWithRunner extends Thread {
      public ThreadWithRunner(ThreadRunner runner) {
        this.runner = runner;
      }

      protected ThreadRunner runner;
    }

    Thread threads[] = new Thread[4];

    threads[0] = new ThreadWithRunner(this) {
      public void run() {
        runner.thread1();
      }
    };
    threads[1] = new ThreadWithRunner(this) {
      public void run() {
        runner.thread2();
      }
    };
    threads[2] = new ThreadWithRunner(this) {
      public void run() {
        runner.thread3();
      }
    };
    threads[3] = new ThreadWithRunner(this) {
      public void run() {
        runner.thread4();
      }
    };

    try {
      setUp();
      for (int i = 0; i < n; i++) {
        threads[i].start();
        Thread.yield();
      }
      for (int i = 0; i < n; i++) {
        threads[i].join();
      }
      tearDown();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
