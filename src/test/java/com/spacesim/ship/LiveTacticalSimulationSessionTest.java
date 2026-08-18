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
    void productionAiMovementConsumesPhysicalReactionMass() {
        LiveTacticalSimulationSession session = new LiveTacticalSimulationSession();
        var initial = session.snapshot();

        assertTrue(initial.attackerReactionMassKg() > 0d,
                "the production live fit must start with finite physical reaction mass");
        assertTrue(!initial.attackerIntent().targetSelected(),
                "the AI must not begin with an omniscient preselected target");
        assertTrue(!initial.attackerFireAuthorized(),
                "fire cannot be authorized before the production information model has observed a target");

        for (int index = 0; index < 80; index++) {
            session.advanceOneTick();
        }

        var after = session.snapshot();
        assertNotNull(after.attackerTrack(),
                "production sensing should establish actor-visible target information");
        assertTrue(after.attackerIntent().targetSelected(),
                "production Stage-19 tactical AI should select the observed hostile contact");
        assertTrue(Math.hypot(
                        after.attackerXM() - initial.attackerXM(),
                        after.attackerYM() - initial.attackerYM()) > 0d,
                "tactical intent must move the authoritative physical transform rather than only set a label");
        assertTrue(Math.hypot(after.attackerVelocityXMps(), after.attackerVelocityYMps()) > 0d,
                "AI maneuvering must produce real inertial velocity through the shared flight integrator");
        assertTrue(after.attackerReactionMassKg() < initial.attackerReactionMassKg(),
                "AI-requested thrust must consume finite Stage-17.5 reaction mass");
        assertTrue(after.attackerReactionMassKg() >= 0d,
                "physical maneuvering may not create a negative reaction-mass inventory");
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
