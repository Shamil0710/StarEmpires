package com.spacesim.combat.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipMathematicsV05SweepAcceptanceTest {
    private static final String EXPECTED_FINGERPRINT =
            "5c1ee91e262a410fffd7af46a4d328c7788c82612dd594ae375f3bd9487eac26";

    private static final List<ShipMathematicsV05SweepHarness.SurfacePoint> SURFACE =
            ShipMathematicsV05SweepHarness.runSingleWaveSurface();
    private static final List<ShipMathematicsV05SweepHarness.EndurancePoint> ENDURANCE =
            ShipMathematicsV05SweepHarness.runCanonicalEnduranceSurface();

    @Test
    void completeSweepIsDeterministicAndEveryThreatIsAccountedFor() {
        assertEquals(95, SURFACE.size());
        assertEquals(247, ENDURANCE.size());
        assertEquals(EXPECTED_FINGERPRINT,
                ShipMathematicsV05SweepHarness.fingerprint(SURFACE, ENDURANCE));

        for (ShipMathematicsV05SweepHarness.SurfacePoint point : SURFACE) {
            assertEquals(point.report().incomingThreats(), point.report().accountedThreats());
        }
        for (ShipMathematicsV05SweepHarness.EndurancePoint point : ENDURANCE) {
            assertEquals(point.report().incomingThreats(), point.report().accountedThreats());
        }
    }

    @Test
    void generalizedSolverPreservesTheV04CanonicalAnchors() {
        DeterministicSalvoHarness.SalvoReport v04Battleship =
                DeterministicSalvoHarness.runScenario(false);
        DeterministicSalvoHarness.SalvoReport v04Escorted =
                DeterministicSalvoHarness.runScenario(true);

        ShipMathematicsV05SweepHarness.SurfacePoint v05Battleship =
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 24, 0, 0.0);
        ShipMathematicsV05SweepHarness.SurfacePoint v05Escorted =
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 24, 1, 15_000.0);

        assertSameOutcome(v04Battleship, v05Battleship.report());
        assertSameOutcome(v04Escorted, v05Escorted.report());
    }

    @Test
    void firstWaveSurfaceExposesSaturationAndFormationGeometry() {
        assertEquals(0,
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 16, 0, 0.0)
                        .report().leakers());
        assertEquals(3,
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 24, 0, 0.0)
                        .report().leakers());
        assertEquals(18,
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 32, 0, 0.0)
                        .report().leakers());
        assertEquals(50,
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 48, 0, 0.0)
                        .report().leakers());

        ShipMathematicsV05SweepHarness.SalvoReport oneEscort15 =
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 24, 1, 15_000.0).report();
        ShipMathematicsV05SweepHarness.SalvoReport oneEscort25 =
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 24, 1, 25_000.0).report();
        assertEquals(8, oneEscort15.fleetInterceptorKills());
        assertEquals(4, oneEscort25.fleetInterceptorKills());
        assertTrue(oneEscort25.laserBeamSeconds() > oneEscort15.laserBeamSeconds());

        assertEquals(20,
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 48, 1, 15_000.0)
                        .report().leakers());
        assertEquals(0,
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 48, 2, 15_000.0)
                        .report().leakers());
        assertEquals(10,
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 48, 2, 25_000.0)
                        .report().leakers());
        assertEquals(1,
                ShipMathematicsV05SweepHarness.findSurfacePoint(SURFACE, 48, 3, 25_000.0)
                        .report().leakers());
    }

    @Test
    void enduranceSurfaceExposesMagazineCliffsWithoutInventingReloads() {
        ShipMathematicsV05SweepHarness.EndurancePoint battleshipWave1 =
                ShipMathematicsV05SweepHarness.findEndurancePoint(ENDURANCE, 0, 0.0, 1);
        ShipMathematicsV05SweepHarness.EndurancePoint battleshipWave8 =
                ShipMathematicsV05SweepHarness.findEndurancePoint(ENDURANCE, 0, 0.0, 8);
        ShipMathematicsV05SweepHarness.EndurancePoint battleshipWave9 =
                ShipMathematicsV05SweepHarness.findEndurancePoint(ENDURANCE, 0, 0.0, 9);
        ShipMathematicsV05SweepHarness.EndurancePoint battleshipWave12 =
                ShipMathematicsV05SweepHarness.findEndurancePoint(ENDURANCE, 0, 0.0, 12);
        ShipMathematicsV05SweepHarness.EndurancePoint battleshipWave13 =
                ShipMathematicsV05SweepHarness.findEndurancePoint(ENDURANCE, 0, 0.0, 13);

        assertEquals(3, battleshipWave1.report().leakers());
        assertEquals(0, battleshipWave8.areaRoundsRemaining());
        assertEquals(0, battleshipWave9.report().areaInterceptorsExpended());
        assertEquals(9, battleshipWave9.report().leakers());
        assertEquals(0, battleshipWave12.fleetRoundsRemaining());
        assertEquals(0, battleshipWave13.report().fleetInterceptorsExpended());
        assertEquals(13, battleshipWave13.report().leakers());

        ShipMathematicsV05SweepHarness.EndurancePoint escortedPdOnly =
                ShipMathematicsV05SweepHarness.findEndurancePoint(ENDURANCE, 1, 15_000.0, 13);
        assertEquals(0, escortedPdOnly.areaRoundsRemaining());
        assertEquals(0, escortedPdOnly.fleetRoundsRemaining());
        assertEquals(0, escortedPdOnly.report().leakers(),
                "Current 10-laser seed remains strong enough to stop the canonical 48-threat wave even after missile magazines are empty");
    }

    private static void assertSameOutcome(
            DeterministicSalvoHarness.SalvoReport v04,
            ShipMathematicsV05SweepHarness.SalvoReport v05) {
        assertEquals(v04.incomingThreats(), v05.incomingThreats());
        assertEquals(v04.areaInterceptorKills(), v05.areaInterceptorKills());
        assertEquals(v04.fleetInterceptorKills(), v05.fleetInterceptorKills());
        assertEquals(v04.areaInterceptorsExpended(), v05.areaInterceptorsExpended());
        assertEquals(v04.fleetInterceptorsExpended(), v05.fleetInterceptorsExpended());
        assertEquals(v04.pointDefenseLasers(), v05.pointDefenseLasers());
        assertEquals(v04.laserMissionKills(), v05.laserMissionKills());
        assertEquals(v04.laserBallisticMissNeutralizations(), v05.laserBallisticMissNeutralizations());
        assertEquals(v04.laserHardKills(), v05.laserHardKills());
        assertEquals(v04.laserBeamSeconds(), v05.laserBeamSeconds(), 1.0e-9);
        assertEquals(v04.leakers(), v05.leakers());
    }
}
