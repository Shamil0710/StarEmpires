package com.spacesim.campaign;

import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        campaign.advanceFrame(0.1f);

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
        assertArrayEquals(campaign.encode(), restored.encode());
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
    void coordinatorAdvanceRunsDueLivingActorsAndPersistsTheirNextDeadline() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var beforeActors = campaign.authorities().actors().capture();
        assertFalse(beforeActors.isEmpty());
        assertTrue(beforeActors.stream().allMatch(state -> state.completedReviewCount() == 0L));

        campaign.session().setPaused(false);
        campaign.session().setTimeScale(1d);
        var advance = campaign.advanceFrame(0.1f);

        assertTrue(advance.fixedTicks() > 0L);
        long nowTick = campaign.session().runtime().world().getAuthoritativeWorldTick();
        var afterActors = campaign.authorities().actors().capture();
        assertEquals(beforeActors.size(), afterActors.size());
        assertTrue(afterActors.stream().allMatch(state -> state.completedReviewCount() == 1L));
        assertTrue(afterActors.stream().allMatch(state -> state.lastReviewTick() == nowTick));
        assertTrue(afterActors.stream().allMatch(state ->
                state.nextReviewTick() == nowTick + GeneratedCampaignCoordinator.LIVING_ACTOR_REVIEW_CADENCE_TICKS));
    }

    @Test
    void saveBeforeFirstLivingActorDeadlineContinuesDeterministicallyExactlyOnce() {
        GeneratedCampaignCoordinator source = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var checkpoint = source.captureState();

        GeneratedCampaignCoordinator first = GeneratedCampaignCoordinator.restore(checkpoint);
        GeneratedCampaignCoordinator second = GeneratedCampaignCoordinator.restore(checkpoint);
        first.session().setPaused(false);
        second.session().setPaused(false);
        first.session().setTimeScale(1d);
        second.session().setTimeScale(1d);

        first.advanceFrame(0.1f);
        second.advanceFrame(0.1f);

        assertArrayEquals(first.encode(), second.encode(),
                "same checkpoint and frame input must produce byte-identical composed campaign continuation");
        assertTrue(first.authorities().actors().capture().stream()
                .allMatch(state -> state.completedReviewCount() == 1L));

        var afterReview = first.captureState();
        first.advanceFrame(0f);
        assertEquals(afterReview, first.captureState(),
                "zero-delta presentation must not duplicate a living-actor review at the same world tick");

        first.session().setPaused(true);
        first.advanceFrame(1f);
        assertEquals(afterReview, first.captureState(),
                "paused campaign must not advance physical or living-world authority");
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
        var advance = campaign.advanceFrame(0.1f);
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

        restored.advanceFrame(0.1f);
        assertNotEquals(beforeReload, restored.captureState(),
                "restored production campaign must continue from the saved authoritative state");
    }
}
