package com.spacesim.world.generation;

import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20ResolvedFreightAcceptance;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20ResolvedGeneratedWorldProductionProbeTest {
    @Test
    void fixedAcceptedSeedUsesCoordinatedFreightWithoutChangingGeneratedPhysicalEvidence() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent();
        var resolved = Stage20ResolvedGeneratedWorldProductionProbe.run(1L, profile);
        var historical = Stage20GeneratedWorldProductionProbe.run(1L, profile.inputs());
        var generated = resolved.generation();

        assertEquals(Stage20ResolvedGeneratedWorldProductionProbe.CURRENT_VERSION, resolved.version());
        assertEquals(historical.version(), generated.version());
        assertEquals(historical.rootSeed(), generated.rootSeed());
        assertEquals(historical.macroGeometry(), generated.macroGeometry());
        assertEquals(historical.topology(), generated.topology());
        assertEquals(historical.jumpEdges().orElseThrow().version(), generated.jumpEdges().orElseThrow().version());
        assertEquals(historical.jumpEdges().orElseThrow().topology(), generated.jumpEdges().orElseThrow().topology());
        assertEquals(historical.jumpEdges().orElseThrow().edges(), generated.jumpEdges().orElseThrow().edges());
        assertEquals(historical.localLayouts(), generated.localLayouts());
        assertEquals(historical.physicalHosts(), generated.physicalHosts());
        assertEquals(historical.resourceWorld(), generated.resourceWorld());
        assertEquals(historical.logisticsReport(), generated.logisticsReport());
        assertEquals(historical.supplyThroughput(), generated.supplyThroughput());
        assertEquals(historical.candidateEvaluations(), generated.candidateEvaluations());
        assertEquals(historical.placement(), generated.placement());
        assertEquals(historical.economicAcceptance(), generated.economicAcceptance());
        assertEquals(historical.seedAcceptance(), generated.seedAcceptance());
        assertEquals(Stage20GeneratedWorldSeedAcceptance.RESOLVED_FREIGHT_VERSION,
                resolved.seedAcceptance().version());
        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED,
                resolved.seedAcceptance().status());
        assertTrue(resolved.coordinatedFreightAcceptance().isPresent());
        assertEquals(Stage20ResolvedFreightAcceptance.CURRENT_VERSION,
                resolved.coordinatedFreightAcceptance().orElseThrow().version());
        assertEquals(resolved.rootSeed(),
                resolved.coordinatedFreightAcceptance().orElseThrow().rootSeed());
        assertTrue(resolved.coordinatedFreightAcceptance().orElseThrow().accepted());
        assertEquals(13,
                resolved.coordinatedFreightAcceptance().orElseThrow()
                        .remoteFreighterBudgetByFaction().values().iterator().next());

        var freight = resolved.coordinatedFreightAcceptance().orElseThrow();
        var wrongSeedFreight = new Stage20ResolvedFreightAcceptance.AcceptanceReport(
                freight.version(),
                2L,
                freight.placementVersion(),
                freight.supplyProfileVersion(),
                freight.searchNodeBudgetPerCommodity(),
                freight.remoteFreighterBudgetByFaction(),
                freight.commodityFrontiers(),
                freight.combination());
        assertThrows(IllegalArgumentException.class, () ->
                new Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult(
                        resolved.version(),
                        resolved.rootSeed(),
                        resolved.sourceProbeVersion(),
                        resolved.representativeProfileVersion(),
                        resolved.generation(),
                        Optional.of(wrongSeedFreight),
                        resolved.seedAcceptance()));
    }
}
