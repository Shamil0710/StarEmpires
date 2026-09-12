package com.spacesim.ui;

import com.spacesim.campaign.GeneratedCampaignSession;
import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.ui.GeneratedWorldUiSnapshot.LocalObjectView;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedWorldFreightProjectionTest {
    @Test
    void generatedClientUsesFactionProductionFreightArtWithoutChangingPhysicalScaleAuthority() {
        GeneratedCampaignSession campaign = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var runtime = campaign.runtime();
        GeneratedWorldUiModel model = new GeneratedWorldUiModel(
                campaign.rootSeed(), runtime, campaign.content());

        assertProductionFreightProjection(
                runtime,
                model,
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                "assets/ships/empire/production/freight/freight_base.png");
        assertProductionFreightProjection(
                runtime,
                model,
                Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID,
                "assets/ships/industrial_union/production/freight/freight_base.png");
    }

    @Test
    void movingFreighterUsesExactPhysicalSidecarInsteadOfStaleCheckpointMirror() {
        GeneratedCampaignSession campaign = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var runtime = campaign.runtime();
        FreighterState freight = orderedOperationalFreighter(campaign);
        var order = runtime.freight().findOrder(freight.activeOrderId()).orElseThrow();
        StarSystemId origin = freight.currentSystemId();
        StarSystemId destination = nextNeighbor(freight, order.orderedSystems());
        runtime.world().activateSystem(origin);
        var placement = runtime.world().findFleet(freight.fleetId()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, placement.locationKind());
        var exactBefore = runtime.arrival().materialization(origin)
                .physicalState(placement.localEntityId()).orElseThrow().position();
        var mirrorBefore = runtime.freight().findFreighter(freight.fleetId()).orElseThrow()
                .physicalState().position();

        runtime.world().requestFleetJump(freight.fleetId(), destination);
        GeneratedWorldUiModel model = new GeneratedWorldUiModel(
                campaign.rootSeed(), runtime, campaign.content());
        assertEquals(exactBefore, projectedFleet(model, freight.fleetId().value()).position());

        campaign.advanceFrame(0.1f);

        var currentPlacement = runtime.world().findFleet(freight.fleetId()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, currentPlacement.locationKind());
        var exactAfter = runtime.arrival().materialization(origin)
                .physicalState(currentPlacement.localEntityId()).orElseThrow().position();
        var mirrorAfter = runtime.freight().findFreighter(freight.fleetId()).orElseThrow()
                .physicalState().position();
        var projectedAfter = projectedFleet(model, freight.fleetId().value());

        assertNotEquals(exactBefore, exactAfter);
        assertEquals(mirrorBefore, mirrorAfter,
                "freight physicalState is a checkpoint mirror and must not drive live rendering");
        assertEquals(exactAfter, projectedAfter.position());
        assertNotEquals(exactBefore, projectedAfter.position());
    }

    @Test
    void saveRestoreMidApproachPreservesFleetIdentityJumpProgressAndProjection() {
        GeneratedCampaignSession continuous = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var runtime = continuous.runtime();
        FreighterState freight = orderedOperationalFreighter(continuous);
        var order = runtime.freight().findOrder(freight.activeOrderId()).orElseThrow();
        StarSystemId origin = freight.currentSystemId();
        StarSystemId destination = nextNeighbor(freight, order.orderedSystems());
        runtime.world().activateSystem(origin);

        var started = runtime.world().requestFleetJump(freight.fleetId(), destination);
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP, started.phase());
        assertTrue(started.phaseEndsTick() - started.phaseStartedTick() > 1L,
                "acceptance world must expose a real multi-tick physical approach");
        continuous.advanceFrame(0.1f);
        var midJump = runtime.world().findFleetJump(freight.fleetId()).orElseThrow();
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP, midJump.phase());
        var midPlacement = runtime.world().findFleet(freight.fleetId()).orElseThrow();
        var midPosition = runtime.arrival().materialization(origin)
                .physicalState(midPlacement.localEntityId()).orElseThrow().position();

        var checkpoint = continuous.captureState();
        GeneratedCampaignSession resumed = GeneratedCampaignSession.restore(checkpoint);
        var resumedRuntime = resumed.runtime();
        var resumedPlacement = resumedRuntime.world().findFleet(freight.fleetId()).orElseThrow();
        var resumedJump = resumedRuntime.world().findFleetJump(freight.fleetId()).orElseThrow();
        var resumedPosition = resumedRuntime.arrival().materialization(origin)
                .physicalState(resumedPlacement.localEntityId()).orElseThrow().position();
        GeneratedWorldUiModel resumedModel = new GeneratedWorldUiModel(
                resumed.rootSeed(), resumedRuntime, resumed.content());

        assertEquals(freight.fleetId(), resumedJump.fleetId());
        assertEquals(midJump, resumedJump);
        assertEquals(midPosition, resumedPosition);
        assertEquals(midPosition, projectedFleet(resumedModel, freight.fleetId().value()).position());

        continuous.advanceFrame(0.1f);
        resumed.advanceFrame(0.1f);

        assertEquals(continuous.captureState(), resumed.captureState());
    }

    private static void assertProductionFreightProjection(
            com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            GeneratedWorldUiModel model,
            String factionId,
            String expectedTexturePath) {
        FreighterState freight = runtime.freight().capture().freighters().stream()
                .filter(FreighterState::operational)
                .filter(value -> factionId.equals(value.stableFactionId()))
                .findFirst().orElseThrow();
        runtime.world().activateSystem(freight.currentSystemId());

        var legacyPhysical = runtime.freightSprite(freight.fleetId());
        LocalObjectView projected = projectedFleet(model, freight.fleetId().value());

        assertEquals(factionId, projected.factionId());
        assertEquals(expectedTexturePath, projected.sprite().texturePath());
        assertTrue(projected.sprite().assetId().startsWith("stage22.production:"));
        assertEquals(legacyPhysical.worldLengthM(), projected.physicalLengthM());
        assertEquals(legacyPhysical.worldWidthM(), projected.physicalWidthM());
    }

    private static FreighterState orderedOperationalFreighter(GeneratedCampaignSession campaign) {
        var runtime = campaign.runtime();
        return runtime.freight().capture().freighters().stream()
                .filter(FreighterState::operational)
                .filter(value -> runtime.freight().findOrder(value.activeOrderId()).isPresent())
                .findFirst().orElseThrow();
    }

    private static StarSystemId nextNeighbor(FreighterState freight, java.util.List<StarSystemId> route) {
        assertTrue(route.size() > 1);
        int nextIndex = freight.routeIndex() + 1;
        if (nextIndex >= route.size()) {
            nextIndex = freight.routeIndex() - 1;
        }
        assertTrue(nextIndex >= 0 && nextIndex < route.size());
        return route.get(nextIndex);
    }

    private static LocalObjectView projectedFleet(GeneratedWorldUiModel model, long fleetId) {
        String stableId = "fleet:" + fleetId;
        return model.capture().localObjects().stream()
                .filter(value -> value.stableId().equals(stableId))
                .findFirst().orElseThrow();
    }
}
