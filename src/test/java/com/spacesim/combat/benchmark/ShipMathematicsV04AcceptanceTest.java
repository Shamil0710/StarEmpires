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
                "The reference first wave should be stopped by battleship plus one dedicated escort");
        assertTrue(escorted.leakers() * 2 <= battleshipOnly.leakers(),
                "The reference escort should cut first-wave leakers by at least half");
    }

    @Test
    void defensiveDepthComesFromFiniteLaunchersChannelsAndPointDefense() {
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
        assertEquals(6, escorted.laserBallisticMissNeutralizations());
        assertEquals(22, escorted.laserHardKills());
        assertEquals(143.12, escorted.laserBeamSeconds(), 0.05);

        assertTrue(battleshipOnly.laserMissionKills() > battleshipOnly.laserBallisticMissNeutralizations(),
                "A guidance kill must not automatically erase a still-dangerous 12 t missile body");
        assertTrue(battleshipOnly.laserHardKills() > 0,
                "Some late guidance kills should require additional physical hard-kill dwell");
    }

    @Test
    void proportionalNavigationInterceptHasARealTimeAndSafeDistanceEnvelope() {
        double areaEntryTime = (DeterministicSalvoHarness.INITIAL_RANGE_M
                - DeterministicSalvoHarness.AREA_DEFENSE_RANGE_M)
                / DeterministicSalvoHarness.INCOMING_SPEED_MPS;
        DeterministicSalvoHarness.InterceptResult timely =
                DeterministicSalvoHarness.predictReferenceIntercept(true, 23, areaEntryTime, 0.0);
        DeterministicSalvoHarness.InterceptResult tooLate =
                DeterministicSalvoHarness.predictReferenceIntercept(
                        false,
                        23,
                        DeterministicSalvoHarness.IMPACT_TIME_S - 1.0,
                        0.0);

        assertTrue(timely.success());
        assertTrue(timely.interceptTimeS() > areaEntryTime);
        assertTrue(timely.interceptTimeS() < DeterministicSalvoHarness.IMPACT_TIME_S);
        assertTrue(timely.closestRangeM() <= 150.0);

        assertFalse(tooLate.success(),
                "A proximity event inside the protected ship's safe-intercept boundary is not defense");
    }
}