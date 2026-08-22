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
    void goalIdentityEvidenceBudgetLifecycleAndCooldownSurviveRoundTrip() {
        StrategicGoalState active = goal(
                "faction.alpha:strategic-goal:1",
                "faction.alpha",
                StrategicGoalType.STOCKPILE,
                InterestKind.RESOURCE_DEFICIT,
                "resource.water",
                Lifecycle.ACTIVE,
                40L,
                0L,
                0L);
        StrategicGoalState cancelled = goal(
                "faction.alpha:strategic-goal:2",
                "faction.alpha",
                StrategicGoalType.SECURE_ROUTE,
                InterestKind.ROUTE_EXPOSURE,
                "route.alpha-beta",
                Lifecycle.CANCELLED,
                0L,
                60L,
                4L);
        FactionStrategicIntentState state = new FactionStrategicIntentState(
                "faction.alpha",
                3L,
                List.of(cancelled, active));

        byte[] encoded = FactionStrategicIntentStateCodec.encode(List.of(state));
        List<FactionStrategicIntentState> decoded = FactionStrategicIntentStateCodec.decode(encoded);

        assertEquals(List.of(state), decoded);
        assertArrayEquals(encoded, FactionStrategicIntentStateCodec.encode(decoded));
        assertEquals("faction.alpha:strategic-goal:1", decoded.get(0).goals().get(0).goalId());
        assertEquals("ledger.source", decoded.get(0).goals().get(0).sourceEvidence().provenance().get(0).provenanceId());
    }

    @Test
    void codecRejectsDuplicateFactionAggregates() {
        FactionStrategicIntentState state = FactionStrategicIntentState.initial("faction.alpha");
        assertThrows(
                IllegalArgumentException.class,
                () -> FactionStrategicIntentStateCodec.encode(List.of(state, state)));
    }

    private static StrategicGoalState goal(
            String goalId,
            String factionId,
            StrategicGoalType type,
            InterestKind kind,
            String target,
            Lifecycle lifecycle,
            long allocation,
            long cooldown,
            long cancellationCost) {
        StrategicGoalEvidence evidence = new StrategicGoalEvidence(
                kind,
                target,
                7000,
                List.of(new ObservationEvidence(
                        ObservationChannel.ECONOMIC_LEDGER,
                        "ledger.source",
                        10L,
                        -1L)));
        return new StrategicGoalState(
                goalId,
                factionId,
                type,
                target,
                evidence,
                7000,
                8000,
                50L,
                allocation,
                lifecycle,
                10L,
                lifecycle == Lifecycle.ACTIVE ? 20L : 40L,
                cooldown,
                cancellationCost);
    }
}
