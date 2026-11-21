package com.linzin.clockcore;

public final class CityNames {
    private CityNames() { }
    public static String select(String locality, String city, String country, String subdivision,
        int[] levels, String[] names) {
        if (levels.length != names.length) throw new IllegalArgumentException("Misaligned administrative entries");
        country = clean(country); subdivision = clean(subdivision);
        for (String direct : new String[] { locality, city }) {
            String candidate = clean(direct);
            if (usable(candidate, country, subdivision)) return candidate;
        }
        for (int i = levels.length - 1; i >= 0; i--) {
            String candidate = clean(names[i]);
            if (levels[i] >= 5 && levels[i] <= 8 && usable(candidate, country, subdivision)) return candidate;
        }
        return null;
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static boolean usable(String value, String country, String subdivision) {
        return !value.isEmpty() && !value.equals(country) && !value.equals(subdivision);
    }
}
