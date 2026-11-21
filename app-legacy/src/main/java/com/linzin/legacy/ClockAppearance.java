package com.linzin.legacy;

import android.content.SharedPreferences;
import java.util.Locale;

enum ClockAppearance {
    TK("tk", 0, 0), TTN("ttn", 0, 1), RAFM("rafm", 0, 2),
    ABW("abw", 1, 0), OET("oet", 1, 1), ZMG("zmg", 1, 2),
    PP("pp", 2, 0), FFT("fft", 2, 1);

    final String prefix;
    final int family;
    final int style;
    ClockAppearance(String prefix, int family, int style) { this.prefix = prefix; this.family = family; this.style = style; }
    String familyKey() { return new String[] { "digital", "analog", "flip" }[family]; }
    String label() { return prefix.toUpperCase(Locale.US); }
    boolean seconds(SharedPreferences prefs) { return prefs.getBoolean("pref_" + familyKey() + "_show_sec", family == 1); }
    void save(SharedPreferences prefs) {
        prefs.edit().putInt("pref_main_page", family).putInt("pref_" + familyKey() + "_style_idx", style).apply();
    }
    static ClockAppearance read(SharedPreferences prefs) {
        int family = prefs.getInt("pref_main_page", 0);
        if (family < 0 || family > 2) family = 0;
        String key = new String[] { "digital", "analog", "flip" }[family];
        int style = prefs.getInt("pref_" + key + "_style_idx", 0);
        for (ClockAppearance value : values()) if (value.family == family && value.style == style) return value;
        return family == 1 ? ABW : family == 2 ? PP : TK;
    }
}
