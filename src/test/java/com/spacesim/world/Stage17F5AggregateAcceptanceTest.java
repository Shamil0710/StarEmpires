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
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.trade.CriticalImportLimitPolicy;
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.RouteRedundancyPolicy;
import com.spacesim.trade.SupplierDiversificationPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F5AggregateAcceptanceTest {
    private static final String SOURCE = "faction.neutral";
    private static final String CONCENTRATED = "faction.trade_league";
    private static final String ALTERNATIVE = "faction.miners";

    @Test
    void onePhysicalDependenceSnapshotDrivesAllResilienceChoicesWithoutFreeEconomyEffects() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F50099L, content),
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
                "Aggregate resilience source market",
                sourceRuntime,
                item.runtimeId(),
                20,
                100,
                50f);
        addMarketStation(
                world,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                "Aggregate concentrated supplier",
                concentratedRuntime,
                item.runtimeId(),
                100,
                20,
                10f);
        addMarketStation(
                world,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                "Aggregate alternative supplier",
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
        FleetTradeProfile fleet = fleet(sourceRuntime, item.runtimeId());
        byte[] beforePlanning = WorldStateCodec.encode(world.snapshot());

        FactionResiliencePlan resilience = FactionResiliencePlanner.analyze(world, SOURCE);
        FactionResilienceItemDecision decision = resilience.items().stream()
                .filter(candidate -> candidate.itemContentId().equals(item.id()))
                .findFirst()
                .orElseThrow();
        SupplierDiversificationPolicy.Assessment concentratedSupplier =
                new WorldSupplierDiversificationPolicy(world).assess(
                        fleet, concentratedRuntime, item.runtimeId());
        SupplierDiversificationPolicy.Assessment alternativeSupplier =
                new WorldSupplierDiversificationPolicy(world).assess(
                        fleet, alternativeRuntime, item.runtimeId());
        RouteRedundancyPolicy.Assessment route =
                new WorldRouteRedundancyPolicy(world).assess(fleet, item.runtimeId());
        CriticalImportLimitPolicy.Assessment concentratedImport =
                new WorldCriticalImportLimitPolicy(world).assess(
                        fleet, concentratedRuntime, item.runtimeId());
        CriticalImportLimitPolicy.Assessment alternativeImport =
                new WorldCriticalImportLimitPolicy(world).assess(
                        fleet, alternativeRuntime, item.runtimeId());
        FactionLocalProductionPlan localProduction =
                FactionLocalProductionPlanner.analyze(world, resilience);
        FactionStockProductionPolicyState mergedPolicy = resilience.mergeRecommendedStockFloors(
                world.findFactionStockProductionPolicy(SOURCE).orElseThrow());

        assertEquals(80, resilience.economicResiliencePriority());
        assertTrue(decision.recommendedTargetFloorPerMarketUnits() > 0);
        assertTrue(decision.diversifySuppliersRecommended());
        assertTrue(decision.localProductionRecommended());
        assertTrue(decision.routeRedundancyRecommended());
        assertEquals(6_000, decision.preferredMaximumPartnerShareBasisPoints());

        assertTrue(concentratedSupplier.active());
        assertEquals(6_666, concentratedSupplier.supplierShareBasisPoints());
        assertTrue(alternativeSupplier.active());
        assertEquals(3_333, alternativeSupplier.supplierShareBasisPoints());
        assertTrue(route.active());
        assertTrue(route.acceptableProfitSacrificeMilliCredits() > 0L);

        assertTrue(concentratedImport.active());
        assertEquals(6_000, concentratedImport.maximumSupplierShareBasisPoints());
        assertFalse(concentratedImport.authorized());
        assertTrue(alternativeImport.active());
        assertEquals(6_000, alternativeImport.maximumSupplierShareBasisPoints());
        assertTrue(alternativeImport.authorized());

        long recommendationCount = localProduction.recommendations().stream()
                .filter(recommendation -> recommendation.itemContentId().equals(item.id()))
                .count();
        long gapCount = localProduction.capacityGapItemContentIds().stream()
                .filter(item.id()::equals)
                .count();
        assertEquals(1L, recommendationCount + gapCount,
                "Critical local-production intent must resolve to owned capacity or one explicit gap");
        assertTrue(mergedPolicy.stockPolicies().stream()
                .filter(policy -> policy.itemContentId().equals(item.id()))
                .anyMatch(policy -> policy.targetStockFloor()
                        >= decision.recommendedTargetFloorPerMarketUnits()));

        assertArrayEquals(beforePlanning, WorldStateCodec.encode(world.snapshot()),
                "All resilience analysis, policy assessment and merge planning must remain read-only");

        long stockBeforeApply = totalInventoryUnits(world);
        long walletsBeforeApply = totalEntityWallets(world);
        long treasuryBeforeApply = world.findFactionEconomicState(SOURCE)
                .orElseThrow().treasuryMilliCredits();
        world.updateFactionStockProductionPolicy(SOURCE, mergedPolicy);
        world.applyFactionStrategicPolicy(SOURCE);

        assertEquals(stockBeforeApply, totalInventoryUnits(world),
                "Policy authoring/apply may change targets or recipes but must not create cargo");
        assertEquals(walletsBeforeApply, totalEntityWallets(world),
                "Policy authoring/apply must not create or spend entity wallet money");
        assertEquals(treasuryBeforeApply,
                world.findFactionEconomicState(SOURCE).orElseThrow().treasuryMilliCredits(),
                "Policy authoring/apply must not create or spend faction treasury money");
    }

    private static FleetTradeProfile fleet(int factionRuntimeId, int itemRuntimeId) {
        return new FleetTradeProfile(
                0f,
                0f,
                20f,
                100_000_000L,
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

    private static long totalInventoryUnits(WorldSimulation world) {
        long result = 0L;
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (inventory == null) {
                    continue;
                }
                for (int units : inventory.stock) {
                    result = Math.addExact(result, units);
                }
            }
        }
        return result;
    }

    private static long totalEntityWallets(WorldSimulation world) {
        long result = 0L;
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                WalletComponent wallet = entity.getComponent(WalletComponent.class);
                if (wallet != null) {
                    result = Math.addExact(result, wallet.getBalanceMilliCredits());
                }
            }
        }
        return result;
    }
}
