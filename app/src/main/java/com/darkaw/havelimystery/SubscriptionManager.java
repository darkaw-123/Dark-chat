package com.darkaw.havelimystery;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.android.billingclient.api.*;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/** Play subscriptions remain disabled until the Play Console product and license key are configured. */
final class SubscriptionManager implements PurchasesUpdatedListener {
    private static final String PRODUCT = "haveli_ad_free";
    private final Activity activity; private final Runnable changed; private final BillingClient client;
    private volatile boolean resolved=false, active=false;
    private boolean connected=false;
    SubscriptionManager(Activity activity, Runnable changed) {
        this.activity=activity; this.changed=changed;
        client=BillingClient.newBuilder(activity).setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection().build();
        if(configured()) connect(); else {resolved=true;changed.run();}
    }
    private boolean configured(){return !BuildConfig.PLAY_LICENSE_KEY.trim().isEmpty();}
    boolean isActive(){return active;}
    boolean adsAllowed(){return resolved && !active;}
    private void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
    private void connect(){client.startConnection(new BillingClientStateListener(){
        public void onBillingSetupFinished(@NonNull BillingResult r){connected=r.getResponseCode()==BillingClient.BillingResponseCode.OK;if(connected) refresh();}
        public void onBillingServiceDisconnected(){connected=false;}
    });}
    void refresh(){if(!configured()||!connected)return;client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),(r,purchases)->{
        if(r.getResponseCode()!=BillingClient.BillingResponseCode.OK)return;
        boolean valid=false;for(Purchase p:purchases)if(valid(p)){valid=true;acknowledge(p);}
        active=valid;resolved=true;changed.run();
    });}
    private boolean valid(Purchase p){try{
        if(p.getPurchaseState()!=Purchase.PurchaseState.PURCHASED||!p.getProducts().contains(PRODUCT))return false;
        JSONObject data=new JSONObject(p.getOriginalJson());if(!activity.getPackageName().equals(data.optString("packageName")))return false;
        byte[] key=Base64.decode(BuildConfig.PLAY_LICENSE_KEY,Base64.DEFAULT);
        Signature verifier=Signature.getInstance("SHA1withRSA");
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));
        verifier.update(p.getOriginalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return verifier.verify(Base64.decode(p.getSignature(),Base64.DEFAULT));
    }catch(Exception e){return false;}}
    private void acknowledge(Purchase p){if(!p.isAcknowledged())client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(),r->{});}
    @Override public void onPurchasesUpdated(@NonNull BillingResult r,List<Purchase> purchases){
        if(r.getResponseCode()==BillingClient.BillingResponseCode.OK&&purchases!=null){for(Purchase p:purchases)if(valid(p)){acknowledge(p);active=true;resolved=true;changed.run();}refresh();}
        else if(r.getResponseCode()!=BillingClient.BillingResponseCode.USER_CANCELED)toast("Purchase could not be completed. Please try again.");
    }
    void showOptions(){
        if(!configured()){new AlertDialog.Builder(activity).setTitle("Ad-free subscription").setMessage("Subscriptions are not configured yet. No payment will be taken. Please check back later.").setPositiveButton("OK",null).show();return;}
        new AlertDialog.Builder(activity).setTitle("Ad-free subscription")
            .setMessage(active?"Your subscription is active. Manage or cancel it in Google Play.":"Remove ads and use hints without watching ads. Your plan renews automatically until cancelled in Google Play. The price and billing period will be shown before you subscribe.")
            .setPositiveButton(active?"Manage":"See price",(d,w)->{if(active)manage();else loadOffer();})
            .setNeutralButton("Restore",(d,w)->{if(connected){refresh();toast("Checking your Google Play purchases…");}else {connect();toast("Connecting to Google Play…");}})
            .setNegativeButton("Close",null).show();
    }
    private void manage(){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/account/subscriptions?package="+activity.getPackageName())));}catch(Exception e){toast("Open Google Play to manage your subscriptions.");}}
    private void loadOffer(){if(!connected){connect();toast("Connecting to Google Play. Please try again.");return;}
        QueryProductDetailsParams.Product product=QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build();
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(product)).build(),(r,result)->{
            List<ProductDetails> products=result.getProductDetailsList();
            if(r.getResponseCode()!=BillingClient.BillingResponseCode.OK||products.isEmpty()){toast("The subscription is not available in Google Play yet.");return;}
            ProductDetails details=products.get(0);List<ProductDetails.SubscriptionOfferDetails> offers=details.getSubscriptionOfferDetails();
            if(offers==null){toast("No subscription plan is available.");return;}
            ProductDetails.SubscriptionOfferDetails chosen=null;
            for(ProductDetails.SubscriptionOfferDetails offer:offers){List<ProductDetails.PricingPhase> phases=offer.getPricingPhases().getPricingPhaseList();if(phases.size()==1&&phases.get(0).getRecurrenceMode()==1){chosen=offer;break;}}
            if(chosen==null){toast("No supported recurring base plan is available.");return;}
            ProductDetails.SubscriptionOfferDetails offer=chosen;ProductDetails.PricingPhase price=offer.getPricingPhases().getPricingPhaseList().get(0);
            String period=price.getBillingPeriod().replace("P1M","month").replace("P1Y","year").replace("P1W","week");
            new AlertDialog.Builder(activity).setTitle("Ad-free subscription").setMessage(price.getFormattedPrice()+" per "+period+". Automatically renews until cancelled in Google Play. Includes ad-free play and hints without ads.")
                .setPositiveButton("Continue to Google Play",(d,w)->{BillingFlowParams.ProductDetailsParams params=BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.getOfferToken()).build();client.launchBillingFlow(activity,BillingFlowParams.newBuilder().setProductDetailsParamsList(Collections.singletonList(params)).build());}).setNegativeButton("Not now",null).show();
        });
    }
    void close(){client.endConnection();}
}
