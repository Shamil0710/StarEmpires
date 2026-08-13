package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.trade.MarketDirectory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestructionEconomicShockTest {
    private static final long ROOT_SEED = 0x9C5A0CL;

    @Test
    void destructionOfRealFoundryНемедленноУдаляетProductionMarketCapacity() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        LocatedEntity foundry = findArchetype(world, "station.foundry");
        SimulationSession session = foundry.session();
        Entity target = foundry.entity();
        EntityId targetId = target.getComponent(EntityIdComponent.class).id;
        assertNotNull(target.getComponent(ProductionComponent.class));
        assertNotNull(target.getComponent(MarketComponent.class));

        int foundriesBefore = countArchetype(world, "station.foundry");
        MarketDirectory beforeDirectory = new MarketDirectory(content);
        beforeDirectory.rebuild(session.getEngine().getEntities());
        assertNotNull(beforeDirectory.find(targetId));

        DestructionResult result = world.destroyEntity(
                foundry.systemId(), targetId, DestructionPolicy.destroyAll());

        assertTrue(result.removedProduction());
        assertTrue(result.removedMarket());
        assertFalse(session.getEntityRegistry().contains(targetId));
        assertEquals(foundriesBefore - 1, countArchetype(world, "station.foundry"));

        MarketDirectory afterDirectory = new MarketDirectory(content);
        afterDirectory.rebuild(session.getEngine().getEntities());
        assertEquals(null, afterDirectory.find(targetId));
        assertTrue(session.getEventManager().snapshotState().pendingNews().stream()
                .anyMatch(article -> article.content() != null && article.content().contains("Уничтожен объект")));

        WorldSimulation restored = WorldSimulation.restore(world.snapshot(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        assertFalse(restored.findSession(foundry.systemId()).orElseThrow().getEntityRegistry().contains(targetId));
    }

    @Test
    void одинаковаяDestructionПоследовательностьДаётОдинаковыйWorldSnapshot() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation first = DemoGalaxyFactory.create(ROOT_SEED);
        WorldSimulation second = DemoGalaxyFactory.create(ROOT_SEED);
        LocatedEntity firstFoundry = findArchetype(first, "station.foundry");
        LocatedEntity secondFoundry = findArchetype(second, "station.foundry");
        EntityId firstId = firstFoundry.entity().getComponent(EntityIdComponent.class).id;
        EntityId secondId = secondFoundry.entity().getComponent(EntityIdComponent.class).id;
        assertEquals(firstFoundry.systemId(), secondFoundry.systemId());
        assertEquals(firstId, secondId);

        first.destroyEntity(firstFoundry.systemId(), firstId, DestructionPolicy.salvageResources());
        second.destroyEntity(secondFoundry.systemId(), secondId, DestructionPolicy.salvageResources());

        assertEquals(first.snapshot(), second.snapshot());
        assertEquals(content.getFingerprint(), ContentCatalogLoader.loadDefault().getFingerprint());
    }

    private static LocatedEntity findArchetype(WorldSimulation world, String archetypeId) {
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
                ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
                if (archetype != null && archetypeId.equals(archetype.contentId)) {
                    return new LocatedEntity(system.id(), session, entity);
                }
            }
        }
        throw new AssertionError("Archetype not found in demo world: " + archetypeId);
    }

    private static int countArchetype(WorldSimulation world, String archetypeId) {
        int count = 0;
        for (StarSystemNode system : world.getTopology().systems()) {
            SimulationSession session = world.findSession(system.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
                ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
                if (archetype != null && archetypeId.equals(archetype.contentId)) {
                    count++;
                }
            }
        }
        return count;
    }

    private record LocatedEntity(StarSystemId systemId, SimulationSession session, Entity entity) {
    }
}
