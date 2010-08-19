#!/usr/bin/python2.4

import os
import re
import sys

tmpdir = sys.argv[1]
print tmpdir

# Parse java output.
java_log = open(os.path.join(tmpdir, "java.log"), "r")
test_re = re.compile("===== ([^ ]+) ====")
exc_re = re.compile("Exception occured during transformation")
disable_re = re.compile("DISABLE (.*)")
results = {}
tests = []
disable_str = ""
disabled = 0
test = ""
for line in java_log:
  match_exc = False
  m = test_re.search(line)
  if m:
    test = m.group(1)
    tests.append(test)
    results[test] = True
  else:
    match_exc = exc_re.search(line)
  if test != "" and match_exc:
    results[test] = False
  if disable_re.search(line):
    disable_str += line
    disabled += 1
java_log.close()

# Parse offline ThreadSanitizer output.
tsan_log = open(os.path.join(tmpdir, "tsan.log"), "r")
notfound_re = re.compile("WARNING: expected race not found.")
found_re = re.compile("WARNING: Possible data race")
test = ""
for line in tsan_log:
  m = test_re.search(line)
  if m:
    test = m.group(1)
  if notfound_re.search(line):
    results[test] = False
  if found_re.search(line):
    results[test] = False
tsan_log.close()

# Produce test report.
passed = 0
failed = 0
for test in tests:
  if results[test]:
    res = " PASS  "
    passed += 1
  else:
    res = "!FAIL! "
    failed += 1
  print "%s %s" % (res, test)
print disable_str
print "----"
print "passed: %d, failed: %d, disabled: %d, total: %d" % \
(passed, failed, disabled, passed + failed + disabled)
