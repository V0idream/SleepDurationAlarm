package app.sleep.durationalarm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.Field;
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
    private static final String CLOCK_PACKAGE = "com.coloros.alarmclock";
    private static final String CLOCK_API_ACTIVITY =
            "com.oplus.alarmclock.cts.HandleApiActivity";
    private static final String CLOCK_MAIN_ACTIVITY =
            "com.oplus.alarmclock.AlarmClock";
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
        if ("android".equals(param.packageName)
                && "android".equals(param.processName)) {
            hookColorOsLockScreenInterceptor(param.classLoader);
            return;
        }
        if (CLOCK_PACKAGE.equals(param.packageName)
                && CLOCK_PACKAGE.equals(param.processName)) {
            hookClockActivityBridge(param.classLoader);
            return;
        }
        if (!TARGET_PACKAGE.equals(param.packageName)
                || !TARGET_PROCESS.equals(param.processName)) {
            return;
        }

        hookApplicationContext(param.classLoader);
        hookSleepState(param.classLoader);
        XposedBridge.log(TAG + "Loaded in " + param.processName);
    }

    private static void hookColorOsLockScreenInterceptor(
            ClassLoader classLoader) {
        try {
            Class<?> activityRecord = Class.forName(
                    "com.android.server.wm.ActivityRecord",
                    false,
                    classLoader);
            Class<?> keyguardController = Class.forName(
                    "com.android.server.wm.KeyguardController",
                    false,
                    classLoader);

            XposedHelpers.findAndHookMethod(
                    "com.android.server.wm.OplusInterceptLockScreenWindow",
                    classLoader,
                    "keyguardFlagCheck",
                    activityRecord,
                    keyguardController,
                    lockScreenBypassHook("keyguardFlagCheck"));
            XposedHelpers.findAndHookMethod(
                    "com.android.server.wm.OplusInterceptLockScreenWindow",
                    classLoader,
                    "execInterceptWindow",
                    Context.class,
                    activityRecord,
                    boolean.class,
                    lockScreenBypassHook("execInterceptWindow"));
            XposedBridge.log(TAG
                    + "ColorOS lock-screen interceptor hooks installed");
        } catch (Throwable throwable) {
            XposedBridge.log(TAG
                    + "ColorOS lock-screen interceptor hook installation failed");
            XposedBridge.log(throwable);
        }
    }

    private static XC_MethodHook lockScreenBypassHook(String method) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object record = param.args.length > 1
                        && param.args[1] != null
                        && param.args[1].getClass().getName()
                                .endsWith("ActivityRecord")
                        ? param.args[1]
                        : param.args[0];
                if (!isClockActivity(record)) {
                    return;
                }
                param.setResult(false);
                XposedBridge.log(TAG
                        + "ColorOS lock-screen interception bypassed: "
                        + method);
            }
        };
    }

    private static boolean isClockActivity(Object activityRecord) {
        if (activityRecord == null) {
            return false;
        }
        String record;
        try {
            Field field = findField(
                    activityRecord.getClass(), "mActivityComponent");
            record = String.valueOf(field.get(activityRecord));
        } catch (Throwable ignored) {
            record = String.valueOf(activityRecord);
        }
        return record.contains(CLOCK_PACKAGE);
    }

    private static Field findField(Class<?> type, String name)
            throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void hookClockActivityBridge(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "onCreate",
                    Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(
                                MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;
                            Intent intent = activity.getIntent();
                            if (intent == null
                                    || (!intent.getBooleanExtra(
                                            Config.EXTRA_SLEEP_ALARM, false)
                                    && !intent.getBooleanExtra(
                                            Config.EXTRA_CLOCK_BRIDGE, false))) {
                                return;
                            }
                            activity.setShowWhenLocked(true);
                            activity.setTurnScreenOn(true);
                            XposedBridge.log(TAG
                                    + "Clock lock-screen bridge enabled");
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(
                                MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;
                            if (!CLOCK_MAIN_ACTIVITY.equals(
                                    activity.getClass().getName())) {
                                return;
                            }
                            Intent intent = activity.getIntent();
                            if (intent == null
                                    || !intent.getBooleanExtra(
                                            Config.EXTRA_CLOCK_BRIDGE,
                                            false)) {
                                return;
                            }
                            intent.removeExtra(Config.EXTRA_CLOCK_BRIDGE);
                            int durationMinutes = intent.getIntExtra(
                                    Config.EXTRA_DURATION_MINUTES, 0);
                            String label = intent.getStringExtra(
                                    Config.EXTRA_ALARM_LABEL);
                            try {
                                long target = AlarmScheduler.createOneTimeAlarm(
                                        activity, durationMinutes, label);
                                XposedBridge.log(TAG
                                        + "One-time alarm created from Clock foreground: "
                                        + AlarmScheduler.formatTime(target));
                                disablePlanAfterAlarmCreated(activity);
                                new Handler(Looper.getMainLooper()).postDelayed(
                                        () -> {
                                            activity.moveTaskToBack(true);
                                            if (!activity.isFinishing()) {
                                                activity.finish();
                                            }
                                            XposedBridge.log(TAG
                                                    + "Clock bridge finished");
                                        },
                                        1500L);
                            } catch (Throwable throwable) {
                                XposedBridge.log(TAG
                                        + "Clock foreground alarm creation failed");
                                XposedBridge.log(throwable);
                            }
                        }
                    });
            XposedBridge.log(TAG + "Clock activity bridge hooks installed");
        } catch (Throwable throwable) {
            XposedBridge.log(TAG
                    + "Clock activity bridge hook installation failed");
            XposedBridge.log(throwable);
        }
    }

    private static void disablePlanAfterAlarmCreated(Context context) {
        try {
            Intent disable = new Intent(Config.ACTION_DISABLE_PLAN);
            disable.setClassName(
                    Config.MODULE_PACKAGE,
                    Config.MODULE_PACKAGE + ".ConfigBridgeReceiver");
            disable.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            context.sendBroadcast(disable);
            XposedBridge.log(TAG
                    + "One-shot plan disabled after alarm creation");
        } catch (Throwable throwable) {
            XposedBridge.log(TAG
                    + "Unable to disable one-shot plan");
            XposedBridge.log(throwable);
        }
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

            Intent clock = new Intent(Intent.ACTION_MAIN);
            clock.setClassName(CLOCK_PACKAGE, CLOCK_MAIN_ACTIVITY);
            clock.putExtra(Config.EXTRA_CLOCK_BRIDGE, true);
            clock.putExtra(
                    Config.EXTRA_DURATION_MINUTES, durationMinutes);
            clock.putExtra(Config.EXTRA_ALARM_LABEL, planLabel);
            clock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(clock);
            XposedBridge.log(TAG
                    + "Clock foreground bridge launched: duration="
                    + durationMinutes + "min");
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + "Failed to create one-time alarm");
            XposedBridge.log(throwable);
        }
    }
}
