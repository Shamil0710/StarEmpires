package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17DTerritorialControlAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player.territorial_control";
    private static final int PLAYER_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;
    private static final String NEUTRAL = "faction.neutral";
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void physicalAnchorRequiresExplicitClaimAndContinuousStabilizationBeforeControl() {
        WorldSimulation world = restoreWithDynamicFaction(17_410L);
        EntityId anchorId = createOperationalAnchor(world, PLAYER_FACTION, PLAYER_RUNTIME_ID, "Frontier Anchor");

        FactionTerritoryView present = FactionTerritoryService.assess(
                world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, PLAYER_FACTION);
        assertEquals(FactionTerritoryView.Jurisdiction.PRESENT, present.jurisdiction());
        assertTrue(present.physicalPresence());
        assertFalse(present.claimedByFaction());
        assertTrue(world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).isEmpty());

        long noClaimStart = world.getAuthoritativeWorldTick();
        advanceToAtLeast(world, noClaimStart + TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS + 50L);
        assertTrue(world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).isEmpty());

        TerritorialClaimState declared = world.declareTerritorialClaim(
                PLAYER_FACTION, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        assertEquals(TerritorialClaimState.Status.ACTIVE, declared.status());
        FactionTerritoryView claimed = FactionTerritoryService.assess(
                world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, PLAYER_FACTION);
        assertEquals(FactionTerritoryView.Jurisdiction.CLAIMED, claimed.jurisdiction());
        assertEquals(0L, claimed.stabilizationTicks());

        world.recognizeTerritorialClaim(
                TRADE_LEAGUE, PLAYER_FACTION, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        assertEquals(1, FactionTerritoryService.assess(
                world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, PLAYER_FACTION).recognitionCount());

        long claimStart = world.getAuthoritativeWorldTick();
        advanceToAtLeast(world, claimStart + TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS + 50L);

        FactionTerritoryView controlled = FactionTerritoryService.assess(
                world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, PLAYER_FACTION);
        assertEquals(FactionTerritoryView.Jurisdiction.SELF_CONTROLLED, controlled.jurisdiction());
        assertTrue(controlled.controlledByFaction());
        assertEquals(PLAYER_FACTION, controlled.controllingFactionContentId());
        assertEquals(TerritorialClaimState.Status.ESTABLISHED, controlled.claimStatus());
        assertTrue(controlled.stabilizationTicks() >= TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS);
        assertNotNull(world.findFactionStrategicState(PLAYER_FACTION)
                .orElseThrow()
                .controlStateFor(DemoGalaxyFactory.FRONTIER_SYSTEM_ID));

        world.recognizeTerritorialControl(
                NEUTRAL, PLAYER_FACTION, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        FactionTerritoryView recognizedControl = FactionTerritoryService.assess(
                world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, PLAYER_FACTION);
        assertEquals(1, recognizedControl.recognitionCount());

        byte[] encoded = WorldStateCodec.encode(world.snapshot());
        WorldState decoded = WorldStateCodec.decode(encoded);
        WorldSimulation restored = WorldSimulation.restore(
                decoded,
                ContentCatalogLoader.loadDefault(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        FactionTerritoryView restoredView = FactionTerritoryService.assess(
                restored, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, PLAYER_FACTION);
        assertEquals(FactionTerritoryView.Jurisdiction.SELF_CONTROLLED, restoredView.jurisdiction());
        assertEquals(TerritorialClaimState.Status.ESTABLISHED, restoredView.claimStatus());
        assertEquals(1, restoredView.recognitionCount());
        assertNotNull(restored.findFactionStrategicState(PLAYER_FACTION)
                .orElseThrow()
                .controlStateFor(DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
        assertTrue(restored.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow()
                .getEntityRegistry()
                .find(anchorId) != null);
    }

    @Test
    void materiallySupportedRivalClaimsRemainContestedInsteadOfPickingWinnerByOrder() {
        WorldSimulation world = restoreWithDynamicFaction(17_411L);
        createOperationalAnchor(world, PLAYER_FACTION, PLAYER_RUNTIME_ID, "Player Claim Anchor");
        int neutralRuntimeId = world.findFactionRuntimeId(NEUTRAL).orElseThrow();
        createOperationalAnchor(world, NEUTRAL, neutralRuntimeId, "Neutral Claim Anchor");

        world.declareTerritorialClaim(PLAYER_FACTION, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        world.declareTerritorialClaim(NEUTRAL, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        long start = world.getAuthoritativeWorldTick();
        advanceToAtLeast(world, start + TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS + 100L);

        assertTrue(world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).isEmpty());
        FactionTerritoryView player = FactionTerritoryService.assess(
                world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, PLAYER_FACTION);
        FactionTerritoryView neutral = FactionTerritoryService.assess(
                world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, NEUTRAL);
        assertEquals(FactionTerritoryView.Jurisdiction.CONTESTED, player.jurisdiction());
        assertEquals(FactionTerritoryView.Jurisdiction.CONTESTED, neutral.jurisdiction());
        assertEquals(TerritorialClaimState.Status.CONTESTED, player.claimStatus());
        assertEquals(TerritorialClaimState.Status.CONTESTED, neutral.claimStatus());
        assertEquals(0L, player.stabilizationTicks());
        assertEquals(0L, neutral.stabilizationTicks());
    }

    @Test
    void establishedControlSurvivesShortDisruptionButIsLostAfterPersistentUnsupportedPeriod() {
        WorldSimulation world = restoreWithDynamicFaction(17_412L);
        EntityId anchorId = createOperationalAnchor(world, PLAYER_FACTION, PLAYER_RUNTIME_ID, "Disposable Anchor");
        world.declareTerritorialClaim(PLAYER_FACTION, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        long start = world.getAuthoritativeWorldTick();
        advanceToAtLeast(world, start + TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS + 50L);
        assertEquals(PLAYER_FACTION,
                world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).orElseThrow());

        assertTrue(world.removeEntity(DemoGalaxyFactory.FRONTIER_SYSTEM_ID, anchorId));
        long unsupportedStart = world.getAuthoritativeWorldTick();
        advanceToAtLeast(
                world,
                unsupportedStart + TerritorialControlRuntime.CONTROL_LOSS_GRACE_TICKS - 600L);
        assertEquals(PLAYER_FACTION,
                world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).orElseThrow());

        advanceToAtLeast(
                world,
                unsupportedStart + TerritorialControlRuntime.CONTROL_LOSS_GRACE_TICKS + 100L);
        assertTrue(world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).isEmpty());
        TerritorialClaimState claim = world.findFactionStrategicState(PLAYER_FACTION)
                .orElseThrow()
                .claimFor(DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        assertNotNull(claim);
        assertEquals(TerritorialClaimState.Status.ACTIVE, claim.status());
        assertEquals(0L, claim.stabilizationTicks());
    }

    private static EntityId createOperationalAnchor(
            WorldSimulation world,
            String factionId,
            int runtimeFactionId,
            String name) {
        assertEquals(runtimeFactionId, world.findFactionRuntimeId(factionId).orElseThrow());
        return world.createEntity(
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                        .add(new MarketComponent())
                        .add(new FactionComponent(runtimeFactionId)));
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1.0f);
            guard++;
            if (guard > 20_000) {
                throw new AssertionError("World did not reach target authoritative tick");
            }
        }
    }

    private static WorldSimulation restoreWithDynamicFaction(long seed) {
        WorldState base = DemoGalaxyFactory.create(seed).snapshot();
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            List<StarSystemId> controlled = strategy.controlledSystems().stream()
                    .filter(systemId -> !systemId.equals(DemoGalaxyFactory.FRONTIER_SYSTEM_ID))
                    .toList();
            strategies.add(new FactionStrategicState(
                    strategy.factionContentId(),
                    strategy.minimumMarketAccessRelation(),
                    strategy.relations(),
                    controlled,
                    strategy.stationTaxBasisPoints(),
                    strategy.foreignTerritoryTariffBasisPoints(),
                    strategy.stockPolicies(),
                    strategy.productionPolicies(),
                    strategy.strategicGoals()));
        }
        strategies.add(new FactionStrategicState(PLAYER_FACTION, 0, List.of(), List.of()));

        List<FactionEconomicState> factions = new ArrayList<>(base.factions());
        factions.add(new FactionEconomicState(PLAYER_FACTION, 0L, 0L, 0L));

        List<WorldFactionIdentityState> identities = new ArrayList<>(base.factionIdentities());
        identities.add(new WorldFactionIdentityState(
                PLAYER_FACTION,
                PLAYER_RUNTIME_ID,
                "Territorial Control Test Faction",
                WorldFactionIdentityState.Origin.PLAYER_CREATED));

        WorldState state = new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                factions,
                strategies,
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                identities);
        return WorldSimulation.restore(
                state,
                ContentCatalogLoader.loadDefault(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }
}
