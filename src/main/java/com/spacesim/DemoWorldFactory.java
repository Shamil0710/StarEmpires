package com.spacesim;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ArchetypeEntityFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityIdAllocator;

import java.util.List;
import java.util.Objects;

/**
 * Создаёт детерминированный демонстрационный мир из versioned content archetypes.
 *
 * <p>Класс содержит только сценарные решения конкретной карты: имена экземпляров, координаты,
 * специализацию транспорта и стартовые отношения. Физические параметры кораблей, станции,
 * рынки, капитал, производственные рецепты и faction runtime IDs принадлежат
 * {@link ContentCatalog} и материализуются через {@link ArchetypeEntityFactory}.</p>
 *
 * <p>Bootstrap-сущности получают устойчивые {@link EntityIdComponent} в стабильном порядке.
 * Каждый вызов возвращает независимый граф ECS-компонентов и не требует OpenGL.</p>
 */
public final class DemoWorldFactory {
    private static final ContentCatalog DEFAULT_CONTENT = ContentCatalogLoader.loadDefault();

    private DemoWorldFactory() {
        throw new AssertionError("Фабрика демонстрационного мира не создаёт экземпляров");
    }

    /**
     * Создаёт полный demo-world с новой ID-последовательностью от единицы.
     *
     * @return шесть станций и семь кораблей
     */
    public static List<Entity> createEntities() {
        return createEntities(new EntityIdAllocator(), DEFAULT_CONTENT);
    }

    /**
     * Создаёт demo-world на встроенном production catalog и общем ID allocator.
     *
     * @param idAllocator общий allocator persistent EntityId
     * @return шесть станций и семь кораблей
     */
    public static List<Entity> createEntities(EntityIdAllocator idAllocator) {
        return createEntities(idAllocator, DEFAULT_CONTENT);
    }

    /**
     * Создаёт demo-world на явно заданном полном catalog.
     *
     * @param idAllocator общий allocator persistent EntityId
     * @param contentCatalog catalog с используемыми station/ship/faction/item archetypes
     * @return шесть станций и семь кораблей
     * @throws NullPointerException если зависимость не задана
     * @throws IllegalArgumentException если обязательный archetype/content ID отсутствует
     */
    public static List<Entity> createEntities(
            EntityIdAllocator idAllocator,
            ContentCatalog contentCatalog) {
        EntityIdAllocator ids = Objects.requireNonNull(idAllocator, "EntityIdAllocator не задан");
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");

        Entity mine = ArchetypeEntityFactory.createStation(
                content, "station.mining_base", "Шахтёрская база Ковчег", 420f, 880f);
        Entity powerPlant = ArchetypeEntityFactory.createStation(
                content, "station.power_plant", "Энергоузел Корона", 470f, 430f);
        Entity farm = ArchetypeEntityFactory.createStation(
                content, "station.agrodome", "Агрокупол Аврора", 850f, 280f);
        Entity foundry = ArchetypeEntityFactory.createStation(
                content, "station.foundry", "Кузница Гелиос", 900f, 900f);
        Entity arsenal = ArchetypeEntityFactory.createStation(
                content, "station.arsenal", "Арсенал Титан", 1350f, 730f);
        Entity colony = ArchetypeEntityFactory.createStation(
                content, "station.colony", "Колония Фронтир", 1600f, 330f);

        Entity oreTransport = trader(
                content, "ship.ore_hauler", "Материаловоз Атлас", 660f, 820f,
                "item.ore", "faction.miners");
        Entity energyTransport = trader(
                content, "ship.energy_tanker", "Танкер Луч", 650f, 500f,
                "item.energy", "faction.neutral");
        Entity foodTransport = trader(
                content, "ship.food_container", "Контейнеровоз Аврора", 1050f, 350f,
                "item.food", "faction.trade_league");
        Entity steelTransport = trader(
                content, "ship.steel_hauler", "Материаловоз Вулкан", 1100f, 800f,
                "item.steel", "faction.miners");
        Entity weaponsTransport = trader(
                content, "ship.weapons_container", "Контейнеровоз Щит", 1450f, 500f,
                "item.weapons", "faction.trade_league");

        Entity miningShip = ArchetypeEntityFactory.createMiner(
                content,
                "ship.basic_miner",
                "Добытчик Старатель",
                450f,
                930f,
                "item.ore",
                "faction.miners");
        Entity combatShip = ArchetypeEntityFactory.createCombatShip(
                content,
                "ship.guard_frigate",
                "Фрегат Страж",
                1500f,
                1050f,
                "faction.trade_league");
        combatShip.getComponent(TransformComponent.class).velocity.set(1f, 0.35f);

        List<Entity> entities = List.of(
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
        for (Entity entity : entities) {
            entity.add(new EntityIdComponent(ids.allocate()));
        }
        miningShip.getComponent(MiningComponent.class).homeBaseId =
                mine.getComponent(EntityIdComponent.class).id;
        return entities;
    }

    private static Entity trader(
            ContentCatalog content,
            String archetypeId,
            String name,
            float x,
            float y,
            String itemId,
            String factionId) {
        Entity entity = ArchetypeEntityFactory.createTrader(
                content, archetypeId, name, x, y, itemId, factionId);
        ReputationComponent reputation = new ReputationComponent();
        reputation.addReputation(requireFactionRuntimeId(content, "faction.trade_league"), 25f);
        reputation.addReputation(requireFactionRuntimeId(content, "faction.miners"), 10f);
        entity.add(reputation);
        return entity;
    }

    private static int requireFactionRuntimeId(ContentCatalog content, String factionId) {
        ContentCatalog.FactionDefinition faction = content.findFaction(factionId);
        if (faction == null) {
            throw new IllegalArgumentException("Demo-world требует faction: " + factionId);
        }
        return faction.runtimeId();
    }
}
