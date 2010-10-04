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
public class CustomTests {

  static class staticFinalProvider {
    private static final int staticFinalX = hugeMethod();

    static int getStaticFinalX() {
      return staticFinalX;
    }

    static int hugeMethod() {
      int x = 1;
      int y = 1;
      for (int i = 0; i < 1000000; i++) {
        x = x + y;
        y = x - y;
      }
      return x;
    }
  }

  @RaceTest(expectRace = false,
      description = "Static final variable init and access")
  public void staticFinal() {
    new ThreadRunner(4) {
      public void thread1() {
        int z = staticFinalProvider.getStaticFinalX();
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

  @RaceTest(expectRace = false,
      description = "Stop thread by throwing Exception")
  public void exceptionExit() {
    new ThreadRunner(2) {
      public synchronized void thread1() {
        sharedVar++;
        throw new RuntimeException("Exit from thread1 with this Exception");
      }

      public synchronized void thread2() {
        sharedVar++;
      }
    };
  }

  static class StaticCounter {
    static int counter = 0;
    static void sleep() {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    static synchronized int inc() {
      sleep();
      int res = counter++;
      sleep();
      return res;
    }
    static int inc2() {
      synchronized (StaticCounter.class) {
        sleep();
        int res = counter++;
        sleep();
        return res;
      }
    }
  }

  @ExcludedTest(reason = "We don't handle monitorEnter in static synchronized methods")
  @RaceTest(expectRace = false,
    description = "Synchronized increment from static context")
  public void staticSync() {
    new ThreadRunner(2) {
      public void thread1() {
        StaticCounter.inc();
      }
      public void thread2() {
        thread1();
      }
    };
  }

  @RaceTest(expectRace = false,
    description = "Synchronized increment from static context")
  public void staticSync2() {
    new ThreadRunner(2) {
      public void thread1() {
        StaticCounter.inc2();
      }
      public void thread2() {
        thread1();
      }
    };
  }

  @ExcludedTest(reason = "We don't handle monitorEnter in static synchronized methods")  
  @RaceTest(expectRace = false,
    description = "Synchronized increment from static context")
  public void staticSync3() {
    new ThreadRunner(2) {
      public void thread1() {
        StaticCounter.inc();
      }
      public void thread2() {
        StaticCounter.inc2();
      }
    };
  }


}
