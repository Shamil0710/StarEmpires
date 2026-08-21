package com.spacesim.world.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentativeSupplyBottleneckDiagnosticsTest {
    private static final String LOG_BEGIN = "STAGE20E_SUPPLY_BOTTLENECK_DIAGNOSTICS_BEGIN";
    private static final String LOG_END = "STAGE20E_SUPPLY_BOTTLENECK_DIAGNOSTICS_END";

    @Test
    void fixedCorpusReportsPhysicalSupplyLayersWithoutChangingAcceptance() {
        var report = Stage20RepresentativeSupplyBottleneckDiagnostics.evaluateCurrent();

        assertEquals(Stage20RepresentativeSupplyBottleneckDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(Stage20RepresentativeSeedCorpus.CURRENT_VERSION, report.corpusVersion());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds().size(), report.seeds().size());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds(),
                report.seeds().stream().map(value -> value.rootSeed()).toList());

        for (var seed : report.seeds()) {
            assertFalse(seed.commodities().isEmpty());
            assertEquals(seed.initialExtractionSiteCount(),
                    seed.resolvedLogisticsSiteCount()
                            + seed.noCompatibleArchetypeSiteCount()
                            + seed.ambiguousArchetypeSiteCount());
            assertTrue(seed.unresolvedSupplySiteCount() <= seed.initialExtractionSiteCount());
            for (var commodity : seed.commodities()) {
                assertTrue(commodity.requiredKgPerSecond() > 0d);
                assertTrue(commodity.totalResolvedSupplyKgPerSecond() >= 0d);
                assertTrue(commodity.maximumProducerSystemSupplyKgPerSecond() >= 0d);
                assertTrue(commodity.initialSiteCount() <= commodity.occurrenceCount());
            }
        }

        String text = Stage20RepresentativeSupplyBottleneckDiagnostics.toText(report);
        assertTrue(text.contains("commodity=commodity.feedstock.water_ice"));
        assertTrue(text.contains("commodity=commodity.feedstock.metallic_ore"));
        System.out.println(LOG_BEGIN);
        System.out.print(text);
        System.out.println(LOG_END);
    }
}
