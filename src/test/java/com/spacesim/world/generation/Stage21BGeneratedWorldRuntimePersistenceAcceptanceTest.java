package com.spacesim.world.generation;

import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FactionStrategicGoalPlanner;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.StrategicGoalCandidate;
import com.spacesim.world.StrategicGoalEvidence;
import com.spacesim.world.StrategicGoalOutcomeSignal;
import com.spacesim.world.StrategicGoalType;
import com.spacesim.world.StrategicPlanningEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage21BGeneratedWorldRuntimePersistenceAcceptanceTest {

    @Test
    void strategicGoalRoundTripsWithoutRewritingEmbeddedStage21AAuthority() {
        var stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        String factionId = stage20.captureState().worldState().factions().get(0).factionContentId();
        long nowTick = stage20.world().getAuthoritativeWorldTick();
        var stage21a = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(
                stage20,
                List.of(factionId),
                30L);
        var stage21aState = stage21a.captureState();

        StrategicGoalCandidate candidate = new StrategicGoalCandidate(
                StrategicGoalType.STOCKPILE,
                "resource.propellant",
                new StrategicGoalEvidence(
                        InterestKind.RESOURCE_DEFICIT,
                        "resource.propellant",
                        8000,
                        List.of(new ObservationEvidence(
                                ObservationChannel.ECONOMIC_LEDGER,
                                "ledger.propellant.shortage",
                                nowTick,
                                -1L))),
                8000,
                9000,
                new StrategicPlanningEnvelope(20L, 30L, 10L, 0L),
                List.of(),
                -1L,
                24L,
                StrategicGoalOutcomeSignal.NONE);
        var planned = FactionStrategicGoalPlanner.review(
                stage21aState.livingActors().get(0),
                FactionStrategicIntentState.initial(factionId),
                List.of(candidate),
                StrategicPlanningEnvelope.balanced(100L),
                nowTick);

        Stage21BGeneratedWorldRuntimePersistentState stage21b =
                new Stage21BGeneratedWorldRuntimePersistentState(
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21aState,
                        List.of(planned.state()));
        byte[] encoded = Stage21BGeneratedWorldRuntimePersistenceCodec.encode(stage21b);
        Stage21BGeneratedWorldRuntimePersistentState decoded =
                Stage21BGeneratedWorldRuntimePersistenceCodec.decode(encoded);

        assertArrayEquals(encoded, Stage21BGeneratedWorldRuntimePersistenceCodec.encode(decoded));
        assertArrayEquals(
                Stage21AGeneratedWorldRuntimePersistenceCodec.encode(stage21aState),
                Stage21AGeneratedWorldRuntimePersistenceCodec.encode(decoded.stage21ARuntime()));
        assertEquals(
                planned.state().activeGoals().get(0).goalId(),
                decoded.strategicIntents().get(0).activeGoals().get(0).goalId());
        assertEquals(
                new StrategicPlanningEnvelope(20L, 30L, 10L, 0L),
                decoded.strategicIntents().get(0).activeGoals().get(0).allocatedBudget());
        assertEquals(
                "ledger.propellant.shortage",
                decoded.strategicIntents().get(0).activeGoals().get(0)
                        .sourceEvidence().provenance().get(0).provenanceId());
    }
}
