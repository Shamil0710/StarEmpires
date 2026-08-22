package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.StrategicGoalState.Lifecycle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactionStrategicIntentStateCodecTest {

    @Test
    void identityEvidenceMultidimensionalBudgetBlockersAndLifecycleSurviveRoundTrip() {
        StrategicGoalState active = goal(
                "faction.alpha:strategic-goal:1", StrategicGoalType.STOCKPILE, InterestKind.RESOURCE_DEFICIT,
                "resource.water", Lifecycle.ACTIVE, StrategicPlanningEnvelope.balanced(40L), List.of(),
                21L, 0L, StrategicPlanningEnvelope.ZERO, StrategicGoalOutcomeSignal.NONE);
        StrategicGoalState stalled = goal(
                "faction.alpha:strategic-goal:2", StrategicGoalType.SECURE_ROUTE, InterestKind.ROUTE_EXPOSURE,
                "route.alpha-beta", Lifecycle.STALLED, StrategicPlanningEnvelope.ZERO,
                List.of(StrategicGoalBlocker.LOGISTICS_CAPACITY), 41L, 0L,
                StrategicPlanningEnvelope.ZERO, StrategicGoalOutcomeSignal.NONE);
        StrategicGoalState cancelled = goal(
                "faction.alpha:strategic-goal:3", StrategicGoalType.OBTAIN_ACCESS, InterestKind.MARKET_ACCESS,
                "market.beta", Lifecycle.CANCELLED, StrategicPlanningEnvelope.ZERO, List.of(), 0L, 64L,
                new StrategicPlanningEnvelope(4L, 2L, 0L, 0L), StrategicGoalOutcomeSignal.FAILED);
        FactionStrategicIntentState state = new FactionStrategicIntentState(
                "faction.alpha", 4L, List.of(cancelled, stalled, active));

        byte[] encoded = FactionStrategicIntentStateCodec.encode(List.of(state));
        List<FactionStrategicIntentState> decoded = FactionStrategicIntentStateCodec.decode(encoded);

        assertEquals(List.of(state), decoded);
        assertArrayEquals(encoded, FactionStrategicIntentStateCodec.encode(decoded));
        assertEquals("faction.alpha:strategic-goal:1", decoded.get(0).goals().get(0).goalId());
        assertEquals("ledger.source", decoded.get(0).goals().get(0).sourceEvidence().provenance().get(0).provenanceId());
        assertEquals(List.of(StrategicGoalBlocker.LOGISTICS_CAPACITY), decoded.get(0).goals().get(1).blockers());
    }

    @Test
    void codecRejectsDuplicateFactionAggregates() {
        FactionStrategicIntentState state = FactionStrategicIntentState.initial("faction.alpha");
        assertThrows(IllegalArgumentException.class,
                () -> FactionStrategicIntentStateCodec.encode(List.of(state, state)));
    }

    private static StrategicGoalState goal(
            String goalId,
            StrategicGoalType type,
            InterestKind kind,
            String target,
            Lifecycle lifecycle,
            StrategicPlanningEnvelope allocation,
            List<StrategicGoalBlocker> blockers,
            long nextReview,
            long cooldown,
            StrategicPlanningEnvelope cancellationCost,
            StrategicGoalOutcomeSignal outcome) {
        StrategicGoalEvidence evidence = new StrategicGoalEvidence(
                kind,
                target,
                7000,
                List.of(new ObservationEvidence(ObservationChannel.ECONOMIC_LEDGER, "ledger.source", 10L, -1L)));
        return new StrategicGoalState(
                goalId, "faction.alpha", type, target, evidence, 7000, 8000,
                StrategicPlanningEnvelope.balanced(50L), allocation, blockers, lifecycle,
                10L, lifecycle == Lifecycle.ACTIVE ? 20L : 40L, nextReview, -1L, cooldown,
                cancellationCost, outcome);
    }
}
