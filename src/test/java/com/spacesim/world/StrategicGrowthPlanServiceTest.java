package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicGrowthPlanServiceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void createsCanonicalPersistentPlanFromExpansionOpportunity() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(0x11B0L);
        FactionStrategicState strategy = world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow();
        ExpansionOpportunity opportunity = FactionExpansionOpportunityAnalyzer
                .analyze(world, content, TRADE_LEAGUE)
                .get(0);

        FactionStrategicState planned = StrategicGrowthPlanService.createPlan(
                strategy, opportunity, content, 42L);
        List<StrategicGrowthState.Plan> plans = StrategicGrowthPlanService.plans(planned);

        assertEquals(1, plans.size());
        StrategicGrowthState.Plan plan = plans.get(0);
        assertEquals(new StrategicGrowthState.PlanId(TRADE_LEAGUE, 1L), plan.id());
        assertEquals(opportunity.sourceSystemId(), plan.sourceSystemId());
        assertEquals(opportunity.targetSystemId(), plan.targetSystemId());
        assertEquals(opportunity.anchorStationArchetypeContentId(), plan.anchorArchetypeContentId());
        assertEquals(opportunity.constructionFundingMilliCredits(), plan.approvedBudgetMilliCredits());
        assertEquals(StrategicGrowthState.Status.PLANNED, plan.status());
        assertFalse(plan.initialStockTargets().isEmpty());
        assertTrue(planned.strategicGoals().stream()
                .filter(FactionStrategicGoalState::hasGrowthPlan)
                .allMatch(goal -> goal.type() == FactionStrategicGoalState.GoalType.EXPANSION));
    }

    @Test
    void rejectsSecondActivePlanForSameTarget() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(0x11B1L);
        FactionStrategicState strategy = world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow();
        ExpansionOpportunity opportunity = FactionExpansionOpportunityAnalyzer
                .analyze(world, content, TRADE_LEAGUE)
                .get(0);
        FactionStrategicState planned = StrategicGrowthPlanService.createPlan(
                strategy, opportunity, content, 10L);

        assertThrows(IllegalStateException.class,
                () -> StrategicGrowthPlanService.createPlan(planned, opportunity, content, 11L));
    }

    @Test
    void lifecycleTransitionsRemainValidatedAndReplaceable() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(0x11B2L);
        FactionStrategicState strategy = world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow();
        ExpansionOpportunity opportunity = FactionExpansionOpportunityAnalyzer
                .analyze(world, content, TRADE_LEAGUE)
                .get(0);
        FactionStrategicState planned = StrategicGrowthPlanService.createPlan(
                strategy, opportunity, content, 10L);
        StrategicGrowthState.Plan original = StrategicGrowthPlanService.plans(planned).get(0);

        StrategicGrowthState.Plan approved = StrategicGrowthPlanService.transition(
                original, StrategicGrowthState.Status.APPROVED, null, 20L);
        StrategicGrowthState.Plan executing = StrategicGrowthPlanService.transition(
                approved, StrategicGrowthState.Status.EXECUTING, new ConstructionProjectId(7L), 30L);
        FactionStrategicState updated = StrategicGrowthPlanService.replacePlan(planned, executing);

        assertEquals(StrategicGrowthState.Status.EXECUTING,
                StrategicGrowthPlanService.findPlan(updated, original.id()).orElseThrow().status());
        assertEquals(new ConstructionProjectId(7L),
                StrategicGrowthPlanService.findPlan(updated, original.id()).orElseThrow().anchorProjectId());
        assertThrows(IllegalStateException.class, () -> StrategicGrowthPlanService.transition(
                executing, StrategicGrowthState.Status.APPROVED, null, 31L));
    }
}
