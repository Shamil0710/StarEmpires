package com.spacesim.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalStaleMeasurementRegressionTest {
    @Test
    void sensorLossPastLocalMeasurementAgeDropsContactInsteadOfCrashingFusion() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.legacyDuel());
        LiveTacticalBattleControlRuntime control = new LiveTacticalBattleControlRuntime(battle);
        long observerId = LiveTacticalSimulationSession.ATTACKER_ENTITY_ID;

        control.advanceOneTick();
        assertFalse(battle.visibleContacts(observerId).isEmpty(),
                "the regression requires an initially measured hostile track");

        new LiveTacticalInitialReadinessService().setModuleIntegrity(
                battle.requireCombatant(observerId),
                "utility_sensor",
                0d);

        long ticksPastFreshnessHorizon =
                (long) Math.ceil(DatalinkState.local().maxMeasurementAgeSeconds()
                        / LiveTacticalBattleControlRuntime.TICK_SECONDS) + 10L;
        assertDoesNotThrow(() -> {
            for (long index = 0L; index < ticksPastFreshnessHorizon; index++) {
                control.advanceOneTick();
            }
        });

        assertTrue(battle.visibleContacts(observerId).isEmpty(),
                "stale local measurements must not remain visible after the fusion freshness horizon");
    }
}
