package com.navi.link.window;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.navi.link.R;
import com.navi.link.view.CameraWarningView;
import com.navi.link.view.LaneLineView;
import com.navi.link.view.TmcProgressBar;
import com.navi.link.view.TrafficLightView;
import com.navi.link.utils.CustomLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 副屏唯一窗口（模块自定义系统）
 * 所有自定义模块（可重复添加、独立实例）在此窗口内统一管理：
 * 添加 / 删除 / 拖动 / 缩放 / 数据分发，配置 JSON 持久化，重启自动恢复。
 * 数据流：高德广播 → AmapNaviReceiver → FloatingWindowManager → CustomDisplayWindow → 各模块
 */
public class CustomDisplayWindow extends BaseFloatingWindow {

    private FrameLayout moduleContainer;
    private final List<ScalableModuleContainer> modules = new ArrayList<>();

    /** 模块数量上限：防止无限重复添加耗尽内存 */
    private static final int MAX_MODULES = 30;

    /** 配置保存防抖：300ms 内多次变化只写盘一次，避免频繁 I/O */
    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = this::doSaveConfigs;

    // ---- 数据缓存：新添加的模块立即用最近一次数据填充 ----
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

    public CustomDisplayWindow(Context context, View floatingView) {
        super(context, floatingView);
    }

    @Override
    protected void initViews() {
        CustomLog.d("[副屏窗口] initViews: 创建模块容器");
        moduleContainer = new FrameLayout(context);
        moduleContainer.setClipChildren(false);
        moduleContainer.setClipToPadding(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        ((ViewGroup) floatingView).addView(moduleContainer, lp);

        // 恢复已保存的模块配置
        List<ModuleConfig> configs = ModuleConfig.loadAll(context);
        CustomLog.d("[副屏窗口] 恢复模块配置: " + configs.size() + " 个");
        for (ModuleConfig cfg : configs) {
            addModuleInstance(cfg);
        }
    }

    // ======================== 模块管理 ========================

    /** 添加一个模块（可重复添加，每次新实例） */
    public void addModule(String moduleId) {
        if (ModuleRegistry.get(moduleId) == null) {
            CustomLog.d("[副屏窗口] 添加失败: 未知模块ID " + moduleId);
            return;
        }
        if (modules.size() >= MAX_MODULES) {
            CustomLog.d("[副屏窗口] 模块数量已达上限 " + MAX_MODULES);
            try {
                Toast.makeText(context, "模块数量已达上限(" + MAX_MODULES + ")", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
            }
            return;
        }
        ModuleConfig cfg = new ModuleConfig(ModuleConfig.newInstanceId(moduleId), moduleId, 1.0f, 0f, 0f);
        addModuleInstance(cfg);
        scheduleSave();
    }

    /** 删除指定实例 */
    public void removeModule(String instanceId) {
        CustomLog.d("[副屏窗口] 移除模块实例: " + instanceId);
        ScalableModuleContainer target = null;
        for (ScalableModuleContainer mc : modules) {
            if (mc.getConfig().instanceId != null && mc.getConfig().instanceId.equals(instanceId)) {
                target = mc;
                break;
            }
        }
        if (target != null) {
            modules.remove(target);
            moduleContainer.removeView(target);
            // 断开监听回调 + 释放子 View，避免内存泄漏
            target.setOnConfigChangeListener(null);
            target.removeAllViews();
            scheduleSave();
        }
    }

    private void addModuleInstance(ModuleConfig cfg) {
        CustomLog.d("[副屏窗口] 实例化模块: " + cfg.moduleId + " 实例=" + cfg.instanceId + " scale=" + cfg.scale + " pos=(" + Math.round(cfg.x) + "," + Math.round(cfg.y) + ")");
        ScalableModuleContainer mc = new ScalableModuleContainer(context, cfg);
        mc.setOnConfigChangeListener(conf -> saveConfigs());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Math.round(cfg.x);
        lp.topMargin = Math.round(cfg.y);
        moduleContainer.addView(mc, lp);
        modules.add(mc);
        refreshModuleWithCache(mc);
    }

    /** 防抖保存：多次变化 300ms 合并为一次写盘 */
    private void scheduleSave() {
        saveHandler.removeCallbacks(saveRunnable);
        saveHandler.postDelayed(saveRunnable, 300);
    }

    private void doSaveConfigs() {
        List<ModuleConfig> list = new ArrayList<>();
        for (ScalableModuleContainer mc : modules) {
            list.add(mc.getConfig());
        }
        ModuleConfig.saveAll(context, list);
    }

    private boolean isCruiseMode() {
        FloatingWindowManager fwm = FloatingWindowManager.getInstance();
        return fwm != null && fwm.getCurrentMode() == FloatingWindowManager.MODE_CRUISE;
    }

    // ======================== 数据分发 ========================

    @Override
    public void updateCruiseInfo(int speed, String roadName, int cameraType, int cameraSpeed, int cameraDist, int carDirection) {
        cacheSpeed = speed;
        cacheRoadName = roadName == null ? "" : roadName;
        if (cameraSpeed > 0) cacheLimitedSpeed = cameraSpeed;
        cacheCameraType = cameraType;
        cacheCameraDist = cameraDist;
        cacheCameraSpeed = cameraSpeed;

        boolean cruise = isCruiseMode();
        CustomLog.dThrottle("[数据] 巡航: 速度=" + speed + " 道路=" + cacheRoadName + " 限速=" + cameraSpeed + " 模块数=" + modules.size());
        for (ScalableModuleContainer mc : modules) {
            String mid = mc.getConfig().moduleId;
            if ("module_speed".equals(mid)) {
                setText(mc, R.id.tv_module_speed, String.valueOf(speed));
            } else if ("module_road_name".equals(mid) || ("module_cruise_road_name".equals(mid) && cruise)) {
                setText(mc, R.id.tv_module_road_name, cacheRoadName);
            } else if ("module_speed_limit".equals(mid)) {
                updateSpeedLimitModule(mc, cameraSpeed);
            } else if ("module_camera_distance".equals(mid)) {
                updateCameraModule(mc, cameraType, cameraDist, cameraSpeed);
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

        CustomLog.dThrottle("[数据] 导航: 图标=" + icon + " 距离=" + cacheDisNum + cacheDisUnit + " 速度=" + curSpeed + " 限速=" + limitedSpeed + " 模块数=" + modules.size());
        for (ScalableModuleContainer mc : modules) {
            String mid = mc.getConfig().moduleId;
            if ("module_speed".equals(mid)) {
                setText(mc, R.id.tv_module_speed, String.valueOf(curSpeed));
            } else if ("module_road_name".equals(mid)) {
                setText(mc, R.id.tv_module_road_name, cacheRoadName);
            } else if ("module_turn_icon".equals(mid)) {
                updateTurnIconModule(mc, icon);
            } else if ("module_turn_distance".equals(mid)) {
                setText(mc, R.id.tv_module_distance_num, cacheDisNum);
                setText(mc, R.id.tv_module_distance_unit, disNumIsNow(cacheDisNum) ? "" : cacheDisUnit);
            } else if ("module_eta".equals(mid)) {
                setText(mc, R.id.tv_module_eta, formatEtaSpannable(cacheEta));
            } else if ("module_speed_limit".equals(mid)) {
                updateSpeedLimitModule(mc, limitedSpeed);
            } else if ("module_camera_distance".equals(mid)) {
                updateCameraModule(mc, cameraType, cameraDist, cameraSpeed);
            }
        }
    }

    @Override
    public void updateTrafficLight(int status, int dir, int countdown) {
        CustomLog.dThrottle("[数据] 导航单灯: status=" + status + " dir=" + dir + " 倒计时=" + countdown);
        cacheLightStatus = status;
        cacheLightDir = dir;
        cacheLightCountdown = countdown;
        for (ScalableModuleContainer mc : modules) {
            if ("module_traffic_light".equals(mc.getConfig().moduleId)) {
                updateSingleTrafficLight(mc, status, dir, countdown);
            }
        }
    }

    @Override
    public void updateCruiseTrafficLights(JSONArray lightsArray) {
        CustomLog.dThrottle("[数据] 巡航红绿灯: 灯数=" + (lightsArray == null ? 0 : lightsArray.length()));
        cacheCruiseLights = lightsArray;
        for (ScalableModuleContainer mc : modules) {
            String mid = mc.getConfig().moduleId;
            if ("module_traffic_light".equals(mid) || "module_cruise_traffic_light".equals(mid)) {
                updateCruiseTrafficLightModule(mc, lightsArray);
            }
        }
    }

    @Override
    public void updateLaneLines(String driveWayJson) {
        String brief = driveWayJson != null && driveWayJson.length() > 60 ? driveWayJson.substring(0, 60) : driveWayJson;
        CustomLog.dThrottle("[数据] 车道线: " + brief);
        cacheDriveWayJson = driveWayJson;
        boolean cruise = isCruiseMode();
        for (ScalableModuleContainer mc : modules) {
            String mid = mc.getConfig().moduleId;
            if ("module_lane_line".equals(mid) || ("module_cruise_lane_line".equals(mid) && cruise)) {
                LaneLineView lv = mc.findViewById(R.id.lane_line_module);
                if (lv != null) {
                    lv.updateLanes(driveWayJson);
                }
            }
        }
    }

    @Override
    public void updateTmcData(String tmcJson) {
        String brief = tmcJson != null && tmcJson.length() > 60 ? tmcJson.substring(0, 60) : tmcJson;
        CustomLog.dThrottle("[数据] TMC: " + brief);
        cacheTmcJson = tmcJson;
        for (ScalableModuleContainer mc : modules) {
            if ("module_tmc_progress".equals(mc.getConfig().moduleId)) {
                TmcProgressBar bar = mc.findViewById(R.id.tmc_module);
                if (bar != null) {
                    bar.updateTmcData(tmcJson);
                }
            }
        }
    }

    @Override
    public void updateExitInfo(String exitName, String exitDirection) {
        cacheExitName = exitName == null ? "" : exitName.trim();
        CustomLog.d("[数据] 出口信息: " + cacheExitName);
        for (ScalableModuleContainer mc : modules) {
            if ("module_exit_info".equals(mc.getConfig().moduleId)) {
                View v = mc.findViewById(R.id.tv_module_exit_info);
                if (v != null) {
                    if (cacheExitName.isEmpty()) {
                        v.setVisibility(View.GONE);
                    } else {
                        ((TextView) v).setText(cacheExitName);
                        v.setVisibility(View.VISIBLE);
                    }
                }
            }
        }
    }

    // ======================== 模块更新辅助 ========================

    /** 新添加模块时用缓存数据立即填充 */
    private void refreshModuleWithCache(ScalableModuleContainer mc) {
        String mid = mc.getConfig().moduleId;
        CustomLog.dThrottle("[副屏窗口] 缓存刷新模块: " + mid);
        if ("module_speed".equals(mid)) {
            setText(mc, R.id.tv_module_speed, String.valueOf(cacheSpeed));
        } else if ("module_road_name".equals(mid) || "module_cruise_road_name".equals(mid)) {
            setText(mc, R.id.tv_module_road_name, cacheRoadName);
        } else if ("module_turn_icon".equals(mid)) {
            updateTurnIconModule(mc, cacheIcon);
        } else if ("module_turn_distance".equals(mid)) {
            setText(mc, R.id.tv_module_distance_num, cacheDisNum);
            setText(mc, R.id.tv_module_distance_unit, cacheDisUnit);
        } else if ("module_eta".equals(mid)) {
            setText(mc, R.id.tv_module_eta, formatEtaSpannable(cacheEta));
        } else if ("module_exit_info".equals(mid)) {
            if (!cacheExitName.isEmpty()) {
                setText(mc, R.id.tv_module_exit_info, cacheExitName);
            }
        } else if ("module_speed_limit".equals(mid)) {
            updateSpeedLimitModule(mc, cacheLimitedSpeed);
        } else if ("module_camera_distance".equals(mid)) {
            updateCameraModule(mc, cacheCameraType, cacheCameraDist, cacheCameraSpeed);
        } else if ("module_lane_line".equals(mid) || "module_cruise_lane_line".equals(mid)) {
            if (cacheDriveWayJson != null) {
                LaneLineView lv = mc.findViewById(R.id.lane_line_module);
                if (lv != null) lv.updateLanes(cacheDriveWayJson);
            }
        } else if ("module_tmc_progress".equals(mid)) {
            if (cacheTmcJson != null) {
                TmcProgressBar bar = mc.findViewById(R.id.tmc_module);
                if (bar != null) bar.updateTmcData(cacheTmcJson);
            }
        } else if ("module_traffic_light".equals(mid)) {
            if (cacheCruiseLights != null && cacheCruiseLights.length() > 0) {
                updateCruiseTrafficLightModule(mc, cacheCruiseLights);
            } else if (cacheLightCountdown > 0) {
                updateSingleTrafficLight(mc, cacheLightStatus, cacheLightDir, cacheLightCountdown);
            }
        } else if ("module_cruise_traffic_light".equals(mid)) {
            if (cacheCruiseLights != null && cacheCruiseLights.length() > 0) {
                updateCruiseTrafficLightModule(mc, cacheCruiseLights);
            }
        }
    }

    private void setText(ScalableModuleContainer mc, int viewId, CharSequence text) {
        View v = mc.findViewById(viewId);
        if (v instanceof TextView) {
            ((TextView) v).setText(text);
        }
    }

    private void updateTurnIconModule(ScalableModuleContainer mc, int icon) {
        ImageView iv = mc.findViewById(R.id.iv_module_turn_icon);
        if (iv == null) return;
        int res = getTurnIconRes(icon);
        if (res != 0) iv.setImageResource(res);
    }

    private void updateSpeedLimitModule(ScalableModuleContainer mc, int limitSpeed) {
        TextView tv = mc.findViewById(R.id.tv_module_speed_limit);
        if (tv == null) return;
        if (limitSpeed > 0) {
            tv.setText(String.valueOf(limitSpeed));
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private void updateCameraModule(ScalableModuleContainer mc, int cameraType, int cameraDist, int cameraSpeed) {
        CameraWarningView cv = mc.findViewById(R.id.camera_module);
        if (cv != null) {
            cv.updateCameraInfo(cameraType, cameraDist, cameraSpeed);
        }
    }

    /** 导航单灯红绿灯 */
    private void updateSingleTrafficLight(ScalableModuleContainer mc, int status, int dir, int countdown) {
        LinearLayout container = mc.findViewById(R.id.ll_module_traffic_light);
        if (container == null) return;
        container.removeAllViews();
        if (countdown <= 0) {
            container.setVisibility(View.GONE);
            return;
        }
        TrafficLightView lv = new TrafficLightView(context);
        container.addView(lv);
        lv.setData(status, dir, countdown, true);
        container.setVisibility(View.VISIBLE);
    }

    /** 巡航多灯红绿灯 */
    private void updateCruiseTrafficLightModule(ScalableModuleContainer mc, JSONArray lightsArray) {
        LinearLayout container = mc.findViewById(R.id.ll_module_traffic_light);
        if (container == null) return;
        container.removeAllViews();
        int count = lightsArray != null ? lightsArray.length() : 0;
        if (count == 0) {
            container.setVisibility(View.GONE);
            return;
        }
        for (int i = 0; i < count; i++) {
            try {
                JSONObject o = lightsArray.getJSONObject(i);
                int status = o.getInt("status");
                int countdown = o.getInt("countdown");
                int dir = o.getInt("dir");
                TrafficLightView lv = new TrafficLightView(context);
                container.addView(lv);
                if (countdown > 0) {
                    lv.setData(status, dir, countdown, false);
                } else {
                    lv.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                CustomLog.e("[数据] 巡航红绿灯解析失败", e);
            }
        }
        container.setVisibility(View.VISIBLE);
    }

    // ======================== 主题（简化：仅文字颜色跟随） ========================

    @Override
    public void applyThemeColor(int themeColor) {
        applyDayNightTextColors(isNightMode);
    }

    @Override
    public void applyDayNightTextColors(boolean isNightMode) {
        this.isNightMode = isNightMode;
        int primary = getPrimaryTextColor(isNightMode);
        for (ScalableModuleContainer mc : modules) {
            tintText(mc, R.id.tv_module_speed, primary);
            tintText(mc, R.id.tv_module_road_name, primary);
            tintText(mc, R.id.tv_module_distance_num, primary);
            tintText(mc, R.id.tv_module_distance_unit, primary);
            tintText(mc, R.id.tv_module_eta, primary);
            tintText(mc, R.id.tv_module_exit_info, primary);
        }
    }

    @Override
    public void resetToDefaultTextColors() {
        for (ScalableModuleContainer mc : modules) {
            tintText(mc, R.id.tv_module_speed, TEXT_PRIMARY_DARK);
            tintText(mc, R.id.tv_module_road_name, TEXT_PRIMARY_DARK);
            tintText(mc, R.id.tv_module_distance_num, TEXT_PRIMARY_DARK);
            tintText(mc, R.id.tv_module_distance_unit, TEXT_PRIMARY_DARK);
            tintText(mc, R.id.tv_module_eta, TEXT_PRIMARY_DARK);
            tintText(mc, R.id.tv_module_exit_info, TEXT_PRIMARY_DARK);
        }
    }

    private void tintText(ScalableModuleContainer mc, int viewId, int color) {
        View v = mc.findViewById(viewId);
        if (v instanceof TextView) {
            ((TextView) v).setTextColor(color);
        }
    }

    @Override
    public void updateSapaInfo(String sapaName, String sapaDist, int sapaType, String nextSapaName, String nextSapaDist, int nextSapaType) {
        // 暂无对应模块
    }

    @Override
    public void updateIntervalSpeed(int startDist, String startDistText, int avgSpeed, String endDistText, int limitSpeed) {
        // 暂无对应模块
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 立即保存最后状态（必须在清空 modules 之前，否则会把配置清空）
        // 防止用户调整后立即退出/熄火导致最后一次操作丢失
        saveHandler.removeCallbacksAndMessages(null);
        doSaveConfigs();
        // 完整释放：移除 View + 断开监听 + 释放子 View，避免内存泄漏
        for (ScalableModuleContainer mc : modules) {
            moduleContainer.removeView(mc);
            mc.setOnConfigChangeListener(null);
            mc.removeAllViews();
        }
        modules.clear();
    }
}
