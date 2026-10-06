package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.player.PlayerState;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignPersonalRouteTest {
    @Test void undiscoveredDestinationsAndCurrentSystemDoNotProduceRoutesOrMutateKnowledge() {
        var campaign = FoundedCampaignFixture.restore();
        var player = campaign.playerState().orElseThrow();
        var baseline = campaign.captureState();
        var unknown = campaign.coordinator().runtime().world().getTopology().systems().stream()
                .map(system -> system.id()).filter(id -> !player.discoveredSystemIds().contains(id)).findFirst().orElseThrow();
        assertTrue(campaign.previewPilotRoute(unknown).isEmpty());
        assertTrue(campaign.previewPilotRoute(player.homeSystemId()).isEmpty());
        assertTrue(campaign.previewPilotRoute(new StarSystemId(Long.MAX_VALUE)).isEmpty());
        assertEquals(baseline, campaign.captureState());
    }

    @Test void previewUsesSharedPlannerAndFirstHopTokenRejectsForeignStaleAndRepeatedSubmission() {
        var campaign = knownSystemsFixture();
        var world = campaign.coordinator().runtime().world();
        var player = campaign.playerState().orElseThrow();
        var origin = world.findFleet(player.activeFleetId()).orElseThrow().systemId();
        var destination = world.getTopology().neighbors(origin).get(0);
        var baseline = campaign.captureState();
        var preview = campaign.previewPilotRoute(destination).orElseThrow();
        assertTrue(preview.departure().allowed());
        assertEquals(java.util.List.of(origin, destination), preview.route().path());
        assertEquals(baseline, campaign.captureState());
        var foreign = Stage228CampaignAuthority.restore(baseline);
        assertThrows(IllegalStateException.class, () -> foreign.submitPilotAction(preview.departure()));
        assertEquals(baseline, foreign.captureState());
        campaign.coordinator().setPaused(true);
        assertThrows(IllegalStateException.class, () -> campaign.submitPilotAction(preview.departure()));
        var current = campaign.previewPilotRoute(destination).orElseThrow();
        campaign.submitPilotAction(current.departure());
        assertEquals(destination, world.findFleetJump(player.activeFleetId()).orElseThrow().destinationSystemId());
        assertTrue(campaign.playerState().orElseThrow().fleetOrders().isEmpty());
        var departed = campaign.captureState();
        assertThrows(IllegalStateException.class, () -> campaign.submitPilotAction(current.departure()));
        assertTrue(campaign.previewPilotRoute(destination).isEmpty());
        assertEquals(departed, campaign.captureState());
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(departed)));
        assertEquals(departed, loaded.captureState());
    }

    @Test void multipleHopsRemainDiagnosticsAndOnlyFirstOrdinaryJumpIsSubmitted() {
        var campaign = knownSystemsFixture();
        var world = campaign.coordinator().runtime().world();
        var player = campaign.playerState().orElseThrow();
        var origin = world.findFleet(player.activeFleetId()).orElseThrow().systemId();
        var preview = world.getTopology().systems().stream().map(system -> system.id())
                .filter(id -> !id.equals(origin) && !world.getTopology().neighbors(origin).contains(id))
                .map(campaign::previewPilotRoute).flatMap(java.util.Optional::stream)
                .filter(value -> value.route().path().size() >= 3 && value.departure().allowed())
                .findFirst().orElseThrow();
        assertTrue(preview.route().travelTicks() > 0);
        assertTrue(preview.route().uncertaintyExposure() > 0);
        var before = campaign.captureState();
        var again = campaign.previewPilotRoute(preview.route().path().get(preview.route().path().size() - 1)).orElseThrow();
        assertEquals(preview.route(), again.route());
        assertEquals(before, campaign.captureState());
        campaign.submitPilotAction(preview.departure());
        assertEquals(preview.route().path().get(1), world.findFleetJump(player.activeFleetId()).orElseThrow().destinationSystemId());
        assertEquals(player.walletMilliCredits(), campaign.playerState().orElseThrow().walletMilliCredits());
        assertEquals(player.fleetOrders(), campaign.playerState().orElseThrow().fleetOrders());
    }

    private static Stage228CampaignAuthority knownSystemsFixture() {
        var campaign = FoundedCampaignFixture.restore();
        var saved = campaign.captureState();
        var p = saved.playerState();
        // Explicit knowledge fixture: no production command awards remote discoveries.
        var player = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(),
                p.activeFleetId(), campaign.coordinator().runtime().world().getTopology().systems().stream()
                        .map(system -> system.id()).toList(), p.discoveredObjects(), p.homeSystemId(), p.dockedAt(),
                p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(), p.ownedStations());
        return Stage228CampaignAuthority.restore(new Stage228GeneratedCampaignPersistentState(saved.schemaVersion(),
                saved.runtimeVersion(), saved.stage21Runtime(), saved.smallCraft(), saved.hangars(), saved.flightDeck(),
                saved.operations(), player));
    }
}
