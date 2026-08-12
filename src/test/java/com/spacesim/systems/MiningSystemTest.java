package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.ShipType;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningSystemTest {
    private static final double EPSILON = 0.000001d;

    @Test
    void выбираетБлижайшийАстероидИЛетитДоРадиусаДобычи() {
        MiningFixture fixture = fixture(20, 2f, 10f, 5f);
        Entity far = asteroid("FAR", 100f, 0f, 20L);
        Entity near = asteroid("NEAR", 30f, 0f, 20L);
        fixture.engine.addEntity(far);
        fixture.engine.addEntity(near);

        fixture.engine.update(0f);
        assertEquals(MiningComponent.State.TRAVEL_TO_ASTEROID, fixture.mining.state);
        assertSame(near, fixture.mining.targetAsteroid);

        fixture.engine.update(1f);
        assertEquals(10f, fixture.transform.position.x, 0.0001f);
        assertEquals(10f, fixture.transform.velocity.x, 0.0001f);
        assertEquals(MiningComponent.State.TRAVEL_TO_ASTEROID, fixture.mining.state);

        fixture.engine.update(2f);
        assertEquals(25f, fixture.transform.position.x, 0.0001f);
        assertEquals(0f, fixture.transform.velocity.len2(), 0f);
        assertEquals(MiningComponent.State.MINING, fixture.mining.state);
        assertEquals(0, fixture.inventory.getTotalStock());
    }

    @Test
    void конечныйЗапасИстощаетсяУдаляетсяИДоставляетсяНаРынок() {
        MiningFixture fixture = fixture(20, 2f, 50f, 0f);
        Entity source = asteroid("ONE", 0f, 0f, 3L);
        Entity base = market("Рудный терминал", 0f, 0f, 100, 5f);
        fixture.engine.addEntity(source);
        fixture.engine.addEntity(base);
        advanceToMining(fixture.engine);

        fixture.engine.update(0.25f);
        assertEquals(0, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0.5d, fixture.mining.extractionRemainder, EPSILON);

        fixture.engine.update(1.25f);
        assertEquals(3, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(3L, fixture.mining.totalMined);
        assertEquals(0d, fixture.mining.extractionRemainder, 0d);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, fixture.mining.state);
        assertNull(fixture.mining.targetAsteroid);
        assertEquals(0, fixture.engine.getEntitiesFor(
                Family.all(AsteroidComponent.class).get()).size());

        fixture.engine.update(0f);
        assertEquals(MiningComponent.State.UNLOADING, fixture.mining.state);
        MarketComponent market = base.getComponent(MarketComponent.class);
        market.isDirty = false;
        fixture.engine.update(0f);

        assertEquals(MiningComponent.State.SEARCHING, fixture.mining.state);
        assertEquals(0, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(3, base.getComponent(InventoryComponent.class).stock[Constants.ITEM_ORE]);
        assertEquals(15f, fixture.mining.credits, 0.0001f);
        assertEquals(3L, fixture.mining.totalDelivered);
        assertTrue(market.isDirty);
    }

    @Test
    void полныйТрюмПрерываетДобычуНоНеУдаляетБогатыйИсточник() {
        MiningFixture fixture = fixture(2, 10f, 10f, 0f);
        Entity source = asteroid("RICH", 0f, 0f, 100L);
        Entity base = market("База", 40f, 0f, 100, 2f);
        fixture.engine.addEntity(source);
        fixture.engine.addEntity(base);
        advanceToMining(fixture.engine);

        fixture.engine.update(1f);
        assertEquals(2, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(98L, source.getComponent(AsteroidComponent.class).remainingResource);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, fixture.mining.state);

        fixture.engine.update(2f);
        assertEquals(20f, fixture.transform.position.x, 0.0001f);
        fixture.engine.update(2f);
        assertEquals(40f, fixture.transform.position.x, 0.0001f);
        assertEquals(MiningComponent.State.UNLOADING, fixture.mining.state);
        fixture.engine.update(0f);

        assertEquals(0, fixture.inventory.getTotalStock());
        assertEquals(2, base.getComponent(InventoryComponent.class).stock[Constants.ITEM_ORE]);
        assertEquals(4f, fixture.mining.credits, 0.0001f);
        assertEquals(2L, fixture.mining.totalDelivered);
        assertEquals(MiningComponent.State.SEARCHING, fixture.mining.state);
    }

    @Test
    void безНазначеннойБазыВыбираетБлижайшийПригодныйРынок() {
        MiningFixture fixture = fixture(10, 1f, 100f, 0f);
        fixture.inventory.stock[Constants.ITEM_ORE] = 5;
        fixture.mining.state = MiningComponent.State.RETURNING_TO_BASE;
        Entity invalidNear = market("Нет цены", 2f, 0f, 100, 0f);
        Entity validFar = market("Покупатель", 8f, 0f, 100, 3f);
        fixture.engine.addEntity(invalidNear);
        fixture.engine.addEntity(validFar);

        fixture.engine.update(0.1f);
        assertSame(validFar, fixture.mining.homeBase);
        assertEquals(MiningComponent.State.UNLOADING, fixture.mining.state);
        fixture.engine.update(0f);

        assertEquals(0, fixture.inventory.getTotalStock());
        assertEquals(5, validFar.getComponent(InventoryComponent.class)
                .stock[Constants.ITEM_ORE]);
        assertEquals(0, invalidNear.getComponent(InventoryComponent.class)
                .stock[Constants.ITEM_ORE]);
        assertEquals(15f, fixture.mining.credits, 0.0001f);
    }

    @Test
    void частичнаяРазгрузкаПродолжаетсяНаДругомРынке() {
        MiningFixture fixture = fixture(10, 1f, 100f, 0f);
        fixture.inventory.stock[Constants.ITEM_ORE] = 5;
        fixture.mining.state = MiningComponent.State.RETURNING_TO_BASE;
        Entity smallBase = market("Малый склад", 0f, 0f, 2, 2f);
        Entity largeBase = market("Большой склад", 5f, 0f, 20, 4f);
        fixture.engine.addEntity(smallBase);
        fixture.engine.addEntity(largeBase);

        fixture.engine.update(0f);
        fixture.engine.update(0f);
        assertEquals(2, smallBase.getComponent(InventoryComponent.class)
                .stock[Constants.ITEM_ORE]);
        assertEquals(3, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, fixture.mining.state);

        fixture.engine.update(0.1f);
        assertSame(largeBase, fixture.mining.homeBase);
        assertEquals(MiningComponent.State.UNLOADING, fixture.mining.state);
        fixture.engine.update(0f);

        assertEquals(0, fixture.inventory.getTotalStock());
        assertEquals(3, largeBase.getComponent(InventoryComponent.class)
                .stock[Constants.ITEM_ORE]);
        assertEquals(16f, fixture.mining.credits, 0.0001f);
        assertEquals(5L, fixture.mining.totalDelivered);
    }

    @Test
    void исчезновениеЦелиВПолётеБезопасноПерезапускаетАвтомат() {
        MiningFixture fixture = fixture(10, 1f, 10f, 0f);
        Entity source = asteroid("TEMP", 100f, 0f, 10L);
        fixture.engine.addEntity(source);
        fixture.engine.update(0f);
        fixture.engine.removeEntity(source);

        fixture.engine.update(1f);
        assertEquals(MiningComponent.State.SEARCHING, fixture.mining.state);
        assertNull(fixture.mining.targetAsteroid);
        assertEquals(0f, fixture.transform.position.x, 0f);

        fixture.inventory.stock[Constants.ITEM_ORE] = 1;
        fixture.mining.targetAsteroid = source;
        fixture.mining.state = MiningComponent.State.TRAVEL_TO_ASTEROID;
        fixture.engine.update(0f);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, fixture.mining.state);
    }

    @Test
    void безАстероидаРесурсБольшеНеПоявляетсяИзПустоты() {
        MiningFixture fixture = fixture(100, 10f, 100f, 10f);

        fixture.engine.update(1000f);

        assertEquals(0, fixture.inventory.getTotalStock());
        assertEquals(0L, fixture.mining.totalMined);
        assertEquals(MiningComponent.State.SEARCHING, fixture.mining.state);
    }

    @Test
    void несколькоКораблейНеМогутИзвлечьБольшеОбщегоОстатка() {
        Engine engine = new Engine();
        engine.addSystem(new MiningSystem());
        MiningFixture first = addMiner(engine, 10, 10f, 10f, 0f);
        MiningFixture second = addMiner(engine, 10, 10f, 10f, 0f);
        Entity source = asteroid("SHARED", 0f, 0f, 3L);
        engine.addEntity(source);
        advanceToMining(engine);

        engine.update(1f);

        assertEquals(3,
                first.inventory.stock[Constants.ITEM_ORE]
                        + second.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(3L, first.mining.totalMined + second.mining.totalMined);
        assertEquals(0, engine.getEntitiesFor(Family.all(AsteroidComponent.class).get()).size());
    }

    @Test
    void огромныйШагОграниченТрюмомИНеПереполняетЧисла() {
        MiningFixture fixture = fixture(Integer.MAX_VALUE, Float.MAX_VALUE, 0f, 0f);
        fixture.inventory.stock[Constants.ITEM_ORE] = Integer.MAX_VALUE - 1;
        Entity source = asteroid("HUGE", 0f, 0f, Long.MAX_VALUE);
        fixture.engine.addEntity(source);
        advanceToMining(fixture.engine);

        assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> fixture.engine.update(Float.MAX_VALUE));

        assertEquals(Integer.MAX_VALUE, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(1L, fixture.mining.totalMined);
        assertEquals(Long.MAX_VALUE - 1L,
                source.getComponent(AsteroidComponent.class).remainingResource);
        assertEquals(0d, fixture.mining.extractionRemainder, 0d);
    }

    @Test
    void повреждённыйДробныйОстатокНормализуетсяАСтатистикаНасыщается() {
        double[] invalidRemainders = {
                -1d, 1d, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY
        };
        for (double invalidRemainder : invalidRemainders) {
            MiningFixture fixture = fixture(10, 1f, 0f, 0f);
            fixture.engine.addEntity(asteroid("R-" + invalidRemainder, 0f, 0f, 10L));
            advanceToMining(fixture.engine);
            fixture.mining.extractionRemainder = invalidRemainder;
            fixture.engine.update(0.5f);
            assertEquals(0.5d, fixture.mining.extractionRemainder, EPSILON);
            assertEquals(0, fixture.inventory.getTotalStock());
        }

        MiningFixture saturated = fixture(20, 10f, 0f, 0f);
        saturated.engine.addEntity(asteroid("SAT", 0f, 0f, 20L));
        advanceToMining(saturated.engine);
        saturated.mining.totalMined = Long.MAX_VALUE - 5L;
        saturated.engine.update(1f);
        assertEquals(Long.MAX_VALUE, saturated.mining.totalMined);
    }

    @Test
    void выключениеПовреждённаяКонфигурацияИТорговыйПолётБлокируютЦикл() {
        MiningFixture paused = fixture(10, 2f, 10f, 0f);
        paused.engine.addEntity(asteroid("P", 10f, 0f, 10L));
        paused.mining.active = false;
        paused.engine.update(1f);
        assertEquals(MiningComponent.State.PAUSED, paused.mining.state);
        assertEquals(0f, paused.transform.position.x, 0f);
        paused.mining.active = true;
        paused.engine.update(0f);
        assertEquals(MiningComponent.State.TRAVEL_TO_ASTEROID, paused.mining.state);

        MiningFixture wrongType = fixture(10, 2f, 10f, 0f);
        wrongType.ship.type = ShipType.COMBAT_SHIP;
        wrongType.engine.addEntity(asteroid("C", 10f, 0f, 10L));
        wrongType.engine.update(1f);
        assertEquals(0f, wrongType.transform.position.x, 0f);

        MiningFixture invalidRate = fixture(10, 2f, 10f, 0f);
        invalidRate.mining.extractionPerSecond = Float.NaN;
        invalidRate.engine.addEntity(asteroid("N", 10f, 0f, 10L));
        invalidRate.engine.update(1f);
        assertEquals(0f, invalidRate.transform.position.x, 0f);

        MiningFixture trading = fixture(10, 2f, 10f, 0f);
        TradeAIComponent tradeAI = new TradeAIComponent();
        tradeAI.state = TradeAIComponent.State.TRAVEL_TO_BUY;
        trading.entity.add(tradeAI);
        trading.engine.addEntity(asteroid("T", 10f, 0f, 10L));
        trading.engine.update(1f);
        assertEquals(MiningComponent.State.SEARCHING, trading.mining.state);
        assertEquals(0f, trading.transform.position.x, 0f);
    }

    @Test
    void некорректноеВремяПолностьюИгнорируется() {
        MiningFixture fixture = fixture(10, 2f, 10f, 0f);
        fixture.engine.addEntity(asteroid("D", 10f, 0f, 10L));

        fixture.engine.update(-1f);
        fixture.engine.update(Float.NaN);
        fixture.engine.update(Float.NEGATIVE_INFINITY);
        fixture.engine.update(Float.POSITIVE_INFINITY);

        assertEquals(MiningComponent.State.SEARCHING, fixture.mining.state);
        assertNull(fixture.mining.targetAsteroid);
        assertEquals(0f, fixture.transform.position.x, 0f);
    }

    @Test
    void рынокБезПоложительнойЦеныНеПолучаетБесплатныйРесурс() {
        MiningFixture fixture = fixture(10, 1f, 10f, 0f);
        fixture.inventory.stock[Constants.ITEM_ORE] = 4;
        fixture.mining.state = MiningComponent.State.RETURNING_TO_BASE;
        Entity base = market("Некорректный рынок", 0f, 0f, 100, Float.NaN);
        fixture.engine.addEntity(base);

        fixture.engine.update(0f);
        fixture.engine.update(0f);

        assertEquals(4, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0, base.getComponent(InventoryComponent.class)
                .stock[Constants.ITEM_ORE]);
        assertEquals(0f, fixture.mining.credits, 0f);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, fixture.mining.state);
        assertFalse(base.getComponent(MarketComponent.class).isDirty);
    }

    private MiningFixture fixture(
            int capacity,
            float extractionPerSecond,
            float movementSpeed,
            float extractionRange) {
        Engine engine = new Engine();
        engine.addSystem(new MiningSystem());
        return addMiner(engine, capacity, extractionPerSecond, movementSpeed, extractionRange);
    }

    private MiningFixture addMiner(
            Engine engine,
            int capacity,
            float extractionPerSecond,
            float movementSpeed,
            float extractionRange) {
        ShipComponent ship = new ShipComponent(ShipType.MINING_SHIP);
        MiningComponent mining = new MiningComponent(Constants.ITEM_ORE, extractionPerSecond);
        mining.movementSpeed = movementSpeed;
        mining.extractionRange = extractionRange;
        mining.dockingRange = 0f;
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        TransformComponent transform = new TransformComponent();

        Entity entity = new Entity()
                .add(new IdentityComponent("Добытчик", IdentityComponent.Kind.FLEET))
                .add(ship)
                .add(mining)
                .add(inventory)
                .add(transform);
        engine.addEntity(entity);
        return new MiningFixture(engine, entity, ship, mining, inventory, transform);
    }

    private Entity asteroid(String pointId, float x, float y, long resource) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        return new Entity()
                .add(new IdentityComponent("Астероид " + pointId, IdentityComponent.Kind.ASTEROID))
                .add(transform)
                .add(new AsteroidComponent(pointId, Constants.ITEM_ORE, resource));
    }

    private Entity market(String name, float x, float y, int capacity, float buyPrice) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_ORE, Math.max(1, capacity), 0f);
        market.buyPrices[Constants.ITEM_ORE] = buyPrice;
        market.sellPrices[Constants.ITEM_ORE] = Math.max(1f, buyPrice + 1f);
        market.isDirty = false;
        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(transform)
                .add(inventory)
                .add(market);
    }

    private void advanceToMining(Engine engine) {
        engine.update(0f);
        engine.update(0f);
    }

    private record MiningFixture(
            Engine engine,
            Entity entity,
            ShipComponent ship,
            MiningComponent mining,
            InventoryComponent inventory,
            TransformComponent transform) {
    }
}
