package com.bluemark.bmsmobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/**
 * BootReceiver
 * ------------
 * يُعيد تشغيل خدمة مراقبة الطلبات تلقائياً بعد إعادة تشغيل الجهاز
 * أو بعد تحديث التطبيق، لتبقى الإشعارات تصل دون فتح التطبيق يدوياً.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Intent svc = new Intent(context, OrderNotificationService.class);
            ContextCompat.startForegroundService(context, svc);
        }
    }
}
