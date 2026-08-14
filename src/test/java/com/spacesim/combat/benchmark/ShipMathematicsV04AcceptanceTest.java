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

        assertTrue(battleshipOnly.leakers() > 0,
                "An unescorted line ship must not make the synchronized corvette wave irrelevant");
        assertTrue(escorted.leakers() < battleshipOnly.leakers(),
                "One area-defense destroyer must materially reduce terminal leakers");
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

        assertEquals(12, escorted.areaInterceptorsExpended());
        assertEquals(8, escorted.fleetInterceptorsExpended());
        assertEquals(12, escorted.areaInterceptorKills());
        assertEquals(8, escorted.fleetInterceptorKills());
        assertEquals(10, escorted.pointDefenseLasers());

        assertTrue(escorted.laserBeamSeconds() > 0.0);
        assertTrue(battleshipOnly.laserMissionKills() > battleshipOnly.laserBallisticMissNeutralizations(),
                "A guidance kill must not automatically erase a still-dangerous 12 t missile body");
        assertTrue(battleshipOnly.laserHardKills() > 0,
                "Some late guidance kills should require additional physical hard-kill dwell");
    }

    @Test
    void proportionalNavigationInterceptHasARealTimeAndAccelerationEnvelope() {
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
                "A missile launched one second before impact cannot teleport into an interception");
    }
}