#!/usr/bin/env python3
"""Summarize actual JUnit XML into a file and an API-readable Actions notice."""
from pathlib import Path
import xml.etree.ElementTree as ET

reports = sorted(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
if not reports:
    raise SystemExit('No JVM test reports found')
tests = failures = errors = skipped = 0
for path in reports:
    for case in ET.parse(path).getroot().iter('testcase'):
        tests += 1
        failures += case.find('failure') is not None
        errors += case.find('error') is not None
        skipped += case.find('skipped') is not None
summary = f'{tests} JVM test cases: {tests-failures-errors-skipped} passed, {failures} failed, {errors} errors, {skipped} skipped ({len(reports)} XML reports)'
output = Path('app/build/reports/JVM-TEST-SUMMARY.txt')
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(summary + '\n')
print('::notice title=Actual JVM test results::' + summary)
if failures or errors or tests == 0:
    raise SystemExit(1)
