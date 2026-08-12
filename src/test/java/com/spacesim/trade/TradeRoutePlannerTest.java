package com.spacesim.trade;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeRoutePlannerTest {
    private final ContentCatalog catalog = ContentCatalogLoader.loadDefault();
    private long nextId = 1L;

    @Test
    void grossProfitИProfitPerSecondНамеренноВыбираютРазныеМаршруты() {
        Entity supplier = station(0f, 100, 100, 1f, 10f, 100_000d);
        Entity nearConsumer = station(100f, 0, 100, 20f, 30f, 100_000d);
        Entity farConsumer = station(2_000f, 0, 100, 25f, 30f, 100_000d);
        MarketDirectory directory = directory(supplier, nearConsumer, farConsumer);
        FleetTradeProfile fleet = fleetProfile(0f, 100f, 1_000d, 10);

        TradeRoute gross = new TradeRoutePlanner(
                catalog, TradeRoutePlanner.ScoringMode.GROSS_PROFIT)
                .findBestNewCargoRoute(fleet, directory)
                .orElseThrow();
        TradeRoute perSecond = new TradeRoutePlanner(
                catalog, TradeRoutePlanner.ScoringMode.PROFIT_PER_SECOND)
                .findBestNewCargoRoute(fleet, directory)
                .orElseThrow();

        assertEquals(id(supplier), gross.buyStationId());
        assertEquals(id(farConsumer), gross.sellStationId());
        assertEquals(Money.fromCredits(150d), gross.grossProfitMilliCredits());
        assertEquals(gross.grossProfitMilliCredits(), gross.netProfitMilliCredits());

        assertEquals(id(supplier), perSecond.buyStationId());
        assertEquals(id(nearConsumer), perSecond.sellStationId());
        assertEquals(Money.fromCredits(100d), perSecond.grossProfitMilliCredits());
        assertEquals(perSecond.grossProfitMilliCredits(), perSecond.netProfitMilliCredits());
        assertTrue(perSecond.netProfitPerSecond() > gross.netProfitPerSecond());
    }

    @Test
    void routeCostModelМожетИзменитьЛучшийМаршрутБезИзмененияFsm() {
        Entity supplier = station(0f, 100, 100, 1f, 10f, 100_000d);
        Entity nearConsumer = station(100f, 0, 100, 20f, 30f, 100_000d);
        Entity farConsumer = station(2_000f, 0, 100, 25f, 30f, 100_000d);
        MarketDirectory directory = directory(supplier, nearConsumer, farConsumer);
        FleetTradeProfile fleet = fleetProfile(0f, 100f, 1_000d, 10);
        TradeRouteCostModel costs = (profile, context) -> context.travelDistance() > 500f
                ? Money.fromCredits(120d)
                : Money.fromCredits(20d);

        TradeRoute route = new TradeRoutePlanner(
                catalog,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                costs)
                .findBestNewCargoRoute(fleet, directory)
                .orElseThrow();

        assertEquals(id(nearConsumer), route.sellStationId());
        assertEquals(Money.fromCredits(100d), route.grossProfitMilliCredits());
        assertEquals(Money.fromCredits(20d), route.estimatedRouteCostMilliCredits());
        assertEquals(Money.fromCredits(80d), route.netProfitMilliCredits());
    }

    @Test
    void existingCargoИспользуетRevenuePerSecondАНеТолькоМаксимальнуюВыручку() {
        Entity nearConsumer = station(100f, 0, 100, 20f, 30f, 100_000d);
        Entity farConsumer = station(2_000f, 0, 100, 25f, 30f, 100_000d);
        MarketDirectory directory = directory(nearConsumer, farConsumer);
        FleetTradeProfile loadedFleet = fleetProfileWithFoodCargo(0f, 100f, 1_000d, 10);

        TradeSaleRoute gross = new TradeRoutePlanner(
                catalog, TradeRoutePlanner.ScoringMode.GROSS_PROFIT)
                .findBestExistingCargoSale(loadedFleet, directory)
                .orElseThrow();
        TradeSaleRoute perSecond = new TradeRoutePlanner(
                catalog, TradeRoutePlanner.ScoringMode.PROFIT_PER_SECOND)
                .findBestExistingCargoSale(loadedFleet, directory)
                .orElseThrow();

        assertEquals(id(farConsumer), gross.sellStationId());
        assertEquals(Money.fromCredits(250d), gross.saleRevenueMilliCredits());
        assertEquals(gross.saleRevenueMilliCredits(), gross.netRevenueMilliCredits());

        assertEquals(id(nearConsumer), perSecond.sellStationId());
        assertEquals(Money.fromCredits(200d), perSecond.saleRevenueMilliCredits());
        assertEquals(perSecond.saleRevenueMilliCredits(), perSecond.netRevenueMilliCredits());
        assertTrue(perSecond.netRevenuePerSecond() > gross.netRevenuePerSecond());
    }

    @Test
    void plannerСохраняетЛимитыCargoDemandИLiquidity() {
        Entity supplier = station(0f, 100, 100, 1f, 10f, 100_000d);
        Entity consumer = station(100f, 98, 100, 20f, 30f, 50d);
        MarketDirectory directory = directory(supplier, consumer);
        FleetTradeProfile fleet = fleetProfile(0f, 100f, 25d, 10);

        TradeRoute route = new TradeRoutePlanner(
                catalog, TradeRoutePlanner.ScoringMode.GROSS_PROFIT)
                .findBestNewCargoRoute(fleet, directory)
                .orElseThrow();

        assertEquals(2, route.amount());
        assertEquals(Money.fromCredits(20d), route.grossProfitMilliCredits());
    }

    @Test
    void directoryЯвляетсяDefensiveSnapshotИИндексируетSupplyDemand() {
        Entity supplier = station(0f, 7, 100, 1f, 10f, 100_000d);
        Entity consumer = station(100f, 0, 100, 20f, 30f, 100_000d);
        MarketDirectory directory = directory(supplier, consumer);
        EntityId supplierId = id(supplier);

        MarketDirectory.StationMarket snapshot = directory.find(supplierId);
        assertNotNull(snapshot);
        assertEquals(7, snapshot.stock(Constants.ITEM_FOOD));
        assertEquals(10f, snapshot.sellPrice(Constants.ITEM_FOOD), 0f);
        assertEquals(List.of(snapshot), directory.suppliers(Constants.ITEM_FOOD));
        assertEquals(id(consumer), directory.consumers(Constants.ITEM_FOOD).get(0).id());

        supplier.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD] = 0;
        supplier.getComponent(MarketComponent.class).sellPrices[Constants.ITEM_FOOD] = 999f;

        assertEquals(7, snapshot.stock(Constants.ITEM_FOOD));
        assertEquals(10f, snapshot.sellPrice(Constants.ITEM_FOOD), 0f);
    }

    @Test
    void opportunityShortlistЛинейноОграниченЧисломSuppliers() {
        List<Entity> markets = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            markets.add(station(index * 10f, 100, 100, 1f, 10f + index * 0.01f, 100_000d));
        }
        for (int index = 0; index < 40; index++) {
            markets.add(station(1_000f + index * 10f, 0, 100, 20f + index * 0.01f, 30f, 100_000d));
        }

        MarketDirectory directory = new MarketDirectory(catalog);
        directory.rebuild(markets);
        int suppliers = directory.suppliers(Constants.ITEM_FOOD).size();
        int opportunities = directory.opportunities(Constants.ITEM_FOOD).size();

        assertEquals(40, suppliers);
        assertTrue(opportunities > 0);
        assertTrue(opportunities <= suppliers * MarketDirectory.MAX_CONSUMERS_PER_SUPPLIER);
        assertTrue(opportunities < suppliers * directory.consumers(Constants.ITEM_FOOD).size());
    }

    private MarketDirectory directory(Entity... stations) {
        MarketDirectory directory = new MarketDirectory(catalog);
        directory.rebuild(List.of(stations));
        return directory;
    }

    private FleetTradeProfile fleetProfile(float x, float speed, double credits, int capacity) {
        return new FleetTradeProfile(
                x,
                0f,
                speed,
                Money.fromCredits(credits),
                capacity,
                0,
                capacity,
                Constants.ITEM_FOOD,
                false,
                null,
                new int[Constants.MAX_ITEMS],
                new float[Constants.MAX_FACTIONS]);
    }

    private FleetTradeProfile fleetProfileWithFoodCargo(
            float x,
            float speed,
            double credits,
            int foodCargo) {
        int[] stock = new int[Constants.MAX_ITEMS];
        stock[Constants.ITEM_FOOD] = foodCargo;
        return new FleetTradeProfile(
                x,
                0f,
                speed,
                Money.fromCredits(credits),
                foodCargo,
                foodCargo,
                foodCargo,
                Constants.ITEM_FOOD,
                false,
                null,
                stock,
                new float[Constants.MAX_FACTIONS]);
    }

    private Entity station(
            float x,
            int foodStock,
            int targetStock,
            float buyPrice,
            float sellPrice,
            double credits) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        inventory.stock[Constants.ITEM_FOOD] = foodStock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, targetStock, 0f);
        market.buyPrices[Constants.ITEM_FOOD] = buyPrice;
        market.sellPrices[Constants.ITEM_FOOD] = sellPrice;
        market.isDirty = false;
        return new Entity()
                .add(new EntityIdComponent(new EntityId(nextId++)))
                .add(transform)
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(credits)));
    }

    private EntityId id(Entity entity) {
        return entity.getComponent(EntityIdComponent.class).id;
    }
}
