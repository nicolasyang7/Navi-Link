package com.navi.link.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import com.navi.link.R;
import com.navi.link.window.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 副屏投屏位置调整页（V12）
 * 官方逻辑原样保留：方向键 ◀▲▼▶ / 居中 / 保存退出 / 窗口位置保存（updateClusterPosition）。
 * 布局：左侧模拟屏幕（模块预览，可拖拽/缩放/长按删除）+ 右侧可用模块列表。
 * 数据联动：通过 FloatingWindowManager.getCachedXxx() 只读刷新预览，不监听广播、不写操作。
 */
public class ClusterPositionActivity extends AppCompatActivity {

    private TextView tvTitle;
    private TextView tvPosInfo;
    private FrameLayout mockScreen;
    private LinearLayout moduleList;

    private View btnCenter;
    private View btnUp;
    private View btnDown;
    private View btnLeft;
    private View btnRight;
    private MaterialButton btnDone;
    private TextView btnClearAll;

    private int themeColor = 0xFF4FC3F7;
    private int accentColor = 0xFF4FC3F7;

    private FloatingWindowManager fwm;
    private int moduleScreenW = 1920;
    private int moduleScreenH = 720;
    private int moduleWindowW = 480;
    private int moduleWindowH = 360;
    private int[] currentPos = new int[]{0, 0};
    private List<ModuleConfig> currentConfigs = new ArrayList<>();

    private Handler dataHandler;
    private Runnable dataRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cluster_position);

        // ===== 官方原样：insets 处理 =====
        ViewGroup contentView = findViewById(android.R.id.content);
        View root = contentView.getChildAt(0);
        if (root != null) {
            final int paddingLeft = root.getPaddingLeft();
            final int paddingTop = root.getPaddingTop();
            final int paddingRight = root.getPaddingRight();
            final int paddingBottom = root.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsetsCompat) -> {
                Insets insets = windowInsetsCompat.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(
                        insets.left + paddingLeft,
                        insets.top + paddingTop,
                        insets.right + paddingRight,
                        insets.bottom + paddingBottom
                );
                return windowInsetsCompat;
            });
        }

        androidx.core.view.WindowInsetsControllerCompat windowInsetsController =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false);
        }

        // ===== 官方原样：副屏未开启则提示退出 =====
        fwm = FloatingWindowManager.getInstance();
        if (fwm == null || !fwm.isClusterMirrorActive()) {
            Toast.makeText(this, "副屏投屏未开启，请先开启投屏", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ===== 官方原样：读取主题色 =====
        SharedPreferences sp = getSharedPreferences("floating_config", MODE_PRIVATE);
        themeColor = sp.getInt("theme_color", 0xFF4FC3F7);
        accentColor = isDarkColor(themeColor) ? Color.WHITE : themeColor;

        // 绑定视图（官方 id 原样 + 新增模块控件）
        tvTitle = findViewById(R.id.tv_dialog_title);
        tvPosInfo = findViewById(R.id.tv_cluster_pos_info);
        mockScreen = findViewById(R.id.mock_screen);
        moduleList = findViewById(R.id.module_list);
        btnCenter = findViewById(R.id.btn_pos_center);
        btnUp = findViewById(R.id.btn_pos_up);
        btnDown = findViewById(R.id.btn_pos_down);
        btnLeft = findViewById(R.id.btn_pos_left);
        btnRight = findViewById(R.id.btn_pos_right);
        btnDone = findViewById(R.id.btn_pos_done);
        btnClearAll = findViewById(R.id.btn_clear_all);

        if (tvTitle != null) tvTitle.setTextColor(accentColor);
        if (btnDone != null) {
            btnDone.setBackgroundTintList(ColorStateList.valueOf(accentColor));
            btnDone.setOnClickListener(v -> finish());
        }

        // 模拟屏圆角背景
        if (mockScreen != null) {
            GradientDrawable msBg = new GradientDrawable();
            msBg.setColor(0xFF2C2C2E);
            msBg.setCornerRadius(dpToPx(12));
            msBg.setStroke(dpToPx(1), 0x33FFFFFF);
            mockScreen.setBackground(msBg);
        }

        // ===== 官方原样：屏幕/窗口尺寸 =====
        int sw = fwm.getClusterScreenWidth();
        int sh = fwm.getClusterScreenHeight();
        int w = fwm.getClusterNaturalWidth();
        int h = fwm.getClusterNaturalHeight();
        if (sw <= 0 || sh <= 0) { sw = 1920; sh = 720; }
        if (w <= 0 || h <= 0) { w = dpToPx(160); h = dpToPx(120); }

        moduleScreenW = sw;
        moduleScreenH = sh;
        moduleWindowW = w;
        moduleWindowH = h;

        final int finalSw = sw;
        final int finalSh = sh;
        final int finalW = w;
        final int finalH = h;

        currentPos[0] = fwm.getClusterSavedPosX();
        currentPos[1] = fwm.getClusterSavedPosY();

        // ===== refreshIndicator（官方语义：坐标显示 + 模块联动） =====
        Runnable refreshIndicator = new Runnable() {
            @Override
            public void run() {
                if (tvPosInfo != null) {
                    tvPosInfo.setText(String.format("当前位置: X = %d px, Y = %d px", currentPos[0], currentPos[1]));
                }
                updateModuleHandlePositions();
            }
        };

        // ===== 官方原样：方向键监听（◀▲▼▶ 居中） =====
        if (btnUp != null) {
            btnUp.setOnClickListener(v -> {
                currentPos[1] = Math.max(0, currentPos[1] - 5);
                fwm.updateClusterPosition(currentPos[0], currentPos[1]);
                refreshIndicator.run();
            });
        }
        if (btnDown != null) {
            btnDown.setOnClickListener(v -> {
                currentPos[1] = Math.max(0, Math.min(currentPos[1] + 5, finalSh - finalH));
                fwm.updateClusterPosition(currentPos[0], currentPos[1]);
                refreshIndicator.run();
            });
        }
        if (btnLeft != null) {
            btnLeft.setOnClickListener(v -> {
                currentPos[0] = Math.max(0, currentPos[0] - 5);
                fwm.updateClusterPosition(currentPos[0], currentPos[1]);
                refreshIndicator.run();
            });
        }
        if (btnRight != null) {
            btnRight.setOnClickListener(v -> {
                currentPos[0] = Math.max(0, Math.min(currentPos[0] + 5, finalSw - finalW));
                fwm.updateClusterPosition(currentPos[0], currentPos[1]);
                refreshIndicator.run();
            });
        }
        if (btnCenter != null) {
            btnCenter.setOnClickListener(v -> {
                currentPos[0] = (finalSw - finalW) / 2;
                currentPos[1] = (finalSh - finalH) / 2;
                fwm.updateClusterPosition(currentPos[0], currentPos[1]);
                refreshIndicator.run();
            });
        }

        // ===== 新增：模块列表 + 清空 =====
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> {
                currentConfigs = new ArrayList<>();
                ModuleConfig.saveAll(this, currentConfigs);
                loadModulesToMockScreen();
                Toast.makeText(this, "已清空所有模块", Toast.LENGTH_SHORT).show();
            });
        }
        buildModuleList();

        // 首次加载 + 数据刷新（判空防御：布局缺失时不崩溃）
        if (mockScreen != null) {
            mockScreen.post(this::loadModulesToMockScreen);
        }
        startDataRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fwm != null) {
            loadModulesToMockScreen();
            startDataRefresh();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopDataRefresh();
    }

    // ========================[MOD] 模块列表 ========================

    private void buildModuleList() {
        if (moduleList == null) return;
        moduleList.removeAllViews();
        for (ModuleRegistry.ModuleInfo info : ModuleRegistry.getAll()) {
            TextView row = new TextView(this);
            row.setText("＋ " + info.name);
            row.setTextColor(Color.WHITE);
            row.setTextSize(13);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF23233A);
            bg.setCornerRadius(dpToPx(6));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dpToPx(6);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                if (currentConfigs.size() >= 30) {
                    Toast.makeText(this, "模块数量已达上限(30)", Toast.LENGTH_SHORT).show();
                    return;
                }
                ModuleConfig cfg = new ModuleConfig(ModuleConfig.newInstanceId(info.id), info.id, 1.0f, 0f, 0f);
                currentConfigs.add(cfg);
                ModuleConfig.saveAll(this, currentConfigs);
                loadModulesToMockScreen();
            });
            moduleList.addView(row);
        }
    }

    // ========================[MOD] 模拟屏模块预览 ========================

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
            for (ModuleConfig cfg : currentConfigs) {
                addModuleToMockScreen(cfg);
            }
        });
    }

    private void addModuleToMockScreen(final ModuleConfig cfg) {
        final ModulePreviewContainer mc = new ModulePreviewContainer(this, cfg);
        mc.setDragBounds(mockScreen.getWidth(), mockScreen.getHeight());
        mc.setOnConfigChangeListener(conf -> {
            // 容器拖动/缩放后 conf.x/y 为模拟屏坐标 → 反算回副屏窗口相对坐标
            float[] wr = windowRect();
            conf.x = Math.round((mc.getX() - wr[0]) / Math.max(wr[2], 1) * moduleWindowW);
            conf.y = Math.round((mc.getY() - wr[1]) / Math.max(wr[3], 1) * moduleWindowH);
            ModuleConfig.saveAll(this, currentConfigs);
        });
        mockScreen.addView(mc, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        // 初始位置：副屏坐标 → 模拟屏坐标（与官方 indicator 同套映射）
        mc.post(() -> {
            float[] wr = windowRect();
            float bx = wr[0] + (float) cfg.x / Math.max(moduleWindowW, 1) * wr[2];
            float by = wr[1] + (float) cfg.y / Math.max(moduleWindowH, 1) * wr[3];
            mc.setPosition(bx, by);
        });
    }

    /** 窗口在模拟屏上的矩形（官方 refreshIndicator 同套映射） */
    private float[] windowRect() {
        int mw = Math.max(mockScreen.getWidth(), 1);
        int mh = Math.max(mockScreen.getHeight(), 1);
        float winX = (float) currentPos[0] / moduleScreenW * mw;
        float winY = (float) currentPos[1] / moduleScreenH * mh;
        float winW = (float) moduleWindowW / moduleScreenW * mw;
        float winH = (float) moduleWindowH / moduleScreenH * mh;
        return new float[]{winX, winY, winW, winH};
    }

    /** 窗口位置变化时模块方块联动 */
    private void updateModuleHandlePositions() {
        if (mockScreen == null) return;
        for (int i = 0; i < mockScreen.getChildCount(); i++) {
            View child = mockScreen.getChildAt(i);
            if (!(child instanceof ModulePreviewContainer)) continue;
            ModulePreviewContainer mc = (ModulePreviewContainer) child;
            ModuleConfig cfg = mc.getConfig();
            float[] wr = windowRect();
            float bx = wr[0] + (float) cfg.x / Math.max(moduleWindowW, 1) * wr[2];
            float by = wr[1] + (float) cfg.y / Math.max(moduleWindowH, 1) * wr[3];
            mc.setPosition(bx, by);
        }
    }

    // ========================[MOD] 数据刷新（getCachedXxx 只读） ========================

    private void startDataRefresh() {
        stopDataRefresh();
        dataHandler = new Handler(Looper.getMainLooper());
        dataRunnable = new Runnable() {
            @Override
            public void run() {
                refreshModuleData();
                if (dataHandler != null) dataHandler.postDelayed(this, 800);
            }
        };
        dataHandler.post(dataRunnable);
    }

    private void stopDataRefresh() {
        if (dataHandler != null && dataRunnable != null) {
            dataHandler.removeCallbacks(dataRunnable);
        }
        dataHandler = null;
        dataRunnable = null;
    }

    private void refreshModuleData() {
        if (mockScreen == null || fwm == null) return;
        for (int i = 0; i < mockScreen.getChildCount(); i++) {
            View child = mockScreen.getChildAt(i);
            if (!(child instanceof ModulePreviewContainer)) continue;
            ModulePreviewContainer mc = (ModulePreviewContainer) child;
            String mid = mc.getConfig().moduleId;
            if ("module_speed".equals(mid)) {
                mc.setPreviewData(1, String.valueOf(fwm.getCachedSpeed()));
            } else if ("module_road_name".equals(mid) || "module_cruise_road_name".equals(mid)) {
                mc.setPreviewData(2, fwm.getCachedRoadName());
            } else if ("module_lane_line".equals(mid) || "module_cruise_lane_line".equals(mid)) {
                mc.setPreviewData(3, fwm.getCachedLaneLines());
            } else if ("module_traffic_light".equals(mid)) {
                org.json.JSONArray arr = fwm.getCachedTrafficLights();
                if (arr != null) mc.setPreviewData(5, arr);
                else if (fwm.getCachedLightCountdown() > 0) {
                    mc.setPreviewData(4, new int[]{fwm.getCachedLightStatus(), fwm.getCachedLightDir(), fwm.getCachedLightCountdown()});
                }
            } else if ("module_cruise_traffic_light".equals(mid)) {
                mc.setPreviewData(5, fwm.getCachedTrafficLights());
            } else if ("module_tmc_progress".equals(mid)) {
                mc.setPreviewData(6, fwm.getCachedTmc());
            } else if ("module_eta".equals(mid)) {
                mc.setPreviewData(7, fwm.getCachedEta());
            } else if ("module_exit_info".equals(mid)) {
                mc.setPreviewData(8, fwm.getCachedExit());
            } else if ("module_speed_limit".equals(mid)) {
                mc.setPreviewData(9, fwm.getCachedLimit());
            } else if ("module_camera_distance".equals(mid)) {
                mc.setPreviewData(10, fwm.getCachedCam());
            } else if ("module_turn_distance".equals(mid)) {
                mc.setPreviewData(12, new Object[]{fwm.getCachedDisNum(), fwm.getCachedDisUnit()});
            }
            // 转向图标（module_turn_icon）模拟屏暂不渲染实时图标，真实副屏正常显示
        }
    }

    // ======================== 官方原样工具方法 ========================

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isDarkColor(int color) {
        return ((color >> 16) & 0xFF) * 0.299
                + ((color >> 8) & 0xFF) * 0.587
                + (color & 0xFF) * 0.114 < 100;
    }
}
