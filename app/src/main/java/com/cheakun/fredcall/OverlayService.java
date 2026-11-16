package com.cheakun.fredcall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.Locale;
import java.util.Random;

public class OverlayService extends Service {

    private static final String CH = "fredcall";

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    private WindowManager wm;
    private View banner;
    private TextView sub;
    private View acceptBtn;
    private MediaPlayer mp;
    private Ringtone ring;
    private Vibrator vib;

    private String callerName = "Fredbear";
    private int minS = 30, maxS = 90;
    private boolean auto = false;
    private boolean accepted = false;
    private boolean oneShot = false;
    private int gapSec = 5;
    private static final int LINES = 1;
    private int seg = 0, durMs = 0;
    private int seconds = 0;

    private final Runnable nextRun = () -> {
        showCall();
        if (banner == null) scheduleNext();
    };
    private final Runnable missed = this::endCall;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            seconds++;
            if (sub != null) {
                sub.setText(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60));
            }
            h.postDelayed(this, 1000);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startFg();
        String action = intent != null ? intent.getAction() : null;

        if ("STOP".equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null) {
            String n = intent.getStringExtra("name");
            if (n != null && !n.isEmpty()) callerName = n;
            minS = intent.getIntExtra("min", minS);
            maxS = intent.getIntExtra("max", maxS);
            gapSec = Math.max(1, intent.getIntExtra("gap", gapSec));
        }
        if ("CALL_NOW".equals(action)) {
            oneShot = true; auto = false;
            showCall();
        } else if ("CALL_RANDOM".equals(action)) {
            oneShot = true; auto = false;
            scheduleNext();
        } else {
            oneShot = false;
            auto = true;
            scheduleNext();
        }
        return START_STICKY;
    }

    private void startFg() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "Fred Call", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CH)
                .setContentTitle("Fred Call is running")
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private void scheduleNext() {
        h.removeCallbacks(nextRun);
        int lo = Math.max(5, minS);
        int hi = Math.max(lo, maxS);
        long delay = (lo + rnd.nextInt(hi - lo + 1)) * 1000L;
        h.postDelayed(nextRun, delay);
    }

    private void showCall() {
        if (banner != null) return;
        if (!Settings.canDrawOverlays(this)) return;

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        accepted = false;
        seconds = 0;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0303033);
        bg.setCornerRadius(dp(30));
        root.setBackground(bg);

        // avatar: assets/avatar.png if present, else a plain yellow circle
        ImageView av = new ImageView(this);
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(0xFFF5C518);
        av.setBackground(avBg);
        av.setClipToOutline(true);
        av.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bmp = loadAvatar();
        if (bmp != null) av.setImageBitmap(bmp);
        root.addView(av, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(callerName);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(17);
        sub = new TextView(this);
        sub.setText("incoming call...");
        sub.setTextColor(0xFFBBBBBB);
        sub.setTextSize(12);
        col.addView(name);
        col.addView(sub);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = dp(12);
        root.addView(col, cp);

        TextView dec = circleButton("✕", 0xFFE53935);
        dec.setOnClickListener(v -> endCall());
        acceptBtn = circleButton("✓", 0xFF34C759);
        acceptBtn.setOnClickListener(v -> acceptCall());
        root.addView(dec, btnLp());
        root.addView(acceptBtn, btnLp());

        int width = getResources().getDisplayMetrics().widthPixels - dp(24);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(36);

        banner = root;
        root.setTranslationY(-dp(200));
        wm.addView(root, lp);
        root.animate().translationY(0).setDuration(300).start();

        startRing();
        h.postDelayed(missed, 25000);
    }

    private void acceptCall() {
        if (accepted) return;
        accepted = true;
        stopRing();
        h.removeCallbacks(missed);
        if (acceptBtn != null) acceptBtn.setVisibility(View.GONE);

        String f = findAudio();
        if (f == null) {
            Toast.makeText(this, "Put call.mp3 in app/src/main/assets", Toast.LENGTH_LONG).show();
            endCall();
            return;
        }
        try {
            AssetFileDescriptor afd = getAssets().openFd(f);
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.prepare();
            durMs = mp.getDuration();
            seg = 0;
            if (sub != null) sub.setText("00:00");
            h.postDelayed(tick, 1000);
            h.postDelayed(playSeg, 700);   // short pause after you answer
        } catch (Exception e) {
            endCall();
        }
    }

    // The audio is one long clip. Play it as LINES short bursts with a pause
    // after each one so you can talk back before it speaks again.
    private final Runnable playSeg = new Runnable() {
        @Override public void run() {
            if (mp == null || banner == null) return;
            if (seg >= LINES) { endCall(); return; }
            int lead = 0, tail = 0;
            int usable = Math.max(1000, durMs - lead - tail);
            int chunk = usable / LINES;
            int start = lead + seg * chunk;
            int len = durMs;
            try {
                mp.setVolume(1f, 1f);
                mp.seekTo(start, MediaPlayer.SEEK_CLOSEST);
                mp.start();
            } catch (Exception e) { endCall(); return; }
            shake(true);
            h.postDelayed(fadeSeg, Math.max(100, len - 150));
            h.postDelayed(stopSeg, len);
        }
    };
    private final Runnable fadeSeg = () -> {
        try { if (mp != null) mp.setVolume(0.35f, 0.35f); } catch (Exception ignored) {}
    };
    private final Runnable stopSeg = () -> {
        try { if (mp != null && mp.isPlaying()) mp.pause(); } catch (Exception ignored) {}
        shake(false);
        seg++;
        if (seg >= LINES) h.postDelayed(this::endCall, 500);
        else h.postDelayed(playSeg, gapSec * 1000L);   // your turn to talk
    };

    // light shake while it talks
    private void shake(boolean on) {
        try {
            if (vib == null) vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib == null) return;
            if (!on) { vib.cancel(); return; }
            long[] t = {0, 40, 30};
            if (vib.hasAmplitudeControl()) {
                vib.vibrate(VibrationEffect.createWaveform(t, new int[]{0, 70, 0}, 0));
            } else {
                vib.vibrate(VibrationEffect.createWaveform(t, 0));
            }
        } catch (Exception ignored) {}
    }

    private void endCall() {
        h.removeCallbacks(missed);
        h.removeCallbacks(tick);
        h.removeCallbacks(playSeg);
        h.removeCallbacks(fadeSeg);
        h.removeCallbacks(stopSeg);
        stopRing();
        shake(false);
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) {}
            mp = null;
        }
        final View b = banner;
        banner = null;
        accepted = false;
        if (b != null) {
            b.animate().translationY(-dp(200)).setDuration(250).withEndAction(() -> {
                try { wm.removeView(b); } catch (Exception ignored) {}
            }).start();
        }
        if (auto) scheduleNext();
        else if (oneShot) h.postDelayed(this::stopSelf, 500);
    }

    private void startRing() {
        try {
            ring = RingtoneManager.getRingtone(this,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (ring != null) {
                if (Build.VERSION.SDK_INT >= 28) ring.setLooping(true);
                ring.play();
            }
            vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib != null) vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 800}, 0));
        } catch (Exception ignored) {}
    }

    private void stopRing() {
        try { if (ring != null) ring.stop(); } catch (Exception ignored) {}
        try { if (vib != null) vib.cancel(); } catch (Exception ignored) {}
        ring = null;
    }

    private String findAudio() {
        try {
            String[] list = getAssets().list("");
            if (list == null) return null;
            for (String s : list) if (s.toLowerCase(Locale.US).startsWith("call.")) return s;
            for (String s : list) {
                String l = s.toLowerCase(Locale.US);
                if (l.endsWith(".mp3") || l.endsWith(".ogg") || l.endsWith(".wav") || l.endsWith(".m4a")) return s;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Bitmap loadAvatar() {
        for (String n : new String[]{"avatar.png", "avatar.jpg"}) {
            try (InputStream is = getAssets().open(n)) {
                return BitmapFactory.decodeStream(is);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private TextView circleButton(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        t.setBackground(d);
        return t;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
        lp.leftMargin = dp(10);
        return lp;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        auto = false;
        h.removeCallbacksAndMessages(null);
        stopRing();
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; }
        if (banner != null && wm != null) {
            try { wm.removeView(banner); } catch (Exception ignored) {}
            banner = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
