package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * Persistent Stage-21B state of one explainable strategic goal.
 *
 * <p>The goal is intent metadata only. Budget values are normalized planning envelopes and do not
 * duplicate treasury, logistics, construction or fleet authority.</p>
 *
 * @param goalId persistent goal identity
 * @param factionContentId owning faction content identity
 * @param type strategic goal family
 * @param targetId stable target identity
 * @param sourceEvidence persisted actor-bounded source evidence
 * @param urgencyBasisPoints urgency in {@code [0,10000]}
 * @param strategicValueBasisPoints strategic value in {@code [0,10000]}
 * @param feasibilityBasisPoints feasibility in {@code [0,10000]}
 * @param doctrinePreferenceBasisPoints caller-owned doctrine preference in {@code [0,10000]}
 * @param requestedBudget requested multidimensional planning envelope
 * @param allocatedBudget currently allocated multidimensional planning envelope
 * @param blockers current explainable blockers
 * @param lifecycle current goal lifecycle
 * @param createdAtTick creation tick
 * @param updatedAtTick last review/update tick
 * @param nextReviewTick next scheduled strategic review for open goals, otherwise zero
 * @param expiresAtTick terminal expiry tick, or {@code -1} for no automatic expiry
 * @param cooldownUntilTick earliest tick a cancelled target may be reconsidered, or zero
 * @param cancellationCost visible switching cost in normalized planning-envelope units
 * @param outcomeSignal last authoritative terminal outcome signal
 */
public record StrategicGoalState(
        String goalId,
        String factionContentId,
        StrategicGoalType type,
        String targetId,
        StrategicGoalEvidence sourceEvidence,
        int urgencyBasisPoints,
        int strategicValueBasisPoints,
        int feasibilityBasisPoints,
        int doctrinePreferenceBasisPoints,
        StrategicPlanningEnvelope requestedBudget,
        StrategicPlanningEnvelope allocatedBudget,
        List<StrategicGoalBlocker> blockers,
        Lifecycle lifecycle,
        long createdAtTick,
        long updatedAtTick,
        long nextReviewTick,
        long expiresAtTick,
        long cooldownUntilTick,
        StrategicPlanningEnvelope cancellationCost,
        StrategicGoalOutcomeSignal outcomeSignal) implements Comparable<StrategicGoalState> {

    /** Persistent Stage-21B lifecycle for accepted goals. */
    public enum Lifecycle {
        /** Goal is accepted, feasible and currently funded by the planning envelope. */ ACTIVE,
        /** Goal remains accepted but cannot currently progress for explainable reasons. */ STALLED,
        /** An authoritative execution layer reported that the success condition was met. */ SUCCEEDED,
        /** Goal was abandoned or an authoritative execution layer reported terminal failure. */ CANCELLED,
        /** The accepted goal reached its explicit expiry horizon. */ EXPIRED
    }

    /**
     * Validates one immutable persistent goal state.
     *
     * @param goalId persistent goal identity
     * @param factionContentId owning faction content identity
     * @param type strategic goal family
     * @param targetId stable target identity
     * @param sourceEvidence persisted actor-bounded source evidence
     * @param urgencyBasisPoints urgency in {@code [0,10000]}
     * @param strategicValueBasisPoints strategic value in {@code [0,10000]}
     * @param feasibilityBasisPoints feasibility in {@code [0,10000]}
     * @param doctrinePreferenceBasisPoints caller-owned doctrine preference in {@code [0,10000]}
     * @param requestedBudget requested multidimensional planning envelope
     * @param allocatedBudget currently allocated multidimensional planning envelope
     * @param blockers current explainable blockers
     * @param lifecycle current goal lifecycle
     * @param createdAtTick creation tick
     * @param updatedAtTick last review/update tick
     * @param nextReviewTick next scheduled strategic review for open goals, otherwise zero
     * @param expiresAtTick terminal expiry tick, or {@code -1} for no automatic expiry
     * @param cooldownUntilTick earliest tick a cancelled target may be reconsidered, or zero
     * @param cancellationCost visible switching cost in normalized planning-envelope units
     * @param outcomeSignal last authoritative terminal outcome signal
     */
    public StrategicGoalState {
        goalId = requireText(goalId, "Strategic goal ID");
        factionContentId = requireText(factionContentId, "Strategic goal faction ID");
        Objects.requireNonNull(type, "Strategic goal type not set");
        targetId = requireText(targetId, "Strategic goal target ID");
        Objects.requireNonNull(sourceEvidence, "Strategic goal source evidence not set");
        Objects.requireNonNull(requestedBudget, "Strategic goal requested budget not set");
        Objects.requireNonNull(allocatedBudget, "Strategic goal allocated budget not set");
        blockers = Objects.requireNonNull(blockers, "Strategic goal blockers not set").stream()
                .map(blocker -> Objects.requireNonNull(blocker, "Strategic goal blocker not set"))
                .sorted()
                .distinct()
                .toList();
        Objects.requireNonNull(lifecycle, "Strategic goal lifecycle not set");
        Objects.requireNonNull(cancellationCost, "Strategic goal cancellation cost not set");
        Objects.requireNonNull(outcomeSignal, "Strategic goal outcome signal not set");
        if (!targetId.equals(sourceEvidence.targetId())) {
            throw new IllegalArgumentException("Strategic goal target must match source evidence target");
        }
        if (!type.supports(sourceEvidence.kind())) {
            throw new IllegalArgumentException("Strategic goal type is incompatible with persisted evidence");
        }
        requireBasisPoints(urgencyBasisPoints, "Strategic goal urgency");
        requireBasisPoints(strategicValueBasisPoints, "Strategic goal value");
        requireBasisPoints(feasibilityBasisPoints, "Strategic goal feasibility");
        requireBasisPoints(doctrinePreferenceBasisPoints, "Strategic doctrine preference");
        if (!allocatedBudget.fitsWithin(requestedBudget)) {
            throw new IllegalArgumentException("Strategic goal allocation cannot exceed request");
        }
        requireNonNegative(createdAtTick, "Strategic goal creation tick");
        requireNonNegative(updatedAtTick, "Strategic goal update tick");
        if (updatedAtTick < createdAtTick) {
            throw new IllegalArgumentException("Strategic goal update cannot precede creation");
        }
        if (nextReviewTick < 0L) {
            throw new IllegalArgumentException("Strategic goal next review tick cannot be negative");
        }
        if (expiresAtTick < -1L) {
            throw new IllegalArgumentException("Strategic goal expiry must be -1 or non-negative");
        }
        requireNonNegative(cooldownUntilTick, "Strategic goal cooldown horizon");

        boolean open = lifecycle == Lifecycle.ACTIVE || lifecycle == Lifecycle.STALLED;
        if (open) {
            if (nextReviewTick < updatedAtTick) {
                throw new IllegalArgumentException("Open strategic goal review cannot precede last update");
            }
            if (cooldownUntilTick != 0L || !cancellationCost.isZero()) {
                throw new IllegalArgumentException("Open strategic goal cannot carry cooldown or cancellation cost");
            }
            if (outcomeSignal != StrategicGoalOutcomeSignal.NONE) {
                throw new IllegalArgumentException("Open strategic goal cannot carry a terminal outcome");
            }
        } else if (nextReviewTick != 0L || !allocatedBudget.isZero()) {
            throw new IllegalArgumentException("Terminal strategic goal cannot retain review or allocation");
        }

        if (lifecycle == Lifecycle.ACTIVE && !blockers.isEmpty()) {
            throw new IllegalArgumentException("Active strategic goal cannot carry blockers");
        }
        if (lifecycle == Lifecycle.STALLED) {
            if (blockers.isEmpty()) {
                throw new IllegalArgumentException("Stalled strategic goal requires at least one blocker");
            }
            if (!allocatedBudget.isZero()) {
                throw new IllegalArgumentException("Stalled strategic goal cannot retain planning allocation");
            }
        }
        if (lifecycle == Lifecycle.SUCCEEDED) {
            if (outcomeSignal != StrategicGoalOutcomeSignal.SUCCEEDED || !blockers.isEmpty()
                    || cooldownUntilTick != 0L || !cancellationCost.isZero()) {
                throw new IllegalArgumentException("Succeeded strategic goal carries invalid terminal metadata");
            }
        }
        if (lifecycle == Lifecycle.CANCELLED) {
            if (cooldownUntilTick < updatedAtTick) {
                throw new IllegalArgumentException("Cancelled strategic goal cooldown cannot precede cancellation");
            }
        } else if (lifecycle == Lifecycle.EXPIRED) {
            if (outcomeSignal != StrategicGoalOutcomeSignal.NONE || !blockers.isEmpty()
                    || cooldownUntilTick != 0L || !cancellationCost.isZero()) {
                throw new IllegalArgumentException("Expired strategic goal carries invalid terminal metadata");
            }
        }
    }

    /**
     * Compatibility constructor using neutral strategic value and doctrine preference.
     *
     * @param goalId persistent goal identity
     * @param factionContentId owning faction content identity
     * @param type strategic goal family
     * @param targetId stable target identity
     * @param sourceEvidence persisted actor-bounded source evidence
     * @param urgencyBasisPoints urgency in {@code [0,10000]}
     * @param feasibilityBasisPoints feasibility in {@code [0,10000]}
     * @param requestedBudget requested multidimensional planning envelope
     * @param allocatedBudget currently allocated multidimensional planning envelope
     * @param blockers current explainable blockers
     * @param lifecycle current goal lifecycle
     * @param createdAtTick creation tick
     * @param updatedAtTick last review/update tick
     * @param nextReviewTick next scheduled strategic review for open goals, otherwise zero
     * @param expiresAtTick terminal expiry tick, or {@code -1}
     * @param cooldownUntilTick earliest reconsideration tick, or zero
     * @param cancellationCost visible switching cost
     * @param outcomeSignal last authoritative terminal outcome signal
     */
    public StrategicGoalState(
            String goalId,
            String factionContentId,
            StrategicGoalType type,
            String targetId,
            StrategicGoalEvidence sourceEvidence,
            int urgencyBasisPoints,
            int feasibilityBasisPoints,
            StrategicPlanningEnvelope requestedBudget,
            StrategicPlanningEnvelope allocatedBudget,
            List<StrategicGoalBlocker> blockers,
            Lifecycle lifecycle,
            long createdAtTick,
            long updatedAtTick,
            long nextReviewTick,
            long expiresAtTick,
            long cooldownUntilTick,
            StrategicPlanningEnvelope cancellationCost,
            StrategicGoalOutcomeSignal outcomeSignal) {
        this(goalId, factionContentId, type, targetId, sourceEvidence, urgencyBasisPoints, 10_000,
                feasibilityBasisPoints, 10_000, requestedBudget, allocatedBudget, blockers, lifecycle,
                createdAtTick, updatedAtTick, nextReviewTick, expiresAtTick, cooldownUntilTick,
                cancellationCost, outcomeSignal);
    }

    /**
     * Stable identity of a target-specific strategic intent, independent of the persistent goal ID.
     *
     * @return canonical goal type/target key
     */
    public String intentKey() {
        return type.wireId() + "\u0000" + targetId;
    }

    /**
     * Reports whether this goal is still an accepted open intent.
     *
     * @return true for active or stalled lifecycle states
     */
    public boolean isOpen() {
        return lifecycle == Lifecycle.ACTIVE || lifecycle == Lifecycle.STALLED;
    }

    /**
     * Returns a refreshed active state while preserving persistent identity and creation tick.
     *
     * @param candidate current candidate for the same type/target identity
     * @param reviewTick authoritative review tick
     * @return refreshed immutable active goal state
     */
    public StrategicGoalState refreshActive(StrategicGoalCandidate candidate, long reviewTick) {
        StrategicGoalCandidate checked = requireSameIntent(candidate);
        return fromCandidate(checked, checked.requestedBudget(), List.of(), Lifecycle.ACTIVE, reviewTick,
                Math.addExact(reviewTick, checked.reviewCadenceTicks()), 0L, StrategicPlanningEnvelope.ZERO,
                StrategicGoalOutcomeSignal.NONE);
    }

    /**
     * Returns a stalled state while preserving persistent identity and creation tick.
     *
     * @param candidate current candidate for the same type/target identity
     * @param currentBlockers capacity and/or external blockers preventing progress
     * @param reviewTick authoritative review tick
     * @return stalled immutable goal state
     */
    public StrategicGoalState stall(
            StrategicGoalCandidate candidate,
            List<StrategicGoalBlocker> currentBlockers,
            long reviewTick) {
        StrategicGoalCandidate checked = requireSameIntent(candidate);
        return fromCandidate(checked, StrategicPlanningEnvelope.ZERO, currentBlockers, Lifecycle.STALLED, reviewTick,
                Math.addExact(reviewTick, checked.reviewCadenceTicks()), 0L, StrategicPlanningEnvelope.ZERO,
                StrategicGoalOutcomeSignal.NONE);
    }

    /**
     * Returns a successful terminal state from an authoritative outcome signal.
     *
     * @param candidate current candidate for the same type/target identity
     * @param reviewTick authoritative review tick
     * @return succeeded immutable goal state
     */
    public StrategicGoalState succeed(StrategicGoalCandidate candidate, long reviewTick) {
        StrategicGoalCandidate checked = requireSameIntent(candidate);
        return fromCandidate(checked, StrategicPlanningEnvelope.ZERO, List.of(), Lifecycle.SUCCEEDED, reviewTick,
                0L, 0L, StrategicPlanningEnvelope.ZERO, StrategicGoalOutcomeSignal.SUCCEEDED);
    }

    /**
     * Returns a cancelled state with explicit cooldown and switching cost.
     *
     * @param candidate latest candidate for the same type/target identity, or null when displaced
     * @param reviewTick authoritative cancellation review tick
     * @param cooldownUntil earliest tick at which this intent may be reconsidered
     * @param cost visible strategic switching cost
     * @param outcome terminal execution outcome, either NONE or FAILED
     * @return cancelled immutable goal state
     */
    public StrategicGoalState cancel(
            StrategicGoalCandidate candidate,
            long reviewTick,
            long cooldownUntil,
            StrategicPlanningEnvelope cost,
            StrategicGoalOutcomeSignal outcome) {
        if (outcome == StrategicGoalOutcomeSignal.SUCCEEDED) {
            throw new IllegalArgumentException("Succeeded outcome cannot cancel a strategic goal");
        }
        if (candidate == null) {
            return new StrategicGoalState(
                    goalId, factionContentId, type, targetId, sourceEvidence,
                    urgencyBasisPoints, strategicValueBasisPoints, feasibilityBasisPoints,
                    doctrinePreferenceBasisPoints, requestedBudget, StrategicPlanningEnvelope.ZERO,
                    List.of(), Lifecycle.CANCELLED, createdAtTick, reviewTick, 0L, expiresAtTick,
                    cooldownUntil, Objects.requireNonNull(cost, "Strategic cancellation cost not set"), outcome);
        }
        StrategicGoalCandidate checked = requireSameIntent(candidate);
        return fromCandidate(checked, StrategicPlanningEnvelope.ZERO, List.of(), Lifecycle.CANCELLED, reviewTick,
                0L, cooldownUntil, Objects.requireNonNull(cost, "Strategic cancellation cost not set"), outcome);
    }

    /**
     * Returns an expired terminal state while preserving the latest candidate evidence.
     *
     * @param candidate latest candidate for the same type/target identity
     * @param reviewTick authoritative expiry review tick
     * @return expired immutable goal state
     */
    public StrategicGoalState expire(StrategicGoalCandidate candidate, long reviewTick) {
        StrategicGoalCandidate checked = requireSameIntent(candidate);
        return fromCandidate(checked, StrategicPlanningEnvelope.ZERO, List.of(), Lifecycle.EXPIRED, reviewTick,
                0L, 0L, StrategicPlanningEnvelope.ZERO, StrategicGoalOutcomeSignal.NONE);
    }

    @Override
    public int compareTo(StrategicGoalState other) {
        Objects.requireNonNull(other, "other");
        return goalId.compareTo(other.goalId);
    }

    private StrategicGoalCandidate requireSameIntent(StrategicGoalCandidate candidate) {
        StrategicGoalCandidate checked = Objects.requireNonNull(candidate, "Strategic goal candidate not set");
        if (type != checked.type() || !targetId.equals(checked.targetId())) {
            throw new IllegalArgumentException("Cannot transition strategic goal with different intent identity");
        }
        return checked;
    }

    private StrategicGoalState fromCandidate(
            StrategicGoalCandidate candidate,
            StrategicPlanningEnvelope allocation,
            List<StrategicGoalBlocker> currentBlockers,
            Lifecycle nextLifecycle,
            long reviewTick,
            long nextReview,
            long cooldownUntil,
            StrategicPlanningEnvelope cost,
            StrategicGoalOutcomeSignal outcome) {
        return new StrategicGoalState(
                goalId, factionContentId, type, targetId, candidate.sourceEvidence(),
                candidate.urgencyBasisPoints(), candidate.strategicValueBasisPoints(),
                candidate.feasibilityBasisPoints(), candidate.doctrinePreferenceBasisPoints(),
                candidate.requestedBudget(), allocation, currentBlockers, nextLifecycle, createdAtTick,
                reviewTick, nextReview, candidate.expiresAtTick(), cooldownUntil, cost, outcome);
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in [0,10000]");
        }
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
