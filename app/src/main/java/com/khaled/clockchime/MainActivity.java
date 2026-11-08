package com.khaled.clockchime;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 1000L);
        }
    };

    private TextView clockText;
    private TextView statusText;
    private TextView volumeLabel;
    private TextView fromLabel;
    private TextView toLabel;
    private Switch enableSwitch;
    private SeekBar volumeSeek;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clockText = (TextView) findViewById(R.id.clockText);
        statusText = (TextView) findViewById(R.id.statusText);
        volumeLabel = (TextView) findViewById(R.id.volumeLabel);
        fromLabel = (TextView) findViewById(R.id.fromLabel);
        toLabel = (TextView) findViewById(R.id.toLabel);
        enableSwitch = (Switch) findViewById(R.id.enableSwitch);
        volumeSeek = (SeekBar) findViewById(R.id.volumeSeek);

        enableSwitch.setChecked(Prefs.enabled(this));
        volumeSeek.setProgress(Prefs.volume(this));
        showHours();
        showVolume();

        enableSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton button, boolean checked) {
                Prefs.setEnabled(MainActivity.this, checked);
                AlarmScheduler.schedule(MainActivity.this);
                render();
            }
        });
        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                Prefs.setVolume(MainActivity.this, progress);
                showVolume();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                Prefs.setVolume(MainActivity.this, bar.getProgress());
                showVolume();
            }
        });

        bindHour((Button) findViewById(R.id.fromMinus), true, -1);
        bindHour((Button) findViewById(R.id.fromPlus), true, 1);
        bindHour((Button) findViewById(R.id.toMinus), false, -1);
        bindHour((Button) findViewById(R.id.toPlus), false, 1);

        findViewById(R.id.testButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testChime();
            }
        });
        findViewById(R.id.batteryButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askBattery();
            }
        });
    }

    private void bindHour(Button button, final boolean from, final int delta) {
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (from) {
                    Prefs.setFromHour(MainActivity.this, Prefs.fromHour(MainActivity.this) + delta);
                } else {
                    Prefs.setToHour(MainActivity.this, Prefs.toHour(MainActivity.this) + delta);
                }
                showHours();
                AlarmScheduler.schedule(MainActivity.this);
                render();
            }
        });
    }

    private void testChime() {
        int volume = volumeSeek.getProgress();
        Prefs.setVolume(this, volume);
        if (volume <= 0) {
            Toast.makeText(this, "مستوى الصوت صفر.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Quiet.isBlocked(this)) {
            Toast.makeText(this, "الهاتف على الصامت أو عدم الإزعاج. لن يُشغَّل الصوت.", Toast.LENGTH_LONG).show();
            return;
        }
        ChimeService.start(this, true, volume);
    }

    private void askBattery() {
        if (Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, "هذا النظام لا يحتاج هذا الإذن.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "العمل في الخلفية مسموح بالفعل.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ex) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ex2) {
                Toast.makeText(this, "افتح إعدادات البطارية واسمح للتطبيق بدون قيود.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showVolume() {
        volumeLabel.setText("مستوى صوت الجرس: " + volumeSeek.getProgress() + "%");
    }

    private void showHours() {
        fromLabel.setText(hourText(Prefs.fromHour(this)));
        toLabel.setText(hourText(Prefs.toHour(this)));
    }

    private static String hourText(int hour) {
        return String.format(Locale.US, "%02d:00", hour);
    }

    private void render() {
        Calendar now = Calendar.getInstance();
        clockText.setText(String.format(
                Locale.US,
                "%02d:%02d:%02d",
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                now.get(Calendar.SECOND)));
        if (!Prefs.enabled(this)) {
            statusText.setText("الجرس متوقف");
            return;
        }
        long next = AlarmScheduler.nextTrigger(this);
        if (next < 0L) {
            statusText.setText("لا توجد ساعة داخل الفترة");
            return;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(next);
        statusText.setText("الرنة القادمة: " + String.format(
                Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmScheduler.schedule(this);
        render();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }
}
