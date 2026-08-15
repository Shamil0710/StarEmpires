package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.SupplierDiversificationPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F5WorldSupplierDiversificationPolicyTest {
    private static final String SOURCE = "faction.neutral";
    private static final String CONCENTRATED = "faction.trade_league";
    private static final String ALTERNATIVE = "faction.miners";

    @Test
    void liveDependenceAndDoctrineProduceBoundedSupplierWillingnessToPay() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F50022L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        ContentCatalog.ItemDefinition item = content.getItems().get(0);
        clearExistingEconomicSignal(world);

        int sourceRuntime = world.findFactionRuntimeId(SOURCE).orElseThrow();
        int concentratedRuntime = world.findFactionRuntimeId(CONCENTRATED).orElseThrow();
        int alternativeRuntime = world.findFactionRuntimeId(ALTERNATIVE).orElseThrow();
        addMarketStation(
                world,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                "Resilience source market",
                sourceRuntime,
                item.runtimeId(),
                20,
                100,
                50f);
        addMarketStation(
                world,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                "Concentrated supplier",
                concentratedRuntime,
                item.runtimeId(),
                100,
                20,
                10f);
        addMarketStation(
                world,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                "Alternative supplier",
                alternativeRuntime,
                item.runtimeId(),
                60,
                20,
                20f);

        FactionDoctrineState doctrine = world.findFactionStrategicState(SOURCE).orElseThrow().doctrine();
        world.updateFactionDoctrine(SOURCE, new FactionDoctrineState(
                doctrine.tradeOpenness(),
                doctrine.securityPosture(),
                doctrine.expansionPreference(),
                doctrine.sovereigntySensitivity(),
                doctrine.treatyLegalism(),
                doctrine.interventionism(),
                80));
        WorldSupplierDiversificationPolicy policy = new WorldSupplierDiversificationPolicy(world);
        FleetTradeProfile fleet = fleet(sourceRuntime, item.runtimeId());

        SupplierDiversificationPolicy.Assessment concentrated = policy.assess(
                fleet, concentratedRuntime, item.runtimeId());
        SupplierDiversificationPolicy.Assessment alternative = policy.assess(
                fleet, alternativeRuntime, item.runtimeId());

        assertTrue(concentrated.active());
        assertEquals(6_666, concentrated.supplierShareBasisPoints());
        assertEquals(320_000L, concentrated.acceptableProfitSacrificeMilliCredits());
        assertTrue(alternative.active());
        assertEquals(3_333, alternative.supplierShareBasisPoints());
        assertEquals(320_000L, alternative.acceptableProfitSacrificeMilliCredits());

        world.updateFactionDoctrine(SOURCE, new FactionDoctrineState(
                doctrine.tradeOpenness(),
                doctrine.securityPosture(),
                doctrine.expansionPreference(),
                doctrine.sovereigntySensitivity(),
                doctrine.treatyLegalism(),
                doctrine.interventionism(),
                0));
        SupplierDiversificationPolicy.Assessment zeroPriority = policy.assess(
                fleet, concentratedRuntime, item.runtimeId());
        assertFalse(zeroPriority.active());
        assertEquals(0L, zeroPriority.acceptableProfitSacrificeMilliCredits());
    }

    private static FleetTradeProfile fleet(int factionRuntimeId, int itemRuntimeId) {
        return new FleetTradeProfile(
                0f,
                0f,
                20f,
                Money.fromCredits(100_000d),
                10,
                0,
                10,
                itemRuntimeId,
                false,
                null,
                factionRuntimeId,
                new int[Constants.MAX_ITEMS],
                new float[Constants.FACTION_RUNTIME_CAPACITY]);
    }

    private static void clearExistingEconomicSignal(WorldSimulation world) {
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                MarketComponent market = entity.getComponent(MarketComponent.class);
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (market != null && inventory != null) {
                    for (int itemId = 0; itemId < inventory.stock.length; itemId++) {
                        inventory.stock[itemId] = 0;
                        market.targetStock[itemId] = 0;
                    }
                }
                ProductionComponent production = entity.getComponent(ProductionComponent.class);
                if (production != null) {
                    production.activeRecipeIndex = -1;
                }
            }
        }
    }

    private static void addMarketStation(
            WorldSimulation world,
            StarSystemId systemId,
            String name,
            int factionRuntimeId,
            int itemId,
            int stock,
            int target,
            float sellPrice) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(itemId, Math.max(1, target), 0f);
        market.targetStock[itemId] = target;
        market.sellPrices[itemId] = sellPrice;
        market.buyPrices[itemId] = Math.max(1f, sellPrice * 0.9f);
        Entity shell = new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(new FactionComponent(factionRuntimeId))
                .add(inventory)
                .add(market)
                .add(new WalletComponent());
        EntityId id = world.createEntity(systemId, shell);
        Entity live = world.findSession(systemId).orElseThrow().getEntityRegistry().find(id);
        live.getComponent(InventoryComponent.class).stock[itemId] = stock;
        assertTrue(live.getComponent(WalletComponent.class).creditFromSource(1_000_000L));
    }
}
