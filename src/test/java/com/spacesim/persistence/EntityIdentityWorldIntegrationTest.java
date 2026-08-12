package com.spacesim.persistence;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.spacesim.DemoWorldFactory;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.systems.AsteroidSpawnSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityIdentityWorldIntegrationTest {
    @Test
    void bootstrapИДинамическиеСущностиИспользуютОднуПоследовательностьИRegistry() {
        EntityIdAllocator allocator = new EntityIdAllocator();
        EntityRegistry registry = new EntityRegistry();
        Engine engine = new Engine();
        engine.addEntityListener(Family.all(EntityIdComponent.class).get(), registry);

        List<Entity> bootstrap = DemoWorldFactory.createEntities(allocator);
        for (Entity entity : bootstrap) {
            engine.addEntity(entity);
        }

        assertEquals(13, bootstrap.size());
        assertEquals(13, registry.size());
        assertEquals(14L, allocator.getNextValue());
        for (int index = 0; index < bootstrap.size(); index++) {
            EntityId expected = new EntityId(index + 1L);
            assertEquals(expected, bootstrap.get(index).getComponent(EntityIdComponent.class).id);
            assertEquals(bootstrap.get(index), registry.require(expected));
        }

        AsteroidSpawnSystem spawner = new AsteroidSpawnSystem(
                AsteroidSpawnConfig.demoWorld(),
                new Random(12345L),
                new EconomicLedger(),
                allocator);
        engine.addSystem(spawner);
        engine.update(0f);

        List<Entity> asteroids = new ArrayList<>();
        for (Entity entity : engine.getEntities()) {
            if (entity.getComponent(AsteroidComponent.class) != null) {
                asteroids.add(entity);
            }
        }

        assertFalse(asteroids.isEmpty());
        assertEquals(13 + asteroids.size(), registry.size());
        assertEquals(14L + asteroids.size(), allocator.getNextValue());
        for (Entity asteroid : asteroids) {
            EntityIdComponent id = asteroid.getComponent(EntityIdComponent.class);
            assertNotNull(id);
            assertTrue(id.id.value() >= 14L);
            assertEquals(asteroid, registry.require(id.id));
            assertEquals(IdentityComponent.Kind.ASTEROID,
                    asteroid.getComponent(IdentityComponent.class).kind);
        }

        Entity removed = asteroids.get(0);
        EntityId removedId = removed.getComponent(EntityIdComponent.class).id;
        engine.removeEntity(removed);

        assertFalse(registry.contains(removedId));
        assertEquals(12 + asteroids.size(), registry.size());
    }

    @Test
    void фабрикаСобственнойПоследовательностьюВсегдаДаётОдинаковыеBootstrapId() {
        List<Entity> first = DemoWorldFactory.createEntities();
        List<Entity> second = DemoWorldFactory.createEntities();

        for (int index = 0; index < first.size(); index++) {
            assertEquals(
                    first.get(index).getComponent(EntityIdComponent.class).id,
                    second.get(index).getComponent(EntityIdComponent.class).id);
        }
        assertThrows(NullPointerException.class,
                () -> DemoWorldFactory.createEntities(null));
    }
}
