package com.spacesim.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalCollisionGeometryTest {
    @Test
    void fastBodyCrossingEntireHullWithinOneTickStillProducesFirstHit() {
        var hit = TacticalCollisionGeometry.firstSegmentAabbHitFraction(
                -100d, 0d,
                100d, 0d,
                10d, 5d);

        assertTrue(hit.isPresent());
        assertEquals(0.45d, hit.getAsDouble(), 1e-12d);
    }

    @Test
    void relativeMotionDetectsCollisionWithMovingTargetWithoutEndpointOverlap() {
        double projectileStartX = 0d;
        double projectileEndX = 100d;
        double targetStartX = 60d;
        double targetEndX = 80d;

        var hit = TacticalCollisionGeometry.firstSegmentAabbHitFraction(
                projectileStartX - targetStartX,
                0d,
                projectileEndX - targetEndX,
                0d,
                5d,
                5d);

        assertTrue(hit.isPresent());
        assertEquals(55d / 80d, hit.getAsDouble(), 1e-12d);
    }

    @Test
    void parallelSegmentOutsideFootprintMisses() {
        var hit = TacticalCollisionGeometry.firstSegmentAabbHitFraction(
                -100d, 6d,
                100d, 6d,
                10d, 5d);

        assertFalse(hit.isPresent());
    }

    @Test
    void bodyStartingInsideFootprintHitsAtIntervalStart() {
        var hit = TacticalCollisionGeometry.firstSegmentAabbHitFraction(
                0d, 0d,
                20d, 0d,
                10d, 5d);

        assertTrue(hit.isPresent());
        assertEquals(0d, hit.getAsDouble(), 0d);
    }

    @Test
    void invalidHullExtentIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TacticalCollisionGeometry.firstSegmentAabbHitFraction(
                        0d, 0d, 1d, 1d, 0d, 1d));
    }
}
