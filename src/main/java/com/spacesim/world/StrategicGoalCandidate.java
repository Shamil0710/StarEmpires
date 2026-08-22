package com.spacesim.world;

import java.util.Objects;

/**
 * One read-only Stage-21B strategic option presented to the pure goal planner.
 *
 * <p>Budget units are a planning envelope only. They are not credits and this record has no
 * authority to mutate treasury, production, freight, diplomacy, fleets or territory.</p>
 *
 * @param type peaceful strategic goal family
 * @param targetId stable goal target identity
 * @param sourceEvidence actor-bounded evidence supporting the candidate
 * @param urgencyBasisPoints urgency in {@code [0,10000]}
 * @param feasibilityBasisPoints feasibility in {@code [0,10000]}
 * @param requestedBudgetUnits non-negative strategic planning-envelope request
 */
public record StrategicGoalCandidate(
        StrategicGoalType type,
        String targetId,
        StrategicGoalEvidence sourceEvidence,
        int urgencyBasisPoints,
        int feasibilityBasisPoints,
        long requestedBudgetUnits) implements Comparable<StrategicGoalCandidate> {

    /** Validates one candidate without deriving hidden-world information. */
    public StrategicGoalCandidate {
        Objects.requireNonNull(type, "Strategic goal type not set");
        targetId = requireText(targetId, "Strategic goal target ID");
        Objects.requireNonNull(sourceEvidence, "Strategic goal source evidence not set");
        if (!targetId.equals(sourceEvidence.targetId())) {
            throw new IllegalArgumentException("Strategic goal target must match source evidence target");
        }
        if (!type.supports(sourceEvidence.kind())) {
            throw new IllegalArgumentException(
                    "Strategic goal type " + type + " is not supported by evidence " + sourceEvidence.kind());
        }
        requireBasisPoints(urgencyBasisPoints, "Strategic goal urgency");
        requireBasisPoints(feasibilityBasisPoints, "Strategic goal feasibility");
        if (requestedBudgetUnits < 0L) {
            throw new IllegalArgumentException("Strategic goal requested budget cannot be negative");
        }
    }

    /**
     * Deterministic priority metric before hysteresis.
     *
     * @return urgency multiplied by feasibility, normalized back to basis points
     */
    public int effectivePriorityBasisPoints() {
        return (int) (((long) urgencyBasisPoints * (long) feasibilityBasisPoints) / 10_000L);
    }

    @Override
    public int compareTo(StrategicGoalCandidate other) {
        Objects.requireNonNull(other, "other");
        int priority = Integer.compare(other.effectivePriorityBasisPoints(), effectivePriorityBasisPoints());
        if (priority != 0) {
            return priority;
        }
        int typeOrder = type.compareTo(other.type);
        return typeOrder != 0 ? typeOrder : targetId.compareTo(other.targetId);
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in [0,10000]");
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
