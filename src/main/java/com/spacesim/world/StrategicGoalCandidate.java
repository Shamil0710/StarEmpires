package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * One read-only Stage-21B strategic option presented to the pure goal planner.
 *
 * <p>The budget is a normalized planning envelope only. This record has no authority to mutate
 * treasury, production, freight, diplomacy, fleets or territory. Blockers and outcome signals must
 * likewise be supplied by actor-visible authority/read-model adapters rather than hidden world
 * truth.</p>
 *
 * @param type strategic goal family
 * @param targetId stable goal target identity
 * @param sourceEvidence actor-bounded evidence supporting the candidate
 * @param urgencyBasisPoints urgency in {@code [0,10000]}
 * @param strategicValueBasisPoints strategic value in {@code [0,10000]}
 * @param feasibilityBasisPoints feasibility in {@code [0,10000]}
 * @param doctrinePreferenceBasisPoints caller-owned doctrine preference in {@code [0,10000]}
 * @param requestedBudget multidimensional normalized planning request
 * @param blockers current actor-known non-capacity blockers
 * @param expiresAtTick terminal expiry tick, or {@code -1} for no automatic expiry
 * @param reviewCadenceTicks positive interval between strategic re-reviews
 * @param outcomeSignal read-only terminal outcome reported by an execution authority
 */
public record StrategicGoalCandidate(
        StrategicGoalType type,
        String targetId,
        StrategicGoalEvidence sourceEvidence,
        int urgencyBasisPoints,
        int strategicValueBasisPoints,
        int feasibilityBasisPoints,
        int doctrinePreferenceBasisPoints,
        StrategicPlanningEnvelope requestedBudget,
        List<StrategicGoalBlocker> blockers,
        long expiresAtTick,
        long reviewCadenceTicks,
        StrategicGoalOutcomeSignal outcomeSignal) implements Comparable<StrategicGoalCandidate> {

    /**
     * Validates one candidate without deriving hidden-world information.
     *
     * @param type strategic goal family
     * @param targetId stable goal target identity
     * @param sourceEvidence actor-bounded evidence supporting the candidate
     * @param urgencyBasisPoints urgency in {@code [0,10000]}
     * @param strategicValueBasisPoints strategic value in {@code [0,10000]}
     * @param feasibilityBasisPoints feasibility in {@code [0,10000]}
     * @param doctrinePreferenceBasisPoints caller-owned doctrine preference in {@code [0,10000]}
     * @param requestedBudget multidimensional normalized planning request
     * @param blockers current actor-known non-capacity blockers
     * @param expiresAtTick terminal expiry tick, or {@code -1} for no automatic expiry
     * @param reviewCadenceTicks positive interval between strategic re-reviews
     * @param outcomeSignal read-only terminal outcome reported by an execution authority
     */
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
        requireBasisPoints(strategicValueBasisPoints, "Strategic goal value");
        requireBasisPoints(feasibilityBasisPoints, "Strategic goal feasibility");
        requireBasisPoints(doctrinePreferenceBasisPoints, "Strategic doctrine preference");
        Objects.requireNonNull(requestedBudget, "Strategic goal requested budget not set");
        blockers = Objects.requireNonNull(blockers, "Strategic goal blockers not set").stream()
                .map(blocker -> Objects.requireNonNull(blocker, "Strategic goal blocker not set"))
                .sorted()
                .distinct()
                .toList();
        if (expiresAtTick < -1L) {
            throw new IllegalArgumentException("Strategic goal expiry must be -1 or non-negative");
        }
        if (reviewCadenceTicks <= 0L) {
            throw new IllegalArgumentException("Strategic goal review cadence must be positive");
        }
        Objects.requireNonNull(outcomeSignal, "Strategic goal outcome signal not set");
    }

    /**
     * Backward-compatible constructor for callers that have not supplied value/doctrine weights.
     *
     * @param type strategic goal family
     * @param targetId stable goal target identity
     * @param sourceEvidence actor-bounded evidence supporting the candidate
     * @param urgencyBasisPoints urgency in {@code [0,10000]}
     * @param feasibilityBasisPoints feasibility in {@code [0,10000]}
     * @param requestedBudget multidimensional normalized planning request
     * @param blockers current actor-known non-capacity blockers
     * @param expiresAtTick terminal expiry tick, or {@code -1}
     * @param reviewCadenceTicks positive interval between reviews
     * @param outcomeSignal read-only terminal outcome signal
     */
    public StrategicGoalCandidate(
            StrategicGoalType type,
            String targetId,
            StrategicGoalEvidence sourceEvidence,
            int urgencyBasisPoints,
            int feasibilityBasisPoints,
            StrategicPlanningEnvelope requestedBudget,
            List<StrategicGoalBlocker> blockers,
            long expiresAtTick,
            long reviewCadenceTicks,
            StrategicGoalOutcomeSignal outcomeSignal) {
        this(type, targetId, sourceEvidence, urgencyBasisPoints, 10_000, feasibilityBasisPoints, 10_000,
                requestedBudget, blockers, expiresAtTick, reviewCadenceTicks, outcomeSignal);
    }

    /**
     * Deterministic roadmap score before hysteresis.
     *
     * @return urgency × strategic value × feasibility × doctrine preference, normalized to basis points
     */
    public int effectivePriorityBasisPoints() {
        long product = (long) urgencyBasisPoints * strategicValueBasisPoints;
        product = product * feasibilityBasisPoints;
        product = product * doctrinePreferenceBasisPoints;
        return (int) (product / 1_000_000_000_000L);
    }

    /**
     * Reports whether this candidate is expired at the supplied authoritative tick.
     *
     * @param tick authoritative review tick
     * @return true when a finite expiry has been reached
     */
    public boolean isExpiredAt(long tick) {
        if (tick < 0L) {
            throw new IllegalArgumentException("Strategic review tick cannot be negative");
        }
        return expiresAtTick >= 0L && tick >= expiresAtTick;
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
