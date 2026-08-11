package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.ShipType;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningSystemTest {
    private static final double DOUBLE_EPSILON = 0.000001d;

    @Test
    void дробнаяПроизводительностьНакапливаетсяДоЦелойЕдиницы() {
        MiningFixture fixture = createFixture(ShipType.MINING_SHIP, 2.5f, 100, null);

        fixture.engine.update(0.2f);
        assertEquals(0, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0.5d, fixture.mining.extractionRemainder, DOUBLE_EPSILON);
        assertEquals(0L, fixture.mining.totalMined);

        fixture.engine.update(0.2f);
        assertEquals(1, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0d, fixture.mining.extractionRemainder, DOUBLE_EPSILON);
        assertEquals(1L, fixture.mining.totalMined);
    }

    @Test
    void одинБольшойШагЭквивалентенНесколькимМалым() {
        MiningFixture largeStep = createFixture(ShipType.MINING_SHIP, 3.25f, 100, null);
        MiningFixture smallSteps = createFixture(ShipType.MINING_SHIP, 3.25f, 100, null);

        largeStep.engine.update(5.5f);
        for (int update = 0; update < 22; update++) {
            smallSteps.engine.update(0.25f);
        }

        assertEquals(largeStep.inventory.stock[Constants.ITEM_ORE],
                smallSteps.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(largeStep.mining.extractionRemainder,
                smallSteps.mining.extractionRemainder, DOUBLE_EPSILON);
        assertEquals(largeStep.mining.totalMined, smallSteps.mining.totalMined);
        assertEquals(17, largeStep.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0.875d, largeStep.mining.extractionRemainder, DOUBLE_EPSILON);
    }

    @Test
    void вместимостьОграничиваетПакетИСбрасываетСкрытыйОстаток() {
        MiningFixture fixture = createFixture(ShipType.MINING_SHIP, 10f, 3, null);
        fixture.inventory.stock[Constants.ITEM_ORE] = 1;
        fixture.mining.extractionRemainder = 0.75d;
        MarketComponent market = new MarketComponent();
        market.isDirty = false;
        fixture.entity.add(market);

        fixture.engine.update(1f);

        assertEquals(3, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(2L, fixture.mining.totalMined);
        assertEquals(0d, fixture.mining.extractionRemainder, 0d);
        assertTrue(market.isDirty);

        fixture.mining.extractionRemainder = 0.75d;
        fixture.engine.update(1f);
        assertEquals(3, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(2L, fixture.mining.totalMined);
        assertEquals(0d, fixture.mining.extractionRemainder, 0d);
    }

    @Test
    void огромныйШагНеСоздаётЦиклИНеПереполняетЦелыйЗапас() {
        MiningFixture fixture = createFixture(
                ShipType.MINING_SHIP,
                Float.MAX_VALUE,
                Integer.MAX_VALUE,
                null
        );
        fixture.inventory.stock[Constants.ITEM_ORE] = Integer.MAX_VALUE - 1;

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> fixture.engine.update(Float.MAX_VALUE));

        assertEquals(Integer.MAX_VALUE, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(1L, fixture.mining.totalMined);
        assertEquals(0d, fixture.mining.extractionRemainder, 0d);
    }

    @Test
    void счётчикДобычиНормализуетсяИНасыщаетсяБезПереполнения() {
        MiningFixture saturated = createFixture(ShipType.MINING_SHIP, 10f, 100, null);
        saturated.mining.totalMined = Long.MAX_VALUE - 5L;
        saturated.engine.update(1f);
        assertEquals(Long.MAX_VALUE, saturated.mining.totalMined);
        assertEquals(10, saturated.inventory.stock[Constants.ITEM_ORE]);

        MiningFixture corrupted = createFixture(ShipType.MINING_SHIP, 2f, 100, null);
        corrupted.mining.totalMined = -100L;
        corrupted.engine.update(1f);
        assertEquals(2L, corrupted.mining.totalMined);
    }

    @Test
    void добычаТребуетИменноДобывающийТипИРазрешённыйРесурс() {
        for (ShipType type : ShipType.values()) {
            MiningFixture fixture = createFixture(type, 2f, 100, null);
            fixture.engine.update(1f);
            assertEquals(type == ShipType.MINING_SHIP ? 2 : 0,
                    fixture.inventory.stock[Constants.ITEM_ORE], type.name());
        }

        MiningFixture wrongResource = createFixture(ShipType.MINING_SHIP, 2f, 100, null);
        wrongResource.mining.resourceItem = Constants.ITEM_FOOD;
        wrongResource.engine.update(1f);
        assertEquals(0, wrongResource.inventory.getTotalStock());

        wrongResource.mining.resourceItem = -1;
        wrongResource.engine.update(1f);
        assertEquals(0, wrongResource.inventory.getTotalStock());
    }

    @Test
    void выключенноеИПовреждённоеОборудованиеНичегоНеМеняет() {
        float[] invalidRates = {0f, -1f, Float.NaN, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY};
        for (float invalidRate : invalidRates) {
            MiningFixture fixture = createFixture(ShipType.MINING_SHIP, 1f, 100, null);
            fixture.mining.extractionPerSecond = invalidRate;
            fixture.mining.extractionRemainder = 0.25d;
            fixture.engine.update(1f);
            assertEquals(0, fixture.inventory.getTotalStock());
            assertEquals(0.25d, fixture.mining.extractionRemainder, 0d);
        }

        MiningFixture inactive = createFixture(ShipType.MINING_SHIP, 2f, 100, null);
        inactive.mining.active = false;
        inactive.engine.update(1f);
        assertEquals(0, inactive.inventory.getTotalStock());

        MiningFixture missingType = createFixture(ShipType.MINING_SHIP, 2f, 100, null);
        missingType.ship.type = null;
        missingType.engine.update(1f);
        assertEquals(0, missingType.inventory.getTotalStock());
    }

    @Test
    void торговыйКорабльДобываетТолькоВОжиданииАКомпонентAIНеОбязателен() {
        TradeAIComponent tradeAI = new TradeAIComponent();
        tradeAI.state = TradeAIComponent.State.TRAVEL_TO_SELL;
        MiningFixture controlled = createFixture(ShipType.MINING_SHIP, 2f, 100, tradeAI);

        controlled.engine.update(1f);
        assertEquals(0, controlled.inventory.getTotalStock());

        tradeAI.state = null;
        controlled.engine.update(1f);
        assertEquals(0, controlled.inventory.getTotalStock());

        tradeAI.state = TradeAIComponent.State.IDLE;
        controlled.engine.update(1f);
        assertEquals(2, controlled.inventory.stock[Constants.ITEM_ORE]);

        MiningFixture autonomous = createFixture(ShipType.MINING_SHIP, 2f, 100, null);
        autonomous.engine.update(1f);
        assertEquals(2, autonomous.inventory.stock[Constants.ITEM_ORE]);
    }

    @Test
    void некорректноеВремяПолностьюИгнорируется() {
        MiningFixture fixture = createFixture(ShipType.MINING_SHIP, 2f, 100, null);
        fixture.mining.extractionRemainder = 0.25d;
        float[] invalidDeltas = {0f, -1f, Float.NaN, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY};

        for (float invalidDelta : invalidDeltas) {
            fixture.engine.update(invalidDelta);
            assertEquals(0, fixture.inventory.getTotalStock());
            assertEquals(0.25d, fixture.mining.extractionRemainder, 0d);
            assertEquals(0L, fixture.mining.totalMined);
        }
    }

    @Test
    void повреждённыйДробныйОстатокНормализуетсяПередДобычей() {
        double[] invalidRemainders = {
                -1d, 1d, 10d, Double.NaN, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY
        };

        for (double invalidRemainder : invalidRemainders) {
            MiningFixture fixture = createFixture(ShipType.MINING_SHIP, 1f, 100, null);
            fixture.mining.extractionRemainder = invalidRemainder;
            fixture.engine.update(0.5f);
            assertEquals(0, fixture.inventory.getTotalStock());
            assertEquals(0.5d, fixture.mining.extractionRemainder, DOUBLE_EPSILON);
        }
    }

    @Test
    void повреждённыйИнвентарьНеИзменяется() {
        MiningFixture negativeStock = createFixture(ShipType.MINING_SHIP, 2f, 100, null);
        negativeStock.inventory.stock[Constants.ITEM_ORE] = -1;
        negativeStock.engine.update(1f);
        assertEquals(-1, negativeStock.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0L, negativeStock.mining.totalMined);

        MiningFixture invalidCapacity = createFixture(ShipType.MINING_SHIP, 2f, -1, null);
        invalidCapacity.mining.extractionRemainder = 0.5d;
        invalidCapacity.engine.update(1f);
        assertEquals(0, invalidCapacity.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0d, invalidCapacity.mining.extractionRemainder, 0d);
    }

    @Test
    void рынокПомечаетсяТолькоПослеФактическогоИзмененияЗапаса() {
        MiningFixture fixture = createFixture(ShipType.MINING_SHIP, 0.5f, 100, null);
        MarketComponent market = new MarketComponent();
        market.isDirty = false;
        fixture.entity.add(market);

        fixture.engine.update(1f);
        assertEquals(0, fixture.inventory.getTotalStock());
        assertFalse(market.isDirty);

        fixture.engine.update(1f);
        assertEquals(1, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertTrue(market.isDirty);
    }

    private MiningFixture createFixture(
            ShipType shipType,
            float extractionPerSecond,
            int capacity,
            TradeAIComponent tradeAI) {
        Engine engine = new Engine();
        engine.addSystem(new MiningSystem());

        ShipComponent ship = new ShipComponent(shipType);
        MiningComponent mining = new MiningComponent();
        mining.extractionPerSecond = extractionPerSecond;
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;

        Entity entity = new Entity()
                .add(ship)
                .add(mining)
                .add(inventory);
        if (tradeAI != null) {
            entity.add(tradeAI);
        }
        engine.addEntity(entity);
        return new MiningFixture(engine, entity, ship, mining, inventory);
    }

    private record MiningFixture(
            Engine engine,
            Entity entity,
            ShipComponent ship,
            MiningComponent mining,
            InventoryComponent inventory) {
    }
}
