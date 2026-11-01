package com.visorcraft.hy450control;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;

final class SleepTimer {
    static void set(Context context, int minutes) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent action = action(context);
        alarms.cancel(action);
        if (minutes > 0) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + minutes * 60_000L, action);
        }
    }

    static void sleepNow(Context context) throws Exception {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        power.getClass().getMethod("goToSleep", long.class)
                .invoke(power, SystemClock.uptimeMillis());
    }

    private static PendingIntent action(Context context) {
        return PendingIntent.getBroadcast(context, 0,
                new Intent(context, SleepReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
