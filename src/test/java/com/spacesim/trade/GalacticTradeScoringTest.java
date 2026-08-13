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
import com.spacesim.world.GalacticPath;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalacticTradeScoringTest {
    private static final StarSystemId A = new StarSystemId(1L);
    private static final StarSystemId B = new StarSystemId(42L);
    private final ContentCatalog catalog = ContentCatalogLoader.loadDefault();
    private long nextId = 20_000L;

    @Test
    void sharedCostModelReceivesGalacticContext() {
        Entity supplierEntity = station(100, 100, 10f, 8f);
        Entity consumerEntity = station(0, 100, 32f, 35f);
        MarketDirectory supplierDirectory = directory(supplierEntity);
        MarketDirectory consumerDirectory = directory(consumerEntity);
        GalacticPath path = new GalacticPath(List.of(A, B), 50L, 5d, 120d);
        GalacticTradeOpportunity opportunity = new GalacticTradeOpportunity(
                new SystemMarketRef(A, supplierDirectory.find(id(supplierEntity))),
                new SystemMarketRef(B, consumerDirectory.find(id(consumerEntity))),
                Constants.ITEM_FOOD,
                path,
                40f,
                2d,
                250);
        AtomicReference<TradeRouteCostModel.Context> captured = new AtomicReference<>();
        TradeRoutePlanner planner = new TradeRoutePlanner(
                catalog,
                TradeRoutePlanner.ScoringMode.PROFIT_PER_SECOND,
                (fleet, context) -> {
                    captured.set(context);
                    return 1_000L;
                });

        GalacticTradeRoute route = planner.findBestGalacticRoute(fleet(), List.of(opportunity)).orElseThrow();

        assertEquals(A, route.buySystemId());
        assertEquals(B, route.sellSystemId());
        assertEquals(7d, route.expectedDurationSeconds(), 0d);
        assertEquals(120d, route.strategicJumpDistance(), 0d);
        assertEquals(250, route.routeRiskBasisPoints());
        assertEquals(route.grossProfitMilliCredits() - 1_000L, route.netProfitMilliCredits());
        TradeRouteCostModel.Context context = captured.get();
        assertTrue(context.isGalactic());
        assertEquals(A, context.buySystemId());
        assertEquals(B, context.sellSystemId());
        assertEquals(path, context.jumpPath());
        assertEquals(250, context.routeRiskBasisPoints());
        assertEquals(7d, context.travelSeconds(), 0d);
    }

    @Test
    void externalCostCanRejectGalacticRoute() {
        Entity supplierEntity = station(100, 100, 10f, 8f);
        Entity consumerEntity = station(0, 100, 20f, 22f);
        MarketDirectory supplierDirectory = directory(supplierEntity);
        MarketDirectory consumerDirectory = directory(consumerEntity);
        GalacticTradeOpportunity opportunity = new GalacticTradeOpportunity(
                new SystemMarketRef(A, supplierDirectory.find(id(supplierEntity))),
                new SystemMarketRef(B, consumerDirectory.find(id(consumerEntity))),
                Constants.ITEM_FOOD,
                new GalacticPath(List.of(A, B), 10L, 1d, 10d),
                0f,
                0d,
                0);
        TradeRoutePlanner planner = new TradeRoutePlanner(
                catalog,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                (fleet, context) -> context.saleRevenueMilliCredits());

        assertTrue(planner.findBestGalacticRoute(fleet(), List.of(opportunity)).isEmpty());
    }

    private FleetTradeProfile fleet() {
        return new FleetTradeProfile(
                0f, 0f, 20f, Money.fromCredits(100_000d), 50, 0, 50,
                Constants.ITEM_FOOD, false, null, -1,
                new int[Constants.MAX_ITEMS], new float[Constants.MAX_FACTIONS]);
    }

    private MarketDirectory directory(Entity station) {
        MarketDirectory directory = new MarketDirectory(catalog);
        directory.rebuild(List.of(station));
        return directory;
    }

    private Entity station(int stock, int target, float buyPrice, float sellPrice) {
        TransformComponent transform = new TransformComponent();
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        inventory.stock[Constants.ITEM_FOOD] = stock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, target, 0f);
        market.buyPrices[Constants.ITEM_FOOD] = buyPrice;
        market.sellPrices[Constants.ITEM_FOOD] = sellPrice;
        market.isDirty = false;
        return new Entity()
                .add(new EntityIdComponent(new EntityId(nextId++)))
                .add(transform)
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(100_000d)));
    }

    private static EntityId id(Entity entity) {
        return entity.getComponent(EntityIdComponent.class).id;
    }
}
