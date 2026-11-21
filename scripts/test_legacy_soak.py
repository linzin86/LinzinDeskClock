import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock

spec = importlib.util.spec_from_file_location("legacy_soak", Path(__file__).with_name("Test-LegacySoak.py"))
soak = importlib.util.module_from_spec(spec)
spec.loader.exec_module(soak)


class InstalledPackagePathTest(unittest.TestCase):
    def test_normal_path(self):
        self.assertEqual("/data/app/clock.apk", soak.installed_package_path("package:/data/app/clock.apk\r\n"))

    def test_linker_warning_does_not_become_a_path(self):
        warning = "WARNING: linker: libdvm.so has text relocations.\r\r\n"
        self.assertEqual("/data/app/clock.apk", soak.installed_package_path(warning + "package:/data/app/clock.apk\r\n" + warning))

    def test_missing_multiple_or_relative_paths_are_rejected(self):
        for output in ("No package found", "package:/one.apk\npackage:/two.apk", "package:relative.apk"):
            with self.assertRaises(AssertionError):
                soak.installed_package_path(output)


class MemoryObservationTest(unittest.TestCase):
    def state(self):
        return {"phase": "observing", "heap_used_bytes": 16 * 1024 * 1024, "pss_kib": 32 * 1024}

    def test_valid_sample(self):
        soak.check_memory(self.state())

    def test_missing_measurement_is_not_success(self):
        for key in ("heap_used_bytes", "pss_kib"):
            state = self.state()
            del state[key]
            with self.assertRaises(AssertionError):
                soak.check_memory(state)

    def test_budget_overrun_is_rejected(self):
        for key, limit in (("heap_used_bytes", 64 * 1024 * 1024), ("pss_kib", 80 * 1024)):
            state = self.state()
            state[key] = limit
            soak.check_memory(state)
            state[key] += 1
            with self.assertRaises(AssertionError):
                soak.check_memory(state)

    def test_non_observing_or_invalid_values_are_rejected(self):
        state = self.state()
        state["phase"] = "power-ready"
        with self.assertRaises(AssertionError):
            soak.check_memory(state)
        for value in (0, -1, True, "1024"):
            state = self.state()
            state["heap_used_bytes"] = value
            with self.assertRaises(AssertionError):
                soak.check_memory(state)


class MemoryCoverageTest(unittest.TestCase):
    def test_controller_rejects_long_pause_with_only_one_memory_observation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("platform-tools/adb.exe", "candidate.apk",
                         "app-legacy/build/outputs/apk/androidTest/debug/app-legacy-debug-androidTest.apk"):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"apk")
            elapsed = [0]
            markers = []
            state = dict(nonce="42-abcd", phase="observing", pid=42, elapsed_ms=60000,
                         heap_used_bytes=16000000, pss_kib=32000)
            result = dict(nonce="42-abcd", status="PASS", pid=42, duration_ms=1800000,
                          observing_started_ms=40000, clock_style="OET", samples=61,
                          initial_stat="42 (clock) S " + "0 " * 20, final_stat="42 (clock) S " + "0 " * 20)

            def run(command, **kwargs):
                args = command[3:]
                output = ""
                code = 0
                if args[:2] == ["shell", "getprop"]:
                    output = "1" if args[2] == "ro.kernel.qemu" else "14"
                elif args[:2] == ["shell", "id"]:
                    output = "uid=0"
                elif args[:2] == ["shell", "date"]:
                    output = "+0000"
                elif args[0] == "install":
                    output = "Success"
                elif args[0] == "pull":
                    if args[1] == "/installed.apk":
                        Path(args[2]).write_bytes(b"apk")
                    else:
                        output, code = "No such file or directory", 1
                elif args[:3] == ["shell", "pm", "path"]:
                    output = "package:/installed.apk"
                elif args[:2] == ["shell", "cat"]:
                    output = json.dumps(result if elapsed[0] >= 1800 else state) if args[2].endswith("result.json") else json.dumps(state)
                    if args[2].endswith("result.json") and elapsed[0] == 0:
                        output = "No such file or directory"
                elif args[:2] == ["shell", "log"]:
                    markers.append(args[-1])
                elif args[0] == "logcat":
                    output = "\n".join(markers)
                elif args[0] == "shell" and "LINZIN_REMOVED" in args[1]:
                    output = "LINZIN_REMOVED"
                return Mock(returncode=code, stdout=output.encode(), stderr=b"")

            child = Mock(returncode=0)
            with patch.object(soak, "__file__", str(root / "scripts/Test-LegacySoak.py")), \
                 patch.dict(soak.os.environ, ANDROID_HOME=str(root)), \
                 patch("sys.argv", ["soak", "--serial", "127.0.0.1:5561", "--console-port", "5560", "--apk", str(root / "candidate.apk")]), \
                 patch.object(soak.subprocess, "run", side_effect=run), \
                 patch.object(soak.subprocess, "Popen", return_value=child), \
                 patch.object(soak.time, "monotonic", side_effect=lambda: elapsed[0]), \
                 patch.object(soak.time, "sleep", side_effect=lambda seconds: elapsed.__setitem__(0, 1800)):
                with self.assertRaises(AssertionError):
                    soak.main()

    def test_complete_observing_window(self):
        soak.check_memory_coverage(range(60000, 1800000, 30000), 40000, 1800000)

    def test_sparse_or_truncated_observations_are_rejected(self):
        for samples in ([60000], [60000, 1770000], range(180000, 1800000, 30000),
                        range(60000, 1600000, 30000), []):
            with self.assertRaises(AssertionError):
                soak.check_memory_coverage(samples, 40000, 1800000)


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
    def test_failed_removal_without_remote_exit_status_is_rejected(self):
        with self.assertRaises(AssertionError):
            soak.remove_file(lambda *arguments: "", "/data/data/example/cache/stale")

    def test_confirmed_removal_is_accepted(self):
        soak.remove_file(lambda *arguments: "LINZIN_REMOVED\n", "/data/data/example/cache/stale")

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
