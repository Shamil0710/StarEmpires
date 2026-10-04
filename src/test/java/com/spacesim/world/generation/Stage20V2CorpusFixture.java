package com.spacesim.world.generation;

import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeInputs;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeResult;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Immutable legacy V2 generation only; each diagnostic still executes its own downstream analysis. */
final class Stage20V2CorpusFixture {
    private Stage20V2CorpusFixture() { }

    static ProbeResult probe(long seed, ProbeInputs inputs) {
        if (!Stage20RepresentativeSeedCorpus.seeds().contains(seed)) {
            throw new IllegalArgumentException("Only the fixed representative corpus can be reused");
        }
        if (!Baseline.INPUTS.equals(inputs)) {
            throw new IllegalArgumentException("Fixture requires the exact legacy V2 production inputs");
        }
        return Baseline.RESULTS.computeIfAbsent(seed,
                root -> Stage20GeneratedWorldProductionProbe.run(root, Baseline.INPUTS));
    }

    private static final class Baseline {
        private static final ProbeInputs INPUTS =
                Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent().inputs();
        private static final ConcurrentMap<Long, ProbeResult> RESULTS = new ConcurrentHashMap<>();
    }
}
