package com.darkchat.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;
import android.view.Gravity;
import android.view.WindowManager;
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
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.DisplayMetrics;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import androidx.webkit.WebViewAssetLoader;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private WebView webView;
    private AdView adView;
    private LinearLayout bannerBox;
    private TextView bannerStatus;
    private boolean bannerLoaded=false, bannerIsDebug=false, bannerReady=false, bannerForeground=false;
    private volatile String lastAdStatus="AdMob not initialized yet";
    private long lastBannerLoadMs=0L;
    private int bannerFailures=0;
    private final Handler bannerHandler=new Handler(Looper.getMainLooper());
    private final Runnable bannerRetry=() -> requestBanner(false);
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
        // Protect every Dark Chat screen, including account setup, profiles, groups,
        // messages and payment details, from standard Android screenshot and capture APIs.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                             WindowManager.LayoutParams.FLAG_SECURE);
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

        // Banner below the WebView. Debug uses Google's official test ad unit.
        bannerIsDebug=(getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        bannerBox=new LinearLayout(this);
        bannerBox.setOrientation(LinearLayout.VERTICAL);
        bannerBox.setGravity(Gravity.CENTER_HORIZONTAL);
        bannerBox.setBackgroundColor(0xFF0B0A14);
        bannerBox.setVisibility(View.GONE);
        bannerStatus=new TextView(this);
        bannerStatus.setTextSize(11);
        bannerStatus.setTextColor(0xFFD2C5ED);
        bannerStatus.setGravity(Gravity.CENTER);
        bannerStatus.setMaxLines(2);
        bannerStatus.setPadding(12,5,12,6);
        bannerStatus.setText("Checking AdMob test banner...");
        lastAdStatus="Initializing Google Mobile Ads SDK";
        bannerStatus.setVisibility(bannerIsDebug?View.VISIBLE:View.GONE);
        bannerStatus.setOnClickListener(v -> {
            bannerFailures=0;
            bannerHandler.removeCallbacks(bannerRetry);
            requestBanner(true);
        });
        adView=new AdView(this);
        // Adaptive anchored size fits each Android screen rather than forcing a fixed 320dp ad.
        DisplayMetrics metrics=getResources().getDisplayMetrics();
        int deviceWidthDp=(int)(metrics.widthPixels/metrics.density);
        int bannerWidthDp=Math.max(1,deviceWidthDp-16);
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this,bannerWidthDp));
        adView.setAdUnitId(bannerIsDebug
            ?"ca-app-pub-3940256099942544/6300978111"
            :"ca-app-pub-7138311889196067/3355094444");
        adView.setVisibility(View.GONE);
        adView.setAdListener(new AdListener() {
            @Override public void onAdLoaded() {
                bannerLoaded=true;
                bannerFailures=0;
                bannerHandler.removeCallbacks(bannerRetry);
                lastAdStatus=(bannerIsDebug?"Google TEST":"Real AdMob")+" banner loaded successfully";
                Log.i("DarkChatAds",lastAdStatus);
                if(adView!=null)adView.setVisibility(View.VISIBLE);
                if(bannerStatus!=null)bannerStatus.setVisibility(View.GONE);
                if(bannerBox!=null)bannerBox.setVisibility(View.VISIBLE);
            }
            @Override public void onAdFailedToLoad(LoadAdError error) {
                bannerLoaded=false;
                bannerFailures++;
                final String issue="AdMob error "+error.getCode()+": "+error.getMessage();
                lastAdStatus=issue+(error.getCode()==3
                    ?" (No fill: Google did not return an ad. Check AdMob app readiness, policy and availability.)":"");
                Log.w("DarkChatAds",issue+" | "+error.getDomain());
                // A banner must remain visible for AdMob's configured
                // automatic refresh to run after a no-fill response.
                if(adView!=null)adView.setVisibility(View.VISIBLE);
                if(bannerBox!=null)bannerBox.setVisibility(View.VISIBLE);
                if(bannerIsDebug&&bannerStatus!=null){
                    bannerStatus.setText(issue+" · Retrying automatically");
                    bannerStatus.setVisibility(View.VISIBLE);
                }
                // Bounded fallback when automatic refresh is disabled in AdMob.
                int seconds=Math.min(180,60*Math.min(bannerFailures,3));
                bannerHandler.removeCallbacks(bannerRetry);
                if(bannerForeground)bannerHandler.postDelayed(bannerRetry,seconds*1000L);
            }
            @Override public void onAdImpression() {
                lastAdStatus=(bannerIsDebug?"Test":"Real")+" banner impression recorded by Google";
            }
        });
        bannerBox.addView(adView,new LinearLayout.LayoutParams(-2,-2));
        bannerBox.addView(bannerStatus,new LinearLayout.LayoutParams(-1,-2));
        shell.addView(bannerBox,new LinearLayout.LayoutParams(-1,-2));
        setContentView(shell);
        new Thread(() -> MobileAds.initialize(this,status->
            runOnUiThread(() -> {
                bannerReady=true;
                requestBanner(true);
            }))).start();
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setTextZoom(100);
        webView.getSettings().setSupportZoom(false);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setGeolocationEnabled(false);
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= 26) webView.getSettings().setSafeBrowsingEnabled(true);
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void refreshToken() { runOnUiThread(() -> checkFirebaseToken()); }
            @JavascriptInterface public void requestPermission() { runOnUiThread(() -> requestPushPermission()); }
            @JavascriptInterface public void openNotificationSettings() { runOnUiThread(() -> openAppNotificationSettings()); }
            @JavascriptInterface public String adStatus() { return lastAdStatus; }
            @JavascriptInterface public void retryAd() { runOnUiThread(() -> requestBanner(true)); }
        }, "DarkChatDevice");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri=request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())) return false;
                String scheme=uri.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); } catch (Exception ignored) { }
                }
                return true; // Keep external sites away from the JavaScript bridge.
            }
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


    private void requestBanner(boolean force){
        if(!bannerReady||adView==null||isFinishing()||!bannerForeground)return;
        // Avoid overlapping an SDK-managed refresh, and keep retries bounded.
        if(adView.isLoading()){
            bannerHandler.removeCallbacks(bannerRetry);
            bannerHandler.postDelayed(bannerRetry,60000L);
            return;
        }
        long now=SystemClock.elapsedRealtime();
        if(!force&&lastBannerLoadMs>0L&&now-lastBannerLoadMs<60000L){
            bannerHandler.removeCallbacks(bannerRetry);
            bannerHandler.postDelayed(bannerRetry,60000L-(now-lastBannerLoadMs));
            return;
        }
        lastBannerLoadMs=now;
        lastAdStatus=(bannerIsDebug?"Google TEST":"Real AdMob")+" banner request sent; waiting for Google";
        bannerHandler.removeCallbacks(bannerRetry);
        if(bannerIsDebug&&bannerStatus!=null){
            bannerStatus.setText("Loading Google test banner...");
            bannerStatus.setVisibility(View.VISIBLE);
        }
        if(bannerBox!=null)bannerBox.setVisibility(View.VISIBLE);
        adView.setVisibility(View.VISIBLE);
        try{
            adView.loadAd(new AdRequest.Builder().build());
        }catch(Exception e){
            bannerLoaded=false;
            lastAdStatus="AdMob request exception: "+e.getClass().getSimpleName()+": "+e.getMessage();
            Log.e("DarkChatAds","Banner request error",e);
            if(bannerIsDebug&&bannerStatus!=null){
                bannerStatus.setText("Ad request error: "+e.getClass().getSimpleName()+" · Tap to retry");
                bannerStatus.setVisibility(View.VISIBLE);
            }
            if(bannerForeground)bannerHandler.postDelayed(bannerRetry,60000L);
        }
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


    private boolean isAppNotificationsEnabled() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm == null || nm.areNotificationsEnabled();
    }

    private void openAppNotificationSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Exception ex) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
            try { startActivity(fallback); } catch (Exception ignored) {}
        }
    }

    private void updateNotificationPermissionStatus() {
        if (!isAppNotificationsEnabled()) {
            sendPushStatus("notification_blocked",
                "Android notifications are disabled. Open app notification settings to allow them.");
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel msg = nm.getNotificationChannel("default");
            NotificationChannel calls = nm.getNotificationChannel(DarkChatMessagingService.CALL_CHANNEL);
            if ((msg != null && msg.getImportance() == NotificationManager.IMPORTANCE_NONE) ||
                (calls != null && calls.getImportance() == NotificationManager.IMPORTANCE_NONE)) {
                sendPushStatus("notification_blocked",
                    "Messages or incoming calls channel is disabled in Android notification settings.");
                return;
            }
        }
        sendPushStatus("notification_enabled", "Android notifications allowed. Firebase delivery still depends on network.");
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
        updateNotificationPermissionStatus();
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
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PRIVATE);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            DarkChatMessagingService.createCallChannel(this);
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(req, permissions, grants);
        if (req == 300) {
            updateNotificationPermissionStatus();
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED)
                checkFirebaseToken();
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
        bannerForeground=true;
        if (adView != null) adView.resume();
        if(bannerBox!=null)bannerBox.setVisibility(View.VISIBLE);
        if(adView!=null)adView.setVisibility(View.VISIBLE);
        if(bannerReady&&!bannerLoaded)requestBanner(false);
        active = this;
        if (pageLoaded) { sendSavedToken(); checkFirebaseToken(); }
    }

    @Override protected void onPause() {
        bannerForeground=false;
        bannerHandler.removeCallbacks(bannerRetry);
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
        bannerHandler.removeCallbacks(bannerRetry);
        if (adView != null) { adView.destroy(); adView = null; }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
