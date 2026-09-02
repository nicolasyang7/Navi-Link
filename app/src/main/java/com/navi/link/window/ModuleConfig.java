package com.navi.link.window;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**副屏自定义模块配置数据模型（JSON持久化，含 NaN 防护） */
public class ModuleConfig {
 public String instanceId;
 public String moduleId;
 public float scale = 1.0f;
 public float x = 0f;
 public float y = 0f;
 public float length = 1.0f;
 public boolean orientation = false;

 private static final String PREFS_KEY = "custom_display_modules";
 private static final AtomicLong ID_COUNTER = new AtomicLong(0);

 public ModuleConfig() {}

 public ModuleConfig(String instanceId, String moduleId, float scale, float x, float y) {
 this.instanceId = instanceId;
 this.moduleId = moduleId;
 this.scale = scale;
 this.x = x;
 this.y = y;
 }

 public static String newInstanceId(String moduleId) {
 return moduleId + "_" + System.currentTimeMillis() + "_" + ID_COUNTER.incrementAndGet();
 }

 public JSONObject toJson() {
 try {
 JSONObject obj = new JSONObject();
 obj.put("instanceId", instanceId == null ? "" : instanceId);
 obj.put("moduleId", moduleId == null ? "" : moduleId);
 obj.put("scale", Float.isNaN(scale) ? 1.0f : scale);
 obj.put("x", Float.isNaN(x) ? 0f : x);
 obj.put("y", Float.isNaN(y) ? 0f : y);
 obj.put("length", Float.isNaN(length) ? 1.0f : length);
 obj.put("orientation", orientation);
 return obj;
 } catch (Exception e) {
 return new JSONObject();
 }
 }

 public static ModuleConfig fromJson(JSONObject obj) {
 ModuleConfig cfg = new ModuleConfig();
 cfg.instanceId = obj.optString("instanceId", "");
 cfg.moduleId = obj.optString("moduleId", "");
 cfg.scale = (float) obj.optDouble("scale", 1.0f);
 cfg.x = (float) obj.optDouble("x", 0f);
 cfg.y = (float) obj.optDouble("y", 0f);
 cfg.length = (float) obj.optDouble("length", 1.0f);
 cfg.orientation = obj.optBoolean("orientation", false);
 if (Float.isNaN(cfg.scale)) cfg.scale = 1.0f;
 if (Float.isNaN(cfg.x)) cfg.x = 0f;
 if (Float.isNaN(cfg.y)) cfg.y = 0f;
 if (Float.isNaN(cfg.length)) cfg.length = 1.0f;
 return cfg;
 }

 public static List<ModuleConfig> loadAll(Context context) {
 List<ModuleConfig> list = new ArrayList<>();
 try {
 SharedPreferences sp = context.getSharedPreferences("floating_config", Context.MODE_PRIVATE);
 String raw = sp.getString(PREFS_KEY, "");
 if (raw == null || raw.isEmpty()) return list;
 JSONArray arr = new JSONArray(raw);
 //[V13.13]按 instanceId 去重（修复移动后重复导致一个变两个）
 java.util.LinkedHashMap<String, ModuleConfig> map = new java.util.LinkedHashMap<>();
 for (int i = 0; i < arr.length(); i++) {
 ModuleConfig cfg = fromJson(arr.getJSONObject(i));
 if (cfg.moduleId != null && !cfg.moduleId.isEmpty()) {
 if (cfg.instanceId == null || cfg.instanceId.isEmpty()) {
 cfg.instanceId = newInstanceId(cfg.moduleId);
 }
 map.put(cfg.instanceId, cfg);
 }
 }
 list.addAll(map.values());
 } catch (Exception e) {
 //配置解析失败：返回空（不删除配置，避免误清）
 }
 return list;
 }

 public static void saveAll(Context context, List<ModuleConfig> configs) {
 try {
 JSONArray arr = new JSONArray();
 if (configs != null) {
 //[V13.13]保存前按 instanceId 去重，双重保险
 java.util.LinkedHashMap<String, ModuleConfig> map = new java.util.LinkedHashMap<>();
 for (ModuleConfig cfg : configs) {
 if (cfg != null) {
 if (cfg.instanceId == null || cfg.instanceId.isEmpty()) {
 cfg.instanceId = newInstanceId(cfg.moduleId);
 }
 map.put(cfg.instanceId, cfg);
 }
 }
 for (ModuleConfig cfg : map.values()) {
 arr.put(cfg.toJson());
 }
 }
 context.getSharedPreferences("floating_config", Context.MODE_PRIVATE).edit()
 .putString(PREFS_KEY, arr.toString()).apply();
 } catch (Exception ignored) {
 }
 }
}
