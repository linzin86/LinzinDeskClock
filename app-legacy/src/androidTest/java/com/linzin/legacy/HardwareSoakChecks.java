package com.linzin.legacy;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

@TargetApi(23)
final class HardwareSoakChecks {
    static void run(LegacyInstrumentation test, String mode) throws Exception {
        long duration;
        if ("hardwareSoakChecks".equals(mode)) duration = 90000;
        else if ("hardwareSoak30".equals(mode)) duration = 30 * 60000L;
        else if ("hardwareSoak8h".equals(mode)) duration = 8 * 60 * 60000L;
        else throw new AssertionError("Unknown hardware soak mode: " + mode);
        Context context = test.getTargetContext();
        check(android.os.Build.VERSION.SDK_INT == 23, "Hardware soak currently requires the calibrated API23 device");
        check("com.linzin.linzindeskclock.legacy.profile".equals(context.getPackageName()), "Hardware soak requires an isolated profile package");
        check("SOYES 7S".equals(android.os.Build.MODEL), "Hardware soak requires the calibrated SOYES 7S");
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        check(!keyguard.isKeyguardSecure() && !keyguard.isKeyguardLocked(), "Unlock the non-secure test device before observation");
        check(power(context).isInteractive(), "Hardware observation must start with the screen on");
        LegacySoakChecks.run(test, duration, true);
    }

    static void emit(LegacyInstrumentation test, String kind, JSONObject value) throws Exception {
        value.put("kind", kind);
        Bundle progress = new Bundle();
        progress.putString("stream", "LINZIN_HARDWARE " + value.toString() + "\n");
        test.sendStatus(1, progress);
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    static void power(LegacyInstrumentation test, String nonce) throws Exception {
        Context context = test.getTargetContext();
        PowerManager manager = power(test.getTargetContext());
        check(manager.isInteractive(), "Screen must initially be on");
        AtomicBoolean off = new AtomicBoolean(), on = new AtomicBoolean();
        String action = context.getPackageName() + ".HARDWARE_SOAK_POWER";
        android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context ignored, android.content.Intent intent) {
                if (!nonce.equals(intent.getStringExtra("nonce"))) return;
                boolean desired = intent.getBooleanExtra("interactive", true), actual = manager.isInteractive();
                if (desired != actual || on.get() || desired != off.get()) return;
                try {
                    emit(test, desired ? "power_on" : "power_off", new JSONObject().put("nonce", nonce).put("interactive", actual));
                    if (desired) on.set(true); else off.set(true);
                } catch (Exception error) { throw new AssertionError(error); }
            }
        };
        // API23-only entry; DUMP limits this test signal to adb and privileged senders.
        context.registerReceiver(receiver, new android.content.IntentFilter(action), "android.permission.DUMP", null);
        try {
            emit(test, "power_ready", new JSONObject().put("nonce", nonce));
            long deadline = SystemClock.elapsedRealtime() + 90000;
            while (!on.get() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
            check(off.get() && on.get() && manager.isInteractive(), "Controller must complete native-confirmed screen off/on");
        } finally {
            context.unregisterReceiver(receiver);
        }
    }

    private static PowerManager power(Context context) {
        return (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
