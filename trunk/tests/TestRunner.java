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

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * @author Sergey Vorobyev
 */
public class TestRunner {

  private final String REGEXP_PREFIX = "-test_name=";
  private final String VERBOSE_FLAG = "-verbose";
  private final String IGNORE_DISABLE_FLAG = "-all";
  private final String IGNORE_EXPECTED_RACE_FLAG = "-ignore_expected";
  protected PrintWriter out;

  public static void main(String[] args) {
    TestRunner runner = new TestRunner();
    runner.parseArgs(args);
    runner.run();
  }

  public TestRunner() {
    out = new PrintWriter(System.out, true);
    regexp = ".*";
    verbose = false;
    ignoreDisable = false;
    ignoreExpectedRace = false;
    tests = new ArrayList<Object>();
    // Add instances of classes with @RaceTest annotated methods.
    tests.add(new EasyTests());
    tests.add(new MediumTests());
  }

  public void parseArgs(String[] args) {
    for (String s : args) {
      if (s.startsWith(REGEXP_PREFIX)) {
        regexp = s.substring(REGEXP_PREFIX.length());
        out.println("test name regexp = " + regexp);
      } else if (s.equals(VERBOSE_FLAG)) {
        verbose = true;
        out.println("verbose");
      } else if (s.equals(IGNORE_DISABLE_FLAG)) {
        ignoreDisable = true;
        out.println("Ignore @Disable");
      } else if (s.equals(IGNORE_EXPECTED_RACE_FLAG)) {
        ignoreExpectedRace = true;
        out.println("Ignore expected race");
      }
    }
  }

  public void run() {
    out.println(">>>> org.jtsan.TestRunner: START");
    Pattern includePattern = Pattern.compile(regexp);

    ArrayList<String[]> disableTests = new ArrayList<String[]>();

    for (Object testObject : tests) {
      Class testsClass = testObject.getClass();
      Method[] methods = testsClass.getDeclaredMethods();
      if (verbose) {
        out.println();
      }
      out.println(">>>> org.jtsan.TestRunner: Handle " + testsClass);

      for (Method method : methods) {
        String methodName = method.getName();
        RaceTest raceTestAnnotation = method.getAnnotation(RaceTest.class);
        if (raceTestAnnotation == null || !includePattern.matcher(methodName).matches()) {
          continue;
        }
        Disable disableAnnotation = method.getAnnotation(Disable.class);
        if (disableAnnotation != null) {
          disableTests.add(new String[] {methodName, disableAnnotation.reason()});
          if (!ignoreDisable) {
            continue;
          }
        }
        if (verbose) {
          out.println();
        }
        out.println("======== " + methodName + " ========");
        if (disableAnnotation != null) {
          out.println("Warning: test disabled by default. Reason: "
              + disableAnnotation.reason());
        }
        if (verbose) {
          out.println("Description: " + raceTestAnnotation.description());
          out.println("Race = " + raceTestAnnotation.race());
        }

        RaceDetectorApi.print("======== " + methodName + " ========");
        RaceDetectorApi.print("Description: " + raceTestAnnotation.description());
        RaceDetectorApi.print("Race = " + raceTestAnnotation.race());

        if (!ignoreExpectedRace && raceTestAnnotation.race()) {
          RaceDetectorApi.expectRaceBegin();
        }
        try {
          method.invoke(testObject);
        } catch (Exception e) {
          throw new RuntimeException("Exception when run test " + methodName, e);
        }
        if (!ignoreExpectedRace && raceTestAnnotation.race()) {
          RaceDetectorApi.expectRaceEnd();
        }
      }
    }
    if (disableTests.size() > 0) {
      out.println(">>>> org.jtsan.TestRunner: Following tests are disable:");
    }
    for (String[] disableTest : disableTests) {
      out.println("DISABLE " + disableTest[0] + ". Reason: " + disableTest[1]);
    }
    out.close();
  }

  private String regexp;
  private boolean ignoreDisable;
  private boolean ignoreExpectedRace;
  private boolean verbose;
  private ArrayList<Object> tests;
}
