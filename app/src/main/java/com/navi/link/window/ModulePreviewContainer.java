package com.navi.link.window;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.navi.link.R;

/**
 * 轻量级模块预览容器（V12）
 * 只依赖 ModuleConfig + 布局资源，不持有 FloatingWindowManager 引用。
 * 用于：调位页面模拟屏幕 + 真实副屏模块渲染（同一组件）。
 * 数据由外部通过 setPreviewData 被动注入（读取 getCachedXxx 后调用）。
 */
public class ModulePreviewContainer extends FrameLayout {

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

    // TMC 专属控件
    private ImageButton btnLengthMinus;
    private ImageButton btnLengthPlus;
    private TextView btnOrientation;

    // 拖拽
    private float downRawX = 0f;
    private float downRawY = 0f;
    private float startX = 0f;
    private float startY = 0f;
    private boolean dragging = false;

    // 拖动边界（由外部设置，如模拟屏幕区域）
    private float dragBoundW = -1;
    private float dragBoundH = -1;

    public ModulePreviewContainer(Context context, ModuleConfig config) {
        super(context);
        this.config = config;
        ModuleRegistry.ModuleInfo info = ModuleRegistry.get(config.moduleId);
        if (info == null) return;

        contentView = inflate(context, info.layoutRes, null);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        addView(contentView, contentLp);

        buildControlBar(context);
        setupDrag();
        post(() -> applyScale(config.scale));
        applyLengthOrientation();
    }

    public ModuleConfig getConfig() {
        return config;
    }

    public void setOnConfigChangeListener(OnConfigChangeListener listener) {
        this.listener = listener;
    }

    /** 设置拖动边界（不设置则默认屏幕边界） */
    public void setDragBounds(float w, float h) {
        dragBoundW = w;
        dragBoundH = h;
    }

    /** 设置位置（模拟屏幕坐标），不回调保存 */
    public void setPosition(float x, float y) {
        setX(x);
        setY(y);
    }

    /** 应用配置中的位置（相对父容器） */
    public void applyPositionFromConfig() {
        setX(config.x);
        setY(config.y);
    }

    private void buildControlBar(Context context) {
        scaleBar = new LinearLayout(context);
        scaleBar.setOrientation(LinearLayout.HORIZONTAL);
        scaleBar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(0x99000000);
        barBg.setCornerRadius(dp(6));
        scaleBar.setBackground(barBg);
        scaleBar.setPadding(dp(2), dp(2), dp(2), dp(2));

        boolean isTmc = "module_tmc_progress".equals(config.moduleId);

        if (isTmc) {
            // TMC 专属：长度调节 + 横竖切换
            btnLengthMinus = new ImageButton(context);
            btnLengthMinus.setImageResource(R.drawable.ic_scale_minus);
            btnLengthMinus.setBackgroundResource(R.drawable.bg_scale_btn);
            btnLengthMinus.setPadding(dp(4), dp(4), dp(4), dp(4));
            btnLengthMinus.setOnClickListener(v -> {
                config.length = Math.max(0.25f, config.length - 0.25f);
                applyLengthOrientation();
                notifyChange();
            });
            scaleBar.addView(btnLengthMinus, new LinearLayout.LayoutParams(dp(24), dp(24)));

            btnOrientation = new TextView(context);
            btnOrientation.setText("横竖");
            btnOrientation.setTextColor(Color.WHITE);
            btnOrientation.setTextSize(10);
            btnOrientation.setGravity(Gravity.CENTER);
            btnOrientation.setBackgroundResource(R.drawable.bg_scale_btn);
            btnOrientation.setOnClickListener(v -> {
                config.orientation = 1 - config.orientation;
                applyLengthOrientation();
                notifyChange();
            });
            scaleBar.addView(btnOrientation, new LinearLayout.LayoutParams(dp(28), dp(24)));

            btnLengthPlus = new ImageButton(context);
            btnLengthPlus.setImageResource(R.drawable.ic_scale_plus);
            btnLengthPlus.setBackgroundResource(R.drawable.bg_scale_btn);
            btnLengthPlus.setPadding(dp(4), dp(4), dp(4), dp(4));
            btnLengthPlus.setOnClickListener(v -> {
                config.length = Math.min(3.0f, config.length + 0.25f);
                applyLengthOrientation();
                notifyChange();
            });
            scaleBar.addView(btnLengthPlus, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }

        btnMinus = new ImageButton(context);
        btnMinus.setImageResource(R.drawable.ic_scale_minus);
        btnMinus.setBackgroundResource(R.drawable.bg_scale_btn);
        btnMinus.setPadding(dp(6), dp(6), dp(6), dp(6));
        btnMinus.setOnClickListener(v -> applyScale(config.scale - SCALE_STEP));
        scaleBar.addView(btnMinus, new LinearLayout.LayoutParams(dp(28), dp(28)));

        tvScalePercent = new TextView(context);
        tvScalePercent.setTextColor(Color.WHITE);
        tvScalePercent.setTextSize(10);
        tvScalePercent.setGravity(Gravity.CENTER);
        tvScalePercent.setMinWidth(dp(44));
        tvScalePercent.setText("100%");
        scaleBar.addView(tvScalePercent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        btnPlus = new ImageButton(context);
        btnPlus.setImageResource(R.drawable.ic_scale_plus);
        btnPlus.setBackgroundResource(R.drawable.bg_scale_btn);
        btnPlus.setPadding(dp(6), dp(6), dp(6), dp(6));
        btnPlus.setOnClickListener(v -> applyScale(config.scale + SCALE_STEP));
        scaleBar.addView(btnPlus, new LinearLayout.LayoutParams(dp(28), dp(28)));

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        barLp.setMargins(0, 0, dp(6), dp(6));
        addView(scaleBar, barLp);
        scaleBar.bringToFront();
    }

    private void applyScale(float newScale) {
        float clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        config.scale = clamped;
        if (contentView != null) {
            contentView.post(() -> {
                if (contentView.getWidth() > 0) {
                    contentView.setPivotX(contentView.getWidth() / 2f);
                    contentView.setPivotY(contentView.getHeight() / 2f);
                }
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
        notifyChange();
    }

    /** TMC 专属：应用长度因子与横竖方向 */
    public void applyLengthOrientation() {
        if (contentView == null) return;
        if ("module_tmc_progress".equals(config.moduleId)) {
            contentView.post(() -> {
                int w = contentView.getWidth();
                int h = contentView.getHeight();
                if (w <= 0) w = dp(180);
                if (h <= 0) h = dp(8);
                float len = Math.max(0.25f, Math.min(3.0f, config.length));
                if (config.orientation == 0) {
                    ViewGroup.LayoutParams lp = contentView.getLayoutParams();
                    if (lp != null) {
                        lp.width = Math.round(w * len);
                        lp.height = h;
                    }
                    contentView.setRotation(0);
                } else {
                    ViewGroup.LayoutParams lp = contentView.getLayoutParams();
                    if (lp != null) {
                        lp.width = Math.round(h * len);
                        lp.height = Math.round(w * len);
                    }
                    contentView.setRotation(90);
                }
            });
        }
    }

    private void setupDrag() {
        setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = getX();
                    startY = getY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (!dragging && (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4))) {
                        dragging = true;
                    }
                    if (dragging) {
                        float newX = startX + dx;
                        float newY = startY + dy;
                        float limitW = dragBoundW > 0 ? dragBoundW : getResources().getDisplayMetrics().widthPixels;
                        float limitH = dragBoundH > 0 ? dragBoundH : getResources().getDisplayMetrics().heightPixels;
                        float actualW = Math.max(getWidth(), 1) * config.scale;
                        float actualH = Math.max(getHeight(), 1) * config.scale;
                        newX = Math.max(0, Math.min(newX, limitW - actualW));
                        newY = Math.max(0, Math.min(newY, limitH - actualH));
                        setX(newX);
                        setY(newY);
                        config.x = getX();
                        config.y = getY();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging && listener != null) {
                        listener.onConfigChanged(config);
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private void notifyChange() {
        if (listener != null) {
            listener.onConfigChanged(config);
        }
    }

    /** 数据注入：由外部（getCachedXxx 读取后）调用 */
    public void setPreviewData(int type, Object data) {
        if (contentView == null) return;
        switch (type) {
            case 1: // 速度
                TextView tvSpeed = contentView.findViewById(R.id.tv_module_speed);
                if (tvSpeed != null) tvSpeed.setText(String.valueOf(data));
                break;
            case 2: // 道路名
                TextView tvRoad = contentView.findViewById(R.id.tv_module_road_name);
                if (tvRoad != null) tvRoad.setText(String.valueOf(data));
                break;
            case 3: // 车道线
                com.navi.link.view.LaneLineView lv = contentView.findViewById(R.id.lane_line_module);
                if (lv != null && data != null) lv.updateLanes(String.valueOf(data));
                break;
            case 4: // 红绿灯（导航单灯）
                LinearLayout container = contentView.findViewById(R.id.ll_module_traffic_light);
                if (container != null && data != null) {
                    container.removeAllViews();
                    com.navi.link.view.TrafficLightView tv = new com.navi.link.view.TrafficLightView(getContext());
                    container.addView(tv);
                    int[] arr = (int[]) data;
                    tv.setData(arr[0], arr[1], arr[2], true);
                }
                break;
            case 5: // 巡航红绿灯（多灯）
                LinearLayout container2 = contentView.findViewById(R.id.ll_module_traffic_light);
                if (container2 != null && data != null) {
                    container2.removeAllViews();
                    org.json.JSONArray arr = (org.json.JSONArray) data;
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            org.json.JSONObject o = arr.getJSONObject(i);
                            com.navi.link.view.TrafficLightView tv = new com.navi.link.view.TrafficLightView(getContext());
                            container2.addView(tv);
                            int status = o.getInt("status");
                            int countdown = o.getInt("countdown");
                            int dir = o.getInt("dir");
                            if (countdown > 0) tv.setData(status, dir, countdown, false);
                            else tv.setVisibility(View.GONE);
                        } catch (Exception ignored) {
                        }
                    }
                }
                break;
            case 6: // TMC
                com.navi.link.view.TmcProgressBar tmc = contentView.findViewById(R.id.tmc_module);
                if (tmc != null && data != null) tmc.updateTmcData(String.valueOf(data));
                break;
            case 7: // ETA
                TextView tvEta = contentView.findViewById(R.id.tv_module_eta);
                if (tvEta != null) tvEta.setText(String.valueOf(data));
                break;
            case 8: // 出口信息
                TextView tvExit = contentView.findViewById(R.id.tv_module_exit_info);
                if (tvExit != null) {
                    String s = String.valueOf(data);
                    if (s.isEmpty()) tvExit.setVisibility(View.GONE);
                    else {
                        tvExit.setText(s);
                        tvExit.setVisibility(View.VISIBLE);
                    }
                }
                break;
            case 9: // 限速
                TextView tvLimit = contentView.findViewById(R.id.tv_module_speed_limit);
                if (tvLimit != null) {
                    int v = (Integer) data;
                    if (v > 0) {
                        tvLimit.setText(String.valueOf(v));
                        tvLimit.setVisibility(View.VISIBLE);
                    } else {
                        tvLimit.setVisibility(View.GONE);
                    }
                }
                break;
            case 10: // 电子眼距离
                com.navi.link.view.CameraWarningView cam = contentView.findViewById(R.id.camera_module);
                if (cam != null && data != null) {
                    int[] arr = (int[]) data;
                    cam.updateCameraInfo(arr[0], arr[1], arr[2]);
                }
                break;
            case 11: // 转向图标
                android.widget.ImageView iv = contentView.findViewById(R.id.iv_module_turn_icon);
                if (iv != null && data != null) iv.setImageResource((Integer) data);
                break;
            case 12: // 转向距离
                TextView tvDisNum = contentView.findViewById(R.id.tv_module_distance_num);
                if (tvDisNum != null && data != null) {
                    Object[] arr = (Object[]) data;
                    tvDisNum.setText(String.valueOf(arr[0]));
                    TextView tvDisUnit = contentView.findViewById(R.id.tv_module_distance_unit);
                    if (tvDisUnit != null) tvDisUnit.setText(String.valueOf(arr[1]));
                }
                break;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
