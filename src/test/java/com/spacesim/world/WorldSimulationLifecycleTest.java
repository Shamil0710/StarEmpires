package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.persistence.EntityId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSimulationLifecycleTest {
    private static final long ROOT_SEED = 0x9A_600DL;

    @Test
    void activeИRemoteSystemsПоддерживаютCreateSaveLoadRemove() {
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);

        EntityId activeId = world.createEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                probe("Active lifecycle probe", 10f, 20f));
        EntityId remoteId = world.createEntity(
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                probe("Remote lifecycle probe", 30f, 40f));

        assertNotNull(world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().find(activeId));
        assertNotNull(world.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().find(remoteId));

        WorldState saved = world.snapshot();
        WorldSimulation restored = WorldSimulation.restore(saved, DemoGalaxyFactory.ACTIVE_SYSTEM_ID);

        assertNotNull(restored.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().find(activeId));
        assertNotNull(restored.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().find(remoteId));

        assertTrue(restored.removeEntity(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, activeId));
        assertTrue(restored.removeEntity(DemoGalaxyFactory.FRONTIER_SYSTEM_ID, remoteId));
        assertFalse(restored.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().contains(activeId));
        assertFalse(restored.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().contains(remoteId));

        WorldSimulation reloaded = WorldSimulation.restore(
                restored.snapshot(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        assertFalse(reloaded.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().contains(activeId));
        assertFalse(reloaded.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().contains(remoteId));
    }

    private static Entity probe(String name, float x, float y) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(transform);
    }
}
