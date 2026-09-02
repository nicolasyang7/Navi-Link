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

/**副屏模块系统统一日志工具：logcat + 文件双通道（车机无 adb 时用文件） */
public class CustomLog {
 public static final String TAG = "NaviLinkModule";
 private static boolean enabled = true;
 private static Context appContext = null;

 private static final long THROTTLE_MS = 3000;
 private static long lastThrottleTime = 0;

 public static void init(Context context) {
 if (context == null || appContext != null) return;
 appContext = context.getApplicationContext();
 clearFile("navi_module.log");
 Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
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

 /**高频数据节流日志 */
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

 private static void writeCrash(Throwable t) {
 writeFile("navi_crash.log", "\n===== " + time() + " CRASH =====\n" + stack(t));
 }

 private static String stack(Throwable t) {
 if (t == null) return "";
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
 if (f.exists()) f.delete();
 } catch (Exception ignored) {
 }
 }
}
