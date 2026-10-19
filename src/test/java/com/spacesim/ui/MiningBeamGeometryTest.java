package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningBeamGeometryTest {
    @Test
    void beamLeavesASpineEmitterAndEndsAtTheSourceSurface() {
        // Ship 100 px long at the origin heading +X; the emitter may move 24 px along its spine.
        float reach = (float) (50d * MiningBeamGeometry.HULL_FILL * MiningBeamGeometry.EMITTER_REACH);
        assertEquals(24f, reach, 1e-4f);
        // A 50 px rock ahead: the emitter moves to the forward limit, the beam ends 20 px short of the centre.
        assertArrayEquals(new float[] {24f, 0f, 180f, 0f}, MiningBeamGeometry.segment(0f, 0f, 100f, 0d, 200f, 0f, 50f), 1e-4f);
        // A rock abeam: the emitter stays at the centre of the spine, inside the visible hull.
        assertArrayEquals(new float[] {0f, 0f, 0f, 180f}, MiningBeamGeometry.segment(0f, 0f, 100f, 0d, 0f, 200f, 50f), 1e-4f);
        // Diagonal: the emitter is the source's projection onto the spine.
        float[] diagonal = MiningBeamGeometry.segment(0f, 0f, 100f, 0d, 10f, 100f, 0f);
        assertEquals(10f, diagonal[0], 1e-4f);
        assertEquals(0f, diagonal[1], 1e-4f);
        // Turned a quarter, the spine points up.
        float[] turned = MiningBeamGeometry.segment(0f, 0f, 100f, Math.PI / 2d, 0f, 200f, 50f);
        assertEquals(0f, turned[0], 1e-4f);
        assertEquals(24f, turned[1], 1e-4f);
        // Zoomed out until the emitter is inside the rock on screen: no beam.
        assertNull(MiningBeamGeometry.segment(0f, 0f, 100f, 0d, 30f, 0f, 50f));
        assertNull(MiningBeamGeometry.segment(5f, 5f, 10f, 0d, 5f, 5f, 4f));
        // An unknown heading counts as +X, like the sprite.
        assertArrayEquals(new float[] {24f, 0f, 180f, 0f}, MiningBeamGeometry.segment(0f, 0f, 100f, Double.NaN, 200f, 0f, 50f), 1e-4f);
    }

    @Test
    void sparksFanBackTowardsTheShipAndFadeOverTheBurst() {
        var early = MiningBeamGeometry.sparks(42L, 0.05f, 0.45f, 200f, 0f, 1d, 0d, 20f);
        assertEquals(MiningBeamGeometry.SPARKS_PER_BURST, early.size());
        assertEquals(early, MiningBeamGeometry.sparks(42L, 0.05f, 0.45f, 200f, 0f, 1d, 0d, 20f), "deterministic");
        var late = MiningBeamGeometry.sparks(42L, 0.40f, 0.45f, 200f, 0f, 1d, 0d, 20f);
        for (int index = 0; index < early.size(); index++) {
            var spark = late.get(index);
            assertTrue(spark.x() <= 200f + 1e-3f, "sparks fly back towards the ship");
            assertTrue(Math.hypot(spark.x() - 200f, spark.y()) <= 20f + 1e-3f, "within the reach");
            assertTrue(Math.hypot(spark.x() - 200f, spark.y()) >= Math.hypot(early.get(index).x() - 200f, early.get(index).y()),
                    "sparks move outwards");
            assertTrue(spark.alpha() < early.get(index).alpha(), "sparks fade");
        }
        assertTrue(MiningBeamGeometry.sparks(42L, 0.45f, 0.45f, 200f, 0f, 1d, 0d, 20f).isEmpty());
        assertTrue(MiningBeamGeometry.sparks(42L, -1f, 0.45f, 200f, 0f, 1d, 0d, 20f).isEmpty());
        assertTrue(MiningBeamGeometry.sparks(42L, 0.1f, 0.45f, 200f, 0f, 0d, 0d, 20f).isEmpty());
    }

    @Test
    void pulseStaysInItsBand() {
        for (float t = 0f; t < 3f; t += 0.01f) {
            float pulse = MiningBeamGeometry.pulse(t);
            assertTrue(pulse >= 0.78f - 1e-6f && pulse <= 1f + 1e-6f);
        }
        assertEquals(1f, MiningBeamGeometry.pulse(Float.NaN));
    }
}
