package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionExpansionCompetitionCoordinatorTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void coordinatorAdvancesPhysicalWinnerThroughOrdinaryExecutor() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState unclaimed = withoutController(
                DemoGalaxyFactory.createState(0x11D0L, content),
                DemoGalaxyFactory.INNER_SYSTEM_ID);
        FactionExpansionRuntime runtime = new FactionExpansionRuntime(
                WorldSimulation.restore(
                        unclaimed,
                        content,
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                        WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME),
                content);
        StrategicGrowthState.PlanId planId = runtime.planBestUnclaimed(TRADE_LEAGUE).orElseThrow().id();

        List<StrategicGrowthState.Plan> plans = List.of();
        for (int iteration = 0; iteration < 20_000; iteration++) {
            plans = FactionExpansionCompetitionCoordinator.advanceAll(runtime);
            StrategicGrowthState.Plan current = plans.stream()
                    .filter(plan -> plan.id().equals(planId))
                    .findFirst().orElseThrow();
            if (current.status().terminal()) {
                break;
            }
            runtime.advanceFrame(1f);
        }

        StrategicGrowthState.Plan finalPlan = plans.stream()
                .filter(plan -> plan.id().equals(planId))
                .findFirst().orElseThrow();
        assertEquals(StrategicGrowthState.Status.ESTABLISHED, finalPlan.status());
        assertEquals(TRADE_LEAGUE,
                runtime.world().controllingFaction(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow());
        assertTrue(runtime.world().findConstructionProject(finalPlan.anchorProjectId())
                .map(project -> project.status() == ConstructionProjectStatus.COMPLETED)
                .orElse(false));
    }

    private static WorldState withoutController(WorldState state, StarSystemId target) {
        List<FactionStrategicState> strategies = new ArrayList<>(state.factionStrategies().size());
        for (FactionStrategicState strategy : state.factionStrategies()) {
            List<StarSystemId> controlled = strategy.controlledSystems().stream()
                    .filter(systemId -> !systemId.equals(target))
                    .toList();
            strategies.add(new FactionStrategicState(
                    strategy.factionContentId(),
                    strategy.minimumMarketAccessRelation(),
                    strategy.relations(),
                    controlled,
                    strategy.stationTaxBasisPoints(),
                    strategy.foreignTerritoryTariffBasisPoints(),
                    strategy.stockPolicies(),
                    strategy.productionPolicies(),
                    strategy.strategicGoals()));
        }
        return new WorldState(
                state.schemaVersion(),
                state.topology(),
                state.systems(),
                state.factions(),
                strategies,
                state.nextConstructionProjectIdValue(),
                state.constructionProjects(),
                state.factionEconomicPressures(),
                state.nextFleetIdValue(),
                state.fleets(),
                state.fleetJumps());
    }
}
