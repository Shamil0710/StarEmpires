package com.spacesim.player;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalFleetMapModelTest {
    @Test
    void mapContainsOnlyDiscoveredTopologyOwnedFleetsAndStoredIntel() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(15_601L);
        PlayerRuntime runtime = scenario.runtime();
        GlobalFleetMapSnapshot initial = GlobalFleetMapModel.capture(runtime);
        assertEquals(1, initial.systems().size());
        assertEquals(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, initial.systems().get(0).systemId());
        assertTrue(initial.links().isEmpty(), "undiscovered remote topology must stay hidden");
        assertEquals(runtime.player().ownedFleetIds().size(), initial.fleets().size());
        assertTrue(initial.fleets().stream().allMatch(marker ->
                runtime.player().ownedFleetIds().contains(marker.fleetId())));

        PlayerState previous = runtime.player();
        runtime.replacePlayerState(new PlayerState(
                previous.walletMilliCredits(),
                previous.factionContentId(),
                previous.reputations(),
                previous.ownedFleetIds(),
                previous.activeFleetId(),
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.INNER_SYSTEM_ID),
                previous.discoveredObjects(),
                previous.homeSystemId(),
                previous.dockedAt(),
                previous.fleetOrders(),
                previous.threatIntel()));
        long tick = runtime.world().findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getClock().getTick();
        PlayerThreatIntelService intel = new PlayerThreatIntelService(runtime);
        assertTrue(intel.observeSystem(DemoGalaxyFactory.INNER_SYSTEM_ID, 7f, 0.75f, tick));
        assertTrue(intel.observeLink(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                3f,
                0.5f,
                tick));

        GlobalFleetMapSnapshot expanded = GlobalFleetMapModel.capture(runtime);
        assertEquals(2, expanded.systems().size());
        assertEquals(1, expanded.links().size());
        GlobalFleetMapSnapshot.SystemMarker inner = expanded.systems().stream()
                .filter(marker -> DemoGalaxyFactory.INNER_SYSTEM_ID.equals(marker.systemId()))
                .findFirst().orElseThrow();
        assertEquals(7d, inner.observedDanger(), 0.000001d);
        assertEquals(0.75f, inner.intelConfidence(), 0.000001f);
        assertFalse(expanded.fleets().stream().anyMatch(marker ->
                !runtime.player().ownedFleetIds().contains(marker.fleetId())),
                "global map must never leak remote non-owned FleetIds");
    }

    @Test
    void strategicMovePersistsIntentButDoesNotTeleportFleet() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(15_602L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState previous = runtime.player();
        runtime.replacePlayerState(new PlayerState(
                previous.walletMilliCredits(),
                previous.factionContentId(),
                previous.reputations(),
                previous.ownedFleetIds(),
                previous.activeFleetId(),
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.INNER_SYSTEM_ID),
                previous.discoveredObjects(),
                previous.homeSystemId(),
                previous.dockedAt(),
                previous.fleetOrders(),
                previous.threatIntel()));
        FleetId fleetId = runtime.player().activeFleetId();
        FleetPlacementState before = runtime.world().findFleet(fleetId).orElseThrow();
        PlayerStrategicCommandService commands = new PlayerStrategicCommandService(runtime);

        assertTrue(commands.previewMove(fleetId, DemoGalaxyFactory.INNER_SYSTEM_ID).isPresent());
        assertTrue(commands.move(fleetId, DemoGalaxyFactory.INNER_SYSTEM_ID));
        FleetPlacementState immediatelyAfter = runtime.world().findFleet(fleetId).orElseThrow();
        assertEquals(before, immediatelyAfter,
                "global-map command must persist intent without moving or teleporting the physical fleet");
        assertEquals(FleetOrderType.MOVE,
                new PlayerFleetOrderService(runtime).order(fleetId).orElseThrow().type());
    }
}
