# DARK CHAT V9.13 — اصلی AdMob اشتہارات کے ساتھ Production APK

V9.12 تک جو APK GitHub Actions سے ملی تھی، وہ **Debug** تھی۔ Android کے سورس میں موجود چیک (FLAG_DEBUGGABLE) کی وجہ سے Debug ورژن Google کی Test Ad Unit استعمال کرتا ہے۔ اصل V9.13 **Release** ورژن اصلی Banner Ad Unit استعمال کرتا ہے۔

- Android package: `com.darkchat.app`
- Original AdMob App ID: `ca-app-pub-7138311889196067~7564520939`
- Production Banner Ad Unit: `ca-app-pub-7138311889196067/3355094444`
- Debug Test Unit: `ca-app-pub-3940256099942544/6300978111`

> صرف Test Ad کا متن چھپانا مسئلے کا حل نہیں۔ اصلی اشتہار کے لیے non-debug، درست signing، AdMob ایپ کی منظوری اور اشتہاری inventory درکار ہے۔

## GitHub Actions سے مستقل سائن شدہ APK کیسے بنائیں؟

1. **اگر پہلے سے Production Signing Key موجود ہے، ہمیشہ اسی کو استعمال کریں۔** نئی key پر دستخط کرنے سے پرانے Production ایپ کے اوپر اپ ڈیٹ نہیں ہو سکے گی۔ Debug APK سے Production APK کا دستخط عموماً مختلف ہوتا ہے۔
2. اگر key نئی ہے تو اسے **نجی اور محفوظ** رکھیں؛ public GitHub repository میں upload نہ کریں۔
3. GitHub پر `Settings → Secrets and variables → Actions → New repository secret` کھول کر چار Repository Secrets بنائیں:
   - `DARKCHAT_RELEASE_KEYSTORE_B64` — مستقل keystore کی پوری Base64 value (بغیر line breaks)
   - `DARKCHAT_RELEASE_STORE_PASSWORD` — keystore کا password
   - `DARKCHAT_RELEASE_KEY_ALIAS` — signing key alias
   - `DARKCHAT_RELEASE_KEY_PASSWORD` — key کا password
4. `Actions` ٹیب میں **DARK CHAT V9.13 — Signed Production APK (Real AdMob)** کھولیں اور `Run workflow` کریں۔
5. کامیابی کی صورت میں GitHub Release `v9.13` پر `Dark-Chat-v9.13-Production.apk` شائع ہوگی۔ APK signatures کی verification workflow میں شامل ہے۔ Passwords یا key کبھی code میں hard-code نہ کریں۔

**رابطے:** [Production Workflow](../.github/workflows/build-production-apk.yml) · [GitHub Actions](../../actions) · [ریلیز](../../releases)

## اصل AdMob اشتہارات کے لیے کیا شرط ہے؟

- Google AdMob میں `com.darkchat.app` والی ایپ کا **Ready** status اور اجازت موجود ہو۔ اپنے AdMob Dashboard میں یہ status خود دیکھیں۔
- AdMob کی app readiness review کے لیے ایپ Google کے supported app store میں عام دستیاب ہو، اور اسٹور لسٹنگ AdMob میں link ہو۔ **صرف GitHub یا Netlify پر APK رکھنے سے یہ شرط پوری نہیں ہوتی۔**
- ضرورت پڑنے پر `app-ads.txt` verification، privacy disclosure/consent اور AdMob policies مکمل کریں۔
- اپنی ایپ میں اپنے live ads پر ہرگز click نہ کریں؛ اپنی test devices کو AdMob کے ذریعے ٹیسٹ موڈ میں رکھیں۔
- **اصل APK میں Google Test Ad نہیں ہوتا، مگر اس کا مطلب یہ نہیں کہ Google ہر وقت اصلی اشتہار دکھائے گا۔** اگر AdMob review pending، ad serving limited یا no-fill ہو تو کوئی banner نظر نہیں آئے گا۔

### APK اپ ڈیٹ کرنے سے پہلے

پرانے Debug build کے اوپر نئی Production build install کرنے میں دستخط کا تنازعہ آ سکتا ہے۔ پرانی APK ان انسٹال کرنے سے پہلے مقامی ضروری ڈیٹا کا بیک اپ لیں۔ پیغامات اور پروفائل ڈیٹا کے لیے Supabase اکاؤنٹ برقرار رکھیں۔

**Official guidance:** https://developers.google.com/admob/android/test-ads · https://support.google.com/admob/answer/10564477 · https://developer.android.com/studio/publish/app-signing
