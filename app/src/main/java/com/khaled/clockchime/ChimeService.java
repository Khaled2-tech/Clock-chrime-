package com.khaled.clockchime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

public class ChimeService extends Service {
    private static final String TAG = "ClockChime";
    private static final String CHANNEL = "chime";
    private static final int NOTE_ID = 7;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable stopTask;

    private MediaPlayer player;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private boolean volumeChanged;
    private int usedStream = AudioManager.STREAM_ALARM;
    private int savedVolume = -1;
    private int activeStartId = -1;

    public static void start(Context ctx, boolean test, int volume) {
        Intent i = new Intent(ctx, ChimeService.class);
        i.putExtra("test", test);
        i.putExtra("volume", Prefs.clamp(volume));
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        promote();
        activeStartId = startId;
        boolean test = intent != null && intent.getBooleanExtra("test", false);
        if (intent == null || Quiet.isBlocked(this)) {
            end(startId);
            return START_NOT_STICKY;
        }
        int volume = intent.hasExtra("volume")
                ? intent.getIntExtra("volume", Prefs.volume(this))
                : Prefs.volume(this);
        if (!test && !Prefs.enabled(this)) {
            end(startId);
            return START_NOT_STICKY;
        }
        play(volume, startId);
        return START_NOT_STICKY;
    }

    private void promote() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel existing = nm.getNotificationChannel(CHANNEL);
            if (existing == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL, "جرس الساعة", NotificationManager.IMPORTANCE_LOW);
                ch.setSound(null, null);
                ch.enableVibration(false);
                nm.createNotificationChannel(ch);
            }
        }
        Notification note = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("جرس الساعة")
                .setContentText("يرن الجرس")
                .setOngoing(true)
                .build();
        startForeground(NOTE_ID, note);
    }

    private void play(int percent, final int startId) {
        releasePlayer();
        restoreVolume();
        releaseWakeLock();
        if (stopTask != null) handler.removeCallbacks(stopTask);
        percent = Prefs.clamp(percent);
        if (percent <= 0) {
            end(startId);
            return;
        }
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "clockchime:play");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(15000L);
        }
        final int stream = applyAbsoluteVolume(percent);
        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        AssetFileDescriptor afd = null;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(usageFor(stream))
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setLooping(false);
            mp.setVolume(1f, 1f);
            afd = getResources().openRawResourceFd(R.raw.hour_chime);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    end(startId);
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    Log.w(TAG, "player error " + what + "/" + extra);
                    end(startId);
                    return true;
                }
            });
            mp.prepare();
            mp.start();
            stopTask = new Runnable() {
                @Override
                public void run() {
                    end(startId);
                }
            };
            handler.postDelayed(stopTask, 6000L);
        } catch (Exception ex) {
            Log.w(TAG, "play failed", ex);
            end(startId);
        } finally {
            if (afd != null) {
                try {
                    afd.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static int usageFor(int stream) {
        if (stream == AudioManager.STREAM_MUSIC) return AudioAttributes.USAGE_MEDIA;
        if (stream == AudioManager.STREAM_RING) return AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
        return AudioAttributes.USAGE_ALARM;
    }

    private int applyAbsoluteVolume(int percent) {
        int[] streams = new int[] {
                AudioManager.STREAM_ALARM,
                AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_RING
        };
        if (audioManager == null) return AudioManager.STREAM_ALARM;
        for (int i = 0; i < streams.length; i++) {
            int stream = streams[i];
            int max = audioManager.getStreamMaxVolume(stream);
            if (max <= 0) continue;
            int previous = audioManager.getStreamVolume(stream);
            int target = (int) Math.round(max * (percent / 100.0));
            if (target < 1) target = 1;
            if (target > max) target = max;
            try {
                audioManager.setStreamVolume(stream, target, 0);
                usedStream = stream;
                savedVolume = previous;
                volumeChanged = true;
                Prefs.markVolumeOverride(this, stream, previous);
                Log.i(TAG, "volume stream=" + stream + " " + previous + " -> " + target + " (" + percent + "%)");
                return stream;
            } catch (Exception ex) {
                Log.w(TAG, "cannot set stream " + stream, ex);
            }
        }
        return AudioManager.STREAM_ALARM;
    }

    private void restoreVolume() {
        int stream = -1;
        int previous = -1;
        if (volumeChanged) {
            stream = usedStream;
            previous = savedVolume;
        } else if (Prefs.volumeDirty(this)) {
            stream = Prefs.savedStream(this);
            previous = Prefs.savedVolume(this);
        }
        volumeChanged = false;
        savedVolume = -1;
        Prefs.clearVolumeOverride(this);
        if (stream < 0 || previous < 0) return;
        try {
            if (audioManager == null) {
                audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            }
            if (audioManager != null) {
                int max = audioManager.getStreamMaxVolume(stream);
                if (previous > max) previous = max;
                if (previous < 0) previous = 0;
                audioManager.setStreamVolume(stream, previous, 0);
            }
        } catch (Exception ex) {
            Log.w(TAG, "restore volume failed", ex);
        }
    }

    private void releasePlayer() {
        MediaPlayer mp = player;
        player = null;
        if (mp == null) return;
        try {
            mp.setOnCompletionListener(null);
            mp.setOnErrorListener(null);
            if (mp.isPlaying()) mp.stop();
        } catch (Exception ignored) {
        }
        try {
            mp.release();
        } catch (Exception ignored) {
        }
    }

    private void releaseWakeLock() {
        PowerManager.WakeLock lock = wakeLock;
        wakeLock = null;
        if (lock != null && lock.isHeld()) {
            try {
                lock.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void end(int startId) {
        if (startId != activeStartId) return;
        activeStartId = -1;
        if (stopTask != null) handler.removeCallbacks(stopTask);
        releasePlayer();
        restoreVolume();
        releaseWakeLock();
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                stopForeground(true);
            } catch (Exception ignored) {
            }
        }
        stopSelf(startId);
    }

    @Override
    public void onDestroy() {
        if (stopTask != null) handler.removeCallbacks(stopTask);
        releasePlayer();
        restoreVolume();
        releaseWakeLock();
        activeStartId = -1;
        super.onDestroy();
    }
}
