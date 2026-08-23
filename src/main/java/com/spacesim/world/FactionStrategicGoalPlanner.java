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

/** Pure Stage-21B strategic-goal arbitration over actor-bounded read-only inputs. */
public final class FactionStrategicGoalPlanner {
    /** Candidate feasibility below this threshold stalls rather than silently disappearing. */
    public static final int MIN_FEASIBILITY_BASIS_POINTS = 2_500;
    /** Existing accepted intent receives this anti-churn priority bonus. */
    public static final int HYSTERESIS_BONUS_BASIS_POINTS = 750;
    /** Additional anti-churn bonus while the Stage-21A actor commitment horizon is active. */
    public static final int COMMITMENT_BONUS_BASIS_POINTS = 1_250;
    /** Default target-specific cooldown after cancellation or terminal failure. */
    public static final long DEFAULT_COOLDOWN_TICKS = 24L;
    /** Visible cancellation cost as a fraction of the previously allocated planning envelope. */
    public static final int CANCELLATION_COST_BASIS_POINTS = 1_000;

    private FactionStrategicGoalPlanner() {
        throw new AssertionError("Utility class");
    }

    /**
     * Reviews candidate intents without mutating any neighboring simulation authority.
     *
     * @param actorState Stage-21A lifecycle context
     * @param current current Stage-21B persistent intent state
     * @param candidates actor-bounded candidate goals
     * @param availableBudget normalized multidimensional strategic capacity projection
     * @param reviewTick authoritative review tick
     * @return deterministic immutable planning result
     */
    public static PlanningResult review(
            FactionLivingActorState actorState,
            FactionStrategicIntentState current,
            Collection<StrategicGoalCandidate> candidates,
            StrategicPlanningEnvelope availableBudget,
            long reviewTick) {
        FactionLivingActorState actor = Objects.requireNonNull(actorState, "Living actor state not set");
        FactionStrategicIntentState state = Objects.requireNonNull(current, "Strategic intent state not set");
        StrategicPlanningEnvelope capacity = Objects.requireNonNull(availableBudget, "Strategic planning budget not set");
        Objects.requireNonNull(candidates, "Strategic goal candidates not set");
        if (!actor.factionContentId().equals(state.factionContentId())) {
            throw new IllegalArgumentException("Living actor and strategic intent faction IDs differ");
        }
        if (reviewTick < 0L) {
            throw new IllegalArgumentException("Strategic review tick cannot be negative");
        }
        if (actor.lastReviewTick() >= 0L && reviewTick < actor.lastReviewTick()) {
            throw new IllegalArgumentException("Strategic review cannot precede actor lifecycle review history");
        }

        Map<String, StrategicGoalState> openByIntent = new HashMap<>();
        Map<String, Long> cooldownByIntent = new HashMap<>();
        for (StrategicGoalState goal : state.goals()) {
            if (goal.isOpen()) {
                openByIntent.put(goal.intentKey(), goal);
            } else if (goal.lifecycle() == Lifecycle.CANCELLED) {
                cooldownByIntent.merge(goal.intentKey(), goal.cooldownUntilTick(), Math::max);
            }
        }

        Map<String, StrategicGoalCandidate> candidateByIntent = new HashMap<>();
        for (StrategicGoalCandidate candidate : candidates) {
            StrategicGoalCandidate checked = Objects.requireNonNull(candidate, "Strategic goal candidate not set");
            String key = intentKey(checked.type(), checked.targetId());
            if (candidateByIntent.putIfAbsent(key, checked) != null) {
                throw new IllegalArgumentException("Duplicate strategic goal candidate: " + key);
            }
        }

        ArrayList<StrategicGoalState> nextGoals = new ArrayList<>();
        HashSet<String> resolvedOpenKeys = new HashSet<>();
        StrategicPlanningEnvelope remaining = capacity;
        StrategicPlanningEnvelope cancellationCost = StrategicPlanningEnvelope.ZERO;

        for (StrategicGoalState goal : state.goals()) {
            if (!goal.isOpen()) {
                nextGoals.add(goal);
                continue;
            }
            StrategicGoalCandidate candidate = candidateByIntent.get(goal.intentKey());
            if (candidate != null && candidate.isExpiredAt(reviewTick)) {
                nextGoals.add(goal.expire(candidate, reviewTick));
                resolvedOpenKeys.add(goal.intentKey());
                continue;
            }
            if (candidate != null && candidate.outcomeSignal() == StrategicGoalOutcomeSignal.SUCCEEDED) {
                nextGoals.add(goal.succeed(candidate, reviewTick));
                resolvedOpenKeys.add(goal.intentKey());
                continue;
            }
            if (candidate != null && candidate.outcomeSignal() == StrategicGoalOutcomeSignal.FAILED) {
                StrategicPlanningEnvelope cost = goal.allocatedBudget().fractionCeil(CANCELLATION_COST_BASIS_POINTS);
                cancellationCost = cancellationCost.plus(cost);
                nextGoals.add(goal.cancel(candidate, reviewTick, Math.addExact(reviewTick, DEFAULT_COOLDOWN_TICKS),
                        cost, StrategicGoalOutcomeSignal.FAILED));
                resolvedOpenKeys.add(goal.intentKey());
                continue;
            }
            if (reviewTick < goal.nextReviewTick()) {
                StrategicGoalState retained = retainBeforeCadence(goal, candidate, remaining, reviewTick);
                nextGoals.add(retained);
                resolvedOpenKeys.add(goal.intentKey());
                if (retained.lifecycle() == Lifecycle.ACTIVE) {
                    remaining = remaining.minus(retained.allocatedBudget());
                }
            }
        }

        ArrayList<ScoredCandidate> scored = new ArrayList<>();
        for (StrategicGoalCandidate candidate : candidateByIntent.values()) {
            String key = intentKey(candidate.type(), candidate.targetId());
            if (resolvedOpenKeys.contains(key)) {
                continue;
            }
            StrategicGoalState existing = openByIntent.get(key);
            long cooldownUntil = cooldownByIntent.getOrDefault(key, 0L);
            if (existing == null && cooldownUntil > reviewTick) {
                continue;
            }
            if (existing == null && (candidate.isExpiredAt(reviewTick)
                    || candidate.outcomeSignal() != StrategicGoalOutcomeSignal.NONE)) {
                continue;
            }
            int score = candidate.effectivePriorityBasisPoints();
            if (existing != null) {
                score = cappedAdd(score, HYSTERESIS_BONUS_BASIS_POINTS);
                if (reviewTick < actor.commitmentUntilTick()) {
                    score = cappedAdd(score, COMMITMENT_BONUS_BASIS_POINTS);
                }
            }
            scored.add(new ScoredCandidate(candidate, score));
        }
        scored.sort(Comparator
                .comparingInt(ScoredCandidate::scoreBasisPoints).reversed()
                .thenComparing(row -> row.candidate().type())
                .thenComparing(row -> row.candidate().targetId()));

        long nextSequence = state.nextGoalSequence();
        for (ScoredCandidate row : scored) {
            StrategicGoalCandidate candidate = row.candidate();
            String key = intentKey(candidate.type(), candidate.targetId());
            StrategicGoalState existing = openByIntent.get(key);
            if (candidate.isExpiredAt(reviewTick)) {
                if (existing != null) {
                    nextGoals.add(existing.expire(candidate, reviewTick));
                    resolvedOpenKeys.add(key);
                }
                continue;
            }
            if (candidate.outcomeSignal() == StrategicGoalOutcomeSignal.SUCCEEDED) {
                if (existing != null) {
                    nextGoals.add(existing.succeed(candidate, reviewTick));
                    resolvedOpenKeys.add(key);
                }
                continue;
            }
            if (candidate.outcomeSignal() == StrategicGoalOutcomeSignal.FAILED) {
                if (existing != null) {
                    StrategicPlanningEnvelope cost = existing.allocatedBudget()
                            .fractionCeil(CANCELLATION_COST_BASIS_POINTS);
                    cancellationCost = cancellationCost.plus(cost);
                    nextGoals.add(existing.cancel(candidate, reviewTick,
                            Math.addExact(reviewTick, DEFAULT_COOLDOWN_TICKS), cost,
                            StrategicGoalOutcomeSignal.FAILED));
                    resolvedOpenKeys.add(key);
                }
                continue;
            }

            List<StrategicGoalBlocker> currentBlockers = blockers(candidate, remaining);
            Lifecycle lifecycle = currentBlockers.isEmpty() ? Lifecycle.ACTIVE : Lifecycle.STALLED;
            StrategicPlanningEnvelope allocation = currentBlockers.isEmpty()
                    ? candidate.requestedBudget() : StrategicPlanningEnvelope.ZERO;
            StrategicGoalState next;
            if (existing != null) {
                next = currentBlockers.isEmpty()
                        ? existing.refreshActive(candidate, reviewTick)
                        : existing.stall(candidate, currentBlockers, reviewTick);
                resolvedOpenKeys.add(key);
            } else {
                String goalId = state.factionContentId() + ":strategic-goal:" + nextSequence;
                nextSequence = Math.addExact(nextSequence, 1L);
                next = new StrategicGoalState(
                        goalId, state.factionContentId(), candidate.type(), candidate.targetId(),
                        candidate.sourceEvidence(), candidate.urgencyBasisPoints(),
                        candidate.strategicValueBasisPoints(), candidate.feasibilityBasisPoints(),
                        candidate.doctrinePreferenceBasisPoints(), candidate.requestedBudget(),
                        candidate.costCeiling(), candidate.successConditions(), candidate.failureConditions(),
                        allocation, currentBlockers, lifecycle, reviewTick, reviewTick,
                        Math.addExact(reviewTick, candidate.reviewCadenceTicks()), candidate.expiresAtTick(),
                        0L, StrategicPlanningEnvelope.ZERO, StrategicGoalOutcomeSignal.NONE);
            }
            nextGoals.add(next);
            if (next.lifecycle() == Lifecycle.ACTIVE) {
                remaining = remaining.minus(next.allocatedBudget());
            }
        }

        for (StrategicGoalState goal : state.openGoals()) {
            if (resolvedOpenKeys.contains(goal.intentKey()) || reviewTick < goal.nextReviewTick()) {
                continue;
            }
            StrategicPlanningEnvelope cost = goal.allocatedBudget().fractionCeil(CANCELLATION_COST_BASIS_POINTS);
            cancellationCost = cancellationCost.plus(cost);
            nextGoals.add(goal.cancel(null, reviewTick, Math.addExact(reviewTick, DEFAULT_COOLDOWN_TICKS),
                    cost, StrategicGoalOutcomeSignal.NONE));
        }

        FactionStrategicIntentState nextState = new FactionStrategicIntentState(
                state.factionContentId(), nextSequence, nextGoals);
        StrategicPlanningEnvelope allocated = nextState.activeGoals().stream()
                .map(StrategicGoalState::allocatedBudget)
                .reduce(StrategicPlanningEnvelope.ZERO, StrategicPlanningEnvelope::plus);
        if (!allocated.fitsWithin(capacity)) {
            throw new IllegalStateException("Strategic planner oversubscribed multidimensional capacity");
        }
        List<GoalProjection> projections = nextState.goals().stream().map(GoalProjection::from).toList();
        return new PlanningResult(nextState, capacity, allocated, cancellationCost, projections);
    }

    private static StrategicGoalState retainBeforeCadence(
            StrategicGoalState goal, StrategicGoalCandidate candidate,
            StrategicPlanningEnvelope remaining, long reviewTick) {
        if (goal.lifecycle() == Lifecycle.STALLED) {
            return goal;
        }
        if (goal.allocatedBudget().fitsWithin(remaining)) {
            return goal;
        }
        StrategicGoalCandidate basis = candidate != null ? candidate : candidateFromState(goal);
        return goal.stall(basis, capacityBlockers(goal.allocatedBudget(), remaining), reviewTick);
    }

    private static StrategicGoalCandidate candidateFromState(StrategicGoalState goal) {
        long cadence = Math.max(1L, goal.nextReviewTick() - goal.updatedAtTick());
        return new StrategicGoalCandidate(
                goal.type(), goal.targetId(), goal.sourceEvidence(), goal.urgencyBasisPoints(),
                goal.strategicValueBasisPoints(), goal.feasibilityBasisPoints(),
                goal.doctrinePreferenceBasisPoints(), goal.requestedBudget(), goal.costCeiling(),
                goal.successConditions(), goal.failureConditions(), goal.blockers(), goal.expiresAtTick(),
                cadence, StrategicGoalOutcomeSignal.NONE);
    }

    private static List<StrategicGoalBlocker> blockers(
            StrategicGoalCandidate candidate, StrategicPlanningEnvelope remaining) {
        ArrayList<StrategicGoalBlocker> blockers = new ArrayList<>(candidate.blockers());
        if (!candidate.requestedBudget().fitsWithin(candidate.costCeiling())) {
            blockers.add(StrategicGoalBlocker.COST_CEILING);
        }
        if (candidate.feasibilityBasisPoints() < MIN_FEASIBILITY_BASIS_POINTS) {
            blockers.add(StrategicGoalBlocker.FEASIBILITY);
        }
        blockers.addAll(capacityBlockers(candidate.requestedBudget(), remaining));
        return blockers.stream().sorted().distinct().toList();
    }

    private static List<StrategicGoalBlocker> capacityBlockers(
            StrategicPlanningEnvelope request, StrategicPlanningEnvelope capacity) {
        ArrayList<StrategicGoalBlocker> blockers = new ArrayList<>();
        if (request.treasuryUnits() > capacity.treasuryUnits()) blockers.add(StrategicGoalBlocker.TREASURY_CAPACITY);
        if (request.logisticsUnits() > capacity.logisticsUnits()) blockers.add(StrategicGoalBlocker.LOGISTICS_CAPACITY);
        if (request.constructionUnits() > capacity.constructionUnits()) blockers.add(StrategicGoalBlocker.CONSTRUCTION_CAPACITY);
        if (request.readinessUnits() > capacity.readinessUnits()) blockers.add(StrategicGoalBlocker.READINESS_CAPACITY);
        return List.copyOf(blockers);
    }

    private static int cappedAdd(int value, int bonus) {
        return Math.min(10_000, Math.addExact(value, bonus));
    }

    private static String intentKey(StrategicGoalType type, String targetId) {
        return type.wireId() + "\u0000" + targetId;
    }

    private record ScoredCandidate(StrategicGoalCandidate candidate, int scoreBasisPoints) {
    }

    /**
     * Read-only explainability projection for UI/debug/replay inspection.
     *
     * @param goalId persistent goal identity
     * @param type strategic goal family
     * @param targetId stable target identity
     * @param lifecycle persistent lifecycle
     * @param urgencyBasisPoints current urgency
     * @param strategicValueBasisPoints current strategic value
     * @param feasibilityBasisPoints current feasibility
     * @param doctrinePreferenceBasisPoints current doctrine preference
     * @param scoreBasisPoints roadmap score before hysteresis
     * @param requestedBudget requested planning envelope
     * @param costCeiling accepted planning cost ceiling
     * @param successConditions declarative success conditions
     * @param failureConditions declarative failure conditions
     * @param allocatedBudget allocated planning envelope
     * @param blockers current explainable blockers
     * @param cancellationCost visible switching cost when cancelled
     * @param nextReviewTick next strategic re-review tick for open goals
     * @param expiresAtTick expiry horizon, or {@code -1}
     * @param cooldownUntilTick target cooldown horizon
     * @param outcomeSignal last authoritative terminal outcome signal
     * @param evidenceKind source Stage-21A interest family
     * @param evidencePriorityBasisPoints strongest source evidence magnitude
     * @param provenanceIds delivered report/ledger provenance identities
     */
    public record GoalProjection(
            String goalId, StrategicGoalType type, String targetId, Lifecycle lifecycle,
            int urgencyBasisPoints, int strategicValueBasisPoints, int feasibilityBasisPoints,
            int doctrinePreferenceBasisPoints, int scoreBasisPoints,
            StrategicPlanningEnvelope requestedBudget, StrategicPlanningEnvelope costCeiling,
            List<StrategicGoalCondition> successConditions, List<StrategicGoalCondition> failureConditions,
            StrategicPlanningEnvelope allocatedBudget, List<StrategicGoalBlocker> blockers,
            StrategicPlanningEnvelope cancellationCost, long nextReviewTick, long expiresAtTick,
            long cooldownUntilTick, StrategicGoalOutcomeSignal outcomeSignal,
            FactionActorObservationSnapshot.InterestKind evidenceKind,
            int evidencePriorityBasisPoints, List<String> provenanceIds) {

        private static GoalProjection from(StrategicGoalState goal) {
            return new GoalProjection(
                    goal.goalId(), goal.type(), goal.targetId(), goal.lifecycle(), goal.urgencyBasisPoints(),
                    goal.strategicValueBasisPoints(), goal.feasibilityBasisPoints(),
                    goal.doctrinePreferenceBasisPoints(), score(goal), goal.requestedBudget(), goal.costCeiling(),
                    goal.successConditions(), goal.failureConditions(), goal.allocatedBudget(), goal.blockers(),
                    goal.cancellationCost(), goal.nextReviewTick(), goal.expiresAtTick(), goal.cooldownUntilTick(),
                    goal.outcomeSignal(), goal.sourceEvidence().kind(), goal.sourceEvidence().priorityBasisPoints(),
                    goal.sourceEvidence().provenance().stream().map(ObservationEvidence::provenanceId).toList());
        }

        private static int score(StrategicGoalState goal) {
            long product = (long) goal.urgencyBasisPoints() * goal.strategicValueBasisPoints();
            product = product * goal.feasibilityBasisPoints();
            product = product * goal.doctrinePreferenceBasisPoints();
            return (int) (product / 1_000_000_000_000L);
        }

        /** @return lifecycle/type/evidence/target/blocker explanation code */
        public String explanationCode() {
            String blockerCode = blockers.isEmpty() ? "clear"
                    : blockers.stream().map(blocker -> blocker.name().toLowerCase()).sorted()
                            .reduce((left, right) -> left + "," + right).orElse("clear");
            return lifecycle.name().toLowerCase() + ":" + type.wireId() + ":"
                    + evidenceKind.name().toLowerCase() + ":" + targetId + ":" + blockerCode;
        }
    }

    /**
     * Complete pure-planner result.
     *
     * @param state next persistent intent state
     * @param availableBudget supplied multidimensional planning capacity
     * @param allocatedBudget capacity allocated to active goals
     * @param cancellationCost visible switching costs created by this review
     * @param projections read-only explainability rows
     */
    public record PlanningResult(
            FactionStrategicIntentState state,
            StrategicPlanningEnvelope availableBudget,
            StrategicPlanningEnvelope allocatedBudget,
            StrategicPlanningEnvelope cancellationCost,
            List<GoalProjection> projections) {
        /**
         * Validates immutable planning output accounting.
         *
         * @param state next persistent intent state
         * @param availableBudget supplied multidimensional planning capacity
         * @param allocatedBudget capacity allocated to active goals
         * @param cancellationCost visible switching costs created by this review
         * @param projections read-only explainability rows
         */
        public PlanningResult {
            Objects.requireNonNull(state, "Strategic planning state not set");
            Objects.requireNonNull(availableBudget, "Strategic planning capacity not set");
            Objects.requireNonNull(allocatedBudget, "Strategic allocated budget not set");
            Objects.requireNonNull(cancellationCost, "Strategic cancellation cost not set");
            if (!allocatedBudget.fitsWithin(availableBudget)) {
                throw new IllegalArgumentException("Strategic goal allocation exceeds planning envelope");
            }
            projections = List.copyOf(Objects.requireNonNull(projections, "Strategic goal projections not set"));
        }
    }
}
