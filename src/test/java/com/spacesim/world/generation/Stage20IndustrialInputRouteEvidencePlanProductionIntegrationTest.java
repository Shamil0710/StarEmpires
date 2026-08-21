package com.spacesim.world.generation;

import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan.ProcessInputRoutePlan;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan.RouteEvidenceReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.RouteAdmissionStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20IndustrialInputRouteEvidencePlanProductionIntegrationTest {
    @Test
    void acceptedResolvedSeedRetainsExactCandidateInputSourcesAndNeighborRoutes() {
        var resolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);

        RouteEvidenceReport report = Stage20IndustrialInputRouteEvidencePlan.reconstruct(resolved);
        RouteEvidenceReport repeated = Stage20IndustrialInputRouteEvidencePlan.reconstruct(resolved);

        assertEquals(Stage20IndustrialInputRouteEvidencePlan.CURRENT_VERSION, report.version());
        assertEquals(resolved.rootSeed(), report.rootSeed());
        assertEquals(resolved.version(), report.resolvedProbeVersion());
        assertEquals(resolved.generation().supplyThroughput().orElseThrow().profileVersion(),
                report.supplyProfileVersion());
        assertEquals(report, repeated);
        assertEquals(resolved.generation().supplyThroughput().orElseThrow().processEvidence().size(),
                report.processes().size());
        assertTrue(report.candidateRouteCount() > 0);
        assertTrue(report.admittedRouteCount() > 0);

        var topology = resolved.generation().topology().requireAcceptedTopology();
        var finalSupply = resolved.generation().supplyThroughput().orElseThrow()
                .capacityKgPerSecondBySupply();
        for (ProcessInputRoutePlan process : report.processes()) {
            assertFalse(process.inputs().isEmpty());
            assertEquals(process.candidate().throughput().inputEvidence(), process.inputs());
            for (var input : process.inputs()) {
                for (var route : input.supplyRoutes()) {
                    assertTrue(finalSupply.get(route.supplyKey()) + 1e-9
                            >= route.sourceCapacityKgPerSecond());
                    if (route.route().isEmpty()) {
                        assertEquals(RouteAdmissionStatus.NO_FEASIBLE_ROUTE, route.status());
                        continue;
                    }
                    List<com.spacesim.world.StarSystemId> path =
                            route.route().orElseThrow().orderedSystems();
                    assertEquals(route.supplyKey().systemId(), path.get(0));
                    assertEquals(process.candidate().capacity().systemId(), path.get(path.size() - 1));
                    for (int index = 0; index < path.size() - 1; index++) {
                        assertTrue(topology.neighbors(path.get(index)).contains(path.get(index + 1)));
                    }
                }
            }
        }

        assertEquals(EnumSet.allOf(MissingAuthority.class), report.missingAuthorities());
        assertFalse(report.reservationAuthoritative());

        EnumSet<MissingAuthority> incomplete = EnumSet.allOf(MissingAuthority.class);
        incomplete.remove(MissingAuthority.OWNED_INDUSTRIAL_INPUT_FREIGHT);
        assertThrows(IllegalArgumentException.class, () -> new RouteEvidenceReport(
                report.version(),
                report.rootSeed(),
                report.resolvedProbeVersion(),
                report.candidatePlanVersion(),
                report.supplyProfileVersion(),
                report.processes(),
                incomplete));

        ProcessInputRoutePlan first = report.processes().get(0);
        assertThrows(IllegalArgumentException.class, () -> new ProcessInputRoutePlan(
                first.candidate(),
                List.of()));
    }
}
