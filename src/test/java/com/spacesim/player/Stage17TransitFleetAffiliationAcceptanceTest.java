package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpState;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetTransitState;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17TransitFleetAffiliationAcceptanceTest {
    private static final String PLAYER_FACTION_ID = "faction.player.transit_affiliation";

    @Test
    void realJumpTransitChangesOnlyDetachedFactionAndKeepsJumpState() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_501L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        FleetId fleetId = runtime.player().activeFleetId();
        FleetPlacementState localBeforeJump = runtime.world().findFleet(fleetId).orElseThrow();
        StarSystemId destination = runtime.world().getTopology()
                .neighbors(localBeforeJump.systemId())
                .get(0);

        runtime.world().requestFleetJump(fleetId, destination, 25f, -15f);
        runtime.advanceFrame(0.2f);

        FleetPlacementState transitBefore = runtime.world().findFleet(fleetId).orElseThrow();
        assertEquals(FleetLocationKind.IN_TRANSIT, transitBefore.locationKind());
        FleetTransitState transitStateBefore = transitBefore.transitState();
        assertNotNull(transitStateBefore);
        EntityState payloadBefore = transitStateBefore.entityState();
        FleetJumpState jumpBefore = runtime.world().findFleetJump(fleetId).orElseThrow();
        int targetFactionId = runtime.world().findFactionRuntimeId(PLAYER_FACTION_ID).orElseThrow();

        PlayerFactionAssetAffiliationService.TransitAffiliationReport report =
                new PlayerFactionAssetAffiliationService(runtime).affiliateTransitOwnedFleets();

        FleetPlacementState transitAfter = runtime.world().findFleet(fleetId).orElseThrow();
        FleetTransitState transitStateAfter = transitAfter.transitState();
        EntityState payloadAfter = transitStateAfter.entityState();
        FleetJumpState jumpAfter = runtime.world().findFleetJump(fleetId).orElseThrow();

        assertEquals(1, report.inspectedOwnedFleets());
        assertEquals(1, report.newlyAffiliatedTransitFleets());
        assertEquals(0, report.alreadyAffiliatedTransitFleets());
        assertEquals(0, report.deferredLocalFleets());
        assertEquals(fleetId, transitAfter.id());
        assertEquals(FleetLocationKind.IN_TRANSIT, transitAfter.locationKind());
        assertEquals(transitStateBefore.originSystemId(), transitStateAfter.originSystemId());
        assertEquals(transitStateBefore.destinationSystemId(), transitStateAfter.destinationSystemId());
        assertEquals(jumpBefore, jumpAfter);
        assertEquals(targetFactionId, payloadAfter.faction().factionId());
        assertSamePayloadExceptFaction(payloadBefore, payloadAfter);

        PlayerFactionAssetAffiliationService.TransitAffiliationReport second =
                new PlayerFactionAssetAffiliationService(runtime).affiliateTransitOwnedFleets();
        assertEquals(0, second.newlyAffiliatedTransitFleets());
        assertEquals(1, second.alreadyAffiliatedTransitFleets());
        assertEquals(jumpAfter, runtime.world().findFleetJump(fleetId).orElseThrow());
    }

    @Test
    void transitAffiliationSurvivesMidJumpSaveLoadAndOrdinaryArrival() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_502L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        FleetId fleetId = runtime.player().activeFleetId();
        FleetPlacementState localBeforeJump = runtime.world().findFleet(fleetId).orElseThrow();
        StarSystemId origin = localBeforeJump.systemId();
        StarSystemId destination = runtime.world().getTopology().neighbors(origin).get(0);
        var originEntityId = localBeforeJump.localEntityId();

        runtime.world().requestFleetJump(fleetId, destination, 25f, -15f);
        runtime.advanceFrame(0.2f);
        new PlayerFactionAssetAffiliationService(runtime).affiliateTransitOwnedFleets();
        FleetJumpState jumpBeforeSave = runtime.world().findFleetJump(fleetId).orElseThrow();
        int targetFactionId = runtime.world().findFactionRuntimeId(PLAYER_FACTION_ID).orElseThrow();

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        PlayerRuntime restored = PlayerRuntime.restore(
                decoded,
                scenario.content(),
                runtime.world().getActiveSystemId());

        FleetPlacementState restoredTransit = restored.world().findFleet(fleetId).orElseThrow();
        assertEquals(FleetLocationKind.IN_TRANSIT, restoredTransit.locationKind());
        assertEquals(targetFactionId, restoredTransit.transitState().entityState().faction().factionId());
        assertEquals(jumpBeforeSave, restored.world().findFleetJump(fleetId).orElseThrow());

        restored.advanceFrame(6.0f);

        FleetPlacementState arrived = restored.world().findFleet(fleetId).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(destination, arrived.systemId());
        assertEquals(fleetId, arrived.id());
        Entity arrivedEntity = restored.world().findSession(destination).orElseThrow()
                .getEntityRegistry().find(arrived.localEntityId());
        assertNotNull(arrivedEntity);
        assertEquals(targetFactionId, arrivedEntity.getComponent(FactionComponent.class).factionId);
        assertTrue(restored.world().findFleetJump(fleetId).isEmpty());
        assertNotSame(originEntityId, arrived.localEntityId());
    }

    @Test
    void transitCommandDefersLocalOwnedFleetWithoutMutation() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_503L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        FleetId fleetId = runtime.player().activeFleetId();
        FleetPlacementState before = runtime.world().findFleet(fleetId).orElseThrow();
        Entity entityBefore = runtime.world().findSession(before.systemId()).orElseThrow()
                .getEntityRegistry().find(before.localEntityId());
        int factionBefore = entityBefore.getComponent(FactionComponent.class).factionId;

        PlayerFactionAssetAffiliationService.TransitAffiliationReport report =
                new PlayerFactionAssetAffiliationService(runtime).affiliateTransitOwnedFleets();

        FleetPlacementState after = runtime.world().findFleet(fleetId).orElseThrow();
        Entity entityAfter = runtime.world().findSession(after.systemId()).orElseThrow()
                .getEntityRegistry().find(after.localEntityId());
        assertEquals(1, report.inspectedOwnedFleets());
        assertEquals(0, report.newlyAffiliatedTransitFleets());
        assertEquals(0, report.alreadyAffiliatedTransitFleets());
        assertEquals(1, report.deferredLocalFleets());
        assertEquals(before, after);
        assertEquals(factionBefore, entityAfter.getComponent(FactionComponent.class).factionId);
    }

    private static PlayerRuntime foundedRuntime(PlayableTestWorldFactory.Scenario scenario) {
        PlayableWorldState source = scenario.runtime().snapshot();
        PlayerState independent = copyWithFaction(source.playerState(), null);
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                new PlayableWorldState(PlayableWorldState.CURRENT_VERSION, source.worldState(), independent),
                scenario.content(),
                PLAYER_FACTION_ID,
                "Transit Affiliation Union");
        return PlayerRuntime.restore(
                founded,
                scenario.content(),
                source.playerState().homeSystemId());
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

    private static void assertSamePayloadExceptFaction(EntityState before, EntityState after) {
        assertEquals(before.id(), after.id());
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
}
