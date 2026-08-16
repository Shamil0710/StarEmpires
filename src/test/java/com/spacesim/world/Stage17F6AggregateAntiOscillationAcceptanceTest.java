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
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F6AggregateAntiOscillationAcceptanceTest {
    private static final String SOURCE = "faction.neutral";
    private static final String CONCENTRATED = "faction.trade_league";
    private static final String ALTERNATIVE = "faction.miners";
    private static final long STATION_RESERVE = 100_000_000L;

    @Test
    void repeatedStressRecoveryAndSaveLoadRemainBoundedDeterministicAndExplicitlyScoped() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = world(content, 0x17F60051L);
        FactionPolicyReviewCadence cadence = FactionPolicyReviewCadence.defaultForFaction(SOURCE);
        advanceToTick(world, cadence.firstReviewOffsetTicks());
        clearExistingEconomicSignal(world);

        ContentCatalog.ItemDefinition item = content.getItems().get(0);
        int sourceRuntime = world.findFactionRuntimeId(SOURCE).orElseThrow();
        int concentratedRuntime = world.findFactionRuntimeId(CONCENTRATED).orElseThrow();
        int alternativeRuntime = world.findFactionRuntimeId(ALTERNATIVE).orElseThrow();
        Entity sourceMarket = addMarketStation(
                world,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                "17F6 aggregate source",
                sourceRuntime,
                item.runtimeId(),
                20,
                5,
                100,
                50f);
        addMarketStation(
                world,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                "17F6 aggregate concentrated",
                concentratedRuntime,
                item.runtimeId(),
                100,
                20,
                20,
                10f);
        addMarketStation(
                world,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                "17F6 aggregate alternative",
                alternativeRuntime,
                item.runtimeId(),
                60,
                20,
                20,
                20f);

        configureResilienceDoctrine(world);
        FactionStockProductionPolicyState existingStockPolicy =
                world.findFactionStockProductionPolicy(SOURCE).orElseThrow();
        FactionStockProductionPolicyState baseStockPolicy = new FactionStockProductionPolicyState(
                List.of(new FactionStockPolicyState(item.id(), 7)),
                existingStockPolicy.productionPolicies());
        world.updateFactionStockProductionPolicy(SOURCE, baseStockPolicy);
        FactionFiscalPolicyState initialFiscalPolicy = configureFiscalPolicy(world);
        setOwnedMarketWallets(world, SOURCE, 0L);

        assertTrue(FactionResiliencePlanner.analyze(world, SOURCE).items().stream()
                        .anyMatch(decision -> decision.itemContentId().equals(item.id())
                                && decision.recommendedTargetFloorPerMarketUnits() > 10),
                "Fixture must begin with a resilience shock that needs multiple bounded observations");
        assertFalse(world.findFactionPolicyReviewState(ALTERNATIVE).orElseThrow().reviewed());
        assertFalse(world.findFactionPolicyReviewState(CONCENTRATED).orElseThrow().reviewed());

        WorldSimulation deterministicTwin = restore(world.snapshot(), content);
        Totals firstBefore = totals(world);
        FactionPolicyReviewCoordinator.Report first = FactionPolicyReviewCoordinator.reviewPolicies(
                world, List.of(SOURCE, SOURCE));
        FactionPolicyReviewCoordinator.Report twinFirst = FactionPolicyReviewCoordinator.reviewPolicies(
                deterministicTwin, List.of(SOURCE));
        Totals firstAfter = totals(world);

        assertEquals(first, twinFirst,
                "Identical authoritative state must produce the same decision despite duplicate caller input");
        assertEquals(firstBefore, firstAfter,
                "Review authoring must not move cargo, wallets, treasury or physical market targets");
        assertEquals(1L, first.claimedReviewCount());
        assertEquals(1L, first.changedFiscalPolicyCount());
        assertEquals(1L, first.changedStockResiliencePolicyCount());
        assertEquals(FactionFiscalPolicyReviewer.Zone.STRESS,
                first.factionReviews().get(0).fiscalReview().zone());
        assertTrue(first.factionReviews().get(0).fiscalReview().resultingPolicy().stationTaxBasisPoints()
                        < initialFiscalPolicy.stationTaxBasisPoints(),
                "Liquidity stress must move station tax only toward the doctrine-backed stress target");
        assertTrue(first.factionReviews().get(0).fiscalReview().resultingPolicy()
                        .maxLiquiditySupportPerDecisionMilliCredits() > 0L,
                "Liquidity stress must open support authorization only by the bounded policy path");
        int firstOverlay = overlayFloorOrZero(world, item.id());
        assertEquals(10, firstOverlay);
        assertEquals(baseStockPolicy, world.findFactionStockProductionPolicy(SOURCE).orElseThrow());
        assertFalse(world.findFactionPolicyReviewState(ALTERNATIVE).orElseThrow().reviewed());
        assertFalse(world.findFactionPolicyReviewState(CONCENTRATED).orElseThrow().reviewed());

        FactionPolicyReviewCoordinator.Report sameWindow = FactionPolicyReviewCoordinator.reviewPolicies(
                world, List.of(SOURCE));
        assertEquals(0L, sameWindow.claimedReviewCount());
        assertEquals(0L, sameWindow.changedFiscalPolicyCount());
        assertEquals(0L, sameWindow.changedStockResiliencePolicyCount());
        assertEquals(firstOverlay, overlayFloorOrZero(world, item.id()));

        Totals beforePhysicalApply = totals(world);
        world.applyFactionStrategicPolicy(SOURCE);
        Totals afterPhysicalApply = totals(world);
        assertEquals(beforePhysicalApply.inventoryUnits(), afterPhysicalApply.inventoryUnits());
        assertEquals(beforePhysicalApply.entityWallets(), afterPhysicalApply.entityWallets());
        assertEquals(beforePhysicalApply.treasury(), afterPhysicalApply.treasury());
        assertEquals(10, sourceMarket.getComponent(MarketComponent.class).targetStock[item.runtimeId()],
                "Explicit ordinary apply must recompute effective demand from current configured/base/overlay sources");

        long secondTick = world.findFactionPolicyReviewState(SOURCE).orElseThrow().lastPolicyReviewTick()
                + cadence.intervalTicks();
        advanceToTick(world, secondTick);
        reimposeDependencyShock(world, sourceRuntime, item.runtimeId());
        setOwnedMarketWallets(world, SOURCE, 0L);
        int overlayBeforeSecond = overlayFloorOrZero(world, item.id());
        int taxBeforeSecond = world.findFactionFiscalPolicy(SOURCE).orElseThrow().stationTaxBasisPoints();
        Totals secondBefore = totals(world);

        FactionPolicyReviewCoordinator.Report second = FactionPolicyReviewCoordinator.reviewPolicies(
                world, List.of(SOURCE));

        assertEquals(secondBefore, totals(world));
        assertEquals(1L, second.claimedReviewCount());
        assertEquals(FactionFiscalPolicyReviewer.Zone.STRESS,
                second.factionReviews().get(0).fiscalReview().zone());
        assertTrue(world.findFactionFiscalPolicy(SOURCE).orElseThrow().stationTaxBasisPoints() <= taxBeforeSecond);
        int overlayAfterSecond = overlayFloorOrZero(world, item.id());
        int overlayDelta = overlayAfterSecond - overlayBeforeSecond;
        assertTrue(overlayDelta <= 10,
                "A later observation may add at most one configured resilience increase step");
        assertTrue(overlayDelta >= -5,
                "A later observation may release at most one configured resilience decrease step");

        WorldSimulation restored = restore(world.snapshot(), content);
        assertEquals(world.findFactionFiscalPolicy(SOURCE), restored.findFactionFiscalPolicy(SOURCE));
        assertEquals(world.findFactionResilienceDemandFloors(SOURCE), restored.findFactionResilienceDemandFloors(SOURCE));
        assertEquals(world.findFactionPolicyReviewState(SOURCE), restored.findFactionPolicyReviewState(SOURCE));
        assertEquals(baseStockPolicy, restored.findFactionStockProductionPolicy(SOURCE).orElseThrow());
        FactionPolicyReviewCoordinator.Report immediateAfterLoad = FactionPolicyReviewCoordinator.reviewPolicies(
                restored, List.of(SOURCE));
        assertEquals(0L, immediateAfterLoad.claimedReviewCount(),
                "Save/load must preserve the claimed observation window and prevent a duplicate step");

        world = restored;
        int previousOverlay = overlayFloorOrZero(world, item.id());
        int recoveryWindows = 0;
        while (previousOverlay > 0 && recoveryWindows++ < 10) {
            satisfyOwnedMarketDemand(world, sourceRuntime, item.runtimeId());
            setOwnedMarketWallets(world, SOURCE, STATION_RESERVE);
            long dueTick = world.findFactionPolicyReviewState(SOURCE).orElseThrow().lastPolicyReviewTick()
                    + cadence.intervalTicks();
            advanceToTick(world, dueTick);
            satisfyOwnedMarketDemand(world, sourceRuntime, item.runtimeId());
            setOwnedMarketWallets(world, SOURCE, STATION_RESERVE);

            FactionFiscalReviewProfile recoveryProfile =
                    WorldFactionFiscalReviewProfileSelector.select(world, SOURCE);
            FactionFiscalPolicyState fiscalBefore = world.findFactionFiscalPolicy(SOURCE).orElseThrow();
            Totals recoveryBefore = totals(world);
            FactionPolicyReviewCoordinator.Report recovery = FactionPolicyReviewCoordinator.reviewPolicies(
                    world, List.of(SOURCE));
            Totals recoveryAfter = totals(world);

            assertEquals(recoveryBefore, recoveryAfter,
                    "Recovery review authoring must stay physically pure");
            assertEquals(1L, recovery.claimedReviewCount());
            assertEquals(FactionFiscalPolicyReviewer.Zone.NORMAL,
                    recovery.factionReviews().get(0).fiscalReview().zone());
            FactionFiscalPolicyState fiscalAfter = world.findFactionFiscalPolicy(SOURCE).orElseThrow();
            assertMovesToward(
                    fiscalBefore.stationTaxBasisPoints(),
                    fiscalAfter.stationTaxBasisPoints(),
                    recoveryProfile.normalStationTaxTargetBasisPoints());
            assertMovesToward(
                    fiscalBefore.maxLiquiditySupportPerDecisionMilliCredits(),
                    fiscalAfter.maxLiquiditySupportPerDecisionMilliCredits(),
                    recoveryProfile.normalLiquiditySupportCapMilliCredits());

            int currentOverlay = overlayFloorOrZero(world, item.id());
            assertTrue(currentOverlay <= previousOverlay,
                    "Recovery must never increase an automatic resilience overlay");
            assertTrue(previousOverlay - currentOverlay <= 5,
                    "Recovery may release at most one configured downward step per review");
            previousOverlay = currentOverlay;
            assertEquals(baseStockPolicy, world.findFactionStockProductionPolicy(SOURCE).orElseThrow());
            assertFalse(world.findFactionPolicyReviewState(ALTERNATIVE).orElseThrow().reviewed());
            assertFalse(world.findFactionPolicyReviewState(CONCENTRATED).orElseThrow().reviewed());
        }

        assertEquals(0, previousOverlay,
                "Persistent recovery must eventually release the temporary resilience overlay completely");
        assertTrue(recoveryWindows <= 10, "Recovery must converge in a bounded number of review windows");
        world.applyFactionStrategicPolicy(SOURCE);
        Entity restoredSourceMarket = findNamedEntity(world, "17F6 aggregate source");
        assertEquals(7, restoredSourceMarket.getComponent(MarketComponent.class).targetStock[item.runtimeId()],
                "After recovery, physical demand must fall back to the independent base stock floor");
        assertEquals(baseStockPolicy, world.findFactionStockProductionPolicy(SOURCE).orElseThrow());
    }

    private static WorldSimulation world(ContentCatalog content, long seed) {
        return WorldSimulation.restore(
                DemoGalaxyFactory.createState(seed, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    private static WorldSimulation restore(WorldState state, ContentCatalog content) {
        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(state));
        return WorldSimulation.restore(
                decoded,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    private static void configureResilienceDoctrine(WorldSimulation world) {
        FactionDoctrineState doctrine = world.findFactionStrategicState(SOURCE).orElseThrow().doctrine();
        world.updateFactionDoctrine(SOURCE, new FactionDoctrineState(
                doctrine.tradeOpenness(),
                doctrine.securityPosture(),
                doctrine.expansionPreference(),
                doctrine.sovereigntySensitivity(),
                doctrine.treatyLegalism(),
                doctrine.interventionism(),
                80));
    }

    private static FactionFiscalPolicyState configureFiscalPolicy(WorldSimulation world) {
        FactionFiscalPolicyState base = world.findFactionFiscalPolicy(SOURCE).orElseThrow();
        FactionFiscalPolicyState configured = new FactionFiscalPolicyState(
                2_000,
                base.foreignTerritoryLevyBasisPoints(),
                base.treasuryReserveFloorMilliCredits(),
                STATION_RESERVE,
                0L,
                base.maxConstructionInvestmentPerDecisionMilliCredits());
        world.updateFactionFiscalPolicy(SOURCE, configured);
        return configured;
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

    private static Entity findNamedEntity(WorldSimulation world, String name) {
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                IdentityComponent identity = entity.getComponent(IdentityComponent.class);
                if (identity != null && name.equals(identity.name)) {
                    return entity;
                }
            }
        }
        throw new AssertionError("Entity not found: " + name);
    }

    private static void setOwnedMarketWallets(WorldSimulation world, String factionContentId, long targetBalance) {
        int runtimeFactionId = world.findFactionRuntimeId(factionContentId).orElseThrow();
        int touched = 0;
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                MarketComponent market = entity.getComponent(MarketComponent.class);
                WalletComponent wallet = entity.getComponent(WalletComponent.class);
                if (faction == null || faction.factionId != runtimeFactionId || market == null || wallet == null) {
                    continue;
                }
                long balance = wallet.getBalanceMilliCredits();
                if (balance > targetBalance) {
                    assertTrue(wallet.debitToSink(balance - targetBalance));
                } else if (balance < targetBalance) {
                    assertTrue(wallet.creditFromSource(targetBalance - balance));
                }
                touched++;
            }
        }
        assertTrue(touched > 0, "Fixture faction must own at least one market station");
    }

    private static void reimposeDependencyShock(WorldSimulation world, int sourceRuntime, int itemId) {
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                MarketComponent market = entity.getComponent(MarketComponent.class);
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (faction == null || faction.factionId != sourceRuntime || market == null || inventory == null) {
                    continue;
                }
                inventory.stock[itemId] = 0;
            }
        }
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

    private static int overlayFloorOrZero(WorldSimulation world, String itemContentId) {
        return world.findFactionResilienceDemandFloors(SOURCE).stream()
                .filter(stock -> stock.itemContentId().equals(itemContentId))
                .findFirst()
                .map(FactionStockPolicyState::targetStockFloor)
                .orElse(0);
    }

    private static Totals totals(WorldSimulation world) {
        long inventoryUnits = 0L;
        long entityWallets = 0L;
        long marketTargets = 0L;
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (inventory != null) {
                    for (int units : inventory.stock) {
                        inventoryUnits = Math.addExact(inventoryUnits, units);
                    }
                }
                WalletComponent wallet = entity.getComponent(WalletComponent.class);
                if (wallet != null) {
                    entityWallets = Math.addExact(entityWallets, wallet.getBalanceMilliCredits());
                }
                MarketComponent market = entity.getComponent(MarketComponent.class);
                if (market != null) {
                    for (int target : market.targetStock) {
                        marketTargets = Math.addExact(marketTargets, target);
                    }
                }
            }
        }
        long treasury = world.findFactionEconomicState(SOURCE).orElseThrow().treasuryMilliCredits();
        return new Totals(inventoryUnits, entityWallets, marketTargets, treasury);
    }

    private static void assertMovesToward(int before, int after, int target) {
        if (before < target) {
            assertTrue(after >= before && after <= target);
        } else if (before > target) {
            assertTrue(after <= before && after >= target);
        } else {
            assertEquals(target, after);
        }
    }

    private static void assertMovesToward(long before, long after, long target) {
        if (before < target) {
            assertTrue(after >= before && after <= target);
        } else if (before > target) {
            assertTrue(after <= before && after >= target);
        } else {
            assertEquals(target, after);
        }
    }

    private static void advanceToTick(WorldSimulation world, long targetTick) {
        float fixedStep = world.findSession(world.getActiveSystemId()).orElseThrow()
                .getClock().getFixedStepSeconds();
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick && guard++ < 80_000) {
            world.advanceFrame(fixedStep);
        }
        assertTrue(world.getAuthoritativeWorldTick() >= targetTick, "World did not reach requested review tick");
    }

    private record Totals(long inventoryUnits, long entityWallets, long marketTargets, long treasury) {
    }
}
