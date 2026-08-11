package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.FloatArray;

public class PriceGraphRenderer {
    private final ShapeRenderer sr = new ShapeRenderer();

    public void render(Matrix4 projectionMatrix, FloatArray history, float x, float y, float w, float h) {
        PriceRange priceRange = calculateRange(history);
        if (projectionMatrix == null || priceRange == null || !isValidGeometry(x, y, w, h)) {
            return;
        }

        sr.setProjectionMatrix(projectionMatrix);
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(Color.GREEN);

        double stepX = (double) w / (history.size - 1);

        for (int i = 0; i < history.size - 1; i++) {
            float y1 = projectPrice(history.get(i), y, h, priceRange);
            float y2 = projectPrice(history.get(i + 1), y, h, priceRange);
            float x1 = (float) ((double) x + i * stepX);
            float x2 = (float) ((double) x + (i + 1) * stepX);
            sr.line(x1, y1, x2, y2);
        }
        sr.end();
    }

    static PriceRange calculateRange(FloatArray history) {
        if (history == null || history.size < 2) {
            return null;
        }

        float min = history.get(0);
        float max = min;
        if (!Float.isFinite(min)) {
            return null;
        }

        for (int index = 1; index < history.size; index++) {
            float value = history.get(index);
            if (!Float.isFinite(value)) {
                return null;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        double span = Math.max(1d, (double) max - min);
        return new PriceRange(min, span);
    }

    static boolean isValidGeometry(float x, float y, float width, float height) {
        if (!Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(width)
                || !Float.isFinite(height)
                || width <= 0f
                || height <= 0f) {
            return false;
        }

        double right = (double) x + width;
        double top = (double) y + height;
        return right >= -Float.MAX_VALUE
                && right <= Float.MAX_VALUE
                && top >= -Float.MAX_VALUE
                && top <= Float.MAX_VALUE;
    }

    private float projectPrice(float price, float y, float height, PriceRange range) {
        double normalized = ((double) price - range.min()) / range.span();
        return (float) ((double) y + normalized * height);
    }

    public void dispose() { sr.dispose(); }

    record PriceRange(float min, double span) {
    }
}
