package com.linzin.legacy;

import android.content.Context;
import com.linzin.clockcore.ForecastTimeline;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

final class LegacyWeather {
    final double currentTempC;
    final int currentCode;
    final String body;
    private final long[] times;
    private final double[] temperatures;
    private final int[] codes;
    private final String timeZoneId;

    private LegacyWeather(double currentTempC, int currentCode, long[] times, double[] temperatures,
        int[] codes, String timeZoneId, String body) {
        this.currentTempC = currentTempC; this.currentCode = currentCode;
        this.times = times; this.temperatures = temperatures; this.codes = codes;
        this.timeZoneId = timeZoneId;
        this.body = body;
    }
    static LegacyWeather parse(String body) throws JSONException {
        JSONObject root = new JSONObject(body), current = root.getJSONObject("current"), hourly = root.getJSONObject("hourly");
        double currentC = finite(current.getDouble("temperature_2m"));
        int currentCode = current.getInt("weather_code");
        String zone = root.getString("timezone");
        ZoneId.of(zone);
        JSONArray timestamps = hourly.getJSONArray("time"), temps = hourly.getJSONArray("temperature_2m"), conditions = hourly.getJSONArray("weather_code");
        if (timestamps.length() != temps.length() || timestamps.length() != conditions.length())
            throw new IllegalArgumentException("Mismatched hourly arrays");
        long[] times = new long[timestamps.length()];
        double[] temperatures = new double[times.length]; int[] codes = new int[times.length];
        for (int i = 0; i < times.length; i++) {
            long seconds = timestamps.getLong(i);
            if (seconds < Long.MIN_VALUE / 1000 || seconds > Long.MAX_VALUE / 1000)
                throw new IllegalArgumentException("Invalid forecast timestamp");
            times[i] = seconds * 1000; temperatures[i] = finite(temps.getDouble(i)); codes[i] = conditions.getInt(i);
        }
        return new LegacyWeather(currentC, currentCode, times, temperatures, codes, zone, body);
    }
    String forecastText(Context context, boolean fahrenheit, boolean is24) {
        return forecastText(context, fahrenheit, is24, System.currentTimeMillis());
    }
    String forecastText(Context context, boolean fahrenheit, boolean is24, long now) {
        Locale locale = context.getResources().getConfiguration().locale;
        DateTimeFormatter format = DateTimeFormatter.ofPattern(is24 ? "HH:mm" : "h:mm a", locale).withZone(ZoneId.of(timeZoneId));
        StringBuilder text = new StringBuilder();
        for (int index : ForecastTimeline.upcomingIndices(times, now, timeZoneId)) {
            if (text.length() > 0) text.append('\n');
            double value = fahrenheit ? temperatures[index] * 9 / 5 + 32 : temperatures[index];
            text.append(format.format(Instant.ofEpochMilli(times[index]))).append(" · ").append(Math.round(value))
                .append("° · ").append(description(context, codes[index]));
        }
        return text.toString();
    }
    static String description(Context context, int code) {
        int label = code == 0 ? R.string.weather_clear : code == 1 ? R.string.weather_mainly_clear
            : code == 2 ? R.string.weather_partly_cloudy : code == 3 ? R.string.weather_overcast
            : code == 45 || code == 48 ? R.string.weather_fog
            : code == 51 || code == 53 || code == 55 || code == 56 || code == 57 ? R.string.weather_drizzle
            : code == 61 || code == 63 || code == 65 || code == 66 || code == 67 || code >= 80 && code <= 82 ? R.string.weather_rain
            : code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86 ? R.string.weather_snow
            : code == 95 || code == 96 || code == 99 ? R.string.weather_thunderstorm : R.string.weather_unknown;
        return context.getString(label);
    }
    private static double finite(double temperature) {
        if (Double.isNaN(temperature) || Double.isInfinite(temperature)) throw new IllegalArgumentException("Invalid temperature");
        return temperature;
    }
}
