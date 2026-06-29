package app.sleep.durationalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class ConfigBridgeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (Config.ACTION_CONFIG_REQUEST.equals(intent.getAction())) {
            sendConfigToHealth(context);
        } else if (Config.ACTION_DISABLE_PLAN.equals(intent.getAction())) {
            context.getSharedPreferences(
                            Config.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(Config.KEY_ENABLED, false)
                    .apply();
            sendConfigToHealth(context);
        }
    }

    static void sendConfigToHealth(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                Config.PREFS_NAME, Context.MODE_PRIVATE);
        Intent update = new Intent(Config.ACTION_CONFIG_UPDATE);
        update.setPackage(Config.HEALTH_PACKAGE);
        update.putExtra(Config.KEY_ENABLED, preferences.getBoolean(
                Config.KEY_ENABLED, Config.DEFAULT_ENABLED));
        update.putExtra(Config.KEY_HOURS, preferences.getInt(
                Config.KEY_HOURS, Config.DEFAULT_HOURS));
        update.putExtra(Config.KEY_MINUTES, preferences.getInt(
                Config.KEY_MINUTES, Config.DEFAULT_MINUTES));
        update.putExtra(Config.KEY_LABEL, preferences.getString(
                Config.KEY_LABEL, Config.DEFAULT_LABEL));
        update.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(update);
    }
}
