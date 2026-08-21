package com.spacesim.world.generation;

import com.spacesim.world.Stage20IndustrialInputReservationPlan;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessOutputRequest;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ReservationReport;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.SelectionAuthority;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.Status;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20IndustrialInputReservationPlanProductionIntegrationTest {
    @Test
    void acceptedSeedReservesAnExplicitPhysicalProcessRateWithoutClosingFreightOwnership() {
        var resolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);
        var routeEvidence = Stage20IndustrialInputRouteEvidencePlan.reconstruct(resolved);
        var selected = routeEvidence.processes().stream()
                .filter(value -> value.candidate().throughput().inputLimitedOutputKgPerSecond() > 0d)
                .findFirst()
                .orElseThrow();
        double requestedOutput = selected.candidate().throughput().inputLimitedOutputKgPerSecond() * 0.5d;
        SelectionAuthority selection = new SelectionAuthority(
                "selection.production-seed-1.test.v1",
                resolved.rootSeed(),
                List.of(new ProcessOutputRequest(
                        ProcessSelectionKey.from(selected.candidate()),
                        requestedOutput)));

        ReservationReport report = Stage20IndustrialInputReservationPlan.reserve(resolved, selection);
        ReservationReport repeated = Stage20IndustrialInputReservationPlan.reserve(resolved, selection);

        assertEquals(Stage20IndustrialInputReservationPlan.CURRENT_VERSION, report.version());
        assertEquals(resolved.rootSeed(), report.rootSeed());
        assertEquals(resolved.version(), report.resolvedProbeVersion());
        assertEquals(Stage20IndustrialInputRouteEvidencePlan.CURRENT_VERSION, report.routeEvidenceVersion());
        assertEquals(Status.ACCEPTED, report.status());
        assertEquals(report, repeated);
        assertFalse(report.reservations().isEmpty());
        assertTrue(report.inputDemands().stream().allMatch(value -> value.status() == Status.ACCEPTED));
        assertTrue(report.commodities().stream().allMatch(value -> value.status() == Status.ACCEPTED));
        assertTrue(report.industrialInputReservationAuthoritative());
        assertFalse(report.operationallyAuthoritative());

        EnumSet<MissingAuthority> expectedMissing = EnumSet.allOf(MissingAuthority.class);
        expectedMissing.remove(MissingAuthority.RESERVED_INDUSTRIAL_INPUTS);
        assertEquals(expectedMissing, report.missingAuthorities());
        assertTrue(report.missingAuthorities().contains(MissingAuthority.OWNED_INDUSTRIAL_INPUT_FREIGHT));
        assertTrue(report.missingAuthorities().contains(MissingAuthority.INSTALLED_FACILITY_OPERATING_STATE));
        assertTrue(report.missingAuthorities().contains(MissingAuthority.INITIAL_STATION_INVENTORY));
        assertTrue(report.missingAuthorities().contains(MissingAuthority.INSTALLED_SHIPYARDS));

        TreeMap<SupplyKey, Double> reservedBySupply = new TreeMap<>();
        report.reservations().forEach(value -> reservedBySupply.merge(
                value.supplyKey(),
                value.reservedInputKgPerSecond(),
                Double::sum));
        var finalSupply = resolved.generation().supplyThroughput().orElseThrow()
                .capacityKgPerSecondBySupply();
        reservedBySupply.forEach((key, reserved) -> assertTrue(reserved <= finalSupply.get(key) + 1e-9));

        report.inputDemands().forEach(value -> assertEquals(
                value.requiredInputKgPerSecond(),
                value.maxReservableInputKgPerSecond(),
                1e-9));
        report.reservations().forEach(value -> {
            var path = value.route().orderedSystems();
            assertEquals(value.supplyKey().systemId(), path.get(0));
            assertEquals(value.process().systemId(), path.get(path.size() - 1));
        });

        ProcessSelectionKey key = selection.requests().get(0).process();
        ProcessSelectionKey unknown = new ProcessSelectionKey(
                key.systemId(),
                key.stationPlacementId(),
                key.facilityDefinitionId(),
                key.processId() + ".unknown",
                key.outputCommodityId());
        assertThrows(IllegalArgumentException.class, () -> Stage20IndustrialInputReservationPlan.reserve(
                resolved,
                new SelectionAuthority(
                        "selection.invalid.test.v1",
                        resolved.rootSeed(),
                        List.of(new ProcessOutputRequest(unknown, requestedOutput)))));
    }
}
