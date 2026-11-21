package com.linzin.legacy;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.view.ContextThemeWrapper;
import java.util.Locale;

final class LegacyLocales {
    static String tag(Context context) {
        String value = context.getSharedPreferences("desk_clock_prefs", 0).getString("pref_app_language", "system");
        return "en".equals(value) || "ja".equals(value) || "zh-TW".equals(value) || "zh-CN".equals(value) ? value : "system";
    }
    private static Locale locale(String tag) {
        if ("ja".equals(tag)) return Locale.JAPANESE;
        if ("zh-TW".equals(tag)) return Locale.TRADITIONAL_CHINESE;
        if ("zh-CN".equals(tag)) return Locale.SIMPLIFIED_CHINESE;
        return Locale.ENGLISH;
    }
    static Context wrap(Context base) {
        String tag = tag(base);
        if ("system".equals(tag)) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration()); config.locale = locale(tag);
        if (Build.VERSION.SDK_INT >= 17) return base.createConfigurationContext(config);
        try {
            // API14–16 lack configuration contexts; separate assets keep other contexts' language intact.
            AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
            int cookie = (Integer) AssetManager.class.getMethod("addAssetPath", String.class).invoke(assets, base.getApplicationInfo().sourceDir);
            if (cookie == 0) { assets.close(); throw new IllegalStateException("Unable to load app language resources"); }
            Resources resources = new Resources(assets, base.getResources().getDisplayMetrics(), config);
            return new ContextThemeWrapper(base, android.R.style.Theme_Holo_NoActionBar) {
                @Override public Resources getResources() { return resources; }
            };
        } catch (Exception error) {
            throw new IllegalStateException("Unable to isolate Activity language resources", error);
        }
    }
    static void configurationChanged(Context context, Configuration configuration) {
        String tag = tag(context);
        if ("system".equals(tag)) return;
        Configuration local = new Configuration(configuration); local.locale = locale(tag);
        context.getResources().updateConfiguration(local, context.getResources().getDisplayMetrics());
    }
}
