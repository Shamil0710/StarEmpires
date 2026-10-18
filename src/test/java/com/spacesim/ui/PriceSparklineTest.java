package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceSparklineTest {
    private static final MapLabelLayout.Box CELL = new MapLabelLayout.Box(100f, 20f, 60f, 12f);

    @Test
    void xFollowsTicksSoGapsBetweenVisitsKeepTheirLength() {
        // Two quick observations, then a long absence.
        var xy = PriceSparkline.polyline(List.of(new PriceSparkline.Point(0, 1_000), new PriceSparkline.Point(10, 1_100),
                new PriceSparkline.Point(100, 1_200)), CELL);
        assertEquals(100f, xy[0], 1e-4f);
        assertEquals(106f, xy[2], 1e-4f, "10 of 100 ticks is a tenth of the width, not a half");
        assertEquals(160f, xy[4], 1e-4f);
    }

    @Test
    void ySpansTheObservedPriceRange() {
        var xy = PriceSparkline.polyline(List.of(new PriceSparkline.Point(0, 900), new PriceSparkline.Point(5, 1_200),
                new PriceSparkline.Point(9, 1_050)), CELL);
        assertEquals(20f, xy[1], 1e-4f, "lowest price at the bottom");
        assertEquals(32f, xy[3], 1e-4f, "highest price at the top");
        assertEquals(26f, xy[5], 1e-4f, "half way");
    }

    @Test
    void flatAndSinglePointSeriesAreDrawnAtMidHeight() {
        var flat = PriceSparkline.polyline(List.of(new PriceSparkline.Point(0, 500), new PriceSparkline.Point(7, 500)), CELL);
        assertEquals(26f, flat[1], 1e-4f);
        assertEquals(26f, flat[3], 1e-4f);
        assertArrayEquals(new float[] {160f, 26f}, PriceSparkline.polyline(List.of(new PriceSparkline.Point(3, 500)), CELL), 1e-4f,
                "a single observation sits at the right edge, the most recent position");
        assertEquals(0, PriceSparkline.polyline(List.of(), CELL).length);
    }
}
