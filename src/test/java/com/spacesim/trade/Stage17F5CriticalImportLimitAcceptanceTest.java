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

class Stage17F5CriticalImportLimitAcceptanceTest {
    private static final StarSystemId SUPPLY_SYSTEM = new StarSystemId(1L);
    private static final StarSystemId DEMAND_SYSTEM = new StarSystemId(2L);
    private static final int CONCENTRATED_SUPPLIER = 1;
    private static final int ALTERNATIVE_SUPPLIER = 2;
    private static final int TRADER_FACTION = 3;
    private static final int FOREIGN_CONSUMER = 4;

    private final ContentCatalog content = ContentCatalogLoader.loadDefault();
    private long nextId = 70_000L;

    @Test
    void hardLimitChoosesMoreExpensiveAuthorizedSupplierWithoutMutatingEconomy() {
        Entity concentrated = station(100, 100, 0f, 8f, CONCENTRATED_SUPPLIER);
        Entity alternative = station(100, 100, 0f, 10f, ALTERNATIVE_SUPPLIER);
        Entity consumer = station(0, 100, 22f, 23f, TRADER_FACTION);
        List<GalacticTradeOpportunity> opportunities = opportunities(concentrated, alternative, consumer);
        FleetTradeProfile fleet = fleet();
        TradeRoutePlanner economic = planner();

        GalacticTradeRoute unrestricted = economic.findBestGalacticRoute(fleet, opportunities).orElseThrow();
        assertEquals(id(concentrated), unrestricted.buyStationId());
        long concentratedWalletBefore = wallet(concentrated);
        long alternativeWalletBefore = wallet(alternative);
        long consumerWalletBefore = wallet(consumer);
        int concentratedStockBefore = stock(concentrated);
        int alternativeStockBefore = stock(alternative);
        int consumerStockBefore = stock(consumer);

        FactionResilientGalacticTradePlanner resilient = new FactionResilientGalacticTradePlanner(
                economic,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                SupplierDiversificationPolicy.none(),
                hardLimit());
        GalacticTradeRoute selected = resilient.findBestGalacticRoute(fleet, opportunities).orElseThrow();

        assertEquals(id(alternative), selected.buyStationId());
        assertTrue(selected.netProfitMilliCredits() < unrestricted.netProfitMilliCredits());
        assertEquals(concentratedWalletBefore, wallet(concentrated));
        assertEquals(alternativeWalletBefore, wallet(alternative));
        assertEquals(consumerWalletBefore, wallet(consumer));
        assertEquals(concentratedStockBefore, stock(concentrated));
        assertEquals(alternativeStockBefore, stock(alternative));
        assertEquals(consumerStockBefore, stock(consumer));
    }

    @Test
    void hardLimitLeavesRealShortageWhenNoAuthorizedSupplierExists() {
        Entity concentrated = station(100, 100, 0f, 8f, CONCENTRATED_SUPPLIER);
        Entity consumer = station(0, 100, 22f, 23f, TRADER_FACTION);
        List<GalacticTradeOpportunity> opportunities = opportunities(List.of(concentrated), consumer);
        FleetTradeProfile fleet = fleet();
        long supplierWalletBefore = wallet(concentrated);
        long consumerWalletBefore = wallet(consumer);
        int supplierStockBefore = stock(concentrated);
        int consumerStockBefore = stock(consumer);

        FactionResilientGalacticTradePlanner resilient = new FactionResilientGalacticTradePlanner(
                planner(),
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                SupplierDiversificationPolicy.none(),
                hardLimit());

        assertTrue(resilient.findBestGalacticRoute(fleet, opportunities).isEmpty());
        assertEquals(supplierWalletBefore, wallet(concentrated));
        assertEquals(consumerWalletBefore, wallet(consumer));
        assertEquals(supplierStockBefore, stock(concentrated));
        assertEquals(consumerStockBefore, stock(consumer));
    }

    @Test
    void domesticSupplyAndNonCriticalCommodityRemainUnrestricted() {
        Entity domestic = station(100, 100, 0f, 8f, TRADER_FACTION);
        Entity consumer = station(0, 100, 22f, 23f, TRADER_FACTION);
        GalacticTradeOpportunity domesticOpportunity = opportunities(List.of(domestic), consumer).get(0);
        CriticalImportLimitPolicy policy = (fleet, supplierFactionId, itemId) -> {
            if (supplierFactionId == TRADER_FACTION || itemId != Constants.ITEM_FOOD) {
                return CriticalImportLimitPolicy.Assessment.inactive();
            }
            return new CriticalImportLimitPolicy.Assessment(true, 9_000, 6_000);
        };
        FactionResilientGalacticTradePlanner resilient = new FactionResilientGalacticTradePlanner(
                planner(),
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                SupplierDiversificationPolicy.none(),
                policy);

        assertEquals(
                id(domestic),
                resilient.findBestGalacticRoute(fleet(), List.of(domesticOpportunity))
                        .orElseThrow().buyStationId());

        CriticalImportLimitPolicy.Assessment inactive = policy.assess(
                fleet(), CONCENTRATED_SUPPLIER, Constants.ITEM_ENERGY);
        assertFalse(inactive.active());
        assertTrue(inactive.authorized());
    }

    @Test
    void hardImportPolicyDoesNotRestrictOrdinaryExportToForeignConsumer() {
        Entity concentrated = station(100, 100, 0f, 8f, CONCENTRATED_SUPPLIER);
        Entity foreignConsumer = station(0, 100, 22f, 23f, FOREIGN_CONSUMER);
        List<GalacticTradeOpportunity> opportunities = opportunities(List.of(concentrated), foreignConsumer);
        FactionResilientGalacticTradePlanner resilient = new FactionResilientGalacticTradePlanner(
                planner(),
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                SupplierDiversificationPolicy.none(),
                (fleet, supplierFactionId, itemId) ->
                        new CriticalImportLimitPolicy.Assessment(true, 10_000, 0));

        GalacticTradeRoute selected = resilient.findBestGalacticRoute(fleet(), opportunities).orElseThrow();

        assertEquals(id(concentrated), selected.buyStationId());
        assertEquals(id(foreignConsumer), selected.sellStationId());
    }

    private CriticalImportLimitPolicy hardLimit() {
        return (fleet, supplierFactionId, itemId) -> {
            if (fleet.factionId() != TRADER_FACTION || itemId != Constants.ITEM_FOOD) {
                return CriticalImportLimitPolicy.Assessment.inactive();
            }
            if (supplierFactionId == CONCENTRATED_SUPPLIER) {
                return new CriticalImportLimitPolicy.Assessment(true, 9_000, 6_000);
            }
            if (supplierFactionId == ALTERNATIVE_SUPPLIER) {
                return new CriticalImportLimitPolicy.Assessment(true, 3_000, 6_000);
            }
            return CriticalImportLimitPolicy.Assessment.inactive();
        };
    }

    private TradeRoutePlanner planner() {
        return new TradeRoutePlanner(
                content,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                TradeRouteCostModel.none());
    }

    private List<GalacticTradeOpportunity> opportunities(
            Entity concentrated,
            Entity alternative,
            Entity consumer) {
        return opportunities(List.of(concentrated, alternative), consumer);
    }

    private List<GalacticTradeOpportunity> opportunities(
            List<Entity> suppliers,
            Entity consumer) {
        MarketDirectory supplierDirectory = directory(suppliers.toArray(Entity[]::new));
        MarketDirectory consumerDirectory = directory(consumer);
        GalacticPath path = new GalacticPath(List.of(SUPPLY_SYSTEM, DEMAND_SYSTEM), 20L, 2d, 25d);
        return suppliers.stream()
                .map(supplier -> new GalacticTradeOpportunity(
                        new SystemMarketRef(SUPPLY_SYSTEM, supplierDirectory.find(id(supplier))),
                        new SystemMarketRef(DEMAND_SYSTEM, consumerDirectory.find(id(consumer))),
                        Constants.ITEM_FOOD,
                        path,
                        0f,
                        0d,
                        0))
                .toList();
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
