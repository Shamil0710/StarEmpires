package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.systems.FactionMarketAccessSystem;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17OwnedStationAffiliationAcceptanceTest {
    private static final String PLAYER_FACTION_ID = "faction.player.station_affiliation";

    @Test
    void realPlayerBuiltStationAffiliatesWithoutChangingPhysicalState() {
        FoundedStationFixture fixture = createFoundedOwnedStation(17_451L);
        PlayerRuntime runtime = fixture.runtime();
        OwnedStationRef stationRef = fixture.stationRef();
        Entity stationBefore = station(runtime, stationRef);
        EntityState stateBefore = EntityStateMapper.capture(stationBefore);
        long playerWalletBefore = runtime.player().walletMilliCredits();
        assertNull(stationBefore.getComponent(FactionComponent.class));
        assertNull(stationBefore.getComponent(FactionMarketAccessComponent.class));

        PlayerFactionAssetAffiliationService.StationAffiliationReport report =
                new PlayerFactionAssetAffiliationService(runtime).affiliateOwnedStations();

        Entity stationAfter = station(runtime, stationRef);
        EntityState stateAfter = EntityStateMapper.capture(stationAfter);
        int targetFactionId = runtime.world().findFactionRuntimeId(PLAYER_FACTION_ID).orElseThrow();
        assertEquals(1, report.inspectedOwnedStations());
        assertEquals(1, report.newlyAffiliatedStations());
        assertEquals(0, report.alreadyAffiliatedStations());
        assertEquals(runtime.world().getTopology().systems().size(), report.refreshedPolicySessions());
        assertEquals(targetFactionId, stationAfter.getComponent(FactionComponent.class).factionId);
        assertEquals(stationRef.stationEntityId(), stateAfter.id());
        assertEquals(playerWalletBefore, runtime.player().walletMilliCredits());
        assertSamePhysicalStateExceptFaction(stateBefore, stateAfter);
        assertTrue(runtime.player().ownedStations().contains(stationRef));

        FactionMarketAccessComponent access = stationAfter.getComponent(FactionMarketAccessComponent.class);
        assertNotNull(access);
        assertTrue(access.canTrade(-1));
        assertTrue(access.canTrade(targetFactionId));
        SimulationSession session = runtime.world().findSession(stationRef.systemId()).orElseThrow();
        FactionMarketAccessSystem accessSystem = session.getEngine().getSystem(FactionMarketAccessSystem.class);
        assertNotNull(accessSystem);

        PlayerFactionAssetAffiliationService.StationAffiliationReport second =
                new PlayerFactionAssetAffiliationService(runtime).affiliateOwnedStations();
        assertEquals(0, second.newlyAffiliatedStations());
        assertEquals(1, second.alreadyAffiliatedStations());
        assertSame(accessSystem, session.getEngine().getSystem(FactionMarketAccessSystem.class));
    }

    @Test
    void stationAffiliationAndLiveAccessPolicySurvivePlayableSaveLoad() {
        FoundedStationFixture fixture = createFoundedOwnedStation(17_452L);
        PlayerRuntime runtime = fixture.runtime();
        OwnedStationRef stationRef = fixture.stationRef();
        int targetFactionId = runtime.world().findFactionRuntimeId(PLAYER_FACTION_ID).orElseThrow();

        new PlayerFactionAssetAffiliationService(runtime).affiliateOwnedStations();
        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        PlayerRuntime restored = PlayerRuntime.restore(decoded, fixture.content(), stationRef.systemId());

        assertTrue(restored.player().ownedStations().contains(stationRef));
        Entity restoredStation = station(restored, stationRef);
        assertEquals(targetFactionId, restoredStation.getComponent(FactionComponent.class).factionId);
        FactionMarketAccessComponent restoredAccess =
                restoredStation.getComponent(FactionMarketAccessComponent.class);
        assertNotNull(restoredAccess);
        assertTrue(restoredAccess.canTrade(-1));
        assertTrue(restoredAccess.canTrade(targetFactionId));
    }

    private static FoundedStationFixture createFoundedOwnedStation(long seed) {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(seed);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState initial = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                initial,
                100_000_000L,
                initial.ownedFleetIds(),
                initial.activeFleetId()));

        FleetId fleetId = runtime.player().activeFleetId();
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        SimulationSession session = runtime.world().findSession(placement.systemId()).orElseThrow();
        Entity ship = session.getEntityRegistry().find(placement.localEntityId());
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        InventoryComponent shipInventory = ship.getComponent(InventoryComponent.class);
        assertNotNull(shipTransform);
        assertNotNull(shipInventory);
        shipTransform.velocity.setZero();

        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        ConstructionProjectId projectId = construction.createProject(
                "station.mining_base", shipTransform.position.x, shipTransform.position.y);
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(project.minimumFundingMilliCredits(),
                construction.fundProject(projectId, project.minimumFundingMilliCredits()));

        Arrays.fill(shipInventory.stock, 0);
        int requiredTotal = project.materials().stream()
                .mapToInt(ConstructionMaterialState::requiredAmount)
                .sum();
        shipInventory.capacity = Math.max(shipInventory.capacity, requiredTotal);
        for (ConstructionMaterialState material : project.materials()) {
            int itemId = runtime.content().findItem(material.itemContentId()).runtimeId();
            shipInventory.stock[itemId] = material.requiredAmount();
        }
        for (ConstructionMaterialState material : project.materials()) {
            assertEquals(material.requiredAmount(), construction.deliverMaterial(
                    projectId, fleetId, material.itemContentId(), material.requiredAmount()));
        }

        ConstructionProjectState fulfilled = runtime.world().findConstructionProject(projectId).orElseThrow();
        long safetyTicks = fulfilled.buildDurationTicks() + 20L;
        for (long tick = 0L; tick < safetyTicks; tick++) {
            runtime.advanceFrame(0.1f);
            if (runtime.world().findConstructionProject(projectId).orElseThrow().status()
                    == ConstructionProjectStatus.COMPLETED) {
                break;
            }
        }
        ConstructionProjectState completed = runtime.world().findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.COMPLETED, completed.status());
        OwnedStationRef stationRef = new OwnedStationRef(
                completed.systemId(), completed.completedStationEntityId());
        assertTrue(runtime.player().ownedStations().contains(stationRef));
        assertNull(station(runtime, stationRef).getComponent(FactionComponent.class));

        PlayableWorldState preFounding = runtime.snapshot();
        PlayerState independent = copyWithFaction(preFounding.playerState(), null);
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                new PlayableWorldState(
                        PlayableWorldState.CURRENT_VERSION,
                        preFounding.worldState(),
                        independent),
                scenario.content(),
                PLAYER_FACTION_ID,
                "Station Affiliation Union");
        PlayerRuntime foundedRuntime = PlayerRuntime.restore(
                founded,
                scenario.content(),
                stationRef.systemId());
        assertNull(station(foundedRuntime, stationRef).getComponent(FactionComponent.class));
        return new FoundedStationFixture(foundedRuntime, stationRef, scenario.content());
    }

    private static PlayerState copyWithFaction(PlayerState source, String factionId) {
        return new PlayerState(
                source.walletMilliCredits(),
                factionId,
                source.reputations(),
                source.ownedFleetIds(),
                source.activeFleetId(),
                source.discoveredSystemIds(),
                source.discoveredObjects(),
                source.homeSystemId(),
                source.dockedAt(),
                source.fleetOrders(),
                source.threatIntel(),
                source.ownedConstructionProjectIds(),
                source.ownedStations());
    }

    private static Entity station(PlayerRuntime runtime, OwnedStationRef reference) {
        Entity entity = runtime.world().findSession(reference.systemId()).orElseThrow()
                .getEntityRegistry().find(reference.stationEntityId());
        assertNotNull(entity);
        return entity;
    }

    private static void assertSamePhysicalStateExceptFaction(EntityState before, EntityState after) {
        assertEquals(before.identity(), after.identity());
        assertEquals(before.transform(), after.transform());
        assertEquals(before.inventory(), after.inventory());
        assertEquals(before.wallet(), after.wallet());
        assertEquals(before.market(), after.market());
        assertEquals(before.production(), after.production());
        assertEquals(before.priceHistory(), after.priceHistory());
        assertEquals(before.reputation(), after.reputation());
        assertEquals(before.ship(), after.ship());
        assertEquals(before.tradeAi(), after.tradeAi());
        assertEquals(before.mining(), after.mining());
        assertEquals(before.combat(), after.combat());
        assertEquals(before.asteroid(), after.asteroid());
        assertEquals(before.archetype(), after.archetype());
    }

    private record FoundedStationFixture(
            PlayerRuntime runtime,
            OwnedStationRef stationRef,
            com.spacesim.content.ContentCatalog content) {
    }
}
