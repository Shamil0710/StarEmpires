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
import com.spacesim.world.GalacticPathPlanner;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.JumpTransitTiming;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F5RouteRedundancyAcceptanceTest {
    private static final StarSystemId A = new StarSystemId(1L);
    private static final StarSystemId B = new StarSystemId(2L);
    private static final StarSystemId C = new StarSystemId(3L);
    private static final StarSystemId D = new StarSystemId(4L);
    private static final StarSystemId E = new StarSystemId(5L);
    private static final int SUPPLIER_FACTION = 1;
    private static final int TRADER_FACTION = 2;

    private final ContentCatalog content = ContentCatalogLoader.loadDefault();

    @Test
    void boundedResilienceBudgetCanSelectLongerRealEdgeDisjointPath() {
        GalacticPathPlanner pathPlanner = pathPlanner(redundantTopology());
        GalacticPath primary = pathPlanner.findPath(A, B).orElseThrow();
        assertEquals(List.of(A, C, B), primary.systems());
        GalacticPath alternative = pathPlanner.findEdgeDisjointAlternative(primary).orElseThrow();
        assertEquals(List.of(A, D, E, B), alternative.systems());
        assertTrue(edgeSet(primary).stream().noneMatch(edgeSet(alternative)::contains));

        Fixture fixture = fixture(primary);
        TradeRoutePlanner economic = new TradeRoutePlanner(
                content,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                perJumpCost(10_000L));
        FactionResilientGalacticTradePlanner planner = new FactionResilientGalacticTradePlanner(
                economic,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                SupplierDiversificationPolicy.none(),
                pathPlanner,
                4,
                jumps -> jumps * 100,
                (fleet, itemId) -> new RouteRedundancyPolicy.Assessment(true, 15_000L));

        FactionResilientGalacticTradePlanner.Selection selection = planner
                .selectBestGalacticRoute(fixture.fleet(), List.of(fixture.opportunity()))
                .orElseThrow();

        assertEquals(primary.systems(), selection.economicBaseline().jumpPath().systems());
        assertEquals(alternative.systems(), selection.selectedRoute().jumpPath().systems());
        assertEquals(200, selection.economicBaseline().routeRiskBasisPoints());
        assertEquals(300, selection.selectedRoute().routeRiskBasisPoints());
        assertEquals(10_000L, selection.actualProfitSacrificeMilliCredits());
        assertEquals(15_000L, selection.acceptableProfitSacrificeMilliCredits());
        assertFalse(selection.diversificationApplied());
        assertTrue(selection.routeRedundancyApplied());
        assertEquals(fixture.supplierStockBefore(), fixture.supplier().getComponent(InventoryComponent.class)
                .stock[Constants.ITEM_FOOD]);
        assertEquals(fixture.consumerStockBefore(), fixture.consumer().getComponent(InventoryComponent.class)
                .stock[Constants.ITEM_FOOD]);
        assertEquals(fixture.supplierWalletBefore(), fixture.supplier().getComponent(WalletComponent.class)
                .getBalanceMilliCredits());
        assertEquals(fixture.consumerWalletBefore(), fixture.consumer().getComponent(WalletComponent.class)
                .getBalanceMilliCredits());
    }

    @Test
    void insufficientProfitBudgetKeepsOrdinaryShortestPath() {
        GalacticPathPlanner pathPlanner = pathPlanner(redundantTopology());
        GalacticPath primary = pathPlanner.findPath(A, B).orElseThrow();
        Fixture fixture = fixture(primary);
        TradeRoutePlanner economic = new TradeRoutePlanner(
                content,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                perJumpCost(10_000L));
        FactionResilientGalacticTradePlanner planner = new FactionResilientGalacticTradePlanner(
                economic,
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                SupplierDiversificationPolicy.none(),
                pathPlanner,
                4,
                jumps -> jumps * 100,
                (fleet, itemId) -> new RouteRedundancyPolicy.Assessment(true, 9_999L));

        FactionResilientGalacticTradePlanner.Selection selection = planner
                .selectBestGalacticRoute(fixture.fleet(), List.of(fixture.opportunity()))
                .orElseThrow();

        assertEquals(primary.systems(), selection.selectedRoute().jumpPath().systems());
        assertEquals(0L, selection.actualProfitSacrificeMilliCredits());
        assertFalse(selection.routeRedundancyApplied());
    }

    @Test
    void routePolicyCannotInventRedundancyWhenTopologyHasNoEdgeDisjointPath() {
        GalaxyTopology topology = topology(
                List.of(
                        new StarSystemNode(A, "A", 0d, 0d),
                        new StarSystemNode(B, "B", 100d, 0d),
                        new StarSystemNode(C, "C", 50d, 0d)),
                List.of(new JumpConnection(A, C), new JumpConnection(C, B)));
        GalacticPathPlanner pathPlanner = pathPlanner(topology);
        GalacticPath primary = pathPlanner.findPath(A, B).orElseThrow();
        assertTrue(pathPlanner.findEdgeDisjointAlternative(primary).isEmpty());
        Fixture fixture = fixture(primary);
        FactionResilientGalacticTradePlanner planner = new FactionResilientGalacticTradePlanner(
                new TradeRoutePlanner(
                        content,
                        TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                        perJumpCost(10_000L)),
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                SupplierDiversificationPolicy.none(),
                pathPlanner,
                4,
                jumps -> jumps * 100,
                (fleet, itemId) -> new RouteRedundancyPolicy.Assessment(true, 1_000_000L));

        FactionResilientGalacticTradePlanner.Selection selection = planner
                .selectBestGalacticRoute(fixture.fleet(), List.of(fixture.opportunity()))
                .orElseThrow();

        assertEquals(primary.systems(), selection.selectedRoute().jumpPath().systems());
        assertFalse(selection.routeRedundancyApplied());
    }

    @Test
    void routeResilienceDoesNotRewriteTradeToForeignConsumer() {
        GalacticPathPlanner pathPlanner = pathPlanner(redundantTopology());
        GalacticPath primary = pathPlanner.findPath(A, B).orElseThrow();
        Fixture fixture = fixture(primary, 9);
        FactionResilientGalacticTradePlanner planner = new FactionResilientGalacticTradePlanner(
                new TradeRoutePlanner(
                        content,
                        TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                        perJumpCost(10_000L)),
                TradeRoutePlanner.ScoringMode.GROSS_PROFIT,
                SupplierDiversificationPolicy.none(),
                pathPlanner,
                4,
                jumps -> jumps * 100,
                (fleet, itemId) -> new RouteRedundancyPolicy.Assessment(true, 1_000_000L));

        FactionResilientGalacticTradePlanner.Selection selection = planner
                .selectBestGalacticRoute(fixture.fleet(), List.of(fixture.opportunity()))
                .orElseThrow();

        assertEquals(primary.systems(), selection.selectedRoute().jumpPath().systems());
        assertFalse(selection.routeRedundancyApplied());
    }

    private TradeRouteCostModel perJumpCost(long milliCreditsPerJump) {
        return (fleet, context) -> context.jumpPath() == null
                ? 0L
                : Math.multiplyExact((long) context.jumpPath().jumpCount(), milliCreditsPerJump);
    }

    private Fixture fixture(GalacticPath primary) {
        return fixture(primary, TRADER_FACTION);
    }

    private Fixture fixture(GalacticPath primary, int consumerFaction) {
        Entity supplier = station(50_001L, 100, 50, 8f, 7f, SUPPLIER_FACTION);
        Entity consumer = station(50_002L, 0, 100, 22f, 23f, consumerFaction);
        MarketDirectory supplierDirectory = new MarketDirectory(content);
        supplierDirectory.rebuild(List.of(supplier));
        MarketDirectory consumerDirectory = new MarketDirectory(content);
        consumerDirectory.rebuild(List.of(consumer));
        GalacticTradeOpportunity opportunity = new GalacticTradeOpportunity(
                new SystemMarketRef(A, supplierDirectory.find(id(supplier))),
                new SystemMarketRef(B, consumerDirectory.find(id(consumer))),
                Constants.ITEM_FOOD,
                primary,
                0f,
                0d,
                primary.jumpCount() * 100);
        FleetTradeProfile fleet = new FleetTradeProfile(
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
        return new Fixture(
                supplier,
                consumer,
                opportunity,
                fleet,
                supplier.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD],
                consumer.getComponent(InventoryComponent.class).stock[Constants.ITEM_FOOD],
                supplier.getComponent(WalletComponent.class).getBalanceMilliCredits(),
                consumer.getComponent(WalletComponent.class).getBalanceMilliCredits());
    }

    private Entity station(
            long entityId,
            int stock,
            int target,
            float buyPrice,
            float sellPrice,
            int factionId) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        inventory.stock[Constants.ITEM_FOOD] = stock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, target, 0f);
        market.targetStock[Constants.ITEM_FOOD] = target;
        market.buyPrices[Constants.ITEM_FOOD] = buyPrice;
        market.sellPrices[Constants.ITEM_FOOD] = sellPrice;
        market.isDirty = false;
        return new Entity()
                .add(new EntityIdComponent(new EntityId(entityId)))
                .add(new TransformComponent())
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(100_000d)))
                .add(new FactionComponent(factionId));
    }

    private static EntityId id(Entity entity) {
        return entity.getComponent(EntityIdComponent.class).id;
    }

    private static GalacticPathPlanner pathPlanner(GalaxyTopology topology) {
        return new GalacticPathPlanner(topology, JumpTransitTiming.DEFAULT, 0.1f);
    }

    private static GalaxyTopology redundantTopology() {
        return topology(
                List.of(
                        new StarSystemNode(A, "A", 0d, 0d),
                        new StarSystemNode(B, "B", 100d, 0d),
                        new StarSystemNode(C, "C", 50d, 0d),
                        new StarSystemNode(D, "D", 0d, 100d),
                        new StarSystemNode(E, "E", 100d, 100d)),
                List.of(
                        new JumpConnection(A, C),
                        new JumpConnection(C, B),
                        new JumpConnection(A, D),
                        new JumpConnection(D, E),
                        new JumpConnection(E, B)));
    }

    private static GalaxyTopology topology(
            List<StarSystemNode> systems,
            List<JumpConnection> connections) {
        return new GalaxyTopology(
                new GalaxyId(17_500L),
                "Stage 17F.5 route redundancy",
                List.of(new SectorNode(new SectorId(1L), "Test", systems)),
                connections);
    }

    private static Set<JumpConnection> edgeSet(GalacticPath path) {
        Set<JumpConnection> edges = new HashSet<>();
        for (int index = 1; index < path.systems().size(); index++) {
            edges.add(new JumpConnection(path.systems().get(index - 1), path.systems().get(index)));
        }
        return edges;
    }

    private record Fixture(
            Entity supplier,
            Entity consumer,
            GalacticTradeOpportunity opportunity,
            FleetTradeProfile fleet,
            int supplierStockBefore,
            int consumerStockBefore,
            long supplierWalletBefore,
            long consumerWalletBefore) {
    }
}
