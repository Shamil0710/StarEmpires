package com.spacesim.world;

import com.spacesim.world.FactionLivingActorScheduler.ScheduledReview;
import com.spacesim.world.FactionLivingActorScheduler.TriggerType;
import com.spacesim.world.FactionLivingActorState.EventWakeup;
import com.spacesim.world.FactionLivingActorState.WakeupReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionLivingActorKernelTest {

    @Test
    void eventWakeupIsConsumedByExactlyOneCompletedReview() {
        FactionLivingActorState state = FactionLivingActorState.initial("faction.alpha", 100L)
                .withWakeup(new EventWakeup(
                        WakeupReason.ATTACK_OBSERVED,
                        "contact.attack.17",
                        25L,
                        25L));
        ScheduledReview authorization = FactionLivingActorScheduler
                .selectDue(List.of(state), 25L, 1)
                .selected()
                .get(0);
        FactionActorObservationSnapshot snapshot = new FactionActorObservationSnapshot(
                "faction.alpha", 25L, List.of(), List.of(), List.of(), List.of());

        FactionLivingActorKernel.ReviewResult result = FactionLivingActorKernel.review(
                state,
                snapshot,
                authorization,
                30L);

        assertTrue(result.updatedState().pendingWakeups().isEmpty());
        assertEquals(1L, result.updatedState().completedReviewCount());
        assertEquals(55L, result.updatedState().nextReviewTick());
        assertTrue(FactionLivingActorScheduler
                .selectDue(List.of(result.updatedState()), 25L, 1)
                .selected()
                .isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> FactionLivingActorKernel.review(
                        result.updatedState(),
                        snapshot,
                        authorization,
                        30L));
    }

    @Test
    void forgedDeadlineAuthorizationFailsClosed() {
        FactionLivingActorState state = FactionLivingActorState.initial("faction.alpha", 50L);
        FactionActorObservationSnapshot snapshot = new FactionActorObservationSnapshot(
                "faction.alpha", 50L, List.of(), List.of(), List.of(), List.of());
        ScheduledReview forged = new ScheduledReview(
                "faction.alpha",
                TriggerType.DEADLINE,
                40L,
                List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> FactionLivingActorKernel.review(state, snapshot, forged, 20L));
    }

    @Test
    void forgedWakeupReasonsFailClosed() {
        FactionLivingActorState state = FactionLivingActorState.initial("faction.alpha", 100L)
                .withWakeup(new EventWakeup(
                        WakeupReason.LOSS_REPORTED,
                        "loss.1",
                        30L,
                        30L));
        FactionActorObservationSnapshot snapshot = new FactionActorObservationSnapshot(
                "faction.alpha", 30L, List.of(), List.of(), List.of(), List.of());
        ScheduledReview forged = new ScheduledReview(
                "faction.alpha",
                TriggerType.EVENT_WAKEUP,
                30L,
                List.of(WakeupReason.PROJECT_COMPLETED));

        assertThrows(
                IllegalArgumentException.class,
                () -> FactionLivingActorKernel.review(state, snapshot, forged, 20L));
    }

    @Test
    void reviewIdentityMustMatchAcrossStateSnapshotAndAuthorization() {
        FactionLivingActorState state = FactionLivingActorState.initial("faction.alpha", 10L);
        FactionActorObservationSnapshot wrongSnapshot = new FactionActorObservationSnapshot(
                "faction.bravo", 10L, List.of(), List.of(), List.of(), List.of());
        ScheduledReview authorization = new ScheduledReview(
                "faction.alpha",
                TriggerType.DEADLINE,
                10L,
                List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> FactionLivingActorKernel.review(state, wrongSnapshot, authorization, 20L));
    }
}
