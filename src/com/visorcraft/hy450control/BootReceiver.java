package com.visorcraft.hy450control;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

public final class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String pkg = context.getSharedPreferences("automation", Context.MODE_PRIVATE)
                    .getString("startup_package", "");
            if (pkg.isEmpty()) return;
            try {
                PackageManager packages = context.getPackageManager();
                Intent launch = packages.getLaunchIntentForPackage(pkg);
                if (launch != null) context.startActivity(launch);
            } catch (Exception ignored) {
            }
        }, 20_000);
    }
}
