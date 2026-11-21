package com.linzin.legacy;

import android.app.Activity;
import android.os.SystemClock;
import android.widget.TextView;

final class WeatherRefreshChecks {
    static void run(LegacyInstrumentation test, Activity clock) throws Exception {
        ForecastDataChecks.run(test, clock);
        int first = ForecastDataChecks.weatherRequests;
        Activity settings = test.openSettings(clock);
        java.lang.reflect.Field fetchedAt = LegacyActivity.class.getDeclaredField("lastWeatherAt");
        fetchedAt.setAccessible(true);
        test.runOnMainSync(() -> {
            try { fetchedAt.setLong(clock, System.currentTimeMillis() - 31 * 60000); }
            catch (IllegalAccessException invalid) { throw new AssertionError(invalid); }
            settings.finish();
        });
        test.awaitResumed(clock.getComponentName().getClassName(), null);
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (ForecastDataChecks.weatherRequests == first && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
        if (ForecastDataChecks.weatherRequests != first + 1)
            throw new AssertionError("Returning to expired weather must refresh once, with no parallel owners");
        String[] value = new String[1];
        do {
            SystemClock.sleep(100);
            test.runOnMainSync(() -> value[0] = ((TextView) clock.getWindow().getDecorView().findViewWithTag("weather_status")).getText().toString());
        } while (!value[0].contains("26.0") && SystemClock.elapsedRealtime() < deadline);
        if (!value[0].contains("26.0")) throw new AssertionError("Automatic refresh must reach the visible weather");
        Activity background = test.openSettings(clock);
        SystemClock.sleep(500);
        if (ForecastDataChecks.weatherRequests != first + 1) throw new AssertionError("Background must stop scheduled weather work");
        test.runOnMainSync(background::finish);
    }
}
