package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17LocalFleetAffiliationAcceptanceTest {
    private static final String PLAYER_FACTION_ID = "faction.player.local_fleet_affiliation";

    @Test
    void localOwnedFleetChangesOnlyFactionAndKeepsPhysicalIdsAndState() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_401L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        PlayerState player = runtime.player();
        FleetId ownedFleetId = player.activeFleetId();
        FleetPlacementState placementBefore = runtime.world().findFleet(ownedFleetId).orElseThrow();
        Entity ownedBefore = localFleetEntity(runtime, placementBefore);
        EntityState stateBefore = EntityStateMapper.capture(ownedBefore);
        int oldFactionId = ownedBefore.getComponent(FactionComponent.class).factionId;
        int targetFactionId = runtime.world().findFactionRuntimeId(PLAYER_FACTION_ID).orElseThrow();
        assertNotEquals(targetFactionId, oldFactionId);

        FleetPlacementState nonOwnedPlacement = runtime.world().getFleetPlacements().stream()
                .filter(placement -> placement.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(placement -> !player.ownedFleetIds().contains(placement.id()))
                .findFirst()
                .orElseThrow();
        Entity nonOwned = localFleetEntity(runtime, nonOwnedPlacement);
        FactionComponent nonOwnedFaction = nonOwned.getComponent(FactionComponent.class);
        int nonOwnedFactionBefore = nonOwnedFaction == null ? -1 : nonOwnedFaction.factionId;

        PlayerFactionAssetAffiliationService.AffiliationReport report =
                new PlayerFactionAssetAffiliationService(runtime).affiliateLocalOwnedFleets();

        FleetPlacementState placementAfter = runtime.world().findFleet(ownedFleetId).orElseThrow();
        Entity ownedAfter = localFleetEntity(runtime, placementAfter);
        EntityState stateAfter = EntityStateMapper.capture(ownedAfter);

        assertEquals(1, report.inspectedOwnedFleets());
        assertEquals(1, report.newlyAffiliatedLocalFleets());
        assertEquals(0, report.alreadyAffiliatedLocalFleets());
        assertEquals(0, report.deferredTransitFleets());
        assertEquals(ownedFleetId, placementAfter.id());
        assertEquals(placementBefore.localEntityId(), placementAfter.localEntityId());
        assertEquals(stateBefore.id(), stateAfter.id());
        assertEquals(targetFactionId, ownedAfter.getComponent(FactionComponent.class).factionId);
        assertSamePhysicalStateExceptFaction(stateBefore, stateAfter);

        FactionComponent nonOwnedFactionAfter = nonOwned.getComponent(FactionComponent.class);
        assertEquals(nonOwnedFactionBefore,
                nonOwnedFactionAfter == null ? -1 : nonOwnedFactionAfter.factionId);

        PlayerFactionAssetAffiliationService.AffiliationReport second =
                new PlayerFactionAssetAffiliationService(runtime).affiliateLocalOwnedFleets();
        assertEquals(0, second.newlyAffiliatedLocalFleets());
        assertEquals(1, second.alreadyAffiliatedLocalFleets());
    }

    @Test
    void affiliatedFleetFactionSurvivesPlayableSaveLoad() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_402L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        FleetId fleetId = runtime.player().activeFleetId();
        FleetPlacementState before = runtime.world().findFleet(fleetId).orElseThrow();
        int targetFactionId = runtime.world().findFactionRuntimeId(PLAYER_FACTION_ID).orElseThrow();

        new PlayerFactionAssetAffiliationService(runtime).affiliateLocalOwnedFleets();
        PlayableWorldState saved = runtime.snapshot();
        PlayableWorldState decoded = PlayableWorldStateCodec.decode(PlayableWorldStateCodec.encode(saved));
        PlayerRuntime restored = PlayerRuntime.restore(
                decoded,
                scenario.content(),
                before.systemId());

        FleetPlacementState after = restored.world().findFleet(fleetId).orElseThrow();
        Entity restoredFleet = localFleetEntity(restored, after);
        assertEquals(fleetId, after.id());
        assertEquals(before.localEntityId(), after.localEntityId());
        assertEquals(targetFactionId, restoredFleet.getComponent(FactionComponent.class).factionId);
        assertEquals(PLAYER_FACTION_ID,
                restored.world().findFactionStableId(targetFactionId).orElseThrow());
    }

    @Test
    void independentPlayerCannotAffiliateAssets() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_403L);
        PlayableWorldState source = scenario.runtime().snapshot();
        PlayerState independent = copyWithFaction(source.playerState(), null);
        PlayerRuntime runtime = PlayerRuntime.restore(
                new PlayableWorldState(PlayableWorldState.CURRENT_VERSION, source.worldState(), independent),
                scenario.content(),
                source.playerState().homeSystemId());

        assertThrows(IllegalStateException.class,
                () -> new PlayerFactionAssetAffiliationService(runtime).affiliateLocalOwnedFleets());
    }

    private static PlayerRuntime foundedRuntime(PlayableTestWorldFactory.Scenario scenario) {
        PlayableWorldState source = scenario.runtime().snapshot();
        PlayerState independent = copyWithFaction(source.playerState(), null);
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                new PlayableWorldState(PlayableWorldState.CURRENT_VERSION, source.worldState(), independent),
                scenario.content(),
                PLAYER_FACTION_ID,
                "Local Fleet Affiliation Union");
        StarSystemId activeSystem = source.playerState().homeSystemId();
        return PlayerRuntime.restore(founded, scenario.content(), activeSystem);
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

    private static Entity localFleetEntity(PlayerRuntime runtime, FleetPlacementState placement) {
        assertEquals(FleetLocationKind.IN_SYSTEM, placement.locationKind());
        SimulationSession session = runtime.world().findSession(placement.systemId()).orElseThrow();
        Entity entity = session.getEntityRegistry().find(placement.localEntityId());
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
        assertTrue(after.faction() != null);
    }
}
