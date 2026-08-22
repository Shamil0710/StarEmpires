package com.spacesim.world;

import com.spacesim.world.FactionLivingActorState.EventWakeup;
import com.spacesim.world.FactionLivingActorState.WakeupReason;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded deterministic Stage-21A scheduler for autonomous faction reviews.
 *
 * <p>The scheduler performs one lightweight lifecycle scan over the supplied actors, but retains
 * only the best {@code maxReviews} due candidates. Expensive review work, candidate memory and
 * candidate ordering therefore remain bounded by the explicit review budget as faction counts grow.
 * Ordering depends only on persisted deadlines/wakeups and stable faction identity.</p>
 */
public final class FactionLivingActorScheduler {
    private FactionLivingActorScheduler() {
        throw new AssertionError("Utility class");
    }

    /** Cause that made one actor eligible for review. */
    public enum TriggerType {
        /** Ordinary medium-cadence deadline became due. */ DEADLINE,
        /** One or more persisted event wakeups became due. */ EVENT_WAKEUP
    }

    /**
     * One scheduler-authorized actor review.
     *
     * @param factionContentId stable faction identity
     * @param triggerType winning trigger family
     * @param dueTick earliest persisted due tick used for ordering
     * @param wakeupReasons due event reasons in stable enum order
     */
    public record ScheduledReview(
            String factionContentId,
            TriggerType triggerType,
            long dueTick,
            List<WakeupReason> wakeupReasons) implements Comparable<ScheduledReview> {

        /**
         * Validates one scheduler authorization.
         *
         * @param factionContentId stable faction identity
         * @param triggerType winning trigger family
         * @param dueTick earliest persisted due tick used for ordering
         * @param wakeupReasons due event reasons in stable enum order
         */
        public ScheduledReview {
            factionContentId = requireText(factionContentId, "Faction content ID");
            Objects.requireNonNull(triggerType, "Trigger type not set");
            if (dueTick < 0L) {
                throw new IllegalArgumentException("Due tick cannot be negative");
            }
            wakeupReasons = Objects.requireNonNull(wakeupReasons, "Wakeup reasons not set")
                    .stream()
                    .distinct()
                    .sorted()
                    .toList();
            if (triggerType == TriggerType.DEADLINE && !wakeupReasons.isEmpty()) {
                throw new IllegalArgumentException("Deadline review cannot carry event wakeup reasons");
            }
            if (triggerType == TriggerType.EVENT_WAKEUP && wakeupReasons.isEmpty()) {
                throw new IllegalArgumentException("Event review requires at least one wakeup reason");
            }
        }

        @Override
        public int compareTo(ScheduledReview other) {
            Objects.requireNonNull(other, "other");
            int due = Long.compare(dueTick, other.dueTick);
            if (due != 0) {
                return due;
            }
            int trigger = triggerType.compareTo(other.triggerType);
            return trigger != 0 ? trigger : factionContentId.compareTo(other.factionContentId);
        }
    }

    /**
     * Bounded scheduling report.
     *
     * @param nowTick scheduler observation tick
     * @param eligibleCount number of actors due before applying the work budget
     * @param selected reviews authorized this invocation
     * @param deferredCount due actors left for a later invocation
     */
    public record ScheduleBatch(
            long nowTick,
            int eligibleCount,
            List<ScheduledReview> selected,
            int deferredCount) {

        /**
         * Validates one bounded scheduling report.
         *
         * @param nowTick scheduler observation tick
         * @param eligibleCount number of actors due before applying the work budget
         * @param selected reviews authorized this invocation
         * @param deferredCount due actors left for a later invocation
         */
        public ScheduleBatch {
            if (nowTick < 0L) {
                throw new IllegalArgumentException("Scheduler tick cannot be negative");
            }
            if (eligibleCount < 0 || deferredCount < 0) {
                throw new IllegalArgumentException("Scheduler counts cannot be negative");
            }
            selected = List.copyOf(Objects.requireNonNull(selected, "Selected reviews not set"));
            if (eligibleCount != selected.size() + deferredCount) {
                throw new IllegalArgumentException("Eligible count must equal selected plus deferred reviews");
            }
            if (!selected.equals(selected.stream().sorted().toList())) {
                throw new IllegalArgumentException("Selected reviews must use canonical scheduler order");
            }
        }
    }

    /**
     * Selects a deterministic bounded batch of due actor reviews.
     *
     * <p>Selection uses a reverse-order top-K heap. The method scans lifecycle metadata once,
     * retains at most {@code maxReviews} candidates and finally sorts only those retained rows.
     * Duplicate stable faction identities fail closed rather than authorizing two reviews.</p>
     *
     * @param states persistent actor lifecycle states
     * @param nowTick authoritative review clock tick
     * @param maxReviews hard upper bound on expensive reviews authorized now
     * @return deterministic bounded schedule batch
     */
    public static ScheduleBatch selectDue(
            Collection<FactionLivingActorState> states,
            long nowTick,
            int maxReviews) {
        Objects.requireNonNull(states, "Living actor states not set");
        if (nowTick < 0L) {
            throw new IllegalArgumentException("Scheduler tick cannot be negative");
        }
        if (maxReviews <= 0) {
            throw new IllegalArgumentException("Review work budget must be positive");
        }

        PriorityQueue<ScheduledReview> selectedHeap = new PriorityQueue<>(
                maxReviews,
                Comparator.reverseOrder());
        Set<String> observedFactionIds = new HashSet<>();
        int eligibleCount = 0;
        for (FactionLivingActorState state : states) {
            FactionLivingActorState checked = Objects.requireNonNull(state, "Living actor state not set");
            if (!observedFactionIds.add(checked.factionContentId())) {
                throw new IllegalArgumentException(
                        "Duplicate living actor state: " + checked.factionContentId());
            }
            ScheduledReview candidate = dueCandidate(checked, nowTick);
            if (candidate == null) {
                continue;
            }
            eligibleCount = Math.addExact(eligibleCount, 1);
            if (selectedHeap.size() < maxReviews) {
                selectedHeap.add(candidate);
                continue;
            }
            ScheduledReview worstSelected = selectedHeap.peek();
            if (worstSelected != null && candidate.compareTo(worstSelected) < 0) {
                selectedHeap.remove();
                selectedHeap.add(candidate);
            }
        }

        List<ScheduledReview> selected = selectedHeap.stream().sorted().toList();
        return new ScheduleBatch(
                nowTick,
                eligibleCount,
                selected,
                eligibleCount - selected.size());
    }

    private static ScheduledReview dueCandidate(FactionLivingActorState state, long nowTick) {
        List<EventWakeup> dueWakeups = state.dueWakeups(nowTick);
        if (!dueWakeups.isEmpty()) {
            long earliest = dueWakeups.get(0).eligibleAtTick();
            List<WakeupReason> reasons = dueWakeups.stream()
                    .map(EventWakeup::reason)
                    .distinct()
                    .sorted()
                    .toList();
            return new ScheduledReview(
                    state.factionContentId(),
                    TriggerType.EVENT_WAKEUP,
                    earliest,
                    reasons);
        }
        if (state.nextReviewTick() <= nowTick) {
            return new ScheduledReview(
                    state.factionContentId(),
                    TriggerType.DEADLINE,
                    state.nextReviewTick(),
                    List.of());
        }
        return null;
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
