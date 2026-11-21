package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

final class BatteryChecks {
    private static final class Source extends ContextWrapper {
        int registrations, removals;
        BroadcastReceiver receiver;
        Source(Activity activity) { super(activity); }
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            registrations++; this.receiver = receiver; return state(50, 100, BatteryManager.BATTERY_STATUS_CHARGING);
        }
        public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) { return registerReceiver(receiver, filter); }
        public void unregisterReceiver(BroadcastReceiver receiver) {
            if (this.receiver != receiver) throw new AssertionError("Only the owned receiver may be removed");
            removals++;
        }
    }
    static void run(Instrumentation test, Activity activity) {
        Source source = new Source(activity);
        LegacyBattery[] battery = new LegacyBattery[1];
        test.runOnMainSync(() -> { battery[0] = new LegacyBattery(source); battery[0].start(); battery[0].start(); });
        if (source.registrations != 1 || !battery[0].getText().toString().contains("50%")) throw new AssertionError("Battery needs one sticky receiver");
        test.runOnMainSync(() -> source.receiver.onReceive(source, state(75, 100, BatteryManager.BATTERY_STATUS_DISCHARGING)));
        if (!battery[0].getText().toString().contains("75%")) throw new AssertionError("Battery updates must replace the value");
        test.runOnMainSync(() -> source.receiver.onReceive(source, state(50, 0, BatteryManager.BATTERY_STATUS_UNKNOWN)));
        if (battery[0].getText().toString().contains("%")) throw new AssertionError("Invalid battery data must not invent a percentage");
        test.runOnMainSync(() -> { battery[0].stop(); battery[0].stop(); });
        if (source.removals != 1) throw new AssertionError("Pause must remove exactly one receiver");
        test.runOnMainSync(() -> { battery[0].start(); battery[0].stop(); });
        if (source.registrations != 2 || source.removals != 2) throw new AssertionError("Resume must restore one owned receiver");
    }
    private static Intent state(int level, int scale, int status) {
        return new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, level)
            .putExtra(BatteryManager.EXTRA_SCALE, scale).putExtra(BatteryManager.EXTRA_STATUS, status);
    }
}
