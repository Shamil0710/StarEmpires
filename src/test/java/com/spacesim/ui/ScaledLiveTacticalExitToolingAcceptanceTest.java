package com.spacesim.ui;

import com.spacesim.ui.ScaledLiveTacticalSimulationSession.SimulationSpeed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaledLiveTacticalExitToolingAcceptanceTest {
    @Test
    void pauseResumeAndSingleStepNeverChangeFixedTickAuthority() {
        ScaledLiveTacticalSimulationSession live = new ScaledLiveTacticalSimulationSession();
        live.pause();
        var pausedFingerprint = live.fingerprint();

        assertEquals(0, live.advanceScheduledBatch());
        assertEquals(0L, live.tick());
        assertEquals(pausedFingerprint, live.fingerprint());
        assertTrue(live.paused());

        live.stepOneTick();
        assertEquals(1L, live.tick(), "single-step must execute exactly one unchanged production tick while paused");
        assertTrue(live.paused(), "single-step must not implicitly resume scheduled execution");

        live.resume();
        assertEquals(1, live.advanceScheduledBatch());
        assertEquals(2L, live.tick());
        assertFalse(live.paused());
    }

    @Test
    void speedControlOnlyBatchesIdenticalFixedTicks() {
        ScaledLiveTacticalSimulationSession batched = new ScaledLiveTacticalSimulationSession();
        ScaledLiveTacticalSimulationSession single = new ScaledLiveTacticalSimulationSession();
        batched.setSimulationSpeed(SimulationSpeed.X8);

        for (int batch = 0; batch < 10; batch++) {
            assertEquals(8, batched.advanceScheduledBatch());
        }
        for (int tick = 0; tick < 80; tick++) {
            single.stepOneTick();
        }

        assertEquals(80L, batched.tick());
        assertEquals(single.tick(), batched.tick());
        assertEquals(single.fingerprint(), batched.fingerprint(),
                "presentation speed must change only fixed-tick batching, never authoritative results");
    }

    @Test
    void deterministicResetRecreatesCanonicalFactoryState() {
        ScaledLiveTacticalSimulationSession live = new ScaledLiveTacticalSimulationSession();
        ScaledLiveTacticalSimulationSession fresh = new ScaledLiveTacticalSimulationSession();
        var initialFingerprint = fresh.fingerprint();
        live.setSimulationSpeed(SimulationSpeed.X4);
        live.pause();
        for (int tick = 0; tick < 30; tick++) {
            live.stepOneTick();
        }

        live.reset();

        assertEquals(0L, live.tick());
        assertEquals(initialFingerprint, live.fingerprint());
        assertEquals(SimulationSpeed.X1, live.simulationSpeed());
        assertFalse(live.paused());
    }

    @Test
    void repeatedDebugReadsExposeAuthorityWithoutMutatingIt() {
        ScaledLiveTacticalSimulationSession live = new ScaledLiveTacticalSimulationSession();
        for (int tick = 0; tick < 24; tick++) {
            live.stepOneTick();
        }
        var before = live.fingerprint();
        ScaledTacticalDebugSnapshot debug = null;

        for (int read = 0; read < 40; read++) {
            debug = live.debugSnapshot();
        }

        assertEquals(before, live.fingerprint());
        assertEquals(24L, live.tick());
        assertEquals(24L, debug.tick());
        assertEquals(32, debug.combatants().size());
        assertTrue(debug.combatants().stream().allMatch(value -> value.ammunitionCount() >= 0L));
        assertTrue(debug.combatants().stream().allMatch(value -> value.reactionMassKg() >= 0d));
        assertTrue(debug.combatants().stream().allMatch(value -> value.sharedBusEnergyJ() >= 0d));
        assertTrue(debug.combatants().stream().allMatch(value -> value.shipHeatStoredJ() >= 0d));
        assertTrue(debug.combatants().stream().allMatch(value -> value.localHeatStoredJ() >= 0d));
        assertEquals(
                live.snapshot().bodies().size(),
                debug.bodies().total(),
                "debug body counts must be a read-only projection of the same physical pools as the visual snapshot");
    }
}
