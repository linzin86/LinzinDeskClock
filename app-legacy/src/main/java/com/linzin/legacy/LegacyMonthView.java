package com.linzin.legacy;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.linzin.clockcore.MonthGrid;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class LegacyMonthView extends LinearLayout {
    private final Handler handler = new Handler();
    private final List<TextView> cells = new ArrayList<>();
    private String displayed;
    private boolean active;
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!active) return;
            long now = System.currentTimeMillis(); showTime(now);
            handler.postDelayed(this, 60000 - now % 60000);
        }
    };
    LegacyMonthView(Context context) { super(context); setOrientation(VERTICAL); setTag("month_calendar"); }
    void start() { stop(); active = true; tick.run(); }
    void stop() { active = false; handler.removeCallbacksAndMessages(null); }
    void showTime(long instant) {
        Locale locale = getResources().getConfiguration().locale;
        TimeZone zone = TimeZone.getDefault();
        MonthGrid month = MonthGrid.at(instant, zone);
        String key = month.year + "/" + month.month + "/" + month.day + zone.getID() + locale;
        if (key.equals(displayed)) return;
        displayed = key; removeAllViews(); cells.clear();
        TextView title = text(new SimpleDateFormat("MMMM yyyy", locale).format(new Date(instant)));
        title.setTag("month_title"); title.setSingleLine(false); title.setTextSize(16); title.setPadding(0, dp(6), 0, dp(6)); addView(title, new LayoutParams(-1, -2));
        DateFormatSymbols symbols = new DateFormatSymbols(locale);
        String[] weekdays = symbols.getShortWeekdays();
        LinearLayout header = row();
        for (int day = 1; day <= 7; day++) {
            String name = weekdays[day];
            TextView label = text("zh".equals(locale.getLanguage()) || "ja".equals(locale.getLanguage())
                ? name.substring(name.length() - 1) : name.substring(0, 1));
            label.setContentDescription(symbols.getWeekdays()[day]);
            addCell(header, label);
        }
        Calendar date = Calendar.getInstance(zone); date.setTimeInMillis(instant);
        SimpleDateFormat accessible = new SimpleDateFormat("EEEE, MMMM d, yyyy", locale); accessible.setTimeZone(zone);
        for (int offset = 0; offset < month.cellCount; offset += 7) {
            LinearLayout row = row();
            for (int column = 0; column < 7; column++) {
                int day = offset + column - month.firstDayOffset + 1;
                boolean valid = day >= 1 && day <= month.daysInMonth;
                TextView label = text(valid ? Integer.toString(day) : "");
                if (valid) {
                    date.set(Calendar.DAY_OF_MONTH, day); label.setTag("day_" + day);
                    label.setContentDescription(accessible.format(date.getTime()));
                    if (day == month.day) {
                        label.setTextColor(0xff5dac81);
                        GradientDrawable border = new GradientDrawable(); border.setShape(GradientDrawable.OVAL);
                        border.setStroke(dp(1), 0xff5dac81); label.setBackgroundDrawable(border);
                    }
                }
                addCell(row, label);
            }
        }
        resizeCells(getWidth());
    }
    private TextView text(String value) {
        TextView text = new TextView(getContext()); text.setText(value); text.setTextColor(Color.WHITE);
        text.setGravity(Gravity.CENTER); text.setSingleLine(true); return text;
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext()); addView(row, new LayoutParams(-1, -2)); return row;
    }
    private void addCell(LinearLayout row, TextView cell) {
        cells.add(cell); row.addView(cell, new LayoutParams(0, dp(24), 1));
    }
    private void resizeCells(int width) {
        if (width <= 0) return;
        float size = Math.min(14 * getResources().getDisplayMetrics().scaledDensity, width / 7f * .65f);
        for (TextView cell : cells) cell.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size);
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); resizeCells(w); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
