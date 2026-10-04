package com.spacesim.world.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.junit.jupiter.api.Tag("slow")
class Stage20ResolvedFreightAcceptanceCorpusDiagnosticsTest {
    @Test
    void printsFixedCorpusEvidenceWithoutApplyingAPassRateTarget() {
        Stage20ResolvedFreightAcceptanceCorpusDiagnostics.Report report =
                Stage20ResolvedFreightAcceptanceCorpusDiagnostics.evaluateCurrent(Stage20V2CorpusFixture::probe);

        System.out.println("STAGE20E_RESOLVED_FREIGHT_ACCEPTANCE_CORPUS_BEGIN");
        System.out.print(Stage20ResolvedFreightAcceptanceCorpusDiagnostics.toText(report));
        System.out.println("STAGE20E_RESOLVED_FREIGHT_ACCEPTANCE_CORPUS_END");

        assertEquals(Stage20RepresentativeSeedCorpus.seeds().size(), report.fixedSeedCount());
        assertEquals(
                report.acceptedPlacementSeedCount(),
                report.freightAcceptedSeedCount()
                        + report.freightInfeasibleSeedCount()
                        + report.freightUnresolvedSeedCount());
        assertTrue(report.totalSearchNodesVisited() >= 0);
    }
}
