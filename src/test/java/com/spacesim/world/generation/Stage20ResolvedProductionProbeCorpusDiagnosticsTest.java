package com.spacesim.world.generation;

import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20ResolvedProductionProbeCorpusDiagnosticsTest {
    @Test
    void fixedCorpusRemainsStructurallyCompleteWithoutPassRateTarget() {
        Stage20ResolvedProductionProbeCorpusDiagnostics.Report report =
                Stage20ResolvedProductionProbeCorpusDiagnostics.runFixed();

        assertEquals(Stage20ResolvedProductionProbeCorpusDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(Stage20ResolvedGeneratedWorldProductionProbe.CURRENT_VERSION, report.resolvedProbeVersion());
        assertEquals(Stage20RepresentativeGeneratedWorldProbeProfileV3.CURRENT_VERSION,
                report.representativeProfileVersion());
        assertEquals(Stage20GeneratedWorldSeedAcceptance.RESOLVED_FREIGHT_VERSION,
                report.wholeSeedAcceptanceVersion());
        assertEquals(16, report.fixedSeedCount());
        assertEquals(
                report.fixedSeedCount(),
                report.acceptedSeedCount() + report.rejectedSeedCount() + report.unresolvedSeedCount());
        assertEquals(report.fixedSeedCount(), report.seeds().size());
        assertTrue(report.totalFreightSearchNodesVisited() >= 0);

        report.seeds().forEach(seed -> {
            switch (seed.status()) {
                case ACCEPTED -> {
                    assertTrue(seed.failureReasons().isEmpty());
                    assertTrue(seed.freightStatus().isPresent(),
                            "accepted resolved seed must have accepted placement/freight evidence");
                    assertEquals(
                            com.spacesim.world.Stage20CommodityFreightFrontierCombiner.Status.ACCEPTED,
                            seed.freightStatus().orElseThrow());
                }
                case REJECTED -> assertFalse(seed.failureReasons().isEmpty(),
                        "rejected resolved seed must expose a causal failure");
                case UNRESOLVED -> {
                    assertFalse(seed.failureReasons().isEmpty(),
                            "unresolved resolved seed must expose an authority blocker");
                    assertTrue(seed.failureReasons().stream().anyMatch(reason ->
                                    reason == Stage20GeneratedWorldSeedAcceptance.FailureReason
                                            .COORDINATED_FREIGHT_AUTHORITY_UNRESOLVED
                                    || reason == Stage20GeneratedWorldSeedAcceptance.FailureReason
                                            .FACTION_START_AUTHORITY_UNRESOLVED),
                            "unresolved whole seed must retain an explicit unresolved-authority reason");
                }
            }
        });

        System.out.println("STAGE20E_RESOLVED_PRODUCTION_PROBE_CORPUS_BEGIN");
        System.out.print(Stage20ResolvedProductionProbeCorpusDiagnostics.toText(report));
        System.out.println("STAGE20E_RESOLVED_PRODUCTION_PROBE_CORPUS_END");
    }
}
