package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;
import com.spacesim.model.ShipType;
import com.spacesim.persistence.EntityId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningSystemTest {
    private long nextEntityId = 1L;

    @Test
    void извлечениеПереноситРесурсИзАстероидаВТрюмБезСозданияТовара() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = engine(ledger);
        Entity miner = miner("Miner", 0f, 10, 2f, 100d);
        Entity asteroid = asteroid("A-1", 0f, 10L);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        long resourceBefore = resourcePool(miner, asteroid);
        engine.update(0f);
        engine.update(0f);
        engine.update(1f);

        assertEquals(resourceBefore, resourcePool(miner, asteroid));
        assertEquals(8L, asteroid.getComponent(AsteroidComponent.class).remainingResource);
        assertEquals(2, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(2L, mining(miner).totalMined);
        assertEquals(0, ledger.size());
    }

    @Test
    void полныйЦиклДобычиИПродажиСохраняетДеньгиИЗаписываетTrade() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = engine(ledger);
        Entity base = base("Base", 0f, 10f, 1_000d, 0, 100);
        Entity miner = miner("Miner", 0f, 2, 2f, 100d);
        Entity asteroid = asteroid("A-1", 0f, 10L);
        mining(miner).homeBaseId = id(base);
        engine.addEntity(base);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        long moneyBefore = wallet(base).getBalanceMilliCredits() + wallet(miner).getBalanceMilliCredits();

        engine.update(0f);
        engine.update(0f);
        engine.update(1f);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, mining(miner).state);
        engine.update(0f);
        assertEquals(MiningComponent.State.UNLOADING, mining(miner).state);
        engine.update(0f);

        assertEquals(0, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(2, inventory(base).stock[Constants.ITEM_ORE]);
        assertEquals(Money.fromCredits(120d), wallet(miner).getBalanceMilliCredits());
        assertEquals(Money.fromCredits(980d), wallet(base).getBalanceMilliCredits());
        assertEquals(moneyBefore,
                wallet(base).getBalanceMilliCredits() + wallet(miner).getBalanceMilliCredits());
        assertEquals(2L, mining(miner).totalDelivered);
        assertEquals(MiningComponent.State.SEARCHING, mining(miner).state);
        assertEquals(1, ledger.size());
    }

    @Test
    void истощённыйАстероидУдаляетсяИзДвижкаПослеФизическогоПереноса() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner("Miner", 0f, 10, 5f, 100d);
        Entity asteroid = asteroid("A-1", 0f, 1L);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        engine.update(0f);
        engine.update(0f);
        engine.update(1f);

        assertEquals(1, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(1L, mining(miner).totalMined);
        assertFalse(engine.getEntitiesFor(Family.all(AsteroidComponent.class).get()).contains(asteroid, true));
        assertNull(mining(miner).targetAsteroidId);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, mining(miner).state);
    }

    @Test
    void базаБезДенегНеВыбираетсяДляВозврата() {
        Engine engine = engine(new EconomicLedger());
        Entity poorBase = base("Poor", 0f, 10f, 5d, 0, 100);
        Entity miner = miner("Miner", 0f, 10, 1f, 100d);
        inventory(miner).stock[Constants.ITEM_ORE] = 1;
        mining(miner).state = MiningComponent.State.RETURNING_TO_BASE;
        mining(miner).homeBaseId = id(poorBase);
        engine.addEntity(poorBase);
        engine.addEntity(miner);

        engine.update(0f);

        assertEquals(MiningComponent.State.RETURNING_TO_BASE, mining(miner).state);
        assertNull(mining(miner).homeBaseId);
        assertEquals(1, inventory(miner).stock[Constants.ITEM_ORE]);
    }

    @Test
    void приНеплатёжеспособнойПредпочтительнойБазеВыбираетДругую() {
        Engine engine = engine(new EconomicLedger());
        Entity poor = base("Poor", 1f, 10f, 1d, 0, 100);
        Entity good = base("Good", 5f, 10f, 100d, 0, 100);
        Entity miner = miner("Miner", 0f, 10, 1f, 0d);
        inventory(miner).stock[Constants.ITEM_ORE] = 1;
        mining(miner).state = MiningComponent.State.RETURNING_TO_BASE;
        mining(miner).homeBaseId = id(poor);
        engine.addEntity(poor);
        engine.addEntity(good);
        engine.addEntity(miner);

        engine.update(0f);

        assertEquals(id(good), mining(miner).homeBaseId);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, mining(miner).state);
    }

    @Test
    void базаСОграниченнойЛиквидностьюПокупаетТолькоОплачиваемуюЧасть() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = engine(ledger);
        Entity base = base("Base", 0f, 10f, 15d, 0, 100);
        Entity miner = miner("Miner", 0f, 10, 1f, 0d);
        inventory(miner).stock[Constants.ITEM_ORE] = 3;
        mining(miner).state = MiningComponent.State.UNLOADING;
        mining(miner).homeBaseId = id(base);
        engine.addEntity(base);
        engine.addEntity(miner);

        engine.update(0f);

        assertEquals(2, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(1, inventory(base).stock[Constants.ITEM_ORE]);
        assertEquals(Money.fromCredits(10d), wallet(miner).getBalanceMilliCredits());
        assertEquals(Money.fromCredits(5d), wallet(base).getBalanceMilliCredits());
        assertEquals(1L, mining(miner).totalDelivered);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, mining(miner).state);
        assertNull(mining(miner).homeBaseId);
        assertEquals(1, ledger.size());
    }

    @Test
    void заполненнаяБазаНеВыбирается() {
        Engine engine = engine(new EconomicLedger());
        Entity full = base("Full", 0f, 10f, 1_000d, 100, 100);
        Entity miner = miner("Miner", 0f, 10, 1f, 100d);
        inventory(miner).stock[Constants.ITEM_ORE] = 1;
        mining(miner).state = MiningComponent.State.RETURNING_TO_BASE;
        mining(miner).homeBaseId = id(full);
        engine.addEntity(full);
        engine.addEntity(miner);

        engine.update(0f);

        assertNull(mining(miner).homeBaseId);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, mining(miner).state);
    }

    @Test
    void поискВыбираетБлижайшийСовместимыйАстероид() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner("Miner", 0f, 10, 1f, 100d);
        Entity far = asteroid("Far", 100f, 10L);
        Entity near = asteroid("Near", 10f, 10L);
        engine.addEntity(miner);
        engine.addEntity(far);
        engine.addEntity(near);

        engine.update(0f);

        assertEquals(id(near), mining(miner).targetAsteroidId);
        assertEquals(MiningComponent.State.TRAVEL_TO_ASTEROID, mining(miner).state);
    }

    @Test
    void нулеваяСкоростьНеТелепортируетКорабль() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner("Miner", 0f, 10, 1f, 100d);
        Entity asteroid = asteroid("A", 100f, 10L);
        mining(miner).movementSpeed = 0f;
        engine.addEntity(miner);
        engine.addEntity(asteroid);
        engine.update(0f);
        engine.update(5f);

        assertEquals(0f, transform(miner).position.x, 0f);
        assertEquals(MiningComponent.State.SEARCHING, mining(miner).state,
                "zero propulsion is invalid navigation and must not fake a travel state or teleport");
        assertNull(mining(miner).targetAsteroidId);
    }

    @Test
    void выключенноеОборудованиеПереходитВPausedИНеМеняетРесурсы() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner("Miner", 0f, 10, 2f, 100d);
        Entity asteroid = asteroid("A", 0f, 10L);
        mining(miner).active = false;
        transform(miner).velocity.set(5f, 5f);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        engine.update(1f);

        assertEquals(MiningComponent.State.PAUSED, mining(miner).state);
        assertEquals(0, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(10L, asteroid.getComponent(AsteroidComponent.class).remainingResource);
        assertEquals(0f, transform(miner).velocity.len2(), 0f);
    }

    @Test
    void повреждённаяКонфигурацияБезДобываемогоРесурсаБезопасноОстанавливается() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner("Miner", 0f, 10, 1f, 100d);
        mining(miner).resourceItem = Constants.ITEM_FOOD;
        transform(miner).velocity.set(10f, 0f);
        engine.addEntity(miner);

        engine.update(1f);

        assertEquals(0f, transform(miner).velocity.len2(), 0f);
        assertEquals(0, inventory(miner).getTotalStock());
    }

    @Test
    void торговыйАвтоматСПолетомВременноБлокируетДобычу() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner("Hybrid", 0f, 10, 1f, 100d);
        TradeAIComponent trade = new TradeAIComponent();
        trade.state = TradeAIComponent.State.TRAVEL_TO_BUY;
        miner.add(trade);
        transform(miner).velocity.set(1f, 0f);
        Entity asteroid = asteroid("A", 0f, 10L);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        engine.update(1f);

        assertEquals(MiningComponent.State.SEARCHING, mining(miner).state);
        assertEquals(0, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(0f, transform(miner).velocity.len2(), 0f);
    }

    @Test
    void объектБезPersistentIdНеУчаствуетВДобывающейСимуляции() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner("Miner", 0f, 10, 1f, 100d);
        miner.remove(EntityIdComponent.class);
        Entity asteroid = asteroid("A", 0f, 10L);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        engine.update(1f);

        assertEquals(MiningComponent.State.SEARCHING, mining(miner).state);
        assertNull(mining(miner).targetAsteroidId);
    }

    @Test
    void отрицательныйИНеконечныйDeltaИгнорируются() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner("Miner", 0f, 10, 1f, 100d);
        Entity asteroid = asteroid("A", 0f, 10L);
        engine.addEntity(miner);
        engine.addEntity(asteroid);

        engine.update(-1f);
        engine.update(Float.NaN);

        assertEquals(MiningComponent.State.SEARCHING, mining(miner).state);
        assertNull(mining(miner).targetAsteroidId);
    }

    @Test
    void дробнаяПроизводительностьНакопляетОстатокДоЦелойЕдиницы() {
        Engine engine = engine(new EconomicLedger());
        Entity miner = miner("Miner", 0f, 10, 0.5f, 100d);
        Entity asteroid = asteroid("A", 0f, 10L);
        engine.addEntity(miner);
        engine.addEntity(asteroid);
        engine.update(0f);
        engine.update(0f);
        engine.update(1f);
        assertEquals(0, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(0.5d, mining(miner).extractionRemainder, 1e-12);
        engine.update(1f);
        assertEquals(1, inventory(miner).stock[Constants.ITEM_ORE]);
        assertEquals(0d, mining(miner).extractionRemainder, 1e-12);
    }

    private Engine engine(EconomicLedger ledger) {
        Engine engine = new Engine();
        engine.addSystem(new MiningSystem(ledger));
        return engine;
    }

    private Entity miner(String name, float x, int capacity, float extractionRate, double credits) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        MiningComponent mining = new MiningComponent(Constants.ITEM_ORE, extractionRate);
        mining.movementSpeed = 100f;
        mining.extractionRange = 1f;
        mining.dockingRange = 1f;
        return identified(new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
                .add(transform)
                .add(inventory)
                .add(new WalletComponent(Money.fromCredits(credits)))
                .add(new ShipComponent(ShipType.MINING_SHIP))
                .add(mining));
    }

    private Entity asteroid(String name, float x, long resource) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        return identified(new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.ASTEROID))
                .add(transform)
                .add(new AsteroidComponent(name, Constants.ITEM_ORE, resource)));
    }

    private Entity base(
            String name,
            float x,
            float buyPrice,
            double credits,
            int oreStock,
            int capacity) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        inventory.stock[Constants.ITEM_ORE] = oreStock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_ORE, Math.max(1, capacity), 0f);
        market.buyPrices[Constants.ITEM_ORE] = buyPrice;
        market.sellPrices[Constants.ITEM_ORE] = buyPrice * 1.1f;
        market.isDirty = false;
        return identified(new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(transform)
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(credits))));
    }

    private Entity identified(Entity entity) {
        return entity.add(new EntityIdComponent(new EntityId(nextEntityId++)));
    }

    private EntityId id(Entity entity) {
        return entity.getComponent(EntityIdComponent.class).id;
    }

    private MiningComponent mining(Entity entity) {
        return entity.getComponent(MiningComponent.class);
    }

    private InventoryComponent inventory(Entity entity) {
        return entity.getComponent(InventoryComponent.class);
    }

    private WalletComponent wallet(Entity entity) {
        return entity.getComponent(WalletComponent.class);
    }

    private TransformComponent transform(Entity entity) {
        return entity.getComponent(TransformComponent.class);
    }

    private long resourcePool(Entity miner, Entity asteroid) {
        return inventory(miner).stock[Constants.ITEM_ORE]
                + asteroid.getComponent(AsteroidComponent.class).remainingResource;
    }
}
