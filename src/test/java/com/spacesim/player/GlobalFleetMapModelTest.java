package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        assertTrue(initial.projects().isEmpty());
        assertTrue(initial.stations().isEmpty());

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
                previous.threatIntel(),
                previous.ownedConstructionProjectIds(),
                previous.ownedStations()));
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
        assertTrue(expanded.stations().isEmpty(),
                "discovering a remote system must not leak its non-owned station entities");
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
                previous.threatIntel(),
                previous.ownedConstructionProjectIds(),
                previous.ownedStations()));
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

    @Test
    void mapProjectsOnlyAuthoritativeOwnedConstructionAssets() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_603L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                100_000_000L,
                initial.ownedFleetIds(),
                initial.activeFleetId()));
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionPlacementView placement = findValidPlacement(construction);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", placement.x(), placement.y());
        var project = runtime.world().findConstructionProject(projectId).orElseThrow();
        long partialFunding = project.minimumFundingMilliCredits() / 2L;
        assertEquals(partialFunding, construction.fundProject(projectId, partialFunding));
        assertTrue(new PlayerFleetOrderService(runtime).supplyProject(
                runtime.player().activeFleetId(), projectId, "item.steel"));

        GlobalFleetMapSnapshot withProject = GlobalFleetMapModel.capture(runtime);
        assertEquals(1, withProject.projects().size());
        assertTrue(withProject.stations().isEmpty());
        GlobalFleetMapSnapshot.ConstructionProjectMarker projectMarker = withProject.projects().get(0);
        PlayerConstructionProjectView managementProject = new PlayerConstructionManagementModel(runtime)
                .capture().projects().get(0);
        assertEquals(projectId, projectMarker.projectId());
        assertEquals(managementProject.status(), projectMarker.status());
        assertEquals(managementProject.buildProgress(), projectMarker.buildProgress());
        assertEquals(managementProject.totalMissingUnits(), projectMarker.missingMaterialUnits());
        assertEquals(managementProject.fundingShortfallMilliCredits(),
                projectMarker.fundingShortfallMilliCredits());
        assertEquals(managementProject.supplyFleetIds(), projectMarker.supplyFleetIds());

        Entity ownedStation = findStation(runtime, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, "station.power_plant");
        EntityIdComponent stationId = ownedStation.getComponent(EntityIdComponent.class);
        OwnedStationRef ownedRef = new OwnedStationRef(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, stationId.id);
        PlayerState current = runtime.player();
        List<OwnedStationRef> stations = new ArrayList<>(current.ownedStations());
        stations.add(ownedRef);
        runtime.replacePlayerState(PlayerRuntime.copyWithConstructionOwnership(
                current,
                current.ownedConstructionProjectIds(),
                stations));

        PlayerState beforeRemoteDiscovery = runtime.player();
        runtime.replacePlayerState(new PlayerState(
                beforeRemoteDiscovery.walletMilliCredits(),
                beforeRemoteDiscovery.factionContentId(),
                beforeRemoteDiscovery.reputations(),
                beforeRemoteDiscovery.ownedFleetIds(),
                beforeRemoteDiscovery.activeFleetId(),
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.INNER_SYSTEM_ID),
                beforeRemoteDiscovery.discoveredObjects(),
                beforeRemoteDiscovery.homeSystemId(),
                beforeRemoteDiscovery.dockedAt(),
                beforeRemoteDiscovery.fleetOrders(),
                beforeRemoteDiscovery.threatIntel(),
                beforeRemoteDiscovery.ownedConstructionProjectIds(),
                beforeRemoteDiscovery.ownedStations()));

        GlobalFleetMapSnapshot finalSnapshot = GlobalFleetMapModel.capture(runtime);
        assertEquals(1, finalSnapshot.projects().size());
        assertEquals(1, finalSnapshot.stations().size(),
                "only the explicitly owned ordinary station may appear despite remote system discovery");
        GlobalFleetMapSnapshot.OwnedStationMarker stationMarker = finalSnapshot.stations().get(0);
        assertEquals(ownedRef, stationMarker.reference());
        assertEquals("station.power_plant", stationMarker.stationArchetypeContentId());
        assertEquals(ownedStation.getComponent(WalletComponent.class).getBalanceMilliCredits(),
                stationMarker.walletMilliCredits());
        assertFalse(finalSnapshot.stations().stream().anyMatch(marker ->
                        DemoGalaxyFactory.INNER_SYSTEM_ID.equals(marker.systemId())),
                "known remote non-owned stations must remain absent from the global asset map");
    }

    private static PlayerConstructionPlacementView findValidPlacement(PlayerConstructionService construction) {
        for (float y = 100f; y <= Constants.WORLD_HEIGHT - 100f; y += 100f) {
            for (float x = 100f; x <= Constants.WORLD_WIDTH - 100f; x += 100f) {
                PlayerConstructionPlacementView view = construction.previewPlacement(x, y);
                if (view.allowed()) {
                    return view;
                }
            }
        }
        throw new AssertionError("Playable test world has no valid construction placement");
    }

    private static Entity findStation(PlayerRuntime runtime, StarSystemId systemId, String archetypeId) {
        for (Entity entity : runtime.world().findSession(systemId).orElseThrow().getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (archetype != null && archetypeId.equals(archetype.contentId)
                    && entity.getComponent(EntityIdComponent.class) != null
                    && entity.getComponent(WalletComponent.class) != null
                    && transform != null) {
                return entity;
            }
        }
        throw new AssertionError("No station archetype " + archetypeId + " in system " + systemId);
    }
}