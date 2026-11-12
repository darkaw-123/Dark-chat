package com.darkaw.havelimystery;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public final class MainActivity extends Activity {
    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/6300978111";
    private static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";
    private static final String LIVE_BANNER = "ca-app-pub-7138311889196067/5526768117";
    private static final String LIVE_INTERSTITIAL = "ca-app-pub-7138311889196067/2297969540";
    private static final String LIVE_REWARDED = "ca-app-pub-7138311889196067/5035813523";
    private WebView web; private AdView banner; private RewardedAd reward; private InterstitialAd interstitial;
    private ConsentInformation consent; private boolean adsStarted=false; private SubscriptionManager subscriptions;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff120d0a); getWindow().setNavigationBarColor(0xff120d0a);
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setBackgroundColor(0xff120d0a);
        web=new WebView(this); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        WebViewAssetLoader assets=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request){return assets.shouldInterceptRequest(request.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !"appassets.androidplatform.net".equals(request.getUrl().getHost());}
        });
        web.addJavascriptInterface(new AdsBridge(),"HaveliAds");
        layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        banner=new AdView(this); banner.setVisibility(View.GONE); banner.setAdUnitId(BuildConfig.DEBUG?TEST_BANNER:LIVE_BANNER);
        banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)));
        layout.addView(banner,new LinearLayout.LayoutParams(-1,-2)); setContentView(layout);
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        consent=UserMessagingPlatform.getConsentInformation(this);
        consent.requestConsentInfoUpdate(this,new ConsentRequestParameters.Builder().build(),()->UserMessagingPlatform.loadAndShowConsentFormIfRequired(this,e->maybeStart()),e->maybeStart());
        subscriptions=new SubscriptionManager(this,()->runOnUiThread(this::subscriptionChanged));
        maybeStart();
    }
    private void subscriptionChanged(){if(subscriptions==null)return;boolean active=subscriptions.isActive();if(active){banner.setVisibility(View.GONE);banner.pause();reward=null;interstitial=null;}else if(subscriptions.adsAllowed()){if(adsStarted){banner.resume();loadBanner();loadReward();loadInterstitial();}else maybeStart();}web.evaluateJavascript("window.haveliSubscriptionChanged && window.haveliSubscriptionChanged("+active+")",null);}
    private void maybeStart(){if(adsStarted || !consent.canRequestAds() || subscriptions==null || !subscriptions.adsAllowed())return;adsStarted=true;MobileAds.initialize(this,status->runOnUiThread(()->{loadBanner();loadReward();loadInterstitial();}));}
    private AdRequest req(){return new AdRequest.Builder().build();}
    private void loadBanner(){if(subscriptions==null||!subscriptions.adsAllowed())return;banner.setAdListener(new AdListener(){@Override public void onAdLoaded(){if(subscriptions!=null&&subscriptions.adsAllowed())banner.setVisibility(View.VISIBLE);}@Override public void onAdFailedToLoad(@NonNull LoadAdError e){banner.setVisibility(View.GONE);}});banner.loadAd(req());}
    private void loadReward(){if(subscriptions==null||!subscriptions.adsAllowed())return;RewardedAd.load(this,BuildConfig.DEBUG?TEST_REWARDED:LIVE_REWARDED,req(),new RewardedAdLoadCallback(){@Override public void onAdLoaded(@NonNull RewardedAd a){if(subscriptions!=null&&subscriptions.adsAllowed())reward=a;}@Override public void onAdFailedToLoad(@NonNull LoadAdError e){reward=null;}});}
    private void loadInterstitial(){if(subscriptions==null||!subscriptions.adsAllowed())return;InterstitialAd.load(this,BuildConfig.DEBUG?TEST_INTERSTITIAL:LIVE_INTERSTITIAL,req(),new InterstitialAdLoadCallback(){@Override public void onAdLoaded(@NonNull InterstitialAd a){if(subscriptions!=null&&subscriptions.adsAllowed())interstitial=a;}@Override public void onAdFailedToLoad(@NonNull LoadAdError e){interstitial=null;}});}
    private void notifyReward(String value){web.evaluateJavascript("window.haveliRewardResult('"+value+"')",null);}
    private final class AdsBridge {
      @JavascriptInterface public boolean isAdFree(){return subscriptions!=null&&subscriptions.isActive();}
      @JavascriptInterface public void subscription(){runOnUiThread(()->{if(subscriptions!=null)subscriptions.showOptions();});}
      @JavascriptInterface public void reward(){runOnUiThread(()->{if(subscriptions==null||!subscriptions.adsAllowed()||!consent.canRequestAds()||reward==null){notifyReward("unavailable");if(adsStarted)loadReward();return;}RewardedAd a=reward;reward=null;final boolean[] earned={false};a.setFullScreenContentCallback(new FullScreenContentCallback(){@Override public void onAdDismissedFullScreenContent(){loadReward();if(!earned[0])notifyReward("closed");}@Override public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError e){loadReward();notifyReward("unavailable");}});a.show(MainActivity.this,item->{earned[0]=true;notifyReward("earned");});});}
      @JavascriptInterface public void interstitial(){runOnUiThread(()->{if(subscriptions==null||!subscriptions.adsAllowed()||!consent.canRequestAds()||interstitial==null){Toast.makeText(MainActivity.this,"Test ad is not available yet. Try again.",Toast.LENGTH_SHORT).show();if(adsStarted)loadInterstitial();return;}InterstitialAd a=interstitial;interstitial=null;a.setFullScreenContentCallback(new FullScreenContentCallback(){@Override public void onAdDismissedFullScreenContent(){loadInterstitial();}@Override public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError e){loadInterstitial();}});a.show(MainActivity.this);});}
      @JavascriptInterface public void privacy(){runOnUiThread(()->UserMessagingPlatform.showPrivacyOptionsForm(MainActivity.this,error->{if(error!=null)Toast.makeText(MainActivity.this,"Privacy options are not available right now.",Toast.LENGTH_SHORT).show();maybeStart();}));}
    }
    @Override protected void onPause(){if(banner!=null)banner.pause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(banner!=null&&subscriptions!=null&&subscriptions.adsAllowed())banner.resume();if(subscriptions!=null)subscriptions.refresh();}
    @Override protected void onDestroy(){if(subscriptions!=null)subscriptions.close();if(banner!=null)banner.destroy();if(web!=null){web.removeJavascriptInterface("HaveliAds");web.destroy();}super.onDestroy();}
}
