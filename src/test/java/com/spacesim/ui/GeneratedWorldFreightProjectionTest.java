package com.spacesim.ui;

import com.spacesim.campaign.GeneratedCampaignSession;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.ui.GeneratedWorldUiSnapshot.LocalObjectView;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedWorldFreightProjectionTest {
    @Test
    void movingFreighterUsesExactPhysicalSidecarInsteadOfStaleCheckpointMirror() {
        GeneratedCampaignSession campaign = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var runtime = campaign.runtime();
        FreighterState freight = runtime.freight().capture().freighters().stream()
                .filter(FreighterState::operational)
                .filter(value -> runtime.freight().findOrder(value.activeOrderId()).isPresent())
                .findFirst().orElseThrow();
        var order = runtime.freight().findOrder(freight.activeOrderId()).orElseThrow();
        assertTrue(order.orderedSystems().size() > 1);

        var origin = freight.currentSystemId();
        var destination = order.orderedSystems().stream()
                .filter(value -> !value.equals(origin))
                .findFirst().orElseThrow();
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

    private static LocalObjectView projectedFleet(GeneratedWorldUiModel model, long fleetId) {
        String stableId = "fleet:" + fleetId;
        return model.capture().localObjects().stream()
                .filter(value -> value.stableId().equals(stableId))
                .findFirst().orElseThrow();
    }
}
