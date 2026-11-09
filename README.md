# DARK CHAT — V9.12 (Android)

**Latest public Android APK:** [Download Dark Chat V9.12](https://github.com/darkaw-123/Dark-chat/releases/download/v9.12/Dark-Chat-v9.12.apk)

**All public releases and source files:** [GitHub Releases](https://github.com/darkaw-123/Dark-chat/releases/tag/v9.12)

## ڈارک چیٹ ڈاؤن لوڈ

یہ Dark Chat کا آفیشل سورس پروجیکٹ ہے۔ اینڈرائیڈ APK، Firebase Cloud Messaging نوٹیفکیشن، وائس/ویڈیو کال، چیٹ، گروپس اور اسٹور کا موجودہ سسٹم شامل ہے۔

- **APK:** اوپر موجود `Download Dark Chat V9.12` لنک سے براہِ راست حاصل کریں۔
- **HTML:** [index.html (V9.12)](https://github.com/darkaw-123/Dark-chat/releases/download/v9.12/index.html)
- **Source:** [app/src/main/assets/index.html](app/src/main/assets/index.html)
- **GitHub Actions:** [Build status & testing artifacts](https://github.com/darkaw-123/Dark-chat/actions/workflows/build-apk.yml)

## V9.12 کی خصوصیات

- پسندیدہ V9.10 UI برقرار، V9.11 کی ظاہری تبدیلی واپس لی گئی۔
- پروفائل فوٹو اپلوڈ کرتے ہوئے گول فریم میں زوم اور تصویر موو کرنے کی سہولت۔
- ڈیفالٹ DP اور کسی دوست کی DP کو بڑا کر کے دیکھنا۔
- کسی دوست کو اپنی چیٹ لسٹ سے ہٹانا؛ دوبارہ پیغام آنے یا محفوظ نام سے تلاش کرنے پر چیٹ واپس کھولنا۔
- گروپس، اسٹور، وائس/ویڈیو کال اور Firebase نوٹیفکیشن۔

## اہم: AdMob اشتہارات

موجودہ GitHub سے ڈاؤن لوڈ ہونے والی **V9.12 APK Debug build** ہے۔ اس میں Google AdMob SDK اور test banner موجود ہے۔ یہ **test ad unit** استعمال کرتی ہے، اس لیے **اصل اشتہارات کی آمدنی نہیں ہوتی**۔

Source میں production AdMob unit کی ترتیب بھی موجود ہے، مگر live ads کی ذمہ دارانہ public distribution کے لیے ایک **مستقل release signing key** سے non-debug APK بنانا، AdMob ایپ/اکاؤنٹ کی approval مکمل کرنا اور اصل فون پر ads کے load ہونے کی تصدیق کرنا ضروری ہے۔ Google اشتہارات ہمیشہ دکھائی دینے کی ضمانت نہیں دیتا۔ ہر APK کا GitHub Release پر آنا خود بخود AdMob monetization فعال نہیں کرتا۔

## سیکیورٹی نوٹ

یہ public repository ہے۔ Firebase service-account JSON، Supabase service-role key، APK signing keystore یا پاس ورڈ کبھی repository میں commit نہ کریں۔ GitHub Actions کی debug APK کو production Play Store release تصور نہ کریں۔

**Developer repository:** https://github.com/darkaw-123/Dark-chat
