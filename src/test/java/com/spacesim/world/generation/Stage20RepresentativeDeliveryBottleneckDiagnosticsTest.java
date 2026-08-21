package com.spacesim.world.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentativeDeliveryBottleneckDiagnosticsTest {
    private static final String LOG_BEGIN = "STAGE20E_DELIVERY_BOTTLENECK_DIAGNOSTICS_BEGIN";
    private static final String LOG_END = "STAGE20E_DELIVERY_BOTTLENECK_DIAGNOSTICS_END";

    @Test
    void fixedCorpusSeparatesDeliveryLayersAndProvesProductionEvaluationParity() {
        var report = Stage20RepresentativeDeliveryBottleneckDiagnostics.evaluateCurrent();

        assertEquals(Stage20RepresentativeDeliveryBottleneckDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(Stage20RepresentativeSeedCorpus.CURRENT_VERSION, report.corpusVersion());
        assertEquals(Stage20RepresentativeGeneratedWorldProbeProfile.ACTIVE_FREIGHTER_COUNT,
                report.activeFreighterCount());
        assertTrue(report.candidateCount() > 0);
        assertEquals(report.evidence().size(),
                report.bottleneckCounts().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(report.evidence().size(),
                report.bottleneckCountsByCommodity().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(report.candidateCount() * 2, report.evidence().size());

        for (var row : report.evidence()) {
            assertTrue(row.requiredKgPerSecond() > 0d);
            assertTrue(row.maxSupplierRouteTimeS() > 0d);
            assertTrue(row.globalResolvedSupplyKgPerSecond() >= row.physicallyRoutableProducerSupplyKgPerSecond());
            assertTrue(row.physicallyRoutableProducerSupplyKgPerSecond()
                    >= row.timeAdmittedProducerSupplyKgPerSecond());
            assertTrue(row.aggregateDeliveredUpperBoundKgPerSecond() >= 0d);
        }

        String text = Stage20RepresentativeDeliveryBottleneckDiagnostics.toText(report);
        assertTrue(text.contains("candidateCount="));
        assertTrue(text.contains("commodity.feedstock.water_ice"));
        assertTrue(text.contains("commodity.feedstock.metallic_ore"));
        System.out.println(LOG_BEGIN);
        System.out.print(text);
        System.out.println(LOG_END);
    }
}
