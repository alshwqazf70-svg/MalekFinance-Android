#!/usr/bin/env python3
"""Record the actual APK file length (not the GitHub artifact ZIP length)."""
from pathlib import Path
import argparse
import os


def inspect(path: Path) -> str:
    size = path.stat().st_size
    report = f'{path.name}: {size} bytes = {size / 1_000_000:.6f} MB = {size / 1_048_576:.6f} MiB\n'
    path.with_name('APK-SIZE.txt').write_text(report)
    print(report, end='')
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a') as output:
            output.write('\n### Actual APK size\n\n' + report)
    if not 0 < size < 30_000_000:
        raise ValueError(f'APK must be smaller than 30 MB (30,000,000 bytes); actual={size}')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    args = parser.parse_args()
    try:
        inspect(args.apk)
    except (OSError, ValueError) as error:
        parser.exit(1, str(error) + '\n')


if __name__ == '__main__':
    main()
