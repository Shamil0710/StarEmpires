package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.Money;
import com.spacesim.model.Recipe;
import com.spacesim.model.ShipType;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityDetailsUITest {
    @Test
    void описываетСтанциюСРынкомПроизводствомКошелькомИПолнымИнвентарём() {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 700;
        inventory.stock[Constants.ITEM_ORE] = 125;
        inventory.stock[Constants.ITEM_ENERGY] = 40;

        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_ORE, 300, 1.5f);
        market.buyPrices[Constants.ITEM_ORE] = 8.5f;
        market.sellPrices[Constants.ITEM_ORE] = 11.25f;

        ProductionComponent production = new ProductionComponent();
        production.recipes.add(new Recipe("Выплавка стали", 12f)
                .input(Constants.ITEM_ORE, 2)
                .output(Constants.ITEM_STEEL, 1));
        production.progressSeconds = 4.5f;

        TransformComponent transform = new TransformComponent();
        transform.position.set(120.25f, -30f);

        Entity station = new Entity()
                .add(new IdentityComponent("Орбитальная кузница", IdentityComponent.Kind.STATION))
                .add(new FactionComponent(Constants.FACTION_MINERS))
                .add(transform)
                .add(inventory)
                .add(new WalletComponent(Money.fromCredits(10_000.25d)))
                .add(market)
                .add(production);

        EntityDetailsUI.DetailsText details = EntityDetailsUI.describe(station);

        assertEquals("Орбитальная кузница", details.title());
        assertTrue(details.body().contains("Тип: Станция"));
        assertTrue(details.body().contains("Позиция: x=120.3, y=-30.0"));
        assertTrue(details.body().contains("Фракция: Шахтёры"));
        assertTrue(details.body().contains("Кредиты: 10000.3 кр."));
        assertTrue(details.body().contains("Руда [Материалы]: 125 ед."));
        assertTrue(details.body().contains("Энергия [Газы и жидкости]: 40 ед."));
        assertFalse(details.body().contains("Продовольствие:"));
        assertTrue(details.body().contains("Заполнено: 165 / 700 ед."));
        assertTrue(details.body().contains("Цель: 300 ед."));
        assertTrue(details.body().contains("Покупка: 8.5 кр."));
        assertTrue(details.body().contains("Продажа: 11.3 кр."));
        assertTrue(details.body().contains("Потребление: 1.5 ед./с"));
        assertTrue(details.body().contains("Активный рецепт: Выплавка стали"));
        assertTrue(details.body().contains("Входы: Руда × 2"));
        assertTrue(details.body().contains("Выходы: Сталь × 1"));
        assertTrue(details.body().contains("Прогресс: 4.5 / 12.0 с"));
    }

    @Test
    void описываетДвижущийсяФлотИРазрешаетPersistentЦельЧерезRegistry() {
        EntityId targetId = new EntityId(10L);
        Entity target = new Entity()
                .add(new EntityIdComponent(targetId))
                .add(new IdentityComponent("Аграрный узел", IdentityComponent.Kind.STATION));
        EntityRegistry registry = new EntityRegistry();
        registry.register(target);

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 80;
        inventory.stock[Constants.ITEM_FOOD] = 24;

        TradeAIComponent tradeAI = new TradeAIComponent();
        tradeAI.state = TradeAIComponent.State.TRAVEL_TO_SELL;
        tradeAI.movementSpeed = 75f;
        tradeAI.specializedItem = Constants.ITEM_FOOD;
        tradeAI.cargoSpace = 60;
        tradeAI.targetStationId = targetId;
        tradeAI.targetItem = Constants.ITEM_FOOD;
        tradeAI.targetAmount = 24;
        tradeAI.expectedProfitMilliCredits = Money.fromCredits(315.4d);
        tradeAI.routeSearchCooldown = 2.25f;

        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_TRADE_LEAGUE, 18f);
        reputation.addReputation(Constants.FACTION_MINERS, -7.5f);

        Entity fleet = new Entity()
                .add(new IdentityComponent("Караван Альфа", IdentityComponent.Kind.FLEET))
                .add(inventory)
                .add(new WalletComponent(Money.fromCredits(2450.75d)))
                .add(new ShipComponent(ShipType.FINISHED_GOODS_CARRIER))
                .add(tradeAI)
                .add(reputation);

        EntityDetailsUI.DetailsText details = EntityDetailsUI.describe(fleet, registry);

        assertEquals("Караван Альфа", details.title());
        assertTrue(details.body().contains("Тип: Перевозчик готовых товаров"));
        assertTrue(details.body().contains("Класс: Перевозчик готовых товаров"));
        assertTrue(details.body().contains("Грузовое назначение: Готовые товары"));
        assertTrue(details.body().contains("Продовольствие [Готовые товары]: 24 ед."));
        assertTrue(details.body().contains("Позиция: не задана"));
        assertTrue(details.body().contains("Состояние: летит к станции продажи"));
        assertTrue(details.body().contains("Скорость: 75.0 ед./с"));
        assertTrue(details.body().contains("Специализация: Продовольствие"));
        assertTrue(details.body().contains("Кредиты: 2450.8 кр."));
        assertTrue(details.body().contains("Груз: 24 / 60 ед."));
        assertTrue(details.body().contains("Цель: Аграрный узел"));
        assertTrue(details.body().contains("Товар: Продовольствие"));
        assertTrue(details.body().contains("Количество: 24 ед."));
        assertTrue(details.body().contains("Ожидаемая прибыль: 315.4 кр."));
        assertTrue(details.body().contains("Новый поиск через: 2.3 с"));
        assertTrue(details.body().contains("Нейтралы: 0.0"));
        assertTrue(details.body().contains("Торговая лига: 18.0"));
        assertTrue(details.body().contains("Шахтёры: -7.5"));
    }

    @Test
    void описываетДобывающийКорабльИРазрешаетPersistentЦелиЧерезRegistry() {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 40;
        inventory.stock[Constants.ITEM_ORE] = 7;

        EntityId asteroidId = new EntityId(21L);
        EntityId baseId = new EntityId(22L);
        Entity asteroidTarget = new Entity()
                .add(new EntityIdComponent(asteroidId))
                .add(new IdentityComponent("Астероид W-1", IdentityComponent.Kind.ASTEROID));
        Entity homeBase = new Entity()
                .add(new EntityIdComponent(baseId))
                .add(new IdentityComponent("База Ковчег", IdentityComponent.Kind.STATION));
        EntityRegistry registry = new EntityRegistry();
        registry.register(asteroidTarget);
        registry.register(homeBase);

        MiningComponent mining = new MiningComponent(Constants.ITEM_ORE, 1.25f);
        mining.active = false;
        mining.state = MiningComponent.State.RETURNING_TO_BASE;
        mining.movementSpeed = 140f;
        mining.extractionRange = 16f;
        mining.extractionRemainder = 0.75d;
        mining.totalMined = 18L;
        mining.totalDelivered = 11L;
        mining.targetAsteroidId = asteroidId;
        mining.homeBaseId = baseId;

        Entity miner = new Entity()
                .add(new IdentityComponent("Старатель", IdentityComponent.Kind.FLEET))
                .add(new ShipComponent(ShipType.MINING_SHIP))
                .add(inventory)
                .add(new WalletComponent(Money.fromCredits(96.5d)))
                .add(mining);

        EntityDetailsUI.DetailsText details = EntityDetailsUI.describe(miner, registry);

        assertEquals("Старатель", details.title());
        assertTrue(details.body().contains("Тип: Добывающий корабль"));
        assertTrue(details.body().contains("Грузовое назначение: добываемые ресурсы"));
        assertTrue(details.body().contains("Добыча\n"));
        assertTrue(details.body().contains("Состояние: остановлена"));
        assertTrue(details.body().contains("Этап: Возврат на базу"));
        assertTrue(details.body().contains("Ресурс: Руда"));
        assertTrue(details.body().contains("Скорость: 1.3 ед./с"));
        assertTrue(details.body().contains("Скорость полёта: 140.0 ед./с"));
        assertTrue(details.body().contains("Радиус добычи: 16.0 ед."));
        assertTrue(details.body().contains("Целевой астероид: Астероид W-1"));
        assertTrue(details.body().contains("База разгрузки: База Ковчег"));
        assertTrue(details.body().contains("Дробный остаток: 0.8 ед."));
        assertTrue(details.body().contains("Всего добыто: 18 ед."));
        assertTrue(details.body().contains("Доставлено: 11 ед."));
        assertTrue(details.body().contains("Кредиты: 96.5 кр."));
        assertFalse(details.body().contains("Ожидаемая прибыль:"));
    }

    @Test
    void недоступнаяPersistentЦельПоказываетStableIdВместоRuntimeСсылки() {
        TradeAIComponent tradeAI = new TradeAIComponent();
        tradeAI.targetStationId = new EntityId(99L);
        Entity fleet = new Entity().add(tradeAI);

        EntityDetailsUI.DetailsText details = EntityDetailsUI.describe(fleet, new EntityRegistry());

        assertTrue(details.body().contains("Цель: entity:99 (недоступна)"));
    }

    @Test
    void описываетКонечныйЗапасАстероида() {
        AsteroidComponent asteroid = new AsteroidComponent(
                "NW-1", Constants.ITEM_ORE, 80L);
        asteroid.remainingResource = 30L;
        TransformComponent transform = new TransformComponent();
        transform.position.set(150f, 1230f);
        Entity entity = new Entity()
                .add(new IdentityComponent("Астероид NW-1-7", IdentityComponent.Kind.ASTEROID))
                .add(transform)
                .add(asteroid);

        EntityDetailsUI.DetailsText details = EntityDetailsUI.describe(entity);

        assertEquals("Астероид NW-1-7", details.title());
        assertTrue(details.body().contains("Тип: Астероид"));
        assertTrue(details.body().contains("Позиция: x=150.0, y=1230.0"));
        assertTrue(details.body().contains("Источник ресурса"));
        assertTrue(details.body().contains("Ресурс: Руда"));
        assertTrue(details.body().contains("Осталось: 30 / 80 ед."));
        assertTrue(details.body().contains("Заполненность: 37.5 %"));
        assertTrue(details.body().contains("Точка пояса: NW-1"));
        assertFalse(details.body().contains("Инвентарь"));
    }

    @Test
    void описываетБоевойКорабльИОтражаетПотерюБоеготовности() {
        CombatComponent combat = new CombatComponent(80f, 120f, 25f, 60f, 14.5f, 180f);
        Entity fighter = new Entity()
                .add(new IdentityComponent("Корвет Страж", IdentityComponent.Kind.FLEET))
                .add(new ShipComponent(ShipType.COMBAT_SHIP))
                .add(combat);

        EntityDetailsUI.DetailsText operational = EntityDetailsUI.describe(fighter);

        assertTrue(operational.body().contains("Тип: Боевой корабль"));
        assertTrue(operational.body().contains(
                "Грузовое назначение: коммерческий груз не предусмотрен"));
        assertTrue(operational.body().contains("Боевая система\n"));
        assertTrue(operational.body().contains("Состояние: боеготов"));
        assertTrue(operational.body().contains("Корпус: 80.0 / 120.0"));
        assertTrue(operational.body().contains("Щиты: 25.0 / 60.0"));
        assertTrue(operational.body().contains("Урон: 14.5 ед./с"));
        assertTrue(operational.body().contains("Дальность: 180.0 ед."));

        combat.hull = 0f;
        assertTrue(EntityDetailsUI.describe(fighter).body().contains("Состояние: небоеспособен"));
    }

    @Test
    void безопасноФорматируетОтсутствующийВыборИЧастичнуюСущность() {
        EntityDetailsUI.DetailsText empty = EntityDetailsUI.describe(null);

        assertEquals("Объект не выбран", empty.title());
        assertTrue(empty.body().contains("Нажмите на станцию, корабль или астероид"));

        Entity partialEntity = new Entity().add(new TradeAIComponent());
        EntityDetailsUI.DetailsText partial = EntityDetailsUI.describe(partialEntity);

        assertEquals("Безымянный объект", partial.title());
        assertTrue(partial.body().contains("Тип: Торговый корабль"));
        assertTrue(partial.body().contains("Фракция: не указана"));
        assertTrue(partial.body().contains("Инвентарь\n  отсутствует"));
        assertTrue(partial.body().contains("Состояние: ожидает маршрут"));
        assertTrue(partial.body().contains("Специализация: любой товар"));
        assertTrue(partial.body().contains("Груз: — / 100 ед."));
        assertTrue(partial.body().contains("Цель: не выбрана"));
        assertTrue(partial.body().contains("Товар: не выбран"));

        Entity unconfiguredShip = new Entity().add(new ShipComponent());
        EntityDetailsUI.DetailsText unconfigured = EntityDetailsUI.describe(unconfiguredShip);
        assertTrue(unconfigured.body().contains("Тип: Корабль (тип не задан)"));
        assertTrue(unconfigured.body().contains("Класс: не задан"));
        assertTrue(unconfigured.body().contains("Грузовое назначение: не определено"));
    }
}
