package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSimulationFactionStrategyTest {

    @Test
    void demoTerritoryИDiplomacyПереживаютRestoreИМатериализуютMarketAccess() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState state = DemoGalaxyFactory.createState(0x5A7E8001L, content);
        WorldSimulation world = WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                10,
                8);

        assertEquals("faction.trade_league",
                world.controllingFaction(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow());
        assertEquals("faction.miners",
                world.controllingFaction(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow());
        assertEquals("faction.neutral",
                world.controllingFaction(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).orElseThrow());
        assertEquals(state.factionStrategies(), world.snapshot().factionStrategies());

        int minersId = content.findFaction("faction.miners").runtimeId();
        boolean foundTradeLeagueMarket = false;
        for (Entity entity : world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEngine().getEntities()) {
            FactionComponent owner = entity.getComponent(FactionComponent.class);
            if (owner == null
                    || owner.factionId != content.findFaction("faction.trade_league").runtimeId()
                    || entity.getComponent(MarketComponent.class) == null) {
                continue;
            }
            FactionMarketAccessComponent access = entity.getComponent(FactionMarketAccessComponent.class);
            assertTrue(access != null && access.canTrade(minersId));
            foundTradeLeagueMarket = true;
        }
        assertTrue(foundTradeLeagueMarket);
    }

    @Test
    void strictDirectedRelationЗапрещаетTargetFactionНоНеSelf() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(0x5A7E8002L, content);
        WorldState strict = new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                base.factions(),
                java.util.List.of(new FactionStrategicState(
                        "faction.trade_league",
                        25,
                        java.util.List.of(new FactionRelationState("faction.miners", -50)),
                        java.util.List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID))));
        WorldSimulation world = WorldSimulation.restore(
                strict,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                10,
                8);
        int tradeLeagueId = content.findFaction("faction.trade_league").runtimeId();
        int minersId = content.findFaction("faction.miners").runtimeId();

        boolean checked = false;
        for (Entity entity : world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEngine().getEntities()) {
            FactionComponent owner = entity.getComponent(FactionComponent.class);
            if (owner == null || owner.factionId != tradeLeagueId
                    || entity.getComponent(MarketComponent.class) == null) {
                continue;
            }
            FactionMarketAccessComponent access = entity.getComponent(FactionMarketAccessComponent.class);
            assertTrue(access.canTrade(tradeLeagueId));
            assertFalse(access.canTrade(minersId));
            checked = true;
        }
        assertTrue(checked);
    }
}
