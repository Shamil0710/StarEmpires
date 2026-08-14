package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MiningCommandComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.model.ShipType;
import com.spacesim.persistence.EntityId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ManualMiningSystemTest {
    private long nextId = 1L;

    @Test
    void manualMiningUsesFiniteTransferWithoutOwningPlayerMovement() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = engine(ledger);
        Entity miner = miner(ShipType.MINING_SHIP, 0f, 10, 2f);
        Entity asteroid = asteroid(0f, 10L);
        MiningCommandComponent command = command(miner, asteroid, true);
        transform(miner).velocity.set(7f, -2f);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        long poolBefore = asteroid.getComponent(AsteroidComponent.class).remainingResource
                + inventory(miner).stock[Constants.ITEM_ORE];
        engine.update(1f);

        assertEquals(poolBefore,
                asteroid.getComponent(AsteroidComponent.class).remainingResource
                        + inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(2, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(8L, asteroid.getComponent(AsteroidComponent.class).remainingResource);
        assertEquals(2L, mining(miner).totalMined);
        assertEquals(MiningCommandComponent.Status.MINING, command.status);
        assertEquals(2, command.extractedLastTick);
        assertEquals(7f, transform(miner).velocity.x, 0f);
        assertEquals(-2f, transform(miner).velocity.y, 0f);
        assertEquals(0, ledger.size());
    }

    @Test
    void outOfRangeManualMiningDoesNotMoveShipOrCreateCargo() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner(ShipType.MINING_SHIP, 0f, 10, 2f);
        Entity asteroid = asteroid(100f, 10L);
        MiningCommandComponent command = command(miner, asteroid, true);
        transform(miner).velocity.set(3f, 0f);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        engine.update(1f);

        assertEquals(MiningCommandComponent.Status.OUT_OF_RANGE, command.status);
        assertEquals(0, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(10L, asteroid.getComponent(AsteroidComponent.class).remainingResource);
        assertEquals(0f, transform(miner).position.x, 0f);
        assertEquals(3f, transform(miner).velocity.x, 0f);
    }

    @Test
    void fullCargoIsAnAuthoritativeManualMiningRejection() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner(ShipType.MINING_SHIP, 0f, 1, 2f);
        inventory(miner).stock[Constants.ITEM_ORE] = 1;
        Entity asteroid = asteroid(0f, 10L);
        MiningCommandComponent command = command(miner, asteroid, true);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        engine.update(1f);

        assertEquals(MiningCommandComponent.Status.CARGO_FULL, command.status);
        assertEquals(1, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(10L, asteroid.getComponent(AsteroidComponent.class).remainingResource);
        assertEquals(0, command.extractedLastTick);
    }

    @Test
    void depletingManualShotLeavesCargoAboardAndStopsRequestInsteadOfAutoSelling() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner(ShipType.MINING_SHIP, 0f, 10, 2f);
        Entity asteroid = asteroid(0f, 1L);
        MiningCommandComponent command = command(miner, asteroid, true);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        engine.update(1f);

        assertEquals(1, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(1L, mining(miner).totalMined);
        assertEquals(MiningCommandComponent.Status.DEPLETED, command.status);
        assertEquals(1, command.extractedLastTick);
        assertFalse(command.miningRequested);
        assertFalse(engine.getEntitiesFor(Family.all(AsteroidComponent.class).get()).contains(asteroid, true));
    }

    @Test
    void incompatibleControlledHullGetsReadableStatus() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner(ShipType.COMBAT_SHIP, 0f, 10, 2f);
        Entity asteroid = asteroid(0f, 10L);
        MiningCommandComponent command = command(miner, asteroid, true);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        engine.update(1f);

        assertEquals(MiningCommandComponent.Status.INCOMPATIBLE_SHIP, command.status);
        assertEquals(0, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(10L, asteroid.getComponent(AsteroidComponent.class).remainingResource);
    }

    private Engine engine(EconomicLedger ledger) {
        Engine engine = new Engine();
        engine.addSystem(new MiningSystem(ledger));
        return engine;
    }

    private Entity miner(ShipType type, float x, int capacity, float rate) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        MiningComponent mining = new MiningComponent(Constants.ITEM_ORE, rate);
        mining.extractionRange = 2f;
        mining.movementSpeed = 100f;
        return identified(new Entity()
                .add(transform)
                .add(inventory)
                .add(new ShipComponent(type))
                .add(mining)
                .add(new PlayerControlledComponent()));
    }

    private Entity asteroid(float x, long amount) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        return identified(new Entity()
                .add(transform)
                .add(new AsteroidComponent("manual-" + nextId, Constants.ITEM_ORE, amount)));
    }

    private MiningCommandComponent command(Entity miner, Entity asteroid, boolean requested) {
        MiningCommandComponent command = new MiningCommandComponent();
        command.targetAsteroidId = asteroid.getComponent(EntityIdComponent.class).id;
        command.miningRequested = requested;
        miner.add(command);
        return command;
    }

    private Entity identified(Entity entity) {
        return entity.add(new EntityIdComponent(new EntityId(nextId++)));
    }

    private static InventoryComponent inventory(Entity entity) {
        return entity.getComponent(InventoryComponent.class);
    }

    private static MiningComponent mining(Entity entity) {
        return entity.getComponent(MiningComponent.class);
    }

    private static TransformComponent transform(Entity entity) {
        return entity.getComponent(TransformComponent.class);
    }
}
