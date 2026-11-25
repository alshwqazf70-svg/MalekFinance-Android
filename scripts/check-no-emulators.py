#!/usr/bin/env python3
"""Project policy: CI must not download Android system images or start virtual devices.

This checks workflow source, not the tools preinstalled on GitHub-hosted runners.
Compiling instrumentation tests is allowed. Tests may be run on a real phone.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
forbidden = re.compile(
    r'android-emulator-runner|system-images;|avdmanager|emulator\s+.*-avd|'
    r'emulator-options\s*:|qemu-system-', re.IGNORECASE
)
failures = []
for path in sorted((root / '.github/workflows').glob('*.y*ml')):
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if line.lstrip().startswith('#'):
            continue
        if forbidden.search(line):
            failures.append(f'{path.relative_to(root)}:{number}: Android emulator configuration is forbidden')
if failures:
    raise SystemExit('\n'.join(failures))
print('PASS: workflow source contains no Android virtual-device setup or launch commands')
