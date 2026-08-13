package com.spacesim.world;

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
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.GalacticTradeOpportunity;
import com.spacesim.trade.GalacticTradeRoute;
import com.spacesim.trade.MarketDirectory;
import com.spacesim.trade.SystemMarketRef;
import com.spacesim.trade.TradeRouteCostModel;
import com.spacesim.trade.TradeRoutePlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldGalacticRouteChoiceTest {
    private static final StarSystemId A = new StarSystemId(1L);
    private static final StarSystemId B = new StarSystemId(2L);
    private static final int TRADE_LEAGUE = 1;
    private static final int MINERS = 2;

    private final ContentCatalog catalog = ContentCatalogLoader.loadDefault();
    private long nextId = 30_000L;

    @Test
    void fiscalExposureCanFlipSupplierChoiceInsideSharedPlanner() {
        Entity foreignSupplier = station(100, 100, 0f, 8f, TRADE_LEAGUE);
        Entity ownSupplier = station(100, 100, 0f, 10f, MINERS);
        Entity consumer = station(0, 100, 20f, 22f, MINERS);
        MarketDirectory suppliers = directory(foreignSupplier, ownSupplier);
        MarketDirectory consumers = directory(consumer);
        GalacticPath path = new GalacticPath(List.of(A, B), 20L, 2d, 25d);
        List<GalacticTradeOpportunity> opportunities = List.of(
                opportunity(suppliers, foreignSupplier, consumers, consumer, path),
                opportunity(suppliers, ownSupplier, consumers, consumer, path));
        FleetTradeProfile fleet = fleet();

        TradeRoutePlanner noPolicy = new TradeRoutePlanner(
                catalog,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                TradeRouteCostModel.none());
        GalacticTradeRoute rawBest = noPolicy.findBestGalacticRoute(fleet, opportunities).orElseThrow();
        assertEquals(id(foreignSupplier), rawBest.buyStationId());

        FactionStrategicState miners = new FactionStrategicState(
                "faction.miners",
                -100,
                List.of(),
                List.of(A),
                0,
                3_000,
                List.of(),
                List.of(),
                List.of());
        TradeRoutePlanner withWorldPolicy = new TradeRoutePlanner(
                catalog,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                new WorldTradeRouteCostModel(catalog, List.of(miners)));
        GalacticTradeRoute policyBest = withWorldPolicy
                .findBestGalacticRoute(fleet, opportunities)
                .orElseThrow();

        assertEquals(id(ownSupplier), policyBest.buyStationId());
    }

    private GalacticTradeOpportunity opportunity(
            MarketDirectory suppliers,
            Entity supplier,
            MarketDirectory consumers,
            Entity consumer,
            GalacticPath path) {
        return new GalacticTradeOpportunity(
                new SystemMarketRef(A, suppliers.find(id(supplier))),
                new SystemMarketRef(B, consumers.find(id(consumer))),
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
                MINERS,
                new int[Constants.MAX_ITEMS],
                new float[Constants.MAX_FACTIONS]);
    }

    private MarketDirectory directory(Entity... stations) {
        MarketDirectory directory = new MarketDirectory(catalog);
        directory.rebuild(List.of(stations));
        return directory;
    }

    private Entity station(
            int stock,
            int targetStock,
            float buyPrice,
            float sellPrice,
            int factionId) {
        TransformComponent transform = new TransformComponent();
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
                .add(transform)
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(100_000d)))
                .add(new FactionComponent(factionId));
    }

    private static EntityId id(Entity entity) {
        return entity.getComponent(EntityIdComponent.class).id;
    }
}
