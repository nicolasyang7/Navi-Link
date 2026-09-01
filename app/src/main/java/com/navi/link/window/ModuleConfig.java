package com.navi.link.window;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 副屏自定义模块配置数据模型（JSON持久化）
 * V12：扩展 length（TMC长度因子）与 orientation（TMC横竖方向）
 */
public class ModuleConfig {
    public String instanceId;
    public String moduleId;
    public float scale = 1.0f;
    public float x = 0f;
    public float y = 0f;
    public float length = 1.0f;      // TMC专属：长度因子 0.25~3.0
    public int orientation = 0;      // TMC专属：0=横 1=竖

    private static final String PREFS_KEY = "custom_display_modules";

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
        return moduleId + "_" + System.currentTimeMillis();
    }

    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("instanceId", instanceId);
            obj.put("moduleId", moduleId);
            obj.put("scale", scale);
            obj.put("x", x);
            obj.put("y", y);
            obj.put("length", length);
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
        cfg.orientation = obj.optInt("orientation", 0);
        return cfg;
    }

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

    public static void saveAll(Context context, List<ModuleConfig> configs) {
        try {
            JSONArray arr = new JSONArray();
            for (ModuleConfig cfg : configs) {
                arr.put(cfg.toJson());
            }
            context.getSharedPreferences("floating_config", Context.MODE_PRIVATE)
                    .edit().putString(PREFS_KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
