package com.spacesim.trade;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.SectorId;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage10InterSystemLogisticsAcceptanceTest {
    private static final int ITEM = Constants.ITEM_FOOD;

    @Test
    void marketIndexUsesSectorIndexesAndInvalidatesStaleResults() {
        WorldSimulation world = DemoGalaxyFactory.create(0x10D0L);
        GalacticMarketIndex index = new GalacticMarketIndex(world);

        assertTrue(index.rebuild());
        long revision = index.revision();
        assertFalse(index.rebuild());
        assertEquals(
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.INNER_SYSTEM_ID),
                index.systemsInSector(new SectorId(1L)));

        GalacticMarketDiscovery.Result snapshot =
                new GalacticMarketDiscovery.Result(revision, List.of());
        Entity market = markets(world, DemoGalaxyFactory.INNER_SYSTEM_ID).get(0);
        InventoryComponent inventory = market.getComponent(InventoryComponent.class);
        inventory.stock[ITEM]++;

        assertFalse(snapshot.isCurrent(index));
        assertTrue(index.revision() > revision);
    }

    @Test
    void configurableHopHorizonBoundsDiscovery() {
        WorldSimulation world = DemoGalaxyFactory.create(0x10D1L);
        FleetId fleetId = prepareScenario(world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID);

        InterSystemTradeService oneHop = new InterSystemTradeService(
                world,
                new GalacticMarketDiscoveryPolicy(1, 1, 4, 32, 0),
                TradeRoutePlanner.ScoringMode.PROFIT_PER_SECOND);
        InterSystemTradeService twoHops = new InterSystemTradeService(
                world,
                new GalacticMarketDiscoveryPolicy(2, 2, 4, 32, 0),
                TradeRoutePlanner.ScoringMode.PROFIT_PER_SECOND);

        assertTrue(oneHop.plan(fleetId).isEmpty());
        InterSystemTradeJob job = twoHops.plan(fleetId).orElseThrow();
        assertEquals(DemoGalaxyFactory.FRONTIER_SYSTEM_ID, job.route().sellSystemId());
        assertEquals(2, job.route().jumpPath().jumpCount());
    }

    @Test
    void profitableRemoteShortageIsSuppliedByPhysicalFleetTransit() {
        WorldSimulation world = DemoGalaxyFactory.create(0x10E0L);
        FleetId fleetId = prepareScenario(world, DemoGalaxyFactory.INNER_SYSTEM_ID);
        SimulationSession origin = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        SimulationSession destination = world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow();
        Entity supplier = profitableSupplier(origin);
        Entity consumer = profitableConsumer(destination);
        int supplierBefore = supplier.getComponent(InventoryComponent.class).stock[ITEM];
        int consumerBefore = consumer.getComponent(InventoryComponent.class).stock[ITEM];

        InterSystemTradeService service = new InterSystemTradeService(world);
        InterSystemTradeJob job = service.plan(fleetId).orElseThrow();
        int amount = job.route().amount();
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, job.route().sellSystemId());
        assertTrue(amount > 0);

        assertEquals(InterSystemTradeJob.State.JUMPING, job.advance(world));
        assertEquals(supplierBefore - amount, supplier.getComponent(InventoryComponent.class).stock[ITEM]);
        assertTrue(world.findFleetJump(fleetId).isPresent());

        float fixedStep = origin.getClock().getFixedStepSeconds();
        int guard = 0;
        while (!job.isTerminal() && guard++ < 200) {
            world.advanceFrame(fixedStep);
            job.advance(world);
        }

        assertEquals(InterSystemTradeJob.State.COMPLETED, job.state(), job.failureReason());
        assertTrue(guard < 200);
        int delivered = job.soldAmount();
        assertTrue(delivered > 0);
        assertTrue(delivered <= amount);
        FleetPlacementState arrived = world.findFleet(fleetId).orElseThrow();
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, arrived.systemId());
        Entity arrivedFleet = destination.getEntityRegistry().find(arrived.localEntityId());
        assertNotNull(arrivedFleet);
        assertEquals(amount - delivered, arrivedFleet.getComponent(InventoryComponent.class).stock[ITEM]);
        assertEquals(consumerBefore + delivered, consumer.getComponent(InventoryComponent.class).stock[ITEM]);
        assertTrue(consumer.getComponent(InventoryComponent.class).stock[ITEM] > consumerBefore);
    }

    private static FleetId prepareScenario(WorldSimulation world, StarSystemId profitableDestination) {
        FleetPlacementState fleetPlacement = world.getFleetPlacements().stream()
                .filter(placement -> DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId()))
                .filter(placement -> {
                    Entity fleet = world.findSession(placement.systemId()).orElseThrow()
                            .getEntityRegistry().find(placement.localEntityId());
                    TradeAIComponent ai = fleet == null ? null : fleet.getComponent(TradeAIComponent.class);
                    ShipComponent ship = fleet == null ? null : fleet.getComponent(ShipComponent.class);
                    ContentCatalog.ItemDefinition item = world.findSession(placement.systemId()).orElseThrow()
                            .getContentCatalog().findItem(ITEM);
                    return ai != null
                            && (ship == null || (ship.type != null
                            && ship.type.canPurchase(item.category(), item.mineable())));
                })
                .findFirst()
                .orElseThrow();
        Entity fleet = world.findSession(fleetPlacement.systemId()).orElseThrow()
                .getEntityRegistry().find(fleetPlacement.localEntityId());
        InventoryComponent fleetInventory = fleet.getComponent(InventoryComponent.class);
        Arrays.fill(fleetInventory.stock, 0);
        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        ai.resetRoute();
        ai.state = TradeAIComponent.State.IDLE;
        ai.specializedItem = ITEM;
        ai.cargoSpace = Math.max(20, ai.cargoSpace);
        ai.movementSpeed = Math.max(100f, ai.movementSpeed);
        ensureFunds(fleet.getComponent(WalletComponent.class), Money.fromCredits(100_000d));

        for (StarSystemId systemId : List.of(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID)) {
            List<Entity> systemMarkets = markets(world, systemId);
            for (Entity marketEntity : systemMarkets) {
                InventoryComponent inventory = marketEntity.getComponent(InventoryComponent.class);
                MarketComponent market = marketEntity.getComponent(MarketComponent.class);
                market.configureTradableItem(ITEM, 100, 0f);
                inventory.stock[ITEM] = 0;
                market.buyPrices[ITEM] = 1f;
                market.sellPrices[ITEM] = 100f;
                market.isDirty = false;
                ensureFunds(marketEntity.getComponent(WalletComponent.class), Money.fromCredits(100_000d));
            }
        }

        Entity supplier = profitableSupplier(world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow());
        supplier.remove(FactionMarketAccessComponent.class);
        InventoryComponent supplierInventory = supplier.getComponent(InventoryComponent.class);
        MarketComponent supplierMarket = supplier.getComponent(MarketComponent.class);
        supplierInventory.stock[ITEM] = 100;
        supplierMarket.targetStock[ITEM] = 20;
        supplierMarket.buyPrices[ITEM] = 4f;
        supplierMarket.sellPrices[ITEM] = 5f;
        supplierMarket.isDirty = false;

        Entity consumer = profitableConsumer(world.findSession(profitableDestination).orElseThrow());
        consumer.remove(FactionMarketAccessComponent.class);
        InventoryComponent consumerInventory = consumer.getComponent(InventoryComponent.class);
        MarketComponent consumerMarket = consumer.getComponent(MarketComponent.class);
        consumerInventory.stock[ITEM] = 0;
        consumerMarket.targetStock[ITEM] = 100;
        consumerMarket.buyPrices[ITEM] = 30f;
        consumerMarket.sellPrices[ITEM] = 35f;
        consumerMarket.isDirty = false;
        return fleetPlacement.id();
    }

    private static Entity profitableSupplier(SimulationSession session) {
        return markets(session).get(0);
    }

    private static Entity profitableConsumer(SimulationSession session) {
        return markets(session).get(0);
    }

    private static List<Entity> markets(WorldSimulation world, StarSystemId systemId) {
        return markets(world.findSession(systemId).orElseThrow());
    }

    private static List<Entity> markets(SimulationSession session) {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(EntityIdComponent.class) != null
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(InventoryComponent.class) != null
                    && entity.getComponent(WalletComponent.class) != null) {
                result.add(entity);
            }
        }
        result.sort(Comparator.comparingLong(entity ->
                entity.getComponent(EntityIdComponent.class).id.value()));
        return result;
    }

    private static void ensureFunds(WalletComponent wallet, long targetBalance) {
        long missing = targetBalance - wallet.getBalanceMilliCredits();
        if (missing > 0L) {
            assertTrue(wallet.creditFromSource(missing));
        }
    }
}
