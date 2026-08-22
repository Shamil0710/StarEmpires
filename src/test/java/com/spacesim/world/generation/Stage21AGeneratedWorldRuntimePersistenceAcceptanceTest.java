package com.spacesim.world.generation;

import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimePersistentState;
import com.spacesim.world.FactionActorObservationSnapshot;
import com.spacesim.world.FactionLivingActorState.EventWakeup;
import com.spacesim.world.FactionLivingActorState.WakeupReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21AGeneratedWorldRuntimePersistenceAcceptanceTest {

    @Test
    void realGeneratedWorldAndActorLifecycleRoundTripAtomicallyAndConsumeWakeupOnce() {
        var stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var stage20Before = stage20.captureState();
        String factionId = stage20Before.worldState().factions().get(0).factionContentId();
        long nowTick = stage20.world().getAuthoritativeWorldTick();

        var stage21a = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(
                stage20,
                List.of(factionId),
                30L);
        stage21a.actors().setCommitmentUntilTick(factionId, nowTick + 120L);
        stage21a.registerWakeup(
                factionId,
                new EventWakeup(
                        WakeupReason.MATERIAL_OBSERVATION_CHANGED,
                        "integration.observation.1",
                        nowTick,
                        nowTick));

        Stage21AGeneratedWorldRuntimePersistentState captured = stage21a.captureState();
        byte[] encoded = Stage21AGeneratedWorldRuntimePersistenceCodec.encode(captured);
        Stage21AGeneratedWorldRuntimePersistentState decoded =
                Stage21AGeneratedWorldRuntimePersistenceCodec.decode(encoded);
        var restored = Stage21AGeneratedWorldRuntimeBridge.restore(decoded);

        assertArrayEquals(encoded, Stage21AGeneratedWorldRuntimePersistenceCodec.encode(decoded));
        assertArrayEquals(
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(stage20Before),
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(decoded.stage20Runtime()));
        assertEquals(captured.livingActors(), restored.captureState().livingActors());
        assertEquals(120L + nowTick,
                restored.findActorState(factionId).orElseThrow().commitmentUntilTick());
        assertEquals(1, restored.findActorState(factionId).orElseThrow().pendingWakeups().size());

        var review = restored.reviewDue(
                1,
                30L,
                selectedFactionId -> emptySnapshot(selectedFactionId, nowTick));
        assertEquals(1, review.reviews().size());
        assertEquals(1L,
                restored.findActorState(factionId).orElseThrow().completedReviewCount());
        assertTrue(restored.findActorState(factionId).orElseThrow().pendingWakeups().isEmpty());

        byte[] afterReviewBytes = Stage21AGeneratedWorldRuntimePersistenceCodec.encode(
                restored.captureState());
        var restoredAgain = Stage21AGeneratedWorldRuntimeBridge.restore(
                Stage21AGeneratedWorldRuntimePersistenceCodec.decode(afterReviewBytes));
        assertTrue(restoredAgain.reviewDue(
                1,
                30L,
                selectedFactionId -> emptySnapshot(selectedFactionId, nowTick))
                .reviews()
                .isEmpty());
        assertEquals(1L,
                restoredAgain.findActorState(factionId).orElseThrow().completedReviewCount());
        assertArrayEquals(
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(stage20Before),
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(
                        restoredAgain.captureState().stage20Runtime()));
    }

    private static FactionActorObservationSnapshot emptySnapshot(String factionId, long tick) {
        return new FactionActorObservationSnapshot(
                factionId,
                tick,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
