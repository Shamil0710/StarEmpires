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
    void commonReviewWindowBoundsAutomaticOverlayUpAndDownWithoutErasingBasePolicy() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F60041L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        ContentCatalog.ItemDefinition item = content.getItems().get(0);
        FactionPolicyReviewCadence cadence = FactionPolicyReviewCadence.defaultForFaction(SOURCE);
        advanceToTick(world, cadence.firstReviewOffsetTicks());
        clearExistingEconomicSignal(world);

        int sourceRuntime = world.findFactionRuntimeId(SOURCE).orElseThrow();
        int concentratedRuntime = world.findFactionRuntimeId(CONCENTRATED).orElseThrow();
        int alternativeRuntime = world.findFactionRuntimeId(ALTERNATIVE).orElseThrow();
        Entity sourceMarket = addMarketStation(
                world,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                "17F6 source market",
                sourceRuntime,
                item.runtimeId(),
                20,
                5,
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
        FactionStockProductionPolicyState basePolicy = new FactionStockProductionPolicyState(
                List.of(new FactionStockPolicyState(item.id(), 7)),
                existing.productionPolicies());
        world.updateFactionStockProductionPolicy(SOURCE, basePolicy);
        assertTrue(world.findFactionResilienceDemandFloors(SOURCE).isEmpty());

        FactionResilienceItemDecision resilienceDecision = FactionResiliencePlanner.analyze(world, SOURCE)
                .items().stream()
                .filter(candidate -> candidate.itemContentId().equals(item.id()))
                .findFirst()
                .orElseThrow();
        assertTrue(resilienceDecision.recommendedTargetFloorPerMarketUnits() > 10,
                "Fixture must require more than one bounded resilience-overlay step");

        long inventoryBefore = totalInventoryUnits(world);
        long walletsBefore = totalEntityWallets(world);
        long marketTargetsBefore = totalMarketTargets(world);
        long treasuryBefore = world.findFactionEconomicState(SOURCE).orElseThrow().treasuryMilliCredits();

        FactionPolicyReviewCoordinator.Report first = FactionPolicyReviewCoordinator.reviewPolicies(
                world, List.of(SOURCE));
        FactionPolicyReviewCoordinator.FactionReview firstFaction = first.factionReviews().get(0);

        assertEquals(1L, first.claimedReviewCount());
        assertTrue(firstFaction.reviewClaimed());
        assertTrue(firstFaction.stockResilienceReview().policyChanged());
        assertEquals(1, firstFaction.stockResilienceReview().increasedItemCount());
        assertEquals(0, firstFaction.stockResilienceReview().decreasedItemCount());
        assertEquals(10, overlayFloor(world, item.id()),
                "First review may raise only the automatic overlay by the conservative 10-unit step");
        assertEquals(basePolicy, world.findFactionStockProductionPolicy(SOURCE).orElseThrow(),
                "Automatic resilience review must not rewrite the base player/AI stock policy");
        assertEquals(inventoryBefore, totalInventoryUnits(world));
        assertEquals(walletsBefore, totalEntityWallets(world));
        assertEquals(marketTargetsBefore, totalMarketTargets(world),
                "Authoring the overlay must not materialize physical demand before explicit apply");
        assertEquals(treasuryBefore, world.findFactionEconomicState(SOURCE).orElseThrow().treasuryMilliCredits());

        FactionPolicyReviewCoordinator.Report repeated = FactionPolicyReviewCoordinator.reviewPolicies(
                world, List.of(SOURCE));
        assertEquals(0L, repeated.claimedReviewCount());
        assertEquals(0L, repeated.changedStockResiliencePolicyCount());
        assertEquals(10, overlayFloor(world, item.id()),
                "Same-window retry must not apply a second bounded overlay step");

        world.applyFactionStrategicPolicy(SOURCE);
        MarketComponent sourceMarketComponent = sourceMarket.getComponent(MarketComponent.class);
        assertEquals(5, sourceMarketComponent.configuredTargetStock[item.runtimeId()]);
        assertEquals(10, sourceMarketComponent.targetStock[item.runtimeId()],
                "Physical apply must combine baseline 5, base policy 7 and resilience overlay 10");

        satisfyOwnedMarketDemand(world, sourceRuntime, item.runtimeId());
        long secondReviewTick = world.findFactionPolicyReviewState(SOURCE).orElseThrow().lastPolicyReviewTick()
                + cadence.intervalTicks();
        advanceToTick(world, secondReviewTick);
        long secondInventoryBefore = totalInventoryUnits(world);
        long secondWalletsBefore = totalEntityWallets(world);
        long secondTargetsBefore = totalMarketTargets(world);

        FactionPolicyReviewCoordinator.Report second = FactionPolicyReviewCoordinator.reviewPolicies(
                world, List.of(SOURCE));

        assertEquals(1L, second.claimedReviewCount());
        assertEquals(1L, second.decreasedStockResilienceItemCount());
        assertEquals(5, overlayFloor(world, item.id()),
                "Recovery may reduce the automatic overlay by only five units per review");
        assertEquals(basePolicy, world.findFactionStockProductionPolicy(SOURCE).orElseThrow());
        assertEquals(secondInventoryBefore, totalInventoryUnits(world));
        assertEquals(secondWalletsBefore, totalEntityWallets(world));
        assertEquals(secondTargetsBefore, totalMarketTargets(world),
                "Downward overlay authoring must also remain physically pure until explicit apply");

        world.applyFactionStrategicPolicy(SOURCE);
        assertEquals(7, sourceMarketComponent.targetStock[item.runtimeId()],
                "Once overlay falls below base policy, the intentional base reserve must dominate");
        satisfyOwnedMarketDemand(world, sourceRuntime, item.runtimeId());

        long thirdReviewTick = world.findFactionPolicyReviewState(SOURCE).orElseThrow().lastPolicyReviewTick()
                + cadence.intervalTicks();
        advanceToTick(world, thirdReviewTick);
        FactionPolicyReviewCoordinator.Report third = FactionPolicyReviewCoordinator.reviewPolicies(
                world, List.of(SOURCE));

        assertEquals(1L, third.claimedReviewCount());
        assertEquals(1L, third.decreasedStockResilienceItemCount());
        assertTrue(world.findFactionResilienceDemandFloors(SOURCE).isEmpty(),
                "A disappeared resilience risk must eventually release its automatic overlay fully");
        assertEquals(basePolicy, world.findFactionStockProductionPolicy(SOURCE).orElseThrow());

        world.applyFactionStrategicPolicy(SOURCE);
        assertEquals(7, sourceMarketComponent.targetStock[item.runtimeId()],
                "Removing automatic overlay must leave the independent base stock policy intact");
    }

    private static int overlayFloor(WorldSimulation world, String itemContentId) {
        return world.findFactionResilienceDemandFloors(SOURCE).stream()
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
                        market.configuredTargetStock[itemId] = 0;
                        market.targetStock[itemId] = 0;
                        market.baseConsumption[itemId] = 0f;
                        market.consumptionRemainder[itemId] = 0d;
                    }
                }
                ProductionComponent production = entity.getComponent(ProductionComponent.class);
                if (production != null) {
                    production.activeRecipeIndex = -1;
                }
            }
        }
    }

    private static Entity addMarketStation(
            WorldSimulation world,
            StarSystemId systemId,
            String name,
            int factionRuntimeId,
            int itemId,
            int stock,
            int configuredTarget,
            int effectiveTarget,
            float sellPrice) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(itemId, configuredTarget, 0f);
        market.targetStock[itemId] = effectiveTarget;
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
        return live;
    }

    private static void satisfyOwnedMarketDemand(WorldSimulation world, int factionRuntimeId, int itemId) {
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                MarketComponent market = entity.getComponent(MarketComponent.class);
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (faction == null || faction.factionId != factionRuntimeId || market == null || inventory == null) {
                    continue;
                }
                inventory.stock[itemId] = Math.min(
                        Math.max(0, inventory.capacity),
                        Math.max(0, market.targetStock[itemId]));
            }
        }
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
        while (world.getAuthoritativeWorldTick() < targetTick && guard++ < 50_000) {
            world.advanceFrame(fixedStep);
        }
        assertTrue(world.getAuthoritativeWorldTick() >= targetTick, "World did not reach requested review tick");
    }
}
