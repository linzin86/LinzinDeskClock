import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("hardware_soak", Path(__file__).with_name("Test-LegacyHardwareSoak.py"))
hardware = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hardware)


class HardwareSoakTest(unittest.TestCase):
    def test_power_control_rejects_a_screen_that_never_turned_off_and_still_wakes(self):
        calls = []
        def adb(*arguments):
            calls.append(arguments)
            return "mWakefulness=Awake\nDisplay Power: state=ON" if arguments[:2] == ("shell", "dumpsys") else ""
        with tempfile.TemporaryDirectory() as directory, patch.object(hardware.time, "sleep"), self.assertRaises(AssertionError):
            hardware.power_cycle(adb, "123-abc", Path(directory))
        self.assertIn(("shell", "input", "keyevent", "224"), calls)
        self.assertFalse(any("broadcast" in call for call in calls))

    def test_power_control_signals_native_observers_after_each_real_transition(self):
        calls, reports = [], iter(["mWakefulness=Asleep\nDisplay Power: state=OFF", "mWakefulness=Awake\nDisplay Power: state=ON"])
        def adb(*arguments):
            calls.append(arguments)
            return next(reports) if arguments[:2] == ("shell", "dumpsys") else ""
        with tempfile.TemporaryDirectory() as directory, patch.object(hardware.time, "sleep"):
            hardware.power_cycle(adb, "123-abc", Path(directory))
        broadcasts = [call for call in calls if "broadcast" in call]
        self.assertEqual(["false", "true"], [call[-1] for call in broadcasts])
        self.assertTrue(all(hardware.PROFILE in call and "123-abc" in call for call in broadcasts))

    def records(self):
        return [
            {"kind": "start", "nonce": "123-abc", "pid": 123, "duration_ms": 1800000},
            {"kind": "power_ready", "nonce": "123-abc"},
            {"kind": "power_off", "nonce": "123-abc", "interactive": False},
            {"kind": "power_on", "nonce": "123-abc", "interactive": True},
            *[{"kind": "sample", "nonce": "123-abc", "pid": 123, "phase": "observing",
               "elapsed_ms": elapsed, "heap_used_bytes": 16000000, "pss_kib": 32000}
              for elapsed in range(30000, 1800001, 30000)],
            {"kind": "result", "nonce": "123-abc", "pid": 123, "status": "PASS", "duration_ms": 1800000,
             "observing_started_ms": 30000, "home": True, "power": True, "offline": "26.0 unavailable",
             "recovered": "27.0", "clock_style": "OET", "preferences_restored": True,
             "clock_unchanged": True, "samples": 60, "initial_stat": "123 (clock) S " + "0 " * 18 + "500",
             "final_stat": "123 (clock) S " + "0 " * 18 + "500"},
        ]

    def test_complete_window_is_accepted(self):
        hardware.validate_run(self.records(), 1800000, 1801)

    def test_missing_samples_or_long_gap_is_rejected(self):
        records = self.records()
        for broken in ([r for r in records if r["kind"] != "sample"],
                       [r for r in records if r["kind"] != "sample" or r["elapsed_ms"] < 50000]):
            with self.assertRaises(AssertionError):
                hardware.validate_run(broken, 1800000, 1801)

    def test_invalid_native_or_host_duration_is_rejected(self):
        for duration, seconds in ((28800000, 1801), (1800000, 1799)):
            with self.assertRaises(AssertionError):
                hardware.validate_run(self.records(), duration, seconds)

    def test_failed_or_unrestored_native_result_is_rejected(self):
        for key, value in (("status", "FAIL"), ("preferences_restored", False), ("clock_unchanged", False),
                           ("home", False), ("power", False), ("clock_style", "TK"),
                           ("final_stat", "123 (clock) S " + "0 " * 18 + "501")):
            records = self.records()
            records[-1][key] = value
            with self.subTest(key=key), self.assertRaises(AssertionError):
                hardware.validate_run(records, 1800000, 1801)

    def test_stale_or_overbudget_sample_is_rejected(self):
        for key, value in (("pid", 124), ("nonce", "stale"), ("heap_used_bytes", 64 * 1024 * 1024 + 1),
                           ("pss_kib", 0), ("pss_kib", True), ("elapsed_ms", "30000")):
            records = self.records()
            records[4][key] = value
            with self.subTest(key=key), self.assertRaises(AssertionError):
                hardware.validate_run(records, 1800000, 1801)

    def test_missing_reversed_or_duplicate_phase_is_rejected(self):
        for broken in (self.records()[1:], self.records()[:-1], self.records() + [self.records()[-1]],
                       [self.records()[0], *self.records()[2:]],
                       [self.records()[0], self.records()[2], self.records()[1], *self.records()[3:]]):
            with self.assertRaises(AssertionError):
                hardware.validate_run(broken, 1800000, 1801)

    def controller(self, root, calls, wrong_manifest=False, fail_uninstall=False):
        script = root / "scripts/Test-LegacyHardwareSoak.py"
        script.parent.mkdir()
        script.write_bytes(Path(hardware.__file__).read_bytes())
        apk, test_apk = root / "product.apk", root / "tests.apk"
        apk.write_bytes(b"product")
        test_apk.write_bytes(b"tests")
        packages = {hardware.PRIMARY}

        def run(arguments, **kwargs):
            arguments = [str(arg) for arg in arguments]
            if "xmltree" in arguments:
                is_test = arguments[3] == str(test_apk)
                package = hardware.PROFILE + (".test" if is_test else "")
                target = hardware.PRIMARY if wrong_manifest else hardware.PROFILE
                output = f'package="{package}"\ntargetPackage="{target}"'
            else:
                self.assertEqual("0123456789ABCDEF", arguments[2])
                command = arguments[3:]
                calls.append(command)
                if command[:2] == ["shell", "getprop"]:
                    output = {"ro.build.version.sdk": "23", "ro.product.model": "SOYES 7S", "ro.kernel.qemu": "",
                              "ro.product.cpu.abi": "armeabi-v7a"}[command[2]]
                elif command[:3] == ["shell", "pm", "path"]:
                    output = "package:/data/app/clock.apk" if command[3] in packages else ""
                elif command[0] == "install":
                    packages.add(hardware.PROFILE + (".test" if command[-1] == str(test_apk) else ""))
                    output = "Success"
                elif command[0] == "pull":
                    Path(command[2]).write_bytes(apk.read_bytes())
                    output = ""
                elif command[0] == "uninstall":
                    if fail_uninstall and command[1].endswith(".test"):
                        output = "Failure"
                    else:
                        packages.discard(command[1])
                        output = "Success"
                elif command[:3] == ["shell", "am", "start"]:
                    output = "Status: ok"
                else:
                    output = ""
            return subprocess.CompletedProcess(arguments, 0, output.encode(), b"")

        return script, apk, test_apk, run

    def test_wrong_target_manifest_is_rejected_before_any_install(self):
        with tempfile.TemporaryDirectory() as directory:
            root, calls = Path(directory), []
            script, apk, test_apk, run = self.controller(root, calls, wrong_manifest=True)
            with patch.object(hardware, "__file__", str(script)), patch.dict("os.environ", ANDROID_HOME=str(root)), \
                 patch("sys.argv", [str(script), "--serial", "0123456789ABCDEF", "--apk", str(apk), "--test-apk", str(test_apk), "--window", "30m"]), \
                 patch.object(hardware.subprocess, "run", side_effect=run), self.assertRaises(AssertionError):
                hardware.main()
            self.assertFalse(any(command[0] == "install" for command in calls))

    def test_failed_cleanup_still_attempts_other_removal_and_primary_restore(self):
        with tempfile.TemporaryDirectory() as directory:
            root, calls = Path(directory), []
            script, apk, test_apk, run = self.controller(root, calls, fail_uninstall=True)
            with patch.object(hardware, "__file__", str(script)), patch.dict("os.environ", ANDROID_HOME=str(root)), \
                 patch("sys.argv", [str(script), "--serial", "0123456789ABCDEF", "--apk", str(apk), "--test-apk", str(test_apk), "--window", "30m"]), \
                 patch.object(hardware.subprocess, "run", side_effect=run), \
                 patch.object(hardware.subprocess, "Popen", side_effect=RuntimeError("launch failed")), \
                 self.assertRaisesRegex(AssertionError, "cleanup incomplete"):
                hardware.main()
            self.assertIn(["uninstall", hardware.PROFILE], calls)
            self.assertTrue(any(command[:3] == ["shell", "am", "start"] and hardware.PRIMARY in command[-1] for command in calls))


if __name__ == "__main__":
    unittest.main()
