package com.linzin.legacy;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

final class WeatherParserChecks {
    static void run(Context context) throws Exception {
        LegacyWeather valid = LegacyWeather.parse(ForecastDataChecks.body());
        if (valid.currentTempC != 26 || valid.currentCode != 0) throw new AssertionError("Current weather fields must be retained");
        String fahrenheit = valid.forecastText(context, true, true);
        for (int value : new int[] { 70, 72, 73, 75 })
            if (!fahrenheit.contains(value + "°")) throw new AssertionError("Forecast units must convert the retained Celsius values: " + fahrenheit);
        JSONObject mismatch = new JSONObject(ForecastDataChecks.body());
        mismatch.getJSONObject("hourly").put("weather_code", new JSONArray());
        rejects(mismatch);
        JSONObject invalidCurrent = new JSONObject(ForecastDataChecks.body());
        invalidCurrent.getJSONObject("current").put("temperature_2m", "NaN");
        rejects(invalidCurrent);
        JSONObject invalidHourly = new JSONObject(ForecastDataChecks.body());
        invalidHourly.getJSONObject("hourly").getJSONArray("temperature_2m").put(0, "Infinity");
        rejects(invalidHourly);
        JSONObject overflow = new JSONObject(ForecastDataChecks.body());
        overflow.getJSONObject("hourly").getJSONArray("time").put(0, Long.MAX_VALUE);
        rejects(overflow);
        JSONObject unknownZone = new JSONObject(ForecastDataChecks.body()).put("timezone", "Invalid/Zone");
        rejects(unknownZone);
        JSONObject kyiv = new JSONObject(ForecastDataChecks.body()).put("timezone", "Europe/Kyiv");
        LegacyWeather.parse(kyiv.toString()).forecastText(context, false, false);
        String unknown = context.getString(R.string.weather_unknown);
        for (int code : new int[] { -1, 52, 54, 62, 64, 72, 74, 76, 100 })
            if (!unknown.equals(LegacyWeather.description(context, code)))
                throw new AssertionError("Unrecognized WMO code must remain unknown: " + code);
        long now = System.currentTimeMillis();
        LegacyWeather cached = LegacyWeather.parse(ForecastDataChecks.body());
        String previous = cached.forecastText(context, false, true, now - 3600000);
        if (!previous.contains("20°") || previous.contains("24°")) throw new AssertionError("The rollover fixture must first expose the previous hour");
        String advanced = cached.forecastText(context, false, true, now);
        if (advanced.contains("20°") || !advanced.contains("24°"))
            throw new AssertionError("A cached forecast must advance from its original selection when the current hour changes: " + advanced);
    }
    private static void rejects(JSONObject root) throws Exception {
        try { LegacyWeather.parse(root.toString()); }
        catch (Exception expected) { return; }
        throw new AssertionError("Invalid weather must be rejected rather than displayed as current");
    }
}
