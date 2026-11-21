package com.linzin.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import com.linzin.clockcore.LocationAge;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

final class LegacyPosition {
    final double latitude, longitude;
    final Long fixTime, observedAt;
    final String provider;

    LegacyPosition(double latitude, double longitude, Long fixTime, Long observedAt, String provider) {
        this.latitude = latitude; this.longitude = longitude; this.fixTime = fixTime;
        this.observedAt = observedAt; this.provider = provider;
    }
    static LegacyPosition from(Location location, long observedAt) {
        return new LegacyPosition(location.getLatitude(), location.getLongitude(),
            location.getTime() > 0 ? location.getTime() : null, observedAt, location.getProvider());
    }
    void save(SharedPreferences prefs) {
        prefs.edit().putString("pref_last_valid_lat", Double.toString(latitude))
            .putString("pref_last_valid_lon", Double.toString(longitude))
            .putString("pref_last_location_fix_time", fixTime == null ? null : fixTime.toString())
            .putString("pref_last_location_observed_at", observedAt == null ? null : observedAt.toString())
            .putString("pref_last_location_provider", provider).apply();
    }
    static LegacyPosition read(SharedPreferences prefs) {
        Map<String, ?> values = prefs.getAll();
        try {
            double latitude = Double.parseDouble((String) values.get("pref_last_valid_lat"));
            double longitude = Double.parseDouble((String) values.get("pref_last_valid_lon"));
            if (!(latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180)) return null;
            Object provider = values.get("pref_last_location_provider");
            return new LegacyPosition(latitude, longitude, positive(values.get("pref_last_location_fix_time")),
                positive(values.get("pref_last_location_observed_at")), provider instanceof String ? (String) provider : null);
        } catch (RuntimeException invalid) { return null; }
    }
    String label(Context context, long now) {
        Long known = LocationAge.knownFixTime(fixTime, observedAt, now);
        String time = known == null ? context.getString(R.string.weather_location_time_unknown)
            : new SimpleDateFormat("M/d H:mm", context.getResources().getConfiguration().locale).format(new Date(known));
        int source = "gps".equals(provider) ? R.string.weather_location_source_gps
            : "network".equals(provider) ? R.string.weather_location_source_network
            : "passive".equals(provider) ? R.string.weather_location_source_passive : R.string.weather_location_source_unknown;
        return context.getString(R.string.weather_last_location, time, context.getString(source));
    }
    private static Long positive(Object value) {
        try {
            long parsed = Long.parseLong((String) value);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException invalid) { return null; }
    }
}
