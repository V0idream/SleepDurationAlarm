package app.sleep.durationalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "[SleepDurationAlarm] ";
    private static final String TARGET_PACKAGE = "com.heytap.health";
    private static final String TARGET_PROCESS = "com.heytap.health:transport";
    private static final String SLEEP_MODE_MANAGER =
            "com.heytap.device.sleep.SleepModeManager";
    private static final String SLEEP_MODEL_SETTINGS =
            "com.heytap.databaseengine.model.SleepModelSettings";
    private static final long DUPLICATE_GUARD_MS = 30_000L;
    private static final AtomicLong LAST_TRIGGER = new AtomicLong(0L);
    private static volatile Context processContext;
    private static volatile boolean planEnabled = Config.DEFAULT_ENABLED;
    private static volatile int planHours = Config.DEFAULT_HOURS;
    private static volatile int planMinutes = Config.DEFAULT_MINUTES;
    private static volatile String planLabel = Config.DEFAULT_LABEL;
    private static volatile boolean configReceiverRegistered;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!TARGET_PACKAGE.equals(param.packageName)
                || !TARGET_PROCESS.equals(param.processName)) {
            return;
        }

        hookApplicationContext(param.classLoader);
        hookSleepState(param.classLoader);
        XposedBridge.log(TAG + "Loaded in " + param.processName);
    }

    private static void hookApplicationContext(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                "android.app.Application",
                classLoader,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context context = (Context) param.args[0];
                        processContext = context == null
                                ? null
                                : context.getApplicationContext();
                        if (processContext == null) {
                            processContext = context;
                        }
                        registerConfigBridge(processContext);
                    }
                });
    }

    private static synchronized void registerConfigBridge(Context context) {
        if (context == null || configReceiverRegistered) {
            return;
        }
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                if (intent == null
                        || !Config.ACTION_CONFIG_UPDATE.equals(
                                intent.getAction())) {
                    return;
                }
                planEnabled = intent.getBooleanExtra(
                        Config.KEY_ENABLED, Config.DEFAULT_ENABLED);
                planHours = intent.getIntExtra(
                        Config.KEY_HOURS, Config.DEFAULT_HOURS);
                planMinutes = intent.getIntExtra(
                        Config.KEY_MINUTES, Config.DEFAULT_MINUTES);
                String label = intent.getStringExtra(Config.KEY_LABEL);
                planLabel = label == null || label.trim().isEmpty()
                        ? Config.DEFAULT_LABEL
                        : label.trim();
                XposedBridge.log(TAG
                        + "Config received: enabled=" + planEnabled
                        + ", duration=" + planHours + "h"
                        + planMinutes + "m, label=" + planLabel);
            }
        };
        IntentFilter filter = new IntentFilter(Config.ACTION_CONFIG_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                    receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        configReceiverRegistered = true;

        Intent request = new Intent(Config.ACTION_CONFIG_REQUEST);
        request.setClassName(
                Config.MODULE_PACKAGE,
                Config.MODULE_PACKAGE + ".ConfigBridgeReceiver");
        request.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(request);
        XposedBridge.log(TAG + "Config requested from module app");
    }

    private static void hookSleepState(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    SLEEP_MODE_MANAGER,
                    classLoader,
                    "t",
                    SLEEP_MODEL_SETTINGS,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object settings = param.args[0];
                            boolean startNow = Boolean.TRUE.equals(
                                    XposedHelpers.callMethod(settings, "isStartNow"));
                            boolean syncSleepMode = Boolean.TRUE.equals(
                                    XposedHelpers.callMethod(
                                            settings, "isSyncSleepMode"));
                            Object timestamp = XposedHelpers.callMethod(
                                    settings, "getTimestamp");
                            XposedBridge.log(TAG
                                    + "Sleep state received: startNow=" + startNow
                                    + ", syncSleepMode=" + syncSleepMode
                                    + ", timestamp=" + timestamp);
                            if (startNow) {
                                createAlarmFromConfig();
                            }
                        }
                    });
            XposedBridge.log(TAG + "Direct sleep-state hook installed");
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + "Unable to install sleep-state hook");
            XposedBridge.log(throwable);
        }
    }

    private static void createAlarmFromConfig() {
        long now = SystemClock.elapsedRealtime();
        long previous = LAST_TRIGGER.get();
        if (now - previous < DUPLICATE_GUARD_MS
                || !LAST_TRIGGER.compareAndSet(previous, now)) {
            XposedBridge.log(TAG + "Duplicate sleep state ignored");
            return;
        }

        Context context = processContext;
        if (context == null) {
            XposedBridge.log(TAG + "Cannot create alarm: context unavailable");
            return;
        }

        try {
            if (!planEnabled) {
                XposedBridge.log(TAG + "Plan is disabled");
                return;
            }

            int durationMinutes = planHours * 60 + planMinutes;

            Intent bridge = new Intent();
            bridge.setClassName(
                    Config.MODULE_PACKAGE,
                    Config.MODULE_PACKAGE + ".AlarmBridgeReceiver");
            bridge.putExtra(
                    AlarmBridgeReceiver.EXTRA_DURATION_MINUTES,
                    durationMinutes);
            bridge.putExtra(AlarmBridgeReceiver.EXTRA_LABEL, planLabel);
            bridge.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            context.sendBroadcast(bridge);
            XposedBridge.log(TAG
                    + "Alarm request sent to module bridge: duration="
                    + durationMinutes + "min");
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + "Failed to create one-time alarm");
            XposedBridge.log(throwable);
        }
    }
}
