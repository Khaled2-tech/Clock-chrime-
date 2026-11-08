package com.khaled.clockchime;

import android.content.Context;
import android.content.SharedPreferences;

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

    public static int fromHour(Context ctx) {
        return hour(sp(ctx).getInt("from", 0));
    }

    public static void setFromHour(Context ctx, int hour) {
        sp(ctx).edit().putInt("from", hour(hour)).apply();
    }

    public static int toHour(Context ctx) {
        return hour(sp(ctx).getInt("to", 23));
    }

    public static void setToHour(Context ctx, int hour) {
        sp(ctx).edit().putInt("to", hour(hour)).apply();
    }

    public static boolean wasPlayed(Context ctx, String key) {
        return key.equals(sp(ctx).getString("last_key", ""));
    }

    public static void markPlayed(Context ctx, String key) {
        sp(ctx).edit().putString("last_key", key).commit();
    }

    public static void markVolumeOverride(Context ctx, int stream, int previous) {
        sp(ctx).edit()
                .putBoolean("vol_dirty", true)
                .putInt("saved_stream", stream)
                .putInt("saved_vol", previous)
                .commit();
    }

    public static boolean volumeDirty(Context ctx) {
        return sp(ctx).getBoolean("vol_dirty", false);
    }

    public static int savedStream(Context ctx) {
        return sp(ctx).getInt("saved_stream", -1);
    }

    public static int savedVolume(Context ctx) {
        return sp(ctx).getInt("saved_vol", -1);
    }

    public static void clearVolumeOverride(Context ctx) {
        sp(ctx).edit().putBoolean("vol_dirty", false).commit();
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
}
