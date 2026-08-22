package com.spacesim.world;

import com.spacesim.world.FactionLivingActorScheduler.TriggerType;
import com.spacesim.world.FactionLivingActorState.EventWakeup;
import com.spacesim.world.FactionLivingActorState.WakeupReason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionLivingActorSchedulerTest {

    @Test
    void saveLoadImmediatelyBeforeDeadlineProducesExactlyOneReviewAtDeadline() {
        FactionLivingActorState beforeDeadline = FactionLivingActorState.initial("faction.alpha", 50L);
        byte[] checkpoint = FactionLivingActorStateCodec.encode(List.of(beforeDeadline));
        FactionLivingActorState loaded = FactionLivingActorStateCodec.decode(checkpoint).get(0);

        assertTrue(FactionLivingActorScheduler.selectDue(List.of(loaded), 49L, 4).selected().isEmpty());
        FactionLivingActorScheduler.ScheduleBatch due =
                FactionLivingActorScheduler.selectDue(List.of(loaded), 50L, 4);
        assertEquals(1, due.selected().size());
        assertEquals(TriggerType.DEADLINE, due.selected().get(0).triggerType());

        FactionActorObservationSnapshot snapshot = new FactionActorObservationSnapshot(
                "faction.alpha", 50L, List.of(), List.of(), List.of(), List.of());
        FactionLivingActorKernel.ReviewResult result = FactionLivingActorKernel.review(
                loaded, snapshot, due.selected().get(0), 20L);

        assertEquals(1L, result.updatedState().completedReviewCount());
        assertEquals(70L, result.updatedState().nextReviewTick());
        assertTrue(FactionLivingActorScheduler.selectDue(
                List.of(result.updatedState()), 50L, 4).selected().isEmpty());
    }

    @Test
    void schedulerCapsWorkAndUsesStableFactionOrderingForEqualDeadlines() {
        List<FactionLivingActorState> states = List.of(
                FactionLivingActorState.initial("faction.charlie", 10L),
                FactionLivingActorState.initial("faction.alpha", 10L),
                FactionLivingActorState.initial("faction.bravo", 10L));

        FactionLivingActorScheduler.ScheduleBatch batch =
                FactionLivingActorScheduler.selectDue(states, 10L, 2);

        assertEquals(3, batch.eligibleCount());
        assertEquals(2, batch.selected().size());
        assertEquals(1, batch.deferredCount());
        assertEquals(
                List.of("faction.alpha", "faction.bravo"),
                batch.selected().stream().map(FactionLivingActorScheduler.ScheduledReview::factionContentId).toList());
    }

    @Test
    void schedulerKeepsOnlyStableTopKAcrossLargeDuePopulation() {
        ArrayList<FactionLivingActorState> states = new ArrayList<>();
        for (int index = 9_999; index >= 0; index--) {
            states.add(FactionLivingActorState.initial("faction.%05d".formatted(index), 10L));
        }

        FactionLivingActorScheduler.ScheduleBatch batch =
                FactionLivingActorScheduler.selectDue(states, 10L, 7);

        assertEquals(10_000, batch.eligibleCount());
        assertEquals(7, batch.selected().size());
        assertEquals(9_993, batch.deferredCount());
        assertEquals(
                List.of(
                        "faction.00000",
                        "faction.00001",
                        "faction.00002",
                        "faction.00003",
                        "faction.00004",
                        "faction.00005",
                        "faction.00006"),
                batch.selected().stream().map(FactionLivingActorScheduler.ScheduledReview::factionContentId).toList());
    }

    @Test
    void duplicateFactionLifecycleStateFailsClosedBeforeDoubleScheduling() {
        List<FactionLivingActorState> states = List.of(
                FactionLivingActorState.initial("faction.alpha", 10L),
                FactionLivingActorState.initial("faction.alpha", 20L));

        assertThrows(
                IllegalArgumentException.class,
                () -> FactionLivingActorScheduler.selectDue(states, 20L, 2));
    }

    @Test
    void persistedEventWakeupTriggersBeforeLaterPeriodicDeadline() {
        FactionLivingActorState state = FactionLivingActorState.initial("faction.alpha", 100L)
                .withWakeup(new EventWakeup(
                        WakeupReason.SHORTAGE_REPORTED,
                        "report.shortage.1",
                        20L,
                        25L));
        FactionLivingActorState loaded = FactionLivingActorStateCodec.decode(
                FactionLivingActorStateCodec.encode(List.of(state))).get(0);

        FactionLivingActorScheduler.ScheduleBatch batch =
                FactionLivingActorScheduler.selectDue(List.of(loaded), 25L, 1);

        assertEquals(1, batch.selected().size());
        assertEquals(TriggerType.EVENT_WAKEUP, batch.selected().get(0).triggerType());
        assertEquals(25L, batch.selected().get(0).dueTick());
        assertEquals(List.of(WakeupReason.SHORTAGE_REPORTED), batch.selected().get(0).wakeupReasons());
    }

    @Test
    void olderPeriodicDeadlineKeepsPriorityOverLaterEventWakeup() {
        FactionLivingActorState state = FactionLivingActorState.initial("faction.alpha", 10L)
                .withWakeup(new EventWakeup(
                        WakeupReason.PROJECT_COMPLETED,
                        "project.completed.1",
                        20L,
                        20L));

        FactionLivingActorScheduler.ScheduleBatch batch =
                FactionLivingActorScheduler.selectDue(List.of(state), 25L, 1);

        assertEquals(1, batch.selected().size());
        assertEquals(TriggerType.DEADLINE, batch.selected().get(0).triggerType());
        assertEquals(10L, batch.selected().get(0).dueTick());
    }
}
