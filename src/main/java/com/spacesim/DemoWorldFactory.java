package com.spacesim;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.PriceHistoryComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.Recipe;
import com.spacesim.model.ShipType;

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
 * сбалансированы. Для каждого из пяти товаров создаётся отдельный специализированный совместимый
 * транспорт: это не позволяет всем пустым кораблям одновременно переключиться на самый дорогой
 * товар. Дополнительный добывающий корабль абстрактно наполняет свой трюм рудой, а боевой корабль
 * демонстрирует отдельный профиль корпуса без участия в торговле.</p>
 *
 * <p>Фабрика не обращается к libGDX/OpenGL и не регистрирует системы Ashley. Каждый вызов
 * {@link #createEntities()} возвращает новый независимый граф сущностей и компонентов, который
 * можно безопасно добавлять в отдельный {@link com.badlogic.ashley.core.Engine} или проверять в
 * unit-тесте.</p>
 */
public final class DemoWorldFactory {
    private static final int STATION_CAPACITY = 2_500;
    private static final float FLEET_STARTING_CREDITS = 12_000f;

    private DemoWorldFactory() {
        throw new AssertionError("Фабрика демонстрационного мира не создаёт экземпляров");
    }

    /**
     * Создаёт полный набор станций и кораблей демонстрационной сцены.
     *
     * <p>Порядок результата стабилен: сначала шесть станций от первичных источников к конечному
     * потребителю, затем пять специализированных транспортов в порядке идентификаторов товаров,
     * добывающий и боевой корабли. Список неизменяем, однако сами сущности и их ECS-компоненты
     * предназначены для изменения игровыми системами.</p>
     *
     * @return неизменяемый список из шести новых станций и семи новых кораблей
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

        Entity oreTransport = createTradingShip(
                "Материаловоз Атлас", 170f, 300f, 64f, 140,
                Constants.ITEM_ORE, ShipType.MATERIAL_CARRIER, Constants.FACTION_MINERS);
        Entity energyTransport = createTradingShip(
                "Танкер Луч", 270f, 300f, 70f, 160,
                Constants.ITEM_ENERGY, ShipType.GAS_LIQUID_CARRIER, Constants.FACTION_NEUTRAL);
        Entity foodTransport = createTradingShip(
                "Контейнеровоз Аврора", 370f, 250f, 74f, 100,
                Constants.ITEM_FOOD, ShipType.FINISHED_GOODS_CARRIER,
                Constants.FACTION_TRADE_LEAGUE);
        Entity steelTransport = createTradingShip(
                "Материаловоз Вулкан", 470f, 200f, 80f, 140,
                Constants.ITEM_STEEL, ShipType.MATERIAL_CARRIER, Constants.FACTION_MINERS);
        Entity weaponsTransport = createTradingShip(
                "Контейнеровоз Щит", 590f, 260f, 86f, 80,
                Constants.ITEM_WEAPONS, ShipType.FINISHED_GOODS_CARRIER,
                Constants.FACTION_TRADE_LEAGUE);
        Entity miningShip = createMiningShip("Добытчик Старатель", 210f, 335f);
        Entity combatShip = createCombatShip("Фрегат Страж", 560f, 360f);

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
                weaponsTransport,
                miningShip,
                combatShip);
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

    /** Создаёт пустой специализированный транспорт с совместимым отсеком и репутацией. */
    private static Entity createTradingShip(
            String name,
            float x,
            float y,
            float movementSpeed,
            int cargoSpace,
            int specializedItem,
            ShipType shipType,
            int factionId) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = cargoSpace;

        TradeAIComponent tradeAI = new TradeAIComponent();
        tradeAI.cargoSpace = cargoSpace;
        tradeAI.credits = FLEET_STARTING_CREDITS;
        tradeAI.movementSpeed = movementSpeed;
        tradeAI.specializedItem = specializedItem;

        ShipComponent ship = new ShipComponent(shipType);
        if (!ship.canPurchaseItem(specializedItem)) {
            throw new IllegalArgumentException(
                    "Тип корабля не совместим со специализацией: " + name);
        }

        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(Constants.FACTION_TRADE_LEAGUE, 25f);
        reputation.addReputation(Constants.FACTION_MINERS, 10f);

        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
                .add(transform)
                .add(inventory)
                .add(ship)
                .add(tradeAI)
                .add(reputation)
                .add(new FactionComponent(factionId));
    }

    /** Создаёт автономный добывающий корабль, постепенно наполняющий собственный трюм рудой. */
    private static Entity createMiningShip(String name, float x, float y) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 80;

        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
                .add(transform)
                .add(inventory)
                .add(new ShipComponent(ShipType.MINING_SHIP))
                .add(new MiningComponent(Constants.ITEM_ORE, 0.5f))
                .add(new FactionComponent(Constants.FACTION_MINERS));
    }

    /** Создаёт боевой корабль с отдельными характеристиками и без торгового автомата. */
    private static Entity createCombatShip(String name, float x, float y) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.velocity.set(1f, 0.35f);

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 0;

        CombatComponent combat = new CombatComponent(
                320f,
                320f,
                180f,
                180f,
                42f,
                150f);

        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
                .add(transform)
                .add(inventory)
                .add(new ShipComponent(ShipType.COMBAT_SHIP))
                .add(combat)
                .add(new FactionComponent(Constants.FACTION_TRADE_LEAGUE));
    }
}
