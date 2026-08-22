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
        FactionStrategicIntentState initial = FactionStrategicIntentState.initial("faction.alpha");
        StrategicGoalCandidate candidate = candidate(
                StrategicGoalType.STOCKPILE,
                InterestKind.RESOURCE_DEFICIT,
                "resource.water",
                7000,
                9000,
                40L,
                "ledger.water");

        FactionStrategicGoalPlanner.PlanningResult first = FactionStrategicGoalPlanner.review(
                actor, initial, List.of(candidate), 40L, 10L);
        FactionStrategicGoalPlanner.PlanningResult second = FactionStrategicGoalPlanner.review(
                actor, first.state(), List.of(candidate), 40L, 20L);

        assertEquals(1, first.state().activeGoals().size());
        assertEquals(
                first.state().activeGoals().get(0).goalId(),
                second.state().activeGoals().get(0).goalId());
        assertEquals(40L, second.allocatedBudgetUnits());
        assertEquals("ledger.water", second.projections().get(0).provenanceIds().get(0));
        assertTrue(second.projections().get(0).explanationCode().contains("stockpile"));
    }

    @Test
    void budgetArbitrationAndHysteresisPreferCommittedExistingIntentOverSmallPriorityLead() {
        FactionLivingActorState actor = FactionLivingActorState.initial("faction.alpha", 0L)
                .withCommitmentUntilTick(100L);
        StrategicGoalCandidate oldCandidate = candidate(
                StrategicGoalType.DEFEND,
                InterestKind.BORDER_SECURITY,
                "system.home-border",
                6000,
                10_000,
                10L,
                "border.old");
        FactionStrategicGoalPlanner.PlanningResult seeded = FactionStrategicGoalPlanner.review(
                actor,
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(oldCandidate),
                10L,
                10L);
        StrategicGoalCandidate challenger = candidate(
                StrategicGoalType.EXPLORE,
                InterestKind.TERRITORIAL_OPPORTUNITY,
                "system.frontier",
                6500,
                10_000,
                10L,
                "survey.frontier");

        FactionStrategicGoalPlanner.PlanningResult reviewed = FactionStrategicGoalPlanner.review(
                actor,
                seeded.state(),
                List.of(challenger, oldCandidate),
                10L,
                20L);

        assertEquals(1, reviewed.state().activeGoals().size());
        assertEquals(StrategicGoalType.DEFEND, reviewed.state().activeGoals().get(0).type());
        assertEquals(10L, reviewed.allocatedBudgetUnits());
    }

    @Test
    void infeasibleGoalIsCancelledWithCostAndCooldownBeforeFreshIdentityCanBeCreated() {
        FactionLivingActorState actor = FactionLivingActorState.initial("faction.alpha", 0L);
        StrategicGoalCandidate feasible = candidate(
                StrategicGoalType.SECURE_ROUTE,
                InterestKind.ROUTE_EXPOSURE,
                "route.alpha-beta",
                8000,
                8000,
                50L,
                "route.report");
        FactionStrategicGoalPlanner.PlanningResult seeded = FactionStrategicGoalPlanner.review(
                actor,
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(feasible),
                50L,
                10L);
        String originalId = seeded.state().activeGoals().get(0).goalId();
        StrategicGoalCandidate infeasible = candidate(
                StrategicGoalType.SECURE_ROUTE,
                InterestKind.ROUTE_EXPOSURE,
                "route.alpha-beta",
                8000,
                1000,
                50L,
                "route.report.new");

        FactionStrategicGoalPlanner.PlanningResult cancelled = FactionStrategicGoalPlanner.review(
                actor, seeded.state(), List.of(infeasible), 50L, 20L);
        StrategicGoalState cancelledGoal = cancelled.state().goals().get(0);
        assertEquals(Lifecycle.CANCELLED, cancelledGoal.lifecycle());
        assertEquals(5L, cancelledGoal.cancellationCostUnits());
        assertEquals(44L, cancelledGoal.cooldownUntilTick());

        FactionStrategicGoalPlanner.PlanningResult blocked = FactionStrategicGoalPlanner.review(
                actor, cancelled.state(), List.of(feasible), 50L, 30L);
        assertTrue(blocked.state().activeGoals().isEmpty());

        FactionStrategicGoalPlanner.PlanningResult reentered = FactionStrategicGoalPlanner.review(
                actor, blocked.state(), List.of(feasible), 50L, 44L);
        assertEquals(1, reentered.state().activeGoals().size());
        assertTrue(!originalId.equals(reentered.state().activeGoals().get(0).goalId()));
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
    }

    private static StrategicGoalCandidate candidate(
            StrategicGoalType type,
            InterestKind kind,
            String target,
            int urgency,
            int feasibility,
            long budget,
            String provenance) {
        return new StrategicGoalCandidate(
                type,
                target,
                new StrategicGoalEvidence(
                        kind,
                        target,
                        urgency,
                        List.of(new ObservationEvidence(
                                channel(kind),
                                provenance,
                                1L,
                                -1L))),
                urgency,
                feasibility,
                budget);
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
