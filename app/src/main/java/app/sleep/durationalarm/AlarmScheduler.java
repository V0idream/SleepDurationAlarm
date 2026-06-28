package app.sleep.durationalarm;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

final class AlarmScheduler {
    private static final String CLOCK_PACKAGE = "com.coloros.alarmclock";
    private static final String CLOCK_API_ACTIVITY =
            "com.oplus.alarmclock.cts.HandleApiActivity";
    private static final String EXTRA_DELETE_AFTER_USE =
            "android.intent.extra.alarm.DELETE_AFTER_USE";

    private AlarmScheduler() {
    }

    static long createOneTimeAlarm(
            Context context, int durationMinutes, String label) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("计划睡眠时长必须大于 0 分钟");
        }

        Calendar target = Calendar.getInstance();
        target.add(Calendar.MINUTE, durationMinutes);
        if (target.get(Calendar.SECOND) != 0
                || target.get(Calendar.MILLISECOND) != 0) {
            target.add(Calendar.MINUTE, 1);
        }
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.setClassName(CLOCK_PACKAGE, CLOCK_API_ACTIVITY);
        intent.putExtra(AlarmClock.EXTRA_HOUR, target.get(Calendar.HOUR_OF_DAY));
        intent.putExtra(AlarmClock.EXTRA_MINUTES, target.get(Calendar.MINUTE));
        intent.putExtra(AlarmClock.EXTRA_MESSAGE,
                label == null || label.trim().isEmpty()
                        ? Config.DEFAULT_LABEL
                        : label.trim());
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        intent.putExtra(EXTRA_DELETE_AFTER_USE, 1);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
        return target.getTimeInMillis();
    }

    static String formatTime(long timeMillis) {
        return new SimpleDateFormat(
                "M月d日 HH:mm", Locale.getDefault()).format(new Date(timeMillis));
    }
}
