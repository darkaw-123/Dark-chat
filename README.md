# Haveli Mystery Android

Android WebView game source. The local game is loaded through AndroidX WebViewAssetLoader. Debug builds use Google sample App ID and test ad units. Release configuration includes the provided AdMob App ID and banner, interstitial, and rewarded units. Rewarded ads are optional and award a bonus hint only on the SDK reward callback. The interstitial button is explicitly initiated by the player. Consent is handled by the Google UMP SDK. Confirm AdMob app/package association, privacy messaging, and actual ad delivery on a device before production.

GitHub Actions builds a debug/test APK. It is signed using the runner's temporary Android debug key and must not be used as a Play Store release or as an update path for another signing key. No release keystore or production APK is generated. Build a separately signed release using a private, durable keystore and test on a device. Do not upload signing keys to the repository.
