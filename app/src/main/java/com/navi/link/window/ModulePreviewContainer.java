package com.navi.link.window;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.navi.link.R;
import com.navi.link.view.CameraWarningView;
import com.navi.link.view.LaneLineView;
import com.navi.link.view.TmcProgressBar;
import com.navi.link.view.TrafficLightView;

import org.json.JSONArray;
import org.json.JSONObject;

/**副屏/模拟屏模块容器（V13.16）：对齐车机助手 HUD 样式——卡片圆角背景 + 右下角对角线缩放手柄 + 连续缩放 */
public class ModulePreviewContainer extends FrameLayout {

    public interface OnConfigChangeListener {
        void onConfigChanged(ModuleConfig config);
    }

    public static final float MIN_SCALE = 0.3f;
    public static final float MAX_SCALE = 3.0f;

    private final ModuleConfig config;
    private View contentView;
    private ImageView standbyIcon;
    private FrameLayout resizeHandle;
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
    // 缩放（连续，车机助手算法）
    private float scaleStartX = 0f;
    private float scaleStartY = 0f;
    private float startScale = 1f;
    private boolean scaling = false;

    public ModulePreviewContainer(Context context, ModuleConfig config) {
        super(context);
        this.config = config;

        ModuleRegistry.ModuleInfo info = ModuleRegistry.get(config.moduleId);
        if (info == null) return;

        // 卡片背景：车机助手 bg_cluster_normal（#33CCCCCC + 圆角8dp + #CCCCCC 1dp 描边）
        GradientDrawable card = new GradientDrawable();
        card.setShape(GradientDrawable.RECTANGLE);
        card.setColor(0x33CCCCCC);
        card.setCornerRadius(dp(8));
        card.setStroke(dp(1), 0xFFCCCCCC);
        setBackground(card);

        contentView = inflate(context, info.layoutRes, null);
        addView(contentView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        // 待机图标（底层，半透明，线框风格）
        if (info.iconRes != 0) {
            standbyIcon = new ImageView(context);
            standbyIcon.setImageResource(info.iconRes);
            standbyIcon.setAlpha(0.45f);
            addView(standbyIcon, new FrameLayout.LayoutParams(dp(48), dp(48)));
        }

        buildResizeHandle(context);
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

    /**外部统一缩放 */
    public void applyScale(float newScale) {
        applyInitialScale(newScale);
        if (listener != null) {
            listener.onConfigChanged(config);
        }
    }

    private void applyInitialScale(float newScale) {
        float clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        config.scale = clamped;
        if (contentView != null) {
            contentView.post(() -> {
                // 车机助手：pivot 左上角 (0,0)
                contentView.setPivotX(0);
                contentView.setPivotY(0);
                contentView.setScaleX(clamped);
                contentView.setScaleY(clamped);
            });
        }
    }

    /**右下角缩放手柄：车机助手样式——24×24 画对角线角标 + 48dp 触摸热区 */
    private void buildResizeHandle(Context context) {
        resizeHandle = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.BOTTOM | Gravity.END);
        addView(resizeHandle, lp);
        resizeHandle.bringToFront();

        // 内部 24×24 对角线角标（居中在 48dp 热区里）
        View handleIcon = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                Paint paint = new Paint();
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(dp(2));
                paint.setAntiAlias(true);
                int w = getWidth();
                int h = getHeight();
                canvas.drawLine(w * 0.3f, h, w, h * 0.3f, paint);
                canvas.drawLine(w * 0.6f, h, w, h * 0.6f, paint);
            }
        };
        handleIcon.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                dp(24), dp(24), Gravity.CENTER);
        resizeHandle.addView(handleIcon, iconLp);

        resizeHandle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scaling = false;
                    scaleStartX = event.getRawX();
                    scaleStartY = event.getRawY();
                    startScale = config.scale;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float deltaX = event.getRawX() - scaleStartX;
                    float deltaY = event.getRawY() - scaleStartY;
                    float effectiveDelta = Math.abs(deltaX) > Math.abs(deltaY) ? deltaX : deltaY;
                    if (Math.abs(effectiveDelta) > dp(4)) {
                        scaling = true;
                        float baseWidth = contentView != null && contentView.getWidth() > 0
                                ? contentView.getWidth() : dp(100);
                        float newScale = startScale + (effectiveDelta / baseWidth);
                        newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
                        applyInitialScale(newScale);
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

    /**点击模块本体 = 拖动（缩放手柄外的区域） */
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
