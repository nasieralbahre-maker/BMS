package com.bluemark.bmsmobile;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

/**
 * BMS Mobile — WebView wrapper for the Blue Mark Sweets cashier system.
 * Loads the local web app from assets/www and exposes two native bridges that
 * the web code already expects: AndroidBio (fingerprint / face unlock) and
 * AndroidNotify (local notifications).
 */
public class MainActivity extends AppCompatActivity {

    private WebView web;
    private PermissionRequest pendingCameraRequest;
    private static final int REQ_CAMERA = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        // منع سحب الصفحة أفقياً (إيقاف ارتداد الحافة الأفقي)
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHorizontalScrollBarEnabled(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false); // لتشغيل كاميرا ماسح QR مباشرة
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    for (String res : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
                            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_GRANTED) {
                                request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                            } else {
                                pendingCameraRequest = request;
                                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                            }
                            return;
                        }
                    }
                    request.grant(request.getResources());
                });
            }
        });

        web.addJavascriptInterface(new BioBridge(), "AndroidBio");
        web.addJavascriptInterface(new NotifyBridge(), "AndroidNotify");

        createNotificationChannel();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/www/index.html");
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_CAMERA && pendingCameraRequest != null) {
            boolean granted = r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                pendingCameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else {
                pendingCameraRequest.deny();
            }
            pendingCameraRequest = null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    // ===================== جسر البصمة الأصلية =====================
    private class BioBridge {
        @JavascriptInterface
        public boolean isAvailable() {
            BiometricManager bm = BiometricManager.from(MainActivity.this);
            int can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
            return can == BiometricManager.BIOMETRIC_SUCCESS;
        }

        @JavascriptInterface
        public void authenticate(final String reqId, final String title, final String subtitle) {
            runOnUiThread(() -> {
                Executor exec = ContextCompat.getMainExecutor(MainActivity.this);
                BiometricPrompt prompt = new BiometricPrompt(MainActivity.this, exec,
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                                deliver(reqId, true, "ok");
                            }

                            @Override
                            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                                deliver(reqId, false, String.valueOf(errString));
                            }

                            @Override
                            public void onAuthenticationFailed() {
                                // محاولة غير مطابقة — النظام يسمح بإعادة المحاولة
                            }
                        });

                BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title == null || title.isEmpty() ? "تأكيد الهوية" : title)
                        .setSubtitle(subtitle == null ? "" : subtitle)
                        .setNegativeButtonText("إلغاء")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .build();
                try {
                    prompt.authenticate(info);
                } catch (Exception e) {
                    deliver(reqId, false, "exception");
                }
            });
        }

        private void deliver(final String reqId, final boolean ok, final String msg) {
            final String js = "window.__bioNativeResult && window.__bioNativeResult("
                    + jsStr(reqId) + "," + (ok ? "true" : "false") + "," + jsStr(msg) + ");";
            web.post(() -> web.evaluateJavascript(js, null));
        }
    }

    // ===================== جسر الإشعارات =====================
    private static final String CHANNEL_ID = "bms_default";

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "BMS Mobile",
                    NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private class NotifyBridge {
        @JavascriptInterface
        public void notify(String title, String text, String id) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            int nid;
            try { nid = (id != null) ? id.hashCode() : (int) System.currentTimeMillis(); }
            catch (Exception e) { nid = (int) System.currentTimeMillis(); }
            NotificationCompat.Builder b = new NotificationCompat.Builder(MainActivity.this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title == null ? "BMS" : title)
                    .setContentText(text == null ? "" : text)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(nid, b.build());
        }
    }

    // تغليف نص آمن لـ JS
    private static String jsStr(String v) {
        if (v == null) return "''";
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '<': sb.append("\\u003c"); break;
                case '>': sb.append("\\u003e"); break;
                default: sb.append(c);
            }
        }
        sb.append("'");
        return sb.toString();
    }
}
