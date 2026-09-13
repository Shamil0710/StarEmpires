package com.spacesim.campaign;

import com.spacesim.campaign.GeneratedCampaignAuthorityCheckpoint.RestoredAuthorities;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.StrategicOperationState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

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
    void mutatedLateStageAuthoritiesAndStableIdentitiesSurviveNativeCheckpointRestore() {
        GeneratedCampaignSession original = GeneratedCampaignSession.create(ROOT_SEED);
        RestoredAuthorities baseline = GeneratedCampaignAuthorityCheckpoint.restore(
                Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(
                        Stage20GeneratedWorldRuntimePersistenceCodec.encode(original.captureState())));

        List<FactionStrategicIntentState> mutatedIntents = baseline.strategicIntents().stream()
                .map(intent -> new FactionStrategicIntentState(
                        intent.factionContentId(),
                        Math.addExact(intent.nextGoalSequence(), 5L),
                        intent.lastActorReviewCount(),
                        intent.goals()))
                .toList();
        StrategicOperationState mutatedOperations = new StrategicOperationState(
                Math.addExact(baseline.operations().nextOperationId(), 7L),
                baseline.operations().operations());
        SettlementRecoveryState recovery = baseline.recovery();
        SettlementRecoveryState mutatedRecovery = new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                recovery.simulationTick(),
                Math.addExact(recovery.nextSettlementId(), 11L),
                Math.addExact(recovery.nextReplacementDemandId(), 13L),
                recovery.settlements(),
                recovery.payments(),
                recovery.demobilizations(),
                recovery.losses(),
                recovery.replacementDemands());
        Stage21HNpcMissionState npcMissions = baseline.npcMissions();
        Stage21HNpcMissionState mutatedNpcMissions = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                npcMissions.simulationTick(),
                Math.addExact(npcMissions.nextMissionSequence(), 17L),
                npcMissions.npcs(),
                npcMissions.missions(),
                npcMissions.reputations(),
                npcMissions.storyChains());

        List<String> factionIdsBeforeSave = mutatedIntents.stream()
                .map(FactionStrategicIntentState::factionContentId)
                .toList();
        var fleetIdsBeforeSave = baseline.session().captureState().worldState().fleets().stream()
                .map(placement -> placement.id())
                .toList();
        Stage21IGeneratedWorldRuntimePersistentState mutatedCheckpoint =
                GeneratedCampaignAuthorityCheckpoint.capture(
                        baseline.session(),
                        baseline.actors(),
                        mutatedIntents,
                        baseline.diplomacy(),
                        baseline.warfare(),
                        baseline.commands(),
                        mutatedOperations,
                        baseline.transitions(),
                        mutatedRecovery,
                        mutatedNpcMissions);

        RestoredAuthorities restored = GeneratedCampaignAuthorityCheckpoint.restore(
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(
                        Stage21IGeneratedWorldRuntimePersistenceCodec.encode(mutatedCheckpoint)));

        assertEquals(mutatedIntents, restored.strategicIntents());
        assertEquals(mutatedOperations, restored.operations());
        assertEquals(mutatedRecovery, restored.recovery());
        assertEquals(mutatedNpcMissions, restored.npcMissions());
        assertEquals(
                factionIdsBeforeSave,
                restored.strategicIntents().stream()
                        .map(FactionStrategicIntentState::factionContentId)
                        .toList());
        assertEquals(
                fleetIdsBeforeSave,
                restored.session().captureState().worldState().fleets().stream()
                        .map(placement -> placement.id())
                        .toList());
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
