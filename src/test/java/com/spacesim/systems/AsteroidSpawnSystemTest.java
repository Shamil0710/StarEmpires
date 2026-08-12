package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.model.AsteroidSpawnPoint;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsteroidSpawnSystemTest {
    @Test
    void demoWorldСоздаётЧетыреИсточникаИСтабильноПополняетДоШести() {
        Engine engine = new Engine();
        engine.addSystem(new AsteroidSpawnSystem(AsteroidSpawnConfig.demoWorld()));

        engine.update(0f);
        assertEquals(4, asteroids(engine).size());
        assertUniqueValidSources(engine, 36L, 84L);

        engine.update(19.9f);
        assertEquals(4, asteroids(engine).size());
        engine.update(0.1f);
        assertEquals(6, asteroids(engine).size());
        assertUniqueValidSources(engine, 36L, 84L);

        Entity removed = asteroids(engine).get(0);
        engine.removeEntity(removed);
        assertEquals(5, asteroids(engine).size());
        engine.update(20f);
        assertEquals(6, asteroids(engine).size());
        assertUniqueValidSources(engine, 36L, 84L);
    }

    @Test
    void одинаковаяКонфигурацияДаётОдинаковуюПоследовательность() {
        AsteroidSpawnConfig config = AsteroidSpawnConfig.demoWorld();
        Engine first = new Engine();
        Engine second = new Engine();
        first.addSystem(new AsteroidSpawnSystem(config));
        second.addSystem(new AsteroidSpawnSystem(config));

        first.update(0f);
        second.update(0f);

        for (int i = 0; i < asteroids(first).size(); i++) {
            AsteroidComponent left = asteroids(first).get(i).getComponent(AsteroidComponent.class);
            AsteroidComponent right = asteroids(second).get(i).getComponent(AsteroidComponent.class);
            TransformComponent leftTransform = asteroids(first).get(i).getComponent(TransformComponent.class);
            TransformComponent rightTransform = asteroids(second).get(i).getComponent(TransformComponent.class);
            assertEquals(left.spawnPointId, right.spawnPointId);
            assertEquals(left.initialResource, right.initialResource);
            assertEquals(leftTransform.position, rightTransform.position);
        }
    }

    @Test
    void некорректноеВремяНеПродвигаетРасписание() {
        Engine engine = new Engine();
        engine.addSystem(new AsteroidSpawnSystem(AsteroidSpawnConfig.demoWorld()));

        engine.update(0f);
        engine.update(-1f);
        engine.update(Float.NaN);
        engine.update(Float.POSITIVE_INFINITY);

        assertEquals(4, asteroids(engine).size());
    }

    @Test
    void конфигурацияПроверяетТочкиСчётчикиИнтервалИДиапазон() {
        List<AsteroidSpawnPoint> points = List.of(
                new AsteroidSpawnPoint("A", 0f, 0f),
                new AsteroidSpawnPoint("B", 1f, 0f));

        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnConfig(
                Constants.ITEM_FOOD, points, 1, 1, 1f, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnConfig(
                Constants.ITEM_ORE, List.of(), 0, 0, 1f, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnConfig(
                Constants.ITEM_ORE, List.of(new AsteroidSpawnPoint("A", 0f, 0f),
                        new AsteroidSpawnPoint("A", 1f, 1f)), 1, 1, 1f, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnConfig(
                Constants.ITEM_ORE, points, 2, 1, 1f, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnConfig(
                Constants.ITEM_ORE, points, 1, 3, 1f, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnConfig(
                Constants.ITEM_ORE, points, 1, 1, 0f, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnConfig(
                Constants.ITEM_ORE, points, 1, 1, 1f, 0, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnConfig(
                Constants.ITEM_ORE, points, 1, 1, 1f, 3, 2, 1));
    }

    @Test
    void точкаСпавнаОтклоняетПустойIdИНеконечныеКоординаты() {
        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnPoint(" ", 0f, 0f));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnPoint("A", Float.NaN, 0f));
        assertThrows(IllegalArgumentException.class, () -> new AsteroidSpawnPoint("A", 0f, Float.POSITIVE_INFINITY));
        assertEquals("A", new AsteroidSpawnPoint(" A ", 0f, 0f).id());
    }

    private List<Entity> asteroids(Engine engine) {
        var entities = engine.getEntitiesFor(Family.all(AsteroidComponent.class, TransformComponent.class).get());
        java.util.ArrayList<Entity> copy = new java.util.ArrayList<>();
        for (Entity entity : entities) {
            copy.add(entity);
        }
        return copy;
    }

    private void assertUniqueValidSources(Engine engine, long minResource, long maxResource) {
        Set<String> pointIds = new HashSet<>();
        for (Entity entity : asteroids(engine)) {
            AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
            assertTrue(pointIds.add(asteroid.spawnPointId));
            assertEquals(Constants.ITEM_ORE, asteroid.resourceItem);
            assertTrue(asteroid.initialResource >= minResource);
            assertTrue(asteroid.initialResource <= maxResource);
            assertEquals(asteroid.initialResource, asteroid.remainingResource);
        }
    }
}
