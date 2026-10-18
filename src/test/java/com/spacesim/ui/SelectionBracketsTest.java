package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionBracketsTest {
    @Test
    void bracketsReplaceTheCircleOnlyForMarkerSizedSprites() {
        assertTrue(SelectionBrackets.useBrackets(true, 40f, 40f));
        assertTrue(SelectionBrackets.useBrackets(true, 900f, 40f));
        assertFalse(SelectionBrackets.useBrackets(true, 39.9f, 40f), "small hull keeps the marker");
        assertFalse(SelectionBrackets.useBrackets(false, 900f, 40f), "no art keeps the marker");
    }

    @Test
    void frameFollowsTheRotatedHullBounds() {
        var level = SelectionBrackets.around(500f, 300f, 400f, 100f, 0f, 6f, 8f);
        assertFrame(level, 294f, 244f, 706f, 356f);

        var upright = SelectionBrackets.around(500f, 300f, 400f, 100f, 90f, 6f, 8f);
        assertFrame(upright, 444f, 94f, 556f, 506f);

        var diagonal = SelectionBrackets.around(500f, 300f, 400f, 100f, 45f, 0f, 8f);
        float half = (float) (250d / Math.sqrt(2d));
        assertFrame(diagonal, 500f - half, 300f - half, 500f + half, 300f + half);

        // Heading sign and half turns do not change the screen bounds.
        assertFrame(SelectionBrackets.around(500f, 300f, 400f, 100f, -135f, 0f, 8f),
                diagonal.left(), diagonal.bottom(), diagonal.right(), diagonal.top());
    }

    @Test
    void armsScaleWithTheFrameButNeverMeet() {
        var large = SelectionBrackets.around(0f, 0f, 400f, 100f, 0f, 6f, 8f);
        assertEquals(112f * SelectionBrackets.ARM_FRACTION, large.arm(), 1e-3f);

        var tiny = SelectionBrackets.around(0f, 0f, 10f, 4f, 0f, 0f, 8f);
        assertEquals(2f, tiny.arm(), 1e-4f, "arm is capped at half of the 4 px side");

        var segments = large.segments();
        assertEquals(8, segments.size());
        for (var segment : segments) {
            float length = Math.abs(segment.x2() - segment.x1()) + Math.abs(segment.y2() - segment.y1());
            assertEquals(large.arm(), length, 1e-4f);
            assertTrue(segment.x1() == large.left() || segment.x1() == large.right());
            assertTrue(segment.y1() == large.bottom() || segment.y1() == large.top());
        }
    }

    @Test
    void invalidGeometryIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SelectionBrackets.around(0f, 0f, 0f, 10f, 0f, 0f, 8f));
        assertThrows(IllegalArgumentException.class,
                () -> SelectionBrackets.around(Float.NaN, 0f, 10f, 10f, 0f, 0f, 8f));
        assertThrows(IllegalArgumentException.class,
                () -> SelectionBrackets.around(0f, 0f, 10f, 10f, Float.POSITIVE_INFINITY, 0f, 8f));
        assertThrows(IllegalArgumentException.class,
                () -> SelectionBrackets.around(0f, 0f, Float.POSITIVE_INFINITY, 10f, 0f, 0f, 8f));
        assertThrows(IllegalArgumentException.class,
                () -> SelectionBrackets.around(0f, 0f, 10f, Float.POSITIVE_INFINITY, 0f, 0f, 8f));
        assertThrows(IllegalArgumentException.class,
                () -> SelectionBrackets.around(0f, 0f, 10f, 10f, 0f, Float.NaN, 8f));
        assertThrows(IllegalArgumentException.class,
                () -> SelectionBrackets.around(0f, 0f, 10f, 10f, 0f, 0f, Float.POSITIVE_INFINITY));
    }

    private static void assertFrame(SelectionBrackets.Frame frame, float left, float bottom, float right, float top) {
        assertEquals(left, frame.left(), 1e-3f);
        assertEquals(bottom, frame.bottom(), 1e-3f);
        assertEquals(right, frame.right(), 1e-3f);
        assertEquals(top, frame.top(), 1e-3f);
    }
}
