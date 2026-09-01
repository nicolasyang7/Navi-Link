package com.navi.link.window;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.navi.link.utils.CustomLog;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * 副屏唯一窗口（模块自定义系统）V12
 * 继承关系 / 生命周期 / 数据更新方法名 保持不变。
 * 内部实现：动态模块容器（ModulePreviewContainer，轻量级、不依赖 FWM 实例）。
 * 配置变化（SharedPreferences）自动重建模块列表 —— 模拟屏操作实时/重启同步到真实副屏。
 */
public class CustomDisplayWindow extends BaseFloatingWindow implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PREFS_NAME = "floating_config";
    private static final String PREFS_MODULES_KEY = "custom_display_modules";

    private FrameLayout moduleContainer;
    private final List<ModulePreviewContainer> modules = new ArrayList<>();

    // 数据缓存：新添加模块立即用最近数据填充
    private int cacheSpeed = 0;
    private String cacheRoadName = "";
    private int cacheLimitedSpeed = 0;
    private int cacheCameraType = 0;
    private int cacheCameraDist = 0;
    private int cacheCameraSpeed = 0;
    private int cacheIcon = -1;
    private String cacheDisNum = "";
    private String cacheDisUnit = "";
    private String cacheEta = "";
    private String cacheExitName = "";
    private String cacheDriveWayJson = null;
    private String cacheTmcJson = null;
    private JSONArray cacheCruiseLights = null;
    private int cacheLightStatus = -1;
    private int cacheLightDir = -1;
    private int cacheLightCountdown = 0;

    private SharedPreferences prefs;
    private boolean destroyed = false;

    public CustomDisplayWindow(Context context, View floatingView) {
        super(context, floatingView);
    }

    @Override
    protected void initViews() {
        try {
            moduleContainer = new FrameLayout(context);
            moduleContainer.setClipChildren(false);
            moduleContainer.setClipToPadding(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            ((ViewGroup) floatingView).addView(moduleContainer, lp);

            // 注册配置监听：模拟屏写配置 → 真实副屏自动重建（配置驱动，无需 FWM 写方法）
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(this);

            List<ModuleConfig> configs = ModuleConfig.loadAll(context);
            CustomLog.d("[副屏窗口]恢复模块配置: " + configs.size() + "个");
            for (ModuleConfig cfg : configs) {
                addModuleInstance(cfg);
            }
        } catch (Exception e) {
            CustomLog.e("[副屏窗口]initViews异常，已降级为空窗口", e);
        }
    }

    // ========================模块管理（内部） ========================

    private void addModuleInstance(ModuleConfig cfg) {
        try {
            ModulePreviewContainer mc = new ModulePreviewContainer(context, cfg);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Math.round(cfg.x);
            lp.topMargin = Math.round(cfg.y);
            moduleContainer.addView(mc, lp);
            modules.add(mc);
            refreshModuleWithCache(mc);
        } catch (Exception e) {
            CustomLog.e("[副屏窗口]模块实例化失败: " + (cfg != null ? cfg.moduleId : "null"), e);
        }
    }

    /** 配置变化：重建全部模块（模拟屏操作实时同步） */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (destroyed) return;
        if (PREFS_MODULES_KEY.equals(key)) {
            reloadModules();
        }
    }

    private void reloadModules() {
        try {
            CustomLog.d("[副屏窗口]配置变化，重建模块列表");
            for (ModulePreviewContainer mc : modules) {
                moduleContainer.removeView(mc);
            }
            modules.clear();
            List<ModuleConfig> configs = ModuleConfig.loadAll(context);
            for (ModuleConfig cfg : configs) {
                addModuleInstance(cfg);
            }
        } catch (Exception e) {
            CustomLog.e("[副屏窗口]reloadModules异常", e);
        }
    }

    private boolean isCruiseMode() {
        FloatingWindowManager fwm = FloatingWindowManager.getInstance();
        return fwm != null && fwm.getCurrentMode() == FloatingWindowManager.MODE_CRUISE;
    }

    // ========================数据分发（方法名不变） ========================

    @Override
    public void updateCruiseInfo(int speed, String roadName, int cameraType, int cameraSpeed, int cameraDist, int carDirection) {
        cacheSpeed = speed;
        cacheRoadName = roadName == null ? "" : roadName;
        cacheLimitedSpeed = cameraSpeed > 0 ? cameraSpeed : cacheLimitedSpeed;
        cacheCameraType = cameraType;
        cacheCameraDist = cameraDist;
        cacheCameraSpeed = cameraSpeed;

        boolean cruise = isCruiseMode();
        for (ModulePreviewContainer mc : modules) {
            String mid = mc.getConfig().moduleId;
            if ("module_speed".equals(mid)) {
                mc.setPreviewData(1, String.valueOf(speed));
            } else if ("module_road_name".equals(mid) || ("module_cruise_road_name".equals(mid) && cruise)) {
                mc.setPreviewData(2, cacheRoadName);
            } else if ("module_speed_limit".equals(mid)) {
                mc.setPreviewData(9, cameraSpeed);
            } else if ("module_camera_distance".equals(mid)) {
                mc.setPreviewData(10, new int[]{cameraType, cameraDist, cameraSpeed});
            }
        }
    }

    @Override
    public void updateNaviInfo(int icon, String disNum, String disUnit, String actionStr,
                               String roadName, String summaryStr, String eta,
                               int progress, int curSpeed,
                               int limitedSpeed, int cameraType, int cameraDist, int cameraSpeed,
                               String endPoiName, int totalLightNum, int remainLightNum,
                               String curRoadName, int carDirection) {
        cacheSpeed = curSpeed;
        cacheRoadName = roadName == null ? "" : roadName;
        cacheLimitedSpeed = limitedSpeed;
        cacheIcon = icon;
        cacheDisNum = disNum == null ? "" : disNum;
        cacheDisUnit = disUnit == null ? "" : disUnit;
        cacheEta = eta == null ? "" : eta;
        cacheCameraType = cameraType;
        cacheCameraDist = cameraDist;
        cacheCameraSpeed = cameraSpeed;

        for (ModulePreviewContainer mc : modules) {
            String mid = mc.getConfig().moduleId;
            if ("module_speed".equals(mid)) {
                mc.setPreviewData(1, String.valueOf(curSpeed));
            } else if ("module_road_name".equals(mid)) {
                mc.setPreviewData(2, cacheRoadName);
            } else if ("module_turn_icon".equals(mid)) {
                mc.setPreviewData(11, getTurnIconRes(icon));
            } else if ("module_turn_distance".equals(mid)) {
                mc.setPreviewData(12, new Object[]{cacheDisNum, cacheDisUnit});
            } else if ("module_eta".equals(mid)) {
                mc.setPreviewData(7, formatEtaSpannable(cacheEta));
            } else if ("module_speed_limit".equals(mid)) {
                mc.setPreviewData(9, limitedSpeed);
            } else if ("module_camera_distance".equals(mid)) {
                mc.setPreviewData(10, new int[]{cameraType, cameraDist, cameraSpeed});
            }
        }
    }

    @Override
    public void updateTrafficLight(int status, int dir, int countdown) {
        cacheLightStatus = status;
        cacheLightDir = dir;
        cacheLightCountdown = countdown;
        for (ModulePreviewContainer mc : modules) {
            if ("module_traffic_light".equals(mc.getConfig().moduleId)) {
                mc.setPreviewData(4, new int[]{status, dir, countdown});
            }
        }
    }

    @Override
    public void updateCruiseTrafficLights(JSONArray lightsArray) {
        cacheCruiseLights = lightsArray;
        for (ModulePreviewContainer mc : modules) {
            String mid = mc.getConfig().moduleId;
            if ("module_traffic_light".equals(mid) || "module_cruise_traffic_light".equals(mid)) {
                mc.setPreviewData(5, lightsArray);
            }
        }
    }

    @Override
    public void updateLaneLines(String driveWayJson) {
        cacheDriveWayJson = driveWayJson;
        boolean cruise = isCruiseMode();
        for (ModulePreviewContainer mc : modules) {
            String mid = mc.getConfig().moduleId;
            if ("module_lane_line".equals(mid) || ("module_cruise_lane_line".equals(mid) && cruise)) {
                mc.setPreviewData(3, driveWayJson);
            }
        }
    }

    @Override
    public void updateTmcData(String tmcJson) {
        cacheTmcJson = tmcJson;
        for (ModulePreviewContainer mc : modules) {
            if ("module_tmc_progress".equals(mc.getConfig().moduleId)) {
                mc.setPreviewData(6, tmcJson);
            }
        }
    }

    @Override
    public void updateExitInfo(String exitName, String exitDirection) {
        cacheExitName = exitName == null ? "" : exitName.trim();
        for (ModulePreviewContainer mc : modules) {
            if ("module_exit_info".equals(mc.getConfig().moduleId)) {
                mc.setPreviewData(8, cacheExitName);
            }
        }
    }

    // ========================缓存刷新 ========================

    private void refreshModuleWithCache(ModulePreviewContainer mc) {
        String mid = mc.getConfig().moduleId;
        if ("module_speed".equals(mid)) {
            mc.setPreviewData(1, String.valueOf(cacheSpeed));
        } else if ("module_road_name".equals(mid) || "module_cruise_road_name".equals(mid)) {
            mc.setPreviewData(2, cacheRoadName);
        } else if ("module_turn_icon".equals(mid)) {
            mc.setPreviewData(11, getTurnIconRes(cacheIcon));
        } else if ("module_turn_distance".equals(mid)) {
            mc.setPreviewData(12, new Object[]{cacheDisNum, cacheDisUnit});
        } else if ("module_eta".equals(mid)) {
            mc.setPreviewData(7, formatEtaSpannable(cacheEta));
        } else if ("module_exit_info".equals(mid)) {
            if (!cacheExitName.isEmpty()) mc.setPreviewData(8, cacheExitName);
        } else if ("module_speed_limit".equals(mid)) {
            mc.setPreviewData(9, cacheLimitedSpeed);
        } else if ("module_camera_distance".equals(mid)) {
            mc.setPreviewData(10, new int[]{cacheCameraType, cacheCameraDist, cacheCameraSpeed});
        } else if ("module_lane_line".equals(mid) || "module_cruise_lane_line".equals(mid)) {
            if (cacheDriveWayJson != null) mc.setPreviewData(3, cacheDriveWayJson);
        } else if ("module_tmc_progress".equals(mid)) {
            if (cacheTmcJson != null) mc.setPreviewData(6, cacheTmcJson);
        } else if ("module_traffic_light".equals(mid)) {
            if (cacheCruiseLights != null) mc.setPreviewData(5, cacheCruiseLights);
            else if (cacheLightCountdown > 0) mc.setPreviewData(4, new int[]{cacheLightStatus, cacheLightDir, cacheLightCountdown});
        } else if ("module_cruise_traffic_light".equals(mid)) {
            if (cacheCruiseLights != null) mc.setPreviewData(5, cacheCruiseLights);
        }
    }

    // ========================主题（简化：文字颜色） ========================

    @Override
    public void applyThemeColor(int themeColor) {
        applyDayNightTextColors(isNightMode);
    }

    @Override
    public void applyDayNightTextColors(boolean isNightMode) {
        this.isNightMode = isNightMode;
    }

    @Override
    public void resetToDefaultTextColors() {
    }

    // ========================生命周期（不变） ========================

    @Override
    public void updateSapaInfo(String sapaName, String sapaDist, int sapaType, String nextSapaName, String nextSapaDist, int nextSapaType) {
        // 暂无对应模块
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        try {
            if (prefs != null) {
                prefs.unregisterOnSharedPreferenceChangeListener(this);
            }
        } catch (Exception ignored) {
        }
        for (ModulePreviewContainer mc : modules) {
            moduleContainer.removeView(mc);
            mc.setOnConfigChangeListener(null);
            mc.removeAllViews();
        }
        modules.clear();
        super.onDestroy();
    }
}
