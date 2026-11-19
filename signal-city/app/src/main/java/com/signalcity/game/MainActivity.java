package com.signalcity.game;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.webkit.WebViewAssetLoader;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String GAME_URL = "https://appassets.androidplatform.net/assets/index.html";
    private WebView game;
    private AdView banner;
    private RewardedAd rewarded;
    private ConsentInformation consent;
    private Button privacy;
    private boolean adsInitialized, loading, displaying;
    private String pendingId;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16,26,47));
        game = new WebView(this);
        game.setBackgroundColor(Color.rgb(16,26,47));
        game.getSettings().setJavaScriptEnabled(true);
        game.getSettings().setDomStorageEnabled(true);
        game.getSettings().setAllowFileAccess(false);
        game.getSettings().setAllowContentAccess(false);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        game.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getHost()) || !uri.getPath().startsWith("/assets/");
            }
        });
        game.addJavascriptInterface(new AdBridge(), "SignalAds");
        root.addView(game, new LinearLayout.LayoutParams(-1, 0, 1));
        privacy = new Button(this);
        privacy.setText("Privacy choices");
        privacy.setVisibility(View.GONE);
        privacy.setOnClickListener(v -> UserMessagingPlatform.showPrivacyOptionsForm(this, error -> maybeInitializeAds()));
        root.addView(privacy, new LinearLayout.LayoutParams(-1, -2));
        banner = new AdView(this);
        banner.setAdSize(AdSize.BANNER);
        banner.setAdUnitId(BuildConfig.BANNER_AD_UNIT);
        root.addView(banner, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        game.loadUrl(GAME_URL);
        consent = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(this, params,
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, error -> { updatePrivacy(); maybeInitializeAds(); }),
            error -> { updatePrivacy(); maybeInitializeAds(); });
        maybeInitializeAds();
    }
    private void updatePrivacy() {
        privacy.setVisibility(consent.getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED ? View.VISIBLE : View.GONE);
    }
    private void maybeInitializeAds() {
        if (consent == null || !consent.canRequestAds() || adsInitialized) return;
        adsInitialized = true;
        MobileAds.initialize(this, status -> runOnUiThread(() -> {
            banner.loadAd(new AdRequest.Builder().build());
            loadReward();
        }));
    }
    private void loadReward() {
        if (!adsInitialized || loading || rewarded != null) return;
        loading = true;
        RewardedAd.load(this, BuildConfig.REWARDED_AD_UNIT, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd ad) { loading=false; rewarded=ad; if(pendingId!=null) showPending(); }
            @Override public void onAdFailedToLoad(LoadAdError error) { loading=false; rewarded=null; if(pendingId!=null){ send(pendingId,"unavailable"); pendingId=null; } }
        });
    }
    private void showPending() {
        if (pendingId == null || rewarded == null || displaying || isFinishing()) return;
        final String requestId = pendingId;
        pendingId = null;
        RewardedAd ad = rewarded;
        rewarded = null;
        displaying = true;
        final boolean[] earned = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                displaying=false; send(requestId,"unavailable"); loadReward();
            }
            @Override public void onAdDismissedFullScreenContent() {
                displaying=false;
                handler.postDelayed(() -> { if(!earned[0]) send(requestId,"closed"); }, 2000);
                loadReward();
            }
        });
        ad.show(this, reward -> { earned[0]=true; send(requestId,"earned"); });
    }
    private void send(String id, String status) {
        if (game == null || !GAME_URL.equals(game.getUrl())) return;
        game.evaluateJavascript("window.signalRewardResult && window.signalRewardResult("+JSONObject.quote(id)+","+JSONObject.quote(status)+")", null);
    }
    private class AdBridge {
        @JavascriptInterface public void cancelReward(String requestId) {
            runOnUiThread(() -> { if (requestId != null && requestId.equals(pendingId)) pendingId = null; });
        }
        @JavascriptInterface public void showReward(String requestId) {
            if (requestId == null || !requestId.matches("[A-Za-z0-9]{3,64}")) return;
            runOnUiThread(() -> {
                if (!GAME_URL.equals(game.getUrl()) || !adsInitialized || displaying || pendingId!=null) { send(requestId,"unavailable"); return; }
                pendingId=requestId;
                if(rewarded!=null) showPending(); else loadReward();
            });
        }
    }
    @Override protected void onPause() { if(banner!=null)banner.pause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if(banner!=null)banner.resume(); }
    @Override protected void onDestroy() { if(banner!=null)banner.destroy(); if(game!=null){game.removeJavascriptInterface("SignalAds");game.destroy();game=null;} super.onDestroy(); }
}
