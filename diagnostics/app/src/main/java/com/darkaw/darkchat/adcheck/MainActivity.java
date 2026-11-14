package com.darkaw.darkchat.adcheck;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

public final class MainActivity extends Activity {
    private AdView banner;
    private TextView status;
    private boolean sdkReady;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24,24,24,24);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.rgb(16,14,23));
        TextView title=new TextView(this);
        title.setText("Dark Chat — Ad Test");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        layout.addView(title);
        TextView intro=new TextView(this);
        intro.setText("This separate app displays a Google TEST banner. No Dark Chat account or real ads are used. Please share a screenshot of the result.");
        intro.setTextSize(16);
        intro.setTextColor(Color.LTGRAY);
        intro.setPadding(0,20,0,20);
        layout.addView(intro);
        status=new TextView(this);
        status.setTextSize(16);
        status.setTextColor(Color.WHITE);
        status.setPadding(0,10,0,20);
        int services=GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        status.setText(services==ConnectionResult.SUCCESS ? "Starting Google Mobile Ads…" : "Google Play services check returned code "+services+". Starting test…");
        layout.addView(status);
        Button retry=new Button(this);
        retry.setText("Retry test ad");
        retry.setOnClickListener(v->requestAd());
        layout.addView(retry);
        banner=new AdView(this);
        banner.setAdUnitId("ca-app-pub-3940256099942544/6300978111");
        int width=Math.max(1,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-32);
        banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this,width));
        banner.setAdListener(new AdListener(){
            @Override public void onAdLoaded(){status.setText("SUCCESS: Google test ad loaded. Please take a screenshot.");}
            @Override public void onAdFailedToLoad(LoadAdError error){status.setText("TEST AD FAILED\nCode: "+error.getCode()+"\nDomain: "+error.getDomain()+"\nMessage: "+error.getMessage()+"\nPlease take a screenshot.");}
        });
        layout.addView(banner,new LinearLayout.LayoutParams(-2,-2));
        setContentView(layout);
        MobileAds.initialize(this,initializationStatus->runOnUiThread(()->{sdkReady=true;requestAd();}));
    }

    private void requestAd(){
        if(!sdkReady){status.setText("Google Mobile Ads is still starting. Please wait.");return;}
        if(banner.isLoading()){status.setText("A test ad is already loading…");return;}
        status.setText("Requesting a Google test ad…");
        banner.loadAd(new AdRequest.Builder().build());
    }
    @Override protected void onResume(){super.onResume();if(banner!=null)banner.resume();}
    @Override protected void onPause(){if(banner!=null)banner.pause();super.onPause();}
    @Override protected void onDestroy(){if(banner!=null)banner.destroy();super.onDestroy();}
}
