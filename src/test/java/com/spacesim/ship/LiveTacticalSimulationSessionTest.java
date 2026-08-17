package com.spacesim.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalSimulationSessionTest {
    @Test
    void sessionDoesNotAdvanceUntilExplicitFixedTick() {
        LiveTacticalSimulationSession session = new LiveTacticalSimulationSession();
        var before = session.fingerprint();
        var readOnlySnapshot = session.snapshot();

        assertEquals(0L, session.tick());
        assertEquals(0d, session.elapsedSeconds(), 0d);
        assertEquals(before, session.fingerprint());
        assertEquals(0L, readOnlySnapshot.tick());

        session.advanceOneTick();

        assertEquals(1L, session.tick());
        assertEquals(LiveTacticalSimulationSession.TICK_SECONDS, session.elapsedSeconds(), 1e-12d);
        assertNotEquals(before, session.fingerprint());
    }

    @Test
    void sameNumberOfTicksProducesSameAuthoritativeState() {
        LiveTacticalSimulationSession first = new LiveTacticalSimulationSession();
        LiveTacticalSimulationSession second = new LiveTacticalSimulationSession();

        for (int index = 0; index < 500; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(first.snapshot().projectiles(), second.snapshot().projectiles());
        assertEquals(first.snapshot().targetDamage(), second.snapshot().targetDamage());
        assertEquals(first.snapshot().targetShieldState(), second.snapshot().targetShieldState());
    }

    @Test
    void physicalProjectileMovesAcrossMultipleLiveTicks() {
        LiveTacticalSimulationSession session = new LiveTacticalSimulationSession();
        ProjectileBody observed = null;

        for (int index = 0; index < 300 && observed == null; index++) {
            session.advanceOneTick();
            if (!session.snapshot().projectiles().isEmpty()) {
                observed = session.snapshot().projectiles().get(0);
            }
        }

        assertNotNull(observed, "production radar/fire control should eventually materialize a kinetic body");
        long projectileId = observed.projectileId();
        double xBefore = observed.xM();
        double yBefore = observed.yM();

        session.advanceOneTick();
        ProjectileBody next = session.snapshot().projectiles().stream()
                .filter(value -> value.projectileId() == projectileId)
                .findFirst()
                .orElse(null);

        if (next != null) {
            assertTrue(Math.hypot(next.xM() - xBefore, next.yM() - yBefore) > 0d,
                    "the authoritative projectile must move when another simulation tick executes");
        } else {
            assertTrue(session.snapshot().impactsResolved() > 0L,
                    "a body may disappear only because the live simulation resolved its target intersection");
        }
    }

    @Test
    void launchesConsumeFiniteAmmunitionAndImpactsChangePhysicalProtectionState() {
        LiveTacticalSimulationSession session = new LiveTacticalSimulationSession();
        var initial = session.snapshot();
        double initialShield = initial.targetShieldState().reserveJ();
        long initialRounds = initial.primaryRoundsRemaining();

        for (int index = 0; index < 800 && session.snapshot().impactsResolved() == 0L; index++) {
            session.advanceOneTick();
        }

        var afterImpact = session.snapshot();
        assertTrue(afterImpact.shotsFired() > 0L);
        assertTrue(afterImpact.primaryRoundsRemaining() < initialRounds,
                "a live shot must consume physical ammunition before the body exists");
        assertTrue(afterImpact.impactsResolved() > 0L,
                "at least one independently moving body must reach the target through live ticks");
        assertTrue(afterImpact.targetShieldState().reserveJ() < initialShield
                        || meanIntegrity(afterImpact) < 1d,
                "the production shield/material/damage path must change authoritative target state");
    }

    private static double meanIntegrity(LiveTacticalSimulationSession.Snapshot snapshot) {
        return snapshot.targetHull().compartments().stream()
                .mapToDouble(value -> snapshot.targetDamage().compartmentIntegrityById()
                        .getOrDefault(value.id(), 1d))
                .average()
                .orElse(1d);
    }
}
