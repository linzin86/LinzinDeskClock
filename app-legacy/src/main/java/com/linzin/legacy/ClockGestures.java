package com.linzin.legacy;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

final class ClockGestures extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
    private final LegacyClockScreen screen;
    private final View view;
    private final boolean clock;
    private final GestureDetector detector;
    private int pointers;
    private long began;
    private float startX, startY;
    private int firstId, secondId;
    private float secondX, secondY;
    private boolean moved;
    ClockGestures(LegacyClockScreen screen, View view, boolean clock) {
        this.screen = screen; this.view = view; this.clock = clock;
        detector = new GestureDetector(view.getContext(), this);
    }
    // GestureDetector confirms clicks in onSingleTapConfirmed, which invokes performClick.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouch(View target, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            pointers = 1; moved = false; began = event.getEventTime(); startX = event.getX(); startY = event.getY();
            firstId = event.getPointerId(0); secondId = -1;
        }
        pointers = Math.max(pointers, event.getPointerCount());
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && pointers == 2) {
            int index = event.getActionIndex();
            secondId = event.getPointerId(index); secondX = event.getX(index); secondY = event.getY(index);
        }
        for (int i = 0; i < event.getPointerCount(); i++) {
            int id = event.getPointerId(i);
            float x = id == firstId ? startX : secondX, y = id == firstId ? startY : secondY;
            if ((id != firstId && id != secondId) || Math.hypot(event.getX(i) - x, event.getY(i) - y)
                > 16 * view.getResources().getDisplayMetrics().density) moved = true;
        }
        if (pointers > 1) {
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
                detector.onTouchEvent(cancel); cancel.recycle();
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP && pointers == 2 && !moved
                && event.getEventTime() - began <= 450 && clock && !screen.isLocked()) {
                screen.cycleStyle(); screen.showControls();
            }
        } else {
            detector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP && !screen.isLocked()) {
                float dy = event.getY() - startY, dx = event.getX() - startX;
                // Paging follows swipe distance, including a slow drag with no fling velocity.
                if (Math.abs(dy) >= 50 * view.getResources().getDisplayMetrics().density && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                    if (clock) screen.changeFamily(dy < 0 ? 1 : -1);
                    else if (!view.canScrollVertically(1) && !view.canScrollVertically(-1)) screen.toggleSidePage();
                    screen.showControls();
                }
            }
        }
        return clock || screen.isLocked();
    }
    @Override public boolean onDown(MotionEvent event) { return true; }
    @Override public boolean onSingleTapConfirmed(MotionEvent event) { view.performClick(); return true; }
    @Override public boolean onDoubleTap(MotionEvent event) {
        if (clock && !screen.isLocked()) screen.toggleSeconds();
        screen.showControls();
        return true;
    }
}
