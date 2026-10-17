package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApproachLineTest {
    @Test
    void closingSpeedIsTheVelocityComponentTowardTheTarget() {
        // 1.84 km away, flying straight at it at 42.6 m/s.
        var direct = ApproachLine.of(1_840d, 0d, 42.6d, 0d, false);
        assertEquals(1_840d, direct.distanceM(), 1e-9);
        assertEquals(42.6d, direct.closingMps(), 1e-9);
        assertEquals(1_840d / 42.6d, direct.etaSeconds(), 1e-9);
        assertEquals("1.84 км  •  ~43 с", direct.label());

        // Same speed at 60 degrees off the line closes at half the rate.
        var oblique = ApproachLine.of(1_000d, 0d, 20d * Math.cos(Math.toRadians(60)), 20d * Math.sin(Math.toRadians(60)), false);
        assertEquals(10d, oblique.closingMps(), 1e-9);
        assertEquals(100d, oblique.etaSeconds(), 1e-9);
    }

    @Test
    void movingAwayOrDriftingHasNoEstimate() {
        var away = ApproachLine.of(500d, 0d, -3d, 0d, false);
        assertTrue(away.closingMps() < 0d);
        assertTrue(Double.isNaN(away.etaSeconds()));
        assertEquals("500 м  •  не сближается", away.label());

        var drifting = ApproachLine.of(0d, 2_000d, 5d, 0d, false);
        assertEquals(0d, drifting.closingMps(), 1e-9);
        assertTrue(Double.isNaN(drifting.etaSeconds()));
    }

    @Test
    void stationTargetsShowTheOrdinaryDockingLimits() {
        assertEquals(ApproachLine.DockHint.NONE, ApproachLine.of(500d, 0d, 0d, 0d, false).dockHint());
        assertEquals(ApproachLine.DockHint.OUT_OF_RANGE, ApproachLine.of(1_000.5d, 0d, 0d, 0d, true).dockHint());
        var fast = ApproachLine.of(800d, 0d, 1.5d, 0d, true);
        assertEquals(ApproachLine.DockHint.TOO_FAST, fast.dockHint());
        assertTrue(fast.label().endsWith("для стыковки сбросьте скорость до 1 м/с"));
        // The limits are inclusive, like the docking command: 1 km and 1 m/s exactly are allowed.
        var ready = ApproachLine.of(600d, 800d, 0.6d, 0.8d, true);
        assertEquals(ApproachLine.DockHint.WITHIN_LIMITS, ready.dockHint());
        assertTrue(ready.label().endsWith("в пределах стыковки"));
        // Speed counts in any direction, as the docking rule uses the ship's absolute speed.
        assertEquals(ApproachLine.DockHint.TOO_FAST, ApproachLine.of(500d, 0d, 0d, 2d, true).dockHint());
    }

    @Test
    void lineToAFarTargetIsClippedToTheMap() {
        var map = new MapLabelLayout.Box(0f, 0f, 900f, 560f);
        float[] inside = ApproachLine.clip(100f, 100f, 300f, 200f, map);
        assertEquals(100f, inside[0], 1e-3f);
        assertEquals(300f, inside[2], 1e-3f);

        // Target a billion pixels away at inspection zoom: only the visible stretch remains.
        float[] far = ApproachLine.clip(450f, 280f, 1.0e9f, 280f, map);
        assertEquals(450f, far[0], 1e-3f);
        assertEquals(900f, far[2], 1e-2f);
        assertEquals(280f, far[3], 1e-3f);

        assertEquals(null, ApproachLine.clip(-50f, -50f, -10f, 600f, map), "entirely left of the map");
        float[] crossing = ApproachLine.clip(-100f, 280f, 1_000f, 280f, map);
        assertEquals(0f, crossing[0], 1e-3f);
        assertEquals(900f, crossing[2], 1e-3f);
    }

    @Test
    void clippingKeepsPrecisionForAStartFarOffScreen() {
        float[] clipped = ApproachLine.clip(-1e12f, 280f, 450f, 280f, new MapLabelLayout.Box(100f, 0f, 800f, 560f));
        assertEquals(100f, clipped[0], 1e-2f);
        assertEquals(280f, clipped[1], 1e-3f);
        assertEquals(450f, clipped[2], 1e-3f);
        assertEquals(280f, clipped[3], 1e-3f);
    }

    @Test
    void readoutAvoidsTheMapTitleAndScaleBar() {
        var map = new MapLabelLayout.Box(0f, 0f, 900f, 560f);
        var title = new MapLabelLayout.Box(0f, 502f, 650f, 58f);
        var scaleBar = new MapLabelLayout.Box(0f, 0f, 230f, 48f);
        var overlays = java.util.List.of(title, scaleBar);
        // Lines hugging the top-left and bottom-left corners.
        for (float[] line : new float[][] {{20f, 540f, 300f, 540f}, {20f, 30f, 260f, 30f}, {40f, 520f, 40f, 300f}}) {
            var plate = ApproachLine.placeReadout(line, 180f, 20f, 10f, map, overlays);
            assertTrue(plate.inside(map));
            assertTrue(overlays.stream().noneMatch(plate::overlaps),
                    "readout overlaps an overlay for line " + java.util.Arrays.toString(line));
        }
        // In open space the plate sits above the middle of the line.
        var open = ApproachLine.placeReadout(new float[] {300f, 280f, 700f, 280f}, 180f, 20f, 10f, map, overlays);
        assertEquals(500f, open.x() + open.width() / 2f, 1e-3f);
        assertEquals(290f, open.y(), 1e-3f);
    }

    @Test
    void lineRunsBetweenTheObjectEdges() {
        float[] trimmed = ApproachLine.trim(100f, 100f, 600f, 100f, 250f, 20f);
        assertEquals(350f, trimmed[0], 1e-3f);
        assertEquals(580f, trimmed[2], 1e-3f);
        assertEquals(100f, trimmed[3], 1e-3f);
        assertEquals(null, ApproachLine.trim(100f, 100f, 300f, 100f, 150f, 60f), "overlapping clearances draw no line");
    }

    @Test
    void durationsReadNaturally() {
        assertEquals("0 с", ApproachLine.formatDuration(0d));
        assertEquals("59 с", ApproachLine.formatDuration(59.2d));
        assertEquals("1 мин", ApproachLine.formatDuration(60d));
        assertEquals("12 мин", ApproachLine.formatDuration(12d * 60d + 10d));
        assertEquals("1 ч", ApproachLine.formatDuration(3_600d));
        assertEquals("3 ч 20 мин", ApproachLine.formatDuration(3d * 3_600d + 20d * 60d));
        assertEquals("5 сут", ApproachLine.formatDuration(5d * 86_400d));
        assertThrows(IllegalArgumentException.class, () -> ApproachLine.formatDuration(-1d));
        assertThrows(IllegalArgumentException.class, () -> ApproachLine.of(Double.NaN, 0d, 0d, 0d, false));
    }
}
