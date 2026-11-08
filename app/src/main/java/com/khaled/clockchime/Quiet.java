package com.khaled.clockchime;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

public final class Quiet {
    private Quiet() {}

    /** True when the phone is silent or Do Not Disturb is on. */
    public static boolean isBlocked(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
                return true;
            }
        } catch (Exception ignored) {
        }
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                NotificationManager nm =
                        (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    int filter = nm.getCurrentInterruptionFilter();
                    if (filter == NotificationManager.INTERRUPTION_FILTER_NONE
                            || filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
