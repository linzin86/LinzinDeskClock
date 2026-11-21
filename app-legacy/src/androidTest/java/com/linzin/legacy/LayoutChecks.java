package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.view.View;
import android.widget.Button;

final class LayoutChecks {
    static void run(Instrumentation test, Activity activity) {
        Button[] layout = new Button[1];
        test.runOnMainSync(() -> layout[0] = activity.getWindow().getDecorView().findViewWithTag("layout_mode"));
        if (layout[0] == null) throw new AssertionError("Single and both dual layouts must be selectable");
        View[] clock = new View[1], side = new View[1];
        test.runOnMainSync(() -> {
            clock[0] = activity.getWindow().getDecorView().findViewWithTag("clock_panel");
            side[0] = activity.getWindow().getDecorView().findViewWithTag("side_panel");
        });
        if (clock[0] == null || side[0] == null) throw new AssertionError("Both panes must exist");
        if (side[0].isShown()) throw new AssertionError("Single layout must only show the clock");
        for (int i = 0; i < 2; i++) {
            test.runOnMainSync(() -> layout[0].performClick());
            test.waitForIdleSync();
            if (!clock[0].isShown() || !side[0].isShown()) throw new AssertionError("Dual layout must show both panes");
            if ((clock[0].getLeft() < side[0].getLeft()) != (i == 0)) throw new AssertionError("Large clock must mirror sides");
            float ratio = (float) clock[0].getWidth() / (clock[0].getWidth() + side[0].getWidth());
            if (Math.abs(ratio - .7f) > .03f) throw new AssertionError("Clock must take 70 percent: " + ratio);
        }
        Button[] lock = new Button[1], style = new Button[1], page = new Button[1];
        test.runOnMainSync(() -> {
            lock[0] = activity.getWindow().getDecorView().findViewWithTag("ui_lock");
            style[0] = activity.getWindow().getDecorView().findViewWithTag("clock_style");
            page[0] = activity.getWindow().getDecorView().findViewWithTag("side_page");
        });
        if (lock[0] == null || page[0] == null) throw new AssertionError("Lock and sidebar controls must be accessible");
        test.runOnMainSync(() -> page[0].performClick());
        test.waitForIdleSync();
        View[] weather = new View[1];
        test.runOnMainSync(() -> weather[0] = activity.getWindow().getDecorView().findViewWithTag("weather_page"));
        if (!weather[0].isShown()) throw new AssertionError("Sidebar must switch to weather");
        test.runOnMainSync(() -> lock[0].performClick());
        if (style[0].isEnabled() || layout[0].isEnabled() || page[0].isEnabled() || !lock[0].isEnabled())
            throw new AssertionError("Lock must block changes and leave unlock reachable");
        test.runOnMainSync(() -> lock[0].performClick());
        if (!style[0].isEnabled() || !layout[0].isEnabled()) throw new AssertionError("Unlock must restore controls");
        String saved = activity.getSharedPreferences("desk_clock_prefs", 0).getString("pref_layout_mode", "");
        if (!"DUAL_RIGHT_LARGE".equals(saved)) throw new AssertionError("Dual choice must persist");
    }
}
