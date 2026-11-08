package com.khaled.clockchime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public final class AlarmScheduler {
    private static final String TAG = "ClockChime";
    private static final int REQ_ALARM = 1001;
    private static final int REQ_SHOW = 1002;

    private AlarmScheduler() {}

    public static boolean shouldChime(Context ctx, int hour) {
        return !Prefs.isExcluded(ctx, hour);
    }

    public static long nextTrigger(Context ctx) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1500L) {
            cal.add(Calendar.HOUR_OF_DAY, 1);
        }
        for (int i = 0; i < 26; i++) {
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (shouldChime(ctx, hour)) {
                return cal.getTimeInMillis();
            }
            cal.add(Calendar.HOUR_OF_DAY, 1);
        }
        return -1L;
    }

    public static void schedule(Context ctx) {
        Context app = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent op = operation(app);
        am.cancel(op);
        if (!Prefs.enabled(app)) {
            Log.i(TAG, "chime disabled, alarm cancelled");
            return;
        }
        long when = nextTrigger(app);
        if (when < 0L) return;
        Intent show = new Intent(app, MainActivity.class);
        show.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent showPi = PendingIntent.getActivity(app, REQ_SHOW, show, flags());
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, showPi), op);
            Log.i(TAG, "next chime at " + when);
        } catch (Exception ex) {
            Log.w(TAG, "setAlarmClock failed, falling back", ex);
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, op);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, when, op);
            }
        }
    }

    private static PendingIntent operation(Context ctx) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.setAction("com.khaled.clockchime.CHIME");
        return PendingIntent.getBroadcast(ctx, REQ_ALARM, i, flags());
    }

    private static int flags() {
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            f |= PendingIntent.FLAG_IMMUTABLE;
        }
        return f;
    }
}
