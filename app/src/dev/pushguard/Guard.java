package dev.pushguard;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Keeps Google's push stack on HyperOS CN's freezer exemption list.
 *
 * HyperOS CN hard-codes com.google.android.gms onto the Greeze freezer's
 * system blacklist (ListAppsManager, removed only when ro.miui.region != CN).
 * The only exemption the freezer checks that a non-system app can influence is
 * Settings.System "MILLET_NO_RESTRICT_APP". PowerKeeper rebuilds that list from
 * its own battery-settings table on every package change, and Google Play
 * services never has a row there, so it gets dropped. This class puts it back.
 */
final class Guard {
    static final String TAG = "PushGuard";
    static final String KEY = "MILLET_NO_RESTRICT_APP";
    static final String[] DEFAULT_TARGETS = {
            "com.google.android.gms",
            "com.google.android.gsf",
    };
    private static final String PREFS = "pushguard";

    private Guard() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Packages that must stay on the list: Google push stack, ourselves, and user extras. */
    static Set<String> targets(Context c) {
        Set<String> t = new LinkedHashSet<String>();
        for (String p : DEFAULT_TARGETS) {
            t.add(p);
        }
        t.add(c.getPackageName());
        t.addAll(prefs(c).getStringSet("extra", new LinkedHashSet<String>()));
        return t;
    }

    static Set<String> extras(Context c) {
        return new LinkedHashSet<String>(prefs(c).getStringSet("extra", new LinkedHashSet<String>()));
    }

    static void setExtras(Context c, Set<String> extras) {
        prefs(c).edit().putStringSet("extra", new LinkedHashSet<String>(extras)).apply();
    }

    static Set<String> currentList(Context c) {
        return parse(Settings.System.getString(c.getContentResolver(), KEY));
    }

    /** Adds any missing target to the list. Returns a short human-readable result. */
    static synchronized String ensure(Context c, String why) {
        ContentResolver r = c.getContentResolver();
        Set<String> list = parse(Settings.System.getString(r, KEY));
        boolean changed = false;
        for (String p : targets(c)) {
            if (list.add(p)) {
                changed = true;
            }
        }

        String result;
        if (!changed) {
            result = "already present";
        } else {
            try {
                boolean ok = Settings.System.putString(r, KEY, join(list));
                result = ok ? "re-added" : "write returned false";
            } catch (RuntimeException e) {
                result = "write FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        Log.i(TAG, why + " -> " + result);
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor ed = p.edit()
                .putLong("last_time", System.currentTimeMillis())
                .putString("last_why", why)
                .putString("last_result", result);
        if ("re-added".equals(result)) {
            ed.putInt("fix_count", p.getInt("fix_count", 0) + 1);
        }
        ed.apply();
        return result;
    }

    static Set<String> parse(String value) {
        Set<String> out = new LinkedHashSet<String>();
        if (value == null) {
            return out;
        }
        for (String s : value.split(",")) {
            String t = s.trim();
            if (t.length() > 0) {
                out.add(t);
            }
        }
        return out;
    }

    static String join(Set<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        return sb.toString();
    }
}
