"""Boundary regressions for the 30 decimal MB distribution limit."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('apk_size', Path(__file__).with_name('check-apk-size.py'))
apk_size = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apk_size)


class ApkSizeTest(unittest.TestCase):
    def test_size_boundaries(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'app.apk'
            for size, valid in [(0, False), (1, True), (29_999_999, True),
                                (30_000_000, True), (30_000_001, False)]:
                with self.subTest(size=size):
                    with path.open('wb') as output:
                        output.truncate(size)
                    if valid:
                        self.assertIn('PASS:', apk_size.check_size(path))
                    else:
                        with self.assertRaises(ValueError):
                            apk_size.check_size(path)

    def test_missing_file_and_non_apk_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'missing.apk'
            with self.assertRaises(ValueError):
                apk_size.check_size(path)
            path = Path(directory) / 'not-an-apk.zip'
            path.write_bytes(b'example')
            with self.assertRaises(ValueError):
                apk_size.check_size(path)


if __name__ == '__main__':
    unittest.main()
