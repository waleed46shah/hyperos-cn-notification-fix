package dev.pushguard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.Set;

/**
 * Status screen. Also accepts extras so extra packages can be managed over ADB:
 *   adb shell am start -n dev.pushguard/.MainActivity --es add com.google.android.gm
 *   adb shell am start -n dev.pushguard/.MainActivity --es remove com.google.android.gm
 */
public class MainActivity extends Activity {
    private TextView text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        text = new TextView(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        text.setTextSize(15);
        text.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        setContentView(scroll);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        Set<String> extras = Guard.extras(this);
        String add = intent != null ? intent.getStringExtra("add") : null;
        String remove = intent != null ? intent.getStringExtra("remove") : null;
        if (add != null) {
            extras.addAll(Guard.parse(add));
        }
        if (remove != null) {
            extras.removeAll(Guard.parse(remove));
        }
        if (add != null || remove != null) {
            Guard.setExtras(this, extras);
        }

        String result = Guard.ensure(this, "app opened");
        GuardService.start(this, "app opened");
        render(result);
    }

    private void render(String result) {
        Set<String> list = Guard.currentList(this);
        StringBuilder sb = new StringBuilder();
        sb.append("PushGuard\n\n");
        sb.append("Write test: ").append(result).append("\n\n");
        sb.append("Protected packages:\n");
        for (String p : Guard.targets(this)) {
            sb.append(list.contains(p) ? "  [on list]  " : "  [MISSING]  ").append(p).append('\n');
        }
        long t = Guard.prefs(this).getLong("last_time", 0);
        sb.append("\nLast check: ")
                .append(t == 0 ? "never" : DateFormat.getDateTimeInstance().format(new Date(t)))
                .append(" (").append(Guard.prefs(this).getString("last_why", "-")).append(")\n");
        sb.append("Times repaired: ").append(Guard.prefs(this).getInt("fix_count", 0)).append('\n');
        sb.append("\nFull list:\n").append(Guard.join(list));
        text.setText(sb.toString());
    }
}
