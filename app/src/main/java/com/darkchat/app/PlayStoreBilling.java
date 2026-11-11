package com.darkchat.app;

import android.app.Activity;
import android.util.Log;
import com.android.billingclient.api.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Google Play in-app purchase flow for the non-consumable digital store activation.
 * NEVER grants store access: purchase tokens are passed to the authenticated
 * Supabase Edge Function, which verifies with Google Play Developer API.
 * Use only in Play-distributed builds (BuildConfig.PLAY_STORE_BUILD).
 */
public final class PlayStoreBilling implements PurchasesUpdatedListener {
    public static final String PRODUCT_ID = "darkchat_store_activation";
    public interface EventSink { void send(String status, String message, String token, String price); }
    private final Activity activity;
    private final EventSink sink;
    private final BillingClient billing;
    private boolean connected=false;

    public PlayStoreBilling(Activity activity, EventSink sink) {
        this.activity=activity;
        this.sink=sink;
        billing=BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
        connect(null);
    }
    private void connect(Runnable ready) {
        if (billing.isReady()) {connected=true;if(ready!=null)ready.run();return;}
        billing.startConnection(new BillingClientStateListener(){
            @Override public void onBillingSetupFinished(BillingResult result) {
                connected=result.getResponseCode()==BillingClient.BillingResponseCode.OK;
                if(connected){if(ready!=null)ready.run();}
                else emit("error","Google Play Billing unavailable: "+result.getDebugMessage(),null,null);
            }
            @Override public void onBillingServiceDisconnected(){connected=false;}
        });
    }
    public void buy(String userId) {
        if(!BuildConfig.PLAY_STORE_BUILD) {
            emit("error","Google Play purchases require the app installed from Google Play.",null,null);
            return;
        }
        if(userId==null||!userId.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            emit("error","Sign in to your Dark Chat account first.",null,null);
            return;
        }
        connect(()->lookupAndPurchase(userId));
    }
    private void lookupAndPurchase(String userId){
        QueryProductDetailsParams.Product product=QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build();
        QueryProductDetailsParams params=QueryProductDetailsParams.newBuilder()
            .setProductList(Collections.singletonList(product)).build();
        billing.queryProductDetailsAsync(params,(result,details)->{
            if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK||details.getProductDetailsList().isEmpty()){
                emit("error","Store activation is not yet available in Google Play. Publish the one-time product in Play Console.",null,null);
                return;
            }
            ProductDetails item=details.getProductDetailsList().get(0);
            List<ProductDetails.OneTimePurchaseOfferDetails> offers=item.getOneTimePurchaseOfferDetailsList();
            if(offers==null||offers.isEmpty()) {
                emit("error","No eligible Google Play offer is available for this account.",null,null); return;
            }
            ProductDetails.OneTimePurchaseOfferDetails offer=offers.get(0);
            BillingFlowParams.ProductDetailsParams itemParam=BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(item).setOfferToken(offer.getOfferToken()).build();
            BillingFlowParams flow=BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(itemParam))
                .setObfuscatedAccountId(hashUser(userId))
                .build();
            emit("price","Google Play price confirmed",null,offer.getFormattedPrice());
            BillingResult started=billing.launchBillingFlow(activity,flow);
            if(started.getResponseCode()!=BillingClient.BillingResponseCode.OK)
                emit("error","Unable to start payment: "+started.getDebugMessage(),null,null);
        });
    }
    public void restore(){
        if(!BuildConfig.PLAY_STORE_BUILD){emit("error","Restore is available only in the Google Play build.",null,null);return;}
        connect(()->{
            QueryPurchasesParams params=QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build();
            billing.queryPurchasesAsync(params,(result,purchases)->{
                if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){
                    emit("error","Could not restore Google Play purchases.",null,null);return;
                }
                boolean found=false;
                for(Purchase purchase:purchases) if(purchase.getProducts().contains(PRODUCT_ID)){
                    found=true;handlePurchase(purchase);
                }
                if(!found)emit("info","No Google Play store activation purchase found on this Play account.",null,null);
            });
        });
    }
    @Override public void onPurchasesUpdated(BillingResult result,List<Purchase> purchases){
        if(result.getResponseCode()==BillingClient.BillingResponseCode.USER_CANCELED){
            emit("cancelled","Payment cancelled; no money was charged by Dark Chat.",null,null);return;
        }
        if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK||purchases==null){
            emit("error","Google Play payment error: "+result.getDebugMessage(),null,null);return;
        }
        for(Purchase p:purchases)handlePurchase(p);
    }
    private void handlePurchase(Purchase purchase){
        if(!purchase.getProducts().contains(PRODUCT_ID))return;
        if(purchase.getPurchaseState()==Purchase.PurchaseState.PENDING){
            emit("pending","Payment is pending. Store access will activate only after verification.",null,null);
        }else if(purchase.getPurchaseState()==Purchase.PurchaseState.PURCHASED){
            // A PURCHASED callback is NOT proof: backend validates Google Play token.
            emit("verify","Google Play payment received. Checking securely with Google...",purchase.getPurchaseToken(),null);
        }else{
            emit("cancelled","Google Play payment was not completed.",null,null);
        }
    }
    private String hashUser(String userId){
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] bytes=md.digest(userId.toLowerCase(Locale.US).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();
            for(byte b:bytes)hex.append(String.format(Locale.US,"%02x",b&0xff));
            return hex.toString();
        }catch(Exception e){throw new IllegalStateException("Unable to secure Play purchase identity",e);}
    }
    private void emit(String status,String message,String token,String price){
        if(sink!=null)sink.send(status,message,token,price);
    }
    public void close(){billing.endConnection();}
}
