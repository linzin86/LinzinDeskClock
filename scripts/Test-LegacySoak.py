"""Observe a signed Legacy APK for 30 real minutes using controlled network/date boundaries."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time

def preference_metadata(line):
    columns = line.split()
    assert len(columns) >= 4 and re.fullmatch(r"-(?:[r-][w-][x-]){3}", columns[0]), "Unsupported preference metadata"
    first = 2 if columns[1].isdigit() and int(columns[1]) < 10000 else 1
    owner, group = columns[first:first + 2]
    assert all(re.fullmatch(r"[A-Za-z0-9_]+", name) for name in (owner, group)), "Unsafe ownership metadata"
    mode = "".join(str(sum(bit for char, bit in zip(columns[0][index:index + 3], (4, 2, 1)) if char != "-"))
                   for index in (1, 4, 7))
    return mode, owner, group


def restore_preferences(adb, preferences, out):
    for remote, saved in preferences.items():
        if saved is None:
            adb("shell", "rm", "-f", remote)
        else:
            local, metadata = saved
            adb("push", str(local), remote)
            mode, owner, group = metadata
            adb("shell", "chown", owner + "." + group, remote)
            adb("shell", "chmod", mode, remote)
            assert preference_metadata(adb("shell", "ls", "-l", remote)) == metadata, "Preference ownership or permissions were not restored"
            verified = out / (local.name + ".restored")
            adb("pull", remote, str(verified))
            assert verified.read_bytes() == local.read_bytes(), "Preferences were not restored"



def check_runtime(logs, previous_marker, marker, pid, package):
    assert previous_marker and marker in logs and previous_marker in logs, "Native log coverage gap"
    interval = logs.split(previous_marker, 1)[1]
    assert "ANR in " + package not in interval
    assert not re.search(r"E/AndroidRuntime\(\s*" + str(pid) + r"\):.*FATAL EXCEPTION", interval)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--console-port", required=True, type=int)
    parser.add_argument("--apk", required=True, type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    adb_path = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "AppData/Local/Android/Sdk"))) / "platform-tools/adb.exe"
    test_apk = repo / "app-legacy/build/outputs/apk/androidTest/debug/app-legacy-debug-androidTest.apk"
    package = "com.linzin.linzindeskclock.legacy.spike"
    cache = "/data/data/" + package + "/cache/"
    out = repo / "tmp" / ("legacy-soak-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S-%f"))
    out.mkdir(parents=True, exist_ok=False)
    assert args.serial in ("emulator-" + str(args.console_port), "127.0.0.1:" + str(args.console_port + 1)), "Serial and isolated console port must agree"
    assert 5554 <= args.console_port <= 5682 and args.console_port % 2 == 0
    for required in (adb_path, test_apk, args.apk):
        assert required.is_file(), "Missing input: " + str(required)


    def adb(*arguments, timeout=15):
        result = subprocess.run([str(adb_path), "-s", args.serial, *arguments], capture_output=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace") + result.stdout.decode(errors="replace"))
        return result.stdout.decode(errors="replace").replace("\r", "")


    def read(name):
        return adb("shell", "cat", cache + name, timeout=6)


    def wake_console():
        with socket.create_connection(("127.0.0.1", args.console_port), timeout=3) as connection:
            connection.settimeout(3)
            stream = connection.makefile("rwb", buffering=0)

            def reply():
                rows = []
                while True:
                    line = stream.readline()
                    if not line:
                        raise RuntimeError("Emulator console closed")
                    value = line.decode(errors="replace").strip()
                    rows.append(value)
                    if value == "OK" or value.startswith("KO:"):
                        assert value == "OK", value
                        return rows

            if any("Authentication required" in line for line in reply()):
                token = (Path.home() / ".emulator_console_auth_token").read_text().strip()
                stream.write(("auth " + token + "\n").encode())
                reply()
            stream.write(b"event send EV_KEY:KEY_POWER:1 EV_KEY:KEY_POWER:0\n")
            reply()


    def ack(state):
        token = state["nonce"] + "-" + state["phase"]
        assert re.fullmatch(r"[0-9]+-[0-9a-f]+-[a-z-]+", token), "Unexpected controller token"
        local = out / "ack.txt"
        local.write_text(token, encoding="utf-8")
        adb("push", str(local), cache + "linzin-soak-ack.pending")
        adb("shell", "mv", cache + "linzin-soak-ack.pending", cache + "linzin-soak-ack.txt")


    def power_lines(events):
        return [line for line in events.splitlines() if "power_screen_state" in line]


    def restore_clock():
        adb("shell", "date", "-s", datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d.%H%M%S"))
        value = adb("shell", "date", "+%Y%m%d.%H%M%S").strip()
        observed = datetime.datetime.strptime(value, "%Y%m%d.%H%M%S").replace(tzinfo=datetime.timezone.utc)
        assert abs(observed.timestamp() - time.time()) < 10, "Emulator clock was not restored"


    assert adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Emulator required; never run on hardware"
    assert adb("shell", "getprop", "ro.build.version.sdk").strip() in ("14", "19")
    assert "uid=0" in adb("shell", "id"), "Root AOSP emulator required for date control"
    assert adb("shell", "date", "+%z").strip() == "+0000", "UTC emulator required for reversible date fixture"
    for artifact in (args.apk.resolve(), test_apk):
        assert "Success" in adb("install", "-r", "-t", str(artifact), timeout=90)
    adb("shell", "am", "force-stop", package)
    preferences = {}
    for name in ("desk_clock_prefs.xml", "desk_clock_prefs.xml.bak"):
        remote = "/data/data/" + package + "/shared_prefs/" + name
        local = out / name
        try:
            adb("pull", remote, str(local))
        except RuntimeError as error:
            if not any(message in str(error).lower() for message in ("does not exist", "no such file or directory")):
                raise
            preferences[remote] = None
        else:
            preferences[remote] = (local, preference_metadata(adb("shell", "ls", "-l", remote)))
    installed = adb("shell", "pm", "path", package).strip().removeprefix("package:")
    adb("pull", installed, str(out / "installed.apk"), timeout=45)
    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    assert hashlib.sha256((out / "installed.apk").read_bytes()).hexdigest() == digest
    for name in ("linzin-soak-phase.json", "linzin-soak-result.json", "linzin-soak-ack.txt", "linzin-soak-ack.pending"):
        adb("shell", "rm", "-f", cache + name)

    started = time.monotonic()
    clock_changed = False
    nonce = None
    handled = set()
    previous_marker = "LINZIN_CONTROLLED_BEGIN_" + str(time.time_ns())
    adb("shell", "log", "-t", "LinzinSoak", previous_marker)
    sample_index = 0
    next_sample = 0.0
    completed_result = None
    child_log = (out / "instrumentation.log").open("wb")
    child = subprocess.Popen([str(adb_path), "-s", args.serial, "shell", "am", "instrument", "-w", "-e", "mode",
                              "soakControlled", package + ".test/com.linzin.legacy.LegacyInstrumentation"], stdout=child_log, stderr=subprocess.STDOUT)
    print("OUTPUT", out, "APK", digest, flush=True)
    try:
        while time.monotonic() - started < 2100:
            try:
                result = json.loads(read("linzin-soak-result.json"))
            except (RuntimeError, subprocess.TimeoutExpired, json.JSONDecodeError):
                result = None
            if result is not None:
                (out / "native-result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
                assert nonce and result["nonce"] == nonce and result["status"] == "PASS", result
                assert result["duration_ms"] >= 1800000 and time.monotonic() - started >= 1800
                final_logs = adb("logcat", "-d", "-v", "time", "AndroidRuntime:E", "ActivityManager:I", "LinzinSoak:I", "*:S")
                (out / "runtime-final.txt").write_text(final_logs, encoding="utf-8")
                check_runtime(final_logs, previous_marker, previous_marker, result["pid"], package)
                stats = [result[key][result[key].rfind(")") + 2:].split()[19] for key in ("initial_stat", "final_stat")]
                assert stats[0] == stats[1], "Process changed"
                result.update(apk_sha256=digest, host_duration_seconds=time.monotonic() - started,
                              linked_log_samples=sample_index, acceptance="PENDING_CLEANUP")
                (out / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
                completed_result = result
                break
            try:
                state = json.loads(read("linzin-soak-phase.json"))
            except (RuntimeError, subprocess.TimeoutExpired, json.JSONDecodeError):
                if child.poll() is not None:
                    raise RuntimeError("Instrumentation exited without a valid result; see " + str(out / "instrumentation.log"))
                time.sleep(2)
                continue
            if nonce is None:
                nonce = state["nonce"]
            assert state["nonce"] == nonce
            phase = state["phase"]
            if phase not in handled and phase != "observing":
                print("PHASE", phase, "elapsed", round(time.monotonic() - started), flush=True)
                (out / (phase + ".json")).write_text(json.dumps(state), encoding="utf-8")
                if phase == "power-ready":
                    before = set(power_lines(adb("logcat", "-d", "-b", "events", "-v", "time")))
                    try:
                        adb("shell", "input", "keyevent", "26")
                        time.sleep(5)
                    finally:
                        wake_console()
                        adb("shell", "input", "keyevent", "82")
                    events = adb("logcat", "-d", "-b", "events", "-v", "time")
                    (out / "power-events.txt").write_text(events, encoding="utf-8")
                    fresh = [line for line in power_lines(events) if line not in before]
                    states = [re.search(r": \[([01]),", line)[1] for line in fresh]
                    assert states[-2:] == ["0", "1"], "Actual screen off/on events required: " + repr(fresh)
                    ack(state)
                elif phase == "date-before":
                    clock_changed = True
                    adb("shell", "date", "-s", "20261231.235950")
                    ack(state)
                elif phase == "date-after":
                    end = time.monotonic() + 25
                    while not adb("shell", "date", "+%Y%m%d.%H%M%S").strip().startswith("20270101."):
                        assert time.monotonic() < end, "Emulator did not cross midnight"
                        time.sleep(1)
                    ack(state)
                elif phase == "date-restore":
                    restore_clock()
                    clock_changed = False
                    ack(state)
                else:
                    raise AssertionError("Unknown controller phase: " + phase)
                handled.add(phase)
            if time.monotonic() >= next_sample:
                marker = "LINZIN_CONTROLLED_SAMPLE_" + str(sample_index) + "_" + str(time.time_ns())
                adb("shell", "log", "-t", "LinzinSoak", marker)
                logs = adb("logcat", "-d", "-v", "time", "AndroidRuntime:E", "ActivityManager:I", "LinzinSoak:I", "*:S")
                (out / ("runtime-%03d.txt" % sample_index)).write_text(logs, encoding="utf-8")
                check_runtime(logs, previous_marker, marker, state["pid"], package)
                previous_marker = marker
                sample_index += 1
                next_sample = time.monotonic() + 25
            time.sleep(25 if phase == "observing" else 1)
        else:
            raise TimeoutError("Controlled emulator observation exceeded 35 minutes")
    finally:
        try:
            if clock_changed:
                restore_clock()
        finally:
            try:
                adb("shell", "am", "force-stop", package)
                restore_preferences(adb, preferences, out)
            finally:
                try:
                    child.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    child.terminate()
                    child.wait(timeout=5)
                child_log.close()

    completed_result.update(acceptance="EMULATOR_CONTROLLED_ONLY", preference_restore_verified=True, am_watcher_return_code=child.returncode)
    (out / "result.json").write_text(json.dumps(completed_result, indent=2), encoding="utf-8")
    print("PASS", json.dumps(completed_result), flush=True)


if __name__ == "__main__":
    main()
