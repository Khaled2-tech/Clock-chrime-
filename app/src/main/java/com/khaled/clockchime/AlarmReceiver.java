package com.khaled.clockchime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import java.util.Calendar;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "ClockChime";

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock lock = null;
        if (pm != null) {
            lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "clockchime:alarm");
            lock.setReferenceCounted(false);
            lock.acquire(20000L);
        }
        try {
            AlarmScheduler.schedule(context);
            if (!Prefs.enabled(context)) return;
            if (Quiet.isBlocked(context)) {
                Log.i(TAG, "quiet mode, skip");
                return;
            }
            Calendar now = Calendar.getInstance();
            int minute = now.get(Calendar.MINUTE);
            if (minute == 59) {
                now.add(Calendar.HOUR_OF_DAY, 1);
            } else if (minute > 2) {
                Log.i(TAG, "not on the hour, minute=" + minute);
                return;
            }
            int hour = now.get(Calendar.HOUR_OF_DAY);
            if (!AlarmScheduler.inWindow(hour, Prefs.fromHour(context), Prefs.toHour(context))) {
                return;
            }
            String key = now.get(Calendar.YEAR) + "-" + now.get(Calendar.DAY_OF_YEAR) + "-" + hour;
            if (Prefs.wasPlayed(context, key)) return;
            Prefs.markPlayed(context, key);
            ChimeService.start(context, false, Prefs.volume(context));
        } finally {
            if (lock != null && lock.isHeld()) {
                lock.release();
            }
        }
    }
}
