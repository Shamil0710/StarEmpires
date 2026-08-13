package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage9DBudgetGateTest {
    private static final long ROOT_SEED = 0x9DAFF0L;
    private static final String MINERS = "faction.miners";

    @Test
    void emptyTreasuryНеСоздаётUnfundedProducerProject() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(ROOT_SEED, content);
        List<FactionEconomicState> factions = new ArrayList<>();
        for (FactionEconomicState faction : base.factions()) {
            if (MINERS.equals(faction.factionContentId())) {
                factions.add(new FactionEconomicState(
                        faction.factionContentId(),
                        0L,
                        faction.stationLiquidityReserveMilliCredits(),
                        faction.maxLiquiditySupportPerDecisionMilliCredits()));
            } else {
                factions.add(faction);
            }
        }
        WorldState poorState = new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                List.copyOf(factions),
                base.factionStrategies(),
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures());
        WorldSimulation world = WorldSimulation.restore(poorState, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        destroyFoundry(world);

        for (int observation = 0; observation < 3; observation++) {
            world.advanceFrame(0.1f);
            world.applyEconomicInvestmentDecision();
        }

        assertEquals(0L, world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits());
        assertEquals(0, countOwnedFoundryProjects(world));
    }

    private static int countOwnedFoundryProjects(WorldSimulation world) {
        int count = 0;
        for (ConstructionProjectState project : world.getConstructionProjects()) {
            if (MINERS.equals(project.ownerFactionContentId())
                    && DemoGalaxyFactory.INNER_SYSTEM_ID.equals(project.systemId())
                    && "station.foundry".equals(project.stationArchetypeContentId())
                    && project.status() != ConstructionProjectStatus.COMPLETED
                    && project.status() != ConstructionProjectStatus.CANCELLED
                    && project.status() != ConstructionProjectStatus.FAILED) {
                count++;
            }
        }
        return count;
    }

    private static void destroyFoundry(WorldSimulation world) {
        for (Entity entity : world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow()
                .getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && "station.foundry".equals(archetype.contentId)) {
                world.destroyEntity(
                        DemoGalaxyFactory.INNER_SYSTEM_ID,
                        entity.getComponent(EntityIdComponent.class).id,
                        DestructionPolicy.destroyAll());
                return;
            }
        }
        throw new AssertionError("Demo foundry not found");
    }
}
