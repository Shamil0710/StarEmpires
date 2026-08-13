package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicBottleneckAnalyzerTest {
    private static final long ROOT_SEED = 0x9D2026L;

    @Test
    void foundryLossКлассифицируетсяКакProductionCapacityShortage() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        Entity foundry = findArchetype(world, DemoGalaxyFactory.INNER_SYSTEM_ID, "station.foundry");
        world.destroyEntity(
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                foundry.getComponent(EntityIdComponent.class).id,
                DestructionPolicy.destroyAll());

        EconomicBottleneck steel = EconomicBottleneckAnalyzer.analyze(world, content)
                .find(DemoGalaxyFactory.INNER_SYSTEM_ID, "item.steel")
                .orElseThrow();

        assertEquals(EconomicBottleneckType.PRODUCTION_CAPACITY_SHORTAGE, steel.type());
        assertEquals(0, steel.producerCount());
        assertTrue(steel.unmetDemandUnits() > 0L);
        assertTrue(steel.structuralPricePressureBasisPoints() >= 10_000);
    }

    @Test
    void missingFoundryInputsКлассифицируютсяКакLogisticsShortage() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        Entity foundry = findArchetype(world, DemoGalaxyFactory.INNER_SYSTEM_ID, "station.foundry");
        InventoryComponent inventory = foundry.getComponent(InventoryComponent.class);
        inventory.stock[content.findItem("item.ore").runtimeId()] = 0;
        inventory.stock[content.findItem("item.energy").runtimeId()] = 0;
        inventory.stock[content.findItem("item.steel").runtimeId()] = 0;

        EconomicBottleneck steel = EconomicBottleneckAnalyzer.analyze(world, content)
                .find(DemoGalaxyFactory.INNER_SYSTEM_ID, "item.steel")
                .orElseThrow();

        assertEquals(EconomicBottleneckType.LOGISTICS_SHORTAGE, steel.type());
        assertEquals(1, steel.producerCount());
        assertEquals(1, steel.inputBlockedProducerCount());
        assertEquals(0, steel.readyProducerCount());
    }

    @Test
    void одинаковоеСостояниеДаётПолностьюОдинаковыйReport() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation first = DemoGalaxyFactory.create(ROOT_SEED);
        WorldSimulation second = DemoGalaxyFactory.create(ROOT_SEED);

        assertEquals(
                EconomicBottleneckAnalyzer.analyze(first, content),
                EconomicBottleneckAnalyzer.analyze(second, content));
    }

    private static Entity findArchetype(
            WorldSimulation world, StarSystemId systemId, String archetypeId) {
        SimulationSession session = world.findSession(systemId).orElseThrow();
        for (Entity entity : session.getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && archetypeId.equals(archetype.contentId)) {
                return entity;
            }
        }
        throw new AssertionError("Archetype not found: " + archetypeId);
    }
}
