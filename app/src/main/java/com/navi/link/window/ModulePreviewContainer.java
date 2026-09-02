package com.navi.link.window;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.navi.link.R;
import com.navi.link.view.CameraWarningView;
import com.navi.link.view.LaneLineView;
import com.navi.link.view.TmcProgressBar;
import com.navi.link.view.TrafficLightView;

import org.json.JSONArray;
import org.json.JSONObject;

/**副屏/模拟屏模块容器（V13.15）：待机图标 + 数据层；点击模块本体拖动；右下角小圆点拉拽缩放（无双指） */
public class ModulePreviewContainer extends FrameLayout {

    public interface OnConfigChangeListener {
        void onConfigChanged(ModuleConfig config);
    }

    public static final float MIN_SCALE = 0.25f;
    public static final float MAX_SCALE = 3.0f;
    public static final float SCALE_STEP = 0.25f;

    private final ModuleConfig config;
    private View contentView;
    private ImageView standbyIcon;
    private FrameLayout scaleDot;
    private OnConfigChangeListener listener;

    private float dragBoundW = -1;
    private float dragBoundH = -1;
    // 拖动
    private float downRawX = 0f;
    private float downRawY = 0f;
    private float startX = 0f;
    private float startY = 0f;
    private boolean dragging = false;
    private boolean moved = false;
    // 缩放（小点拉拽）
    private float scaleAnchorDist = 0f;
    private float scaleAnchorX = 0f;
    private float scaleAnchorY = 0f;
    private boolean scaling = false;

    public ModulePreviewContainer(Context context, ModuleConfig config) {
        super(context);
        this.config = config;

        ModuleRegistry.ModuleInfo info = ModuleRegistry.get(config.moduleId);
        if (info == null) return;

        contentView = inflate(context, info.layoutRes, null);
        addView(contentView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        // 待机图标（底层，半透明水印）
        if (info.iconRes != 0) {
            standbyIcon = new ImageView(context);
            standbyIcon.setImageResource(info.iconRes);
            standbyIcon.setAlpha(0.45f);
            addView(standbyIcon, new FrameLayout.LayoutParams(dp(48), dp(48)));
        }

        buildScaleDot(context);
        setupDrag();

        post(() -> applyInitialScale(config.scale));
    }

    public ModuleConfig getConfig() {
        return config;
    }

    public void setOnConfigChangeListener(OnConfigChangeListener l) {
        this.listener = l;
    }

    public void setDragBounds(float w, float h) {
        dragBoundW = w;
        dragBoundH = h;
    }

    /**仅设置位置（不改 config 坐标） */
    public void setPosition(float x, float y) {
        setX(x);
        setY(y);
    }

    private void applyInitialScale(float newScale) {
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
    }

    /**右下角缩放小点：视觉 20dp 圆点 + 48dp 透明触摸热区（车机友好），拉拽缩放 */
    private void buildScaleDot(Context context) {
        scaleDot = new FrameLayout(context);
        // 透明触摸热区 48dp
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.BOTTOM | Gravity.END);
        addView(scaleDot, lp);
        scaleDot.bringToFront();

        // 内部视觉小圆点 20dp，居中
        View dot = new View(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xCCFFFFFF);
        bg.setStroke(dp(1), 0x55000000);
        dot.setBackground(bg);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(
                dp(20), dp(20), Gravity.CENTER);
        scaleDot.addView(dot, dotLp);

        scaleDot.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scaling = false;
                    scaleAnchorX = getX() + getWidth() / 2f;
                    scaleAnchorY = getY() + getHeight() / 2f;
                    scaleAnchorDist = distance(event.getRawX(), event.getRawY(), scaleAnchorX, scaleAnchorY);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float d = distance(event.getRawX(), event.getRawY(), scaleAnchorX, scaleAnchorY);
                    float delta = d - scaleAnchorDist;
                    if (Math.abs(delta) > dp(16)) {
                        scaling = true;
                        float ns = delta > 0 ? config.scale + SCALE_STEP : config.scale - SCALE_STEP;
                        applyInitialScale(ns);
                        scaleAnchorDist = d;
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (scaling && listener != null) {
                        listener.onConfigChanged(config);
                    }
                    scaling = false;
                    return true;
                default:
                    return false;
            }
        });
    }

    /**点击模块本体 = 拖动（缩放小点外的区域） */
    private void setupDrag() {
        setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = getX();
                    startY = getY();
                    dragging = false;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (!dragging && (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4))) {
                        dragging = true;
                    }
                    if (dragging) {
                        moved = true;
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
                case MotionEvent.ACTION_CANCEL:
                    if (dragging && moved && listener != null) {
                        listener.onConfigChanged(config);
                    }
                    dragging = false;
                    return true;
                default:
                    return false;
            }
        });
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**模拟屏数据预览：车道线 */
    public void setPreviewLaneLines(String json) {
        View v = contentView != null ? contentView.findViewById(R.id.lane_line_module) : null;
        if (v instanceof LaneLineView && json != null) {
            ((LaneLineView) v).updateLanes(json);
        }
    }

    /**模拟屏数据预览：TMC */
    public void setPreviewTmc(String json) {
        View v = contentView != null ? contentView.findViewById(R.id.tmc_module) : null;
        if (v instanceof TmcProgressBar && json != null) {
            ((TmcProgressBar) v).updateTmcData(json);
        }
    }

    /**模拟屏数据预览：电子眼 */
    public void setPreviewCamera(int type, int dist, int speed) {
        View v = contentView != null ? contentView.findViewById(R.id.camera_module) : null;
        if (v instanceof CameraWarningView) {
            ((CameraWarningView) v).updateCameraInfo(type, dist, speed);
        }
    }

    /**模拟屏数据预览：红绿灯 */
    public void setPreviewTrafficLight(JSONArray lights, int status, int dir, int countdown) {
        try {
            LinearLayout container = contentView != null
                    ? (LinearLayout) contentView.findViewById(R.id.ll_module_traffic_light) : null;
            if (container == null) return;
            container.removeAllViews();
            int count = lights != null ? lights.length() : 0;
            if (count > 0) {
                for (int i = 0; i < count; i++) {
                    try {
                        JSONObject o = lights.getJSONObject(i);
                        int st = o.getInt("status");
                        int cd = o.getInt("countdown");
                        int dr = o.getInt("dir");
                        TrafficLightView lv = new TrafficLightView(getContext());
                        lv.setCompact(count >= 3);
                        container.addView(lv);
                        if (cd > 0) {
                            lv.setData(st, dr, cd, false);
                        } else {
                            lv.setVisibility(View.GONE);
                        }
                    } catch (Exception ignored) {
                    }
                }
                container.setVisibility(View.VISIBLE);
            } else if (countdown > 0) {
                TrafficLightView lv = new TrafficLightView(getContext());
                container.addView(lv);
                lv.setData(status, dir, countdown, true);
                container.setVisibility(View.VISIBLE);
            } else {
                container.setVisibility(View.GONE);
            }
        } catch (Exception ignored) {
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
