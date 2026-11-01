package com.visorcraft.hy450control;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SleepReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        try {
            SleepTimer.sleepNow(context);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }
}
