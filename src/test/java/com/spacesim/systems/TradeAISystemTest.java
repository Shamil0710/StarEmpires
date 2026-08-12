package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeAISystemTest {
    private long nextEntityId = 1L;

    @Test
    void выбираетПрибыльныйМаршрутИХранитОжидаемуюПрибыльВMilliCredits() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = engine(ledger);
        Entity source = station("Source", 0f, 100, 100, 9f, 10f, 100_000d);
        Entity destination = station("Destination", 100f, 0, 100, 20f, 22f, 100_000d);
        Entity fleet = fleet("Trader", 0f, 1_000d, 10);
        engine.addEntity(source);
        engine.addEntity(destination);
        engine.addEntity(fleet);

        engine.update(0f);

        TradeAIComponent ai = ai(fleet);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, ai.state);
        assertEquals(id(source), ai.buyStationId);
        assertEquals(id(destination), ai.sellStationId);
        assertEquals(id(source), ai.targetStationId);
        assertEquals(Constants.ITEM_FOOD, ai.targetItem);
        assertEquals(10, ai.targetAmount);
        assertEquals(Money.fromCredits(100d), ai.expectedProfitMilliCredits);
        assertEquals(0, ledger.size());
    }

    @Test
    void полныйЦиклBuySellСохраняетОбщуюДенежнуюМассуИТовар() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = engine(ledger);
        Entity source = station("Source", 0f, 100, 100, 9f, 10f, 100_000d);
        Entity destination = station("Destination", 100f, 0, 100, 20f, 22f, 100_000d);
        Entity fleet = fleet("Trader", 0f, 1_000d, 10);
        engine.addEntity(source);
        engine.addEntity(destination);
        engine.addEntity(fleet);

        long moneyBefore = totalMoney(source, destination, fleet);
        int goodsBefore = totalFood(source, destination, fleet);

        engine.update(0f);
        engine.update(0f);
        engine.update(0f);
        engine.update(1f);
        engine.update(0f);

        assertEquals(goodsBefore, totalFood(source, destination, fleet));
        assertEquals(moneyBefore, totalMoney(source, destination, fleet));
        assertEquals(90, inventory(source).stock[Constants.ITEM_FOOD]);
        assertEquals(10, inventory(destination).stock[Constants.ITEM_FOOD]);
        assertEquals(0, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(Money.fromCredits(100_100d), wallet(source).getBalanceMilliCredits());
        assertEquals(Money.fromCredits(99_800d), wallet(destination).getBalanceMilliCredits());
        assertEquals(Money.fromCredits(1_100d), wallet(fleet).getBalanceMilliCredits());
        assertEquals(TradeAIComponent.State.IDLE, ai(fleet).state);
        assertEquals(0L, ai(fleet).expectedProfitMilliCredits);
        assertNull(ai(fleet).targetStationId);
        assertEquals(2, ledger.size());
    }

    @Test
    void неСтроитМаршрутЕслиСтанцияНазначенияНеМожетОплатитьГруз() {
        Engine engine = engine(new EconomicLedger());
        Entity source = station("Source", 0f, 100, 100, 9f, 10f, 100_000d);
        Entity poorDestination = station("Poor", 100f, 0, 100, 20f, 22f, 5d);
        Entity fleet = fleet("Trader", 0f, 1_000d, 10);
        engine.addEntity(source);
        engine.addEntity(poorDestination);
        engine.addEntity(fleet);

        engine.update(0f);

        assertEquals(TradeAIComponent.State.IDLE, ai(fleet).state);
        assertNull(ai(fleet).buyStationId);
        assertTrue(ai(fleet).routeSearchCooldown > 0f);
    }

    @Test
    void failedRouteCacheИнвалидируетсяПриИзмененииРынка() {
        Engine engine = engine(new EconomicLedger());
        Entity source = station("Source", 0f, 100, 100, 9f, 10f, 100_000d);
        Entity destination = station("Poor", 100f, 0, 100, 20f, 22f, 5d);
        Entity fleet = fleet("Trader", 0f, 1_000d, 10);
        engine.addEntity(source);
        engine.addEntity(destination);
        engine.addEntity(fleet);

        engine.update(0f);
        assertEquals(TradeAIComponent.State.IDLE, ai(fleet).state);
        assertEquals(1f, ai(fleet).routeSearchCooldown, 0f);

        assertTrue(wallet(destination).creditFromSource(Money.fromCredits(1_000d)));
        engine.update(1f);

        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, ai(fleet).state);
        assertEquals(id(source), ai(fleet).buyStationId);
        assertEquals(id(destination), ai(fleet).sellStationId);
    }

    @Test
    void failedRouteCacheИнвалидируетсяПриИзмененииПрофиляФлота() {
        Engine engine = engine(new EconomicLedger());
        Entity source = station("Source", 0f, 100, 100, 9f, 10f, 100_000d);
        Entity destination = station("Destination", 100f, 0, 100, 20f, 22f, 100_000d);
        Entity fleet = fleet("PoorTrader", 0f, 5d, 10);
        engine.addEntity(source);
        engine.addEntity(destination);
        engine.addEntity(fleet);

        engine.update(0f);
        assertEquals(TradeAIComponent.State.IDLE, ai(fleet).state);
        assertEquals(1f, ai(fleet).routeSearchCooldown, 0f);

        assertTrue(wallet(fleet).creditFromSource(Money.fromCredits(100d)));
        engine.update(1f);

        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, ai(fleet).state);
        assertEquals(id(source), ai(fleet).buyStationId);
        assertEquals(id(destination), ai(fleet).sellStationId);
    }

    @Test
    void учитываетЛимитКошелькаПродавцаИПокупателяПриРазмереПартии() {
        Engine engine = engine(new EconomicLedger());
        Entity source = station("Source", 0f, 100, 100, 9f, 10f, 100_000d);
        Entity destination = station("Destination", 100f, 0, 100, 20f, 22f, 50d);
        Entity fleet = fleet("Trader", 0f, 25d, 10);
        engine.addEntity(source);
        engine.addEntity(destination);
        engine.addEntity(fleet);

        engine.update(0f);

        assertEquals(2, ai(fleet).targetAmount);
        assertEquals(Money.fromCredits(20d), ai(fleet).expectedProfitMilliCredits);
    }

    @Test
    void специализацияИТипГрузовогоОтсекаОграничиваютНовыеПокупки() {
        Engine engine = engine(new EconomicLedger());
        Entity source = station("FoodSource", 0f, 100, 100, 9f, 10f, 100_000d);
        Entity destination = station("FoodDestination", 100f, 0, 100, 20f, 22f, 100_000d);
        Entity fleet = fleet("MaterialTrader", 0f, 1_000d, 10);
        fleet.add(new ShipComponent(ShipType.MATERIAL_CARRIER));
        ai(fleet).specializedItem = Constants.ITEM_FOOD;
        engine.addEntity(source);
        engine.addEntity(destination);
        engine.addEntity(fleet);

        engine.update(0f);

        assertEquals(TradeAIComponent.State.IDLE, ai(fleet).state);
        assertEquals(0, ai(fleet).targetAmount);
    }

    @Test
    void ужеИмеющийсяНесовместимыйГрузМожноАварийноПродать() {
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = engine(ledger);
        Entity destination = station("Buyer", 50f, 0, 100, 20f, 22f, 100_000d);
        Entity fleet = fleet("MaterialShip", 0f, 100d, 10);
        fleet.add(new ShipComponent(ShipType.MATERIAL_CARRIER));
        inventory(fleet).stock[Constants.ITEM_FOOD] = 3;
        engine.addEntity(destination);
        engine.addEntity(fleet);

        engine.update(0f);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_SELL, ai(fleet).state);
        assertEquals(id(destination), ai(fleet).sellStationId);

        engine.update(1f);
        engine.update(0f);

        assertEquals(0, inventory(fleet).stock[Constants.ITEM_FOOD]);
        assertEquals(3, inventory(destination).stock[Constants.ITEM_FOOD]);
        assertEquals(Money.fromCredits(160d), wallet(fleet).getBalanceMilliCredits());
        assertEquals(1, ledger.size());
    }

    @Test
    void исчезнувшаяСтанцияОтменяетМаршрутИВключаетCooldown() {
        Engine engine = engine(new EconomicLedger());
        Entity source = station("Source", 100f, 100, 100, 9f, 10f, 100_000d);
        Entity destination = station("Destination", 200f, 0, 100, 20f, 22f, 100_000d);
        Entity fleet = fleet("Trader", 0f, 1_000d, 10);
        engine.addEntity(source);
        engine.addEntity(destination);
        engine.addEntity(fleet);

        engine.update(0f);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, ai(fleet).state);
        engine.removeEntity(source);
        engine.update(0.1f);

        assertEquals(TradeAIComponent.State.IDLE, ai(fleet).state);
        assertNull(ai(fleet).targetStationId);
        assertEquals(1f, ai(fleet).routeSearchCooldown, 0f);
    }

    @Test
    void некорректнаяСкоростьНеПовреждаетМаршрутИПозицию() {
        Engine engine = engine(new EconomicLedger());
        Entity source = station("Source", 100f, 100, 100, 9f, 10f, 100_000d);
        Entity destination = station("Destination", 200f, 0, 100, 20f, 22f, 100_000d);
        Entity fleet = fleet("Trader", 0f, 1_000d, 10);
        engine.addEntity(source);
        engine.addEntity(destination);
        engine.addEntity(fleet);
        engine.update(0f);

        ai(fleet).movementSpeed = Float.NaN;
        float x = transform(fleet).position.x;
        engine.update(1f);

        assertEquals(x, transform(fleet).position.x, 0f);
        assertEquals(TradeAIComponent.State.TRAVEL_TO_BUY, ai(fleet).state);
    }

    @Test
    void отсутствиеМаршрутаИспользуетCooldownИНеТеряетКошелёк() {
        Engine engine = engine(new EconomicLedger());
        Entity fleet = fleet("Trader", 0f, 123d, 10);
        engine.addEntity(fleet);

        engine.update(0f);
        assertEquals(1f, ai(fleet).routeSearchCooldown, 0f);
        engine.update(0.4f);
        assertEquals(0.6f, ai(fleet).routeSearchCooldown, 0.00001f);
        assertEquals(Money.fromCredits(123d), wallet(fleet).getBalanceMilliCredits());
    }

    @Test
    void рынокБезКошелькаНеСчитаетсяАктивнойСтанцией() {
        Engine engine = engine(new EconomicLedger());
        Entity source = station("Source", 0f, 100, 100, 9f, 10f, 100_000d);
        source.remove(WalletComponent.class);
        Entity destination = station("Destination", 100f, 0, 100, 20f, 22f, 100_000d);
        Entity fleet = fleet("Trader", 0f, 1_000d, 10);
        engine.addEntity(source);
        engine.addEntity(destination);
        engine.addEntity(fleet);

        engine.update(0f);
        assertEquals(TradeAIComponent.State.IDLE, ai(fleet).state);
    }

    @Test
    void объектБезPersistentIdНеУчаствуетВТорговойСимуляции() {
        Engine engine = engine(new EconomicLedger());
        Entity fleet = fleet("Trader", 0f, 100d, 10);
        fleet.remove(EntityIdComponent.class);
        engine.addEntity(fleet);

        engine.update(0f);

        assertEquals(TradeAIComponent.State.IDLE, ai(fleet).state);
        assertEquals(0f, ai(fleet).routeSearchCooldown, 0f);
    }

    @Test
    void отрицательныйИНеконечныйDeltaИгнорируются() {
        Engine engine = engine(new EconomicLedger());
        Entity fleet = fleet("Trader", 0f, 100d, 10);
        engine.addEntity(fleet);

        engine.update(-1f);
        engine.update(Float.NaN);
        assertEquals(TradeAIComponent.State.IDLE, ai(fleet).state);
        assertEquals(0f, ai(fleet).routeSearchCooldown, 0f);
    }

    private Engine engine(EconomicLedger ledger) {
        Engine engine = new Engine();
        engine.addSystem(new TradeAISystem(new SpatialHashGrid(Constants.CELL_SIZE), ledger));
        return engine;
    }

    private Entity station(
            String name,
            float x,
            int foodStock,
            int targetStock,
            float buyPrice,
            float sellPrice,
            double credits) {
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
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(transform)
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(credits))));
    }

    private Entity fleet(String name, float x, double credits, int capacity) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, 0f);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        TradeAIComponent ai = new TradeAIComponent();
        ai.cargoSpace = capacity;
        ai.movementSpeed = 100f;
        return identified(new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
                .add(transform)
                .add(inventory)
                .add(new WalletComponent(Money.fromCredits(credits)))
                .add(ai)
                .add(new ReputationComponent()));
    }

    private Entity identified(Entity entity) {
        return entity.add(new EntityIdComponent(new EntityId(nextEntityId++)));
    }

    private EntityId id(Entity entity) {
        return entity.getComponent(EntityIdComponent.class).id;
    }

    private TradeAIComponent ai(Entity entity) {
        return entity.getComponent(TradeAIComponent.class);
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

    private long totalMoney(Entity... entities) {
        long total = 0L;
        for (Entity entity : entities) {
            total += wallet(entity).getBalanceMilliCredits();
        }
        return total;
    }

    private int totalFood(Entity... entities) {
        int total = 0;
        for (Entity entity : entities) {
            total += inventory(entity).stock[Constants.ITEM_FOOD];
        }
        return total;
    }
}
