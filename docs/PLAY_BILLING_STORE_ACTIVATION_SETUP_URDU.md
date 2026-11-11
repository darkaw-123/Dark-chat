# Dark Chat V9.14 — Google Play Store Activation (PKR 1,500)

## Current implementation
- Android package: `com.darkaw.darkchat`
- App version: `9.14`, versionCode `25`
- Existing Firebase project: `dark-chat-31924` and the new matching `app/google-services.json` are configured.
- Product ID (must match exactly): `darkchat_store_activation`
- Native Google Play Billing library: `9.1.0`
- Backend verification: Supabase Edge Function `darkchat-verify-play-store`, JWT required.
- Account ownership: Google Play's obfuscated account ID is compared with the signed-in Supabase user.
- Grant: restricted service-role RPC `dc_finalize_verified_play_store_purchase`; reuses the existing store-approval trigger.
- Authorized store owners have the native AdMob banner hidden after their `dc_stores.active` status is loaded.
- Non-Play APK: existing JazzCash/manual admin-approval flow. Play AAB: Google Play Billing flow for the *digital* seller store activation.

## Steps you must complete in your own Google Play account
1. Set up a linked Google Play payments/merchant profile, if it is not set up already.
2. Upload the signed Play AAB on an **Internal testing** track for package `com.darkaw.darkchat`.
3. In Play Console > Monetize with Play > Products > One-time products, create an active **non-consumable** product with ID `darkchat_store_activation`. Configure its buy option and price (PKR 1,500 where supported; Google Play will show localized pricing otherwise).
4. Configure and enable the **Google Play Android Publisher API**, create an authorized **service account**, and give it the proper app-level Play Console permissions to verify in-app purchases (including financial data).
5. Set **`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`** in your Supabase project's Edge Function Secrets to the entire service-account JSON, as a valid single JSON string. Never put that private key in HTML, Java, a public GitHub repo, Firebase's `google-services.json`, or a public chat.
6. In Play Console > License Testing, add your test accounts. Install Dark Chat from its Play internal test link. Test purchase / pending / cancellation / restore / ownership and whether ad banner disappears after *verified* seller activation.
7. Before production launch, configure Google Play Real-time Developer Notifications (RTDN) and/or regular purchase/voided purchase reconciliation so refunded/revoked purchases can be reviewed and entitlement revoked as appropriate.

## Important policy notes
Google Pay API is not the appropriate API for a digital store activation. Use **Google Play Billing** in your Google Play distribution. JazzCash/manual activation stays in a separate direct-distribution build unless you enroll in an eligible Google alternative billing program.

**No real Google Play checkout can be considered tested until steps 1–6 are completed.** A source build compiling successfully only verifies the integration compiles, not merchant approval or receipt verification.

## Security notes
The Edge Function refuses activation when its Google Play service account secret is absent, the Google Play purchase state is not PURCHASED, the product ID is different, the obfuscated account identifier does not match the authenticated user, or the receipt has already been used for another user.
Only the service-role RPC can finalize a verified purchase. An uploaded screenshot, forged token or client-only success event cannot activate the store. Exact Play token values and Google service account keys are not stored in the public app or GitHub repo.
