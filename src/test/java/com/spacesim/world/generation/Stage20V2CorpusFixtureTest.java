package com.spacesim.world.generation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("slow")
class Stage20V2CorpusFixtureTest {
    @Test
    void reusedEvidenceMatchesIndependentProductionGenerationAndIsReadOnly() {
        var inputs = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent().inputs();
        var reused = Stage20V2CorpusFixture.probe(1L, inputs);
        var independent = Stage20GeneratedWorldProductionProbe.run(1L, inputs);
        assertEquals(independent.version(), reused.version());
        assertEquals(independent.macroGeometry(), reused.macroGeometry());
        assertEquals(independent.topology(), reused.topology());
        assertEquals(independent.jumpEdges().orElseThrow().version(), reused.jumpEdges().orElseThrow().version());
        assertEquals(independent.jumpEdges().orElseThrow().edges(), reused.jumpEdges().orElseThrow().edges());
        assertEquals(independent.localLayouts(), reused.localLayouts());
        assertEquals(independent.physicalHosts(), reused.physicalHosts());
        assertEquals(independent.resourceWorld(), reused.resourceWorld());
        assertEquals(independent.logisticsReport(), reused.logisticsReport());
        assertEquals(independent.supplyThroughput(), reused.supplyThroughput());
        assertEquals(independent.candidateEvaluations(), reused.candidateEvaluations());
        assertEquals(independent.placement(), reused.placement());
        assertEquals(independent.economicAcceptance(), reused.economicAcceptance());
        assertEquals(independent.seedAcceptance(), reused.seedAcceptance());
        assertSame(reused, Stage20V2CorpusFixture.probe(1L,
                Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent().inputs()));
        assertThrows(UnsupportedOperationException.class,
                () -> reused.localLayouts().orElseThrow().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> reused.supplyThroughput().orElseThrow().capacityKgPerSecondBySupply().clear());
    }

    @Test
    void concurrentReadersShareOnlyTheImmutableGenerationResult() {
        var inputs = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent().inputs();
        var readers = IntStream.range(0, 4)
                .mapToObj(index -> CompletableFuture.supplyAsync(() -> Stage20V2CorpusFixture.probe(2L, inputs)))
                .toList();
        var first = readers.get(0).join();
        readers.forEach(reader -> assertSame(first, reader.join()));
        assertEquals(2L, first.rootSeed());
    }

    @Test
    void fixtureRejectsDifferentPolicyAndSeedsOutsideTheCorpus() {
        var v2 = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent().inputs();
        assertThrows(IllegalArgumentException.class, () -> Stage20V2CorpusFixture.probe(17L, v2));
        assertThrows(IllegalArgumentException.class, () -> Stage20V2CorpusFixture.probe(1L,
                Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent().inputs()));
    }
}
