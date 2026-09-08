package com.spacesim.persistence;

import com.spacesim.world.WorldSimulation;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for exact generated-world continuation with a non-default multi-system scheduler. */
class Stage20GeneratedWorldSchedulerPersistenceAcceptanceTest {
    private static final int CURRENT_FILE_FORMAT_VERSION = 4;

    @Test
    void generatedWorldSchedulerRoundTripsAndKeepsRemoteContinuationExact() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime direct = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        Stage20GeneratedWorldRuntimePersistentState checkpoint = direct.captureState();

        assertEquals(direct.world().getStrategicStepTicks(), checkpoint.strategicStepTicks());
        assertEquals(direct.world().getRemoteUpdateBudgetPerFrame(), checkpoint.remoteUpdateBudgetPerFrame());
        assertEquals(checkpoint.worldState().systems().size(), checkpoint.remoteUpdateBudgetPerFrame(),
                "production generated world intentionally budgets every remote system per frame");
        assertTrue(checkpoint.remoteUpdateBudgetPerFrame() > WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME,
                "regression fixture must differ from the historical restore default");

        byte[] encoded = Stage20GeneratedWorldRuntimePersistenceCodec.encode(checkpoint);
        assertEquals(CURRENT_FILE_FORMAT_VERSION, ByteBuffer.wrap(encoded).getInt(4));
        Stage20GeneratedWorldRuntimePersistentState decoded =
                Stage20GeneratedWorldRuntimePersistenceCodec.decode(encoded);
        assertEquals(checkpoint, decoded);
        assertArrayEquals(encoded, Stage20GeneratedWorldRuntimePersistenceCodec.encode(decoded));

        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restored =
                Stage20GeneratedWorldRuntimeBridge.restore(decoded);
        assertEquals(direct.world().getStrategicStepTicks(), restored.world().getStrategicStepTicks());
        assertEquals(direct.world().getRemoteUpdateBudgetPerFrame(),
                restored.world().getRemoteUpdateBudgetPerFrame());

        float fixedStep = direct.world().findSession(direct.world().getActiveSystemId()).orElseThrow()
                .getClock().getFixedStepSeconds();
        for (int frame = 0; frame < 25; frame++) {
            direct.advanceFrame(fixedStep);
            restored.advanceFrame(fixedStep);
        }

        Stage20GeneratedWorldRuntimePersistentState directFinal = direct.captureState();
        Stage20GeneratedWorldRuntimePersistentState restoredFinal = restored.captureState();
        assertEquals(directFinal, restoredFinal,
                "save/load must preserve the exact remote-system scheduler continuation");
        assertArrayEquals(
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(directFinal),
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(restoredFinal));
    }

    @Test
    void invalidSchedulerConfigurationFailsClosedAtCheckpointBoundary() {
        Stage20GeneratedWorldRuntimePersistentState valid = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime().captureState();

        assertThrows(IllegalArgumentException.class, () -> new Stage20GeneratedWorldRuntimePersistentState(
                valid.schemaVersion(), valid.bridgeVersion(), valid.campaign(), valid.worldState(),
                valid.activeSystemId(), 1, valid.remoteUpdateBudgetPerFrame(), valid.freight(),
                valid.localFleetPhysicalStates()));
        assertThrows(IllegalArgumentException.class, () -> new Stage20GeneratedWorldRuntimePersistentState(
                valid.schemaVersion(), valid.bridgeVersion(), valid.campaign(), valid.worldState(),
                valid.activeSystemId(), valid.strategicStepTicks(), 0, valid.freight(),
                valid.localFleetPhysicalStates()));
    }
}
