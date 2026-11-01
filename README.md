# DARK CHAT — Android APK + Firebase FCM

یہ DARK CHAT کا Android WebView سورس پروجیکٹ ہے۔ HTML ایپ APK کے اندر پیک کی گئی ہے؛ اس کے لیے ویب سائٹ کا لنک ضروری نہیں۔

- GitHub Actions میں مفت Android debug APK بنے گی۔
- Firebase Cloud Messaging (FCM) کا مقامی Android کوڈ شامل ہے۔
- SMS/voice messages کی اطلاعات Supabase Edge Function اور رجسٹرڈ FCM ٹوکن پر منحصر ہیں۔
- ایپ میں اشتہارات کا کوڈ شامل نہیں۔
- Android Force Stop کے بعد نوٹیفکیشن آنے کی ضمانت نہیں۔

## APK حاصل کرنا

GitHub > Actions > **Build DARK CHAT APK with Free FCM** > تازہ کامیاب run > Artifacts > **DARK_CHAT_FREE_FCM_APK**۔ Artifact میں `app-debug.apk` ہوگی۔

## احتیاط

اس ریپوزٹری کو فی الحال Public رکھا گیا ہے۔ خفیہ Firebase service-account JSON، Supabase service-role key یا کوئی پاس ورڈ اس میں شامل نہ کریں۔ `google-services.json` صرف Android Firebase client configuration ہے، ایڈمن کا خفیہ credential نہیں۔

Debug APK ذاتی جانچ کے لیے ہے۔ اصل استعمال کے لیے release signing اور حقیقی موبائل پر جانچ درکار ہے۔
