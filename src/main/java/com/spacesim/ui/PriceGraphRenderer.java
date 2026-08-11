package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.FloatArray;

public class PriceGraphRenderer {
    private ShapeRenderer sr = new ShapeRenderer();

    public void render(FloatArray history, float x, float y, float w, float h) {
        if (history == null || history.size < 2) return;

        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(Color.GREEN);

        float min = history.get(0);
        float max = history.get(0);
        for (int i = 1; i < history.size; i++) {
            float value = history.get(i);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        float range = Math.max(1, max - min);
        float stepX = w / (history.size - 1);

        for (int i = 0; i < history.size - 1; i++) {
            float y1 = y + ((history.get(i) - min) / range) * h;
            float y2 = y + ((history.get(i+1) - min) / range) * h;
            sr.line(x + i * stepX, y1, x + (i+1) * stepX, y2);
        }
        sr.end();
    }

    public void dispose() { sr.dispose(); }
}
