package com.spacesim.world.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20BootstrapServiceCadenceV2CorpusEvidenceTest {
    private static final String EVIDENCE_BEGIN = "STAGE20E_BOOTSTRAP_SERVICE_CADENCE_V2_EVIDENCE_BEGIN";
    private static final String EVIDENCE_END = "STAGE20E_BOOTSTRAP_SERVICE_CADENCE_V2_EVIDENCE_END";

    @Test
    void fixedCorpusProducesDeterministicCandidateEvidenceWithoutPassQuota() {
        var first = Stage20BootstrapServiceCadenceV2CorpusEvidence.evaluateCurrent();
        var second = Stage20BootstrapServiceCadenceV2CorpusEvidence.evaluateCurrent();

        assertEquals(first, second);
        assertEquals(Stage20BootstrapServiceCadenceV2CorpusEvidence.CURRENT_VERSION, first.version());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds().size(), first.seeds().size());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds(),
                first.seeds().stream().map(Stage20BootstrapServiceCadenceV2CorpusEvidence.SeedEvidence::rootSeed).toList());
        assertEquals(first.seeds().size(),
                first.acceptedSeedCount() + first.rejectedSeedCount() + first.unresolvedSeedCount());
        assertEquals(first.seeds().size(), first.seeds().stream()
                .mapToInt(seed -> seed.acceptedCandidateCount()
                        + seed.rejectedCandidateCount()
                        + seed.unresolvedCandidateCount())
                .filter(value -> value > 0)
                .count());
        assertTrue(first.supplierDeliveryBudgetSeconds() > 0d);
        assertEquals(2, first.referenceBufferCoverageSecondsByCommodity().size());

        String text = Stage20BootstrapServiceCadenceV2CorpusEvidence.toText(first);
        assertEquals(text, Stage20BootstrapServiceCadenceV2CorpusEvidence.toText(second));
        System.out.println(EVIDENCE_BEGIN);
        System.out.print(text);
        System.out.println(EVIDENCE_END);
    }
}
