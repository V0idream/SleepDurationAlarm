package app.sleep.durationalarm;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int COLOR_PRIMARY = Color.rgb(38, 92, 246);
    private static final int COLOR_TEXT = Color.rgb(28, 32, 42);
    private static final int COLOR_MUTED = Color.rgb(104, 112, 130);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_BACKGROUND = Color.rgb(245, 247, 251);

    private SharedPreferences preferences;
    private Switch enabledSwitch;
    private NumberPicker hoursPicker;
    private NumberPicker minutesPicker;
    private EditText labelInput;
    private TextView summaryText;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        preferences = getSharedPreferences(
                Config.PREFS_NAME, Context.MODE_PRIVATE);
        setContentView(createContent());
        loadConfig();
    }

    private View createContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout root = vertical();
        root.setPadding(dp(24), dp(28), dp(24), dp(32));
        scroll.addView(root);

        TextView eyebrow = text("SLEEP DURATION ALARM", 12, COLOR_PRIMARY);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(eyebrow);

        TextView title = text("睡眠时长闹钟", 30, COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, marginTop(dp(6)));

        TextView intro = text(
                "检测到 OPPO Watch 入睡后，立即按计划睡眠时长创建一次性闹钟。",
                15, COLOR_MUTED);
        intro.setLineSpacing(0, 1.25f);
        root.addView(intro, marginTop(dp(10)));

        LinearLayout planCard = card();
        root.addView(planCard, marginTop(dp(24)));

        enabledSwitch = new Switch(this);
        enabledSwitch.setText("启用入睡闹钟计划");
        enabledSwitch.setTextSize(17);
        enabledSwitch.setTextColor(COLOR_TEXT);
        enabledSwitch.setTypeface(Typeface.DEFAULT_BOLD);
        planCard.addView(enabledSwitch,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        planCard.addView(text("计划睡眠时长", 13, COLOR_MUTED),
                marginTop(dp(24)));

        LinearLayout durationRow = new LinearLayout(this);
        durationRow.setOrientation(LinearLayout.HORIZONTAL);
        durationRow.setGravity(Gravity.CENTER);
        planCard.addView(durationRow, marginTop(dp(8)));

        hoursPicker = picker(0, 23);
        minutesPicker = picker(0, 59);
        durationRow.addView(hoursPicker, weighted());
        durationRow.addView(text("小时", 16, COLOR_TEXT));
        durationRow.addView(minutesPicker, weighted());
        durationRow.addView(text("分钟", 16, COLOR_TEXT));

        planCard.addView(text("闹钟名称", 13, COLOR_MUTED),
                marginTop(dp(20)));
        labelInput = new EditText(this);
        labelInput.setSingleLine(true);
        labelInput.setTextSize(16);
        labelInput.setTextColor(COLOR_TEXT);
        labelInput.setHint(Config.DEFAULT_LABEL);
        labelInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        labelInput.setBackground(rounded(
                Color.rgb(242, 244, 248), dp(12)));
        planCard.addView(labelInput, marginTop(dp(8)));

        Button saveButton = button("保存计划", COLOR_PRIMARY, Color.WHITE);
        saveButton.setOnClickListener(view -> saveConfig());
        root.addView(saveButton, marginTop(dp(20)));

        LinearLayout statusCard = card();
        root.addView(statusCard, marginTop(dp(16)));
        TextView statusTitle = text("当前计划", 16, COLOR_TEXT);
        statusTitle.setTypeface(Typeface.DEFAULT_BOLD);
        statusCard.addView(statusTitle);
        summaryText = text("", 15, COLOR_MUTED);
        summaryText.setLineSpacing(0, 1.3f);
        statusCard.addView(summaryText, marginTop(dp(8)));

        Button testButton = button(
                "测试创建 2 分钟后的闹钟",
                Color.TRANSPARENT, COLOR_PRIMARY);
        GradientDrawable testBackground = rounded(
                Color.TRANSPARENT, dp(14));
        testBackground.setStroke(dp(1), COLOR_PRIMARY);
        testButton.setBackground(testBackground);
        testButton.setOnClickListener(view -> testAlarm());
        root.addView(testButton, marginTop(dp(16)));

        TextView note = text(
                "LSPosed 作用域仅需勾选“OPPO 健康”。首次使用请保存计划并重启健康进程。",
                13, COLOR_MUTED);
        note.setLineSpacing(0, 1.25f);
        root.addView(note, marginTop(dp(20)));

        return scroll;
    }

    private void loadConfig() {
        enabledSwitch.setChecked(preferences.getBoolean(
                Config.KEY_ENABLED, Config.DEFAULT_ENABLED));
        hoursPicker.setValue(preferences.getInt(
                Config.KEY_HOURS, Config.DEFAULT_HOURS));
        minutesPicker.setValue(preferences.getInt(
                Config.KEY_MINUTES, Config.DEFAULT_MINUTES));
        labelInput.setText(preferences.getString(
                Config.KEY_LABEL, Config.DEFAULT_LABEL));
        updateSummary();
    }

    private void saveConfig() {
        int totalMinutes = hoursPicker.getValue() * 60
                + minutesPicker.getValue();
        if (totalMinutes <= 0) {
            Toast.makeText(this,
                    "计划睡眠时长必须大于 0 分钟",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String label = labelInput.getText().toString().trim();
        if (label.isEmpty()) {
            label = Config.DEFAULT_LABEL;
            labelInput.setText(label);
        }

        preferences.edit()
                .putBoolean(Config.KEY_ENABLED, enabledSwitch.isChecked())
                .putInt(Config.KEY_HOURS, hoursPicker.getValue())
                .putInt(Config.KEY_MINUTES, minutesPicker.getValue())
                .putString(Config.KEY_LABEL, label)
                .apply();
        ConfigBridgeReceiver.sendConfigToHealth(this);
        updateSummary();
        Toast.makeText(this, "计划已保存", Toast.LENGTH_SHORT).show();
    }

    private void testAlarm() {
        try {
            long target = AlarmScheduler.createOneTimeAlarm(
                    this, 2, "睡眠闹钟测试");
            Toast.makeText(this,
                    "已创建一次性闹钟：" + AlarmScheduler.formatTime(target),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable throwable) {
            Toast.makeText(this,
                    "创建失败：" + throwable.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void updateSummary() {
        int hours = hoursPicker.getValue();
        int minutes = minutesPicker.getValue();
        String duration = (hours > 0 ? hours + " 小时 " : "")
                + (minutes > 0 ? minutes + " 分钟" : "");
        summaryText.setText(
                (enabledSwitch.isChecked() ? "已启用" : "已暂停")
                        + "\n检测到入睡后 " + duration + " 响铃"
                        + "\n名称：" + labelInput.getText().toString());
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(rounded(COLOR_SURFACE, dp(18)));
        card.setElevation(dp(2));
        return card;
    }

    private NumberPicker picker(int min, int max) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setWrapSelectorWheel(true);
        return picker;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(dp(54));
        button.setBackground(rounded(background, dp(14)));
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams marginTop(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = margin;
        return params;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, dp(120), 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
