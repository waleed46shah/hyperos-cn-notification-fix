package dev.pushguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

/**
 * Watches MILLET_NO_RESTRICT_APP and repairs it the moment PowerKeeper rewrites it.
 * Runs as a minimal foreground service so the process stays alive to observe changes.
 */
public class GuardService extends Service {
    private static final String CHANNEL = "guard";
    private ContentObserver observer;

    static void start(Context c, String why) {
        Intent i = new Intent(c, GuardService.class).putExtra("why", why);
        if (Build.VERSION.SDK_INT >= 26) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                Guard.ensure(GuardService.this, "list changed");
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Guard.KEY), false, observer);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String why = intent != null ? intent.getStringExtra("why") : null;
        Guard.ensure(this, why != null ? why : "service restart");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (observer != null) {
            getContentResolver().unregisterContentObserver(observer);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void goForeground() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Background guard", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this).setPriority(Notification.PRIORITY_MIN);
        }
        b.setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("PushGuard is keeping notifications alive")
                .setOngoing(true);
        startForeground(1, b.build());
    }
}
