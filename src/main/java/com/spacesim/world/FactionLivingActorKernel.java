package com.spacesim.world;

import com.spacesim.world.FactionInterestResolver.DecisionTrace;
import com.spacesim.world.FactionLivingActorScheduler.ScheduledReview;

import java.util.Objects;

/**
 * Pure Stage-21A autonomous-faction review kernel.
 *
 * <p>The kernel intentionally has no {@link WorldSimulation} reference and no command executor. It
 * can therefore analyze actor-bounded observations and advance only lifecycle metadata; money,
 * cargo, ships, territory and relations remain unreachable from this layer.</p>
 */
public final class FactionLivingActorKernel {
    private FactionLivingActorKernel() {
        throw new AssertionError("Utility class");
    }

    /**
     * Result of exactly one bounded actor review.
     *
     * @param updatedState lifecycle state after consuming due wakeups and advancing the deadline
     * @param trace deterministic interest evidence and explanation trace
     * @param scheduledReview scheduler authorization that caused the review
     */
    public record ReviewResult(
            FactionLivingActorState updatedState,
            DecisionTrace trace,
            ScheduledReview scheduledReview) {

        /**
         * Validates identity alignment across the pure review result.
         *
         * @param updatedState lifecycle state after consuming due wakeups and advancing the deadline
         * @param trace deterministic interest evidence and explanation trace
         * @param scheduledReview scheduler authorization that caused the review
         */
        public ReviewResult {
            Objects.requireNonNull(updatedState, "Updated actor state not set");
            Objects.requireNonNull(trace, "Decision trace not set");
            Objects.requireNonNull(scheduledReview, "Scheduled review not set");
            String factionId = updatedState.factionContentId();
            if (!factionId.equals(trace.factionContentId())
                    || !factionId.equals(scheduledReview.factionContentId())) {
                throw new IllegalArgumentException("Living-actor review identities must match");
            }
        }
    }

    /**
     * Executes one scheduler-authorized pure review.
     *
     * @param state persistent lifecycle state for the faction
     * @param snapshot actor-bounded observation snapshot for the same faction and tick
     * @param scheduledReview scheduler authorization selected for this faction
     * @param cadenceTicks positive delay until the next ordinary review
     * @return new lifecycle state plus deterministic decision trace
     */
    public static ReviewResult review(
            FactionLivingActorState state,
            FactionActorObservationSnapshot snapshot,
            ScheduledReview scheduledReview,
            long cadenceTicks) {
        FactionLivingActorState checkedState = Objects.requireNonNull(state, "Living actor state not set");
        FactionActorObservationSnapshot checkedSnapshot = Objects.requireNonNull(
                snapshot, "Observation snapshot not set");
        ScheduledReview checkedReview = Objects.requireNonNull(scheduledReview, "Scheduled review not set");
        if (cadenceTicks <= 0L) {
            throw new IllegalArgumentException("Review cadence must be positive");
        }
        String factionId = checkedState.factionContentId();
        if (!factionId.equals(checkedSnapshot.factionContentId())
                || !factionId.equals(checkedReview.factionContentId())) {
            throw new IllegalArgumentException("Living actor state, snapshot and schedule identities must match");
        }
        if (checkedSnapshot.observedAtTick() < checkedReview.dueTick()) {
            throw new IllegalArgumentException("Observation snapshot cannot precede the scheduled due tick");
        }
        if (checkedSnapshot.observedAtTick() <= checkedState.lastReviewTick()) {
            throw new IllegalArgumentException("Review tick must advance beyond the previous completed review");
        }
        validateAuthorization(checkedState, checkedReview, checkedSnapshot.observedAtTick());

        DecisionTrace trace = FactionInterestResolver.resolve(checkedSnapshot);
        FactionLivingActorState updated = checkedState.afterReview(
                checkedSnapshot.observedAtTick(), cadenceTicks);
        return new ReviewResult(updated, trace, checkedReview);
    }

    private static void validateAuthorization(
            FactionLivingActorState state,
            ScheduledReview review,
            long nowTick) {
        switch (review.triggerType()) {
            case DEADLINE -> {
                if (state.nextReviewTick() != review.dueTick() || state.nextReviewTick() > nowTick) {
                    throw new IllegalArgumentException("Deadline schedule does not match actor lifecycle state");
                }
            }
            case EVENT_WAKEUP -> {
                var due = state.dueWakeups(nowTick);
                if (due.isEmpty() || due.get(0).eligibleAtTick() != review.dueTick()) {
                    throw new IllegalArgumentException("Event schedule does not match pending actor wakeups");
                }
                var reasons = due.stream()
                        .map(FactionLivingActorState.EventWakeup::reason)
                        .distinct()
                        .sorted()
                        .toList();
                if (!reasons.equals(review.wakeupReasons())) {
                    throw new IllegalArgumentException("Event schedule reasons do not match pending actor wakeups");
                }
            }
        }
    }
}
