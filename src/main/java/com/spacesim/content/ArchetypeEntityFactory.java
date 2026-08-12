package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.PriceHistoryComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.economy.Money;

import java.util.Objects;

/**
 * Универсальная граница между data-driven archetypes и runtime ECS-компонентами.
 *
 * <p>Класс не знает о демонстрационных именах/координатах и не выдаёт {@code EntityId}; он только
 * материализует один экземпляр указанного archetype. Поэтому новый station/ship archetype можно
 * загрузить из данных и создать тем же simulation-кодом без нового Java subclass/enum.</p>
 */
public final class ArchetypeEntityFactory {
    private ArchetypeEntityFactory() {
        throw new AssertionError("ArchetypeEntityFactory не создаёт экземпляров");
    }

    /**
     * Создаёт station Entity со складом, рынком, капиталом, faction и optional production.
     *
     * @param catalog валидированный content catalog
     * @param archetypeId stable station archetype ID
     * @param name отображаемое имя конкретного экземпляра
     * @param x координата X
     * @param y координата Y
     * @return новый независимый runtime Entity без EntityId
     */
    public static Entity createStation(
            ContentCatalog catalog, String archetypeId, String name, float x, float y) {
        ContentCatalog checked = requireCatalog(catalog);
        ContentCatalog.StationArchetypeDefinition definition = checked.findStationArchetype(archetypeId);
        if (definition == null) {
            throw new IllegalArgumentException("Неизвестный station archetype: " + archetypeId);
        }
        ContentCatalog.FactionDefinition faction = checked.findFaction(definition.factionId());
        if (faction == null) {
            throw new IllegalStateException("Station archetype потерял faction: " + definition.factionId());
        }

        TransformComponent transform = transform(x, y);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = definition.inventoryCapacity();
        MarketComponent market = new MarketComponent();
        for (ContentCatalog.MarketDefinition rule : definition.markets()) {
            ContentCatalog.ItemDefinition item = checked.findItem(rule.itemId());
            if (item == null) {
                throw new IllegalStateException("Station archetype потерял item: " + rule.itemId());
            }
            inventory.stock[item.runtimeId()] = rule.initialStock();
            market.configureTradableItem(
                    item.runtimeId(), rule.targetStock(), rule.consumptionPerSecond());
        }

        Entity entity = new Entity()
                .add(new IdentityComponent(requireName(name), IdentityComponent.Kind.STATION))
                .add(new ArchetypeComponent(definition.id()))
                .add(transform)
                .add(inventory)
                .add(new WalletComponent(Money.fromCredits(definition.startingCredits())))
                .add(market)
                .add(new FactionComponent(faction.runtimeId()))
                .add(new PriceHistoryComponent());
        if (definition.recipeId() != null) {
            ProductionComponent production = new ProductionComponent();
            production.recipes.add(checked.createRuntimeRecipe(definition.recipeId()));
            entity.add(production);
        }
        return entity;
    }

    /**
     * Создаёт коммерческий транспорт из ship archetype.
     *
     * @param catalog валидированный content catalog
     * @param archetypeId stable ship archetype ID
     * @param name отображаемое имя экземпляра
     * @param x координата X
     * @param y координата Y
     * @param specializedItemId persistent item ID специализации
     * @param factionId persistent faction ID владельца
     * @return новый торговый Entity без EntityId
     */
    public static Entity createTrader(
            ContentCatalog catalog,
            String archetypeId,
            String name,
            float x,
            float y,
            String specializedItemId,
            String factionId) {
        ContentCatalog checked = requireCatalog(catalog);
        ContentCatalog.ShipArchetypeDefinition definition = requireShip(checked, archetypeId);
        if (!definition.role().isCarrier()) {
            throw new IllegalArgumentException("Ship archetype не является carrier: " + archetypeId);
        }
        ContentCatalog.ItemDefinition item = requireItem(checked, specializedItemId);
        if (!definition.role().canPurchase(item.category(), item.mineable())) {
            throw new IllegalArgumentException(
                    "Cargo policy archetype несовместима с item: " + archetypeId + " -> " + specializedItemId);
        }

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = definition.cargoCapacity();
        TradeAIComponent trade = new TradeAIComponent();
        trade.cargoSpace = definition.cargoCapacity();
        trade.movementSpeed = definition.movementSpeed();
        trade.specializedItem = item.runtimeId();

        return new Entity()
                .add(new IdentityComponent(requireName(name), IdentityComponent.Kind.FLEET))
                .add(new ArchetypeComponent(definition.id()))
                .add(transform(x, y))
                .add(inventory)
                .add(new WalletComponent(Money.fromCredits(definition.startingCredits())))
                .add(new ShipComponent(definition.role()))
                .add(trade)
                .add(new FactionComponent(requireFaction(checked, factionId).runtimeId()));
    }

    /**
     * Создаёт добывающий корабль из data archetype.
     *
     * @param catalog валидированный content catalog
     * @param archetypeId stable mining ship archetype ID
     * @param name отображаемое имя экземпляра
     * @param x координата X
     * @param y координата Y
     * @param resourceItemId persistent ID добываемого ресурса
     * @param factionId persistent faction ID владельца
     * @return новый mining Entity без EntityId
     */
    public static Entity createMiner(
            ContentCatalog catalog,
            String archetypeId,
            String name,
            float x,
            float y,
            String resourceItemId,
            String factionId) {
        ContentCatalog checked = requireCatalog(catalog);
        ContentCatalog.ShipArchetypeDefinition definition = requireShip(checked, archetypeId);
        if (!definition.role().isMining()) {
            throw new IllegalArgumentException("Ship archetype не является miner: " + archetypeId);
        }
        ContentCatalog.ItemDefinition resource = requireItem(checked, resourceItemId);
        if (!resource.mineable()) {
            throw new IllegalArgumentException("Mining ship требует mineable item: " + resourceItemId);
        }

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = definition.cargoCapacity();
        MiningComponent mining = new MiningComponent(resource.runtimeId(), definition.extractionPerSecond());
        mining.movementSpeed = definition.movementSpeed();
        mining.extractionRange = definition.extractionRange();
        mining.dockingRange = definition.dockingRange();

        return new Entity()
                .add(new IdentityComponent(requireName(name), IdentityComponent.Kind.FLEET))
                .add(new ArchetypeComponent(definition.id()))
                .add(transform(x, y))
                .add(inventory)
                .add(new WalletComponent(Money.fromCredits(definition.startingCredits())))
                .add(new ShipComponent(definition.role()))
                .add(mining)
                .add(new FactionComponent(requireFaction(checked, factionId).runtimeId()));
    }

    /**
     * Создаёт боевой корабль из data archetype.
     *
     * @param catalog валидированный content catalog
     * @param archetypeId stable combat ship archetype ID
     * @param name отображаемое имя экземпляра
     * @param x координата X
     * @param y координата Y
     * @param factionId persistent faction ID владельца
     * @return новый combat Entity без EntityId
     */
    public static Entity createCombatShip(
            ContentCatalog catalog,
            String archetypeId,
            String name,
            float x,
            float y,
            String factionId) {
        ContentCatalog checked = requireCatalog(catalog);
        ContentCatalog.ShipArchetypeDefinition definition = requireShip(checked, archetypeId);
        if (!definition.role().isCombat()) {
            throw new IllegalArgumentException("Ship archetype не является combat: " + archetypeId);
        }

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = definition.cargoCapacity();
        CombatComponent combat = new CombatComponent(
                definition.hull(),
                definition.hull(),
                definition.shields(),
                definition.shields(),
                definition.damagePerSecond(),
                definition.weaponRange());
        return new Entity()
                .add(new IdentityComponent(requireName(name), IdentityComponent.Kind.FLEET))
                .add(new ArchetypeComponent(definition.id()))
                .add(transform(x, y))
                .add(inventory)
                .add(new ShipComponent(definition.role()))
                .add(combat)
                .add(new FactionComponent(requireFaction(checked, factionId).runtimeId()));
    }

    private static ContentCatalog requireCatalog(ContentCatalog catalog) {
        return Objects.requireNonNull(catalog, "ContentCatalog не задан");
    }

    private static ContentCatalog.ShipArchetypeDefinition requireShip(
            ContentCatalog catalog, String archetypeId) {
        ContentCatalog.ShipArchetypeDefinition definition = catalog.findShipArchetype(archetypeId);
        if (definition == null) {
            throw new IllegalArgumentException("Неизвестный ship archetype: " + archetypeId);
        }
        return definition;
    }

    private static ContentCatalog.ItemDefinition requireItem(ContentCatalog catalog, String itemId) {
        ContentCatalog.ItemDefinition definition = catalog.findItem(itemId);
        if (definition == null) {
            throw new IllegalArgumentException("Неизвестный item content ID: " + itemId);
        }
        return definition;
    }

    private static ContentCatalog.FactionDefinition requireFaction(ContentCatalog catalog, String factionId) {
        ContentCatalog.FactionDefinition definition = catalog.findFaction(factionId);
        if (definition == null) {
            throw new IllegalArgumentException("Неизвестный faction content ID: " + factionId);
        }
        return definition;
    }

    private static TransformComponent transform(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Координаты archetype instance должны быть конечными");
        }
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        return transform;
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "Имя Entity не задано");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Имя Entity не должно быть пустым");
        }
        return name;
    }
}
