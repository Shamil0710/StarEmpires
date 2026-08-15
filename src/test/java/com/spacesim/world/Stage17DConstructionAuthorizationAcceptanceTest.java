package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17DConstructionAuthorizationAcceptanceTest {
    private static final String CONTROLLER = "faction.trade_league";
    private static final String FOREIGN_BUILDER = "faction.miners";
    private static final String DYNAMIC_FACTION = "faction.player.construction_rights";
    private static final int DYNAMIC_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;
    private static final String ARCHETYPE = "station.foundry";

    @Test
    void friendlyMarketRelationsDoNotGrantForeignConstructionButExplicitConcessionDoes() {
        WorldSimulation world = DemoGalaxyFactory.create(17_420L);
        StarSystemId system = DemoGalaxyFactory.ACTIVE_SYSTEM_ID;
        FactionStrategicState controller = world.findFactionStrategicState(CONTROLLER).orElseThrow();

        assertTrue(controller.relationTo(FOREIGN_BUILDER) >= controller.minimumMarketAccessRelation(),
                "Fixture must prove that old market-access logic would have admitted the builder");
        TerritorialConstructionAuthorization.Decision denied =
                TerritorialConstructionAuthorization.evaluate(world, FOREIGN_BUILDER, system);
        assertFalse(denied.allowed());
        assertEquals(TerritorialConstructionAuthorization.Reason.FOREIGN_CONTROL_NO_RIGHT, denied.reason());
        assertEquals(CONTROLLER, denied.controllingFactionContentId());
        assertThrows(
                IllegalStateException.class,
                () -> world.createConstructionProject(
                        FOREIGN_BUILDER, ARCHETYPE, system, 950f, 650f));

        TerritorialConstructionRightState right = world.grantTerritorialConstructionRight(
                CONTROLLER, FOREIGN_BUILDER, system, -1L);
        assertEquals(FOREIGN_BUILDER, right.granteeFactionContentId());
        assertTrue(TerritorialConstructionAuthorization.evaluate(world, FOREIGN_BUILDER, system).allowed());

        ConstructionProjectId projectId = world.createConstructionProject(
                FOREIGN_BUILDER, ARCHETYPE, system, 950f, 650f);
        ConstructionProjectState project = world.findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionSettlementKind.FACTION_TREASURY, project.settlementKind());
        assertEquals(FOREIGN_BUILDER, project.ownerFactionContentId());
        assertEquals(FOREIGN_BUILDER, project.legalFactionContentId());

        WorldSimulation restored = WorldSimulation.restore(
                WorldStateCodec.decode(WorldStateCodec.encode(world.snapshot())),
                ContentCatalogLoader.loadDefault(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        assertTrue(TerritorialConstructionAuthorization.evaluate(
                restored, FOREIGN_BUILDER, system).allowed());
        assertTrue(restored.revokeTerritorialConstructionRight(CONTROLLER, FOREIGN_BUILDER, system));
        assertFalse(TerritorialConstructionAuthorization.evaluate(
                restored, FOREIGN_BUILDER, system).allowed());
    }

    @Test
    void dynamicFactionCanUseExternalSettlementWithoutLosingFactionLegalIdentity() {
        WorldSimulation world = restoreWithDynamicFactionAndUnclaimedFrontier(17_421L);

        TerritorialConstructionAuthorization.Decision frontier =
                TerritorialConstructionAuthorization.evaluate(
                        world, DYNAMIC_FACTION, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);
        assertTrue(frontier.allowed());
        assertEquals(TerritorialConstructionAuthorization.Reason.UNCLAIMED_FRONTIER, frontier.reason());

        ConstructionProjectId projectId = world.createConstructionProject(
                null,
                DYNAMIC_FACTION,
                ARCHETYPE,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                930f,
                620f);
        ConstructionProjectState project = world.findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionSettlementKind.EXTERNAL_OWNER, project.settlementKind());
        assertNull(project.ownerFactionContentId());
        assertEquals(DYNAMIC_FACTION, project.legalFactionContentId());

        Entity site = world.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow()
                .getEntityRegistry()
                .find(project.constructionSiteEntityId());
        assertNotNull(site);
        FactionComponent siteFaction = site.getComponent(FactionComponent.class);
        assertNotNull(siteFaction);
        assertEquals(DYNAMIC_RUNTIME_ID, siteFaction.factionId);

        assertTrue(world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).isEmpty());
        assertNull(world.findFactionStrategicState(DYNAMIC_FACTION)
                .orElseThrow()
                .claimFor(DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
    }

    @Test
    void independentPrivateConstructionRemainsPoliticallyNeutral() {
        WorldSimulation world = DemoGalaxyFactory.create(17_422L);
        TerritorialConstructionAuthorization.Decision privateDecision =
                TerritorialConstructionAuthorization.evaluate(
                        world, null, DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        assertTrue(privateDecision.allowed());
        assertEquals(TerritorialConstructionAuthorization.Reason.PRIVATE_UNAFFILIATED,
                privateDecision.reason());
        assertEquals(CONTROLLER, privateDecision.controllingFactionContentId());
    }

    private static WorldSimulation restoreWithDynamicFactionAndUnclaimedFrontier(long seed) {
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
        strategies.add(new FactionStrategicState(DYNAMIC_FACTION, 0, List.of(), List.of()));

        List<FactionEconomicState> factions = new ArrayList<>(base.factions());
        factions.add(new FactionEconomicState(DYNAMIC_FACTION, 0L, 0L, 0L));

        List<WorldFactionIdentityState> identities = new ArrayList<>(base.factionIdentities());
        identities.add(new WorldFactionIdentityState(
                DYNAMIC_FACTION,
                DYNAMIC_RUNTIME_ID,
                "Construction Rights Test Faction",
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
