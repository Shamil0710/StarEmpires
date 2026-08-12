package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.PriceHistoryComponent;
import com.spacesim.constants.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceRecorderSystemTest {
    @Test
    void большойШагСоздаётОднуАктуальнуюТочкуБезФиктивногоДогона() {
        TestWorld world = createWorld(42f);

        world.engine.update(3f);
        world.engine.update(0f);
        world.engine.update(0f);

        assertEquals(1, world.history.history[Constants.ITEM_FOOD].size);
        assertEquals(42f, world.history.history[Constants.ITEM_FOOD].first());
    }

    @Test
    void малыеШагиНакапливаютсяДоИнтервала() {
        TestWorld world = createWorld(10f);

        world.engine.update(0.4f);
        assertEquals(0, world.history.history[Constants.ITEM_FOOD].size);

        world.engine.update(0.6f);
        assertEquals(1, world.history.history[Constants.ITEM_FOOD].size);
    }

    @Test
    void остатокБольшогоШагаСохраняетсяДоСледующегоИнтервала() {
        TestWorld world = createWorld(10f);

        world.engine.update(2.5f);
        assertEquals(1, world.history.history[Constants.ITEM_FOOD].size);

        world.engine.update(0.25f);
        assertEquals(1, world.history.history[Constants.ITEM_FOOD].size);

        world.engine.update(0.25f);
        assertEquals(2, world.history.history[Constants.ITEM_FOOD].size);
    }

    @Test
    void некорректноеВремяНеСоздаётЗаписи() {
        TestWorld world = createWorld(10f);

        world.engine.update(-1f);
        world.engine.update(Float.NaN);
        world.engine.update(Float.POSITIVE_INFINITY);

        assertEquals(0, world.history.history[Constants.ITEM_FOOD].size);
    }

    private TestWorld createWorld(float foodPrice) {
        Engine engine = new Engine();
        engine.addSystem(new PriceRecorderSystem());

        Entity station = new Entity();
        MarketComponent market = new MarketComponent();
        market.sellPrices[Constants.ITEM_FOOD] = foodPrice;
        PriceHistoryComponent history = new PriceHistoryComponent();
        station.add(market);
        station.add(history);
        engine.addEntity(station);

        return new TestWorld(engine, history);
    }

    private record TestWorld(Engine engine, PriceHistoryComponent history) {
    }
}
