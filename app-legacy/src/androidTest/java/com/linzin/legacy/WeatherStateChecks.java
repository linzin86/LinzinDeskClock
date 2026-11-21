package com.linzin.legacy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.TextView;

final class WeatherStateChecks {
    static Activity run(LegacyInstrumentation test, Activity clock) {
        ForecastDataChecks.run(test, clock);
        ForecastDataChecks.metadata(test, clock);
        ForecastDataChecks.city(test, clock);
        String[] before = new String[2];
        test.runOnMainSync(() -> {
            before[0] = text(clock, "weather_status");
            before[1] = text(clock, "weather_forecast");
        });
        SharedPreferences prefs = clock.getSharedPreferences("desk_clock_prefs", 0);
        prefs.edit().putString("pref_last_valid_lat", "26.0").putString("pref_last_valid_lon", "122.0")
            .putString("pref_last_location_provider", "network").putString("pref_last_location_fix_time", "1").commit();
        test.runOnMainSync(clock::recreate);
        Activity restored = test.awaitResumed(clock.getComponentName().getClassName(), clock);
        test.runOnMainSync(() -> {
            if (!before[0].equals(text(restored, "weather_status")) || !before[1].equals(text(restored, "weather_forecast")))
                throw new AssertionError("Recreation must retain fetched time, forecast and the weather's original position snapshot");
        });
        ForecastDataChecks.city(test, restored);
        prefs.edit().putBoolean("pref_weather_enabled", false).commit();
        test.runOnMainSync(restored::recreate);
        Activity disabled = test.awaitResumed(clock.getComponentName().getClassName(), restored);
        test.runOnMainSync(() -> {
            if (!text(disabled, "weather_forecast").isEmpty() || !text(disabled, "weather_city").isEmpty())
                throw new AssertionError("Disabled weather must hide restored data");
        });
        return disabled;
    }
    private static String text(Activity clock, String tag) {
        return ((TextView) clock.getWindow().getDecorView().findViewWithTag(tag)).getText().toString();
    }
}
