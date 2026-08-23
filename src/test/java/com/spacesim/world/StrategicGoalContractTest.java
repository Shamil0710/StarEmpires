package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.StrategicGoalState.Lifecycle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicGoalContractTest {
    @Test
    void costCeilingStallsGoalWithoutMutatingCapacityAuthority() {
        StrategicGoalCandidate candidate = candidate(
                StrategicPlanningEnvelope.balanced(20L),
                StrategicPlanningEnvelope.balanced(10L));

        var result = FactionStrategicGoalPlanner.review(
                FactionLivingActorState.initial("faction.alpha", 0L),
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(candidate),
                StrategicPlanningEnvelope.balanced(100L),
                10L);

        StrategicGoalState goal = result.state().openGoals().get(0);
        assertEquals(Lifecycle.STALLED, goal.lifecycle());
        assertEquals(List.of(StrategicGoalBlocker.COST_CEILING), goal.blockers());
        assertTrue(result.allocatedBudget().isZero());
    }

    @Test
    void explicitGoalContractSurvivesStrategicIntentRoundTrip() {
        StrategicGoalCandidate candidate = candidate(
                StrategicPlanningEnvelope.balanced(8L),
                StrategicPlanningEnvelope.balanced(12L));
        var planned = FactionStrategicGoalPlanner.review(
                FactionLivingActorState.initial("faction.alpha", 0L),
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(candidate),
                StrategicPlanningEnvelope.balanced(20L),
                10L);

        byte[] encoded = FactionStrategicIntentStateCodec.encode(List.of(planned.state()));
        StrategicGoalState restored = FactionStrategicIntentStateCodec.decode(encoded)
                .get(0).goals().get(0);

        assertEquals(candidate.costCeiling(), restored.costCeiling());
        assertEquals(candidate.successConditions(), restored.successConditions());
        assertEquals(candidate.failureConditions(), restored.failureConditions());
    }

    private static StrategicGoalCandidate candidate(
            StrategicPlanningEnvelope requested,
            StrategicPlanningEnvelope ceiling) {
        String target = "border:alpha";
        StrategicGoalEvidence evidence = new StrategicGoalEvidence(
                InterestKind.BORDER_SECURITY,
                target,
                8_000,
                List.of(new ObservationEvidence(
                        ObservationChannel.INTELLIGENCE_REPORT,
                        "intel:border-alpha",
                        1L,
                        -1L)));
        return new StrategicGoalCandidate(
                StrategicGoalType.DEFEND,
                target,
                evidence,
                8_000,
                9_000,
                9_000,
                10_000,
                requested,
                ceiling,
                List.of(new StrategicGoalCondition("border-held", target)),
                List.of(new StrategicGoalCondition("border-lost", target)),
                List.of(),
                -1L,
                8L,
                StrategicGoalOutcomeSignal.NONE);
    }
}
