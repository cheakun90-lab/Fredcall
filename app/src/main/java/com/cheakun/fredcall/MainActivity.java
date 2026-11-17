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
import android.widget.CheckBox;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private EditText nameEt, minEt, maxEt, gapEt;
    private CheckBox noPauseCb;

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

        SharedPreferences prefs = getSharedPreferences("fred", MODE_PRIVATE);
        noPauseCb = new CheckBox(this);
        noPauseCb.setText("No waiting (play talking audio straight through)");
        noPauseCb.setChecked(prefs.getBoolean("nopause", false));
        noPauseCb.setOnCheckedChangeListener((cb, on) ->
                prefs.edit().putBoolean("nopause", on).apply());
        root.addView(noPauseCb, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        button(root, "Choose ring audio", v -> pick("audio/*", 10));
        button(root, "Choose talking audio", v -> pick("audio/*", 11));
        button(root, "Choose picture", v -> pick("image/*", 12));
        button(root, "Reset audio and picture to default", v -> {
            new File(getFilesDir(), "user_ring").delete();
            new File(getFilesDir(), "user_call").delete();
            new File(getFilesDir(), "user_avatar").delete();
            Toast.makeText(this, "Back to default", Toast.LENGTH_SHORT).show();
        });

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

    private void pick(String type, int code) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        startActivityForResult(i, code);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        String name = req == 10 ? "user_ring" : req == 11 ? "user_call" : req == 12 ? "user_avatar" : null;
        if (name == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData());
             OutputStream out = new FileOutputStream(new File(getFilesDir(), name))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not load that file", Toast.LENGTH_LONG).show();
        }
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
        i.putExtra("nopause", noPauseCb.isChecked());
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
