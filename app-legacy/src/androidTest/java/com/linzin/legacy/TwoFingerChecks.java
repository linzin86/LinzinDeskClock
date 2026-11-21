package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

final class TwoFingerChecks {
    static void run(Instrumentation test, Activity activity) {
        View clock = activity.getWindow().getDecorView().findViewWithTag("clock");
        SharedPreferences prefs = activity.getSharedPreferences("desk_clock_prefs", 0);
        int before = prefs.getInt("pref_digital_style_idx", 0);
        tap(test, clock, false);
        if (prefs.getInt("pref_digital_style_idx", 0) != (before + 1) % 3)
            throw new AssertionError("A stationary two-finger tap must work when the first pointer lifts first");
        int changed = prefs.getInt("pref_digital_style_idx", 0);
        tap(test, clock, true);
        if (prefs.getInt("pref_digital_style_idx", 0) != changed)
            throw new AssertionError("Moving the second pointer must not count as a two-finger tap");
    }
    private static void tap(Instrumentation test, View view, boolean moveSecond) {
        long down = SystemClock.uptimeMillis();
        float density = view.getResources().getDisplayMetrics().density;
        float x = view.getWidth() * .3f, y = view.getHeight() * .4f;
        float secondX = x + 40 * density;
        send(test, view, down, MotionEvent.ACTION_DOWN, new int[] { 3 }, new float[] { x }, y);
        send(test, view, down, MotionEvent.ACTION_POINTER_DOWN | 256, new int[] { 3, 7 }, new float[] { x, secondX }, y);
        if (moveSecond) {
            secondX += 40 * density;
            send(test, view, down, MotionEvent.ACTION_MOVE, new int[] { 3, 7 }, new float[] { x, secondX }, y);
        }
        send(test, view, down, MotionEvent.ACTION_POINTER_UP, new int[] { 3, 7 }, new float[] { x, secondX }, y);
        send(test, view, down, MotionEvent.ACTION_UP, new int[] { 7 }, new float[] { secondX }, y);
        test.waitForIdleSync();
    }
    private static void send(Instrumentation test, View view, long down, int action, int[] ids, float[] xs, float y) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            properties[i] = new MotionEvent.PointerProperties(); properties[i].id = ids[i]; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords(); coords[i].x = xs[i]; coords[i].y = y; coords[i].pressure = 1; coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, ids.length, properties, coords,
            0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { test.runOnMainSync(() -> view.dispatchTouchEvent(event)); }
        finally { event.recycle(); }
    }
}
