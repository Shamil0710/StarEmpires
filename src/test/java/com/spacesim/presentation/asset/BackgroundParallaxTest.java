package com.spacesim.presentation.asset;

import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundParallaxTest {
    private static final float EPS = 1e-5f;

    @Test
    void viewOffsetIsTheCameraCentreInTheBaseFrame() {
        // A 400 px pan at zoom 1 moves the view 400 base pixels the other way; at zoom 4, 100.
        assertEquals(-400d, BackgroundParallax.viewOffset(1f, 400f), 1e-9);
        assertEquals(-100d, BackgroundParallax.viewOffset(4f, 400f), 1e-9);
        // Deep inspection zoom: the offset stays bounded by the system, not by the pan.
        assertEquals(-250d, BackgroundParallax.viewOffset(1e9f, 2.5e11f), 1e-3);
        assertEquals(0d, BackgroundParallax.viewOffset(0f, 400f));
        assertEquals(0d, BackgroundParallax.viewOffset(Float.NaN, 400f));
        assertEquals(0d, BackgroundParallax.viewOffset(2f, Float.POSITIVE_INFINITY));
    }

    @Test
    void farLayerIsACentredCropWithAnOverscanReserve() {
        // 640 x 360 artwork into an 880 x 556 map: the crop is 569.8 x 360 texels, shrunk by 16 %.
        var still = BackgroundParallax.farCrop(640, 360, 880f, 556f, 0d, 0d);
        double cropWidth = 360d * 880d / 556d;
        double viewWidth = cropWidth / 1.16d;
        assertEquals(viewWidth / 640d, still.u2() - still.u(), EPS);
        assertEquals((360d / 1.16d) / 360d, still.v2() - still.v(), EPS);
        assertEquals(0.5f, (still.u() + still.u2()) / 2f, EPS);
        assertEquals(0.5f, (still.v() + still.v2()) / 2f, EPS);
        // The visible crop keeps the destination aspect.
        assertEquals(880d / 556d, (still.u2() - still.u()) * 640d / ((still.v2() - still.v()) * 360d), 1e-4);
    }

    @Test
    void farLayerShiftsSlowlyAgainstTheCameraAndNeverShowsAnEdge() {
        var still = BackgroundParallax.farCrop(640, 360, 880f, 556f, 0d, 0d);
        // Camera 200 base px to the right and up: the crop moves right and up (V down) by 5 %.
        var moved = BackgroundParallax.farCrop(640, 360, 880f, 556f, 200d, 200d);
        double texelsPerPixel = (still.u2() - still.u()) * 640d / 880d;
        assertEquals(200d * BackgroundParallax.FAR_FACTOR * texelsPerPixel / 640d, moved.u() - still.u(), EPS);
        assertEquals(-200d * BackgroundParallax.FAR_FACTOR * texelsPerPixel / 360d, moved.v() - still.v(), EPS);
        assertEquals(still.u2() - still.u(), moved.u2() - moved.u(), EPS);

        // Far beyond the reserve the crop stops at the artwork's edge.
        for (double offset : new double[] {1e6d, -1e6d}) {
            var edge = BackgroundParallax.farCrop(640, 360, 880f, 556f, offset, offset);
            assertTrue(edge.u() >= -EPS && edge.u2() <= 1f + EPS, "u inside the artwork at " + offset);
            assertTrue(edge.v() >= -EPS && edge.v2() <= 1f + EPS, "v inside the artwork at " + offset);
        }
        assertEquals(still, BackgroundParallax.farCrop(640, 360, 880f, 556f, Double.NaN, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> BackgroundParallax.farCrop(640, 360, 0f, 556f, 0d, 0d));
    }

    @Test
    void nearLayerMovesFasterThanTheFarLayerAndSpreadsWithZoom() {
        float[] phase = {0.25f, 0.75f};
        var still = BackgroundParallax.nearTile(880f, 556f, 1f, 1f, 0d, 0d, phase);
        assertEquals(880f / 512f, still.u2() - still.u(), EPS);
        assertEquals(556f / 512f, still.v2() - still.v(), EPS);

        // 200 base px to the right: the tile shifts by 32 screen px, faster than the far layer's 10.
        var moved = BackgroundParallax.nearTile(880f, 556f, 1f, 1f, 200d, 0d, phase);
        assertEquals(200f * BackgroundParallax.NEAR_FACTOR / 512f, moved.u() - still.u(), EPS);
        assertTrue(BackgroundParallax.NEAR_FACTOR > BackgroundParallax.FAR_FACTOR);

        // Zoom spreads the stars around the centre, capped for deep inspection zoom.
        assertEquals(1f, BackgroundParallax.nearSpread(1f));
        assertEquals(1f, BackgroundParallax.nearSpread(0.6f));
        assertEquals(1.05f, BackgroundParallax.nearSpread(2f), EPS);
        assertEquals(1.5f, BackgroundParallax.nearSpread(1024f), 1e-4f);
        assertEquals(BackgroundParallax.NEAR_MAX_SPREAD, BackgroundParallax.nearSpread(1e12f));
        assertEquals(1f, BackgroundParallax.nearSpread(Float.NaN));
        var zoomed = BackgroundParallax.nearTile(880f, 556f, 1f, 1024f, 0d, 0d, phase);
        assertEquals((880f / 512f) / 1.5f, zoomed.u2() - zoomed.u(), 1e-4f);

        // Coordinates are wrapped into one tile, so a long flight keeps exact texture coordinates.
        var far = BackgroundParallax.nearTile(880f, 556f, 1f, 1f, 1e9d, -1e9d, phase);
        assertTrue(far.u() >= 0f && far.u() < 1f && far.v() >= 0f && far.v() < 1f);
    }

    @Test
    void resizedMapsAndUiScalesKeepTheAspectAndTheTileSize() {
        // Destinations on both sides of the artwork's 16:9 aspect: a tall resized map crops the
        // width, a very wide one crops the height; both keep the destination aspect and stay inside.
        for (float[] dest : new float[][] {{420f, 600f}, {1800f, 400f}, {640f, 360f}}) {
            for (double offset : new double[] {0d, 300d, -1e6d}) {
                var crop = BackgroundParallax.farCrop(640, 360, dest[0], dest[1], offset, -offset);
                double aspect = (crop.u2() - crop.u()) * 640d / ((crop.v2() - crop.v()) * 360d);
                assertEquals(dest[0] / (double) dest[1], aspect, 1e-3, "aspect for " + dest[0] + "x" + dest[1]);
                assertTrue(crop.u() >= -EPS && crop.u2() <= 1f + EPS && crop.v() >= -EPS && crop.v2() <= 1f + EPS,
                        "inside the artwork for " + dest[0] + "x" + dest[1] + " at " + offset);
            }
        }
        var tall = BackgroundParallax.farCrop(640, 360, 420f, 600f, 0d, 0d);
        assertEquals(1f / 1.16f, tall.v2() - tall.v(), EPS);

        // The star tile grows with the UI scale, and the same camera offset shifts it by the same
        // screen distance.
        float[] phase = {0f, 0f};
        for (float scale : new float[] {0.8f, 1f, 1.5f}) {
            var still = BackgroundParallax.nearTile(880f, 556f, scale, 1f, 0d, 0d, phase);
            var moved = BackgroundParallax.nearTile(880f, 556f, scale, 1f, 100d, 0d, phase);
            double tile = 512d * scale;
            assertEquals(880d / tile, still.u2() - still.u(), 1e-4);
            double shift = moved.u() - still.u();
            shift -= Math.floor(shift + 0.5d);
            assertEquals(100d * BackgroundParallax.NEAR_FACTOR, shift * tile, 1e-2);
        }
        assertThrows(IllegalArgumentException.class, () -> BackgroundParallax.nearTile(880f, 556f, 0f, 1f, 0d, 0d, phase));
    }

    @Test
    void starsAndPhaseAreDeterministicAndInsideTheTile() {
        var stars = BackgroundParallax.stars(7L, 512, 150);
        assertEquals(150, stars.size());
        assertEquals(stars, BackgroundParallax.stars(7L, 512, 150));
        assertNotEquals(stars, BackgroundParallax.stars(8L, 512, 150));
        for (var star : stars) {
            int margin = star.large() ? 1 : 0;
            assertTrue(star.x() >= margin && star.x() < 512 - margin && star.y() >= margin && star.y() < 512 - margin);
            assertTrue(star.brightness() >= 0.35f && star.brightness() <= 1f);
        }
        assertTrue(stars.stream().anyMatch(BackgroundParallax.Star::large));

        float[] phase = BackgroundParallax.phase(42L, new StarSystemId(8L));
        assertArrayEquals(phase, BackgroundParallax.phase(42L, new StarSystemId(8L)));
        assertTrue(phase[0] >= 0f && phase[0] < 1f && phase[1] >= 0f && phase[1] < 1f);
        assertNotEquals(phase[0], BackgroundParallax.phase(42L, new StarSystemId(9L))[0]);
    }
}
