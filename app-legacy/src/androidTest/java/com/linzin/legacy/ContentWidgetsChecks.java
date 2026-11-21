package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.view.View;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

final class ContentWidgetsChecks {
    static void battery(Instrumentation test, Activity activity) {
        TextView[] battery = new TextView[1];
        test.runOnMainSync(() -> battery[0] = activity.getWindow().getDecorView().findViewWithTag("battery"));
        if (battery[0] == null || !battery[0].isShown()) throw new AssertionError("Visible battery status is required");
        if (!battery[0].getText().toString().contains("%")) throw new AssertionError("Real sticky battery state must display a percentage");
    }
    static void month(Instrumentation test, Activity activity) {
        View[] month = new View[1];
        test.runOnMainSync(() -> month[0] = activity.getWindow().getDecorView().findViewWithTag("month_calendar"));
        if (month[0] == null || !month[0].isShown()) throw new AssertionError("The calendar sidebar must include the month grid even with events off");
        TextView title = month[0].findViewWithTag("month_title");
        String expected = new SimpleDateFormat("MMMM yyyy", activity.getResources().getConfiguration().locale).format(new Date());
        if (title == null || !expected.equals(title.getText().toString())) throw new AssertionError("Month title must use the Activity language and current time");
        int days = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= days; day++) {
            TextView cell = month[0].findViewWithTag("day_" + day);
            if (cell == null || !Integer.toString(day).equals(cell.getText().toString()) || cell.getContentDescription() == null)
                throw new AssertionError("Month dates must be visible and readable by screen readers: " + day);
        }
    }
}
