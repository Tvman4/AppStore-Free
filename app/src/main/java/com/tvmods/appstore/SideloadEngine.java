package com.tvmods.appstore;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SideloadEngine {
    public static final String PATCHER =
            "https://github.com/Tvman4/apks/releases/download/spm/com.SPM.GorillaPatcher-Signed.apk";
    public static final String GAME =
            "https://github.com/Tvman4/apks/releases/download/spm/GorillaTag-SPM-09-04-2026-v4.1.2.apk";

    private final MainActivity activity;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<File> queue = new ArrayList<>();
    private boolean busy;

    public SideloadEngine(MainActivity activity) {
        this.activity = activity;
    }

    public void installSpm() {
        if (busy) {
            activity.notifyUi("{\"type\":\"busy\"}");
            return;
        }
        if (!activity.canInstall()) {
            activity.notifyUi("{\"type\":\"need_perms\"}");
            activity.runOnUiThread(() -> activity.new Bridge().requestInstallPermission());
            return;
        }
        busy = true;
        io.execute(() -> {
            try {
                File dir = new File(activity.getCacheDir(), "apks");
                if (!dir.exists()) dir.mkdirs();
                File patcher = new File(dir, "patcher.apk");
                File game = new File(dir, "game.apk");
                download(PATCHER, patcher, "SPM Patcher");
                download(GAME, game, "SPM Gorilla Tag");
                queue.clear();
                queue.add(patcher);
                queue.add(game);
                main.post(this::commitNext);
            } catch (Exception e) {
                busy = false;
                activity.notifyUi("{\"type\":\"error\",\"msg\":\"" + esc(e.getMessage()) + "\"}");
            }
        });
    }

    private void download(String url, File dest, String label) throws Exception {
        activity.notifyUi("{\"type\":\"download\",\"name\":\"" + esc(label) + "\",\"pct\":0}");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.connect();
        int code = c.getResponseCode();
        if (code >= 400) throw new Exception("HTTP " + code + " " + label);
        long total = c.getContentLengthLong();
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            long got = 0;
            int n;
            int lastPct = -1;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                got += n;
                if (total > 0) {
                    int pct = (int) (got * 100 / total);
                    if (pct != lastPct) {
                        lastPct = pct;
                        activity.notifyUi("{\"type\":\"download\",\"name\":\"" + esc(label) + "\",\"pct\":" + pct + "}");
                    }
                }
            }
        } finally {
            c.disconnect();
        }
        activity.notifyUi("{\"type\":\"download\",\"name\":\"" + esc(label) + "\",\"pct\":100}");
    }

    void onInstallFinished(int status, String message) {
        if (status == PackageInstaller.STATUS_SUCCESS) {
            activity.notifyUi("{\"type\":\"installed\"}");
            commitNext();
        } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            activity.notifyUi("{\"type\":\"confirm\"}");
        } else {
            busy = false;
            activity.notifyUi("{\"type\":\"error\",\"msg\":\"" + esc(message) + "\"}");
        }
    }

    private void commitNext() {
        if (queue.isEmpty()) {
            busy = false;
            activity.notifyUi("{\"type\":\"done\"}");
            return;
        }
        File apk = queue.remove(0);
        try {
            commitSession(apk);
        } catch (Exception e) {
            busy = false;
            activity.notifyUi("{\"type\":\"error\",\"msg\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private void commitSession(File apk) throws Exception {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        try (FileInputStream in = new FileInputStream(apk);
             java.io.OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            session.fsync(out);
        }
        Intent callback = new Intent(activity, InstallResultReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                activity, sessionId, callback,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session.commit(pi.getIntentSender());
        session.close();
        activity.notifyUi("{\"type\":\"installing\"}");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
