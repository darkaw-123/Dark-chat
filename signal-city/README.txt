SIGNAL CITY — ANDROID SOURCE PROJECT

This is source code, not an APK. It requires Android Studio / Gradle and Android SDK 35 with JDK 17 to build. No APK was built or tested with this source in this workspace.

The debug build is configured with Google's demonstration AdMob App ID, test banner and test rewarded ad unit IDs. Use debug builds for testing. The release configuration contains the AdMob App ID, banner ID and rewarded ID provided by the owner. Confirm Signal City is correctly registered and associated in the AdMob account before using live ads.

The app requests consent through Google UMP before requesting ads. Configure any applicable privacy messages in AdMob. Ad availability is not guaranteed. Rewards are given only when the SDK reports completion. The game stores progress and earned rewards locally on the device; clearing app data or uninstalling can remove them.

The web preview uses simulated ad screens and does not display AdMob banners. Real AdMob integration exists only in this Android source project until a working Android build is compiled and tested. This source does not configure a release signing key. A release needs an owner-controlled key and proper signing setup.

Package: com.signalcity.game (a new application ID; changing it changes app identity). Do not replace the package or signing key of another existing app if you intend it as an update.

A GitHub Actions workflow is included for building a debug APK and a debug AAB with Google's test ads: upload the source folder contents to the root of a GitHub repository, open Actions, choose "Build Android test APK and AAB", and run it. When the workflow succeeds, download the APK and AAB artifacts. The APK can be installed on Android for testing. The debug AAB is for build testing only and is not a Play Store release; a production AAB requires a properly signed release build. The debug build is for testing only; its signature must not be treated as a production release signing key. This workflow has not been executed or verified here.
