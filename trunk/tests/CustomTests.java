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
    private static final int x = 42;
    static int getX() {
      return x;
    }
  }

  @RaceTest(expectRace = false,
      description = "Static final variable init and access")
  public void staticFinal() {
    new ThreadRunner(4) {
      public void thread1() {
        shortSleep();
        int z = staticFinalProvider.getX();
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
