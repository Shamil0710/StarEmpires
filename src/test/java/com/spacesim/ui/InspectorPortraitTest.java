package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectorPortraitTest {
    @Test
    void frameSitsInTheTopRightCornerAndShrinksWithTheInspector() {
        // A 400 px header: the preferred 84 px side fits within the 32 % share.
        var wide = InspectorPortrait.frame(420f, 600f, 400f, 84f, 40f, 1d);
        assertEquals(84f, wide.width(), 1e-4f);
        assertEquals(84f, wide.height(), 1e-4f);
        assertEquals(420f, wide.right(), 1e-4f);
        assertEquals(600f, wide.top(), 1e-4f);

        // A 200 px header: the frame takes at most 64 px.
        var narrow = InspectorPortrait.frame(220f, 600f, 200f, 84f, 40f, 1d);
        assertEquals(64f, narrow.width(), 1e-4f);

        // Below the minimum side there is no portrait at all.
        assertNull(InspectorPortrait.frame(120f, 600f, 100f, 84f, 40f, 1d));
        assertNull(InspectorPortrait.frame(120f, 600f, Float.NaN, 84f, 40f, 1d));
    }

    @Test
    void longHullsGetAWiderFrameWithinTheirShare() {
        // A 9:1 freighter in a 400 px header: twice as wide as high, still in the corner.
        var freighter = InspectorPortrait.frame(420f, 600f, 400f, 84f, 40f, 9d);
        assertEquals(168f, freighter.width(), 1e-4f);
        assertEquals(84f, freighter.height(), 1e-4f);
        assertEquals(420f, freighter.right(), 1e-4f);
        assertEquals(600f, freighter.top(), 1e-4f);

        // In a 300 px header the widening stops at 42 % of the width.
        assertEquals(126f, InspectorPortrait.frame(320f, 600f, 300f, 84f, 40f, 9d).width(), 1e-4f);
        // A 1.5:1 escort widens by its own ratio; compact or unknown shapes stay square.
        assertEquals(126f, InspectorPortrait.frame(420f, 600f, 400f, 84f, 40f, 1.5d).width(), 1e-4f);
        assertEquals(84f, InspectorPortrait.frame(420f, 600f, 400f, 84f, 40f, 0.5d).width(), 1e-4f);
        assertEquals(84f, InspectorPortrait.frame(420f, 600f, 400f, 84f, 40f, Double.NaN).width(), 1e-4f);
        assertEquals(84f, InspectorPortrait.frame(420f, 600f, 400f, 84f, 40f, Double.POSITIVE_INFINITY).width(), 1e-4f);

        // The hull fills the widened frame with even margins: 168 - 2 * 6.72 px long.
        float[] hull = InspectorPortrait.fit(270d, 30d, freighter);
        assertEquals(168f - 2f * 6.72f, hull[0], 1e-3f);
        assertEquals(hull[0] / 9f, hull[1], 1e-3f);
        assertTrue(hull[1] <= freighter.height() - 2f * 6.72f + 1e-3f);
    }

    @Test
    void hullKeepsItsProportionsInsideTheFrame() {
        var frame = new MapLabelLayout.Box(0f, 0f, 100f, 100f);
        // A 120 m x 30 m freighter: the length fills the inner 84 px, the width follows at 4:1.
        float[] freighter = InspectorPortrait.fit(120d, 30d, frame);
        assertEquals(84f, freighter[0], 1e-3f);
        assertEquals(21f, freighter[1], 1e-3f);

        // A station wider than long is limited by its width instead.
        float[] station = InspectorPortrait.fit(200d, 400d, frame);
        assertEquals(42f, station[0], 1e-3f);
        assertEquals(84f, station[1], 1e-3f);

        // The fitted hull never leaves the frame.
        for (float[] hull : new float[][] {freighter, station}) {
            assertTrue(hull[0] <= frame.width() && hull[1] <= frame.height());
        }
        assertNull(InspectorPortrait.fit(0d, 30d, frame));
        assertNull(InspectorPortrait.fit(120d, Double.NaN, frame));
        assertNull(InspectorPortrait.fit(120d, 30d, null));
    }

    @Test
    void headerTextMakesRoomAndTheBodyStartsBelowBoth() {
        var frame = new MapLabelLayout.Box(336f, 516f, 84f, 84f);
        assertEquals(400f - 84f - 12f, InspectorPortrait.textWidth(400f, frame, 12f), 1e-4f);
        assertEquals(400f, InspectorPortrait.textWidth(400f, null, 12f), 1e-4f);

        // Short header text: the body starts below the portrait.
        assertEquals(516f - 10f, InspectorPortrait.bodyTop(540f, frame, 10f), 1e-4f);
        // Long, wrapped header text: the body starts below the text.
        assertEquals(480f, InspectorPortrait.bodyTop(480f, frame, 10f), 1e-4f);
        assertEquals(540f, InspectorPortrait.bodyTop(540f, null, 10f), 1e-4f);
    }
}
