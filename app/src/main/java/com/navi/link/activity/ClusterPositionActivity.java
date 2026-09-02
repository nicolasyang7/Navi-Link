package com.navi.link.activity;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.navi.link.R;
import com.navi.link.utils.CustomLog;
import com.navi.link.window.FloatingWindowManager;
import com.navi.link.window.ModuleConfig;
import com.navi.link.window.ModulePreviewContainer;
import com.navi.link.window.ModuleRegistry;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**副屏调位+模块管理页（V12）：左右分栏，左侧模拟屏+方向键，右侧模块列表；不依赖副屏窗口 */
public class ClusterPositionActivity extends AppCompatActivity {

 private FrameLayout mockScreen;
 private LinearLayout moduleList;
 private TextView tvPosInfo;
 private FloatingWindowManager fwm;

 private int moduleScreenW = 1920;
 private int moduleScreenH = 720;
 private int moduleWindowW = 480;
 private int moduleWindowH = 360;
 private int[] currentPos = new int[]{0, 0};
 private List<ModuleConfig> currentConfigs = new ArrayList<>();
 //V13.5：进入页面时的快照（取消=恢复此快照）
 private List<ModuleConfig> openSnapshot = new ArrayList<>();
 private int[] openPos = new int[]{0, 0};

 private Handler dataHandler = null;
 private Runnable dataRunnable = null;
 private int themeColor = 0xFF4FC3F7;

 @Override
 protected void onCreate(Bundle savedInstanceState) {
 super.onCreate(savedInstanceState);
  setContentView(R.layout.activity_cluster_position);

 CustomLog.d("[调位页]打开");

 fwm = FloatingWindowManager.getInstance();
 //[MOD]不再拦截：副屏窗口未创建（如HDMI2失败）也能打开调位页做模块预览
 if (fwm != null) {
 themeColor = getSharedPreferences("floating_config", MODE_PRIVATE).getInt("theme_color", 0xFF4FC3F7);
 }

 mockScreen = findViewById(R.id.mock_screen);
 moduleList = findViewById(R.id.module_list);
 tvPosInfo = findViewById(R.id.tv_cluster_pos_info);

 if (mockScreen != null) {
 //V13.9：模拟屏背景照官方 bg_floating_dark（#FF121212 圆角12dp）
 GradientDrawable screenBg = new GradientDrawable();
 screenBg.setColor(0xFF121212);
 screenBg.setCornerRadius(dpToPx(12));
 screenBg.setStroke(dpToPx(1), 0x33FFFFFF);
 mockScreen.setBackground(screenBg);
 }

 if (fwm != null) {
 int sw = fwm.getClusterScreenWidth();
 int sh = fwm.getClusterScreenHeight();
 if (sw <= 0) sw = 1920;
 if (sh <= 0) sh = 720;
 int w = fwm.getClusterNaturalWidth();
 int h = fwm.getClusterNaturalHeight();
 if (w <= 0) w = dpToPx(160);
 if (h <= 0) h = dpToPx(120);
 moduleScreenW = sw;
 moduleScreenH = sh;
 moduleWindowW = w;
 moduleWindowH = h;
 currentPos[0] = fwm.getClusterSavedPosX();
 currentPos[1] = fwm.getClusterSavedPosY();
 }

 //V13.5：记录进入页面时的配置快照（深拷贝：loadAll 每次返回新对象）
 openSnapshot = ModuleConfig.loadAll(this);
 openPos[0] = currentPos[0];
 openPos[1] = currentPos[1];

 setupControlButtons();
 buildModuleList();

 TextView btnClearAll = findViewById(R.id.btn_clear_all);
 if (btnClearAll != null) {
 btnClearAll.setOnClickListener(v -> {
 currentConfigs = new ArrayList<>();
 ModuleConfig.saveAll(this, currentConfigs);
 loadModulesToMockScreen();
 CustomLog.d("[调位页]清空所有模块");
 Toast.makeText(this, "已清空所有模块", Toast.LENGTH_SHORT).show();
 });
 }

 if (mockScreen != null) {
 mockScreen.post(this::loadModulesToMockScreen);
 // =====[MOD]诊断水印 + 布局来源检测=====
 try {
 android.widget.TextView watermark = new android.widget.TextView(this);
 watermark.setText("MOD-V13.15");
 watermark.setTextColor(0x66FFFFFF);
 watermark.setTextSize(9);
 android.widget.FrameLayout.LayoutParams wmLp = new android.widget.FrameLayout.LayoutParams(
 android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
 android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
 wmLp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
 wmLp.setMargins(dpToPx(4), dpToPx(4), 0, 0);
 mockScreen.addView(watermark, wmLp);

 int officialId = getResources().getIdentifier("cluster_trackpad_indicator", "id", getPackageName());
 android.view.View official = officialId != 0 ? findViewById(officialId) : null;
 if (official != null) {
 com.navi.link.utils.CustomLog.d("[调位页]警告：检测到官方元素 cluster_trackpad_indicator——加载的是官方布局（资源被系统覆盖）");
 android.widget.Toast.makeText(this, "警告：页面加载的是官方布局（资源被系统覆盖）", android.widget.Toast.LENGTH_LONG).show();
 } else {
 com.navi.link.utils.CustomLog.d("[调位页]布局检测：V12布局正常（无官方元素）R.layout=" + Integer.toHexString(R.layout.activity_cluster_position));
 }
 } catch (Exception ignored) {
 }
 }
 refreshPosInfo();
 }

 @Override
 protected void onResume() {
 super.onResume();
 loadModulesToMockScreen();
 startDataRefresh();
 }

 @Override
 protected void onPause() {
 super.onPause();
 stopDataRefresh();
 }

 private void setupControlButtons() {
 TextView btnLeft = findViewById(R.id.btn_pos_left);
 TextView btnUp = findViewById(R.id.btn_pos_up);
 TextView btnCenter = findViewById(R.id.btn_pos_center);
 TextView btnDown = findViewById(R.id.btn_pos_down);
 TextView btnRight = findViewById(R.id.btn_pos_right);
 TextView btnDone = findViewById(R.id.btn_pos_done);
 TextView btnCancel = findViewById(R.id.btn_pos_cancel);

 if (btnLeft != null) btnLeft.setOnClickListener(v -> moveWindow(-16, 0));
 if (btnUp != null) btnUp.setOnClickListener(v -> moveWindow(0, -16));
 if (btnRight != null) btnRight.setOnClickListener(v -> moveWindow(16, 0));
 if (btnDown != null) btnDown.setOnClickListener(v -> moveWindow(0, 16));
 if (btnCenter != null) btnCenter.setOnClickListener(v -> {
 currentPos[0] = Math.max(0, (moduleScreenW - moduleWindowW) / 2);
 currentPos[1] = Math.max(0, (moduleScreenH - moduleWindowH) / 2);
 applyWindowPosition();
 });
 if (btnDone != null) btnDone.setOnClickListener(v -> {
 //V13.5：保存=固化当前配置再退出
 ModuleConfig.saveAll(this, currentConfigs);
 CustomLog.d("[调位页]保存配置: " + currentConfigs.size() + "个模块");
 finish();
 });
 if (btnCancel != null) btnCancel.setOnClickListener(v -> {
 //V13.5：取消=恢复进入页面时的快照再退出
 ModuleConfig.saveAll(this, openSnapshot);
 if (fwm != null) fwm.updateClusterPosition(openPos[0], openPos[1]);
 CustomLog.d("[调位页]取消：已恢复快照 " + openSnapshot.size() + "个模块");
 finish();
 });
 }

 private void moveWindow(int dx, int dy) {
 currentPos[0] = Math.max(0, Math.min(currentPos[0] + dx, moduleScreenW - moduleWindowW));
 currentPos[1] = Math.max(0, Math.min(currentPos[1] + dy, moduleScreenH - moduleWindowH));
 applyWindowPosition();
 }

 private void applyWindowPosition() {
 if (fwm != null) {
 fwm.updateClusterPosition(currentPos[0], currentPos[1]);
 }
 refreshPosInfo();
 updateModuleHandlePositions();
 }

 private void refreshPosInfo() {
 if (tvPosInfo != null) {
 tvPosInfo.setText(String.format("当前位置: X = %d px, Y = %d px", currentPos[0], currentPos[1]));
 }
 }

 private void buildModuleList() {
 if (moduleList == null) return;
 moduleList.removeAllViews();
 List<ModuleRegistry.ModuleInfo> all = ModuleRegistry.getAll();
 for (final ModuleRegistry.ModuleInfo info : all) {
 TextView row = new TextView(this);
 row.setText("＋ " + info.name);
 row.setTextColor(Color.WHITE);
 row.setTextSize(13);
 row.setGravity(Gravity.CENTER_VERTICAL);
 row.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
 GradientDrawable bg = new GradientDrawable();
 bg.setColor(0xFF1F2430);
 bg.setCornerRadius(dpToPx(6));
 row.setBackground(bg);
 LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
 LinearLayout.LayoutParams.MATCH_PARENT,
 LinearLayout.LayoutParams.WRAP_CONTENT);
 lp.bottomMargin = dpToPx(6);
 row.setLayoutParams(lp);
 row.setOnClickListener(v -> addModuleToConfig(info.id));
 moduleList.addView(row);
 }
 }

 private void addModuleToConfig(String moduleId) {
 List<ModuleConfig> configs = ModuleConfig.loadAll(this);
 if (configs.size() >= 30) {
 Toast.makeText(this, "模块数量已达上限(30)", Toast.LENGTH_SHORT).show();
 return;
 }
 ModuleConfig newConfig = new ModuleConfig(
 ModuleConfig.newInstanceId(moduleId), moduleId, 1.0f, 0f, 0f);
 //[V13.8]防重复：同一 instanceId 已存在则不添加（避免重复点击/异常保存导致一个变两个）
 for (ModuleConfig c : configs) {
 if (newConfig.instanceId.equals(c.instanceId)) {
 CustomLog.d("[调位页]防重复：跳过已存在模块 " + moduleId);
 return;
 }
 }
 configs.add(newConfig);
 ModuleConfig.saveAll(this, configs);
 CustomLog.d("[调位页]添加模块: " + moduleId + " 当前数量: " + configs.size());
 if (mockScreen != null) {
 mockScreen.post(() -> loadModulesToMockScreen());
 }
 }

 private void loadModulesToMockScreen() {
 if (mockScreen == null) return;
 mockScreen.removeAllViews();
 currentConfigs = ModuleConfig.loadAll(this);
 if (currentConfigs.isEmpty()) {
 TextView hint = new TextView(this);
 hint.setText("点击右侧模块添加到副屏");
 hint.setTextColor(0x66FFFFFF);
 hint.setTextSize(15);
 hint.setGravity(Gravity.CENTER);
 mockScreen.addView(hint, new FrameLayout.LayoutParams(
 FrameLayout.LayoutParams.MATCH_PARENT,
 FrameLayout.LayoutParams.MATCH_PARENT));
 return;
 }
 mockScreen.post(() -> {
 if (mockScreen == null) return;
 //[V13.8]防重复：按 instanceId 去重后再渲染（防御：配置异常重复时 mock 屏不显示两份）
 java.util.HashSet<String> seen = new java.util.HashSet<>();
 for (ModuleConfig cfg : currentConfigs) {
 if (cfg == null || !seen.add(cfg.instanceId)) continue;
 addModuleToMockScreen(cfg);
 }
 });
 }

 private void addModuleToMockScreen(final ModuleConfig cfg) {
 final ModulePreviewContainer mc = new ModulePreviewContainer(this, cfg);
 fillMockPreview(mc, cfg.moduleId);
 mc.setDragBounds(mockScreen.getWidth(), mockScreen.getHeight());
 mc.setOnConfigChangeListener(conf -> {
 float[] wr = windowRect();
 conf.x = Math.round((mc.getX() - wr[0]) / Math.max(wr[2], 1) * moduleWindowW);
 conf.y = Math.round((mc.getY() - wr[1]) / Math.max(wr[3], 1) * moduleWindowH);
 ModuleConfig.saveAll(this, currentConfigs);
 });
 mc.setOnLongClickListener(v -> {
 new AlertDialog.Builder(this)
 .setTitle("删除模块")
 .setMessage("确定删除「" + ModuleRegistry.getName(cfg.moduleId) + "」吗？")
 .setPositiveButton("删除", (d, w) -> {
 List<ModuleConfig> list = new ArrayList<>();
 for (ModuleConfig c : currentConfigs) {
 if (!c.instanceId.equals(cfg.instanceId)) list.add(c);
 }
 currentConfigs = list;
 ModuleConfig.saveAll(this, currentConfigs);
 loadModulesToMockScreen();
 })
 .setNegativeButton("取消", null)
 .show();
 return true;
 });
 mockScreen.addView(mc, new FrameLayout.LayoutParams(
 FrameLayout.LayoutParams.WRAP_CONTENT,
 FrameLayout.LayoutParams.WRAP_CONTENT));
 mc.post(() -> {
 if (mockScreen == null) return;
 float[] wr = windowRect();
 float bx = wr[0] + (float) cfg.x / Math.max(moduleWindowW, 1) * wr[2];
 float by = wr[1] + (float) cfg.y / Math.max(moduleWindowH, 1) * wr[3];
 mc.setPosition(bx, by);
 });
 }



 /**模拟屏数据预览：用 FWM 缓存填充，便于确认数据链路（照原作者 BaseFloatingWindow 字段） */
 private void fillMockPreview(ModulePreviewContainer mc, String mid) {
 if (fwm == null || mc == null) return;
 try {
 if ("module_speed".equals(mid)) {
 TextView tv = mc.findViewById(R.id.tv_module_speed);
 if (tv != null) tv.setText(String.valueOf(fwm.getCachedSpeed()));
 } else if ("module_road_name".equals(mid)) {
 TextView tv = mc.findViewById(R.id.tv_module_road_name);
 if (tv != null) tv.setText(fwm.getCachedRoadName());
 } else if ("module_cruise_road_name".equals(mid)) {
 TextView tv = mc.findViewById(R.id.tv_module_road_name);
 if (tv != null) tv.setText(fwm.getCachedRoadName());
 } else if ("module_turn_distance".equals(mid)) {
 TextView tv = mc.findViewById(R.id.tv_module_distance_num);
 if (tv != null) tv.setText(fwm.getCachedDisNum());
 TextView tv2 = mc.findViewById(R.id.tv_module_distance_unit);
 if (tv2 != null) tv2.setText(fwm.getCachedDisUnit());
 } else if ("module_eta".equals(mid)) {
 TextView tv = mc.findViewById(R.id.tv_module_eta);
 if (tv != null) tv.setText(fwm.getCachedEta());
 } else if ("module_exit_info".equals(mid)) {
 TextView tv = mc.findViewById(R.id.tv_module_exit_info);
 if (tv != null) tv.setText(fwm.getCachedExit());
 } else if ("module_speed_limit".equals(mid)) {
 TextView tv = mc.findViewById(R.id.tv_module_speed_limit);
 int limit = fwm.getCachedLimit();
 if (tv != null) {
 if (limit > 0) { tv.setText(String.valueOf(limit)); tv.setVisibility(View.VISIBLE); }
 else tv.setVisibility(View.GONE);
 }
 } else if ("module_lane_line".equals(mid)) {
 mc.setPreviewLaneLines(fwm.getCachedLaneLines());
 } else if ("module_cruise_lane_line".equals(mid)) {
 mc.setPreviewLaneLines(fwm.getCachedLaneLines());
 } else if ("module_tmc_progress".equals(mid)) {
 mc.setPreviewTmc(fwm.getCachedTmc());
 } else if ("module_camera_distance".equals(mid)) {
 int[] cam = fwm.getCachedCam();
 if (cam != null && cam.length >= 3) mc.setPreviewCamera(cam[0], cam[1], cam[2]);
 } else if ("module_traffic_light".equals(mid)) {
 mc.setPreviewTrafficLight(fwm.getCachedTrafficLights(),
 fwm.getCachedLightStatus(), fwm.getCachedLightDir(), fwm.getCachedLightCountdown());
 } else if ("module_cruise_traffic_light".equals(mid)) {
 mc.setPreviewTrafficLight(fwm.getCachedTrafficLights(),
 fwm.getCachedLightStatus(), fwm.getCachedLightDir(), fwm.getCachedLightCountdown());
 }
 } catch (Exception ignored) {
 }
 }
 private float[] windowRect() {
 if (mockScreen == null) return new float[]{0, 0, 1, 1};
 float tw = Math.max(mockScreen.getWidth(), 1);
 float th = Math.max(mockScreen.getHeight(), 1);
 float sw = Math.max(moduleScreenW, 1);
 float sh = Math.max(moduleScreenH, 1);
 float winX = (float) currentPos[0] / sw * tw;
 float winY = (float) currentPos[1] / sh * th;
 float winW = (float) moduleWindowW / sw * tw;
 float winH = (float) moduleWindowH / sh * th;
 return new float[]{winX, winY, winW, winH};
 }

 /**窗口移动时模块方块联动 */
 private void updateModuleHandlePositions() {
 if (mockScreen == null) return;
 float[] wr = windowRect();
 for (int i = 0; i < mockScreen.getChildCount(); i++) {
 View child = mockScreen.getChildAt(i);
 if (child instanceof ModulePreviewContainer) {
 ModulePreviewContainer mc = (ModulePreviewContainer) child;
 ModuleConfig cfg = mc.getConfig();
 float bx = wr[0] + (float) cfg.x / Math.max(moduleWindowW, 1) * wr[2];
 float by = wr[1] + (float) cfg.y / Math.max(moduleWindowH, 1) * wr[3];
 mc.setPosition(bx, by);
 }
 }
 }

 private void startDataRefresh() {
 if (dataHandler != null) return;
 dataHandler = new Handler(Looper.getMainLooper());
 dataRunnable = new Runnable() {
 @Override
 public void run() {
 refreshModuleData();
 dataHandler.postDelayed(this, 800);
 }
 };
 dataHandler.post(dataRunnable);
 }

 private void stopDataRefresh() {
 if (dataHandler != null) {
 dataHandler.removeCallbacksAndMessages(null);
 dataHandler = null;
 dataRunnable = null;
 }
 }

 /**从 FWM 缓存读取实时数据刷新模块预览（只读） */
 private void refreshModuleData() {
 if (mockScreen == null || fwm == null) return;
 for (int i = 0; i < mockScreen.getChildCount(); i++) {
 View child = mockScreen.getChildAt(i);
 if (!(child instanceof ModulePreviewContainer)) continue;
 ModulePreviewContainer mc = (ModulePreviewContainer) child;
 String mid = mc.getConfig().moduleId;
 if ("module_speed".equals(mid)) {
 setPreviewText(mc, R.id.tv_module_speed, String.valueOf(fwm.getCachedSpeed()));
 } else if ("module_road_name".equals(mid) || "module_cruise_road_name".equals(mid)) {
 setPreviewText(mc, R.id.tv_module_road_name, fwm.getCachedRoadName());
 } else if ("module_lane_line".equals(mid) || "module_cruise_lane_line".equals(mid)) {
 //车道线数据直接透传
 mc.setPreviewLaneLines(fwm.getCachedLaneLines());
 } else if ("module_tmc_progress".equals(mid)) {
 mc.setPreviewTmc(fwm.getCachedTmc());
 } else if ("module_eta".equals(mid)) {
 setPreviewText(mc, R.id.tv_module_eta, fwm.getCachedEta());
 } else if ("module_exit_info".equals(mid)) {
 setPreviewText(mc, R.id.tv_module_exit_info, fwm.getCachedExit());
 } else if ("module_speed_limit".equals(mid)) {
 setPreviewText(mc, R.id.tv_module_speed_limit, fwm.getCachedLimit() > 0 ? String.valueOf(fwm.getCachedLimit()) : "");
 } else if ("module_camera_distance".equals(mid)) {
 int[] cam = fwm.getCachedCam();
 if (cam != null && cam.length == 3) mc.setPreviewCamera(cam[0], cam[1], cam[2]);
 }
 }
 }

 private void setPreviewText(ModulePreviewContainer mc, int viewId, String text) {
 View v = mc.findViewById(viewId);
 if (v instanceof TextView) {
 ((TextView) v).setText(text);
 }
 }

 private int dpToPx(int dp) {
 return Math.round(dp * getResources().getDisplayMetrics().density);
 }
}
