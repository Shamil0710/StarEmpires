package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;
import com.spacesim.model.ShipType;
import com.spacesim.persistence.EntityId;
import com.spacesim.util.SpatialHashGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericNpcInertiaAcceptanceTest {
    private long nextId = 1L;

    @Test
    void genericTradeAiAcceleratesInsteadOfSnappingAndRealCargoReducesAcceleration() {
        Engine engine = new Engine();
        engine.addSystem(new TradeAISystem(new SpatialHashGrid(Constants.CELL_SIZE), new EconomicLedger()));
        Entity source = market(100f, 100, 100, 9f, 10f);
        Entity destination = market(100f, 0, 100, 20f, 22f);
        Entity empty = trader(0f, 0, 10);
        Entity loaded = trader(0f, 10, 10);
        engine.addEntity(source);
        engine.addEntity(destination);
        engine.addEntity(empty);
        engine.addEntity(loaded);

        engine.update(0f);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY,
                empty.getComponent(TradeAIComponent.class).state);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_SELL,
                loaded.getComponent(TradeAIComponent.class).state);

        engine.update(0.1f);

        TransformComponent emptyTransform = empty.getComponent(TransformComponent.class);
        TransformComponent loadedTransform = loaded.getComponent(TransformComponent.class);
        assertTrue(emptyTransform.position.x > 0f && emptyTransform.position.x < 100f);
        assertTrue(loadedTransform.position.x > 0f && loadedTransform.position.x < 100f);
        assertTrue(emptyTransform.velocity.x > 0f && emptyTransform.velocity.x < 100f);
        assertTrue(loadedTransform.velocity.x > 0f && loadedTransform.velocity.x < 100f);
        assertTrue(loadedTransform.velocity.x < emptyTransform.velocity.x,
                "same hull/thrust with real cargo mass must accelerate less");
        assertNotNull(empty.getComponent(FlightCommandComponent.class));
        assertNotNull(loaded.getComponent(FlightCommandComponent.class));
    }

    @Test
    void genericMiningAiApproachesAsteroidThroughSameLateTickFlightIntegrator() {
        Engine engine = new Engine();
        engine.addSystem(new MiningSystem(new EconomicLedger()));
        Entity miner = miner(0f);
        Entity asteroid = asteroid(100f);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        engine.update(0f);
        MiningComponent mining = miner.getComponent(MiningComponent.class);
        assertEquals(MiningComponent.State.TRAVEL_TO_ASTEROID, mining.state);
        assertEquals(0f, miner.getComponent(TransformComponent.class).position.x, 0f);

        engine.update(0.1f);

        TransformComponent transform = miner.getComponent(TransformComponent.class);
        assertTrue(transform.position.x > 0f && transform.position.x < 100f);
        assertTrue(transform.velocity.x > 0f && transform.velocity.x < mining.movementSpeed);
        assertNotNull(miner.getComponent(FlightCommandComponent.class));
        assertEquals(MiningComponent.State.TRAVEL_TO_ASTEROID, mining.state);
    }

    private Entity trader(float x, int cargo, int capacity) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        inventory.stock[Constants.ITEM_FOOD] = cargo;
        TradeAIComponent ai = new TradeAIComponent();
        ai.cargoSpace = capacity;
        ai.movementSpeed = 100f;
        return identified(new Entity()
                .add(transform)
                .add(inventory)
                .add(new ShipComponent(ShipType.FINISHED_GOODS_CARRIER))
                .add(new WalletComponent(Money.fromCredits(10_000d)))
                .add(new ReputationComponent())
                .add(ai));
    }

    private Entity market(
            float x,
            int foodStock,
            int targetStock,
            float buyPrice,
            float sellPrice) {
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
        return identified(new Entity()
                .add(transform)
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(100_000d))));
    }

    private Entity miner(float x) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 10;
        MiningComponent mining = new MiningComponent(Constants.ITEM_ORE, 1f);
        mining.movementSpeed = 100f;
        mining.extractionRange = 1f;
        mining.dockingRange = 1f;
        return identified(new Entity()
                .add(transform)
                .add(inventory)
                .add(new ShipComponent(ShipType.MINING_SHIP))
                .add(new WalletComponent(Money.fromCredits(1_000d)))
                .add(mining));
    }

    private Entity asteroid(float x) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        return identified(new Entity()
                .add(transform)
                .add(new AsteroidComponent("generic-inertia-asteroid", Constants.ITEM_ORE, 100L)));
    }

    private Entity identified(Entity entity) {
        return entity.add(new EntityIdComponent(new EntityId(nextId++)));
    }
}
