package com.spacesim.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.TreeSet;

/**
 * Persistent Stage-21A lifecycle state for one autonomous faction actor.
 *
 * <p>The record contains only scheduling and commitment metadata. It does not own diplomacy,
 * treasury, cargo, fleets, territory, or any other upstream world authority.</p>
 *
 * @param factionContentId stable faction content identity
 * @param nextReviewTick next ordinary strategic review deadline
 * @param commitmentUntilTick earliest tick before which a later goal layer should avoid churn
 * @param lastReviewTick last completed review tick, or {@code -1} before the first review
 * @param completedReviewCount number of completed reviews
 * @param pendingWakeups persistent event wakeups not yet consumed by a completed review
 */
public record FactionLivingActorState(
        String factionContentId,
        long nextReviewTick,
        long commitmentUntilTick,
        long lastReviewTick,
        long completedReviewCount,
        List<EventWakeup> pendingWakeups)
        implements Comparable<FactionLivingActorState> {

    /** Event families allowed to wake the bounded strategic review loop. */
    public enum WakeupReason {
        /** A relevant owned or reported fleet arrival completed. */ ARRIVAL,
        /** A hostile attack was observed through an allowed channel. */ ATTACK_OBSERVED,
        /** A physical loss report reached the actor. */ LOSS_REPORTED,
        /** A persisted treaty or diplomatic commitment changed. */ TREATY_CHANGED,
        /** An economic shortage report reached the actor. */ SHORTAGE_REPORTED,
        /** A committed project completed. */ PROJECT_COMPLETED,
        /** Material actor-bounded evidence changed enough to justify re-review. */ MATERIAL_OBSERVATION_CHANGED
    }

    /**
     * One persistent event wakeup.
     *
     * @param reason causal event family
     * @param sourceId stable report/event identity used for deduplication
     * @param observedAtTick tick when the actor received the event
     * @param eligibleAtTick earliest tick at which the event may trigger a review
     */
    public record EventWakeup(
            WakeupReason reason,
            String sourceId,
            long observedAtTick,
            long eligibleAtTick) implements Comparable<EventWakeup> {

        /**
         * Validates and canonicalizes one event wakeup.
         *
         * @param reason causal event family
         * @param sourceId stable report/event identity used for deduplication
         * @param observedAtTick tick when the actor received the event
         * @param eligibleAtTick earliest tick at which the event may trigger a review
         */
        public EventWakeup {
            Objects.requireNonNull(reason, "Wakeup reason not set");
            sourceId = requireText(sourceId, "Wakeup source ID");
            requireNonNegative(observedAtTick, "Wakeup observation tick");
            requireNonNegative(eligibleAtTick, "Wakeup eligibility tick");
            if (eligibleAtTick < observedAtTick) {
                throw new IllegalArgumentException("Wakeup eligibility cannot precede observation");
            }
        }

        @Override
        public int compareTo(EventWakeup other) {
            Objects.requireNonNull(other, "other");
            int eligible = Long.compare(eligibleAtTick, other.eligibleAtTick);
            if (eligible != 0) {
                return eligible;
            }
            int observed = Long.compare(observedAtTick, other.observedAtTick);
            if (observed != 0) {
                return observed;
            }
            int reasonOrder = reason.compareTo(other.reason);
            return reasonOrder != 0 ? reasonOrder : sourceId.compareTo(other.sourceId);
        }
    }

    /**
     * Validates state and normalizes wakeups into stable deterministic order.
     *
     * @param factionContentId stable faction content identity
     * @param nextReviewTick next ordinary strategic review deadline
     * @param commitmentUntilTick persistent anti-churn commitment horizon
     * @param lastReviewTick last completed review tick, or {@code -1} before the first review
     * @param completedReviewCount number of completed reviews
     * @param pendingWakeups persistent event wakeups not yet consumed by a completed review
     */
    public FactionLivingActorState {
        factionContentId = requireText(factionContentId, "Faction content ID");
        requireNonNegative(nextReviewTick, "Next review tick");
        requireNonNegative(commitmentUntilTick, "Commitment horizon");
        if (lastReviewTick < -1L) {
            throw new IllegalArgumentException("Last review tick cannot be less than -1");
        }
        requireNonNegative(completedReviewCount, "Completed review count");
        Objects.requireNonNull(pendingWakeups, "Pending wakeups not set");
        TreeSet<EventWakeup> sortedWakeups = new TreeSet<>();
        HashSet<String> wakeupSources = new HashSet<>();
        for (EventWakeup wakeup : pendingWakeups) {
            EventWakeup checked = Objects.requireNonNull(wakeup, "Pending wakeup not set");
            if (!wakeupSources.add(checked.sourceId())) {
                throw new IllegalArgumentException("Duplicate wakeup source ID: " + checked.sourceId());
            }
            sortedWakeups.add(checked);
        }
        pendingWakeups = List.copyOf(sortedWakeups);
        if (completedReviewCount == 0L && lastReviewTick != -1L) {
            throw new IllegalArgumentException("Never-reviewed actor must use lastReviewTick=-1");
        }
        if (completedReviewCount > 0L && lastReviewTick < 0L) {
            throw new IllegalArgumentException("Reviewed actor must retain a last review tick");
        }
    }

    /**
     * Creates initial lifecycle state for an autonomous faction.
     *
     * @param factionContentId stable faction identity
     * @param firstReviewTick first ordinary review deadline
     * @return initial persistent actor state
     */
    public static FactionLivingActorState initial(String factionContentId, long firstReviewTick) {
        return new FactionLivingActorState(factionContentId, firstReviewTick, 0L, -1L, 0L, List.of());
    }

    /**
     * Adds one deduplicated persistent event wakeup.
     *
     * @param wakeup event wakeup received by this actor
     * @return new immutable state
     */
    public FactionLivingActorState withWakeup(EventWakeup wakeup) {
        ArrayList<EventWakeup> next = new ArrayList<>(pendingWakeups);
        next.add(Objects.requireNonNull(wakeup, "Wakeup not set"));
        return new FactionLivingActorState(
                factionContentId,
                nextReviewTick,
                commitmentUntilTick,
                lastReviewTick,
                completedReviewCount,
                next);
    }

    /**
     * Replaces the commitment horizon without mutating any strategic-world authority.
     *
     * @param newCommitmentUntilTick new persistent commitment horizon
     * @return updated immutable lifecycle state
     */
    public FactionLivingActorState withCommitmentUntilTick(long newCommitmentUntilTick) {
        requireNonNegative(newCommitmentUntilTick, "Commitment horizon");
        return new FactionLivingActorState(
                factionContentId,
                nextReviewTick,
                newCommitmentUntilTick,
                lastReviewTick,
                completedReviewCount,
                pendingWakeups);
    }

    /**
     * Returns the earliest currently due event wakeup.
     *
     * @param nowTick authoritative review clock tick
     * @return earliest due wakeup, or an empty list when none is due
     */
    public List<EventWakeup> dueWakeups(long nowTick) {
        requireNonNegative(nowTick, "Current tick");
        return pendingWakeups.stream()
                .filter(wakeup -> wakeup.eligibleAtTick() <= nowTick)
                .toList();
    }

    /**
     * Completes exactly one review and consumes only wakeups eligible by that review tick.
     *
     * @param reviewTick completed review tick
     * @param cadenceTicks positive delay until the next ordinary review
     * @return new immutable lifecycle state
     */
    public FactionLivingActorState afterReview(long reviewTick, long cadenceTicks) {
        requireNonNegative(reviewTick, "Review tick");
        if (cadenceTicks <= 0L) {
            throw new IllegalArgumentException("Review cadence must be positive");
        }
        if (lastReviewTick >= 0L && reviewTick <= lastReviewTick) {
            throw new IllegalArgumentException("Review tick must advance beyond the previous review");
        }
        long nextDeadline = Math.addExact(reviewTick, cadenceTicks);
        List<EventWakeup> retained = pendingWakeups.stream()
                .filter(wakeup -> wakeup.eligibleAtTick() > reviewTick)
                .toList();
        return new FactionLivingActorState(
                factionContentId,
                nextDeadline,
                commitmentUntilTick,
                reviewTick,
                Math.addExact(completedReviewCount, 1L),
                retained);
    }

    @Override
    public int compareTo(FactionLivingActorState other) {
        Objects.requireNonNull(other, "other");
        return factionContentId.compareTo(other.factionContentId);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }
}
