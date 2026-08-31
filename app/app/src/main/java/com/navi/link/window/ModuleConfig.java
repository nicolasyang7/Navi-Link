package com.navi.link.window;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 副屏自定义模块的配置数据模型（SharedPreferences JSON 持久化）
 */
public class ModuleConfig {
    public String instanceId;   // 实例唯一 ID，如 module_speed_1700000000000
    public String moduleId;     // 模块类型 ID，见 ModuleRegistry
    public float scale = 1.0f;  // 缩放比例 0.25 ~ 3.0
    public float x = 0f;        // X 坐标（相对副屏容器）
    public float y = 0f;        // Y 坐标（相对副屏容器）

    private static final String PREFS_KEY = "custom_display_modules";

    /** 实例ID自增计数器：避免同一毫秒内添加多个模块导致 ID 重复 */
    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    public ModuleConfig() {
    }

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
        JSONObject obj = new JSONObject();
        try {
            obj.put("instanceId", instanceId);
            obj.put("moduleId", moduleId);
            obj.put("scale", scale);
            obj.put("x", x);
            obj.put("y", y);
        } catch (Exception ignored) {
        }
        return obj;
    }

    public static ModuleConfig fromJson(JSONObject obj) {
        ModuleConfig cfg = new ModuleConfig();
        cfg.instanceId = obj.optString("instanceId", "");
        cfg.moduleId = obj.optString("moduleId", "");
        cfg.scale = (float) obj.optDouble("scale", 1.0f);
        cfg.x = (float) obj.optDouble("x", 0f);
        cfg.y = (float) obj.optDouble("y", 0f);
        return cfg;
    }

    /** 读取全部模块配置（应用重启后恢复） */
    public static List<ModuleConfig> loadAll(Context context) {
        List<ModuleConfig> list = new ArrayList<>();
        try {
            SharedPreferences sp = context.getSharedPreferences("floating_config", Context.MODE_PRIVATE);
            String raw = sp.getString(PREFS_KEY, "");
            if (raw == null || raw.isEmpty()) return list;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                ModuleConfig cfg = fromJson(arr.getJSONObject(i));
                if (cfg.moduleId != null && !cfg.moduleId.isEmpty()) {
                    list.add(cfg);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /** 保存全部模块配置 */
    public static void saveAll(Context context, List<ModuleConfig> configs) {
        try {
            JSONArray arr = new JSONArray();
            for (ModuleConfig cfg : configs) {
                arr.put(cfg.toJson());
            }
            context.getSharedPreferences("floating_config", Context.MODE_PRIVATE).edit()
                    .putString(PREFS_KEY, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }
}
