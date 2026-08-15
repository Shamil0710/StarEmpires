package com.spacesim.trade;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.world.GalacticPath;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F5SupplierDiversificationAcceptanceTest {
    private static final StarSystemId SUPPLY_SYSTEM = new StarSystemId(1L);
    private static final StarSystemId DEMAND_SYSTEM = new StarSystemId(2L);
    private static final int CONCENTRATED_SUPPLIER = 1;
    private static final int DIVERSE_SUPPLIER = 2;
    private static final int TRADER_FACTION = 3;
    private static final int FOREIGN_CONSUMER = 4;

    private final ContentCatalog content = ContentCatalogLoader.loadDefault();
    private long nextId = 60_000L;

    @Test
    void measuredResilienceBudgetCanChooseMoreExpensiveRealSupplierWithoutMutatingEconomy() {
        Entity concentrated = station(100, 100, 0f, 8f, CONCENTRATED_SUPPLIER);
        Entity alternative = station(100, 100, 0f, 10f, DIVERSE_SUPPLIER);
        Entity consumer = station(0, 100, 20f, 22f, TRADER_FACTION);
        List<GalacticTradeOpportunity> opportunities = opportunities(concentrated, alternative, consumer);
        FleetTradeProfile fleet = fleet();

        long concentratedWalletBefore = wallet(concentrated);
        long alternativeWalletBefore = wallet(alternative);
        long consumerWalletBefore = wallet(consumer);
        int concentratedStockBefore = stock(concentrated);
        int alternativeStockBefore = stock(alternative);
        int consumerStockBefore = stock(consumer);

        TradeRoutePlanner economic = new TradeRoutePlanner(
                content,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                TradeRouteCostModel.none());
        FactionResilientGalacticTradePlanner planner = new FactionResilientGalacticTradePlanner(
                economic,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                policy(30_000L));

        FactionResilientGalacticTradePlanner.Selection selection = planner
                .selectBestGalacticRoute(fleet, opportunities)
                .orElseThrow();

        assertEquals(id(concentrated), selection.economicBaseline().buyStationId());
        assertEquals(id(alternative), selection.selectedRoute().buyStationId());
        assertEquals(9_000, selection.baselineSupplierShareBasisPoints());
        assertEquals(3_000, selection.selectedSupplierShareBasisPoints());
        assertEquals(20_000L, selection.actualProfitSacrificeMilliCredits());
        assertEquals(30_000L, selection.acceptableProfitSacrificeMilliCredits());
        assertTrue(selection.diversificationApplied());

        assertEquals(concentratedWalletBefore, wallet(concentrated));
        assertEquals(alternativeWalletBefore, wallet(alternative));
        assertEquals(consumerWalletBefore, wallet(consumer));
        assertEquals(concentratedStockBefore, stock(concentrated));
        assertEquals(alternativeStockBefore, stock(alternative));
        assertEquals(consumerStockBefore, stock(consumer));
    }

    @Test
    void diversificationCannotSpendMoreExpectedProfitThanMeasuredPolicyAllows() {
        Entity concentrated = station(100, 100, 0f, 8f, CONCENTRATED_SUPPLIER);
        Entity alternative = station(100, 100, 0f, 10f, DIVERSE_SUPPLIER);
        Entity consumer = station(0, 100, 20f, 22f, TRADER_FACTION);
        TradeRoutePlanner economic = new TradeRoutePlanner(
                content,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                TradeRouteCostModel.none());
        FactionResilientGalacticTradePlanner planner = new FactionResilientGalacticTradePlanner(
                economic,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                policy(10_000L));

        FactionResilientGalacticTradePlanner.Selection selection = planner
                .selectBestGalacticRoute(fleet(), opportunities(concentrated, alternative, consumer))
                .orElseThrow();

        assertEquals(id(concentrated), selection.selectedRoute().buyStationId());
        assertEquals(selection.economicBaseline(), selection.selectedRoute());
        assertEquals(0L, selection.actualProfitSacrificeMilliCredits());
        assertFalse(selection.diversificationApplied());
    }

    @Test
    void diversificationNeverInventsSupplierWhenNoPhysicalAlternativeExists() {
        Entity concentrated = station(100, 100, 0f, 8f, CONCENTRATED_SUPPLIER);
        Entity consumer = station(0, 100, 20f, 22f, TRADER_FACTION);
        MarketDirectory suppliers = directory(concentrated);
        MarketDirectory consumers = directory(consumer);
        GalacticPath path = new GalacticPath(List.of(SUPPLY_SYSTEM, DEMAND_SYSTEM), 20L, 2d, 25d);
        List<GalacticTradeOpportunity> opportunities = List.of(
                opportunity(suppliers, concentrated, consumers, consumer, path));
        TradeRoutePlanner economic = new TradeRoutePlanner(
                content,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                TradeRouteCostModel.none());
        FactionResilientGalacticTradePlanner planner = new FactionResilientGalacticTradePlanner(
                economic,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                policy(1_000_000L));

        FactionResilientGalacticTradePlanner.Selection selection = planner
                .selectBestGalacticRoute(fleet(), opportunities)
                .orElseThrow();

        assertEquals(id(concentrated), selection.selectedRoute().buyStationId());
        assertFalse(selection.diversificationApplied());
    }

    @Test
    void resilienceDoesNotRewriteOrdinaryTradeToForeignConsumer() {
        Entity concentrated = station(100, 100, 0f, 8f, CONCENTRATED_SUPPLIER);
        Entity alternative = station(100, 100, 0f, 10f, DIVERSE_SUPPLIER);
        Entity foreignConsumer = station(0, 100, 20f, 22f, FOREIGN_CONSUMER);
        TradeRoutePlanner economic = new TradeRoutePlanner(
                content,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                TradeRouteCostModel.none());
        FactionResilientGalacticTradePlanner planner = new FactionResilientGalacticTradePlanner(
                economic,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                policy(1_000_000L));

        FactionResilientGalacticTradePlanner.Selection selection = planner
                .selectBestGalacticRoute(
                        fleet(),
                        opportunities(concentrated, alternative, foreignConsumer))
                .orElseThrow();

        assertEquals(id(concentrated), selection.economicBaseline().buyStationId());
        assertEquals(selection.economicBaseline(), selection.selectedRoute());
        assertEquals(0L, selection.actualProfitSacrificeMilliCredits());
        assertFalse(selection.diversificationApplied());
    }

    private SupplierDiversificationPolicy policy(long acceptedSacrifice) {
        return (fleet, supplierFactionId, itemId) -> {
            if (fleet.factionId() != TRADER_FACTION || itemId != Constants.ITEM_FOOD) {
                return SupplierDiversificationPolicy.Assessment.inactive();
            }
            if (supplierFactionId == CONCENTRATED_SUPPLIER) {
                return new SupplierDiversificationPolicy.Assessment(true, 9_000, acceptedSacrifice);
            }
            if (supplierFactionId == DIVERSE_SUPPLIER) {
                return new SupplierDiversificationPolicy.Assessment(true, 3_000, acceptedSacrifice);
            }
            return SupplierDiversificationPolicy.Assessment.inactive();
        };
    }

    private List<GalacticTradeOpportunity> opportunities(
            Entity concentrated,
            Entity alternative,
            Entity consumer) {
        MarketDirectory suppliers = directory(concentrated, alternative);
        MarketDirectory consumers = directory(consumer);
        GalacticPath path = new GalacticPath(List.of(SUPPLY_SYSTEM, DEMAND_SYSTEM), 20L, 2d, 25d);
        return List.of(
                opportunity(suppliers, concentrated, consumers, consumer, path),
                opportunity(suppliers, alternative, consumers, consumer, path));
    }

    private GalacticTradeOpportunity opportunity(
            MarketDirectory suppliers,
            Entity supplier,
            MarketDirectory consumers,
            Entity consumer,
            GalacticPath path) {
        return new GalacticTradeOpportunity(
                new SystemMarketRef(SUPPLY_SYSTEM, suppliers.find(id(supplier))),
                new SystemMarketRef(DEMAND_SYSTEM, consumers.find(id(consumer))),
                Constants.ITEM_FOOD,
                path,
                0f,
                0d,
                0);
    }

    private FleetTradeProfile fleet() {
        return new FleetTradeProfile(
                0f,
                0f,
                20f,
                Money.fromCredits(100_000d),
                10,
                0,
                10,
                Constants.ITEM_FOOD,
                false,
                null,
                TRADER_FACTION,
                new int[Constants.MAX_ITEMS],
                new float[Constants.FACTION_RUNTIME_CAPACITY]);
    }

    private MarketDirectory directory(Entity... stations) {
        MarketDirectory directory = new MarketDirectory(content);
        directory.rebuild(List.of(stations));
        return directory;
    }

    private Entity station(
            int stock,
            int targetStock,
            float buyPrice,
            float sellPrice,
            int factionId) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        inventory.stock[Constants.ITEM_FOOD] = stock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, targetStock, 0f);
        market.buyPrices[Constants.ITEM_FOOD] = buyPrice;
        market.sellPrices[Constants.ITEM_FOOD] = sellPrice;
        market.isDirty = false;
        return new Entity()
                .add(new EntityIdComponent(new EntityId(nextId++)))
                .add(new TransformComponent())
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(100_000d)))
                .add(new FactionComponent(factionId));
    }

    private static EntityId id(Entity entity) {
        return entity.getComponent(EntityIdComponent.class).id;
    }

    private static int stock(Entity entity) {
        return entity.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD];
    }

    private static long wallet(Entity entity) {
        return entity.getComponent(WalletComponent.class).getBalanceMilliCredits();
    }
}
