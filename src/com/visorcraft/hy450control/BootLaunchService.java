package com.visorcraft.hy450control;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

public final class BootLaunchService extends JobService {
    private static final String TAG = "BootLaunch";

    @Override
    public boolean onStartJob(JobParameters parameters) {
        String pkg = getSharedPreferences("automation", MODE_PRIVATE)
                .getString("startup_package", "");
        if (pkg.isEmpty()) return false;
        try {
            PackageManager packages = getPackageManager();
            Intent launch = packages.getLaunchIntentForPackage(pkg);
            if (launch == null) {
                Log.w(TAG, "No launch intent for " + pkg);
                return false;
            }
            startActivity(launch);
        } catch (Exception error) {
            Log.e(TAG, "Startup launch failed: " + Errors.describe(error), error);
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters parameters) {
        return false;
    }
}
