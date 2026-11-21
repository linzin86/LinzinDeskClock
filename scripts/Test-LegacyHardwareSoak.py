"""Observe an isolated Legacy profile on the authorized SOYES without changing its date."""
import argparse
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import queue
import re
import subprocess
import threading
import time

spec = importlib.util.spec_from_file_location("legacy_soak", Path(__file__).with_name("Test-LegacySoak.py"))
soak = importlib.util.module_from_spec(spec)
spec.loader.exec_module(soak)
PROFILE = "com.linzin.linzindeskclock.legacy.profile"
PRIMARY = "com.linzin.linzindeskclock.legacy.spike"


def validate_run(records, duration_ms, host_seconds):
    assert duration_ms in (90000, 1800000, 28800000) and host_seconds * 1000 >= duration_ms, "Incomplete real-time window"
    assert records and records[0].get("kind") == "start" and records[-1].get("kind") == "result"
    assert [r.get("kind") for r in records if r.get("kind") != "sample"] == ["start", "power_ready", "power_off", "power_on", "result"]
    start, result = records[0], records[-1]
    assert start.get("duration_ms") == duration_ms
    assert type(start.get("pid")) is int and start["pid"] > 0
    assert re.fullmatch(str(start["pid"]) + r"-[0-9a-f]+", start.get("nonce", ""))
    assert all(r.get("nonce") == start["nonce"] for r in records), "Stale observation nonce"
    assert records[2].get("interactive") is False and records[3].get("interactive") is True
    assert result.get("pid") == start["pid"] and result.get("status") == "PASS"
    assert type(result.get("duration_ms")) is int and result["duration_ms"] >= duration_ms
    assert all(result.get(key) is True for key in ("home", "power", "preferences_restored", "clock_unchanged"))
    assert result.get("clock_style") == "OET"
    assert "26.0" in result.get("offline", "") and "unavailable" in result["offline"]
    assert "27.0" in result.get("recovered", "") and "unavailable" not in result["recovered"]
    stats = [result.get(key, "").rsplit(")", 1)[-1].split() for key in ("initial_stat", "final_stat")]
    assert all(len(stat) > 19 for stat in stats) and stats[0][19] == stats[1][19], "Process start changed"
    samples = [r for r in records if r.get("kind") == "sample"]
    assert type(result.get("samples")) is int and result["samples"] == len(samples)
    for sample in samples:
        assert sample.get("pid") == start["pid"], "Process changed during observation"
        soak.check_memory(sample)
    times = [sample.get("elapsed_ms") for sample in samples]
    assert all(type(value) is int for value in times) and times == sorted(set(times)), "Duplicate or unordered samples"
    soak.check_memory_coverage(times, result.get("observing_started_ms"), result["duration_ms"])
    return result


def power_cycle(adb, nonce, out):
    assert re.fullmatch(r"[0-9]+-[0-9a-f]+", nonce), "Invalid native power nonce"
    def signal(interactive):
        adb("shell", "am", "broadcast", "-a", PROFILE + ".HARDWARE_SOAK_POWER", "-p", PROFILE,
            "--es", "nonce", nonce, "--ez", "interactive", str(interactive).lower())
    try:
        adb("shell", "input", "keyevent", "26")
        time.sleep(2)
        off = adb("shell", "dumpsys", "power")
        (out / "power-off.txt").write_text(off)
        assert "mWakefulness=Asleep" in off and "Display Power: state=OFF" in off
        signal(False)
        time.sleep(5)
    finally:
        adb("shell", "input", "keyevent", "224")
        adb("shell", "input", "swipe", "120", "400", "120", "80", "350")
    on = adb("shell", "dumpsys", "power")
    (out / "power-on.txt").write_text(on)
    assert "mWakefulness=Awake" in on and "Display Power: state=ON" in on
    signal(True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, choices=["0123456789ABCDEF"])
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--window", required=True, choices=["check", "30m", "8h"])
    args = parser.parse_args()
    sdk = Path(os.environ["ANDROID_HOME"])
    adb_path, aapt = sdk / "platform-tools/adb.exe", sdk / "build-tools/37.0.0/aapt2.exe"
    duration, mode = {"check": (90000, "hardwareSoakChecks"), "30m": (1800000, "hardwareSoak30"),
                      "8h": (28800000, "hardwareSoak8h")}[args.window]
    out = Path(__file__).resolve().parent.parent / "tmp" / ("hardware-soak-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
    out.mkdir(parents=True, exist_ok=False)

    def adb(*arguments, timeout=15):
        result = subprocess.run([str(adb_path), "-s", args.serial, *arguments], capture_output=True, timeout=timeout)
        assert result.returncode == 0, result.stderr.decode(errors="replace")
        return result.stdout.decode(errors="replace").replace("\r", "")

    def manifest(apk):
        result = subprocess.run([str(aapt), "dump", "xmltree", str(apk.resolve()), "--file", "AndroidManifest.xml"],
                                capture_output=True, timeout=15, check=True)
        return result.stdout.decode()

    def package_exists(package):
        return any(line.startswith("package:") for line in adb("shell", "pm", "path", package).splitlines())

    assert adb("shell", "getprop", "ro.build.version.sdk").strip() == "23"
    assert adb("shell", "getprop", "ro.product.model").strip() == "SOYES 7S"
    assert adb("shell", "getprop", "ro.kernel.qemu").strip() != "1"
    assert not package_exists(PROFILE) and not package_exists(PROFILE + ".test"), "Remove only your previous isolated test installation first"
    assert package_exists(PRIMARY), "Existing primary clock must remain available"
    assert f'package="{PROFILE}"' in manifest(args.apk)
    test_manifest = manifest(args.test_apk)
    assert f'package="{PROFILE}.test"' in test_manifest and re.search(r'targetPackage[^\n]*="' + re.escape(PROFILE) + r'"', test_manifest)
    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    inputs = dict(utc=datetime.datetime.now(datetime.timezone.utc).isoformat(), serial=args.serial, api=23,
                  apk_sha256=digest, test_apk_sha256=hashlib.sha256(args.test_apk.read_bytes()).hexdigest(), requested_duration_ms=duration)
    (out / "inputs.json").write_text(json.dumps(inputs, indent=2))
    (out / "controller.py").write_bytes(Path(__file__).read_bytes())
    original_settings = adb("shell", "settings get system accelerometer_rotation; settings get system user_rotation; wm density; settings get system font_scale")
    inputs["display_settings"] = original_settings
    inputs["battery_start"] = adb("shell", "dumpsys", "battery")
    inputs["abi"] = adb("shell", "getprop", "ro.product.cpu.abi").strip()
    (out / "inputs.json").write_text(json.dumps(inputs, indent=2))
    installed = []
    child = None
    records = []
    succeeded = False
    print("OUTPUT", out, flush=True)
    try:
        for artifact, package in ((args.apk, PROFILE), (args.test_apk, PROFILE + ".test")):
            installed.append(package)
            assert "Success" in adb("install", "-r", "-t", str(artifact.resolve()), timeout=90)
        path = soak.installed_package_path(adb("shell", "pm", "path", PROFILE))
        adb("pull", path, str(out / "installed.apk"), timeout=45)
        assert hashlib.sha256((out / "installed.apk").read_bytes()).hexdigest() == digest
        marker = "LINZIN_HARDWARE_BEGIN_" + str(time.time_ns())
        # This OEM drops INFO/WARN diagnostics; ERROR markers survive its native logger.
        adb("shell", "log", "-p", "e", "-t", "LinzinSoak", marker)
        began = time.monotonic()
        child = subprocess.Popen([str(adb_path), "-s", args.serial, "shell", "am", "instrument", "-w", "-r", "-e", "mode", mode,
                                  PROFILE + ".test/com.linzin.legacy.LegacyInstrumentation"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        lines = queue.Queue()

        def read_output():
            try:
                for line in iter(child.stdout.readline, b""):
                    lines.put((time.monotonic(), line.decode(errors="replace")))
            finally:
                lines.put((time.monotonic(), None))

        reader = threading.Thread(target=read_output, daemon=True)
        reader.start()
        next_log, log_index, last_sample = began, 0, None
        with (out / "instrumentation.log").open("w", encoding="utf-8") as log:
            while True:
                assert time.monotonic() - began <= duration / 1000 + 300, "Hardware soak timed out"
                try:
                    arrived, line = lines.get(timeout=1)
                except queue.Empty:
                    line = ""
                if line is None:
                    break
                log.write(line)
                log.flush()
                if line.startswith("INSTRUMENTATION_STATUS: stream=LINZIN_HARDWARE "):
                    record = json.loads(line.split("LINZIN_HARDWARE ", 1)[1])
                    records.append(record)
                    (out / "records.json").write_text(json.dumps(records, indent=2))
                    if record["kind"] == "power_ready":
                        power_cycle(adb, record["nonce"], out)
                    if record["kind"] == "sample":
                        soak.check_memory(record)
                        assert last_sample is None or arrived - last_sample <= 90, "Host memory observation gap"
                        last_sample = arrived
                    print(record["kind"], record.get("elapsed_ms", record.get("status", "")), flush=True)
                now = time.monotonic()
                assert last_sample is None or now - last_sample <= 90, "Host stopped receiving memory observations"
                if records and now >= next_log:
                    fresh = "LINZIN_HARDWARE_SAMPLE_" + str(time.time_ns())
                    adb("shell", "log", "-p", "e", "-t", "LinzinSoak", fresh)
                    runtime = adb("logcat", "-d", "-v", "time", "AndroidRuntime:E", "ActivityManager:I", "LinzinSoak:I", "*:S")
                    (out / ("runtime-%04d.txt" % log_index)).write_text(runtime)
                    soak.check_runtime(runtime, marker, fresh, records[0]["pid"], PROFILE)
                    marker, log_index, next_log = fresh, log_index + 1, now + 25
        child.wait(timeout=10)
        reader.join(timeout=3)
        assert child.returncode == 0 and not reader.is_alive()
        result = validate_run(records, duration, time.monotonic() - began)
        assert last_sample is not None and time.monotonic() - last_sample <= 90
        transcript = (out / "instrumentation.log").read_text()
        assert "INSTRUMENTATION_RESULT: stream=OK: " + mode in transcript and "INSTRUMENTATION_CODE: -1" in transcript
        final = adb("logcat", "-d", "-v", "time", "AndroidRuntime:E", "ActivityManager:I", "LinzinSoak:I", "*:S")
        (out / "runtime-final.txt").write_text(final)
        soak.check_runtime(final, marker, marker, records[0]["pid"], PROFILE)
        result.update(inputs, host_duration_seconds=time.monotonic() - began, linked_log_samples=log_index,
                      battery_end=adb("shell", "dumpsys", "battery"),
                      acceptance="PHYSICAL_SHORT_CHECK_ONLY" if args.window == "check" else "PHYSICAL_CONTROLLED_ONLY", cleanup_verified=False)
        (out / "result.json").write_text(json.dumps(result, indent=2))
        succeeded = True
    finally:
        cleanup = {}
        def clean(name, operation):
            try:
                cleanup[name] = bool(operation())
            except Exception as error:
                cleanup[name] = False
                cleanup[name + "_error"] = str(error)
        def stop_child():
            try:
                adb("shell", "am", "force-stop", PROFILE)
            finally:
                child.terminate()
                child.wait(timeout=10)
            return True
        if child is not None and child.poll() is None:
            clean("instrumentation_stopped", stop_child)
        for package in reversed(installed):
            clean(package, lambda package=package: "Success" in adb("uninstall", package, timeout=60) and not package_exists(package))
        clean("screen_on", lambda: not adb("shell", "input", "keyevent", "224").strip())
        clean("keyguard_dismissed", lambda: not adb("shell", "input", "keyevent", "82").strip())
        clean("primary_available", lambda: package_exists(PRIMARY))
        clean("display_settings_unchanged", lambda: original_settings == adb("shell", "settings get system accelerometer_rotation; settings get system user_rotation; wm density; settings get system font_scale"))
        clean("primary_launched", lambda: "Status: ok" in adb("shell", "am", "start", "-W", "-n", PRIMARY + "/com.linzin.legacy.LegacyActivity"))
        clean("primary_resumed", lambda: any("mResumedActivity" in line and PRIMARY in line
                                             for line in adb("shell", "dumpsys", "activity", "activities").splitlines()))
        (out / "cleanup.json").write_text(json.dumps(cleanup, indent=2))
        assert all(cleanup.values()), "Hardware test cleanup incomplete"
    assert succeeded
    result["cleanup_verified"] = True
    (out / "result.json").write_text(json.dumps(result, indent=2))
    print("PASS", json.dumps(result), flush=True)


if __name__ == "__main__":
    main()
