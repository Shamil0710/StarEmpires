package com.spacesim.world;

import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.RelationEvent;
import com.spacesim.world.DiplomaticLifecycleState.RelationFactor;
import com.spacesim.world.DiplomaticLifecycleState.RelationMemory;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicDiplomaticProposalPlannerTest {
    private static final String ACTOR = "faction.actor";
    private static final String RECIPIENT = "faction.recipient";

    @Test
    void activeStage21BGoalAndRememberedRelationDeterministicallyProduceProposal() {
        StrategicGoalState goal = activeGoal(StrategicGoalType.OBTAIN_ACCESS, InterestKind.MARKET_ACCESS, "market.alpha");
        FactionStrategicIntentState intent = new FactionStrategicIntentState(ACTOR, 2L, List.of(goal));
        RelationMemory memory = relation(30);

        var first = StrategicDiplomaticProposalPlanner.plan(intent, goal.goalId(), RECIPIENT, memory, 100L)
                .orElseThrow();
        var second = StrategicDiplomaticProposalPlanner.plan(intent, goal.goalId(), RECIPIENT, memory, 100L)
                .orElseThrow();

        assertEquals(first, second);
        assertEquals(goal.goalId(), first.sourceGoalId());
        assertEquals(ACTOR, first.proposerFactionId());
        assertEquals(RECIPIENT, first.recipientFactionId());
        assertEquals(ProposalKind.TRADE, first.kind());
        assertEquals(goal.targetId(), first.issueId());
        assertEquals(1, first.demands().size());
        assertEquals(TermKind.MARKET_ACCESS, first.demands().get(0).kind());
    }

    @Test
    void coerciveGoalUsesRememberedHostilityRatherThanRandomWarSelection() {
        StrategicGoalState goal = activeGoal(StrategicGoalType.COERCE, InterestKind.MARKET_ACCESS, "market.alpha");
        FactionStrategicIntentState intent = new FactionStrategicIntentState(ACTOR, 2L, List.of(goal));

        var request = StrategicDiplomaticProposalPlanner.plan(intent, goal.goalId(), RECIPIENT, relation(-60), 100L)
                .orElseThrow();

        assertEquals(ProposalKind.ULTIMATUM, request.kind());
        assertTrue(request.demands().isEmpty());
    }

    @Test
    void plannerRejectsUnownedGoalOrMismatchedActorMemory() {
        StrategicGoalState goal = activeGoal(StrategicGoalType.OBTAIN_ACCESS, InterestKind.MARKET_ACCESS, "market.alpha");
        FactionStrategicIntentState intent = new FactionStrategicIntentState(ACTOR, 2L, List.of(goal));

        assertThrows(IllegalArgumentException.class, () -> StrategicDiplomaticProposalPlanner.plan(
                intent, "goal.not-owned", RECIPIENT, relation(0), 100L));
        assertThrows(IllegalArgumentException.class, () -> StrategicDiplomaticProposalPlanner.plan(
                intent,
                goal.goalId(),
                RECIPIENT,
                new RelationMemory(RECIPIENT, ACTOR, List.of()),
                100L));
    }

    @Test
    void nonDiplomaticActiveGoalDoesNotInventAProposal() {
        StrategicGoalState goal = activeGoal(
                StrategicGoalType.STOCKPILE,
                InterestKind.RESOURCE_DEFICIT,
                "resource.fuel");
        FactionStrategicIntentState intent = new FactionStrategicIntentState(ACTOR, 2L, List.of(goal));

        assertTrue(StrategicDiplomaticProposalPlanner.plan(
                intent, goal.goalId(), RECIPIENT, relation(0), 100L).isEmpty());
    }

    private static StrategicGoalState activeGoal(
            StrategicGoalType type,
            InterestKind interestKind,
            String targetId) {
        StrategicGoalEvidence evidence = new StrategicGoalEvidence(
                interestKind,
                targetId,
                8_000,
                List.of(new ObservationEvidence(
                        ObservationChannel.DIPLOMATIC_REGISTRY,
                        "evidence." + type.wireId(),
                        0L,
                        -1L)));
        StrategicPlanningEnvelope budget = StrategicPlanningEnvelope.balanced(1L);
        return new StrategicGoalState(
                ACTOR + ":strategic-goal:1",
                ACTOR,
                type,
                targetId,
                evidence,
                8_000,
                8_000,
                8_000,
                5_000,
                budget,
                budget,
                List.of(),
                StrategicGoalState.Lifecycle.ACTIVE,
                0L,
                0L,
                10L,
                -1L,
                0L,
                StrategicPlanningEnvelope.ZERO,
                StrategicGoalOutcomeSignal.NONE);
    }

    private static RelationMemory relation(int impact) {
        return new RelationMemory(
                ACTOR,
                RECIPIENT,
                impact == 0
                        ? List.of()
                        : List.of(new RelationEvent(
                                "relation." + impact,
                                RelationFactor.REMEMBERED_ACTION,
                                impact,
                                0L,
                                "actor-known-history")));
    }
}
