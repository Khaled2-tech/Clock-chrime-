package com.khaled.clockchime;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Prefs {
    private static final String NAME = "chime";

    private Prefs() {}

    private static SharedPreferences sp(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context ctx) {
        return sp(ctx).getBoolean("enabled", true);
    }

    public static void setEnabled(Context ctx, boolean value) {
        sp(ctx).edit().putBoolean("enabled", value).apply();
    }

    public static int volume(Context ctx) {
        return clamp(sp(ctx).getInt("volume", 70));
    }

    public static void setVolume(Context ctx, int value) {
        sp(ctx).edit().putInt("volume", clamp(value)).apply();
    }

    /** Hours 0-23 that must not chime. */
    public static Set<Integer> excludedHours(Context ctx) {
        Set<String> raw = sp(ctx).getStringSet("excluded", Collections.<String>emptySet());
        Set<Integer> out = new HashSet<Integer>();
        if (raw != null) {
            for (String s : raw) {
                try {
                    out.add(hour(Integer.parseInt(s)));
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    public static List<Integer> excludedHoursSorted(Context ctx) {
        List<Integer> list = new ArrayList<Integer>(excludedHours(ctx));
        Collections.sort(list);
        return list;
    }

    public static void addExcludedHour(Context ctx, int hour) {
        Set<Integer> set = excludedHours(ctx);
        set.add(hour(hour));
        saveExcluded(ctx, set);
    }

    public static void removeExcludedHour(Context ctx, int hour) {
        Set<Integer> set = excludedHours(ctx);
        set.remove(Integer.valueOf(hour(hour)));
        saveExcluded(ctx, set);
    }

    public static boolean isExcluded(Context ctx, int hour) {
        return excludedHours(ctx).contains(Integer.valueOf(hour(hour)));
    }

    private static void saveExcluded(Context ctx, Set<Integer> hours) {
        Set<String> raw = new HashSet<String>();
        for (Integer h : hours) {
            raw.add(String.valueOf(hour(h.intValue())));
        }
        sp(ctx).edit().putStringSet("excluded", raw).apply();
    }

    public static boolean wasPlayed(Context ctx, String key) {
        return key.equals(sp(ctx).getString("last_key", ""));
    }

    public static void markPlayed(Context ctx, String key) {
        sp(ctx).edit().putString("last_key", key).commit();
    }

    public static int clamp(int volume) {
        if (volume < 0) return 0;
        if (volume > 100) return 100;
        return volume;
    }

    public static int hour(int value) {
        int h = value % 24;
        if (h < 0) h += 24;
        return h;
    }

    /** Example: 14 -> "2:00 مساءً", 0 -> "12:00 صباحًا". */
    public static String formatHourAmPm(int hour24) {
        hour24 = hour(hour24);
        int h12 = hour24 % 12;
        if (h12 == 0) h12 = 12;
        String period = hour24 < 12 ? "صباحًا" : "مساءً";
        return String.format(Locale.US, "%d:00 %s", h12, period);
    }
}
