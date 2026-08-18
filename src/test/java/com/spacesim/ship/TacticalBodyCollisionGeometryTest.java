package com.spacesim.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalBodyCollisionGeometryTest {
    @Test
    void opposingFastBodiesCollideBetweenEndpoints() {
        var hit = TacticalCollisionGeometry.firstSegmentCircleHitFraction(
                -10d,
                0d,
                10d,
                0d,
                2d);

        assertTrue(hit.isPresent());
        assertEquals(0.4d, hit.getAsDouble(), 1e-12d,
                "swept collision must detect contact even when neither endpoint overlaps");
    }

    @Test
    void parallelOffsetMotionMissesPhysicalRadius() {
        var hit = TacticalCollisionGeometry.firstSegmentCircleHitFraction(
                -10d,
                3d,
                10d,
                3d,
                2d);

        assertTrue(hit.isEmpty());
    }

    @Test
    void bodiesAlreadyOverlappingReportImmediateContact() {
        var hit = TacticalCollisionGeometry.firstSegmentCircleHitFraction(
                0.5d,
                0d,
                5d,
                0d,
                1d);

        assertTrue(hit.isPresent());
        assertEquals(0d, hit.getAsDouble(), 1e-12d);
    }
}
