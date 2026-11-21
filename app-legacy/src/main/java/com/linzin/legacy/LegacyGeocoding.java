package com.linzin.legacy;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import com.linzin.clockcore.CityNames;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class LegacyGeocoding {
    interface Request { String get(URL url) throws Exception; }
    private static final ThreadPoolExecutor worker = newWorker("legacy-geocoder");
    private static final ThreadPoolExecutor networkWorker = newWorker("legacy-geocoder-http");
    private static ThreadPoolExecutor newWorker(String name) {
        return new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), action -> {
            Thread thread = new Thread(action, name); thread.setDaemon(true); return thread;
        });
    }

    static String resolve(Context context, LegacyPosition position, Request request) throws InterruptedException {
        Locale locale = context.getResources().getConfiguration().locale;
        Context application = context.getApplicationContext();
        String name = bounded(() -> {
            if (!Geocoder.isPresent()) return null;
            List<Address> values = new Geocoder(application, locale).getFromLocation(position.latitude, position.longitude, 1);
            if (values == null || values.isEmpty()) return null;
            Address address = values.get(0);
            return clean(CityNames.select(address.getLocality(), address.getSubAdminArea(), address.getCountryName(),
                address.getAdminArea(), new int[] { 8 }, new String[] { address.getSubLocality() }));
        }, 3000);
        if (name != null) return name;
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Geocoding cancelled");
        return bounded(networkWorker, () -> { try {
            String language = locale.getLanguage().length() == 0 ? "en" : locale.getLanguage();
            URL url = new URL("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=" + position.latitude
                + "&longitude=" + position.longitude + "&localityLanguage=" + URLEncoder.encode(language, "UTF-8"));
            JSONObject root = new JSONObject(request.get(url));
            JSONArray administrative = root.optJSONObject("localityInfo") == null ? null
                : root.getJSONObject("localityInfo").optJSONArray("administrative");
            int[] levels = new int[administrative == null ? 0 : administrative.length()];
            String[] names = new String[levels.length];
            for (int i = 0; i < levels.length; i++) {
                JSONObject entry = administrative.optJSONObject(i);
                if (entry == null) continue;
                levels[i] = entry.optInt("adminLevel", 0); names[i] = entry.optString("name", "");
            }
            return clean(CityNames.select(root.optString("locality", ""), root.optString("city", ""),
                root.optString("countryName", ""), root.optString("principalSubdivision", ""), levels, names));
        } catch (InterruptedException cancelled) { throw cancelled; }
        catch (Exception unavailable) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Geocoding cancelled");
            return null;
        } }, 6000);
    }
    static String bounded(Callable<String> lookup, long timeoutMillis) throws InterruptedException {
        return bounded(worker, lookup, timeoutMillis);
    }
    private static String bounded(ThreadPoolExecutor executor, Callable<String> lookup, long timeoutMillis) throws InterruptedException {
        final Future<String> task;
        // Platform Binder or socket cleanup can outlive interruption; never queue behind it.
        try { task = executor.submit(lookup); }
        catch (RejectedExecutionException busy) { return null; }
        try { return task.get(timeoutMillis, TimeUnit.MILLISECONDS); }
        catch (InterruptedException cancelled) { throw cancelled; }
        catch (Exception unavailable) { return null; }
        finally { task.cancel(true); }
    }
    private static String clean(String name) {
        if (name == null) return null;
        name = name.trim();
        if (name.length() == 0 || name.length() > 160) return null;
        for (int i = 0; i < name.length(); i++) if (Character.isISOControl(name.charAt(i))) return null;
        return name;
    }
}
