package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.events.GlobalEventManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumptionSystemTest {
    @Test
    void результатНеЗависитОтЧастотыОбновления() {
        assertEquals(50, simulateConsumption(30, 10));
        assertEquals(50, simulateConsumption(60, 10));
        assertEquals(50, simulateConsumption(144, 10));
    }

    @Test
    void большойШагЭквивалентенНаборуМалыхШагов() {
        TestWorld oneLargeStep = createWorld(100, 5f);
        oneLargeStep.engine.update(10f);

        TestWorld manySmallSteps = createWorld(100, 5f);
        for (int i = 0; i < 600; i++) {
            manySmallSteps.engine.update(1f / 60f);
        }

        assertEquals(manySmallSteps.inventory.stock[Constants.ITEM_FOOD],
                oneLargeStep.inventory.stock[Constants.ITEM_FOOD]);
        assertEquals(50, oneLargeStep.inventory.stock[Constants.ITEM_FOOD]);
    }

    @Test
    void дробныйРасходНакапливаетсяДоЦелойЕдиницы() {
        TestWorld world = createWorld(10, 0.5f);
        world.market.isDirty = false;

        world.engine.update(1f);

        assertEquals(10, world.inventory.stock[Constants.ITEM_FOOD]);
        assertEquals(0.5d, world.market.consumptionRemainder[Constants.ITEM_FOOD], 0.000001d);
        assertFalse(world.market.isDirty);

        world.engine.update(1f);

        assertEquals(9, world.inventory.stock[Constants.ITEM_FOOD]);
        assertEquals(0d, world.market.consumptionRemainder[Constants.ITEM_FOOD], 0.000001d);
        assertTrue(world.market.isDirty);
    }

    @Test
    void пустойСкладНеНакапливаетНеудовлетворенныйСпрос() {
        TestWorld world = createWorld(0, 5f);

        world.engine.update(10f);
        world.inventory.stock[Constants.ITEM_FOOD] = 10;
        world.market.isDirty = false;
        world.engine.update(0.1f);

        assertEquals(10, world.inventory.stock[Constants.ITEM_FOOD]);
        assertEquals(0.5d, world.market.consumptionRemainder[Constants.ITEM_FOOD], 0.000001d);
        assertFalse(world.market.isDirty);
    }

    @Test
    void некорректноеВремяНеМеняетСостояние() {
        TestWorld world = createWorld(10, 5f);
        world.market.isDirty = false;

        world.engine.update(-1f);
        world.engine.update(Float.NaN);

        assertEquals(10, world.inventory.stock[Constants.ITEM_FOOD]);
        assertEquals(0d, world.market.consumptionRemainder[Constants.ITEM_FOOD]);
        assertFalse(world.market.isDirty);
    }

    private int simulateConsumption(int framesPerSecond, int seconds) {
        TestWorld world = createWorld(100, 5f);
        float deltaTime = 1f / framesPerSecond;

        for (int i = 0; i < framesPerSecond * seconds; i++) {
            world.engine.update(deltaTime);
        }

        return world.inventory.stock[Constants.ITEM_FOOD];
    }

    private TestWorld createWorld(int initialStock, float consumptionPerSecond) {
        Engine engine = new Engine();
        engine.addSystem(new ConsumptionSystem(new GlobalEventManager()));

        Entity station = new Entity();
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = initialStock;
        MarketComponent market = new MarketComponent();
        market.baseConsumption[Constants.ITEM_FOOD] = consumptionPerSecond;

        station.add(inventory);
        station.add(market);
        station.add(new TransformComponent());
        engine.addEntity(station);

        return new TestWorld(engine, inventory, market);
    }

    private record TestWorld(Engine engine, InventoryComponent inventory, MarketComponent market) {
    }
}
