package com.tvmods.appstore;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static MainActivity instance;
    private WebView web;
    private SideloadEngine engine;

    public static MainActivity get() { return instance; }
    public boolean engineReady() { return engine != null; }
    public SideloadEngine getEngine() { return engine; }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);
        engine = new SideloadEngine(this);
        web = findViewById(R.id.web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "TvMods");
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.evaluateJavascript("window.onPermsChanged && window.onPermsChanged(" +
                    (canInstall() ? "true" : "false") + ")", null);
        }
    }

    boolean canInstall() {
        return getPackageManager().canRequestPackageInstalls();
    }

    public void notifyUi(String json) {
        runOnUiThread(() -> web.evaluateJavascript(
                "window.onStoreEvent && window.onStoreEvent(" + json + ")", null));
    }

    public class Bridge {
        @JavascriptInterface
        public boolean hasInstallPermission() {
            return canInstall();
        }

        @JavascriptInterface
        public void requestInstallPermission() {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }

        @JavascriptInterface
        public void installSpm() {
            engine.installSpm();
        }
    }
}
