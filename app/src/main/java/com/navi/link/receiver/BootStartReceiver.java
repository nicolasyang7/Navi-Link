package com.navi.link.receiver;
import com.navi.link.R;
import com.navi.link.BuildConfig;
import com.navi.link.activity.*;
import com.navi.link.delegate.*;
import com.navi.link.window.*;
import com.navi.link.view.*;
import com.navi.link.receiver.*;
import com.navi.link.service.*;
import com.navi.link.utils.*;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootStartReceiver extends BroadcastReceiver {
    private static final String TAG = "BootStartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast action: " + action);

        // 如果是覆盖安装，我们选择记录日志并退出，等待下一次亮屏/解锁或用户手动打开时自启
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.d(TAG, "App updated. Defer service start until next screen/unlock/boot event.");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences("floating_config", Context.MODE_PRIVATE);
        boolean autoStart = sp.getBoolean("auto_start", false);
        if (!autoStart) {
            Log.d(TAG, "Auto-start is disabled in configuration.");
            return;
        }

        // 启动主悬浮窗服务
        Intent serviceIntent = new Intent(context, AutoMapService.class);
        try {
            PlatformCompat.startService(context, serviceIntent);
            Log.d(TAG, "AutoMapService started successfully from boot/ACC event: " + action);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AutoMapService on boot/ACC", e);
        }
    }
}
