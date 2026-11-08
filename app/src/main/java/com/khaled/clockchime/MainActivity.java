package com.khaled.clockchime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
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
    private TextView pickHourLabel;
    private Switch enableSwitch;
    private SeekBar volumeSeek;
    private EditText volumeInput;
    private LinearLayout excludedList;
    private int pickHour = 22;
    private boolean syncingVolume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clockText = (TextView) findViewById(R.id.clockText);
        statusText = (TextView) findViewById(R.id.statusText);
        volumeLabel = (TextView) findViewById(R.id.volumeLabel);
        pickHourLabel = (TextView) findViewById(R.id.pickHourLabel);
        enableSwitch = (Switch) findViewById(R.id.enableSwitch);
        volumeSeek = (SeekBar) findViewById(R.id.volumeSeek);
        volumeInput = (EditText) findViewById(R.id.volumeInput);
        excludedList = (LinearLayout) findViewById(R.id.excludedList);

        enableSwitch.setChecked(Prefs.enabled(this));
        int vol = Prefs.volume(this);
        volumeSeek.setProgress(vol);
        volumeInput.setText(String.valueOf(vol));
        showVolume();
        showPickHour();
        refreshExcludedList();

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
                if (!fromUser || syncingVolume) return;
                applyVolume(progress, true);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                applyVolume(bar.getProgress(), true);
            }
        });

        findViewById(R.id.volumeApply).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyVolumeFromInput();
            }
        });

        findViewById(R.id.pickMinus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickHour = Prefs.hour(pickHour - 1);
                showPickHour();
            }
        });
        findViewById(R.id.pickPlus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickHour = Prefs.hour(pickHour + 1);
                showPickHour();
            }
        });
        findViewById(R.id.addExcluded).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.addExcludedHour(MainActivity.this, pickHour);
                refreshExcludedList();
                AlarmScheduler.schedule(MainActivity.this);
                render();
                Toast.makeText(MainActivity.this,
                        "تم استبعاد " + Prefs.formatHourAmPm(pickHour),
                        Toast.LENGTH_SHORT).show();
            }
        });

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

    private void applyVolumeFromInput() {
        String text = volumeInput.getText().toString().trim();
        if (text.length() == 0) {
            Toast.makeText(this, "أدخل رقمًا من 0 إلى 100.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int value = Integer.parseInt(text);
            applyVolume(value, true);
        } catch (NumberFormatException ex) {
            Toast.makeText(this, "أدخل رقمًا صحيحًا من 0 إلى 100.", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyVolume(int value, boolean updateInput) {
        value = Prefs.clamp(value);
        Prefs.setVolume(this, value);
        syncingVolume = true;
        volumeSeek.setProgress(value);
        if (updateInput) {
            volumeInput.setText(String.valueOf(value));
            volumeInput.setSelection(volumeInput.getText().length());
        }
        syncingVolume = false;
        showVolume();
    }

    private void testChime() {
        applyVolumeFromInput();
        int volume = Prefs.volume(this);
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
        volumeLabel.setText("مستوى صوت الجرس: " + Prefs.volume(this) + "%");
    }

    private void showPickHour() {
        pickHourLabel.setText(Prefs.formatHourAmPm(pickHour));
    }

    private void refreshExcludedList() {
        excludedList.removeAllViews();
        List<Integer> hours = Prefs.excludedHoursSorted(this);
        if (hours.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("لا توجد أوقات مستبعدة. يرن عند كل ساعة.");
            empty.setTextColor(0xFF4E5A53);
            empty.setTextSize(14f);
            empty.setPadding(0, 8, 0, 8);
            excludedList.addView(empty);
            return;
        }
        for (int i = 0; i < hours.size(); i++) {
            final int hour = hours.get(i).intValue();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 6, 0, 6);

            TextView label = new TextView(this);
            label.setText(Prefs.formatHourAmPm(hour));
            label.setTextColor(0xFF1A2420);
            label.setTextSize(16f);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button remove = new Button(this);
            remove.setText("حذف");
            remove.setTextColor(Color.WHITE);
            remove.setTextSize(14f);
            remove.setBackgroundResource(R.drawable.btn_small);
            if (Build.VERSION.SDK_INT >= 21) {
                remove.setBackgroundTintList(null);
            }
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            remove.setLayoutParams(bp);
            remove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.removeExcludedHour(MainActivity.this, hour);
                    refreshExcludedList();
                    AlarmScheduler.schedule(MainActivity.this);
                    render();
                }
            });

            row.addView(label);
            row.addView(remove);
            excludedList.addView(row);
        }
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
            statusText.setText("كل الساعات مستبعدة");
            return;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(next);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        statusText.setText("الرنة القادمة: " + Prefs.formatHourAmPm(hour));
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
