package com.spacesim.campaign;

import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignCoordinatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void nativeCoordinatorRoundTripPreservesCompleteAuthorityChainAndStableWorldIdentity() throws Exception {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        campaign.session().setTimeScale(4d);
        campaign.session().advanceFrame(0.1f);

        var before = campaign.captureState();
        var fleetIdsBefore = campaign.session().captureState().worldState().fleets().stream()
                .map(placement -> placement.id())
                .toList();
        Path save = temporaryDirectory.resolve("campaign.s21i");

        Stage21IGeneratedWorldRuntimePersistenceCodec.write(save, before);
        GeneratedCampaignCoordinator restored = GeneratedCampaignCoordinator.restore(
                Stage21IGeneratedWorldRuntimePersistenceCodec.read(save));
        var after = restored.captureState();

        assertEquals(before, after);
        assertEquals(campaign.encode(), restored.encode());
        assertEquals(
                fleetIdsBefore,
                restored.session().captureState().worldState().fleets().stream()
                        .map(placement -> placement.id())
                        .toList());
    }

    @Test
    void legacyStage20CheckpointMigratesThroughTheSameProductionCompositionPath() {
        GeneratedCampaignSession legacySession = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        legacySession.setTimeScale(2d);
        legacySession.advanceFrame(0.1f);
        var legacyState = legacySession.captureState();
        byte[] legacyBytes = Stage20GeneratedWorldRuntimePersistenceCodec.encode(legacyState);

        GeneratedCampaignCoordinator migrated = GeneratedCampaignCoordinator.decodeOrMigrate(legacyBytes);

        assertEquals(legacyState, migrated.session().captureState());
        assertFalse(migrated.authorities().strategicIntents().isEmpty());
        assertEquals(
                migrated.authorities().strategicIntents().size(),
                migrated.authorities().actors().capture().size());
    }

    @Test
    void productionClientOrchestrationSmokeCreatesViewDispatchesWorldCommandAndContinuesAfterSaveLoad()
            throws Exception {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        GeneratedWorldUiModel initialModel = new GeneratedWorldUiModel(
                campaign.session().rootSeed(), campaign.session().runtime(), campaign.session().content());
        var initialView = initialModel.capture();
        assertFalse(initialView.galaxy().systems().isEmpty());
        assertFalse(initialView.localObjects().isEmpty());

        StarSystemId initialSystem = campaign.session().runtime().world().getActiveSystemId();
        StarSystemId targetSystem = campaign.session().runtime().world().getTopology().systems().stream()
                .map(system -> system.id())
                .filter(id -> !id.equals(initialSystem))
                .findFirst()
                .orElseThrow();
        campaign.session().runtime().world().activateSystem(targetSystem);
        campaign.session().setPaused(false);
        campaign.session().setTimeScale(4d);
        var advance = campaign.session().advanceFrame(0.1f);
        assertTrue(advance.fixedTicks() > 0L);
        assertEquals(targetSystem, campaign.session().runtime().world().getActiveSystemId());

        Path save = temporaryDirectory.resolve("production-client-smoke.s21i");
        var beforeReload = campaign.captureState();
        Stage21IGeneratedWorldRuntimePersistenceCodec.write(save, beforeReload);
        GeneratedCampaignCoordinator restored = GeneratedCampaignCoordinator.restore(
                Stage21IGeneratedWorldRuntimePersistenceCodec.readOrMigrate(save));
        GeneratedWorldUiModel restoredModel = new GeneratedWorldUiModel(
                restored.session().rootSeed(), restored.session().runtime(), restored.session().content());
        var restoredView = restoredModel.capture();

        assertEquals(beforeReload, restored.captureState());
        assertEquals(targetSystem, restored.session().runtime().world().getActiveSystemId());
        assertNotEquals(initialSystem, targetSystem);
        assertEquals(targetSystem, restoredView.activeSystemId());

        restored.session().advanceFrame(0.1f);
        assertNotEquals(beforeReload, restored.captureState(),
                "restored production campaign must continue from the saved authoritative state");
    }
}
