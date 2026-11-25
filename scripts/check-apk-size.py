#!/usr/bin/env python3
"""Fail before publishing an application APK larger than 30 decimal MB."""
import argparse
from pathlib import Path

MAX_BYTES = 30_000_000


def check_size(path: Path) -> str:
    if not path.is_file() or path.suffix.lower() != '.apk':
        raise ValueError(f'Not an APK file: {path}')
    size = path.stat().st_size
    if size <= 0 or size > MAX_BYTES:
        raise ValueError(f'{path}: {size:,} bytes; allowed range is 1–{MAX_BYTES:,} bytes (30 MB)')
    return f'PASS: {path}: {size:,} bytes ({size / 1_000_000:.2f} MB), limit 30 MB'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apks', type=Path, nargs='+')
    args = parser.parse_args()
    try:
        for path in args.apks:
            print(check_size(path))
    except ValueError as error:
        parser.exit(1, f'ERROR: {error}\n')


if __name__ == '__main__':
    main()
