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
 * @author Konstantin Serebryany
 * @author Sergey Vorobyev
 */
public class TestRunner {

  private final String REGEXP_PREFIX = "-test_filter=";
  private final String VERBOSE_FLAG = "-verbose";
  private final String IGNORE_EXCLUDED_FLAG = "-all";
  private final String IGNORE_EXPECTED_RACE_FLAG = "-ignore_expected";
  protected PrintWriter out;

  private String regexp;
  private boolean positiveRegexp;
  private boolean ignoreExcluded;
  private boolean ignoreExpectedRace;
  private boolean verbose;
  private ArrayList<Object> tests;
  
  public static void main(String[] args) {
    TestRunner runner = new TestRunner();
    runner.parseArgs(args);
    runner.run();
  }

  public TestRunner() {
    out = new PrintWriter(System.out, true);
    regexp = ".*";
    verbose = false;
    ignoreExcluded = false;
    ignoreExpectedRace = false;
    positiveRegexp = true;
    tests = new ArrayList<Object>();
    // Add instances of classes with @RaceTest annotated methods.
    tests.add(new EasyTests());
    tests.add(new MediumTests());
    tests.add(new CustomTests());
  }

  public void parseArgs(String[] args) {
    for (String s : args) {
      if (s.startsWith(REGEXP_PREFIX)) {
        if (s.charAt(REGEXP_PREFIX.length()) == '-') {
          regexp = s.substring(REGEXP_PREFIX.length() + 1);
          positiveRegexp = false;
          out.println("test negative filter regexp = " + regexp);
        } else {
          regexp = s.substring(REGEXP_PREFIX.length());
          out.println("test filter regexp = " + regexp);
        }
      } else if (s.equals(VERBOSE_FLAG)) {
        verbose = true;
        out.println("verbose");
      } else if (s.equals(IGNORE_EXCLUDED_FLAG)) {
        ignoreExcluded = true;
        out.println("Ignore @ExcludedTest");
      } else if (s.equals(IGNORE_EXPECTED_RACE_FLAG)) {
        ignoreExpectedRace = true;
        out.println("Ignore expected race");
      }
    }
  }

  public void run() {
    out.println(">>>> org.jtsan.TestRunner: START");
    Pattern pattern = Pattern.compile(regexp);

    ArrayList<String[]> excludedTests = new ArrayList<String[]>();

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
        if (raceTestAnnotation == null) {
          continue;
        }
        if (pattern.matcher(methodName).matches() ^ positiveRegexp) {
          continue;
        }
        ExcludedTest excludedTestAnnotation = method.getAnnotation(ExcludedTest.class);
        if (excludedTestAnnotation != null) {
          excludedTests.add(new String[] {methodName, excludedTestAnnotation.reason()});
          if (!ignoreExcluded) {
            continue;
          }
        }
        if (verbose) {
          out.println();
        }
        out.println("======== " + methodName + " ========");
        if (excludedTestAnnotation != null) {
          out.println("Warning: test excluded by default. Reason: "
              + excludedTestAnnotation.reason());
        }
        if (verbose) {
          out.println("Description: " + raceTestAnnotation.description());
          out.println("Race = " + raceTestAnnotation.expectRace());
        }

        RaceDetectorApi.print("======== " + methodName + " ========");
        RaceDetectorApi.print("Description: " + raceTestAnnotation.description());
        RaceDetectorApi.print("Race = " + raceTestAnnotation.expectRace());

        if (!ignoreExpectedRace && raceTestAnnotation.expectRace()) {
          RaceDetectorApi.expectRaceBegin();
        }
        try {
          method.invoke(testObject);
        } catch (Exception e) {
          throw new RuntimeException("Exception while running test " + methodName, e);
        }
        if (!ignoreExpectedRace && raceTestAnnotation.expectRace()) {
          RaceDetectorApi.expectRaceEnd();
        }
      }
    }
    if (excludedTests.size() > 0) {
      out.println(">>>> org.jtsan.TestRunner: Exclude list:");
    }
    for (String[] disableTest : excludedTests) {
      out.printf("EXCL %-30sReason: %s\n", disableTest[0], disableTest[1]);
    }
    out.close();
  }


}
