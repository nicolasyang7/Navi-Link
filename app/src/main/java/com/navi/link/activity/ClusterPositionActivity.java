package com.navi.link.activity;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.navi.link.R;
import com.navi.link.utils.CustomLog;
import com.navi.link.window.FloatingWindowManager;
import com.navi.link.window.ModuleConfig;
import com.navi.link.window.ModuleRegistry;
import com.navi.link.window.ScalableModuleContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * 副屏投屏位置调整 + 模块管理（合并页面）
 * 左侧：可用模块列表（点击添加，可重复）
 * 右侧：模拟副屏（模块方块 = 复用 ScalableModuleContainer，可拖动/缩放/长按删除）
 * 底部：D-Pad 窗口调位 + 保存并退出
 * 映射与官方 refreshIndicator 同一套公式：窗口位置 currentPos/屏幕尺寸×模拟屏尺寸
 */
public class ClusterPositionActivity extends AppCompatActivity {

    private FrameLayout mockScreen;
    private LinearLayout moduleList;
    private TextView tvPosInfo;
    private List<ModuleConfig> moduleConfigs = new ArrayList<>();

    private FloatingWindowManager fwm;
    private int moduleScreenW = 1920;
    private int moduleScreenH = 720;
    private int moduleWindowW = 480;
    private int moduleWindowH = 360;
    private int[] currentPos = new int[]{0, 0};
    private boolean positionLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cluster_position);

        fwm = FloatingWindowManager.getInstance();
        if (fwm == null) {
            finish();
            return;
        }

        mockScreen = findViewById(R.id.mock_screen);
        moduleList = findViewById(R.id.module_list);
        tvPosInfo = findViewById(R.id.tv_pos_info);

        // ===== 屏幕尺寸与窗口尺寸（官方同款逻辑）=====
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
        positionLoaded = true;

        setupControlButtons();
        setupClearAll();
        buildModuleList();

        // 布局完成后加载模块方块
        mockScreen.post(this::loadModulesToMockScreen);
        refreshIndicator();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (positionLoaded) {
            mockScreen.post(this::loadModulesToMockScreen);
            refreshIndicator();
        }
    }

    // ========================底部按键：窗口调位 ========================

    private void setupControlButtons() {
        setBtn(R.id.btn_pos_left, () -> moveWindow(-16, 0));
        setBtn(R.id.btn_pos_up, () -> moveWindow(0, -16));
        setBtn(R.id.btn_pos_right, () -> moveWindow(16, 0));
        setBtn(R.id.btn_pos_down, () -> moveWindow(0, 16));
        setBtn(R.id.btn_pos_center, () -> {
            currentPos[0] = (moduleScreenW - moduleWindowW) / 2;
            currentPos[1] = (moduleScreenH - moduleWindowH) / 2;
            applyWindowPosition();
        });
        setBtn(R.id.btn_pos_done, this::finish);
    }

    private void setBtn(int id, Runnable action) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(vv -> action.run());
    }

    private void moveWindow(int dx, int dy) {
        currentPos[0] = Math.max(0, Math.min(currentPos[0] + dx, moduleScreenW - moduleWindowW));
        currentPos[1] = Math.max(0, Math.min(currentPos[1] + dy, moduleScreenH - moduleWindowH));
        applyWindowPosition();
    }

    private void applyWindowPosition() {
        if (fwm != null) fwm.updateClusterPosition(currentPos[0], currentPos[1]);
        refreshIndicator();
    }

    private void refreshIndicator() {
        if (tvPosInfo != null) {
            tvPosInfo.setText(String.format("当前位置: X = %d px, Y = %d px", currentPos[0], currentPos[1]));
        }
        updateModuleHandleLayouts();
    }

    // ========================左侧：模块列表 ========================

    private void setupClearAll() {
        View btnClearAll = findViewById(R.id.btn_clear_all);
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> {
                moduleConfigs = new ArrayList<>();
                ModuleConfig.saveAll(this, moduleConfigs);
                if (fwm != null) fwm.removeAllCustomModules();
                loadModulesToMockScreen();
                toast("已清空所有模块");
            });
        }
    }

    private void buildModuleList() {
        if (moduleList == null) return;
        moduleList.removeAllViews();
        for (ModuleRegistry.ModuleInfo info : ModuleRegistry.getAll()) {
            TextView row = new TextView(this);
            row.setText("＋ " + info.name);
            row.setTextSize(13);
            row.setTextColor(0xFF4FC3F7);
            row.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x22FFFFFF);
            bg.setCornerRadius(dpToPx(6));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dpToPx(6);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                if (fwm != null) {
                    fwm.addCustomModule(info.id);
                    toast("已添加：" + info.name);
                    loadModulesToMockScreen();
                }
            });
            moduleList.addView(row);
        }
    }

    // ========================右侧：模拟副屏模块方块 ========================

    /** 加载已保存模块到模拟屏（复用 ScalableModuleContainer，含缩放条） */
    private void loadModulesToMockScreen() {
        if (mockScreen == null) return;
        mockScreen.removeAllViews();
        moduleConfigs = ModuleConfig.loadAll(this);

        if (moduleConfigs.isEmpty()) {
            TextView emptyHint = new TextView(this);
            emptyHint.setText("点击左侧模块添加到副屏");
            emptyHint.setTextColor(0x66FFFFFF);
            emptyHint.setTextSize(16);
            emptyHint.setGravity(Gravity.CENTER);
            mockScreen.addView(emptyHint, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            return;
        }

        for (ModuleConfig cfg : moduleConfigs) {
            addModuleToMockScreen(cfg);
        }
    }

    private void addModuleToMockScreen(final ModuleConfig cfg) {
        final ScalableModuleContainer mc = new ScalableModuleContainer(this, cfg);
        mc.setOnConfigChangeListener(conf -> {
            // 容器拖动/缩放后：模拟屏坐标反算回副屏窗口相对坐标，保存并实时同步
            float[] wr = windowRectOnMockScreen();
            conf.x = Math.round((mc.getX() - wr[0]) / Math.max(wr[2], 1) * moduleWindowW);
            conf.y = Math.round((mc.getY() - wr[1]) / Math.max(wr[3], 1) * moduleWindowH);
            ModuleConfig.saveAll(this, moduleConfigs);
            if (fwm != null) fwm.updateModuleConfig(conf.instanceId, conf.x, conf.y, conf.scale);
        });

        // 布局完成后：按官方映射放置初始位置 + 限定拖动边界在模拟屏内
        mockScreen.post(() -> {
            if (mc.getParent() == null) return;
            mc.setDragBounds(mockScreen.getWidth(), mockScreen.getHeight());
            float[] wr = windowRectOnMockScreen();
            float bx = wr[0] + (float) cfg.x / Math.max(moduleWindowW, 1) * wr[2];
            float by = wr[1] + (float) cfg.y / Math.max(moduleWindowH, 1) * wr[3];
            bx = Math.max(0, Math.min(bx, mockScreen.getWidth() - mc.getWidth()));
            by = Math.max(0, Math.min(by, mockScreen.getHeight() - mc.getHeight()));
            mc.setX(bx);
            mc.setY(by);
        });

        // 长按删除
        mc.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("删除模块")
                    .setMessage("确定删除「" + ModuleRegistry.getName(cfg.moduleId) + "」吗？")
                    .setPositiveButton("删除", (d, w) -> {
                        if (fwm != null) fwm.removeCustomModule(cfg.instanceId);
                        loadModulesToMockScreen();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        mockScreen.addView(mc, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    /** 窗口移动/尺寸变化时，模块方块联动（窗口位置 + 模块相对窗口坐标，官方同款映射） */
    private void updateModuleHandleLayouts() {
        if (mockScreen == null) return;
        for (int i = 0; i < mockScreen.getChildCount(); i++) {
            View child = mockScreen.getChildAt(i);
            if (child instanceof ScalableModuleContainer) {
                ScalableModuleContainer mc = (ScalableModuleContainer) child;
                ModuleConfig cfg = mc.getConfig();
                float[] wr = windowRectOnMockScreen();
                float bx = wr[0] + (float) cfg.x / Math.max(moduleWindowW, 1) * wr[2];
                float by = wr[1] + (float) cfg.y / Math.max(moduleWindowH, 1) * wr[3];
                bx = Math.max(0, Math.min(bx, mockScreen.getWidth() - mc.getWidth()));
                by = Math.max(0, Math.min(by, mockScreen.getHeight() - mc.getHeight()));
                mc.setX(bx);
                mc.setY(by);
            }
        }
    }

    /** 窗口在模拟屏上的矩形 —— 与官方 refreshIndicator 同一套公式 */
    private float[] windowRectOnMockScreen() {
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

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("unused")
    private Context getCtx() {
        return this;
    }
}
