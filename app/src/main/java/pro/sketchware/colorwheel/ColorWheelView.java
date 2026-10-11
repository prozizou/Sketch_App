package pro.sketchware.colorwheel;

import android.annotation.SuppressLint;
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

import androidx.annotation.Nullable;

/**
 * A hue / saturation wheel with a ball that follows the finger. Brightness is set outside (a slider) and shown by
 * darkening the wheel.
 */
public class ColorWheelView extends View {
    public interface OnColorChangedListener {
        void onColorChanged(float hue, float saturation, boolean fromUser);
    }

    private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint saturationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ballFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ballRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ballShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] ballHsv = new float[3];
    private float hue;
    private float saturation;
    private float value = 1f;
    private float centerX;
    private float centerY;
    private float radius;
    private final float ballRadius;
    @Nullable
    private OnColorChangedListener listener;

    public ColorWheelView(Context context) {
        this(context, null);
    }

    public ColorWheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        ballRadius = 14 * density;
        darkPaint.setColor(Color.BLACK);
        ballRing.setStyle(Paint.Style.STROKE);
        ballRing.setStrokeWidth(3 * density);
        ballRing.setColor(Color.WHITE);
        ballShadow.setStyle(Paint.Style.STROKE);
        ballShadow.setStrokeWidth(1.5f * density);
        ballShadow.setColor(0x66000000);
    }

    public void setOnColorChangedListener(@Nullable OnColorChangedListener listener) {
        this.listener = listener;
    }

    public void setColor(float hue, float saturation, float value) {
        this.hue = hue;
        this.saturation = saturation;
        this.value = value;
        invalidate();
    }

    public void setValue(float value) {
        this.value = value;
        invalidate();
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Square: as wide as allowed (at most 320 dp, so it stays a wheel on tablets), never taller than wide.
        int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), Math.round(320 * getResources().getDisplayMetrics().density));
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? width : MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.max(1f, Math.min(w, h) / 2f - ballRadius - getPaddingLeft());
        int[] hues = new int[7];
        for (int i = 0; i < hues.length; i++) {
            hues[i] = Color.HSVToColor(new float[]{i * 60f, 1f, 1f});
        }
        huePaint.setShader(new SweepGradient(centerX, centerY, hues, null));
        saturationPaint.setShader(new RadialGradient(centerX, centerY, radius, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(centerX, centerY, radius, huePaint);
        canvas.drawCircle(centerX, centerY, radius, saturationPaint);
        darkPaint.setAlpha(Math.round((1f - value) * 255));
        canvas.drawCircle(centerX, centerY, radius, darkPaint);

        double angle = Math.toRadians(hue);
        float x = centerX + (float) (Math.cos(angle) * saturation * radius);
        float y = centerY + (float) (Math.sin(angle) * saturation * radius);
        ballHsv[0] = hue;
        ballHsv[1] = saturation;
        ballHsv[2] = value;
        ballFill.setColor(Color.HSVToColor(ballHsv));
        canvas.drawCircle(x, y, ballRadius, ballFill);
        canvas.drawCircle(x, y, ballRadius, ballRing);
        canvas.drawCircle(x, y, ballRadius + ballRing.getStrokeWidth() / 2, ballShadow);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                getParent().requestDisallowInterceptTouchEvent(true);
                float[] hs = ColorWheelMath.hueSaturationAt(event.getX() - centerX, event.getY() - centerY, radius);
                hue = hs[0];
                saturation = hs[1];
                invalidate();
                if (listener != null) listener.onColorChanged(hue, saturation, true);
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            default -> {
                return super.onTouchEvent(event);
            }
        }
    }
}
