package dev.pushguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Restarts the guard after boot, after PushGuard updates, and after any app is
 * installed, updated or removed (the events that make PowerKeeper rebuild the list).
 */
public class Receiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        GuardService.start(context, action != null ? action : "broadcast");
    }
}
