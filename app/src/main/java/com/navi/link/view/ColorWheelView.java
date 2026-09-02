package com.navi.link.view;
import com.navi.link.R;
import com.navi.link.BuildConfig;
import com.navi.link.activity.*;
import com.navi.link.delegate.*;
import com.navi.link.window.*;
import com.navi.link.view.*;
import com.navi.link.receiver.*;
import com.navi.link.service.*;
import com.navi.link.utils.*;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorWheelView extends View {

    public interface OnColorSelectedListener {
        void onColorSelected(float hue, float saturation);
    }

    private Paint huePaint;
    private Paint saturationPaint;
    private Paint pointerPaint;

    private float centerX;
    private float centerY;
    private float radius;

    private float currentHue = 0f;
    private float currentSaturation = 0f;

    private OnColorSelectedListener listener;

    public ColorWheelView(Context context) {
        super(context);
        init();
    }

    public ColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorWheelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        saturationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointerPaint.setStyle(Paint.Style.STROKE);
        pointerPaint.setStrokeWidth(6f);
        pointerPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(centerX, centerY) - 15f;

        int[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
        Shader sweepShader = new SweepGradient(centerX, centerY, colors, null);
        huePaint.setShader(sweepShader);

        Shader radialShader = new RadialGradient(centerX, centerY, radius, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP);
        saturationPaint.setShader(radialShader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(centerX, centerY, radius, huePaint);
        canvas.drawCircle(centerX, centerY, radius, saturationPaint);
        drawPointer(canvas);
    }

    private void drawPointer(Canvas canvas) {
        double r = currentSaturation * radius;
        double angleRad = Math.toRadians(currentHue);
        float px = (float) (centerX + r * Math.cos(angleRad));
        float py = (float) (centerY + r * Math.sin(angleRad));

        canvas.drawCircle(px, py, 14f, pointerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                updateSelector(event.getX(), event.getY());
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateSelector(float touchX, float touchY) {
        float dx = touchX - centerX;
        float dy = touchY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance > radius) {
            distance = radius;
        }

        currentSaturation = (float) (distance / radius);

        double angleRad = Math.atan2(dy, dx);
        float angleDeg = (float) Math.toDegrees(angleRad);
        currentHue = (angleDeg + 360f) % 360f;

        invalidate();

        if (listener != null) {
            listener.onColorSelected(currentHue, currentSaturation);
        }
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }

    public void setColor(float hue, float saturation) {
        this.currentHue = hue;
        this.currentSaturation = saturation;
        invalidate();
    }
}
