package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.StrategicGoalState.Lifecycle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionStrategicGoalPlannerTest {

    @Test
    void acceptedGoalKeepsPersistentIdAcrossReviewsAndProducesExplainableProjection() {
        FactionLivingActorState actor = FactionLivingActorState.initial("faction.alpha", 0L);
        StrategicGoalCandidate candidate = candidate(
                StrategicGoalType.STOCKPILE,
                InterestKind.RESOURCE_DEFICIT,
                "resource.water",
                7000,
                9000,
                StrategicPlanningEnvelope.balanced(40L),
                List.of(),
                -1L,
                StrategicGoalOutcomeSignal.NONE,
                "ledger.water");

        FactionStrategicGoalPlanner.PlanningResult first = FactionStrategicGoalPlanner.review(
                actor,
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(candidate),
                StrategicPlanningEnvelope.balanced(40L),
                10L);
        FactionStrategicGoalPlanner.PlanningResult second = FactionStrategicGoalPlanner.review(
                actor,
                first.state(),
                List.of(candidate),
                StrategicPlanningEnvelope.balanced(40L),
                20L);

        assertEquals(1, first.state().activeGoals().size());
        assertEquals(first.state().activeGoals().get(0).goalId(), second.state().activeGoals().get(0).goalId());
        assertEquals(StrategicPlanningEnvelope.balanced(40L), second.allocatedBudget());
        assertEquals("ledger.water", second.projections().get(0).provenanceIds().get(0));
        assertTrue(second.projections().get(0).explanationCode().contains("stockpile"));
    }

    @Test
    void multidimensionalBudgetAndHysteresisPreferCommittedIntentAndExposeCapacityBlocker() {
        FactionLivingActorState actor = FactionLivingActorState.initial("faction.alpha", 0L)
                .withCommitmentUntilTick(100L);
        StrategicGoalCandidate oldCandidate = candidate(
                StrategicGoalType.DEFEND,
                InterestKind.BORDER_SECURITY,
                "system.home-border",
                6000,
                10_000,
                StrategicPlanningEnvelope.balanced(10L),
                List.of(),
                -1L,
                StrategicGoalOutcomeSignal.NONE,
                "border.old");
        FactionStrategicGoalPlanner.PlanningResult seeded = FactionStrategicGoalPlanner.review(
                actor,
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(oldCandidate),
                StrategicPlanningEnvelope.balanced(10L),
                10L);
        StrategicGoalCandidate challenger = candidate(
                StrategicGoalType.EXPLORE,
                InterestKind.TERRITORIAL_OPPORTUNITY,
                "system.frontier",
                6500,
                10_000,
                new StrategicPlanningEnvelope(0L, 0L, 8L, 0L),
                List.of(),
                -1L,
                StrategicGoalOutcomeSignal.NONE,
                "survey.frontier");

        FactionStrategicGoalPlanner.PlanningResult reviewed = FactionStrategicGoalPlanner.review(
                actor,
                seeded.state(),
                List.of(challenger, oldCandidate),
                StrategicPlanningEnvelope.balanced(10L),
                20L);

        assertEquals(1, reviewed.state().activeGoals().size());
        assertEquals(StrategicGoalType.DEFEND, reviewed.state().activeGoals().get(0).type());
        StrategicGoalState stalled = reviewed.state().goals().stream()
                .filter(goal -> goal.type() == StrategicGoalType.EXPLORE)
                .findFirst().orElseThrow();
        assertEquals(Lifecycle.STALLED, stalled.lifecycle());
        assertEquals(List.of(StrategicGoalBlocker.CONSTRUCTION_CAPACITY), stalled.blockers());
    }

    @Test
    void feasibilityOrExternalBlockerStallsAndThenRecoversWithSamePersistentGoalId() {
        FactionLivingActorState actor = FactionLivingActorState.initial("faction.alpha", 0L);
        StrategicGoalCandidate blocked = candidate(
                StrategicGoalType.SECURE_ROUTE,
                InterestKind.ROUTE_EXPOSURE,
                "route.alpha-beta",
                8000,
                1000,
                StrategicPlanningEnvelope.balanced(5L),
                List.of(StrategicGoalBlocker.INSUFFICIENT_INTELLIGENCE),
                -1L,
                StrategicGoalOutcomeSignal.NONE,
                "route.report");
        FactionStrategicGoalPlanner.PlanningResult stalled = FactionStrategicGoalPlanner.review(
                actor,
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(blocked),
                StrategicPlanningEnvelope.balanced(10L),
                10L);
        StrategicGoalState stalledGoal = stalled.state().openGoals().get(0);
        assertEquals(Lifecycle.STALLED, stalledGoal.lifecycle());
        assertTrue(stalledGoal.blockers().contains(StrategicGoalBlocker.FEASIBILITY));
        assertTrue(stalledGoal.blockers().contains(StrategicGoalBlocker.INSUFFICIENT_INTELLIGENCE));

        StrategicGoalCandidate recovered = candidate(
                StrategicGoalType.SECURE_ROUTE,
                InterestKind.ROUTE_EXPOSURE,
                "route.alpha-beta",
                8000,
                9000,
                StrategicPlanningEnvelope.balanced(5L),
                List.of(),
                -1L,
                StrategicGoalOutcomeSignal.NONE,
                "route.report.new");
        FactionStrategicGoalPlanner.PlanningResult active = FactionStrategicGoalPlanner.review(
                actor,
                stalled.state(),
                List.of(recovered),
                StrategicPlanningEnvelope.balanced(10L),
                20L);

        assertEquals(1, active.state().activeGoals().size());
        assertEquals(stalledGoal.goalId(), active.state().activeGoals().get(0).goalId());
    }

    @Test
    void terminalFailureCreatesCancellationCostCooldownAndFreshIdentityAfterCooldown() {
        FactionLivingActorState actor = FactionLivingActorState.initial("faction.alpha", 0L);
        StrategicGoalCandidate feasible = candidate(
                StrategicGoalType.STOCKPILE,
                InterestKind.RESOURCE_DEFICIT,
                "resource.fuel",
                8000,
                9000,
                StrategicPlanningEnvelope.balanced(50L),
                List.of(),
                -1L,
                StrategicGoalOutcomeSignal.NONE,
                "fuel.report");
        FactionStrategicGoalPlanner.PlanningResult seeded = FactionStrategicGoalPlanner.review(
                actor,
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(feasible),
                StrategicPlanningEnvelope.balanced(50L),
                10L);
        String originalId = seeded.state().activeGoals().get(0).goalId();
        StrategicGoalCandidate failed = candidate(
                StrategicGoalType.STOCKPILE,
                InterestKind.RESOURCE_DEFICIT,
                "resource.fuel",
                8000,
                9000,
                StrategicPlanningEnvelope.balanced(50L),
                List.of(),
                -1L,
                StrategicGoalOutcomeSignal.FAILED,
                "fuel.execution.failed");

        FactionStrategicGoalPlanner.PlanningResult cancelled = FactionStrategicGoalPlanner.review(
                actor,
                seeded.state(),
                List.of(failed),
                StrategicPlanningEnvelope.balanced(50L),
                20L);
        StrategicGoalState cancelledGoal = cancelled.state().goals().get(0);
        assertEquals(Lifecycle.CANCELLED, cancelledGoal.lifecycle());
        assertEquals(StrategicPlanningEnvelope.balanced(5L), cancelledGoal.cancellationCost());
        assertEquals(44L, cancelledGoal.cooldownUntilTick());

        FactionStrategicGoalPlanner.PlanningResult blocked = FactionStrategicGoalPlanner.review(
                actor,
                cancelled.state(),
                List.of(feasible),
                StrategicPlanningEnvelope.balanced(50L),
                30L);
        assertTrue(blocked.state().openGoals().isEmpty());

        FactionStrategicGoalPlanner.PlanningResult reentered = FactionStrategicGoalPlanner.review(
                actor,
                blocked.state(),
                List.of(feasible),
                StrategicPlanningEnvelope.balanced(50L),
                44L);
        assertEquals(1, reentered.state().activeGoals().size());
        assertTrue(!originalId.equals(reentered.state().activeGoals().get(0).goalId()));
    }

    @Test
    void successAndExpiryAreDistinctTerminalLifecyclesWithoutCancellationPenalty() {
        FactionLivingActorState actor = FactionLivingActorState.initial("faction.alpha", 0L);
        StrategicGoalCandidate base = candidate(
                StrategicGoalType.EXPLORE,
                InterestKind.TERRITORIAL_OPPORTUNITY,
                "system.frontier",
                7000,
                9000,
                StrategicPlanningEnvelope.balanced(5L),
                List.of(),
                30L,
                StrategicGoalOutcomeSignal.NONE,
                "survey.start");
        FactionStrategicGoalPlanner.PlanningResult seeded = FactionStrategicGoalPlanner.review(
                actor,
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(base),
                StrategicPlanningEnvelope.balanced(10L),
                10L);
        StrategicGoalCandidate success = candidate(
                StrategicGoalType.EXPLORE,
                InterestKind.TERRITORIAL_OPPORTUNITY,
                "system.frontier",
                7000,
                9000,
                StrategicPlanningEnvelope.balanced(5L),
                List.of(),
                30L,
                StrategicGoalOutcomeSignal.SUCCEEDED,
                "survey.complete");
        FactionStrategicGoalPlanner.PlanningResult succeeded = FactionStrategicGoalPlanner.review(
                actor,
                seeded.state(),
                List.of(success),
                StrategicPlanningEnvelope.balanced(10L),
                20L);
        assertEquals(Lifecycle.SUCCEEDED, succeeded.state().goals().get(0).lifecycle());
        assertTrue(succeeded.cancellationCost().isZero());

        StrategicGoalCandidate expiring = candidate(
                StrategicGoalType.DEFEND,
                InterestKind.BORDER_SECURITY,
                "border.temp",
                6000,
                9000,
                StrategicPlanningEnvelope.balanced(5L),
                List.of(),
                15L,
                StrategicGoalOutcomeSignal.NONE,
                "border.temp");
        FactionStrategicGoalPlanner.PlanningResult expiringSeed = FactionStrategicGoalPlanner.review(
                actor,
                FactionStrategicIntentState.initial("faction.beta"),
                List.of(),
                StrategicPlanningEnvelope.balanced(10L),
                10L);
        // Use an actor with matching faction for the second independent scenario.
        FactionLivingActorState beta = FactionLivingActorState.initial("faction.beta", 0L);
        expiringSeed = FactionStrategicGoalPlanner.review(
                beta,
                FactionStrategicIntentState.initial("faction.beta"),
                List.of(expiring),
                StrategicPlanningEnvelope.balanced(10L),
                10L);
        FactionStrategicGoalPlanner.PlanningResult expired = FactionStrategicGoalPlanner.review(
                beta,
                expiringSeed.state(),
                List.of(expiring),
                StrategicPlanningEnvelope.balanced(10L),
                15L);
        assertEquals(Lifecycle.EXPIRED, expired.state().goals().get(0).lifecycle());
        assertTrue(expired.cancellationCost().isZero());
    }

    @Test
    void conservativeStage21ABridgeCoversFivePeacefulGoalFamiliesWithoutInventingTreatyPolicy() {
        FactionActorObservationSnapshot snapshot = new FactionActorObservationSnapshot(
                "faction.alpha",
                10L,
                List.of(
                        observation(Domain.ECONOMIC, InterestKind.RESOURCE_DEFICIT, "resource", 5000, "e1"),
                        observation(Domain.ECONOMIC, InterestKind.MARKET_ACCESS, "market", 5000, "e2"),
                        observation(Domain.ECONOMIC, InterestKind.ROUTE_EXPOSURE, "route", 5000, "e3")),
                List.of(observation(
                        Domain.TERRITORIAL,
                        InterestKind.TERRITORIAL_OPPORTUNITY,
                        "system.new",
                        5000,
                        "t1")),
                List.of(observation(
                        Domain.SECURITY,
                        InterestKind.BORDER_SECURITY,
                        "border",
                        5000,
                        "s1")),
                List.of(observation(
                        Domain.DIPLOMATIC,
                        InterestKind.TREATY_OBLIGATION,
                        "treaty",
                        9000,
                        "d1")));

        List<StrategicGoalCandidate> candidates = FactionStrategicGoalCandidateResolver.resolve(
                FactionInterestResolver.resolve(snapshot));

        assertEquals(5, candidates.size());
        assertEquals(
                List.of(
                        StrategicGoalType.SECURE_ROUTE,
                        StrategicGoalType.STOCKPILE,
                        StrategicGoalType.EXPLORE,
                        StrategicGoalType.DEFEND,
                        StrategicGoalType.OBTAIN_ACCESS).stream().sorted().toList(),
                candidates.stream().map(StrategicGoalCandidate::type).sorted().toList());
        assertTrue(candidates.stream().allMatch(candidate -> candidate.reviewCadenceTicks() > 0L));
    }

    private static StrategicGoalCandidate candidate(
            StrategicGoalType type,
            InterestKind kind,
            String target,
            int urgency,
            int feasibility,
            StrategicPlanningEnvelope budget,
            List<StrategicGoalBlocker> blockers,
            long expiresAtTick,
            StrategicGoalOutcomeSignal outcome,
            String provenance) {
        return new StrategicGoalCandidate(
                type,
                target,
                new StrategicGoalEvidence(
                        kind,
                        target,
                        urgency,
                        List.of(new ObservationEvidence(channel(kind), provenance, 1L, -1L))),
                urgency,
                feasibility,
                budget,
                blockers,
                expiresAtTick,
                1L,
                outcome);
    }

    private static ActorObservation observation(
            Domain domain,
            InterestKind kind,
            String target,
            int severity,
            String provenance) {
        return new ActorObservation(
                domain,
                kind,
                target,
                severity,
                new ObservationEvidence(channel(kind), provenance, 1L, -1L));
    }

    private static ObservationChannel channel(InterestKind kind) {
        return switch (kind) {
            case BORDER_SECURITY -> ObservationChannel.INTELLIGENCE_REPORT;
            case TERRITORIAL_OPPORTUNITY -> ObservationChannel.DISCOVERY_KNOWLEDGE;
            case TREATY_OBLIGATION -> ObservationChannel.DIPLOMATIC_REGISTRY;
            default -> ObservationChannel.ECONOMIC_LEDGER;
        };
    }
}
