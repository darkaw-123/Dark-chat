package com.dreamhomedesigner.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String ROOT="https://appassets.androidplatform.net/assets/";
    private static final String BANNER=BuildConfig.DEBUG?"ca-app-pub-3940256099942544/9214589741":"ca-app-pub-7138311889196067/8364589952";
    private static final String REWARDED=BuildConfig.DEBUG?"ca-app-pub-3940256099942544/5354046379":"ca-app-pub-7138311889196067/6093649830";
    private static final int PICK_IMAGE=1234, SAVE_TEXT=1235;
    private WebView web; private FrameLayout bannerContainer; private TextView privacyButton;
    private AdView banner; private RewardedInterstitialAd rewarded; private ConsentInformation consent;
    private ValueCallback<Uri[]> upload; private boolean adsStarted=false, premium=false, premiumKnown=false, loadingReward=false, showing=false;
    private boolean bannerReady=false, bannerLoading=false, consentUpdating=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String pendingText;
    private WebViewAssetLoader assets;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setFitsSystemWindows(true);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff211d3c);
        privacyButton=new TextView(this);privacyButton.setText("Ad privacy choices");privacyButton.setTextColor(0xff554888);privacyButton.setBackgroundColor(0xffffffff);privacyButton.setPadding(18,8,18,8);privacyButton.setVisibility(View.GONE);privacyButton.setOnClickListener(v->showPrivacyOptions());
        web=new WebView(this);bannerContainer=new FrameLayout(this);
        root.addView(privacyButton,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(web,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        root.addView(bannerContainer,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));setContentView(root);
        assets=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if(BuildConfig.DEBUG)WebView.setWebContentsDebuggingEnabled(true);
        web.addJavascriptInterface(new AdBridge(),"AndroidAds");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){return assets.shouldInterceptRequest(req.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return navigate(req.getUrl());}
            @Override @SuppressWarnings("deprecation") public boolean shouldOverrideUrlLoading(WebView view,String url){return navigate(Uri.parse(url));}
        });
        web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams params){if(upload!=null)upload.onReceiveValue(null);upload=cb;try{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(Intent.createChooser(i,"Choose profile photo"),PICK_IMAGE);return true;}catch(Exception ex){upload=null;cb.onReceiveValue(null);return false;}}});
        if(state!=null)web.restoreState(state);else web.loadUrl(ROOT+"index.html");
        consent=UserMessagingPlatform.getConsentInformation(this);
        requestConsent();
        maybeStartAds();
    }
    private boolean navigate(Uri uri){String u=uri.toString();if(u.startsWith(ROOT))return false;if("https".equalsIgnoreCase(uri.getScheme())){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}}return true;}
    private void updatePrivacy(){boolean required=consent!=null&&consent.getPrivacyOptionsRequirementStatus()==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;privacyButton.setVisibility(required?View.VISIBLE:View.GONE);}
    private void showPrivacyOptions(){if(consent==null)return;UserMessagingPlatform.showPrivacyOptionsForm(this,error->{updatePrivacy();maybeStartAds();});}
    private void requestConsent(){if(consent==null||consentUpdating)return;consentUpdating=true;consent.requestConsentInfoUpdate(this,new ConsentRequestParameters.Builder().build(),()->UserMessagingPlatform.loadAndShowConsentFormIfRequired(this,error->{consentUpdating=false;updatePrivacy();maybeStartAds();}),error->{consentUpdating=false;updatePrivacy();maybeStartAds();});}
    private void maybeStartAds(){if(adsStarted||consent==null||!consent.canRequestAds())return;adsStarted=true;MobileAds.initialize(this,status->runOnUiThread(()->{loadBanner();loadRewarded();}));}
    private void updateBannerVisibility(){bannerContainer.setVisibility(bannerReady&&premiumKnown&&!premium?View.VISIBLE:View.GONE);}
    private void loadBanner(){
        if(isFinishing()||!adsStarted||consent==null||!consent.canRequestAds()||bannerLoading||bannerReady)return;
        if(banner==null){
            banner=new AdView(this);banner.setAdUnitId(BANNER);
            int width=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
            banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this,Math.max(1,width)));
            banner.setAdListener(new AdListener(){
                @Override public void onAdLoaded(){bannerLoading=false;bannerReady=true;updateBannerVisibility();}
                @Override public void onAdFailedToLoad(@NonNull LoadAdError error){bannerLoading=false;bannerReady=false;updateBannerVisibility();Log.w("DreamHomeAds","Banner load failed: "+error.getCode()+" "+error.getMessage());handler.removeCallbacks(retryBanner);handler.postDelayed(retryBanner,60000);}
            });
            bannerContainer.removeAllViews();bannerContainer.addView(banner);updateBannerVisibility();
        }
        bannerLoading=true;banner.loadAd(new AdRequest.Builder().build());
    }
    private final Runnable retryBanner=()->{if(!premium)loadBanner();};
    private void loadRewarded(){if(!adsStarted||consent==null||!consent.canRequestAds()||rewarded!=null||loadingReward)return;loadingReward=true;RewardedInterstitialAd.load(this,REWARDED,new AdRequest.Builder().build(),new RewardedInterstitialAdLoadCallback(){@Override public void onAdLoaded(@NonNull RewardedInterstitialAd ad){loadingReward=false;rewarded=ad;}@Override public void onAdFailedToLoad(@NonNull LoadAdError error){loadingReward=false;rewarded=null;}});}
    private void sendToPage(String method){if(web!=null&&web.getUrl()!=null&&web.getUrl().startsWith(ROOT))web.evaluateJavascript("window.DreamAds && window.DreamAds."+method+"()",null);}
    private void showRewarded(String reason){if(showing)return;if(consent==null||!consent.canRequestAds()||rewarded==null){loadRewarded();sendToPage("_failed");return;}showing=true;String reward="item".equals(reason)?"Unlock the selected item after the reward is earned.":"Earn 50 coins after the reward is earned.";
        new AlertDialog.Builder(this).setTitle("Rewarded advertisement").setMessage(reward+" You may skip this ad; skipping gives no reward.").setNegativeButton("Skip",(d,w)->{showing=false;sendToPage("_failed");}).setPositiveButton("Watch ad",(d,w)->{RewardedInterstitialAd ad=rewarded;if(ad==null){showing=false;sendToPage("_failed");return;}final boolean[] earned={false};ad.setFullScreenContentCallback(new FullScreenContentCallback(){@Override public void onAdDismissedFullScreenContent(){rewarded=null;showing=false;if(!earned[0])sendToPage("_failed");loadRewarded();}@Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e){rewarded=null;showing=false;sendToPage("_failed");loadRewarded();}});ad.show(this,item->{if(!earned[0]){earned[0]=true;sendToPage("_rewarded");}});}).setOnCancelListener(d->{showing=false;sendToPage("_failed");}).show();}
    private class AdBridge {
        @JavascriptInterface public void showRewarded(String reason){runOnUiThread(()->MainActivity.this.showRewarded("item".equals(reason)?"item":"coins"));}
        @JavascriptInterface public void setPremium(boolean value){runOnUiThread(()->{premium=value;premiumKnown=true;updateBannerVisibility();if(!value&&adsStarted&&!bannerReady&&!bannerLoading)loadBanner();});}
        @JavascriptInterface public void showPrivacyOptions(){runOnUiThread(()->MainActivity.this.showPrivacyOptions());}
        @JavascriptInterface public void saveText(String text){runOnUiThread(()->{if(text==null||text.length()>524288){Toast.makeText(MainActivity.this,"Item list is too large",Toast.LENGTH_SHORT).show();return;}try{pendingText=text;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"dream-home-item-list.csv");startActivityForResult(i,SAVE_TEXT);}catch(Exception e){pendingText=null;Toast.makeText(MainActivity.this,"Could not open a location to save the list",Toast.LENGTH_LONG).show();}});}
        @JavascriptInterface public void printPlan(){runOnUiThread(()->{try{PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);pm.print("Dream Home room plan",web.createPrintDocumentAdapter("Dream Home room plan"),null);}catch(Exception e){Toast.makeText(MainActivity.this,"Printing is not available on this device",Toast.LENGTH_LONG).show();}});}
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);web.saveState(out);}
    @Override @SuppressWarnings("deprecation") protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==PICK_IMAGE&&upload!=null){Uri[] selected=result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null;upload.onReceiveValue(selected);upload=null;}else if(req==SAVE_TEXT){String text=pendingText;pendingText=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&text!=null){try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){if(stream==null)throw new Exception("No output stream");stream.write(text.getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"Item list saved",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Could not save item list",Toast.LENGTH_LONG).show();}}}}
    @Override protected void onResume(){super.onResume();if(banner!=null)banner.resume();if(consent!=null){if(!adsStarted&&!consent.canRequestAds())requestConsent();else maybeStartAds();if(adsStarted&&!bannerReady&&!bannerLoading)handler.postDelayed(retryBanner,1000);}}
    @Override protected void onPause(){handler.removeCallbacks(retryBanner);if(banner!=null)banner.pause();super.onPause();}
    @Override @SuppressWarnings("deprecation") public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(upload!=null){upload.onReceiveValue(null);upload=null;}if(banner!=null)banner.destroy();if(web!=null){web.removeJavascriptInterface("AndroidAds");web.destroy();}super.onDestroy();}
}
