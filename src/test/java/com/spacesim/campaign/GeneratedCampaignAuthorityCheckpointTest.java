package com.spacesim.campaign;

import com.spacesim.campaign.GeneratedCampaignAuthorityCheckpoint.RestoredAuthorities;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedCampaignAuthorityCheckpointTest {
    private static final long ROOT_SEED = 0x227DCA11L;

    @Test
    void migratedStage20CheckpointRoundTripsThroughCompleteStage21AuthorityChain() {
        GeneratedCampaignSession original = GeneratedCampaignSession.create(ROOT_SEED);
        byte[] stage20Payload = Stage20GeneratedWorldRuntimePersistenceCodec.encode(original.captureState());
        Stage21IGeneratedWorldRuntimePersistentState migrated =
                Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(stage20Payload);

        RestoredAuthorities restored = GeneratedCampaignAuthorityCheckpoint.restore(migrated);
        Stage21IGeneratedWorldRuntimePersistentState recaptured =
                GeneratedCampaignAuthorityCheckpoint.capture(
                        restored.session(),
                        restored.actors(),
                        restored.strategicIntents(),
                        restored.diplomacy(),
                        restored.warfare(),
                        restored.commands(),
                        restored.operations(),
                        restored.transitions(),
                        restored.recovery(),
                        restored.npcMissions());

        assertEquals(ROOT_SEED, restored.session().rootSeed());
        assertEquals(migrated.stage21HRuntime(), recaptured.stage21HRuntime());
        assertEquals(
                Stage21IGeneratedWorldRuntimePersistentState.authoritativeWorldTick(migrated.stage21HRuntime()),
                Stage21IGeneratedWorldRuntimePersistentState.authoritativeWorldTick(recaptured.stage21HRuntime()));
    }
}
