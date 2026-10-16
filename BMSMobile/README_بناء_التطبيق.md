# BMS Mobile — تعليمات بناء ملف APK

هذا مشروع أندرويد جاهز بالكامل يغلّف تطبيق «حلويات العلامة الزرقاء» داخل WebView أصلي،
باسم **BMS Mobile**، مع دعم البصمة / بصمة الوجه والإشعارات وكاميرا ماسح QR.

جميع ملفات الويب مدمجة داخل التطبيق (تعمل دون إنترنت) في:
`app/src/main/assets/www/`

---

## الطريقة الأسهل — Android Studio (موصى بها)

1. ثبّت **Android Studio** (أحدث إصدار).
2. من القائمة: **File → Open** ثم اختر مجلد `BMSMobile`.
3. انتظر حتى ينتهي Gradle Sync (سيُنزّل الأدوات تلقائياً).
4. من القائمة: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. عند الانتهاء اضغط **locate** لفتح مكان الملف:
   `app/build/outputs/apk/debug/app-debug.apk`

هذا الملف قابل للتثبيت مباشرة على أي جوال أندرويد (فعّل «المصادر غير المعروفة»).

---

## الطريقة عبر سطر الأوامر (بدون Android Studio)

تحتاج: JDK 17 + Android SDK مضبوطًا (متغير `ANDROID_HOME`) + اتصال إنترنت لأول بناء.

```bash
cd BMSMobile
# أنشئ ملف local.properties يشير إلى مسار Android SDK:
echo "sdk.dir=/مسار/Android/sdk" > local.properties

# أنشئ Gradle Wrapper (مرة واحدة) إن لم يكن موجودًا:
gradle wrapper --gradle-version 8.9

# بناء نسخة debug (موقّعة تلقائياً وجاهزة للتثبيت):
./gradlew assembleDebug
# الناتج: app/build/outputs/apk/debug/app-debug.apk

# (اختياري) نسخة release:
./gradlew assembleRelease
```

---

## ملاحظات

- **اسم التطبيق:** BMS Mobile (في `res/values/strings.xml`).
- **معرّف الحزمة:** `com.bluemark.bmsmobile`.
- **الأيقونة:** شعار العلامة الزرقاء.
- **البصمة / بصمة الوجه:** تعمل عبر BiometricPrompt الأصلي (جسر `AndroidBio`).
- **السحب الأفقي:** أُوقِف من طرفين: CSS وأيضاً `OVER_SCROLL_NEVER` داخل WebView.
- **لتحديث محتوى التطبيق** لاحقاً: استبدل ملفات `app/src/main/assets/www/` ثم أعد البناء.
