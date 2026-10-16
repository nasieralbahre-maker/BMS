package com.bluemark.bmsmobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * OrderNotificationService
 * -------------------------
 * خدمة أمامية (Foreground Service) تبقى حيّة حتى والتطبيق في الخلفية،
 * تستمع مباشرة إلى مجموعة «orders» في Firestore، وتُظهر إشعارًا في شريط
 * النظام عند وصول أي طلب جديد — دون الحاجة لفتح التطبيق.
 *
 * تتم تهيئة FirebaseApp يدوياً بنفس إعدادات تطبيق الويب (firebase-config.js)
 * دون الحاجة إلى google-services.json.
 */
public class OrderNotificationService extends Service {

    private static final String TAG = "BMSOrderService";

    // إعدادات Firebase — مطابقة تماماً لـ www/firebase-config.js
    private static final String FB_APP_NAME   = "bms";
    private static final String FB_PROJECT_ID = "bluemark-84a3d";
    private static final String FB_APP_ID     = "1:725477176538:web:29c52fd2d6e071b7d021e3";
    private static final String FB_API_KEY    = "AIzaSyCI4V7IY-LkXJJsJaU7SPygTUbTt_qlR7o";

    public static final String CH_SERVICE = "bms_service";   // إشعار الخدمة الدائم (منخفض)
    public static final String CH_ORDERS  = "bms_orders";    // إشعارات الطلبات (عالٍ)
    private static final int FG_NOTIF_ID   = 1;

    private static final String PREFS = "bms_svc";
    private static final String KEY_LAST_TS = "lastTs";

    private FirebaseFirestore db;
    private ListenerRegistration reg;
    private long startThreshold; // لا نُشعر بطلبات أقدم من هذا الزمن

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        // على Android 10+ نمرّر نوع الخدمة (مزامنة بيانات) صراحةً
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;
        ServiceCompat.startForeground(this, FG_NOTIF_ID, buildServiceNotification(), type);
        initFirestore();
        startListening();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY: يُعيد النظام تشغيل الخدمة إن قطعها
        return START_STICKY;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel svc = new NotificationChannel(CH_SERVICE,
                    "تشغيل في الخلفية", NotificationManager.IMPORTANCE_MIN);
            svc.setShowBadge(false);
            nm.createNotificationChannel(svc);

            NotificationChannel ord = new NotificationChannel(CH_ORDERS,
                    "الطلبات الجديدة", NotificationManager.IMPORTANCE_HIGH);
            ord.setDescription("إشعار عند وصول أي طلب جديد");
            ord.enableVibration(true);
            nm.createNotificationChannel(ord);
        }
    }

    private Notification buildServiceNotification() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flag |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, flag);
        return new NotificationCompat.Builder(this, CH_SERVICE)
                .setContentTitle("BMS Mobile يعمل")
                .setContentText("مراقبة الطلبات الجديدة نشطة")
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }

    private void initFirestore() {
        try {
            FirebaseApp app;
            try {
                app = FirebaseApp.getInstance(FB_APP_NAME);
            } catch (IllegalStateException notYet) {
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setProjectId(FB_PROJECT_ID)
                        .setApplicationId(FB_APP_ID)
                        .setApiKey(FB_API_KEY)
                        .build();
                app = FirebaseApp.initializeApp(this, options, FB_APP_NAME);
            }
            db = FirebaseFirestore.getInstance(app);
        } catch (Exception e) {
            Log.e(TAG, "Firestore init failed", e);
        }
    }

    private void startListening() {
        if (db == null) return;

        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastTs = sp.getLong(KEY_LAST_TS, 0L);
        if (lastTs <= 0L) {
            // أول تشغيل: اعتبر كل الطلبات الحالية «مقروءة» حتى لا تنهال الإشعارات
            lastTs = System.currentTimeMillis();
            sp.edit().putLong(KEY_LAST_TS, lastTs).apply();
        }
        startThreshold = lastTs;

        // نستمع فقط للطلبات الأحدث من آخر زمن رأيناه
        Query q = db.collection("orders").whereGreaterThan("createdAt", startThreshold);
        reg = q.addSnapshotListener((snap, err) -> {
            if (err != null || snap == null) {
                if (err != null) Log.w(TAG, "listen error: " + err.getMessage());
                return;
            }
            long maxTs = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_TS, startThreshold);
            for (DocumentChange dc : snap.getDocumentChanges()) {
                if (dc.getType() != DocumentChange.Type.ADDED) continue;
                QueryDocumentSnapshot doc = dc.getDocument();
                long ts = readLong(doc, "createdAt");
                if (ts <= startThreshold) continue;          // طلب قديم — تجاهل
                notifyNewOrder(doc);
                if (ts > maxTs) maxTs = ts;
            }
            if (maxTs > startThreshold) {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putLong(KEY_LAST_TS, maxTs).apply();
            }
        });
    }

    private void notifyNewOrder(QueryDocumentSnapshot doc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return; // لا إذن بالإشعارات
        }

        String name = doc.getString("name");
        long orderNo = readLong(doc, "orderNo");
        double total = readDouble(doc, "total");

        String title = "🔔 طلب جديد" + (orderNo > 0 ? " #" + orderNo : "");
        StringBuilder body = new StringBuilder();
        if (name != null && !name.isEmpty()) body.append(name);
        if (total > 0) {
            if (body.length() > 0) body.append(" · ");
            body.append("💰 ").append(trimNum(total));
        }
        if (body.length() == 0) body.append("وصل طلب جديد");

        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flag |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, (int) (orderNo & 0x7fffffff), i, flag);

        Notification n = new NotificationCompat.Builder(this, CH_ORDERS)
                .setContentTitle(title)
                .setContentText(body.toString())
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int id = (doc.getId() != null) ? doc.getId().hashCode() : (int) System.currentTimeMillis();
            nm.notify(id, n);
        }
    }

    private static long readLong(QueryDocumentSnapshot d, String k) {
        try {
            Object v = d.get(k);
            if (v instanceof Number) return ((Number) v).longValue();
        } catch (Exception ignored) {}
        return 0L;
    }

    private static double readDouble(QueryDocumentSnapshot d, String k) {
        try {
            Object v = d.get(k);
            if (v instanceof Number) return ((Number) v).doubleValue();
        } catch (Exception ignored) {}
        return 0d;
    }

    private static String trimNum(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    @Override
    public void onDestroy() {
        if (reg != null) { reg.remove(); reg = null; }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
