package com.spacesim.world.generation;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FailureReason;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.Status;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator;
import com.spacesim.world.calibration.Stage20BootstrapFreightCapacityRequirementProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20CommodityFrontierCorpusDiagnosticsTest {
    @Test
    void fixedCorpusMeasuresFrontiersAndExactCombinationWithoutPassRateTarget() {
        Stage20CommodityFrontierCorpusDiagnostics.Report report =
                Stage20CommodityFrontierCorpusDiagnostics.evaluateCurrent();

        assertEquals(Stage20CommodityFrontierCorpusDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(Stage20CommodityWholePlacementFrontierGenerator.CURRENT_VERSION,
                report.frontierGeneratorVersion());
        assertEquals(Stage20CommodityFrontierCorpusDiagnostics.FRONTIER_SEARCH_NODE_BUDGET_PER_COMMODITY,
                report.frontierSearchNodeBudgetPerCommodity());
        assertEquals(Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent()
                        .requiredFreighterCountPerFactionStart(),
                report.perStartFreighterBudget());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds().size(), report.fixedSeedCount());
        assertEquals(report.fixedSeedCount(), report.seeds().size());
        assertEquals(report.acceptedPlacementSeedCount(),
                report.combinerAcceptedSeedCount()
                        + report.combinerInfeasibleSeedCount()
                        + report.combinerUnresolvedSeedCount());
        assertTrue(report.maxCommodityFrontierSearchNodesVisited()
                <= report.frontierSearchNodeBudgetPerCommodity());
        assertTrue(report.totalFrontierSearchNodesVisited()
                >= report.maxCommodityFrontierSearchNodesVisited());

        int essentialCommodityCount = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent()
                .inputs()
                .acceptance()
                .bootstrapRequirements()
                .essentialCommodities()
                .size();
        assertTrue(essentialCommodityCount > 0);

        for (Stage20CommodityFrontierCorpusDiagnostics.SeedEvidence seed : report.seeds()) {
            switch (seed.status()) {
                case PLACEMENT_REJECTED -> {
                    assertTrue(seed.commodities().isEmpty());
                    assertTrue(seed.combinerStatus().isEmpty());
                    assertTrue(seed.combinerFailureReason().isEmpty());
                    assertTrue(seed.combinedRemoteFreightersByFaction().isEmpty());
                    assertTrue(seed.selectedOptions().isEmpty());
                }
                case COMBINER_ACCEPTED -> {
                    assertEquals(essentialCommodityCount, seed.commodities().size());
                    assertEquals(Status.ACCEPTED, seed.combinerStatus().orElseThrow());
                    assertTrue(seed.combinerFailureReason().isEmpty());
                    assertFalse(seed.combinedRemoteFreightersByFaction().isEmpty());
                    assertEquals(essentialCommodityCount, seed.selectedOptions().size());
                }
                case COMBINER_INFEASIBLE -> {
                    assertEquals(essentialCommodityCount, seed.commodities().size());
                    assertEquals(Status.INFEASIBLE, seed.combinerStatus().orElseThrow());
                    assertTrue(seed.combinerFailureReason().isPresent());
                    assertTrue(seed.combinerFailureReason().orElseThrow() != FailureReason.FRONTIER_INCOMPLETE);
                    assertTrue(seed.combinedRemoteFreightersByFaction().isEmpty());
                    assertTrue(seed.selectedOptions().isEmpty());
                }
                case COMBINER_UNRESOLVED -> {
                    assertEquals(essentialCommodityCount, seed.commodities().size());
                    assertEquals(Status.UNRESOLVED_FRONTIER, seed.combinerStatus().orElseThrow());
                    assertEquals(FailureReason.FRONTIER_INCOMPLETE, seed.combinerFailureReason().orElseThrow());
                    assertTrue(seed.commodities().stream()
                            .anyMatch(value -> value.status() == FrontierStatus.UNRESOLVED_SEARCH_BUDGET));
                    assertTrue(seed.combinedRemoteFreightersByFaction().isEmpty());
                    assertTrue(seed.selectedOptions().isEmpty());
                }
            }
            for (Stage20CommodityFrontierCorpusDiagnostics.CommodityEvidence commodity : seed.commodities()) {
                assertTrue(commodity.searchNodesVisited() <= report.frontierSearchNodeBudgetPerCommodity());
                assertEquals(commodity.optionCount(), commodity.nondominatedShipVectors().size());
            }
        }

        var seed8 = report.seeds().stream()
                .filter(value -> value.rootSeed() == Stage20Seed8FreightSearchConvergenceDiagnostics.ROOT_SEED)
                .findFirst()
                .orElseThrow();
        assertEquals(8L, seed8.rootSeed());

        System.out.println("STAGE20E_COMMODITY_FRONTIER_CORPUS_DIAGNOSTICS_BEGIN");
        System.out.print(Stage20CommodityFrontierCorpusDiagnostics.toText(report));
        System.out.println("STAGE20E_COMMODITY_FRONTIER_CORPUS_DIAGNOSTICS_END");
    }
}
