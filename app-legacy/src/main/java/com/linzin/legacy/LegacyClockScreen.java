package com.linzin.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.linzin.clockcore.ClockLayoutMode;

final class LegacyClockScreen extends FrameLayout {
    final LegacyClockView clock;
    final TextView calendarStatus, weatherStatus, weatherForecast, weatherCity;
    final Button weatherRefresh, weatherLocate;
    private final LegacyBattery battery;
    private final LegacyMonthView month;
    private final SharedPreferences prefs;
    private final LinearLayout body, calendarPage, weatherPage, controls;
    private final FrameLayout clockPanel;
    private final ScrollView sidePanel;
    private final Button timeMode, style, seconds, layout, page, lock, settings;
    private final Runnable selectionChanged;
    private final Handler handler = new Handler();
    private final Runnable hideControls;
    private ClockLayoutMode layoutMode;
    private int sidePage;
    private boolean locked, weatherBusy, active;

    LegacyClockScreen(Context context, boolean locked, Runnable refreshWeather, Runnable refreshLocation, Runnable selectionChanged) {
        super(context);
        this.locked = locked;
        this.selectionChanged = selectionChanged;
        prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE);
        layoutMode = ClockLayoutMode.fromPersisted(prefs.getString("pref_layout_mode", "SINGLE"));
        sidePage = prefs.getInt("pref_side_page", 0) == 1 ? 1 : 0;
        setBackgroundColor(Color.BLACK);
        body = new LinearLayout(context); body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(dp(4), dp(4), dp(4), dp(4));
        addView(body, new FrameLayout.LayoutParams(-1, -1));
        clockPanel = new FrameLayout(context); clockPanel.setTag("clock_panel");
        clock = new LegacyClockView(context, prefs.getBoolean("pref_24_hour", false));
        clock.setOnClickListener(view -> showControls());
        clockPanel.addView(clock, new FrameLayout.LayoutParams(-1, -1));
        battery = new LegacyBattery(context);
        clockPanel.addView(battery, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));
        sidePanel = new ScrollView(context) {
            @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
                return LegacyClockScreen.this.locked || super.onInterceptTouchEvent(event);
            }
            @Override public boolean performClick() { return super.performClick(); }
        };
        sidePanel.setTag("side_panel"); sidePanel.setFillViewport(true);
        sidePanel.setOnClickListener(view -> showControls());
        LinearLayout sideContent = new LinearLayout(context); sideContent.setOrientation(LinearLayout.VERTICAL);
        sidePanel.addView(sideContent, new ScrollView.LayoutParams(-1, -1));
        calendarPage = new LinearLayout(context); calendarPage.setOrientation(LinearLayout.VERTICAL); calendarPage.setTag("calendar_page");
        month = new LegacyMonthView(context); calendarPage.addView(month, new LinearLayout.LayoutParams(-1, -2));
        calendarStatus = text("calendar_status", R.string.calendar_empty); calendarPage.addView(calendarStatus);
        weatherPage = new LinearLayout(context); weatherPage.setOrientation(LinearLayout.VERTICAL); weatherPage.setTag("weather_page");
        weatherCity = text("weather_city", R.string.location_name_unavailable); weatherCity.setText(""); weatherPage.addView(weatherCity);
        weatherStatus = text("weather_status", R.string.weather_idle); weatherPage.addView(weatherStatus);
        weatherForecast = text("weather_forecast", R.string.weather_idle); weatherForecast.setText(""); weatherPage.addView(weatherForecast);
        weatherRefresh = button("weather_refresh", getResources().getString(R.string.weather_refresh), refreshWeather);
        weatherPage.addView(weatherRefresh, new LinearLayout.LayoutParams(-1, -2));
        weatherLocate = button("weather_locate", getResources().getString(R.string.weather_locate), refreshLocation);
        weatherPage.addView(weatherLocate, new LinearLayout.LayoutParams(-1, -2));
        sideContent.addView(calendarPage, new LinearLayout.LayoutParams(-1, -2));
        sideContent.addView(weatherPage, new LinearLayout.LayoutParams(-1, -2));
        controls = new LinearLayout(context); controls.setOrientation(LinearLayout.VERTICAL); controls.setBackgroundColor(0xe608080b);
        hideControls = () -> controls.setVisibility(View.GONE);
        controls.setTag("clock_controls");
        timeMode = button("time_mode", "", this::toggleTimeMode);
        style = button("clock_style", "", this::nextAppearance);
        seconds = button("clock_seconds", "", this::toggleSeconds);
        layout = button("layout_mode", "", () -> {
            layoutMode = ClockLayoutMode.values()[(layoutMode.ordinal() + 1) % ClockLayoutMode.values().length];
            prefs.edit().putString("pref_layout_mode", layoutMode.name()).apply();
            applyLayout(); selectionChanged.run();
        });
        page = button("side_page", "", this::toggleSidePage);
        lock = button("ui_lock", "", () -> { this.locked = !this.locked; updateControls(); });
        settings = button("settings", getResources().getString(R.string.settings_title), () ->
            context.startActivity(new android.content.Intent(context, LegacySettingsActivity.class)));
        addRow(timeMode, style, seconds); addRow(layout, page, lock); addRow(settings);
        addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        clock.setOnTouchListener(new ClockGestures(this, clock, true));
        sidePanel.setOnTouchListener(new ClockGestures(this, sidePanel, false));
        applyLayout();
    }
    private Button button(String tag, String title, Runnable action) {
        Button button = new Button(getContext()); button.setTag(tag); button.setText(title);
        button.setMinHeight(dp(48)); button.setMinWidth(dp(48)); button.setTextSize(14);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setOnClickListener(view -> {
            if (locked && !"ui_lock".equals(tag)) return;
            action.run(); showControls();
        });
        return button;
    }
    private TextView text(String tag, int title) {
        TextView text = new TextView(getContext()); text.setTag(tag); text.setText(title);
        text.setTextColor(Color.WHITE); text.setTextSize(14); text.setPadding(dp(6), dp(6), dp(6), dp(6));
        return text;
    }
    private void addRow(Button... buttons) {
        LinearLayout row = new LinearLayout(getContext());
        for (Button button : buttons) row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
    private void applyLayout() {
        body.removeAllViews();
        sidePanel.setVisibility(layoutMode == ClockLayoutMode.SINGLE ? View.GONE : View.VISIBLE);
        if (layoutMode == ClockLayoutMode.DUAL_RIGHT_LARGE) {
            body.addView(sidePanel, new LinearLayout.LayoutParams(0, -1, .3f));
            body.addView(clockPanel, new LinearLayout.LayoutParams(0, -1, .7f));
        } else {
            body.addView(clockPanel, new LinearLayout.LayoutParams(0, -1, layoutMode == ClockLayoutMode.SINGLE ? 1 : .7f));
            body.addView(sidePanel, new LinearLayout.LayoutParams(0, -1, .3f));
        }
        applyPage();
    }
    private void applyPage() {
        calendarPage.setVisibility(sidePage == 0 ? View.VISIBLE : View.GONE);
        weatherPage.setVisibility(sidePage == 1 ? View.VISIBLE : View.GONE);
        updateAuxiliaryContent();
        updateControls();
    }
    private void toggleTimeMode() {
        boolean is24 = !prefs.getBoolean("pref_24_hour", false);
        prefs.edit().putBoolean("pref_24_hour", is24).apply(); clock.set24Hour(is24); updateControls();
    }
    private void nextAppearance() {
        ClockAppearance current = ClockAppearance.read(prefs);
        ClockAppearance.values()[(current.ordinal() + 1) % ClockAppearance.values().length].save(prefs);
        clock.reloadSettings(); updateControls();
    }
    void cycleStyle() {
        ClockAppearance current = ClockAppearance.read(prefs);
        int next = (current.style + 1) % (current.family == 2 ? 2 : 3);
        for (ClockAppearance candidate : ClockAppearance.values()) {
            if (candidate.family == current.family && candidate.style == next) { candidate.save(prefs); break; }
        }
        clock.reloadSettings(); updateControls();
    }
    void changeFamily(int direction) {
        int family = (ClockAppearance.read(prefs).family + direction + 3) % 3;
        prefs.edit().putInt("pref_main_page", family).apply(); clock.reloadSettings(); updateControls();
    }
    void toggleSeconds() {
        ClockAppearance current = ClockAppearance.read(prefs);
        prefs.edit().putBoolean("pref_" + current.familyKey() + "_show_sec", !current.seconds(prefs)).apply();
        clock.reloadSettings(); updateControls();
    }
    void toggleSidePage() {
        sidePage = 1 - sidePage; prefs.edit().putInt("pref_side_page", sidePage).apply();
        applyPage(); selectionChanged.run();
    }
    private void updateControls() {
        boolean is24 = prefs.getBoolean("pref_24_hour", false);
        timeMode.setText(is24 ? "24h" : "12h");
        timeMode.setContentDescription(getResources().getString(is24 ? R.string.time_mode_24 : R.string.time_mode_12));
        style.setText(ClockAppearance.read(prefs).label());
        style.setContentDescription(getResources().getString(R.string.clock_style) + ": " + style.getText());
        seconds.setText(ClockAppearance.read(prefs).seconds(prefs) ? R.string.hide_seconds : R.string.show_seconds);
        layout.setText(layoutMode == ClockLayoutMode.SINGLE ? "1" : layoutMode == ClockLayoutMode.DUAL_LEFT_LARGE ? "70|30" : "30|70");
        layout.setContentDescription(getResources().getString(layoutMode == ClockLayoutMode.SINGLE ? R.string.layout_single
            : layoutMode == ClockLayoutMode.DUAL_LEFT_LARGE ? R.string.layout_left : R.string.layout_right));
        page.setText(sidePage == 0 ? R.string.page_calendar : R.string.page_weather);
        page.setContentDescription(getResources().getString(R.string.switch_page) + ": " + page.getText());
        lock.setText(locked ? R.string.unlock_controls : R.string.lock_controls);
        for (Button button : new Button[] { timeMode, style, seconds, layout, page, settings }) button.setEnabled(!locked);
        page.setEnabled(!locked && layoutMode != ClockLayoutMode.SINGLE);
        weatherRefresh.setEnabled(!locked && !weatherBusy && prefs.getBoolean("pref_weather_enabled", false));
        weatherLocate.setEnabled(!locked && !weatherBusy && prefs.getBoolean("pref_weather_enabled", false));
    }
    boolean isCalendarVisible() { return getVisibility() == View.VISIBLE && layoutMode != ClockLayoutMode.SINGLE && sidePage == 0; }
    boolean isWeatherVisible() { return getVisibility() == View.VISIBLE && layoutMode != ClockLayoutMode.SINGLE && sidePage == 1; }
    boolean isLocked() { return locked; }
    void reloadPreferences() {
        layoutMode = ClockLayoutMode.fromPersisted(prefs.getString("pref_layout_mode", "SINGLE"));
        sidePage = prefs.getInt("pref_side_page", 0) == 1 ? 1 : 0;
        clock.set24Hour(prefs.getBoolean("pref_24_hour", false)); clock.reloadSettings(); applyLayout();
    }
    void setWeatherBusy(boolean busy) { weatherBusy = busy; updateControls(); }
    void showControls() {
        controls.setVisibility(View.VISIBLE); handler.removeCallbacks(hideControls);
        if (active) handler.postDelayed(hideControls, 5000);
    }
    private void updateAuxiliaryContent() {
        battery.setVisibility(prefs.getBoolean("pref_show_battery", true) ? View.VISIBLE : View.GONE);
        if (active && battery.getVisibility() == View.VISIBLE) battery.start(); else battery.stop();
        if (active && isCalendarVisible()) month.start(); else month.stop();
    }
    void resume() { active = true; clock.start(); updateAuxiliaryContent(); showControls(); }
    void pause() { active = false; handler.removeCallbacksAndMessages(null); clock.stop(); battery.stop(); month.stop(); }
    @Override protected void onDetachedFromWindow() { pause(); super.onDetachedFromWindow(); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
