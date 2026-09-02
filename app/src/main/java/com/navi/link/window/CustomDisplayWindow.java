package com.navi.link.window;

import android.content.Context;
import android.content.SharedPreferences;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.navi.link.R;
import com.navi.link.utils.CustomLog;
import com.navi.link.view.CameraWarningView;
import com.navi.link.view.LaneLineView;
import com.navi.link.view.TmcProgressBar;
import com.navi.link.view.TrafficLightView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**副屏唯一窗口（框架不变）：继承/方法名/生命周期与官方一致，内部为动态模块容器 */
public class CustomDisplayWindow extends BaseFloatingWindow implements SharedPreferences.OnSharedPreferenceChangeListener {

 private static final String PREFS_KEY = "custom_display_modules";

 private FrameLayout moduleContainer;
 private final List<ModulePreviewContainer> modules = new ArrayList<>();

 //数据缓存：新添加的模块立即用最近数据填充
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
 try {
 moduleContainer = new FrameLayout(context);
 moduleContainer.setClipChildren(false);
 moduleContainer.setClipToPadding(false);
 FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
 FrameLayout.LayoutParams.WRAP_CONTENT,
 FrameLayout.LayoutParams.WRAP_CONTENT);
 ((ViewGroup) floatingView).addView(moduleContainer, lp);

 List<ModuleConfig> configs = ModuleConfig.loadAll(context);
 CustomLog.d("[副屏窗口]恢复模块配置: " + configs.size() + "个");
 for (ModuleConfig cfg : configs) {
 addModuleInstance(cfg);
 }

 context.getSharedPreferences("floating_config", Context.MODE_PRIVATE)
 .registerOnSharedPreferenceChangeListener(this);
 } catch (Exception e) {
 CustomLog.e("[副屏窗口]initViews异常，降级为空窗口", e);
 }
 }

 @Override
 public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
 if (PREFS_KEY.equals(key)) {
 //配置驱动：模拟屏改动后真实副屏自动重建
 try {
 reloadModules();
 } catch (Exception e) {
 CustomLog.e("[副屏窗口]配置变更重建异常", e);
 }
 }
 }

 private void reloadModules() {
 if (moduleContainer == null) return;
 moduleContainer.removeAllViews();
 modules.clear();
 List<ModuleConfig> configs = ModuleConfig.loadAll(context);
 CustomLog.d("[副屏窗口]配置变更重建: " + configs.size() + "个");
 for (ModuleConfig cfg : configs) {
 addModuleInstance(cfg);
 }
 }

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

 /**V13.11：调位页拖动时实时同步副屏模块位置（轻量，无SP无重建，照官方trackpad拖动） */
 public void updateModulePosition(String instanceId, float x, float y) {
 for (ModulePreviewContainer mc : modules) {
 if (mc.getConfig() != null && instanceId != null && instanceId.equals(mc.getConfig().instanceId)) {
 mc.setPosition(x, y);
 return;
 }
 }
 }

 private boolean isCruiseMode() {
 FloatingWindowManager fwm = FloatingWindowManager.getInstance();
 return fwm != null && fwm.getCurrentMode() == FloatingWindowManager.MODE_CRUISE;
 }

 // ========================数据分发（方法名与官方一致） ========================

 @Override
 public void updateCruiseInfo(int speed, String roadName, int cameraType, int cameraSpeed, int cameraDist, int carDirection) {
 CustomLog.d("[副屏窗口]收到巡航数据: 速度=" + speed + " 路名=" + (roadName == null ? "" : roadName) + " 模块数=" + modules.size());
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
 setText(mc, R.id.tv_module_speed, String.valueOf(speed));
 //V13.9：照官方 NormalCruiseWindow 超速警告（限速>0且超速→红色闪烁）
 applyOverspeedToModule(mc, speed, cameraSpeed);
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
 CustomLog.d("[副屏窗口]收到导航数据: 速度=" + curSpeed + " 距离=" + (disNum == null ? "" : disNum) + (disUnit == null ? "" : disUnit) + " 图标=" + icon + " 模块数=" + modules.size());
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
 setText(mc, R.id.tv_module_speed, String.valueOf(curSpeed));
 } else if ("module_road_name".equals(mid)) {
 setText(mc, R.id.tv_module_road_name, cacheRoadName);
 } else if ("module_turn_icon".equals(mid)) {
 updateTurnIconModule(mc, icon, cacheDisNum, cacheDisUnit);
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
 cacheLightStatus = status;
 cacheLightDir = dir;
 cacheLightCountdown = countdown;
 for (ModulePreviewContainer mc : modules) {
 if ("module_traffic_light".equals(mc.getConfig().moduleId)) {
 updateSingleTrafficLight(mc, status, dir, countdown);
 }
 }
 }

 @Override
 public void updateCruiseTrafficLights(JSONArray lightsArray) {
 cacheCruiseLights = lightsArray;
 for (ModulePreviewContainer mc : modules) {
 String mid = mc.getConfig().moduleId;
 if ("module_traffic_light".equals(mid) || "module_cruise_traffic_light".equals(mid)) {
 updateCruiseTrafficLightModule(mc, lightsArray);
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
 LaneLineView lv = mc.findViewById(R.id.lane_line_module);
 if (lv != null) lv.updateLanes(driveWayJson);
 }
 }
 }

 @Override
 public void updateTmcData(String tmcJson) {
 cacheTmcJson = tmcJson;
 for (ModulePreviewContainer mc : modules) {
 if ("module_tmc_progress".equals(mc.getConfig().moduleId)) {
 TmcProgressBar bar = mc.findViewById(R.id.tmc_module);
 if (bar != null) bar.updateTmcData(tmcJson);
 }
 }
 }

 @Override
 public void updateExitInfo(String exitName, String exitDirection) {
 cacheExitName = exitName == null ? "" : exitName.trim();
 for (ModulePreviewContainer mc : modules) {
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

 @Override
 public void applyThemeColor(int themeColor) {
 applyDayNightTextColors(isNightMode);
 }

 @Override
 public void applyDayNightTextColors(boolean isNightMode) {
 this.isNightMode = isNightMode;
 int primary = getPrimaryTextColor(isNightMode);
 for (ModulePreviewContainer mc : modules) {
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
 for (ModulePreviewContainer mc : modules) {
 tintText(mc, R.id.tv_module_speed, TEXT_PRIMARY_DARK);
 tintText(mc, R.id.tv_module_road_name, TEXT_PRIMARY_DARK);
 tintText(mc, R.id.tv_module_distance_num, TEXT_PRIMARY_DARK);
 tintText(mc, R.id.tv_module_distance_unit, TEXT_PRIMARY_DARK);
 tintText(mc, R.id.tv_module_eta, TEXT_PRIMARY_DARK);
 tintText(mc, R.id.tv_module_exit_info, TEXT_PRIMARY_DARK);
 }
 }

 @Override
 public void updateSapaInfo(String sapaName, String sapaDist, int sapaType, String nextSapaName, String nextSapaDist, int nextSapaType) {
 //暂无对应模块
 }

 @Override
 public void onDestroy() {
 super.onDestroy();
 try {
 context.getSharedPreferences("floating_config", Context.MODE_PRIVATE)
 .unregisterOnSharedPreferenceChangeListener(this);
 } catch (Exception ignored) {
 }
 for (ModulePreviewContainer mc : modules) {
 moduleContainer.removeView(mc);
 }
 modules.clear();
 }

 // ========================模块更新辅助 ========================

 private void refreshModuleWithCache(ModulePreviewContainer mc) {
 String mid = mc.getConfig().moduleId;
 if ("module_speed".equals(mid)) {
 setText(mc, R.id.tv_module_speed, String.valueOf(cacheSpeed));
 } else if ("module_road_name".equals(mid) || "module_cruise_road_name".equals(mid)) {
 setText(mc, R.id.tv_module_road_name, cacheRoadName);
 } else if ("module_turn_icon".equals(mid)) {
 updateTurnIconModule(mc, cacheIcon, cacheDisNum, cacheDisUnit);
 } else if ("module_turn_distance".equals(mid)) {
 setText(mc, R.id.tv_module_distance_num, cacheDisNum);
 setText(mc, R.id.tv_module_distance_unit, cacheDisUnit);
 } else if ("module_eta".equals(mid)) {
 setText(mc, R.id.tv_module_eta, formatEtaSpannable(cacheEta));
 } else if ("module_exit_info".equals(mid)) {
 if (!cacheExitName.isEmpty()) setText(mc, R.id.tv_module_exit_info, cacheExitName);
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
 if (cacheCruiseLights != null) {
 updateCruiseTrafficLightModule(mc, cacheCruiseLights);
 } else if (cacheLightCountdown > 0) {
 updateSingleTrafficLight(mc, cacheLightStatus, cacheLightDir, cacheLightCountdown);
 }
 } else if ("module_cruise_traffic_light".equals(mid)) {
 if (cacheCruiseLights != null) {
 updateCruiseTrafficLightModule(mc, cacheCruiseLights);
 }
 }
 }

 private void setText(ModulePreviewContainer mc, int viewId, CharSequence text) {
 View v = mc.findViewById(viewId);
 if (v instanceof TextView) {
 ((TextView) v).setText(text);
 }
 }

 private void updateTurnIconModule(ModulePreviewContainer mc, int icon, String disNum, String disUnit) {
 ImageView iv = mc.findViewById(R.id.iv_module_turn_icon);
 if (iv == null) return;
 int res = getTurnIconRes(icon);
 if (res != 0) iv.setImageResource(res);
 //V13.9：照官方 NormalNaviWindow 转向图标闪烁（距离<=30米或到达时）
 boolean shouldBlink = shouldBlinkTurnIcon(disNum, disUnit);
 ObjectAnimator animator = (ObjectAnimator) iv.getTag();
 if (shouldBlink) {
 if (animator == null) {
 ObjectAnimator newAnimator = ObjectAnimator.ofFloat(iv, "alpha", 1f, 0.3f);
 newAnimator.setDuration(500);
 newAnimator.setRepeatCount(ValueAnimator.INFINITE);
 newAnimator.setRepeatMode(ValueAnimator.REVERSE);
 newAnimator.start();
 iv.setTag(newAnimator);
 }
 } else {
 if (animator != null) {
 animator.cancel();
 iv.setTag(null);
 }
 iv.setAlpha(1f);
 }
 }

 /**V13.9：照官方 NormalCruiseWindow 超速警告（cameraSpeed=限速，超速→红色+闪烁） */
 private void applyOverspeedToModule(ModulePreviewContainer mc, int speed, int cameraSpeed) {
 TextView tv = mc.findViewById(R.id.tv_module_speed);
 if (tv == null) return;
 try {
 int threshold = sp.getInt("overspeed_threshold", 0);
 double factor = 1.0 + threshold / 100.0;
 boolean isOverspeedWarningEnabled = sp.getBoolean("overspeed_warning_enabled", true);
 boolean overspeed = isOverspeedWarningEnabled && cameraSpeed > 0 && speed > Math.round(cameraSpeed * factor);
 ObjectAnimator animator = (ObjectAnimator) tv.getTag();
 if (overspeed) {
 tv.setTextColor(Color.RED);
 if (animator == null) {
 ObjectAnimator newAnimator = ObjectAnimator.ofFloat(tv, "alpha", 1f, 0.3f);
 newAnimator.setDuration(500);
 newAnimator.setRepeatCount(ValueAnimator.INFINITE);
 newAnimator.setRepeatMode(ValueAnimator.REVERSE);
 newAnimator.start();
 tv.setTag(newAnimator);
 }
 } else {
 if (animator != null) {
 animator.cancel();
 tv.setTag(null);
 }
 tv.setAlpha(1f);
 //恢复正常：跟随主题主文字颜色（与官方一致）
 tv.setTextColor(getPrimaryTextColor(isNightMode));
 }
 } catch (Exception ignored) {
 }
 }

 private void updateSpeedLimitModule(ModulePreviewContainer mc, int limitSpeed) {
 TextView tv = mc.findViewById(R.id.tv_module_speed_limit);
 if (tv == null) return;
 if (limitSpeed > 0) {
 tv.setText(String.valueOf(limitSpeed));
 tv.setVisibility(View.VISIBLE);
 } else {
 tv.setVisibility(View.GONE);
 }
 }

 private void updateCameraModule(ModulePreviewContainer mc, int cameraType, int cameraDist, int cameraSpeed) {
 CameraWarningView cv = mc.findViewById(R.id.camera_module);
 if (cv != null) cv.updateCameraInfo(cameraType, cameraDist, cameraSpeed);
 }

 private void updateSingleTrafficLight(ModulePreviewContainer mc, int status, int dir, int countdown) {
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

 private void updateCruiseTrafficLightModule(ModulePreviewContainer mc, JSONArray lightsArray) {
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
 lv.setCompact(count >= 3); //V13.9：照官方巡航多灯紧凑模式
 container.addView(lv);
 if (countdown > 0) {
 lv.setData(status, dir, countdown, false);
 } else {
 lv.setVisibility(View.GONE);
 }
 } catch (Exception ignored) {
 }
 }
 container.setVisibility(View.VISIBLE);
 }

 private void tintText(ModulePreviewContainer mc, int viewId, int color) {
 View v = mc.findViewById(viewId);
 if (v instanceof TextView) {
 ((TextView) v).setTextColor(color);
 }
 }
}
