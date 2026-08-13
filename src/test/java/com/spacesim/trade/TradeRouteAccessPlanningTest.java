package com.spacesim.trade;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionMarketAccessComponent;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeRouteAccessPlanningTest {
    private final ContentCatalog catalog = ContentCatalogLoader.loadDefault();
    private long nextId = 10_000L;

    @Test
    void prefersAllowedConsumerOverMoreProfitableDeniedMarket() {
        Entity supplier = station(0f, 100, 100, 8f, 10f, 100_000d,
                accessForFaction(1, true));
        Entity deniedHighPrice = station(10f, 0, 100, 50f, 52f, 100_000d,
                accessForFaction(1, false));
        Entity allowedLowerPrice = station(20f, 0, 100, 30f, 32f, 100_000d,
                accessForFaction(1, true));
        MarketDirectory directory = directory(supplier, deniedHighPrice, allowedLowerPrice);
        TradeRoutePlanner planner = new TradeRoutePlanner(
                catalog, TradeRoutePlanner.ScoringMode.PROFIT_PER_SECOND);

        TradeRoute route = planner.findBestNewCargoRoute(fleet(1), directory).orElseThrow();

        assertEquals(id(supplier), route.buyStationId());
        assertEquals(id(allowedLowerPrice), route.sellStationId());
    }

    @Test
    void unfactionedAccessAndAccessMutationAffectPlanningAndRevision() {
        FactionMarketAccessComponent supplierAccess = new FactionMarketAccessComponent()
                .allowUnfactioned(false);
        FactionMarketAccessComponent consumerAccess = new FactionMarketAccessComponent()
                .allowUnfactioned(true);
        Entity supplier = station(0f, 100, 100, 8f, 10f, 100_000d, supplierAccess);
        Entity consumer = station(10f, 0, 100, 30f, 32f, 100_000d, consumerAccess);
        MarketDirectory directory = directory(supplier, consumer);
        TradeRoutePlanner planner = new TradeRoutePlanner(
                catalog, TradeRoutePlanner.ScoringMode.PROFIT_PER_SECOND);
        long deniedRevision = directory.revision();

        assertTrue(planner.findBestNewCargoRoute(fleet(-1), directory).isEmpty());

        supplierAccess.allowUnfactioned(true);
        directory.rebuild(List.of(supplier, consumer));

        assertEquals(deniedRevision + 1L, directory.revision());
        assertTrue(planner.findBestNewCargoRoute(fleet(-1), directory).isPresent());
    }

    @Test
    void namedFactionAccessMutationChangesDirectoryRevision() {
        FactionMarketAccessComponent access = accessForFaction(1, true);
        Entity station = station(0f, 100, 100, 8f, 10f, 100_000d, access);
        MarketDirectory directory = directory(station);
        long before = directory.revision();

        access.setFactionAllowed(2, true);
        directory.rebuild(List.of(station));

        assertEquals(before + 1L, directory.revision());
        assertTrue(directory.find(id(station)).canTrade(2));
    }

    private FleetTradeProfile fleet(int factionId) {
        return new FleetTradeProfile(
                -5f,
                0f,
                20f,
                Money.fromCredits(100_000d),
                100,
                0,
                100,
                Constants.ITEM_FOOD,
                false,
                null,
                factionId,
                new int[Constants.MAX_ITEMS],
                new float[Constants.MAX_FACTIONS]);
    }

    private MarketDirectory directory(Entity... stations) {
        MarketDirectory directory = new MarketDirectory(catalog);
        directory.rebuild(List.of(stations));
        return directory;
    }

    private Entity station(
            float x,
            int foodStock,
            int targetStock,
            float buyPrice,
            float sellPrice,
            double credits,
            FactionMarketAccessComponent access) {
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
                .add(new WalletComponent(Money.fromCredits(credits)))
                .add(access);
    }

    private static FactionMarketAccessComponent accessForFaction(int factionId, boolean allowed) {
        return new FactionMarketAccessComponent()
                .allowUnfactioned(false)
                .setFactionAllowed(factionId, allowed);
    }

    private static EntityId id(Entity entity) {
        return entity.getComponent(EntityIdComponent.class).id;
    }
}
