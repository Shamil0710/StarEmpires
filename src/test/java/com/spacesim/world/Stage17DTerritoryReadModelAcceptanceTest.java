package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17DTerritoryReadModelAcceptanceTest {
    private static final String DYNAMIC_ID = "faction.player.territory";
    private static final int DYNAMIC_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;

    @Test
    void physicalStationInUnclaimedSystemCreatesPresenceButNotSovereignty() {
        WorldSimulation world = restoreWithDynamicFaction(true);

        FactionTerritoryView empty = FactionTerritoryService.assess(
                world,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                DYNAMIC_ID);
        assertEquals(FactionTerritoryView.Jurisdiction.UNCLAIMED, empty.jurisdiction());
        assertFalse(empty.physicalPresence());
        assertFalse(empty.controlled());
        assertNull(empty.controllingFactionContentId());

        world.createEntity(
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent("Player Frontier Station", IdentityComponent.Kind.STATION))
                        .add(new MarketComponent())
                        .add(new WalletComponent())
                        .add(new FactionComponent(DYNAMIC_RUNTIME_ID)));

        FactionTerritoryView present = FactionTerritoryService.assess(
                world,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                DYNAMIC_ID);
        assertEquals(FactionTerritoryView.Jurisdiction.PRESENT, present.jurisdiction());
        assertTrue(present.physicalPresence());
        assertFalse(present.controlled());
        assertFalse(present.controlledByFaction());
        assertTrue(world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).isEmpty());
        assertFalse(world.findFactionStrategicState(DYNAMIC_ID)
                .orElseThrow()
                .controls(DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
    }

    @Test
    void persistentControllerRemainsAuthoritativeWithOrWithoutLocalPresence() {
        WorldSimulation base = DemoGalaxyFactory.create(17_401L);
        FactionTerritoryView domestic = FactionTerritoryService.assess(
                base,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                "faction.trade_league");
        assertEquals(FactionTerritoryView.Jurisdiction.SELF_CONTROLLED, domestic.jurisdiction());
        assertTrue(domestic.controlled());
        assertTrue(domestic.controlledByFaction());
        assertEquals("faction.trade_league", domestic.controllingFactionContentId());

        WorldSimulation dynamicWorld = restoreWithDynamicFaction(false);
        dynamicWorld.createEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent("Foreign Player Station", IdentityComponent.Kind.STATION))
                        .add(new MarketComponent())
                        .add(new WalletComponent())
                        .add(new FactionComponent(DYNAMIC_RUNTIME_ID)));

        FactionTerritoryView foreign = FactionTerritoryService.assess(
                dynamicWorld,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DYNAMIC_ID);
        assertEquals(FactionTerritoryView.Jurisdiction.FOREIGN_CONTROLLED, foreign.jurisdiction());
        assertTrue(foreign.physicalPresence());
        assertTrue(foreign.controlled());
        assertFalse(foreign.controlledByFaction());
        assertEquals("faction.trade_league", foreign.controllingFactionContentId());
    }

    @Test
    void territoryAssessmentIsPureAndSupportsWorldDefinedFactionIdentity() {
        WorldSimulation world = restoreWithDynamicFaction(true);
        world.createEntity(
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent("Persistent Presence", IdentityComponent.Kind.STATION))
                        .add(new FactionComponent(DYNAMIC_RUNTIME_ID)));
        WorldState before = world.snapshot();

        FactionTerritoryView view = FactionTerritoryService.assess(
                world,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                DYNAMIC_ID);

        assertEquals(DYNAMIC_RUNTIME_ID, world.findFactionRuntimeId(DYNAMIC_ID).orElseThrow());
        assertEquals(FactionTerritoryView.Jurisdiction.PRESENT, view.jurisdiction());
        assertEquals(before, world.snapshot());
    }

    @Test
    void unknownSystemFactionAndInconsistentViewsAreRejected() {
        WorldSimulation world = restoreWithDynamicFaction(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> FactionTerritoryService.assess(world, new StarSystemId(99_999L), DYNAMIC_ID));
        assertThrows(
                IllegalArgumentException.class,
                () -> FactionTerritoryService.assess(
                        world,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                        "faction.missing"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FactionTerritoryView(
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                        DYNAMIC_ID,
                        FactionTerritoryView.Jurisdiction.UNCLAIMED,
                        true,
                        null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FactionTerritoryView(
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                        DYNAMIC_ID,
                        FactionTerritoryView.Jurisdiction.PRESENT,
                        false,
                        null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FactionTerritoryView(
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                        DYNAMIC_ID,
                        FactionTerritoryView.Jurisdiction.SELF_CONTROLLED,
                        false,
                        "faction.trade_league"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FactionTerritoryView(
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                        DYNAMIC_ID,
                        FactionTerritoryView.Jurisdiction.FOREIGN_CONTROLLED,
                        false,
                        DYNAMIC_ID));
    }

    private static WorldSimulation restoreWithDynamicFaction(boolean frontierUnclaimed) {
        WorldState base = DemoGalaxyFactory.create(17_402L).snapshot();
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            List<StarSystemId> controlled = strategy.controlledSystems();
            if (frontierUnclaimed) {
                controlled = controlled.stream()
                        .filter(systemId -> !systemId.equals(DemoGalaxyFactory.FRONTIER_SYSTEM_ID))
                        .toList();
            }
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
        strategies.add(new FactionStrategicState(DYNAMIC_ID, 0, List.of(), List.of()));

        List<FactionEconomicState> factions = new ArrayList<>(base.factions());
        factions.add(new FactionEconomicState(DYNAMIC_ID, 0L, 0L, 0L));

        List<WorldFactionIdentityState> identities = new ArrayList<>(base.factionIdentities());
        identities.add(new WorldFactionIdentityState(
                DYNAMIC_ID,
                DYNAMIC_RUNTIME_ID,
                "Territory Test Faction",
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
