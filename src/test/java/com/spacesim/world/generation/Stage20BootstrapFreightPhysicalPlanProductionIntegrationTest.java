package com.spacesim.world.generation;

import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan;
import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.PlanReport;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20ResolvedFreightAcceptance.AcceptanceReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20BootstrapFreightPhysicalPlanProductionIntegrationTest {
    @Test
    void acceptedResolvedProductionEvidenceFlowsIntoTheExactPhysicalPlan() {
        var resolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);
        AcceptanceReport acceptance = resolved.coordinatedFreightAcceptance().orElseThrow();

        PlanReport physical = Stage20BootstrapFreightPhysicalPlan.reconstruct(acceptance);

        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED,
                resolved.seedAcceptance().status());
        assertEquals(Stage20CommodityFreightFrontierCombiner.Status.ACCEPTED,
                acceptance.combination().status());
        assertEquals(acceptance.version(), physical.acceptanceVersion());
        assertEquals(resolved.rootSeed(), physical.rootSeed());
        assertEquals(acceptance.placementVersion(), physical.placementVersion());
        assertEquals(acceptance.supplyProfileVersion(), physical.supplyProfileVersion());
        assertEquals(acceptance.searchNodeBudgetPerCommodity(), physical.searchNodeBudgetPerCommodity());
        assertEquals(acceptance.remoteFreighterBudgetByFaction(), physical.remoteFreighterBudgetByFaction());
        assertEquals(acceptance.combination().remoteFreightersUsedByFaction(),
                physical.remoteFreightersByFaction());
        assertEquals(acceptance.commodityFrontiers().size(), physical.commodities().size());
        assertTrue(physical.commodities().stream().allMatch(selected ->
                acceptance.commodityFrontiers().stream()
                        .filter(frontier -> frontier.commodityId().equals(selected.commodityId()))
                        .filter(frontier -> frontier.version().equals(selected.frontierVersion()))
                        .flatMap(frontier -> frontier.options().stream())
                        .anyMatch(option -> option.optionId().equals(selected.optionId())
                                && option.starts().equals(selected.starts())
                                && option.producerUsage().equals(selected.producerUsage()))));
    }
}
