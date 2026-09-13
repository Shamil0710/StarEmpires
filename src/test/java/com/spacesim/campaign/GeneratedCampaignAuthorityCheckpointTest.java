package com.spacesim.campaign;

import com.spacesim.campaign.GeneratedCampaignAuthorityCheckpoint.RestoredAuthorities;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void nativeStage21CheckpointRoundTripsWithoutChangingAuthoritySnapshotsOrStableIds() {
        GeneratedCampaignSession original = GeneratedCampaignSession.create(ROOT_SEED);
        Stage21IGeneratedWorldRuntimePersistentState migrated =
                Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(
                        Stage20GeneratedWorldRuntimePersistenceCodec.encode(original.captureState()));
        RestoredAuthorities beforeSave = GeneratedCampaignAuthorityCheckpoint.restore(migrated);
        Stage21IGeneratedWorldRuntimePersistentState nativeCheckpoint =
                GeneratedCampaignAuthorityCheckpoint.capture(
                        beforeSave.session(),
                        beforeSave.actors(),
                        beforeSave.strategicIntents(),
                        beforeSave.diplomacy(),
                        beforeSave.warfare(),
                        beforeSave.commands(),
                        beforeSave.operations(),
                        beforeSave.transitions(),
                        beforeSave.recovery(),
                        beforeSave.npcMissions());

        byte[] encoded = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(nativeCheckpoint);
        Stage21IGeneratedWorldRuntimePersistentState decoded =
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(encoded);
        RestoredAuthorities afterLoad = GeneratedCampaignAuthorityCheckpoint.restore(decoded);

        assertEquals(nativeCheckpoint, decoded);
        assertEquals(beforeSave.session().captureState(), afterLoad.session().captureState());
        assertEquals(beforeSave.actors().capture(), afterLoad.actors().capture());
        assertEquals(beforeSave.strategicIntents(), afterLoad.strategicIntents());
        assertEquals(beforeSave.diplomacy(), afterLoad.diplomacy());
        assertEquals(beforeSave.warfare(), afterLoad.warfare());
        assertEquals(beforeSave.commands(), afterLoad.commands());
        assertEquals(beforeSave.operations(), afterLoad.operations());
        assertEquals(beforeSave.transitions(), afterLoad.transitions());
        assertEquals(beforeSave.recovery(), afterLoad.recovery());
        assertEquals(beforeSave.npcMissions(), afterLoad.npcMissions());
    }

    @Test
    void nativeStage21CheckpointFailsClosedOnCorruptUnsupportedTruncatedOrTrailingPayload() {
        GeneratedCampaignSession original = GeneratedCampaignSession.create(ROOT_SEED);
        Stage21IGeneratedWorldRuntimePersistentState migrated =
                Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(
                        Stage20GeneratedWorldRuntimePersistenceCodec.encode(original.captureState()));
        byte[] encoded = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(migrated);

        byte[] corruptMagic = encoded.clone();
        corruptMagic[0] ^= 0x01;

        byte[] unsupportedFileVersion = encoded.clone();
        unsupportedFileVersion[4] = 0;
        unsupportedFileVersion[5] = 0;
        unsupportedFileVersion[6] = 0;
        unsupportedFileVersion[7] = 2;

        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        trailing[trailing.length - 1] = 1;

        assertThrows(IllegalArgumentException.class,
                () -> Stage21IGeneratedWorldRuntimePersistenceCodec.decode(corruptMagic));
        assertThrows(IllegalArgumentException.class,
                () -> Stage21IGeneratedWorldRuntimePersistenceCodec.decode(unsupportedFileVersion));
        assertThrows(IllegalArgumentException.class,
                () -> Stage21IGeneratedWorldRuntimePersistenceCodec.decode(truncated));
        assertThrows(IllegalArgumentException.class,
                () -> Stage21IGeneratedWorldRuntimePersistenceCodec.decode(trailing));
    }
}
