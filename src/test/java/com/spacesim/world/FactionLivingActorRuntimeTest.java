package com.spacesim.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactionLivingActorRuntimeTest {

    @Test
    void failedSnapshotPublicationDoesNotPartiallyAdvanceSelectedBatch() {
        FactionLivingActorRuntime runtime = FactionLivingActorRuntime.restore(List.of(
                FactionLivingActorState.initial("faction.alpha", 10L),
                FactionLivingActorState.initial("faction.bravo", 10L)));

        assertThrows(IllegalArgumentException.class, () -> runtime.reviewDue(
                10L,
                2,
                20L,
                factionId -> {
                    if ("faction.alpha".equals(factionId)) {
                        return emptySnapshot(factionId, 10L);
                    }
                    return emptySnapshot(factionId, 9L);
                }));

        assertEquals(0L, runtime.findState("faction.alpha").orElseThrow().completedReviewCount());
        assertEquals(10L, runtime.findState("faction.alpha").orElseThrow().nextReviewTick());
        assertEquals(0L, runtime.findState("faction.bravo").orElseThrow().completedReviewCount());
        assertEquals(10L, runtime.findState("faction.bravo").orElseThrow().nextReviewTick());

        FactionLivingActorRuntime.RuntimeReviewBatch retry = runtime.reviewDue(
                10L,
                2,
                20L,
                factionId -> emptySnapshot(factionId, 10L));

        assertEquals(2, retry.reviews().size());
        assertEquals(1L, runtime.findState("faction.alpha").orElseThrow().completedReviewCount());
        assertEquals(30L, runtime.findState("faction.alpha").orElseThrow().nextReviewTick());
        assertEquals(1L, runtime.findState("faction.bravo").orElseThrow().completedReviewCount());
        assertEquals(30L, runtime.findState("faction.bravo").orElseThrow().nextReviewTick());
    }

    @Test
    void snapshotPublisherRunsOnlyForSchedulerSelectedActors() {
        FactionLivingActorRuntime runtime = FactionLivingActorRuntime.restore(List.of(
                FactionLivingActorState.initial("faction.charlie", 10L),
                FactionLivingActorState.initial("faction.alpha", 10L),
                FactionLivingActorState.initial("faction.bravo", 10L)));
        ArrayList<String> published = new ArrayList<>();

        FactionLivingActorRuntime.RuntimeReviewBatch batch = runtime.reviewDue(
                10L,
                2,
                20L,
                factionId -> {
                    published.add(factionId);
                    return emptySnapshot(factionId, 10L);
                });

        assertEquals(List.of("faction.alpha", "faction.bravo"), published);
        assertEquals(1, batch.schedule().deferredCount());
        assertEquals(1L, runtime.findState("faction.alpha").orElseThrow().completedReviewCount());
        assertEquals(1L, runtime.findState("faction.bravo").orElseThrow().completedReviewCount());
        assertEquals(0L, runtime.findState("faction.charlie").orElseThrow().completedReviewCount());
        assertEquals(10L, runtime.findState("faction.charlie").orElseThrow().nextReviewTick());
    }

    @Test
    void captureRestorePreservesCommitmentAndWakeupLifecycleMetadata() {
        FactionLivingActorRuntime runtime = FactionLivingActorRuntime.restore(List.of(
                FactionLivingActorState.initial("faction.alpha", 100L)));
        runtime.setCommitmentUntilTick("faction.alpha", 140L);
        runtime.registerWakeup(
                "faction.alpha",
                new FactionLivingActorState.EventWakeup(
                        FactionLivingActorState.WakeupReason.TREATY_CHANGED,
                        "treaty.change.42",
                        50L,
                        60L));

        FactionLivingActorRuntime restored = FactionLivingActorRuntime.restore(runtime.capture());

        assertEquals(runtime.capture(), restored.capture());
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
