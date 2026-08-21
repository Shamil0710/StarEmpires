package com.spacesim.world.generation;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.generation.Stage20CommodityFrontierCorpusDiagnostics.SeedStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20CommodityFrontierResolvedCorpusDiagnosticsTest {
    @Test
    void measuresFixedCorpusWithoutAcceptedSeedTarget() {
        var report = Stage20CommodityFrontierResolvedCorpusDiagnostics.evaluateCurrent();

        assertEquals(Stage20CommodityFrontierResolvedCorpusDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(Stage20CommodityWholePlacementFrontierResolverVersion.value(), report.frontierGeneratorVersion());
        assertEquals(report.fixedSeedCount(), report.seeds().size());
        assertEquals(
                report.acceptedPlacementSeedCount(),
                report.combinerAcceptedSeedCount()
                        + report.combinerInfeasibleSeedCount()
                        + report.combinerUnresolvedSeedCount());
        assertTrue(report.maxCommodityFrontierSearchNodesVisited()
                <= report.frontierSearchNodeBudgetPerCommodity());

        for (var seed : report.seeds()) {
            if (seed.status() == SeedStatus.PLACEMENT_REJECTED) {
                assertTrue(seed.commodities().isEmpty());
                assertTrue(seed.combinerStatus().isEmpty());
                continue;
            }
            assertFalse(seed.commodities().isEmpty());
            if (seed.status() == SeedStatus.COMBINER_UNRESOLVED) {
                assertTrue(seed.commodities().stream()
                        .anyMatch(value -> value.status() == FrontierStatus.UNRESOLVED_SEARCH_BUDGET));
            }
        }

        System.out.println("STAGE20E_COMMODITY_FRONTIER_RESOLVED_CORPUS_DIAGNOSTICS_BEGIN");
        System.out.print(Stage20CommodityFrontierResolvedCorpusDiagnostics.toText(report));
        System.out.println("STAGE20E_COMMODITY_FRONTIER_RESOLVED_CORPUS_DIAGNOSTICS_END");
    }

    /** Avoids repeating the production constant as an unreviewed test literal. */
    private static final class Stage20CommodityWholePlacementFrontierResolverVersion {
        private Stage20CommodityWholePlacementFrontierResolverVersion() {
        }

        private static String value() {
            return com.spacesim.world.Stage20CommodityWholePlacementFrontierResolver.CURRENT_VERSION;
        }
    }
}
