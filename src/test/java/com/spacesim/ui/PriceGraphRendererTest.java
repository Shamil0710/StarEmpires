package com.spacesim.ui;

import com.badlogic.gdx.utils.FloatArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceGraphRendererTest {
    @Test
    void отклоняетНечисловыеЗначенияИстории() {
        FloatArray history = new FloatArray();
        history.add(10f);
        history.add(Float.NaN);

        assertNull(PriceGraphRenderer.calculateRange(history));

        history.set(1, Float.POSITIVE_INFINITY);
        assertNull(PriceGraphRenderer.calculateRange(history));
    }

    @Test
    void диапазонЭкстремальныхЦенНеПереполняется() {
        FloatArray history = new FloatArray();
        history.add(-Float.MAX_VALUE);
        history.add(Float.MAX_VALUE);

        PriceGraphRenderer.PriceRange range = PriceGraphRenderer.calculateRange(history);

        assertNotNull(range);
        assertEquals(-Float.MAX_VALUE, range.min());
        assertTrue(Double.isFinite(range.span()));
        assertTrue(range.span() > Float.MAX_VALUE);
    }

    @Test
    void проверяетКонечнуюГеометриюГрафика() {
        assertTrue(PriceGraphRenderer.isValidGeometry(0f, 0f, 200f, 100f));
        assertFalse(PriceGraphRenderer.isValidGeometry(Float.NaN, 0f, 200f, 100f));
        assertFalse(PriceGraphRenderer.isValidGeometry(0f, 0f, Float.POSITIVE_INFINITY, 100f));
        assertFalse(PriceGraphRenderer.isValidGeometry(Float.MAX_VALUE, 0f, Float.MAX_VALUE, 100f));
        assertFalse(PriceGraphRenderer.isValidGeometry(0f, 0f, 0f, 100f));
    }
}
