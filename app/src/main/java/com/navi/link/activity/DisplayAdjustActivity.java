package com.navi.link.activity;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.navi.link.R;
import com.navi.link.window.FloatingWindowManager;
import com.navi.link.window.ModuleConfig;
import com.navi.link.window.ModuleRegistry;
import com.navi.link.utils.CustomLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 副屏模块调整页
 * 上：模块选择列表（点击添加，可重复添加）
 * 下：当前副屏已添加模块（点击移除）
 */
public class DisplayAdjustActivity extends AppCompatActivity {

    private LinearLayout llModuleSelector;
    private LinearLayout llCurrentModules;
    private TextView tvCurrentHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_adjust);

        llModuleSelector = findViewById(R.id.ll_module_selector);
        llCurrentModules = findViewById(R.id.ll_current_modules);
        tvCurrentHint = findViewById(R.id.tv_current_hint);

        buildModuleSelector();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCurrentModules();
    }

    /** 模块选择列表：全部模块（中文名），点击添加 */
    private void buildModuleSelector() {
        llModuleSelector.removeAllViews();
        for (ModuleRegistry.ModuleInfo info : ModuleRegistry.getAll()) {
            TextView row = new TextView(this);
            row.setText("＋ " + info.name);
            row.setTextSize(16);
            row.setTextColor(0xFF4FC3F7);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setBackgroundResource(R.drawable.bg_scale_btn);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                FloatingWindowManager manager = FloatingWindowManager.getInstance();
                if (manager != null) {
                    CustomLog.d("[调整页] 添加模块: " + info.name + " (" + info.id + ")");
                    manager.addCustomModule(info.id);
                    Toast.makeText(this, "已添加：" + info.name, Toast.LENGTH_SHORT).show();
                    refreshCurrentModules();
                } else {
                    Toast.makeText(this, "窗口管理器未就绪，请稍后重试", Toast.LENGTH_SHORT).show();
                }
            });
            llModuleSelector.addView(row);
        }
    }

    /** 当前已添加模块列表（实例ID + 中文名 + 移除） */
    private void refreshCurrentModules() {
        llCurrentModules.removeAllViews();
        List<ModuleConfig> configs = ModuleConfig.loadAll(this);
        CustomLog.d("[调整页] 当前模块数: " + configs.size());
        if (configs.isEmpty()) {
            tvCurrentHint.setText("当前副屏未添加任何模块");
            return;
        }
        tvCurrentHint.setText("当前已添加 " + configs.size() + " 个模块（点击移除）");
        for (final ModuleConfig cfg : configs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackgroundResource(R.drawable.bg_scale_btn);

            TextView label = new TextView(this);
            String shortId = cfg.instanceId;
            int idx = shortId.lastIndexOf('_');
            if (idx > 0 && shortId.length() > idx + 1) {
                shortId = shortId.substring(idx + 1);
            }
            label.setText(ModuleRegistry.getName(cfg.moduleId) + "  (" + shortId + ")  " + Math.round(cfg.scale * 100) + "%");
            label.setTextSize(14);
            label.setTextColor(0xFFFFFFFF);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);

            TextView btnRemove = new TextView(this);
            btnRemove.setText("移除");
            btnRemove.setTextSize(13);
            btnRemove.setTextColor(0xFFFF6B6B);
            btnRemove.setPadding(dp(10), dp(4), dp(10), dp(4));
            btnRemove.setOnClickListener(v -> {
                FloatingWindowManager manager = FloatingWindowManager.getInstance();
                CustomLog.d("[调整页] 移除模块: " + cfg.instanceId);
                if (manager != null) {
                    manager.removeCustomModule(cfg.instanceId);
                } else {
                    ModuleConfig.saveAll(this, without(configs, cfg));
                }
                refreshCurrentModules();
            });
            row.addView(btnRemove);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            llCurrentModules.addView(row);
        }
    }

    private List<ModuleConfig> without(List<ModuleConfig> list, ModuleConfig target) {
        List<ModuleConfig> result = new java.util.ArrayList<>();
        for (ModuleConfig cfg : list) {
            if (cfg != target && !cfg.instanceId.equals(target.instanceId)) {
                result.add(cfg);
            }
        }
        return result;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
