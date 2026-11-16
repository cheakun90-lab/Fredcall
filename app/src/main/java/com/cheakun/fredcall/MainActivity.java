package com.cheakun.fredcall;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private EditText nameEt, minEt, maxEt, gapEt;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(20));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Fred Call");
        title.setTextSize(28);
        root.addView(title);

        nameEt = field(root, "Caller name", "Fredbear", InputType.TYPE_CLASS_TEXT);
        minEt = field(root, "Min seconds between calls", "30", InputType.TYPE_CLASS_NUMBER);
        maxEt = field(root, "Max seconds between calls", "90", InputType.TYPE_CLASS_NUMBER);

        gapEt = field(root, "Pause between its lines (sec)", "5", InputType.TYPE_CLASS_NUMBER);

        button(root, "1. Allow overlay permission", v -> {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        });
        button(root, "2. Start (random calls)", v -> send("START"));
        button(root, "Call me at a random time (once)", v -> send("CALL_RANDOM"));
        button(root, "Call me now", v -> send("CALL_NOW"));
        button(root, "Stop", v -> send("STOP"));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);
    }

    private void send(String action) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow overlay permission first", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, OverlayService.class);
        i.setAction(action);
        i.putExtra("name", nameEt.getText().toString().trim());
        i.putExtra("min", parse(minEt, 30));
        i.putExtra("max", parse(maxEt, 90));
        i.putExtra("gap", parse(gapEt, 5));
        if (action.equals("STOP")) {
            stopService(new Intent(this, OverlayService.class));
        } else {
            startForegroundService(i);
        }
    }

    private int parse(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (Exception ex) { return def; }
    }

    private EditText field(LinearLayout root, String hint, String val, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(val);
        e.setInputType(type);
        root.addView(e, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private void button(LinearLayout root, String text, android.view.View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        root.addView(bt, lp);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
