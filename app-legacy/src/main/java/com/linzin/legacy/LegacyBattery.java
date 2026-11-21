package com.linzin.legacy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.graphics.Color;
import android.widget.TextView;

final class LegacyBattery extends TextView {
    private boolean registered;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { showBattery(intent); }
    };
    LegacyBattery(Context context) {
        super(context); setTag("battery"); setTextColor(Color.WHITE); setBackgroundColor(0xb3000000); setTextSize(14);
        int pad = Math.round(4 * getResources().getDisplayMetrics().density); setPadding(pad, pad, pad, pad);
    }
    void start() {
        setVisibility(VISIBLE);
        if (registered) return;
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent sticky = Build.VERSION.SDK_INT >= 33 ? getContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                : getContext().registerReceiver(receiver, filter);
            registered = true;
            if (sticky != null) showBattery(sticky);
            else setText(R.string.battery_unknown);
        } catch (RuntimeException error) { setText(R.string.battery_unknown); }
    }
    void stop() {
        if (!registered) return;
        registered = false;
        try { getContext().unregisterReceiver(receiver); }
        catch (IllegalArgumentException ignored) { }
    }
    private void showBattery(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0 || level > scale) { setText(R.string.battery_unknown); return; }
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        int label = status == BatteryManager.BATTERY_STATUS_CHARGING ? R.string.battery_charging
            : status == BatteryManager.BATTERY_STATUS_FULL ? R.string.battery_full
            : status == BatteryManager.BATTERY_STATUS_DISCHARGING ? R.string.battery_discharging
            : status == BatteryManager.BATTERY_STATUS_NOT_CHARGING ? R.string.battery_not_charging : R.string.battery_status_unknown;
        setText(getContext().getString(R.string.battery_readout, (int) (level * 100L / scale), getContext().getString(label)));
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
