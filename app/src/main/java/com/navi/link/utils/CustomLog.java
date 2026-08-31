package com.navi.link.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 副屏模块自定义系统统一日志埋点工具
 * 双通道：logcat + 文件日志（车机无 adb 时用文件日志排查）
 *
 * 日志文件位置（App 专属外部目录，无需权限，车机文件管理器可直接访问）：
 *   /sdcard/Android/data/com.navi.link/files/navi_module.log   ← 运行日志
 *   /sdcard/Android/data/com.navi.link/files/navi_crash.log   ← 崩溃堆栈
 *
 * 使用方法：
 *   1. CustomLog.init(context) —— 在 FloatingWindowManager 构造时自动调用（幂等）
 *   2. CustomLog.d(...) / e(...) / dThrottle(...) 埋点
 *   3. enabled 改为 false 可一键关闭全部日志
 */
public class CustomLog {

    public static final String TAG = "NaviLinkModule";

    private static boolean enabled = true;
    private static Context appContext = null;

    private static final long THROTTLE_MS = 3000;
    private static long lastThrottleTime = 0;

    /** 初始化（幂等）：启用文件日志与全局崩溃落盘 */
    public static void init(Context context) {
        if (context == null || appContext != null) return;
        appContext = context.getApplicationContext();

        // 启动时清空上一次运行的主日志，便于单次问题定位
        clearFile("navi_module.log");
        writeFile("navi_module.log", "===== Navi-Link 模块系统启动 @ " + time() + " =====");

        // 全局崩溃捕获：堆栈写入文件后交还原系统默认行为（不改变崩溃表现）
        final Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            writeCrash(throwable);
            if (original != null) {
                original.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
    }

    public static void d(String msg) {
        if (!enabled) return;
        Log.d(TAG, msg);
        writeFile("navi_module.log", "D " + time() + " " + msg);
    }

    public static void e(String msg, Throwable t) {
        if (!enabled) return;
        Log.e(TAG, msg, t);
        writeFile("navi_module.log", "E " + time() + " " + msg + "\n" + stack(t));
    }

    /** 高频数据节流日志：每 THROTTLE_MS 最多输出一条（线程安全） */
    public static synchronized void dThrottle(String msg) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (now - lastThrottleTime >= THROTTLE_MS) {
            Log.d(TAG, msg);
            writeFile("navi_module.log", "D " + time() + " " + msg);
            lastThrottleTime = now;
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // ======================== 文件日志 ========================

    private static void writeCrash(Throwable t) {
        writeFile("navi_crash.log", "\n===== " + time() + " CRASH =====\n" + stack(t));
    }

    private static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    private static String time() {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static synchronized void writeFile(String name, String content) {
        if (appContext == null) return;
        try {
            File dir = appContext.getExternalFilesDir(null);
            if (dir == null) return;
            File f = new File(dir, name);
            BufferedWriter w = new BufferedWriter(new FileWriter(f, true));
            w.write(content);
            w.newLine();
            w.close();
        } catch (Exception ignored) {
        }
    }

    private static synchronized void clearFile(String name) {
        if (appContext == null) return;
        try {
            File dir = appContext.getExternalFilesDir(null);
            if (dir == null) return;
            File f = new File(dir, name);
            if (f.exists() && !f.delete()) {
                // 删除失败则截断
                try {
                    new FileWriter(f).close();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
