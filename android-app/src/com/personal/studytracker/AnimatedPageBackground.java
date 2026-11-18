package com.personal.studytracker;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

public final class AnimatedPageBackground extends Drawable implements Runnable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable botanicalOverlay;
    private final long startTime = SystemClock.uptimeMillis();

    public AnimatedPageBackground(Drawable botanicalOverlay) {
        this.botanicalOverlay = botanicalOverlay;
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;

        float width = bounds.width();
        float height = bounds.height();
        float time = (SystemClock.uptimeMillis() - startTime) / 1000f;
        canvas.save();
        canvas.translate(bounds.left, bounds.top);

        paint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{0xFF15533F, 0xFF103A30, 0xFF0B2B25},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        drawGlow(canvas,
                width * (0.72f + 0.12f * (float) Math.sin(time * 0.16f)),
                height * (0.28f + 0.07f * (float) Math.sin(time * 0.12f + 1.4f)),
                Math.max(width, height) * 0.48f);
        drawGlow(canvas,
                width * (0.22f + 0.09f * (float) Math.sin(time * 0.11f + 2.2f)),
                height * (0.76f + 0.06f * (float) Math.sin(time * 0.14f)),
                Math.max(width, height) * 0.40f);
        drawGlow(canvas,
                width * (0.54f + 0.08f * (float) Math.sin(time * 0.09f + 0.7f)),
                height * (0.50f + 0.09f * (float) Math.cos(time * 0.13f)),
                Math.max(width, height) * 0.34f);

        drawSparkles(canvas, width, height, time);
        if (botanicalOverlay != null) {
            botanicalOverlay.setBounds(0, 0, bounds.width(), bounds.height());
            botanicalOverlay.draw(canvas);
        }
        canvas.restore();

        if (isVisible() && getCallback() != null && ValueAnimator.areAnimatorsEnabled()) {
            scheduleSelf(this, SystemClock.uptimeMillis() + 66L);
        }
    }

    private void drawGlow(Canvas canvas, float x, float y, float radius) {
        paint.setShader(new RadialGradient(x, y, radius,
                new int[]{0xAA68A981, 0x584F9B73, 0x004F9B73},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, paint);
    }

    private void drawSparkles(Canvas canvas, float width, float height, float time) {
        float[] points = {
                0.22f, 0.18f, 0.74f, 0.12f, 0.84f, 0.48f,
                0.36f, 0.63f, 0.15f, 0.82f, 0.66f, 0.91f
        };
        for (int i = 0; i < points.length; i += 2) {
            float phase = time * (0.18f + (i % 3) * 0.025f) + i * 0.9f;
            float x = width * points[i] + (float) Math.sin(phase) * width * 0.025f;
            float y = height * points[i + 1]
                    + (float) Math.cos(phase * 0.8f) * height * 0.012f;
            int alpha = 34 + (int) (42 * (0.5f + 0.5f * Math.sin(phase)));
            paint.setShader(null);
            paint.setColor(0x00F4F4CF | (alpha << 24));
            float radius = Math.max(1.3f, width * 0.004f);
            canvas.drawCircle(x, y, radius * 2.8f, paint);
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    @Override public void run() {
        invalidateSelf();
    }

    @Override public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) unscheduleSelf(this);
        else if (changed) invalidateSelf();
        return changed;
    }

    @Override public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
