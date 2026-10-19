# BMS Mobile — تعليمات بناء ملف APK

هذا مشروع أندرويد جاهز بالكامل يغلّف تطبيق «حلويات العلامة الزرقاء» داخل WebView أصلي،
باسم **BMS Mobile**، مع دعم البصمة / بصمة الوجه والإشعارات وكاميرا ماسح QR.

جميع ملفات الويب مدمجة داخل التطبيق كنسخة احتياطية في:
`app/src/main/assets/www/`

> **مهم:** التطبيق مضبوط ليحمّل النظام من الموقع البعيد مباشرة
> (`https://bluemark1.netlify.app/`) عبر `MainActivity.java`. بهذا تصل أي
> تحديثات للمستخدمين فور رفعها على الموقع **دون إعادة بناء APK**. يكفي بناء
> الـ APK مرة واحدة فقط ثم توزيعه.

---

## ⭐ البناء عبر GitHub Actions (موصى به — بناء سحابي بدون أي برامج)

المشروع يحتوي على ملف سير عمل جاهز في `.github/workflows/build-apk.yml` يبني الـ APK
تلقائياً على خوادم GitHub. الخطوات:

1. أنشئ مستودعاً جديداً على GitHub (Private أو Public).
2. ارفع محتويات مجلد `BMSMobile` كاملةً إلى المستودع. إمّا عبر واجهة الموقع
   (Add file → Upload files) أو عبر الأوامر:
   ```bash
   cd BMSMobile
   git init
   git add .
   git commit -m "BMS Mobile"
   git branch -M main
   git remote add origin https://github.com/<حسابك>/<اسم-المستودع>.git
   git push -u origin main
   ```
3. بمجرد الرفع (push) يبدأ البناء تلقائياً. تابعه من تبويب **Actions** في المستودع.
   (أو شغّله يدوياً: Actions → Build BMS Mobile APK → Run workflow.)
4. بعد انتهاء البناء (~3-5 دقائق)، افتح تشغيل الـ workflow ونزّل ملف الـ APK من
   قسم **Artifacts** باسم `BMS-Mobile-APK`.
5. ثبّت الـ APK على الجوال (فعّل «تثبيت من مصادر غير معروفة»).

> لا تحتاج لتثبيت Android Studio ولا Gradle على جهازك — كل البناء يتم على السحابة.

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
- **لتحديث محتوى التطبيق** لاحقاً: ارفع ملفاتك الجديدة على الموقع
  (`bluemark1.netlify.app`) ثم انشر التحديث من داخل التطبيق — يصل لكل المستخدمين فوراً
  دون إعادة بناء APK. (ملفات `app/src/main/assets/www/` تبقى كنسخة احتياطية فقط.)

## العمل في الخلفية وإشعارات الطلبات الجديدة

يحتوي التطبيق على خدمة أمامية (`OrderNotificationService`) تستمع مباشرة إلى مجموعة
`orders` في Firestore، وتُظهر إشعارًا في شريط النظام عند وصول أي طلب جديد — حتى والتطبيق
مغلق أو في الخلفية. لضمان عملها بشكل دائم على الجهاز:

1. عند أول تشغيل امنح **إذن الإشعارات** (يظهر تلقائياً).
2. وافق على **استثناء التطبيق من تحسين البطارية** (يظهر تلقائياً) لكي لا يوقِف النظام الخدمة.
3. تعمل الخدمة تلقائياً أيضاً بعد إعادة تشغيل الجهاز.

ملاحظة: إعدادات Firebase مدمجة داخل الخدمة (نفس إعدادات تطبيق الويب)،
فلا حاجة لـ google-services.json.
