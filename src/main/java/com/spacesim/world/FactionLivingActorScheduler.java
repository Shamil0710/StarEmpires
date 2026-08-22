package com.spacesim.world;

import com.spacesim.world.FactionLivingActorState.EventWakeup;
import com.spacesim.world.FactionLivingActorState.WakeupReason;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Bounded deterministic Stage-21A scheduler for autonomous faction reviews.
 *
 * <p>The scheduler may inspect lifecycle metadata for all supplied actors, but it authorizes at
 * most {@code maxReviews} expensive actor reviews in one invocation. Ordering depends only on
 * persisted deadlines/wakeups and stable faction identity.</p>
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

        /** Validates one scheduler authorization. */
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

        /** Validates one bounded scheduling report. */
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
        }
    }

    /**
     * Selects a deterministic bounded batch of due actor reviews.
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

        List<ScheduledReview> eligible = new ArrayList<>();
        for (FactionLivingActorState state : states) {
            FactionLivingActorState checked = Objects.requireNonNull(state, "Living actor state not set");
            List<EventWakeup> dueWakeups = checked.dueWakeups(nowTick);
            if (!dueWakeups.isEmpty()) {
                long earliest = dueWakeups.get(0).eligibleAtTick();
                List<WakeupReason> reasons = dueWakeups.stream().map(EventWakeup::reason).distinct().sorted().toList();
                eligible.add(new ScheduledReview(
                        checked.factionContentId(), TriggerType.EVENT_WAKEUP, earliest, reasons));
            } else if (checked.nextReviewTick() <= nowTick) {
                eligible.add(new ScheduledReview(
                        checked.factionContentId(), TriggerType.DEADLINE, checked.nextReviewTick(), List.of()));
            }
        }

        eligible.sort(Comparator.naturalOrder());
        int selectedCount = Math.min(maxReviews, eligible.size());
        List<ScheduledReview> selected = List.copyOf(eligible.subList(0, selectedCount));
        return new ScheduleBatch(nowTick, eligible.size(), selected, eligible.size() - selectedCount);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
