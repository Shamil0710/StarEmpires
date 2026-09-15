package com.spacesim.campaign;

import com.spacesim.persistence.Stage21IGeneratedWorldRuntimeMigration;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignCoordinatorTest {
    @Test
    void newCampaignCapturesOneNativeFinalStage21Checkpoint() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(22_700_101L);

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
        GeneratedCampaignSession legacySession = GeneratedCampaignSession.create(22_700_102L);
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
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(22_700_103L);
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
}
