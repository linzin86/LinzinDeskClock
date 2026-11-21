import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("legacy_soak", Path(__file__).with_name("Test-LegacySoak.py"))
soak = importlib.util.module_from_spec(spec)
spec.loader.exec_module(soak)


class RuntimeCoverageTest(unittest.TestCase):
    def test_first_segment_cannot_discard_pre_marker_anr(self):
        logs = "ANR in example.clock\nSAMPLE0\nclock recovered\n"
        with self.assertRaises(AssertionError):
            soak.check_runtime(logs, None, "SAMPLE0", 123, "example.clock")

    def test_begin_marker_covers_early_recovered_anr(self):
        logs = "BEGIN\nANR in example.clock\nSAMPLE0\nclock recovered\n"
        with self.assertRaises(AssertionError):
            soak.check_runtime(logs, "BEGIN", "SAMPLE0", 123, "example.clock")

    def test_clean_complete_segment(self):
        soak.check_runtime("old history\nBEGIN\nSAMPLE0\n", "BEGIN", "SAMPLE0", 123, "example.clock")

    def test_crash_before_first_sample_is_rejected(self):
        with self.assertRaises(AssertionError):
            soak.check_runtime("BEGIN\nE/AndroidRuntime( 123): FATAL EXCEPTION: main\nSAMPLE0\n", "BEGIN", "SAMPLE0", 123, "example.clock")

    def test_missing_previous_marker_is_rejected(self):
        with self.assertRaises(AssertionError):
            soak.check_runtime("SAMPLE0\n", "MISSING", "SAMPLE0", 123, "example.clock")


class PreferenceRestoreTest(unittest.TestCase):
    def test_old_android_metadata(self):
        self.assertEqual(("660", "app_40", "app_40"),
                         soak.preference_metadata("-rw-rw---- app_40 app_40 655 2026-11-17 12:39 desk_clock_prefs.xml"))

    def test_metadata_with_link_count(self):
        self.assertEqual(("600", "u0_a40", "u0_a40"),
                         soak.preference_metadata("-rw------- 1 u0_a40 u0_a40 655 2026-11-17 12:39 desk_clock_prefs.xml"))

    def test_rejects_non_regular_or_unsafe_metadata(self):
        for line in ("lrwxrwxrwx root root 20 file", "-rw------- bad;owner root 20 file"):
            with self.assertRaises(AssertionError):
                soak.preference_metadata(line)

    def test_restore_preserves_owner_group_and_private_mode(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            original = out / "desk_clock_prefs.xml"
            original.write_bytes(b"<map />")
            remote = "/data/data/com.linzin.linzindeskclock.legacy.spike/shared_prefs/desk_clock_prefs.xml"
            calls = []

            def adb(*arguments):
                calls.append(arguments)
                if arguments[0] == "pull":
                    Path(arguments[2]).write_bytes(original.read_bytes())
                return "-rw-rw---- app_40 app_40 7 2026-11-17 12:39 desk_clock_prefs.xml"

            soak.restore_preferences(adb, {remote: (original, ("660", "app_40", "app_40"))}, out)
            self.assertIn(("shell", "chown", "app_40.app_40", remote), calls)
            self.assertIn(("shell", "chmod", "660", remote), calls)
            self.assertIn(("shell", "ls", "-l", remote), calls)

    def test_matching_bytes_do_not_hide_wrong_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            original = out / "prefs.xml"
            original.write_bytes(b"<map />")

            def adb(*arguments):
                if arguments[0] == "pull":
                    Path(arguments[2]).write_bytes(original.read_bytes())
                return "-rw-rw-rw- root root 7 2026-11-17 12:39 prefs.xml"

            with self.assertRaises(AssertionError):
                soak.restore_preferences(adb, {"/cache/prefs.xml": (original, ("660", "app_40", "app_40"))}, out)


if __name__ == "__main__":
    unittest.main()
