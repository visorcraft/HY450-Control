package com.visorcraft.hy450control;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Intent;
import android.view.KeyEvent;

public final class RemoteAccessibilityService extends AccessibilityService {
    public boolean onKeyEvent(KeyEvent event) {
        android.util.Log.e("HY450Control", "Remote key=" + event.getKeyCode());
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int configured = getSharedPreferences("automation", MODE_PRIVATE)
                .getInt("remote_key", KeyEvent.KEYCODE_MENU);
        if (event.getKeyCode() == configured) {
            startService(new Intent(this, OverlayService.class));
            return true;
        }
        return false;
    }

    protected boolean onGesture(int gestureId) { return false; }

    public void onAccessibilityEvent(AccessibilityEvent event) {}

    public void onInterrupt() {}
}
