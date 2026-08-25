package com.spacesim.world.generation;

import com.spacesim.world.Stage20DirectionalJumpAnchorLayout;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20LocalInfrastructureLayout;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20ResolvedFreightAcceptance;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20ResolvedGeneratedWorldProductionProbeTest {
    @Test
    void fixedAcceptedSeedFacesEveryJumpEntryTowardItsNeighborWithoutChangingRadialOrEconomicEvidence() {
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
        assertNotEquals(historical.localLayouts(), generated.localLayouts());
        assertEquals(historical.physicalHosts(), generated.physicalHosts());
        assertEquals(historical.resourceWorld(), generated.resourceWorld());
        assertEquals(historical.logisticsReport(), generated.logisticsReport());
        assertEquals(historical.supplyThroughput(), generated.supplyThroughput());
        assertEquals(historical.candidateEvaluations(), generated.candidateEvaluations());
        assertEquals(historical.placement(), generated.placement());
        assertEquals(historical.economicAcceptance(), generated.economicAcceptance());
        assertEquals(historical.seedAcceptance(), generated.seedAcceptance());

        Map<StarSystemId, Stage20LocalInfrastructureLayout> historicalLayouts = bySystem(
                historical.localLayouts().orElseThrow());
        Map<StarSystemId, Stage20LocalInfrastructureLayout> alignedLayouts = bySystem(
                generated.localLayouts().orElseThrow());
        var topology = generated.topology().requireAcceptedTopology();
        boolean foundMultiExitSystem = false;
        for (StarSystemNode system : topology.systems()) {
            Stage20LocalInfrastructureLayout before = historicalLayouts.get(system.id());
            Stage20LocalInfrastructureLayout after = alignedLayouts.get(system.id());
            InfrastructurePlacement beforeHub = placement(before, before.majorHubId());
            InfrastructurePlacement afterHub = placement(after, after.majorHubId());
            assertEquals(beforeHub.position(), afterHub.position());

            var neighbors = topology.neighbors(system.id()).stream().sorted().toList();
            long anchorCount = after.placements().stream()
                    .filter(value -> value.kind() == PlacementKind.JUMP_ARRIVAL_ANCHOR)
                    .count();
            assertEquals(neighbors.size(), anchorCount);
            if (neighbors.size() >= 2) {
                foundMultiExitSystem = true;
                assertNotEquals(
                        placement(after, Stage20DirectionalJumpAnchorLayout.anchorId(system.id(), neighbors.get(0))).position(),
                        placement(after, Stage20DirectionalJumpAnchorLayout.anchorId(system.id(), neighbors.get(1))).position());
            }

            for (StarSystemId neighborId : neighbors) {
                String anchorId = Stage20DirectionalJumpAnchorLayout.anchorId(system.id(), neighborId);
                InfrastructurePlacement oldAnchor = placement(before, anchorId);
                InfrastructurePlacement alignedAnchor = placement(after, anchorId);
                double oldRadius = beforeHub.position().distanceTo(oldAnchor.position());
                double alignedRadius = afterHub.position().distanceTo(alignedAnchor.position());
                assertEquals(oldRadius, alignedRadius, Math.max(1e-5d, oldRadius * 1e-12d));

                StarSystemNode neighbor = topology.findSystem(neighborId).orElseThrow();
                double macroX = neighbor.x() - system.x();
                double macroY = neighbor.y() - system.y();
                var local = afterHub.position().displacementTo(alignedAnchor.position());
                double cross = macroX * local.deltaYM() - macroY * local.deltaXM();
                double scale = StrictMath.hypot(macroX, macroY)
                        * StrictMath.hypot(local.deltaXM(), local.deltaYM());
                assertEquals(0d, cross, Math.max(1e-7d, scale * 1e-12d));
                assertTrue(macroX * local.deltaXM() + macroY * local.deltaYM() > 0d);

                var endpoint = generated.jumpEdges().orElseThrow().edges().stream()
                        .filter(edge -> edge.connection().first().equals(system.id())
                                && edge.connection().second().equals(neighborId)
                                || edge.connection().second().equals(system.id())
                                && edge.connection().first().equals(neighborId))
                        .findFirst()
                        .orElseThrow()
                        .arrivalIn(system.id());
                assertEquals(anchorId, endpoint.anchorId());
                assertEquals(alignedAnchor.position(), endpoint.position());
            }
        }
        assertTrue(foundMultiExitSystem, "representative accepted seed must exercise multiple local FTL exits");

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

    private static Map<StarSystemId, Stage20LocalInfrastructureLayout> bySystem(
            java.util.List<Stage20LocalInfrastructureLayout> layouts) {
        TreeMap<StarSystemId, Stage20LocalInfrastructureLayout> result = new TreeMap<>();
        for (Stage20LocalInfrastructureLayout layout : layouts) {
            result.put(layout.systemId(), layout);
        }
        return Map.copyOf(result);
    }

    private static InfrastructurePlacement placement(
            Stage20LocalInfrastructureLayout layout,
            String id) {
        return layout.placements().stream()
                .filter(value -> value.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing generated placement: " + id));
    }
}