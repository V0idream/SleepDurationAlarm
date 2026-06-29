package app.sleep.durationalarm;

final class Config {
    static final String MODULE_PACKAGE = "app.sleep.durationalarm";
    static final String PREFS_NAME = "config";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_HOURS = "hours";
    static final String KEY_MINUTES = "minutes";
    static final String KEY_LABEL = "label";
    static final String ACTION_CONFIG_REQUEST =
            "app.sleep.durationalarm.CONFIG_REQUEST";
    static final String ACTION_CONFIG_UPDATE =
            "app.sleep.durationalarm.CONFIG_UPDATE";
    static final String ACTION_DISABLE_PLAN =
            "app.sleep.durationalarm.DISABLE_PLAN";
    static final String HEALTH_PACKAGE = "com.heytap.health";
    static final String EXTRA_SLEEP_ALARM =
            "app.sleep.durationalarm.EXTRA_SLEEP_ALARM";
    static final String EXTRA_CLOCK_BRIDGE =
            "app.sleep.durationalarm.EXTRA_CLOCK_BRIDGE";
    static final String EXTRA_DURATION_MINUTES = "duration_minutes";
    static final String EXTRA_ALARM_LABEL = "alarm_label";

    static final boolean DEFAULT_ENABLED = true;
    static final int DEFAULT_HOURS = 8;
    static final int DEFAULT_MINUTES = 0;
    static final String DEFAULT_LABEL = "睡眠结束";

    private Config() {
    }
}
