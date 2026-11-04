package com.darkchat.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.ApplicationInfo;
import android.view.View;
import android.widget.LinearLayout;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private WebView webView;
    private AdView adView;
    private String pendingSeller;
    private ValueCallback<Uri[]> filePicker;
    private PermissionRequest pendingWebPermission;
    private static final int FILE_REQUEST = 301;
    private static final int AV_REQUEST = 302;
    private volatile boolean pageLoaded = false;
    static MainActivity active;
    private String pendingCallId;
    private String pendingCallAction;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        active = this;
        captureIncomingCallIntent(getIntent());
        captureSellerIntent(getIntent());
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 300);
        webView = new WebView(this);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        // Native Android AdMob banner: test ads on debug, publisher ads on signed release.
        adView = new AdView(this);
        final boolean debugBuild = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        adView.setAdUnitId(debugBuild
            ? "ca-app-pub-3940256099942544/9214589741"
            : "ca-app-pub-7138311889196067/3355094444");
        adView.setAdSize(AdSize.BANNER);
        adView.setVisibility(View.GONE);
        adView.setAdListener(new AdListener() {
            @Override public void onAdLoaded() { adView.setVisibility(View.VISIBLE); }
            @Override public void onAdFailedToLoad(LoadAdError error) { adView.setVisibility(View.GONE); }
        });
        shell.addView(adView, new LinearLayout.LayoutParams(-1, -2));
        setContentView(shell);
        new Thread(() -> MobileAds.initialize(this, status ->
            runOnUiThread(() -> {
                if (adView != null) adView.loadAd(new AdRequest.Builder().build());
            }))).start();
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void refreshToken() { runOnUiThread(() -> checkFirebaseToken()); }
            @JavascriptInterface public void requestPermission() { runOnUiThread(() -> requestPushPermission()); }
        }, "DarkChatDevice");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                deliverPendingCallIntent();
                deliverPendingSeller();
                sendSavedToken();
                checkFirebaseToken();
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
        String initialUrl = "https://appassets.androidplatform.net/assets/index.html";
        if (pendingSeller != null) {
            initialUrl += "?chat=" + pendingSeller;
            pendingSeller = null;
        }
        webView.loadUrl(initialUrl);
    }


    private void captureSellerIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri link = intent.getData();
        String name = null;
        if ("darkchat".equalsIgnoreCase(link.getScheme()) && "seller".equalsIgnoreCase(link.getHost())) {
            name = link.getLastPathSegment();
        } else if ("https".equalsIgnoreCase(link.getScheme())
                && "darkchatdownload.netlify.app".equalsIgnoreCase(link.getHost())) {
            name = link.getQueryParameter("chat");
            if ((name == null || name.isEmpty()) && link.getPath() != null
                    && link.getPath().matches("/chat/[a-zA-Z0-9_.]{3,32}/?")) {
                String[] path = link.getPath().split("/");
                if (path.length >= 3) name = path[2];
            }
        }
        if (name != null && name.matches("[a-zA-Z0-9_.]{3,32}")) {
            pendingSeller = name.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private void deliverPendingSeller() {
        if (!pageLoaded || webView == null || pendingSeller == null) return;
        final String username = pendingSeller;
        pendingSeller = null;
        final String dest = "https://darkchatdownload.netlify.app/?chat=" + username;
        runOnUiThread(() -> webView.evaluateJavascript(
            "window.DCChatReceiveDeepLink && window.DCChatReceiveDeepLink(" + JSONObject.quote(dest) + ");",
            null));
    }

    private void captureIncomingCallIntent(Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra("call_invite_id");
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) return;
        String action = intent.getStringExtra("call_action");
        if (!"answer".equals(action) && !"decline".equals(action)) action = "open";
        pendingCallId = id;
        pendingCallAction = action;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(DarkChatMessagingService.CALL_TAG, id.hashCode());
    }

    private void deliverPendingCallIntent() {
        if (!pageLoaded || webView == null || pendingCallId == null) return;
        String id = pendingCallId;
        String action = pendingCallAction;
        pendingCallId = null;
        pendingCallAction = null;
        String script = "window.dispatchEvent(new CustomEvent('darkchat-open-call',{detail:{id:"
            + JSONObject.quote(id) + ",action:" + JSONObject.quote(action) + "}}));";
        runOnUiThread(() -> webView.evaluateJavascript(script, null));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureIncomingCallIntent(intent);
        captureSellerIntent(intent);
        deliverPendingCallIntent();
        deliverPendingSeller();
    }

    private void sendPushStatus(String status, String message) {
        if (!pageLoaded || webView == null) return;
        final String script = "window.dispatchEvent(new CustomEvent('darkchat-native-push-status',{detail:{status:"
            + JSONObject.quote(status) + ",message:" + JSONObject.quote(message) + "}}));";
        runOnUiThread(() -> webView.evaluateJavascript(script, null));
    }

    private void checkFirebaseToken() {
        if (webView == null) return;
        sendPushStatus("checking", "Checking Firebase...");
        int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            sendPushStatus("missing_play_services", "Google Play services unavailable (code " + result + ")");
            return;
        }
        FirebaseMessaging.getInstance().getToken()
            .addOnSuccessListener(this::deliverToken)
            .addOnFailureListener(err -> sendPushStatus("token_error", err.getMessage() == null ? "Firebase unavailable" : err.getMessage()));
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            sendPushStatus("notification_blocked", "Android notification permission is not granted");
    }

    private void requestPushPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 300);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("default", "DARK CHAT Messages and Calls", NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            DarkChatMessagingService.createCallChannel(this);
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(req, permissions, grants);
        if (req == 300) {
            if (grants.length == 0 || grants[0] != PackageManager.PERMISSION_GRANTED)
                sendPushStatus("notification_blocked", "User denied Android notification permission");
            else checkFirebaseToken();
        }
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
        if (adView != null) adView.resume();
        active = this;
        if (pageLoaded) { sendSavedToken(); checkFirebaseToken(); }
    }

    @Override protected void onPause() {
        if (adView != null) adView.pause();
        if (active == this) active = null;
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (active == this) active = null;
        if (adView != null) { adView.destroy(); adView = null; }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
