package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.StrategicGoalState.Lifecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure Stage-21B strategic-goal arbitration.
 *
 * <p>The planner consumes actor-bounded candidates and an abstract planning envelope. It never
 * changes treasury, production queues, cargo, fleets, treaties, diplomatic relations or territory.
 * Those remain upstream authorities. The output is persistent intent plus a read-only explanation
 * projection for later execution/UI layers.</p>
 */
public final class FactionStrategicGoalPlanner {
    /** Candidate feasibility below this threshold cannot become active. */
    public static final int MIN_FEASIBILITY_BASIS_POINTS = 2_500;
    /** Existing active intent receives this anti-churn priority bonus. */
    public static final int HYSTERESIS_BONUS_BASIS_POINTS = 750;
    /** Additional anti-churn bonus while the Stage-21A actor commitment horizon is active. */
    public static final int COMMITMENT_BONUS_BASIS_POINTS = 1_250;
    /** Default target-specific cooldown after cancellation. */
    public static final long DEFAULT_COOLDOWN_TICKS = 24L;
    /** Visible cancellation cost, expressed as a fraction of the displaced allocation. */
    public static final int CANCELLATION_COST_BASIS_POINTS = 1_000;

    private FactionStrategicGoalPlanner() {
        throw new AssertionError("Utility class");
    }

    /**
     * Reviews candidate intents and returns the next persistent strategic-goal state.
     *
     * @param actorState Stage-21A lifecycle authority for commitment/hysteresis context
     * @param current current Stage-21B persistent intent state
     * @param candidates actor-bounded candidate goals
     * @param availableBudgetUnits abstract strategic planning-envelope capacity
     * @param reviewTick authoritative review tick
     * @return deterministic immutable planning result
     */
    public static PlanningResult review(
            FactionLivingActorState actorState,
            FactionStrategicIntentState current,
            Collection<StrategicGoalCandidate> candidates,
            long availableBudgetUnits,
            long reviewTick) {
        FactionLivingActorState actor = Objects.requireNonNull(actorState, "Living actor state not set");
        FactionStrategicIntentState state = Objects.requireNonNull(current, "Strategic intent state not set");
        Objects.requireNonNull(candidates, "Strategic goal candidates not set");
        if (!actor.factionContentId().equals(state.factionContentId())) {
            throw new IllegalArgumentException("Living actor and strategic intent faction IDs differ");
        }
        if (availableBudgetUnits < 0L) {
            throw new IllegalArgumentException("Strategic planning budget cannot be negative");
        }
        if (reviewTick < 0L) {
            throw new IllegalArgumentException("Strategic review tick cannot be negative");
        }
        if (actor.lastReviewTick() >= 0L && reviewTick < actor.lastReviewTick()) {
            throw new IllegalArgumentException("Strategic review cannot precede actor lifecycle review history");
        }

        Map<String, StrategicGoalState> activeByIntent = new HashMap<>();
        Map<String, Long> cooldownByIntent = new HashMap<>();
        for (StrategicGoalState goal : state.goals()) {
            if (goal.lifecycle() == Lifecycle.ACTIVE) {
                activeByIntent.put(goal.intentKey(), goal);
            } else if (goal.lifecycle() == Lifecycle.CANCELLED) {
                cooldownByIntent.merge(goal.intentKey(), goal.cooldownUntilTick(), Math::max);
            }
        }

        ArrayList<ScoredCandidate> scored = new ArrayList<>();
        HashSet<String> candidateKeys = new HashSet<>();
        for (StrategicGoalCandidate candidate : candidates) {
            StrategicGoalCandidate checked = Objects.requireNonNull(candidate, "Strategic goal candidate not set");
            String key = intentKey(checked.type(), checked.targetId());
            if (!candidateKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate strategic goal candidate: " + key);
            }
            if (checked.feasibilityBasisPoints() < MIN_FEASIBILITY_BASIS_POINTS
                    || checked.urgencyBasisPoints() == 0
                    || checked.requestedBudgetUnits() == 0L) {
                continue;
            }
            StrategicGoalState existing = activeByIntent.get(key);
            long cooldownUntil = cooldownByIntent.getOrDefault(key, 0L);
            if (existing == null && cooldownUntil > reviewTick) {
                continue;
            }
            int score = checked.effectivePriorityBasisPoints();
            if (existing != null) {
                score = cappedAdd(score, HYSTERESIS_BONUS_BASIS_POINTS);
                if (reviewTick < actor.commitmentUntilTick()) {
                    score = cappedAdd(score, COMMITMENT_BONUS_BASIS_POINTS);
                }
            }
            scored.add(new ScoredCandidate(checked, score));
        }
        scored.sort(Comparator
                .comparingInt(ScoredCandidate::scoreBasisPoints).reversed()
                .thenComparing(row -> row.candidate().type())
                .thenComparing(row -> row.candidate().targetId()));

        Map<String, Long> allocationByIntent = new HashMap<>();
        Map<String, StrategicGoalCandidate> selectedByIntent = new HashMap<>();
        long remaining = availableBudgetUnits;
        for (ScoredCandidate row : scored) {
            if (remaining == 0L) {
                break;
            }
            StrategicGoalCandidate candidate = row.candidate();
            long allocation = Math.min(candidate.requestedBudgetUnits(), remaining);
            if (allocation <= 0L) {
                continue;
            }
            String key = intentKey(candidate.type(), candidate.targetId());
            allocationByIntent.put(key, allocation);
            selectedByIntent.put(key, candidate);
            remaining -= allocation;
        }

        ArrayList<StrategicGoalState> nextGoals = new ArrayList<>();
        long cancellationCostUnits = 0L;
        for (StrategicGoalState goal : state.goals()) {
            if (goal.lifecycle() != Lifecycle.ACTIVE) {
                nextGoals.add(goal);
                continue;
            }
            StrategicGoalCandidate candidate = selectedByIntent.get(goal.intentKey());
            if (candidate != null) {
                nextGoals.add(goal.refresh(candidate, allocationByIntent.get(goal.intentKey()), reviewTick));
            } else {
                long cost = fractionCeil(goal.allocatedBudgetUnits(), CANCELLATION_COST_BASIS_POINTS);
                cancellationCostUnits = Math.addExact(cancellationCostUnits, cost);
                long cooldownUntil = Math.addExact(reviewTick, DEFAULT_COOLDOWN_TICKS);
                nextGoals.add(goal.cancel(reviewTick, cooldownUntil, cost));
            }
        }

        long nextSequence = state.nextGoalSequence();
        for (ScoredCandidate row : scored) {
            StrategicGoalCandidate candidate = row.candidate();
            String key = intentKey(candidate.type(), candidate.targetId());
            Long allocation = allocationByIntent.get(key);
            if (allocation == null || activeByIntent.containsKey(key)) {
                continue;
            }
            String goalId = state.factionContentId() + ":strategic-goal:" + nextSequence;
            nextSequence = Math.addExact(nextSequence, 1L);
            nextGoals.add(new StrategicGoalState(
                    goalId,
                    state.factionContentId(),
                    candidate.type(),
                    candidate.targetId(),
                    candidate.sourceEvidence(),
                    candidate.urgencyBasisPoints(),
                    candidate.feasibilityBasisPoints(),
                    candidate.requestedBudgetUnits(),
                    allocation,
                    Lifecycle.ACTIVE,
                    reviewTick,
                    reviewTick,
                    0L,
                    0L));
        }

        FactionStrategicIntentState nextState = new FactionStrategicIntentState(
                state.factionContentId(),
                nextSequence,
                nextGoals);
        long allocatedBudgetUnits = availableBudgetUnits - remaining;
        List<GoalProjection> projections = nextState.goals().stream()
                .map(GoalProjection::from)
                .toList();
        return new PlanningResult(
                nextState,
                availableBudgetUnits,
                allocatedBudgetUnits,
                cancellationCostUnits,
                projections);
    }

    private static int cappedAdd(int value, int bonus) {
        return Math.min(10_000, Math.addExact(value, bonus));
    }

    private static String intentKey(StrategicGoalType type, String targetId) {
        return type.wireId() + "\u0000" + targetId;
    }

    private static long fractionCeil(long value, int basisPoints) {
        if (value == 0L || basisPoints == 0) {
            return 0L;
        }
        long whole = Math.multiplyExact(value / 10_000L, basisPoints);
        long remainderProduct = (value % 10_000L) * (long) basisPoints;
        long remainder = remainderProduct / 10_000L;
        if (remainderProduct % 10_000L != 0L) {
            remainder = Math.addExact(remainder, 1L);
        }
        return Math.addExact(whole, remainder);
    }

    private record ScoredCandidate(StrategicGoalCandidate candidate, int scoreBasisPoints) {
    }

    /**
     * Read-only explainability projection for UI/debug/replay inspection.
     *
     * @param goalId persistent goal identity
     * @param type peaceful goal family
     * @param targetId stable target identity
     * @param lifecycle persistent lifecycle
     * @param urgencyBasisPoints current urgency
     * @param feasibilityBasisPoints current feasibility
     * @param requestedBudgetUnits requested planning envelope
     * @param allocatedBudgetUnits allocated planning envelope
     * @param cancellationCostUnits visible switching cost when cancelled
     * @param cooldownUntilTick target cooldown horizon
     * @param evidenceKind source Stage-21A interest family
     * @param evidencePriorityBasisPoints strongest source evidence magnitude
     * @param provenanceIds delivered report/ledger provenance identities
     */
    public record GoalProjection(
            String goalId,
            StrategicGoalType type,
            String targetId,
            Lifecycle lifecycle,
            int urgencyBasisPoints,
            int feasibilityBasisPoints,
            long requestedBudgetUnits,
            long allocatedBudgetUnits,
            long cancellationCostUnits,
            long cooldownUntilTick,
            FactionActorObservationSnapshot.InterestKind evidenceKind,
            int evidencePriorityBasisPoints,
            List<String> provenanceIds) {

        private static GoalProjection from(StrategicGoalState goal) {
            return new GoalProjection(
                    goal.goalId(),
                    goal.type(),
                    goal.targetId(),
                    goal.lifecycle(),
                    goal.urgencyBasisPoints(),
                    goal.feasibilityBasisPoints(),
                    goal.requestedBudgetUnits(),
                    goal.allocatedBudgetUnits(),
                    goal.cancellationCostUnits(),
                    goal.cooldownUntilTick(),
                    goal.sourceEvidence().kind(),
                    goal.sourceEvidence().priorityBasisPoints(),
                    goal.sourceEvidence().provenance().stream()
                            .map(ObservationEvidence::provenanceId)
                            .toList());
        }

        /**
         * Returns a compact stable explanation suitable for logs/debug UI.
         *
         * @return lifecycle/type/evidence/target explanation code
         */
        public String explanationCode() {
            return lifecycle.name().toLowerCase()
                    + ":" + type.wireId()
                    + ":" + evidenceKind.name().toLowerCase()
                    + ":" + targetId;
        }
    }

    /**
     * Complete pure-planner result.
     *
     * @param state next persistent intent state
     * @param availableBudgetUnits supplied planning envelope
     * @param allocatedBudgetUnits envelope allocated to active goals
     * @param cancellationCostUnits visible switching costs created by this review
     * @param projections read-only explainability rows
     */
    public record PlanningResult(
            FactionStrategicIntentState state,
            long availableBudgetUnits,
            long allocatedBudgetUnits,
            long cancellationCostUnits,
            List<GoalProjection> projections) {

        /** Validates immutable planning output accounting. */
        public PlanningResult {
            Objects.requireNonNull(state, "Strategic planning state not set");
            if (availableBudgetUnits < 0L || allocatedBudgetUnits < 0L || cancellationCostUnits < 0L) {
                throw new IllegalArgumentException("Strategic planning accounting cannot be negative");
            }
            if (allocatedBudgetUnits > availableBudgetUnits) {
                throw new IllegalArgumentException("Strategic goal allocation exceeds planning envelope");
            }
            projections = List.copyOf(Objects.requireNonNull(projections, "Strategic goal projections not set"));
        }
    }
}
