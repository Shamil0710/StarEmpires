package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.Recipe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityDetailsUITest {
    @Test
    void описываетСтанциюСРынкомПроизводствомИПолнымИнвентарём() {
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
                .add(market)
                .add(production);

        EntityDetailsUI.DetailsText details = EntityDetailsUI.describe(station);

        assertEquals("Орбитальная кузница", details.title());
        assertTrue(details.body().contains("Тип: Станция"));
        assertTrue(details.body().contains("Позиция: x=120.3, y=-30.0"));
        assertTrue(details.body().contains("Фракция: Шахтёры"));
        assertTrue(details.body().contains("Руда: 125 ед."));
        assertTrue(details.body().contains("Энергия: 40 ед."));
        assertFalse(details.body().contains("Продовольствие:"));
        assertTrue(details.body().contains("Заполнено: 165 / 700 ед."));
        assertTrue(details.body().contains("Цель: 300 ед."));
        assertTrue(details.body().contains("Покупка: 8.5 кр."));
        assertTrue(details.body().contains("Продажа: 11.3 кр."));
        assertTrue(details.body().contains("Потребление: 1.5 ед./с"));
        assertTrue(details.body().contains("Активный рецепт: Выплавка стали"));
        assertTrue(details.body().contains("Прогресс: 4.5 / 12.0 с"));
    }

    @Test
    void описываетДвижущийсяФлотЕгоМаршрутГрузИДипломатию() {
        Entity target = new Entity()
                .add(new IdentityComponent("Аграрный узел", IdentityComponent.Kind.STATION));

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 80;
        inventory.stock[Constants.ITEM_FOOD] = 24;

        TradeAIComponent tradeAI = new TradeAIComponent();
        tradeAI.state = TradeAIComponent.State.TRAVEL_TO_SELL;
        tradeAI.movementSpeed = 75f;
        tradeAI.credits = 2450.75f;
        tradeAI.cargoSpace = 60;
        tradeAI.targetStation = target;
        tradeAI.targetItem = Constants.ITEM_FOOD;
        tradeAI.targetAmount = 24;
        tradeAI.expectedProfit = 315.4f;
        tradeAI.routeSearchCooldown = 2.25f;

        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_TRADE_LEAGUE, 18f);
        reputation.addReputation(Constants.FACTION_MINERS, -7.5f);

        Entity fleet = new Entity()
                .add(new IdentityComponent("Караван Альфа", IdentityComponent.Kind.FLEET))
                .add(inventory)
                .add(tradeAI)
                .add(reputation);

        EntityDetailsUI.DetailsText details = EntityDetailsUI.describe(fleet);

        assertEquals("Караван Альфа", details.title());
        assertTrue(details.body().contains("Тип: Торговый корабль"));
        assertTrue(details.body().contains("Позиция: не задана"));
        assertTrue(details.body().contains("Состояние: летит к станции продажи"));
        assertTrue(details.body().contains("Скорость: 75.0 ед./с"));
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
    void безопасноФорматируетОтсутствующийВыборИЧастичнуюСущность() {
        EntityDetailsUI.DetailsText empty = EntityDetailsUI.describe(null);

        assertEquals("Объект не выбран", empty.title());
        assertTrue(empty.body().contains("Нажмите на станцию или корабль"));

        Entity partialEntity = new Entity().add(new TradeAIComponent());
        EntityDetailsUI.DetailsText partial = EntityDetailsUI.describe(partialEntity);

        assertEquals("Безымянный объект", partial.title());
        assertTrue(partial.body().contains("Тип: Торговый корабль"));
        assertTrue(partial.body().contains("Фракция: не указана"));
        assertTrue(partial.body().contains("Инвентарь\n  отсутствует"));
        assertTrue(partial.body().contains("Состояние: ожидает маршрут"));
        assertTrue(partial.body().contains("Груз: — / 100 ед."));
        assertTrue(partial.body().contains("Цель: не выбрана"));
        assertTrue(partial.body().contains("Товар: не выбран"));
    }
}
