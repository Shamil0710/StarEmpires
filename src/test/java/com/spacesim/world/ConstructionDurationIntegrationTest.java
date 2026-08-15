package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionDurationIntegrationTest {
    private static final String OWNER = "faction.miners";
    private static final String TARGET = "station.foundry";

    @Test
    void newProjectUsesFormulaAndRestoredProjectKeepsPersistedTickContract() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(16_001L);
        ContentCatalog.StationArchetypeDefinition station = content.findStationArchetype(TARGET);
        ConstructionDurationPolicy.Estimate estimate = ConstructionDurationPolicy.estimate(content, station);
        float fixedStep = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getClock().getFixedStepSeconds();
        long expectedTicks = Math.max(1L, (long) Math.ceil(estimate.totalSeconds() / fixedStep));

        ConstructionProjectId projectId = ConstructionProjectTestFixtures.createAuthorizedProject(world,
                OWNER, TARGET, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 900f, 650f);
        ConstructionProjectState project = world.findConstructionProject(projectId).orElseThrow();

        assertEquals(expectedTicks, project.buildDurationTicks());
        assertTrue(project.buildDurationTicks()
                        > Math.ceil(station.construction().buildSeconds() / fixedStep),
                "physical material bill must add time beyond authored base setup");

        WorldState persisted = WorldStateCodec.decode(WorldStateCodec.encode(world.snapshot()));
        WorldSimulation restored = WorldSimulation.restore(
                persisted, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        assertEquals(project.buildDurationTicks(),
                restored.findConstructionProject(projectId).orElseThrow().buildDurationTicks(),
                "ongoing projects keep their persisted duration rather than being re-priced on load");
    }
}
