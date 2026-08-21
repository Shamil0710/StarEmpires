package com.spacesim.world.generation;

import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnerAssignment;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnershipAuthority;
import com.spacesim.world.Stage20IndustrialInputReservationPlan;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessOutputRequest;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.SelectionAuthority;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.RouteAdmissionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20IndustrialInputFreightOwnershipPlanProductionIntegrationTest {
    @Test
    void acceptedSeedBindsOneExplicitRemoteIndustrialInputToExistingReserveOwnership() {
        var resolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);
        var routes = Stage20IndustrialInputRouteEvidencePlan.reconstruct(resolved);
        var selected = routes.processes().stream()
                .filter(value -> value.candidate().throughput().inputLimitedOutputKgPerSecond() > 0d)
                .filter(value -> value.inputs().size() == 1)
                .filter(value -> value.inputs().get(0).supplyRoutes().stream()
                        .filter(route -> route.status() == RouteAdmissionStatus.ADMITTED)
                        .noneMatch(route -> route.supplyKey().systemId().equals(
                                value.candidate().capacity().systemId())))
                .filter(value -> value.inputs().get(0).supplyRoutes().stream()
                        .anyMatch(route -> route.status() == RouteAdmissionStatus.ADMITTED))
                .findFirst()
                .orElseThrow();
        ProcessSelectionKey process = ProcessSelectionKey.from(selected.candidate());
        double requestedOutput = selected.candidate().throughput()
                .inputLimitedOutputKgPerSecond() * 0.001d;
        var reservation = Stage20IndustrialInputReservationPlan.reserve(
                resolved,
                new SelectionAuthority(
                        "selection.production-seed-1.remote-freight.test.v1",
                        resolved.rootSeed(),
                        List.of(new ProcessOutputRequest(process, requestedOutput))));
        assertTrue(reservation.remoteReservationCount() > 0);

        var bootstrapOwnership = Stage20BootstrapFreightOwnershipPlan.plan(resolved);
        var owner = bootstrapOwnership.factions().stream()
                .filter(value -> value.reserveFreighterCount() > 0)
                .max(java.util.Comparator.comparingInt(value -> value.reserveFreighterCount()))
                .orElseThrow();
        var authority = new ProcessOwnershipAuthority(
                "process-owners.production-seed-1.test.v1",
                resolved.rootSeed(),
                List.of(new ProcessOwnerAssignment(process, owner.stableFactionId())));

        var report = Stage20IndustrialInputFreightOwnershipPlan.planCurrent(
                resolved, reservation, authority);
        var repeated = Stage20IndustrialInputFreightOwnershipPlan.planCurrent(
                resolved, reservation, authority);

        assertEquals(Stage20IndustrialInputFreightOwnershipPlan.Status.ACCEPTED, report.status());
        assertEquals(report, repeated);
        assertTrue(report.freightOwnershipAuthoritative());
        assertFalse(report.operationallyAuthoritative());
        assertFalse(report.allocations().isEmpty());
        assertEquals(report.demands().size(), report.allocations().size());
        assertEquals(
                report.totalAssignedIndustrialFreighters(),
                report.allocations().stream().flatMap(value -> value.assignedSlots().stream())
                        .map(value -> value.stableFactionId() + ":" + value.ownershipOrdinal())
                        .distinct()
                        .count());
        assertFalse(report.missingAuthorities().contains(
                MissingAuthority.OWNED_INDUSTRIAL_INPUT_FREIGHT));
        assertFalse(report.missingAuthorities().contains(MissingAuthority.RESERVED_INDUSTRIAL_INPUTS));
        assertTrue(report.missingAuthorities().contains(
                MissingAuthority.INSTALLED_FACILITY_OPERATING_STATE));
        assertTrue(report.missingAuthorities().contains(MissingAuthority.INITIAL_STATION_INVENTORY));
        assertTrue(report.missingAuthorities().contains(MissingAuthority.INSTALLED_SHIPYARDS));

        report.allocations().forEach(allocation -> allocation.assignedSlots().forEach(slot ->
                assertTrue(bootstrapOwnership.factions().stream()
                        .filter(value -> value.stableFactionId().equals(slot.stableFactionId()))
                        .flatMap(value -> value.materializationSlots().stream())
                        .anyMatch(value -> value.ownershipOrdinal() == slot.ownershipOrdinal()
                                && value.commitment().isEmpty()))));
    }
}
