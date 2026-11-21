package com.linzin.legacy;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

final class WeatherDisplayChecks {
    static void run(LegacyInstrumentation test, Activity activity) throws Exception {
        for (Locale locale : new Locale[] { Locale.ENGLISH, Locale.TRADITIONAL_CHINESE, Locale.SIMPLIFIED_CHINESE, Locale.JAPANESE }) {
            Configuration configuration = new Configuration(activity.getResources().getConfiguration());
            configuration.fontScale = 2; configuration.locale = locale;
            DisplayMetrics metrics = new DisplayMetrics(); metrics.setTo(activity.getResources().getDisplayMetrics());
            Resources resources = new Resources(activity.getAssets(), metrics, configuration);
            ContextThemeWrapper context = new ContextThemeWrapper(activity, android.R.style.Theme_Holo_NoActionBar) {
                @Override public Resources getResources() { return resources; }
            };
            activity.getSharedPreferences("desk_clock_prefs", 0).edit().putString("pref_layout_mode", "DUAL_LEFT_LARGE")
                .putInt("pref_side_page", 1).putBoolean("pref_weather_enabled", true).commit();
            LegacyWeather weather = LegacyWeather.parse(ForecastDataChecks.body());
            LegacyClockScreen[] screen = new LegacyClockScreen[1];
            AtomicReference<Throwable> failure = new AtomicReference<>();
            test.runOnMainSync(() -> {
                screen[0] = new LegacyClockScreen(context, false, () -> { }, () -> { }, () -> { });
                screen[0].weatherCity.setText("Long locality name with multiple words and a district that must wrap inside the weather pane");
                screen[0].weatherStatus.setText(context.getString(R.string.weather_value, 26., "C", "11/17 12:00")
                    + "\n" + new LegacyPosition(25, 121, null, null, null).label(context, System.currentTimeMillis()));
                screen[0].weatherForecast.setText(weather.forecastText(context, false, true));
                activity.setContentView(screen[0]); screen[0].resume();
                screen[0].findViewWithTag("clock_controls").setVisibility(View.GONE);
            });
            test.waitForIdleSync();
            try {
                test.runOnMainSync(() -> {
                    try {
                        ScrollView scroll = screen[0].findViewWithTag("side_panel");
                        if (screen[0].weatherForecast.getLayout() == null || screen[0].weatherForecast.getLineCount() < 4
                            || screen[0].weatherForecast.getWidth() > scroll.getWidth()
                            || scroll.getChildAt(0).getHeight() <= scroll.getHeight())
                            throw new AssertionError("Large-font data must wrap and scroll inside its pane: " + locale);
                        scroll.setSmoothScrollingEnabled(false); scroll.fullScroll(View.FOCUS_DOWN);
                    } catch (Throwable error) { failure.set(error); }
                });
                test.waitForIdleSync();
                test.runOnMainSync(() -> {
                    try {
                        Button locate = screen[0].findViewWithTag("weather_locate");
                        Rect visible = new Rect();
                        if (locate.getHeight() < 48 * metrics.density || !locate.getGlobalVisibleRect(visible)
                            || visible.height() + 1 < locate.getHeight())
                            throw new AssertionError("The location action must remain reachable after scrolling: " + locale
                                + " height=" + locate.getHeight() + " visible=" + visible + " density=" + metrics.density);
                        Bitmap image = Bitmap.createBitmap(screen[0].getWidth(), screen[0].getHeight(), Bitmap.Config.ARGB_8888);
                        try (FileOutputStream output = new FileOutputStream(new File(activity.getFilesDir(), "weather-data-" + locale + ".png"))) {
                            screen[0].draw(new Canvas(image)); image.compress(Bitmap.CompressFormat.PNG, 100, output);
                        } finally { image.recycle(); }
                    } catch (Throwable error) { failure.set(error); }
                });
                if (failure.get() != null) throw new AssertionError("Weather fontScale 2 fixture failed", failure.get());
            } finally { test.runOnMainSync(() -> screen[0].pause()); }
        }
    }
}
