package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * Persistent Stage-21B state of one explainable strategic goal.
 *
 * <p>The goal is intent metadata only. Budget values are normalized planning envelopes and do not
 * duplicate treasury, logistics, construction or fleet authority. Success/failure conditions are
 * declarative adapter contracts and are never evaluated against hidden world truth here.</p>
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
 * @param costCeiling maximum accepted normalized planning cost
 * @param successConditions declarative external-authority success conditions
 * @param failureConditions declarative external-authority failure conditions
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
        StrategicPlanningEnvelope costCeiling,
        List<StrategicGoalCondition> successConditions,
        List<StrategicGoalCondition> failureConditions,
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
     * @param costCeiling maximum accepted normalized planning cost
     * @param successConditions declarative external-authority success conditions
     * @param failureConditions declarative external-authority failure conditions
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
        Objects.requireNonNull(costCeiling, "Strategic goal cost ceiling not set");
        successConditions = normalizeConditions(successConditions, "Strategic goal success conditions");
        failureConditions = normalizeConditions(failureConditions, "Strategic goal failure conditions");
        Objects.requireNonNull(allocatedBudget, "Strategic goal allocated budget not set");
        blockers = Objects.requireNonNull(blockers, "Strategic goal blockers not set").stream()
                .map(blocker -> Objects.requireNonNull(blocker, "Strategic goal blocker not set"))
                .sorted().distinct().toList();
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
     * Compatibility constructor with scoring metadata and default condition/cost contract.
     *
     * @param goalId persistent goal identity
     * @param factionContentId owning faction content identity
     * @param type strategic goal family
     * @param targetId stable target identity
     * @param sourceEvidence persisted actor-bounded source evidence
     * @param urgencyBasisPoints urgency in {@code [0,10000]}
     * @param strategicValueBasisPoints strategic value in {@code [0,10000]}
     * @param feasibilityBasisPoints feasibility in {@code [0,10000]}
     * @param doctrinePreferenceBasisPoints doctrine preference in {@code [0,10000]}
     * @param requestedBudget requested planning envelope
     * @param allocatedBudget allocated planning envelope
     * @param blockers current blockers
     * @param lifecycle current lifecycle
     * @param createdAtTick creation tick
     * @param updatedAtTick last update tick
     * @param nextReviewTick next review tick
     * @param expiresAtTick expiry tick or {@code -1}
     * @param cooldownUntilTick cooldown horizon
     * @param cancellationCost cancellation cost
     * @param outcomeSignal terminal outcome signal
     */
    public StrategicGoalState(
            String goalId, String factionContentId, StrategicGoalType type, String targetId,
            StrategicGoalEvidence sourceEvidence, int urgencyBasisPoints, int strategicValueBasisPoints,
            int feasibilityBasisPoints, int doctrinePreferenceBasisPoints, StrategicPlanningEnvelope requestedBudget,
            StrategicPlanningEnvelope allocatedBudget, List<StrategicGoalBlocker> blockers, Lifecycle lifecycle,
            long createdAtTick, long updatedAtTick, long nextReviewTick, long expiresAtTick, long cooldownUntilTick,
            StrategicPlanningEnvelope cancellationCost, StrategicGoalOutcomeSignal outcomeSignal) {
        this(goalId, factionContentId, type, targetId, sourceEvidence, urgencyBasisPoints,
                strategicValueBasisPoints, feasibilityBasisPoints, doctrinePreferenceBasisPoints,
                requestedBudget, requestedBudget, defaultSuccess(type, targetId), defaultFailure(targetId),
                allocatedBudget, blockers, lifecycle, createdAtTick, updatedAtTick, nextReviewTick,
                expiresAtTick, cooldownUntilTick, cancellationCost, outcomeSignal);
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
     * @param requestedBudget requested planning envelope
     * @param allocatedBudget allocated planning envelope
     * @param blockers current blockers
     * @param lifecycle current lifecycle
     * @param createdAtTick creation tick
     * @param updatedAtTick last update tick
     * @param nextReviewTick next review tick
     * @param expiresAtTick expiry tick or {@code -1}
     * @param cooldownUntilTick cooldown horizon
     * @param cancellationCost cancellation cost
     * @param outcomeSignal terminal outcome signal
     */
    public StrategicGoalState(
            String goalId, String factionContentId, StrategicGoalType type, String targetId,
            StrategicGoalEvidence sourceEvidence, int urgencyBasisPoints, int feasibilityBasisPoints,
            StrategicPlanningEnvelope requestedBudget, StrategicPlanningEnvelope allocatedBudget,
            List<StrategicGoalBlocker> blockers, Lifecycle lifecycle, long createdAtTick, long updatedAtTick,
            long nextReviewTick, long expiresAtTick, long cooldownUntilTick,
            StrategicPlanningEnvelope cancellationCost, StrategicGoalOutcomeSignal outcomeSignal) {
        this(goalId, factionContentId, type, targetId, sourceEvidence, urgencyBasisPoints, 10_000,
                feasibilityBasisPoints, 10_000, requestedBudget, allocatedBudget, blockers, lifecycle,
                createdAtTick, updatedAtTick, nextReviewTick, expiresAtTick, cooldownUntilTick,
                cancellationCost, outcomeSignal);
    }

    /** @return canonical goal type/target key independent of persistent goal ID */
    public String intentKey() {
        return type.wireId() + "\u0000" + targetId;
    }

    /** @return true for active or stalled lifecycle states */
    public boolean isOpen() {
        return lifecycle == Lifecycle.ACTIVE || lifecycle == Lifecycle.STALLED;
    }

    /**
     * Refreshes an active goal while preserving persistent identity.
     *
     * @param candidate current candidate for the same intent
     * @param reviewTick authoritative review tick
     * @return refreshed goal
     */
    public StrategicGoalState refreshActive(StrategicGoalCandidate candidate, long reviewTick) {
        StrategicGoalCandidate checked = requireSameIntent(candidate);
        return fromCandidate(checked, checked.requestedBudget(), List.of(), Lifecycle.ACTIVE, reviewTick,
                Math.addExact(reviewTick, checked.reviewCadenceTicks()), 0L, StrategicPlanningEnvelope.ZERO,
                StrategicGoalOutcomeSignal.NONE);
    }

    /**
     * Stalls an accepted goal while preserving identity.
     *
     * @param candidate current candidate for the same intent
     * @param currentBlockers current blockers
     * @param reviewTick authoritative review tick
     * @return stalled goal
     */
    public StrategicGoalState stall(
            StrategicGoalCandidate candidate, List<StrategicGoalBlocker> currentBlockers, long reviewTick) {
        StrategicGoalCandidate checked = requireSameIntent(candidate);
        return fromCandidate(checked, StrategicPlanningEnvelope.ZERO, currentBlockers, Lifecycle.STALLED, reviewTick,
                Math.addExact(reviewTick, checked.reviewCadenceTicks()), 0L, StrategicPlanningEnvelope.ZERO,
                StrategicGoalOutcomeSignal.NONE);
    }

    /**
     * Completes a goal from an authoritative success signal.
     *
     * @param candidate current candidate for the same intent
     * @param reviewTick authoritative review tick
     * @return succeeded goal
     */
    public StrategicGoalState succeed(StrategicGoalCandidate candidate, long reviewTick) {
        StrategicGoalCandidate checked = requireSameIntent(candidate);
        return fromCandidate(checked, StrategicPlanningEnvelope.ZERO, List.of(), Lifecycle.SUCCEEDED, reviewTick,
                0L, 0L, StrategicPlanningEnvelope.ZERO, StrategicGoalOutcomeSignal.SUCCEEDED);
    }

    /**
     * Cancels a goal with explicit cooldown and switching cost.
     *
     * @param candidate latest candidate, or null when displaced
     * @param reviewTick authoritative review tick
     * @param cooldownUntil earliest reconsideration tick
     * @param cost visible switching cost
     * @param outcome terminal outcome, either NONE or FAILED
     * @return cancelled goal
     */
    public StrategicGoalState cancel(
            StrategicGoalCandidate candidate, long reviewTick, long cooldownUntil,
            StrategicPlanningEnvelope cost, StrategicGoalOutcomeSignal outcome) {
        if (outcome == StrategicGoalOutcomeSignal.SUCCEEDED) {
            throw new IllegalArgumentException("Succeeded outcome cannot cancel a strategic goal");
        }
        if (candidate == null) {
            return new StrategicGoalState(
                    goalId, factionContentId, type, targetId, sourceEvidence, urgencyBasisPoints,
                    strategicValueBasisPoints, feasibilityBasisPoints, doctrinePreferenceBasisPoints,
                    requestedBudget, costCeiling, successConditions, failureConditions,
                    StrategicPlanningEnvelope.ZERO, List.of(), Lifecycle.CANCELLED, createdAtTick, reviewTick,
                    0L, expiresAtTick, cooldownUntil,
                    Objects.requireNonNull(cost, "Strategic cancellation cost not set"), outcome);
        }
        StrategicGoalCandidate checked = requireSameIntent(candidate);
        return fromCandidate(checked, StrategicPlanningEnvelope.ZERO, List.of(), Lifecycle.CANCELLED, reviewTick,
                0L, cooldownUntil, Objects.requireNonNull(cost, "Strategic cancellation cost not set"), outcome);
    }

    /**
     * Expires a goal while preserving the latest candidate contract.
     *
     * @param candidate latest candidate for the same intent
     * @param reviewTick authoritative expiry tick
     * @return expired goal
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
            StrategicGoalCandidate candidate, StrategicPlanningEnvelope allocation,
            List<StrategicGoalBlocker> currentBlockers, Lifecycle nextLifecycle, long reviewTick,
            long nextReview, long cooldownUntil, StrategicPlanningEnvelope cost,
            StrategicGoalOutcomeSignal outcome) {
        return new StrategicGoalState(
                goalId, factionContentId, type, targetId, candidate.sourceEvidence(),
                candidate.urgencyBasisPoints(), candidate.strategicValueBasisPoints(),
                candidate.feasibilityBasisPoints(), candidate.doctrinePreferenceBasisPoints(),
                candidate.requestedBudget(), candidate.costCeiling(), candidate.successConditions(),
                candidate.failureConditions(), allocation, currentBlockers, nextLifecycle, createdAtTick,
                reviewTick, nextReview, candidate.expiresAtTick(), cooldownUntil, cost, outcome);
    }

    private static List<StrategicGoalCondition> normalizeConditions(
            List<StrategicGoalCondition> conditions, String label) {
        List<StrategicGoalCondition> normalized = Objects.requireNonNull(conditions, label + " not set").stream()
                .map(condition -> Objects.requireNonNull(condition, label + " contains null"))
                .sorted().distinct().toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return normalized;
    }

    private static List<StrategicGoalCondition> defaultSuccess(StrategicGoalType type, String targetId) {
        return List.of(new StrategicGoalCondition(type.wireId() + "-satisfied", targetId));
    }

    private static List<StrategicGoalCondition> defaultFailure(String targetId) {
        return List.of(new StrategicGoalCondition("objective-unreachable", targetId));
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
