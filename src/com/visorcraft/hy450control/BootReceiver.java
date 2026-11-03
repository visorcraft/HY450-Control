package com.visorcraft.hy450control;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final int JOB_ID = 1001;
    private static final long DELAY_MILLIS = 20_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(
                    Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                Log.e("BootLaunch", "Job scheduler unavailable");
                return;
            }
            JobInfo job = new JobInfo.Builder(JOB_ID,
                    new ComponentName(context, BootLaunchService.class))
                    .setMinimumLatency(DELAY_MILLIS)
                    .build();
            if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                Log.e("BootLaunch", "Startup job was rejected");
            }
        } catch (Exception error) {
            Log.e("BootLaunch", "Startup scheduling failed: " + Errors.describe(error), error);
        }
    }
}
