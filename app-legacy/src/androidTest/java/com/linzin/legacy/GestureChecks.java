package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

final class GestureChecks {
    static void run(Instrumentation test, Activity activity) {
        View clock = activity.getWindow().getDecorView().findViewWithTag("clock");
        SharedPreferences prefs = activity.getSharedPreferences("desk_clock_prefs", 0);
        twoFingerTap(test, clock);
        if (prefs.getInt("pref_digital_style_idx", 0) != 1) throw new AssertionError("Two-finger tap must change style");
        SystemClock.sleep(400);
        doubleTap(test, clock);
        if (!prefs.getBoolean("pref_digital_show_sec", false)) throw new AssertionError("Double tap must toggle seconds");
        SystemClock.sleep(400);
        swipe(test, clock, true);
        if (prefs.getInt("pref_main_page", 0) != 1) throw new AssertionError("Swipe up must change clock family");
        swipe(test, clock, false);
        if (prefs.getInt("pref_main_page", -1) != 0 || prefs.getInt("pref_digital_style_idx", 0) != 1)
            throw new AssertionError("Returning to a family must restore its style");
        swipe(test, clock, true, 400);
        if (prefs.getInt("pref_main_page", 0) != 1) throw new AssertionError("A slow swipe must also change clock family");
        swipe(test, clock, false, 400);
        if (prefs.getInt("pref_main_page", -1) != 0) throw new AssertionError("Slow reverse swipe must return");
        Button layout = activity.getWindow().getDecorView().findViewWithTag("layout_mode");
        test.runOnMainSync(() -> layout.performClick());
        test.waitForIdleSync();
        View side = activity.getWindow().getDecorView().findViewWithTag("side_panel");
        long layoutDeadline = SystemClock.elapsedRealtime() + 2000;
        while ((side.getWidth() == 0 || side.getHeight() == 0) && SystemClock.elapsedRealtime() < layoutDeadline) {
            SystemClock.sleep(20); test.waitForIdleSync();
        }
        if (side.getWidth() == 0 || side.getHeight() == 0) throw new AssertionError("Sidebar must finish layout before the gesture");
        swipe(test, side, true);
        if (prefs.getInt("pref_side_page", 0) != 1) throw new AssertionError("Sidebar swipe must switch widget pages");
        Button lock = activity.getWindow().getDecorView().findViewWithTag("ui_lock");
        test.runOnMainSync(() -> lock.performClick());
        twoFingerTap(test, clock); doubleTap(test, clock); swipe(test, clock, true);
        if (prefs.getInt("pref_digital_style_idx", 0) != 1 || prefs.getInt("pref_main_page", -1) != 0
            || !prefs.getBoolean("pref_digital_show_sec", false)) throw new AssertionError("Locked gestures must not change state");
        SystemClock.sleep(5500);
        View controls = activity.getWindow().getDecorView().findViewWithTag("clock_controls");
        if (controls.isShown()) throw new AssertionError("Controls must hide after inactivity");
        singleTap(test, clock); SystemClock.sleep(400); test.waitForIdleSync();
        if (!controls.isShown() || !lock.isEnabled()) throw new AssertionError("A locked screen must still expose unlock");
        test.runOnMainSync(() -> lock.performClick());
    }
    private static void twoFingerTap(Instrumentation test, View view) {
        long down = SystemClock.uptimeMillis();
        float x = view.getWidth() * .4f, y = view.getHeight() * .4f;
        send(test, view, down, MotionEvent.ACTION_DOWN, new float[] { x }, new float[] { y }); SystemClock.sleep(20);
        send(test, view, down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            new float[] { x, x + 30 }, new float[] { y, y }); SystemClock.sleep(20);
        send(test, view, down, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            new float[] { x, x + 30 }, new float[] { y, y }); SystemClock.sleep(20);
        send(test, view, down, MotionEvent.ACTION_UP, new float[] { x }, new float[] { y }); test.waitForIdleSync();
    }
    private static void singleTap(Instrumentation test, View view) {
        long down = SystemClock.uptimeMillis();
        float x = view.getWidth() / 2f, y = view.getHeight() / 2f;
        send(test, view, down, MotionEvent.ACTION_DOWN, new float[] { x }, new float[] { y }); SystemClock.sleep(20);
        send(test, view, down, MotionEvent.ACTION_UP, new float[] { x }, new float[] { y }); test.waitForIdleSync();
    }
    private static void doubleTap(Instrumentation test, View view) {
        singleTap(test, view); SystemClock.sleep(80); singleTap(test, view); test.waitForIdleSync();
    }
    private static void swipe(Instrumentation test, View view, boolean up) {
        swipe(test, view, up, 40);
    }
    private static void swipe(Instrumentation test, View view, boolean up, int pause) {
        long down = SystemClock.uptimeMillis();
        float x = view.getWidth() / 2f, start = view.getHeight() * (up ? .60f : .15f), end = view.getHeight() * (up ? .15f : .60f);
        send(test, view, down, MotionEvent.ACTION_DOWN, new float[] { x }, new float[] { start }); SystemClock.sleep(pause);
        send(test, view, down, MotionEvent.ACTION_MOVE, new float[] { x }, new float[] { (start + end) / 2 }); SystemClock.sleep(pause);
        send(test, view, down, MotionEvent.ACTION_MOVE, new float[] { x }, new float[] { end }); SystemClock.sleep(pause);
        send(test, view, down, MotionEvent.ACTION_UP, new float[] { x }, new float[] { end }); test.waitForIdleSync();
    }
    private static void send(Instrumentation test, View view, long down, int action, float[] x, float[] y) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[x.length];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[x.length];
        for (int i = 0; i < x.length; i++) {
            properties[i] = new MotionEvent.PointerProperties(); properties[i].id = i; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords(); coordinates[i].x = x[i]; coordinates[i].y = y[i];
            coordinates[i].pressure = 1; coordinates[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x.length, properties, coordinates,
            0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { test.runOnMainSync(() -> view.dispatchTouchEvent(event)); }
        finally { event.recycle(); }
    }
}
