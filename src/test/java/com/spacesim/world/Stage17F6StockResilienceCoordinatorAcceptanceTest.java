package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F6StockResilienceCoordinatorAcceptanceTest {
    private static final String SOURCE = "faction.neutral";
    private static final String CONCENTRATED = "faction.trade_league";
    private static final String ALTERNATIVE = "faction.miners";

    @Test
    void oneCommonReviewClaimBoundsStockIncreaseWithoutMaterializingEconomy() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F60041L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        ContentCatalog.ItemDefinition item = content.getItems().get(0);
        advanceToTick(world, FactionPolicyReviewCadence.defaultForFaction(SOURCE).firstReviewOffsetTicks());
        clearExistingEconomicSignal(world);

        int sourceRuntime = world.findFactionRuntimeId(SOURCE).orElseThrow();
        int concentratedRuntime = world.findFactionRuntimeId(CONCENTRATED).orElseThrow();
        int alternativeRuntime = world.findFactionRuntimeId(ALTERNATIVE).orElseThrow();
        addMarketStation(
                world,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                "17F6 source market",
                sourceRuntime,
                item.runtimeId(),
                20,
                100,
                50f);
        addMarketStation(
                world,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                "17F6 concentrated supplier",
                concentratedRuntime,
                item.runtimeId(),
                100,
                20,
                10f);
        addMarketStation(
                world,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                "17F6 alternative supplier",
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
        FactionStockProductionPolicyState existing = world.findFactionStockProductionPolicy(SOURCE).orElseThrow();
        world.updateFactionStockProductionPolicy(
                SOURCE,
                new FactionStockProductionPolicyState(List.of(), existing.productionPolicies()));

        FactionResilienceItemDecision resilienceDecision = FactionResiliencePlanner.analyze(world, SOURCE)
                .items().stream()
                .filter(candidate -> candidate.itemContentId().equals(item.id()))
                .findFirst()
                .orElseThrow();
        assertTrue(resilienceDecision.recommendedTargetFloorPerMarketUnits() > 10,
                "Fixture must require more than one bounded stock-review step");

        long inventoryBefore = totalInventoryUnits(world);
        long walletsBefore = totalEntityWallets(world);
        long marketTargetsBefore = totalMarketTargets(world);
        long treasuryBefore = world.findFactionEconomicState(SOURCE).orElseThrow().treasuryMilliCredits();
        List<FactionProductionPolicyState> productionBefore = world.findFactionStockProductionPolicy(SOURCE)
                .orElseThrow().productionPolicies();

        FactionPolicyReviewCoordinator.Report first = FactionPolicyReviewCoordinator.reviewPolicies(
                world, List.of(SOURCE));
        FactionPolicyReviewCoordinator.FactionReview firstFaction = first.factionReviews().get(0);

        assertEquals(1L, first.claimedReviewCount());
        assertTrue(firstFaction.reviewClaimed());
        assertTrue(firstFaction.stockResilienceReview().policyChanged());
        assertEquals(1, firstFaction.stockResilienceReview().increasedItemCount());
        FactionStockProductionPolicyState afterFirst = world.findFactionStockProductionPolicy(SOURCE).orElseThrow();
        assertEquals(10, stockFloor(afterFirst, item.id()),
                "First review may raise the floor by only the conservative 10-unit step");
        assertEquals(productionBefore, afterFirst.productionPolicies());
        assertEquals(inventoryBefore, totalInventoryUnits(world));
        assertEquals(walletsBefore, totalEntityWallets(world));
        assertEquals(marketTargetsBefore, totalMarketTargets(world),
                "Authoring a stock policy must not materialize target demand before explicit apply");
        assertEquals(treasuryBefore, world.findFactionEconomicState(SOURCE).orElseThrow().treasuryMilliCredits());

        FactionPolicyReviewCoordinator.Report repeated = FactionPolicyReviewCoordinator.reviewPolicies(
                world, List.of(SOURCE));

        assertEquals(0L, repeated.claimedReviewCount());
        assertEquals(0L, repeated.changedStockResiliencePolicyCount());
        assertEquals(afterFirst, world.findFactionStockProductionPolicy(SOURCE).orElseThrow(),
                "Same-window retry must not apply the second bounded step");
        assertEquals(inventoryBefore, totalInventoryUnits(world));
        assertEquals(walletsBefore, totalEntityWallets(world));
        assertEquals(marketTargetsBefore, totalMarketTargets(world));
        assertEquals(treasuryBefore, world.findFactionEconomicState(SOURCE).orElseThrow().treasuryMilliCredits());
    }

    private static int stockFloor(FactionStockProductionPolicyState policy, String itemContentId) {
        return policy.stockPolicies().stream()
                .filter(stock -> stock.itemContentId().equals(itemContentId))
                .findFirst()
                .orElseThrow()
                .targetStockFloor();
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
                if (inventory != null) {
                    for (int units : inventory.stock) {
                        result = Math.addExact(result, units);
                    }
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

    private static long totalMarketTargets(WorldSimulation world) {
        long result = 0L;
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                MarketComponent market = entity.getComponent(MarketComponent.class);
                if (market != null) {
                    for (int target : market.targetStock) {
                        result = Math.addExact(result, target);
                    }
                }
            }
        }
        return result;
    }

    private static void advanceToTick(WorldSimulation world, long targetTick) {
        float fixedStep = world.findSession(world.getActiveSystemId()).orElseThrow()
                .getClock().getFixedStepSeconds();
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick && guard++ < 20_000) {
            world.advanceFrame(fixedStep);
        }
        assertTrue(world.getAuthoritativeWorldTick() >= targetTick, "World did not reach requested review tick");
    }
}
