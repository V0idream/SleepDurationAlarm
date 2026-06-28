package app.sleep.durationalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class AlarmBridgeReceiver extends BroadcastReceiver {
    static final String EXTRA_DURATION_MINUTES = "duration_minutes";
    static final String EXTRA_LABEL = "label";
    private static final String TAG = "SleepDurationAlarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        int durationMinutes = intent.getIntExtra(
                EXTRA_DURATION_MINUTES, 0);
        String label = intent.getStringExtra(EXTRA_LABEL);
        try {
            long target = AlarmScheduler.createOneTimeAlarm(
                    context, durationMinutes, label);
            Log.i(TAG, "One-time alarm created by module bridge: "
                    + AlarmScheduler.formatTime(target));
        } catch (Throwable throwable) {
            Log.e(TAG, "Module bridge failed to create alarm", throwable);
        }
    }
}
