package com.spacesim.campaign;

import com.spacesim.persistence.Stage21IGeneratedWorldRuntimeMigration;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignCoordinatorTest {
    @Test
    void newCampaignCapturesOneNativeFinalStage21Checkpoint() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);

        Stage21IGeneratedWorldRuntimePersistentState checkpoint = campaign.captureState();
        GeneratedCampaignCoordinator restored = GeneratedCampaignCoordinator.restore(checkpoint);

        assertFalse(checkpoint.migrationProvenance().migrated());
        assertEquals("stage21h.native", checkpoint.migrationProvenance().sourceFormat());
        assertEquals(checkpoint, restored.captureState());
        assertEquals(
                campaign.actors().capture().stream().map(state -> state.factionContentId()).toList(),
                restored.actors().capture().stream().map(state -> state.factionContentId()).toList());
    }

    @Test
    void legacyStage20LineageSurvivesRestoreAndSubsequentNativeSave() {
        GeneratedCampaignSession legacySession = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        Stage21IGeneratedWorldRuntimePersistentState migrated =
                Stage21IGeneratedWorldRuntimeMigration.migrate(legacySession.captureState());

        GeneratedCampaignCoordinator restored = GeneratedCampaignCoordinator.restore(migrated);
        Stage21IGeneratedWorldRuntimePersistentState recaptured = restored.captureState();

        assertTrue(recaptured.migrationProvenance().migrated());
        assertEquals(migrated.migrationProvenance(), recaptured.migrationProvenance());
        assertEquals(migrated.stage21HRuntime(), recaptured.stage21HRuntime());
    }

    @Test
    void finalCodecRoundTripPreservesEveryComposedAuthoritySnapshot() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        campaign.actors().capture().forEach(state -> campaign.actors().setCommitmentUntilTick(
                state.factionContentId(),
                state.nextReviewTick() + 17L));
        Stage21IGeneratedWorldRuntimePersistentState checkpoint = campaign.captureState();

        byte[] bytes = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(checkpoint);
        Stage21IGeneratedWorldRuntimePersistentState decoded =
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(bytes);
        GeneratedCampaignCoordinator restored = GeneratedCampaignCoordinator.restore(decoded);

        assertEquals(checkpoint, decoded);
        assertEquals(checkpoint, restored.captureState());
        assertEquals(campaign.strategicIntents(), restored.strategicIntents());
        assertEquals(campaign.diplomacy(), restored.diplomacy());
        assertEquals(campaign.warfare(), restored.warfare());
        assertEquals(campaign.commands(), restored.commands());
        assertEquals(campaign.operations(), restored.operations());
        assertEquals(campaign.transitions(), restored.transitions());
        assertEquals(campaign.recovery(), restored.recovery());
        assertEquals(campaign.npcMissions(), restored.npcMissions());
    }

    @Test
    void nativeSaveRestoreContinuesAtEightTimesSpeedWithoutIdentityOrStateDivergence() {
        GeneratedCampaignCoordinator continuous = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        continuous.setTimeScale(8d);
        continuous.advanceFrame(0.05f);

        Stage21IGeneratedWorldRuntimePersistentState checkpoint = continuous.captureState();
        byte[] payload = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(checkpoint);
        GeneratedCampaignCoordinator resumed = GeneratedCampaignCoordinator.restore(
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(payload));

        assertEquals(
                continuous.actors().capture().stream().map(state -> state.factionContentId()).toList(),
                resumed.actors().capture().stream().map(state -> state.factionContentId()).toList());
        assertEquals(continuous.captureState(), resumed.captureState());

        var continuousReport = continuous.advanceFrame(0.05f);
        var resumedReport = resumed.advanceFrame(0.05f);

        assertEquals(continuousReport.fixedTicks(), resumedReport.fixedTicks());
        assertEquals(continuousReport.autonomousDecisions(), resumedReport.autonomousDecisions());
        assertEquals(continuous.captureState(), resumed.captureState());
    }

    @Test
    void malformedNativeCheckpointFailsClosedWithoutMutatingLiveCampaign() {
        GeneratedCampaignCoordinator live = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        live.advanceFrame(0.1f);
        Stage21IGeneratedWorldRuntimePersistentState before = live.captureState();
        byte[] encoded = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(before);
        byte[] malformed = Arrays.copyOf(encoded, encoded.length + 1);
        malformed[malformed.length - 1] = 0x5a;

        assertThrows(
                IllegalArgumentException.class,
                () -> Stage21IGeneratedWorldRuntimePersistenceCodec.decode(malformed));
        assertEquals(before, live.captureState(),
                "a rejected checkpoint must not mutate the running campaign authority");
    }
}
