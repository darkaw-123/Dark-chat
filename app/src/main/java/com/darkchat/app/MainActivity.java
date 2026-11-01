package com.darkchat.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePicker;
    private PermissionRequest pendingWebPermission;
    private static final int FILE_REQUEST = 301;
    private static final int AV_REQUEST = 302;
    private volatile boolean pageLoaded = false;
    static MainActivity active;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        active = this;
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 300);
        webView = new WebView(this);
        setContentView(webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                sendSavedToken();
                FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(MainActivity.this::deliverToken);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!"appassets.androidplatform.net".equals(request.getOrigin().getHost())) {
                        request.deny(); return;
                    }
                    boolean audio = false, video = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) audio = true;
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) video = true;
                    }
                    if (!audio && !video) { request.deny(); return; }
                    boolean needA = audio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;
                    boolean needV = video && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
                    if (needA || needV) {
                        pendingWebPermission = request;
                        if (needA && needV) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, AV_REQUEST);
                        else requestPermissions(new String[]{needA ? Manifest.permission.RECORD_AUDIO : Manifest.permission.CAMERA}, AV_REQUEST);
                    } else request.grant(request.getResources());
                });
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePicker != null) filePicker.onReceiveValue(null);
                filePicker = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQUEST);
                    return true;
                } catch (Exception e) {
                    filePicker = null;
                    cb.onReceiveValue(null);
                    return false;
                }
            }
        });
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("default", "DARK CHAT Messages and Calls", NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(req, permissions, grants);
        if (req == AV_REQUEST && pendingWebPermission != null) {
            PermissionRequest request = pendingWebPermission;
            pendingWebPermission = null;
            boolean granted = grants.length > 0;
            for (int g : grants) if (g != PackageManager.PERMISSION_GRANTED) granted = false;
            if (granted) request.grant(request.getResources());
            else request.deny();
        }
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == FILE_REQUEST && filePicker != null) {
            filePicker.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            filePicker = null;
        }
    }

    void deliverToken(String token) {
        if (token == null || token.isEmpty()) return;
        getSharedPreferences("fcm", MODE_PRIVATE).edit().putString("token", token).apply();
        if (pageLoaded && webView != null) {
            String script = "window.dispatchEvent(new CustomEvent('darkchat-fcm-token',{detail:{token:" + JSONObject.quote(token) + "}}));";
            runOnUiThread(() -> webView.evaluateJavascript(script, null));
        }
    }

    private void sendSavedToken() {
        String token = getSharedPreferences("fcm", MODE_PRIVATE).getString("token", null);
        if (token != null) deliverToken(token);
    }

    @Override protected void onResume() {
        super.onResume();
        active = this;
        if (pageLoaded) sendSavedToken();
    }

    @Override protected void onPause() {
        if (active == this) active = null;
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (active == this) active = null;
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
