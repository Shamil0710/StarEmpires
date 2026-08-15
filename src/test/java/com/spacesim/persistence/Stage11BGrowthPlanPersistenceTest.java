package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.ExpansionOpportunity;
import com.spacesim.world.FactionExpansionOpportunityAnalyzer;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.StrategicGrowthPlanService;
import com.spacesim.world.StrategicGrowthState;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Stage11BGrowthPlanPersistenceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void growthPlanRoundTripsAndSurvivesRuntimeContinuation() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(0x11B3L);
        FactionStrategicState strategy = world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow();
        ExpansionOpportunity opportunity = FactionExpansionOpportunityAnalyzer
                .analyze(world, content, TRADE_LEAGUE)
                .get(0);
        FactionStrategicState planned = StrategicGrowthPlanService.createPlan(
                strategy, opportunity, content, 77L);
        WorldState state = replaceStrategy(world.snapshot(), planned);

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(state));
        FactionStrategicState decodedStrategy = decoded.factionStrategies().stream()
                .filter(value -> TRADE_LEAGUE.equals(value.factionContentId()))
                .findFirst().orElseThrow();
        List<StrategicGrowthState.Plan> decodedPlans = StrategicGrowthPlanService.plans(decodedStrategy);

        assertEquals(1, decodedPlans.size());
        assertEquals(StrategicGrowthPlanService.plans(planned).get(0), decodedPlans.get(0));

        WorldSimulation restored = WorldSimulation.restore(decoded, DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        FactionStrategicState continued = restored.snapshot().factionStrategies().stream()
                .filter(value -> TRADE_LEAGUE.equals(value.factionContentId()))
                .findFirst().orElseThrow();
        assertEquals(decodedPlans, StrategicGrowthPlanService.plans(continued));
    }

    @Test
    void legacyFileFormatV1MigratesWithNoPhysicalGrowthPlans() {
        WorldState base = DemoGalaxyFactory.createState(0x11B4L, ContentCatalogLoader.loadDefault());
        byte[] v1 = LegacyWorldFileTestSupport.encodeV1(base);

        WorldState migrated = WorldStateCodec.decode(v1);

        assertEquals(base.factionStrategies(), migrated.factionStrategies());
        assertFalse(migrated.factionStrategies().stream()
                .flatMap(value -> value.strategicGoals().stream())
                .anyMatch(goal -> goal.growthPlan() != null));
    }

    private static WorldState replaceStrategy(WorldState state, FactionStrategicState replacement) {
        List<FactionStrategicState> strategies = new ArrayList<>(state.factionStrategies().size());
        for (FactionStrategicState strategy : state.factionStrategies()) {
            strategies.add(strategy.factionContentId().equals(replacement.factionContentId())
                    ? replacement : strategy);
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
