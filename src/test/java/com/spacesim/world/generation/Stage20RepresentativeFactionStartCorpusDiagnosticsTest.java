package com.spacesim.world.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentativeFactionStartCorpusDiagnosticsTest {
    private static final String LOG_BEGIN = "STAGE20E_FACTION_START_CAUSAL_DIAGNOSTICS_BEGIN";
    private static final String LOG_END = "STAGE20E_FACTION_START_CAUSAL_DIAGNOSTICS_END";

    @Test
    void fixedCorpusExposesCandidateHardGateViolationHistogramWithoutChangingAcceptance() {
        var report = Stage20RepresentativeFactionStartCorpusDiagnostics.evaluateCurrent();

        assertEquals(Stage20RepresentativeFactionStartCorpusDiagnostics.CURRENT_VERSION, report.version());
        assertEquals(Stage20RepresentativeSeedCorpus.CURRENT_VERSION, report.corpusVersion());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds().size(), report.seeds().size());
        assertTrue(report.totalCandidateCount() > 0);
        assertEquals(
                report.totalCandidateCount(),
                report.acceptedCandidateCount()
                        + report.rejectedCandidateCount()
                        + report.unresolvedAuthorityCandidateCount());
        assertTrue(report.violationCountsByType().keySet().stream().allMatch(value -> !value.isBlank()));

        System.out.println(LOG_BEGIN);
        System.out.print(Stage20RepresentativeFactionStartCorpusDiagnostics.toText(report));
        System.out.println(LOG_END);
    }
}
