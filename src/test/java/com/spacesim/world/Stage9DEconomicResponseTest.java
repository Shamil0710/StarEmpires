package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage9DEconomicResponseTest {
    private static final long ROOT_SEED = 0x9D1A57L;
    private static final String MINERS = "faction.miners";

    @Test
    void sustainedFoundryLossСоздаётИФинансируетPhysicalReplacementProject() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        destroyFoundry(world);
        FactionEconomicPressureTracker tracker = observeThreeTimes(world, content);
        long treasuryBefore = world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits();

        FactionInvestmentPlanner.InvestmentDecision decision =
                FactionInvestmentPlanner.evaluateFaction(world, content, tracker, MINERS).orElseThrow();

        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, decision.systemId());
        assertEquals("item.steel", decision.itemContentId());
        assertEquals("station.foundry", decision.stationArchetypeContentId());
        ConstructionProjectState project = world.findConstructionProject(decision.projectId()).orElseThrow();
        assertEquals(decision.fundedMilliCredits(), project.projectWalletMilliCredits());
        assertEquals(treasuryBefore - decision.fundedMilliCredits(),
                world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits());
        assertEquals(1, world.getConstructionProjects().size());
        assertTrue(FactionInvestmentPlanner.evaluateFaction(world, content, tracker, MINERS).isEmpty());
    }

    @Test
    void inputStarvedFoundryНеСоздаётЛишнийProducerProject() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        Entity foundry = findFoundry(world);
        InventoryComponent inventory = foundry.getComponent(InventoryComponent.class);
        inventory.stock[content.findItem("item.ore").runtimeId()] = 0;
        inventory.stock[content.findItem("item.energy").runtimeId()] = 0;
        inventory.stock[content.findItem("item.steel").runtimeId()] = 0;
        FactionEconomicPressureTracker tracker = observeThreeTimes(world, content);

        EconomicBottleneck steel = EconomicBottleneckAnalyzer.analyze(world, content)
                .find(DemoGalaxyFactory.INNER_SYSTEM_ID, "item.steel").orElseThrow();
        assertEquals(EconomicBottleneckType.LOGISTICS_SHORTAGE, steel.type());
        assertTrue(FactionInvestmentPlanner.evaluateFaction(world, content, tracker, MINERS).isEmpty());
        assertEquals(List.of(), world.getConstructionProjects());
    }

    private static FactionEconomicPressureTracker observeThreeTimes(WorldSimulation world, ContentCatalog content) {
        FactionEconomicPressureTracker tracker = new FactionEconomicPressureTracker(List.of());
        FactionStrategicState strategy = world.findFactionStrategicState(MINERS).orElseThrow();
        for (int observation = 0; observation < 3; observation++) {
            world.advanceFrame(0.1f);
            EconomicBottleneckReport report = EconomicBottleneckAnalyzer.analyze(world, content);
            long tick = world.findSession(world.getActiveSystemId()).orElseThrow().getClock().getTick();
            tracker.observe(List.of(strategy), report, tick);
        }
        return tracker;
    }

    private static void destroyFoundry(WorldSimulation world) {
        Entity foundry = findFoundry(world);
        world.destroyEntity(
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                foundry.getComponent(EntityIdComponent.class).id,
                DestructionPolicy.destroyAll());
    }

    private static Entity findFoundry(WorldSimulation world) {
        for (Entity entity : world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow()
                .getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && "station.foundry".equals(archetype.contentId)) {
                return entity;
            }
        }
        throw new AssertionError("Demo foundry not found");
    }
}
