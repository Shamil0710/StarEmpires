package com.spacesim.ui;

import com.spacesim.ui.ScaledLiveTacticalSimulationSession.SimulationSpeed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScaledLiveTacticalScenarioSelectionTest {
    @Test
    void everyStage19jScenarioCanDriveTheSameLiveSessionSurface() {
        for (TacticalScenarioDefinition definition : TacticalScenarioCatalog.definitions()) {
            ScaledLiveTacticalSimulationSession live = new ScaledLiveTacticalSimulationSession(definition.id());

            assertEquals(definition.id(), live.scenario().id());
            assertEquals(definition.totalShips(), live.snapshot().ships().size(), definition.displayName());
            assertEquals(0L, live.tick());
        }
    }

    @Test
    void resetRecreatesTheCurrentlySelectedScenarioInsteadOfFallingBackToSaturation() {
        for (TacticalScenarioDefinition definition : TacticalScenarioCatalog.definitions()) {
            ScaledLiveTacticalSimulationSession live = new ScaledLiveTacticalSimulationSession(definition.id());
            var initialFingerprint = live.fingerprint();
            live.setSimulationSpeed(SimulationSpeed.X4);
            live.pause();
            live.stepOneTick();
            live.stepOneTick();

            live.reset();

            assertEquals(definition.id(), live.scenario().id(), definition.displayName());
            assertEquals(definition.totalShips(), live.snapshot().ships().size(), definition.displayName());
            assertEquals(0L, live.tick(), definition.displayName());
            assertEquals(initialFingerprint, live.fingerprint(), definition.displayName());
            assertEquals(SimulationSpeed.X1, live.simulationSpeed(), definition.displayName());
            assertFalse(live.paused(), definition.displayName());
        }
    }
}
