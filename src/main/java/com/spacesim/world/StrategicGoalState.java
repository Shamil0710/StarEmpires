package com.spacesim.world;

import java.util.Objects;

/**
 * Persistent Stage-21B state of one explainable strategic goal.
 *
 * <p>The goal is intent metadata only. Allocation is expressed in abstract planning-envelope units
 * and therefore does not duplicate treasury or project authority.</p>
 *
 * @param goalId persistent goal identity
 * @param factionContentId owning faction content identity
 * @param type peaceful goal family
 * @param targetId stable target identity
 * @param sourceEvidence persisted actor-bounded source evidence
 * @param urgencyBasisPoints urgency in {@code [0,10000]}
 * @param feasibilityBasisPoints feasibility in {@code [0,10000]}
 * @param requestedBudgetUnits requested strategic planning envelope
 * @param allocatedBudgetUnits currently allocated strategic planning envelope
 * @param lifecycle current goal lifecycle
 * @param createdAtTick creation tick
 * @param updatedAtTick last review/update tick
 * @param cooldownUntilTick earliest tick a cancelled target may be reconsidered, or zero
 * @param cancellationCostUnits visible switching cost in planning-envelope units
 */
public record StrategicGoalState(
        String goalId,
        String factionContentId,
        StrategicGoalType type,
        String targetId,
        StrategicGoalEvidence sourceEvidence,
        int urgencyBasisPoints,
        int feasibilityBasisPoints,
        long requestedBudgetUnits,
        long allocatedBudgetUnits,
        Lifecycle lifecycle,
        long createdAtTick,
        long updatedAtTick,
        long cooldownUntilTick,
        long cancellationCostUnits) implements Comparable<StrategicGoalState> {

    /** Persistent Stage-21B goal lifecycle. */
    public enum Lifecycle {
        /** Goal is currently selected and receives planning-envelope allocation. */ ACTIVE,
        /** Goal was explicitly completed by a later execution layer. */ COMPLETED,
        /** Goal was displaced or became infeasible and entered target cooldown. */ CANCELLED
    }

    /** Validates one immutable persistent goal state. */
    public StrategicGoalState {
        goalId = requireText(goalId, "Strategic goal ID");
        factionContentId = requireText(factionContentId, "Strategic goal faction ID");
        Objects.requireNonNull(type, "Strategic goal type not set");
        targetId = requireText(targetId, "Strategic goal target ID");
        Objects.requireNonNull(sourceEvidence, "Strategic goal source evidence not set");
        Objects.requireNonNull(lifecycle, "Strategic goal lifecycle not set");
        if (!targetId.equals(sourceEvidence.targetId())) {
            throw new IllegalArgumentException("Strategic goal target must match source evidence target");
        }
        if (!type.supports(sourceEvidence.kind())) {
            throw new IllegalArgumentException("Strategic goal type is incompatible with persisted evidence");
        }
        requireBasisPoints(urgencyBasisPoints, "Strategic goal urgency");
        requireBasisPoints(feasibilityBasisPoints, "Strategic goal feasibility");
        requireNonNegative(requestedBudgetUnits, "Strategic goal requested budget");
        requireNonNegative(allocatedBudgetUnits, "Strategic goal allocated budget");
        if (allocatedBudgetUnits > requestedBudgetUnits) {
            throw new IllegalArgumentException("Strategic goal allocation cannot exceed request");
        }
        requireNonNegative(createdAtTick, "Strategic goal creation tick");
        requireNonNegative(updatedAtTick, "Strategic goal update tick");
        requireNonNegative(cooldownUntilTick, "Strategic goal cooldown horizon");
        requireNonNegative(cancellationCostUnits, "Strategic goal cancellation cost");
        if (updatedAtTick < createdAtTick) {
            throw new IllegalArgumentException("Strategic goal update cannot precede creation");
        }
        if (lifecycle == Lifecycle.ACTIVE) {
            if (cooldownUntilTick != 0L || cancellationCostUnits != 0L) {
                throw new IllegalArgumentException("Active strategic goal cannot carry cooldown or cancellation cost");
            }
        } else if (allocatedBudgetUnits != 0L) {
            throw new IllegalArgumentException("Terminal strategic goal cannot retain active budget allocation");
        }
        if (lifecycle == Lifecycle.CANCELLED && cooldownUntilTick < updatedAtTick) {
            throw new IllegalArgumentException("Cancelled strategic goal cooldown cannot precede cancellation");
        }
        if (lifecycle == Lifecycle.COMPLETED && cooldownUntilTick != 0L) {
            throw new IllegalArgumentException("Completed strategic goal cannot carry cooldown");
        }
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
     * Returns a refreshed active state while preserving persistent identity and creation tick.
     *
     * @param candidate current candidate for the same type/target identity
     * @param allocation current strategic planning-envelope allocation
     * @param reviewTick authoritative review tick
     * @return refreshed immutable active goal state
     */
    public StrategicGoalState refresh(StrategicGoalCandidate candidate, long allocation, long reviewTick) {
        StrategicGoalCandidate checked = Objects.requireNonNull(candidate, "Strategic goal candidate not set");
        if (type != checked.type() || !targetId.equals(checked.targetId())) {
            throw new IllegalArgumentException("Cannot refresh strategic goal with different intent identity");
        }
        return new StrategicGoalState(
                goalId,
                factionContentId,
                type,
                targetId,
                checked.sourceEvidence(),
                checked.urgencyBasisPoints(),
                checked.feasibilityBasisPoints(),
                checked.requestedBudgetUnits(),
                allocation,
                Lifecycle.ACTIVE,
                createdAtTick,
                reviewTick,
                0L,
                0L);
    }

    /**
     * Returns a cancelled state with explicit cooldown and switching cost.
     *
     * @param reviewTick authoritative cancellation review tick
     * @param cooldownUntil earliest tick at which this intent may be reconsidered
     * @param cancellationCost visible strategic switching cost
     * @return cancelled immutable goal state
     */
    public StrategicGoalState cancel(long reviewTick, long cooldownUntil, long cancellationCost) {
        return new StrategicGoalState(
                goalId,
                factionContentId,
                type,
                targetId,
                sourceEvidence,
                urgencyBasisPoints,
                feasibilityBasisPoints,
                requestedBudgetUnits,
                0L,
                Lifecycle.CANCELLED,
                createdAtTick,
                reviewTick,
                cooldownUntil,
                cancellationCost);
    }

    @Override
    public int compareTo(StrategicGoalState other) {
        Objects.requireNonNull(other, "other");
        return goalId.compareTo(other.goalId);
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
