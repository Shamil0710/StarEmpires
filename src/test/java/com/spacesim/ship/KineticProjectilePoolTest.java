package com.spacesim.ship;

import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticProjectilePoolTest {
    @Test
    void largeWaveUsesDenseNonAshleyStorageAndRetainsIndividualIdentity() {
        KineticProjectilePool pool = new KineticProjectilePool(8);
        int projectileCount = 10_000;
        for (int index = 0; index < projectileCount; index++) {
            pool.add(body(index + 1L, 100L + index, index, 8_000d + index * 0.01d, 25d));
        }

        assertEquals(projectileCount, pool.size());
        assertTrue(pool.capacity() >= projectileCount);
        assertEquals(1L, pool.bodyAt(0).projectileId());
        assertEquals(projectileCount, pool.bodyAt(projectileCount - 1).projectileId());
        assertEquals(projectileCount - 1, pool.indexOf(projectileCount));
        assertFalse(Arrays.stream(KineticProjectilePool.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .anyMatch(name -> name.startsWith("com.badlogic.ashley")));
    }

    @Test
    void identicalPoolsProduceBitIdenticalOrderingAndTrajectories() {
        KineticProjectilePool first = new KineticProjectilePool(4);
        KineticProjectilePool second = new KineticProjectilePool(4);
        for (int index = 0; index < 2_000; index++) {
            ProjectileBody body = body(index + 1L, 500L, 42L + index, 9_000d, index * 0.5d);
            first.add(body);
            second.add(body);
        }

        for (int step = 0; step < 20; step++) {
            first.advanceAll(0.1d);
            second.advanceAll(0.1d);
        }

        assertEquals(first.size(), second.size());
        for (int index = 0; index < first.size(); index++) {
            ProjectileBody left = first.bodyAt(index);
            ProjectileBody right = second.bodyAt(index);
            assertEquals(left.projectileId(), right.projectileId());
            assertEquals(Double.doubleToLongBits(left.xM()), Double.doubleToLongBits(right.xM()));
            assertEquals(Double.doubleToLongBits(left.yM()), Double.doubleToLongBits(right.yM()));
            assertEquals(left.spawnTick(), right.spawnTick());
        }
    }

    @Test
    void renderVisibilityCannotEnterAuthoritativeAdvanceAndMissedBodyRemainsUntilExplicitRemoval() {
        KineticProjectilePool pool = new KineticProjectilePool();
        ProjectileBody missed = body(71L, 9L, 1234L, 12_000d, -400d);
        pool.add(missed);

        pool.advanceAll(5d);
        ProjectileBody advanced = pool.bodyAt(0);

        assertEquals(1, pool.size());
        assertEquals(1234L, advanced.spawnTick());
        assertEquals(missed.xM() + missed.velocityXMps() * 5d, advanced.xM(), 1e-9d);
        assertEquals(missed.yM() + missed.velocityYMps() * 5d, advanced.yM(), 1e-9d);
        assertTrue(pool.remove(71L));
        assertEquals(0, pool.size());
        assertFalse(pool.remove(71L));
    }

    @Test
    void stableIdOrderRejectsDuplicateOrOutOfOrderInsertionAndRemovalKeepsOrder() {
        KineticProjectilePool pool = new KineticProjectilePool(2);
        pool.add(body(10L, 1L, 0L, 1_000d, 0d));
        pool.add(body(20L, 1L, 0L, 1_000d, 0d));
        pool.add(body(30L, 1L, 0L, 1_000d, 0d));

        assertThrows(IllegalArgumentException.class, () -> pool.add(body(30L, 1L, 0L, 1_000d, 0d)));
        assertThrows(IllegalArgumentException.class, () -> pool.add(body(25L, 1L, 0L, 1_000d, 0d)));
        assertTrue(pool.remove(20L));
        assertEquals(2, pool.size());
        assertEquals(10L, pool.bodyAt(0).projectileId());
        assertEquals(30L, pool.bodyAt(1).projectileId());
    }

    private static ProjectileBody body(
            long projectileId,
            long sourceEntityId,
            long spawnTick,
            double velocityXMps,
            double velocityYMps) {
        return new ProjectileBody(
                projectileId,
                sourceEntityId,
                spawnTick,
                "material.high_strength_steel_v1",
                ProjectileShape.DART,
                1.8d,
                0.075d,
                150d,
                projectileId * 10d,
                projectileId * -2d,
                velocityXMps,
                velocityYMps);
    }
}
