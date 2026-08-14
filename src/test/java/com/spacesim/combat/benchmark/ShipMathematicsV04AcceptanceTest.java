package com.spacesim.combat.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipMathematicsV04AcceptanceTest {
    @Test
    void synchronizedCorvetteWaveIsDeterministicAndEscortReducesLeakers() {
        DeterministicSalvoHarness.SalvoReport battleshipOnly =
                DeterministicSalvoHarness.runScenario(false);
        DeterministicSalvoHarness.SalvoReport repeatedBattleshipOnly =
                DeterministicSalvoHarness.runScenario(false);
        DeterministicSalvoHarness.SalvoReport escorted =
                DeterministicSalvoHarness.runScenario(true);

        assertEquals(battleshipOnly, repeatedBattleshipOnly,
                "Identical physical state must produce an identical benchmark outcome");
        assertEquals(DeterministicSalvoHarness.INCOMING_THREATS, battleshipOnly.accountedThreats());
        assertEquals(DeterministicSalvoHarness.INCOMING_THREATS, escorted.accountedThreats());

        assertEquals(3, battleshipOnly.leakers(),
                "The calibrated unescorted battleship must not make the corvette wave irrelevant");
        assertEquals(0, escorted.leakers(),
                "The reference first wave should be stopped by battleship plus one close-screen escort");
        assertTrue(escorted.leakers() * 2 <= battleshipOnly.leakers(),
                "The reference escort should cut first-wave leakers by at least half");
    }

    @Test
    void defensiveDepthComesFromFiniteLaunchersChannelsAndDistributedPointDefense() {
        DeterministicSalvoHarness.SalvoReport battleshipOnly =
                DeterministicSalvoHarness.runScenario(false);
        DeterministicSalvoHarness.SalvoReport escorted =
                DeterministicSalvoHarness.runScenario(true);

        assertEquals(6, battleshipOnly.areaInterceptorsExpended(),
                "One L battery is constrained by six simultaneous terminal-support channels");
        assertEquals(4, battleshipOnly.fleetInterceptorsExpended(),
                "Two M batteries expose four first-wave terminal-support channels");
        assertEquals(6, battleshipOnly.areaInterceptorKills());
        assertEquals(4, battleshipOnly.fleetInterceptorKills());
        assertEquals(6, battleshipOnly.pointDefenseLasers());
        assertEquals(35, battleshipOnly.laserMissionKills());
        assertEquals(5, battleshipOnly.laserBallisticMissNeutralizations());
        assertEquals(30, battleshipOnly.laserHardKills());
        assertEquals(87.64, battleshipOnly.laserBeamSeconds(), 0.05);

        assertEquals(12, escorted.areaInterceptorsExpended());
        assertEquals(8, escorted.fleetInterceptorsExpended());
        assertEquals(12, escorted.areaInterceptorKills());
        assertEquals(8, escorted.fleetInterceptorKills());
        assertEquals(10, escorted.pointDefenseLasers());
        assertEquals(28, escorted.laserMissionKills());
        assertEquals(9, escorted.laserBallisticMissNeutralizations());
        assertEquals(19, escorted.laserHardKills());
        assertEquals(140.88, escorted.laserBeamSeconds(), 0.05);

        assertTrue(battleshipOnly.laserMissionKills() > battleshipOnly.laserBallisticMissNeutralizations(),
                "A guidance kill must not automatically erase a still-dangerous 12 t missile body");
        assertTrue(battleshipOnly.laserHardKills() > 0,
                "Some late guidance kills should require additional physical hard-kill dwell");
    }

    @Test
    void proportionalNavigationExposesSafeDistanceAndFormationGeometry() {
        double areaEntryTime = (DeterministicSalvoHarness.INITIAL_RANGE_M
                - DeterministicSalvoHarness.AREA_DEFENSE_RANGE_M)
                / DeterministicSalvoHarness.INCOMING_SPEED_MPS;
        double fleetEntryTime = (DeterministicSalvoHarness.INITIAL_RANGE_M
                - DeterministicSalvoHarness.FLEET_INTERCEPTOR_RANGE_M)
                / DeterministicSalvoHarness.INCOMING_SPEED_MPS;

        DeterministicSalvoHarness.InterceptResult timelyAreaIntercept =
                DeterministicSalvoHarness.predictReferenceIntercept(true, 23, areaEntryTime, 0.0);
        DeterministicSalvoHarness.InterceptResult closeEscortFleetIntercept =
                DeterministicSalvoHarness.predictReferenceIntercept(
                        false,
                        23,
                        fleetEntryTime,
                        DeterministicSalvoHarness.DEFENDER_ESCORT_OFFSET_Y_M);
        DeterministicSalvoHarness.InterceptResult overWideEscortFleetIntercept =
                DeterministicSalvoHarness.predictReferenceIntercept(false, 23, fleetEntryTime, 25_000.0);
        DeterministicSalvoHarness.InterceptResult tooLate =
                DeterministicSalvoHarness.predictReferenceIntercept(
                        false,
                        23,
                        DeterministicSalvoHarness.IMPACT_TIME_S - 1.0,
                        0.0);

        assertTrue(timelyAreaIntercept.success());
        assertTrue(timelyAreaIntercept.interceptTimeS() > areaEntryTime);
        assertTrue(timelyAreaIntercept.interceptTimeS() < DeterministicSalvoHarness.IMPACT_TIME_S);
        assertTrue(timelyAreaIntercept.closestRangeM() <= 150.0);

        assertTrue(closeEscortFleetIntercept.success(),
                "A 15 km close-screen escort can use the 350 km M-interceptor layer safely");
        assertFalse(overWideEscortFleetIntercept.success(),
                "At 25 km lateral separation the same inner-layer interceptor reaches the threat too late");
        assertFalse(tooLate.success(),
                "A proximity event inside the protected ship's safe-intercept boundary is not defense");
    }
}