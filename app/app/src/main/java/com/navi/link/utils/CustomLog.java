package com.navi.link.utils;

import android.util.Log;

/**
 * 副屏模块自定义系统统一日志埋点工具
 * TAG: NaviLinkModule —— 抓取：adb logcat -s NaviLinkModule
 * ENABLED 置 false 可一键关闭全部日志（稳定后建议关闭）
 * dThrottle 用于高频数据日志（节流，避免刷屏）
 */
public class CustomLog {
    public static final String TAG = "NaviLinkModule";

    private static boolean enabled = true;

    private static final long THROTTLE_MS = 3000;
    private static long lastThrottleTime = 0;

    public static void d(String msg) {
        if (enabled) Log.d(TAG, msg);
    }

    public static void e(String msg, Throwable t) {
        if (enabled) Log.e(TAG, msg, t);
    }

    /** 高频数据节流日志：每 THROTTLE_MS 最多输出一条，防止广播刷屏 */
    public static synchronized void dThrottle(String msg) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (now - lastThrottleTime >= THROTTLE_MS) {
            Log.d(TAG, msg);
            lastThrottleTime = now;
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
