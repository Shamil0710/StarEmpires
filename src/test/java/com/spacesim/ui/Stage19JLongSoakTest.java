package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalBattleControlRuntime;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Long-running Stage-19J closeout soak, activated only by the dedicated PR workflow. */
class Stage19JLongSoakTest {
    private static final double STANDARD_SOAK_SECONDS = 130d;
    private static final double SATURATION_SOAK_SECONDS = 600d;
    private static final int SAMPLE_EVERY_TICKS = 20;

    @Test
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void allCanonicalScenariosSurviveLongRunningProductionTicks() {
        Assumptions.assumeTrue(Boolean.getBoolean("stage19j.soak"),
                "dedicated Stage-19J soak is disabled in ordinary CI");

        boolean observedDamage = false;
        boolean observedPhysicalDepletion = false;
        for (TacticalScenarioId scenarioId : TacticalScenarioId.values()) {
            double requestedSeconds = scenarioId == TacticalScenarioId.SATURATION_16V16
                    ? SATURATION_SOAK_SECONDS
                    : STANDARD_SOAK_SECONDS;
            SoakResult result = runScenario(scenarioId, requestedSeconds);
            observedDamage |= result.observedDamage();
            observedPhysicalDepletion |= result.observedPhysicalDepletion();
            System.out.printf(
                    "STAGE19J_SOAK scenario=%s ticks=%d simulated=%.2fs wall=%.3fs "
                            + "alive=%d/%d maxBodies=%d maxTracks=%d trackLoss=%s damage=%s depletion=%s fingerprintHash=%d%n",
                    scenarioId.cliKey(),
                    result.ticks(),
                    result.simulatedSeconds(),
                    result.wallSeconds(),
                    result.finalAlive(),
                    result.totalShips(),
                    result.maxBodies(),
                    result.maxTracks(),
                    result.observedTrackLoss(),
                    result.observedDamage(),
                    result.observedPhysicalDepletion(),
                    result.fingerprintHash());
        }

        assertTrue(observedDamage,
                "canonical soak matrix must include a real damaged-engineering state");
        assertTrue(observedPhysicalDepletion,
                "canonical soak matrix must include a real depleted physical-resource state");
    }

    private static SoakResult runScenario(TacticalScenarioId scenarioId, double requestedSeconds) {
        ScaledLiveTacticalSimulationSession session = new ScaledLiveTacticalSimulationSession(scenarioId);
        long targetTicks = (long) Math.ceil(requestedSeconds / LiveTacticalBattleControlRuntime.TICK_SECONDS);
        Map<Long, Integer> previousTrackCounts = new HashMap<>();
        boolean observedTrackLoss = false;
        boolean observedDamage = false;
        boolean observedPhysicalDepletion = false;
        int maxBodies = 0;
        int maxTracks = 0;
        long startedNanos = System.nanoTime();

        for (long index = 0L; index < targetTicks; index++) {
            session.stepOneTick();
            if ((index + 1L) % SAMPLE_EVERY_TICKS != 0L && index + 1L != targetTicks) {
                continue;
            }
            ScaledTacticalDebugSnapshot debug = session.debugSnapshot();
            var visual = session.snapshot();
            int bodyCount = debug.bodies().kinetic()
                    + debug.bodies().strike()
                    + debug.bodies().interceptor()
                    + debug.bodies().decoy();
            maxBodies = Math.max(maxBodies, bodyCount);

            for (var actor : debug.combatants()) {
                int currentTracks = actor.tracks().size();
                Integer previousTracks = previousTrackCounts.put(actor.entityId(), currentTracks);
                if (previousTracks != null && currentTracks < previousTracks) {
                    observedTrackLoss = true;
                }
                maxTracks = Math.max(maxTracks, currentTracks);
                observedDamage |= actor.meanCompartmentIntegrity() < 0.999_999d
                        || actor.minimumModuleIntegrity() < 0.999_999d;
                observedPhysicalDepletion |= actor.reactionMassKg() <= 0d
                        || actor.ammunitionCount() <= 0L;
            }
            observedDamage |= visual.ships().stream().anyMatch(ship -> ship.wreck()
                    || ship.integrityFraction() < 0.999_999d);
        }

        long elapsedNanos = System.nanoTime() - startedNanos;
        ScaledTacticalDebugSnapshot finalDebug = session.debugSnapshot();
        var finalVisual = session.snapshot();
        var fingerprint = session.fingerprint();
        assertNotNull(fingerprint);
        assertEquals(targetTicks, session.tick(), "soak must execute every requested fixed tick");
        assertEquals(session.scenario().totalShips(), finalVisual.ships().size(),
                "presentation roster must retain all physical combatant identities");
        if (scenarioId == TacticalScenarioId.SATURATION_16V16) {
            assertTrue(session.tick() * LiveTacticalBattleControlRuntime.TICK_SECONDS >= SATURATION_SOAK_SECONDS,
                    "saturation closeout must reach at least ten simulated minutes");
        }

        int finalAlive = (int) finalVisual.ships().stream().filter(ship -> !ship.wreck()).count();
        return new SoakResult(
                targetTicks,
                session.tick() * LiveTacticalBattleControlRuntime.TICK_SECONDS,
                elapsedNanos / 1_000_000_000d,
                finalAlive,
                finalDebug.combatants().size(),
                maxBodies,
                maxTracks,
                observedTrackLoss,
                observedDamage,
                observedPhysicalDepletion,
                fingerprint.hashCode());
    }

    private record SoakResult(
            long ticks,
            double simulatedSeconds,
            double wallSeconds,
            int finalAlive,
            int totalShips,
            int maxBodies,
            int maxTracks,
            boolean observedTrackLoss,
            boolean observedDamage,
            boolean observedPhysicalDepletion,
            int fingerprintHash) { }
}
