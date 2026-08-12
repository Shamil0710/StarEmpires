package com.spacesim.economy;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.model.Recipe;
import com.spacesim.systems.AsteroidSpawnSystem;
import com.spacesim.systems.ConsumptionSystem;
import com.spacesim.systems.ProductionSystem;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicInvariantsIntegrationTest {
    @Test
    void asteroidSpawnОбъявляетРовноСозданныйПриродныйРесурсКакSource() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = new Engine();
        AsteroidSpawnConfig config = AsteroidSpawnConfig.demoWorld();
        AsteroidSpawnSystem system = new AsteroidSpawnSystem(config, new Random(42L), ledger);
        engine.addSystem(system);

        engine.update(0f);

        long physicalResource = 0L;
        int asteroids = 0;
        for (Entity entity : engine.getEntities()) {
            AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
            if (asteroid != null) {
                physicalResource += asteroid.remainingResource;
                asteroids++;
            }
        }
        long declaredSource = ledger.getEntries().stream()
                .filter(entry -> entry.type() == EconomicTransaction.Type.RESOURCE_SOURCE)
                .mapToLong(EconomicTransaction::itemAmount)
                .sum();

        assertEquals(config.getInitialCount(), asteroids);
        assertEquals(asteroids, ledger.size());
        assertEquals(physicalResource, declaredSource);
        assertTrue(ledger.getEntries().stream().allMatch(entry ->
                entry.type() == EconomicTransaction.Type.RESOURCE_SOURCE
                        && entry.itemId() == Constants.ITEM_ORE
                        && entry.moneyMilliCredits() == 0L
                        && entry.reason().equals("asteroid-spawn")));
        assertSame(ledger, system.getLedger());
    }

    @Test
    void consumptionЗаписываетТолькоФактическиУничтоженныеЕдиницыКакSink() {
        EconomicLedger ledger = new EconomicLedger();
        GlobalEventManager events = new GlobalEventManager(0d);
        Engine engine = new Engine();
        ConsumptionSystem system = new ConsumptionSystem(events, ledger);
        engine.addSystem(system);

        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = 7;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, 10, 2f);
        Entity colony = new Entity()
                .add(new IdentityComponent("Colony", IdentityComponent.Kind.STATION))
                .add(new TransformComponent())
                .add(inventory)
                .add(market);
        engine.addEntity(colony);

        engine.update(2f);

        assertEquals(3, inventory.stock[Constants.ITEM_FOOD]);
        assertEquals(1, ledger.size());
        EconomicTransaction sink = ledger.getEntries().get(0);
        assertEquals(EconomicTransaction.Type.RESOURCE_SINK, sink.type());
        assertEquals("Colony", sink.source());
        assertEquals(Constants.ITEM_FOOD, sink.itemId());
        assertEquals(4L, sink.itemAmount());
        assertEquals(0L, sink.moneyMilliCredits());
        assertSame(ledger, system.getLedger());
    }

    @Test
    void productionЗаписываетTransformТолькоПослеФактическогоЦикла() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = new Engine();
        ProductionSystem system = new ProductionSystem(ledger);
        engine.addSystem(system);

        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_ORE] = 4;
        inventory.stock[Constants.ITEM_ENERGY] = 2;
        ProductionComponent production = new ProductionComponent();
        production.recipes.add(new Recipe("Steel", 1f)
                .input(Constants.ITEM_ORE, 2)
                .input(Constants.ITEM_ENERGY, 1)
                .output(Constants.ITEM_STEEL, 1));
        Entity foundry = new Entity()
                .add(new IdentityComponent("Foundry", IdentityComponent.Kind.STATION))
                .add(inventory)
                .add(production);
        engine.addEntity(foundry);

        engine.update(0.5f);
        assertEquals(0, ledger.size());
        engine.update(1.5f);

        assertEquals(0, inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0, inventory.stock[Constants.ITEM_ENERGY]);
        assertEquals(2, inventory.stock[Constants.ITEM_STEEL]);
        assertEquals(1, ledger.size());
        EconomicTransaction transform = ledger.getEntries().get(0);
        assertEquals(EconomicTransaction.Type.RESOURCE_TRANSFORM, transform.type());
        assertEquals("Foundry", transform.source());
        assertEquals("Foundry", transform.destination());
        assertEquals("Steel x2", transform.reason());
        assertEquals(0L, transform.moneyMilliCredits());
        assertSame(ledger, system.getLedger());
    }
}
