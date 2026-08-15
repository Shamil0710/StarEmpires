package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F5ExternalConstructionCoexistenceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String STEEL = "item.steel";
    private static final String FOUNDRY = "station.foundry";

    @Test
    void externalOwnerProjectDoesNotBreakFactionProducerScan() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F50044L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        ConstructionProjectId externalProjectId = world.createConstructionProject(
                null,
                TRADE_LEAGUE,
                null,
                FOUNDRY,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                5_000f,
                5_000f);
        assertNull(world.findConstructionProject(externalProjectId).orElseThrow().ownerFactionContentId());

        int steelRuntimeId = content.findItem(STEEL).runtimeId();
        int factionRuntimeId = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        Entity ownedMarket = null;
        for (Entity entity : world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (faction != null
                    && faction.factionId == factionRuntimeId
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(InventoryComponent.class) != null) {
                ownedMarket = entity;
                break;
            }
        }
        assertTrue(ownedMarket != null);
        MarketComponent market = ownedMarket.getComponent(MarketComponent.class);
        InventoryComponent inventory = ownedMarket.getComponent(InventoryComponent.class);
        market.configureTradableItem(steelRuntimeId, 250, 0f);
        market.targetStock[steelRuntimeId] = 250;
        inventory.stock[steelRuntimeId] = 0;
        FactionLocalProductionPlan localPlan = new FactionLocalProductionPlan(
                TRADE_LEAGUE,
                world.getAuthoritativeWorldTick(),
                List.of(),
                List.of(STEEL));

        FactionResilienceConstructionRecommendation recommendation =
                FactionResilienceConstructionPlanner.recommendNext(world, localPlan).orElseThrow();

        assertEquals(FOUNDRY, recommendation.stationArchetypeContentId());
        assertEquals(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, recommendation.systemId());
    }
}
