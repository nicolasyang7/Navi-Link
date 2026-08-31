package com.navi.link.window;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.navi.link.R;
import com.navi.link.utils.CustomLog;

/**
 * 可缩放、可拖动的副屏模块容器（继承 FrameLayout）
 * 右下角叠加缩放控制条（− 比例 +），拖动调整位置，配置变化实时回调保存
 * 缩放范围 0.25X ~ 3.0X，步长 0.25X，缩放中心为模块几何中心
 */
public class ScalableModuleContainer extends FrameLayout {

    public interface OnConfigChangeListener {
        void onConfigChanged(ModuleConfig config);
    }

    public static final float MIN_SCALE = 0.25f;
    public static final float MAX_SCALE = 3.0f;
    public static final float SCALE_STEP = 0.25f;

    private final ModuleConfig config;
    private View contentView;
    private LinearLayout scaleBar;
    private TextView tvScalePercent;
    private ImageButton btnMinus;
    private ImageButton btnPlus;
    private OnConfigChangeListener listener;

    private float downRawX = 0f;
    private float downRawY = 0f;
    private float startX = 0f;
    private float startY = 0f;
    private boolean dragging = false;
    // =====[MOD-BEGIN]副屏模块自定义系统：模拟屏拖动边界=====
    private float dragBoundW = -1f;
    private float dragBoundH = -1f;

    public void setDragBounds(float w, float h) {
        dragBoundW = w;
        dragBoundH = h;
    }
    // =====[MOD-END]=====
    private boolean touchOnBar = false;
    private int themeColor = 0;

    public ScalableModuleContainer(Context context, ModuleConfig config) {
        super(context);
        this.config = config;

        ModuleRegistry.ModuleInfo info = ModuleRegistry.get(config.moduleId);
        if (info == null) return;

        contentView = inflate(context, info.layoutRes, null);
        addView(contentView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        CustomLog.d("[容器] 创建: " + config.moduleId + " (实例 " + config.instanceId + ") 布局=" + info.layoutRes);

        // 跟随主题色（用于控制条背景）
        FloatingWindowManager fwm = FloatingWindowManager.getInstance();
        if (fwm != null) themeColor = fwm.getThemeColor();

        buildScaleBar(context);
        setupDrag();

        // 缩放中心：布局变化时重设 pivot（修复 post 时机不可靠导致的缩放偏移）
        contentView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (v.getWidth() > 0) {
                v.setPivotX(v.getWidth() / 2f);
                v.setPivotY(v.getHeight() / 2f);
            }
        });
        // 立即应用缩放比例（无需等布局完成），避免恢复配置时先显示原始尺寸再跳跃缩放
        // setScaleX/Y 在未 layout 时同样生效，pivot 由 onLayoutChangeListener 修正为中心
        contentView.setScaleX(config.scale);
        contentView.setScaleY(config.scale);
        if (contentView.getWidth() > 0) {
            contentView.setPivotX(contentView.getWidth() / 2f);
            contentView.setPivotY(contentView.getHeight() / 2f);
        }
        // 兜底：确保按钮状态/比例显示/持久化与缩放值一致
        post(() -> applyScale(config.scale));
    }

    public ModuleConfig getConfig() {
        return config;
    }

    // =====[MOD-BEGIN]副屏模块自定义系统：调整页模拟屏远程控制=====
    /** 供调整页模拟屏远程设置缩放比例 */
    public void setScale(float newScale) {
        applyScale(newScale);
    }

    /** 供调整页模拟屏远程设置位置（相对模块容器坐标） */
    public void setPosition(float x, float y) {
        setX(x);
        setY(y);
        config.x = x;
        config.y = y;
    }
    // =====[MOD-END]=====

    public void setOnConfigChangeListener(OnConfigChangeListener listener) {
        this.listener = listener;
    }

    private void buildScaleBar(Context context) {
        scaleBar = new LinearLayout(context);
        LinearLayout bar = scaleBar;
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable barBg = new GradientDrawable();
        if (themeColor != 0) {
            barBg.setColor(Color.argb(0x99, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor)));
        } else {
            barBg.setColor(0x99000000);
        }
        barBg.setCornerRadius(dp(6));
        bar.setBackground(barBg);
        bar.setPadding(dp(2), dp(2), dp(2), dp(2));

        btnMinus = new ImageButton(context);
        btnMinus.setImageResource(R.drawable.ic_scale_minus);
        btnMinus.setBackground(createBtnBg());
        btnMinus.setPadding(dp(5), dp(5), dp(5), dp(5));
        btnMinus.setOnClickListener(v -> applyScale(config.scale - SCALE_STEP));
        bar.addView(btnMinus, new LinearLayout.LayoutParams(dp(28), dp(28)));

        tvScalePercent = new TextView(context);
        tvScalePercent.setTextColor(Color.WHITE);
        tvScalePercent.setTextSize(10);
        tvScalePercent.setGravity(Gravity.CENTER);
        tvScalePercent.setMinWidth(dp(42));
        tvScalePercent.setText("100%");
        bar.addView(tvScalePercent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        btnPlus = new ImageButton(context);
        btnPlus.setImageResource(R.drawable.ic_scale_plus);
        btnPlus.setBackground(createBtnBg());
        btnPlus.setPadding(dp(5), dp(5), dp(5), dp(5));
        btnPlus.setOnClickListener(v -> applyScale(config.scale + SCALE_STEP));
        bar.addView(btnPlus, new LinearLayout.LayoutParams(dp(28), dp(28)));

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        barLp.setMargins(0, 0, dp(6), dp(6));
        addView(bar, barLp);
        bar.bringToFront();
    }

    /** 应用缩放比例（缩放内容 View，pivot 为内容几何中心） */
    private void applyScale(float newScale) {
        float clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        config.scale = clamped;
        CustomLog.d("[容器] 缩放: " + config.moduleId + " → " + Math.round(clamped * 100) + "%");

        if (contentView != null) {
            contentView.post(() -> {
                contentView.setPivotX(contentView.getWidth() / 2f);
                contentView.setPivotY(contentView.getHeight() / 2f);
                contentView.setScaleX(clamped);
                contentView.setScaleY(clamped);
            });
        }

        if (tvScalePercent != null) {
            tvScalePercent.setText(Math.round(clamped * 100) + "%");
        }
        if (btnMinus != null) {
            boolean atMin = clamped <= MIN_SCALE;
            btnMinus.setEnabled(!atMin);
            btnMinus.setAlpha(atMin ? 0.3f : 1f);
        }
        if (btnPlus != null) {
            boolean atMax = clamped >= MAX_SCALE;
            btnPlus.setEnabled(!atMax);
            btnPlus.setAlpha(atMax ? 0.3f : 1f);
        }
        if (listener != null) {
            listener.onConfigChanged(config);
        }
    }

    /** 拖动调整位置（setX/setY 相对父容器） */
    private void setupDrag() {
        setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = getX();
                    startY = getY();
                    dragging = false;
                    // 触摸落在缩放控制条区域时，不启动拖动（避免与缩放按钮/比例区干扰）
                    touchOnBar = isTouchInScaleBar(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (touchOnBar) return true;
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (!dragging && (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4))) {
                        dragging = true;
                    }
                    if (dragging) {
                        float newX = startX + dx;
                        float newY = startY + dy;
                        // 边界限制：优先限定在 setDragBounds 指定的区域（如模拟屏），否则用屏幕
                        float actualW = Math.max(getWidth(), 1) * config.scale;
                        float actualH = Math.max(getHeight(), 1) * config.scale;
                        float limitW = dragBoundW > 0 ? dragBoundW : getResources().getDisplayMetrics().widthPixels;
                        float limitH = dragBoundH > 0 ? dragBoundH : getResources().getDisplayMetrics().heightPixels;
                        newX = Math.max(0, Math.min(newX, limitW - actualW));
                        newY = Math.max(0, Math.min(newY, limitH - actualH));
                        setX(newX);
                        setY(newY);
                        config.x = getX();
                        config.y = getY();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        CustomLog.d("[容器] 拖动保存: " + config.moduleId + " → (" + Math.round(config.x) + "," + Math.round(config.y) + ")");
                    }
                    if (dragging && listener != null) {
                        listener.onConfigChanged(config);
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    /** 缩放按钮背景：跟随主题色（半透明），无主题色时回退黑色半透明 */
    private GradientDrawable createBtnBg() {
        GradientDrawable bg = new GradientDrawable();
        if (themeColor != 0) {
            bg.setColor(Color.argb(0x66, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor)));
        } else {
            bg.setColor(0x55000000);
        }
        bg.setCornerRadius(dp(4));
        return bg;
    }

    /** 判断触摸点是否落在缩放控制条区域内 */
    private boolean isTouchInScaleBar(MotionEvent event) {
        if (scaleBar == null) return false;
        int[] loc = new int[2];
        scaleBar.getLocationOnScreen(loc);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= loc[0] && x <= loc[0] + scaleBar.getWidth()
                && y >= loc[1] && y <= loc[1] + scaleBar.getHeight();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
