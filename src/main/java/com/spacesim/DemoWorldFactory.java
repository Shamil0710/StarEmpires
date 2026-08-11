package com.spacesim;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.PriceHistoryComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.Recipe;

import java.util.List;

/**
 * Создаёт детерминированный демонстрационный мир с полной производственной экономикой.
 *
 * <p>Сценарий содержит пять одновременно работающих производств:</p>
 * <pre>
 * первичный ресурс: руда
 * первичный ресурс: энергия
 * энергия -&gt; продовольствие
 * руда + энергия -&gt; сталь
 * сталь + энергия -&gt; вооружение
 * </pre>
 * <p>Шестая станция является конечным потребителем энергии, продовольствия, стали и вооружения.
 * Производительность звеньев и базовое потребление подобраны так, чтобы их номинальные темпы были
 * сбалансированы. Для каждого из пяти товаров создаётся отдельный специализированный транспорт:
 * это не позволяет всем пустым кораблям одновременно переключиться на самый дорогой товар.</p>
 *
 * <p>Фабрика не обращается к libGDX/OpenGL и не регистрирует системы Ashley. Каждый вызов
 * {@link #createEntities()} возвращает новый независимый граф сущностей и компонентов, который
 * можно безопасно добавлять в отдельный {@link com.badlogic.ashley.core.Engine} или проверять в
 * unit-тесте.</p>
 */
public final class DemoWorldFactory {
    private static final int STATION_CAPACITY = 2_500;
    private static final int FLEET_CARGO_SPACE = 100;
    private static final float FLEET_STARTING_CREDITS = 12_000f;

    private DemoWorldFactory() {
        throw new AssertionError("Фабрика демонстрационного мира не создаёт экземпляров");
    }

    /**
     * Создаёт полный набор станций и кораблей демонстрационной сцены.
     *
     * <p>Порядок результата стабилен: сначала шесть станций от первичных источников к конечному
     * потребителю, затем пять специализированных транспортов в порядке идентификаторов товаров.
     * Список неизменяем, однако сами сущности и их ECS-компоненты предназначены для изменения
     * игровыми системами.</p>
     *
     * @return неизменяемый список из шести новых станций и пяти новых кораблей
     */
    public static List<Entity> createEntities() {
        Entity mine = createStation("Рудник Ковчег", 90f, 380f, Constants.FACTION_MINERS);
        configureMarket(mine, Constants.ITEM_ORE, 400, 300, 0f);
        addProduction(mine, new Recipe("Добыча руды", 4f)
                .output(Constants.ITEM_ORE, 2));

        Entity powerPlant = createStation("Энергоузел Корона", 90f, 230f, Constants.FACTION_NEUTRAL);
        configureMarket(powerPlant, Constants.ITEM_ENERGY, 400, 300, 0f);
        addProduction(powerPlant, new Recipe("Генерация энергии", 4f)
                .output(Constants.ITEM_ENERGY, 7));

        Entity farm = createStation("Агрокупол Аврора", 260f, 110f, Constants.FACTION_TRADE_LEAGUE);
        configureMarket(farm, Constants.ITEM_ENERGY, 80, 120, 0f);
        configureMarket(farm, Constants.ITEM_FOOD, 320, 240, 0f);
        addProduction(farm, new Recipe("Выращивание продовольствия", 6f)
                .input(Constants.ITEM_ENERGY, 2)
                .output(Constants.ITEM_FOOD, 6));

        Entity foundry = createStation("Кузница Гелиос", 330f, 390f, Constants.FACTION_MINERS);
        configureMarket(foundry, Constants.ITEM_ORE, 200, 300, 0f);
        configureMarket(foundry, Constants.ITEM_ENERGY, 80, 120, 0f);
        configureMarket(foundry, Constants.ITEM_STEEL, 240, 180, 0f);
        addProduction(foundry, new Recipe("Выплавка стали", 4f)
                .input(Constants.ITEM_ORE, 2)
                .input(Constants.ITEM_ENERGY, 1)
                .output(Constants.ITEM_STEEL, 2));

        Entity arsenal = createStation("Арсенал Титан", 500f, 290f, Constants.FACTION_TRADE_LEAGUE);
        configureMarket(arsenal, Constants.ITEM_ENERGY, 80, 120, 0f);
        configureMarket(arsenal, Constants.ITEM_STEEL, 80, 120, 0f);
        configureMarket(arsenal, Constants.ITEM_WEAPONS, 120, 90, 0f);
        addProduction(arsenal, new Recipe("Сборка вооружения", 6f)
                .input(Constants.ITEM_STEEL, 2)
                .input(Constants.ITEM_ENERGY, 1)
                .output(Constants.ITEM_WEAPONS, 1));

        Entity colony = createStation("Колония Фронтир", 620f, 160f, Constants.FACTION_NEUTRAL);
        configureMarket(colony, Constants.ITEM_ENERGY, 80, 120, 1f);
        configureMarket(colony, Constants.ITEM_FOOD, 200, 300, 1f);
        configureMarket(colony, Constants.ITEM_STEEL, 80, 120, 1f / 6f);
        configureMarket(colony, Constants.ITEM_WEAPONS, 80, 120, 1f / 6f);

        Entity oreTransport = createFleet(
                "Рудовоз Атлас", 170f, 300f, 64f, Constants.ITEM_ORE);
        Entity energyTransport = createFleet(
                "Энергокурьер Луч", 270f, 300f, 70f, Constants.ITEM_ENERGY);
        Entity foodTransport = createFleet(
                "Агрокараван", 370f, 250f, 74f, Constants.ITEM_FOOD);
        Entity steelTransport = createFleet(
                "Сталевоз Вулкан", 470f, 200f, 80f, Constants.ITEM_STEEL);
        Entity weaponsTransport = createFleet(
                "Оружейный транспорт", 590f, 260f, 86f, Constants.ITEM_WEAPONS);

        return List.of(
                mine,
                powerPlant,
                farm,
                foundry,
                arsenal,
                colony,
                oreTransport,
                energyTransport,
                foodTransport,
                steelTransport,
                weaponsTransport);
    }

    /** Создаёт базовую станцию с пустым рынком, историей цен и складом. */
    private static Entity createStation(String name, float x, float y, int factionId) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = STATION_CAPACITY;

        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(transform)
                .add(inventory)
                .add(new MarketComponent())
                .add(new FactionComponent(factionId))
                .add(new PriceHistoryComponent());
    }

    /** Настраивает запас и рыночные параметры одного товара станции. */
    private static void configureMarket(
            Entity station,
            int itemId,
            int initialStock,
            int targetStock,
            float consumptionPerSecond) {
        station.getComponent(InventoryComponent.class).stock[itemId] = initialStock;
        station.getComponent(MarketComponent.class)
                .configureTradableItem(itemId, targetStock, consumptionPerSecond);
    }

    /** Добавляет станции единственный активный производственный рецепт. */
    private static void addProduction(Entity station, Recipe recipe) {
        ProductionComponent production = new ProductionComponent();
        production.recipes.add(recipe);
        station.add(production);
    }

    /** Создаёт пустой специализированный транспорт с репутацией и конечными параметрами. */
    private static Entity createFleet(
            String name,
            float x,
            float y,
            float movementSpeed,
            int specializedItem) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = FLEET_CARGO_SPACE;

        TradeAIComponent tradeAI = new TradeAIComponent();
        tradeAI.cargoSpace = FLEET_CARGO_SPACE;
        tradeAI.credits = FLEET_STARTING_CREDITS;
        tradeAI.movementSpeed = movementSpeed;
        tradeAI.specializedItem = specializedItem;

        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_TRADE_LEAGUE, 25f);
        reputation.addReputation(Constants.FACTION_MINERS, 10f);

        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
                .add(transform)
                .add(inventory)
                .add(tradeAI)
                .add(reputation);
    }
}
