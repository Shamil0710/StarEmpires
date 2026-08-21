package com.spacesim.world;

import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ProcessKind;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.StationProcessCapacity;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessOutputRequest;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ReservationReport;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.SelectionAuthority;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.Status;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan.ProcessInputRoutePlan;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan.RouteEvidenceReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.CandidateStatus;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.ProcessCandidate;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.InputSupplyRouteEvidence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.ProcessInputThroughputEvidence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.ProcessThroughputEvidence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.RouteAdmissionStatus;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20IndustrialInputReservationPlanTest {
    private static final String INPUT = "commodity.feedstock.shared";
    private static final StarSystemId SOURCE = new StarSystemId(1L);
    private static final StarSystemId PROCESSOR_A = new StarSystemId(2L);
    private static final StarSystemId PROCESSOR_B = new StarSystemId(3L);
    private static final SupplyKey SHARED_SUPPLY = new SupplyKey(INPUT, SOURCE);

    @Test
    void explicitSingleProcessRateReservesExactInputAndClosesOnlyInputAuthority() {
        Fixture fixture = fixture();
        ProcessOutputRequest request = new ProcessOutputRequest(
                ProcessSelectionKey.from(fixture.first().candidate()),
                6d);

        ReservationReport report = Stage20IndustrialInputReservationPlan.reserveEvidence(
                fixture.supply(),
                fixture.routes(),
                new SelectionAuthority("selection.test.v1", 7L, List.of(request)));

        assertEquals(Status.ACCEPTED, report.status());
        assertTrue(report.failureReason().isEmpty());
        assertEquals(1, report.reservations().size());
        assertEquals(6d, report.reservations().get(0).reservedInputKgPerSecond(), 1e-9);
        assertEquals(SHARED_SUPPLY, report.reservations().get(0).supplyKey());
        assertEquals(6d, report.inputDemands().get(0).requiredInputKgPerSecond(), 1e-9);
        assertEquals(6d, report.inputDemands().get(0).maxReservableInputKgPerSecond(), 1e-9);

        EnumSet<MissingAuthority> expectedMissing = EnumSet.allOf(MissingAuthority.class);
        expectedMissing.remove(MissingAuthority.RESERVED_INDUSTRIAL_INPUTS);
        assertEquals(expectedMissing, report.missingAuthorities());
        assertTrue(report.industrialInputReservationAuthoritative());
        assertTrue(report.missingAuthorities().contains(MissingAuthority.OWNED_INDUSTRIAL_INPUT_FREIGHT));
        assertFalse(report.operationallyAuthoritative());
    }

    @Test
    void sharedSupplyKeyCannotBeDoubleReservedAcrossSelectedProcesses() {
        Fixture fixture = fixture();
        ProcessOutputRequest first = new ProcessOutputRequest(
                ProcessSelectionKey.from(fixture.first().candidate()),
                6d);
        ProcessOutputRequest second = new ProcessOutputRequest(
                ProcessSelectionKey.from(fixture.second().candidate()),
                6d);

        ReservationReport report = Stage20IndustrialInputReservationPlan.reserveEvidence(
                fixture.supply(),
                fixture.routes(),
                new SelectionAuthority("selection.test.v1", 7L, List.of(second, first)));

        assertEquals(Status.SHARED_SUPPLY_KEY_CONFLICT, report.status());
        assertTrue(report.failureReason().isPresent());
        assertTrue(report.reservations().isEmpty());
        assertEquals(12d, report.commodities().get(0).requiredInputKgPerSecond(), 1e-9);
        assertEquals(10d, report.commodities().get(0).maxReservableInputKgPerSecond(), 1e-9);
        assertEquals(10d, report.inputDemands().stream()
                .mapToDouble(value -> value.maxReservableInputKgPerSecond()).sum(), 1e-9);
        assertEquals(EnumSet.allOf(MissingAuthority.class), report.missingAuthorities());
        assertFalse(report.industrialInputReservationAuthoritative());
    }

    @Test
    void requestOrderingCannotChangeTheDeterministicReservation() {
        Fixture fixture = fixture();
        ProcessOutputRequest first = new ProcessOutputRequest(
                ProcessSelectionKey.from(fixture.first().candidate()),
                4d);
        ProcessOutputRequest second = new ProcessOutputRequest(
                ProcessSelectionKey.from(fixture.second().candidate()),
                6d);

        ReservationReport firstOrder = Stage20IndustrialInputReservationPlan.reserveEvidence(
                fixture.supply(),
                fixture.routes(),
                new SelectionAuthority("selection.test.v1", 7L, List.of(first, second)));
        ReservationReport secondOrder = Stage20IndustrialInputReservationPlan.reserveEvidence(
                fixture.supply(),
                fixture.routes(),
                new SelectionAuthority("selection.test.v1", 7L, List.of(second, first)));

        assertEquals(Status.ACCEPTED, firstOrder.status());
        assertEquals(firstOrder, secondOrder);
        assertEquals(10d, firstOrder.reservations().stream()
                .mapToDouble(value -> value.reservedInputKgPerSecond()).sum(), 1e-9);
    }

    @Test
    void selectionCannotInventCandidateIdentityOrExceedIndividualPhysicalBound() {
        Fixture fixture = fixture();
        ProcessSelectionKey known = ProcessSelectionKey.from(fixture.first().candidate());
        ProcessSelectionKey unknown = new ProcessSelectionKey(
                known.systemId(),
                known.stationPlacementId(),
                known.facilityDefinitionId(),
                "process.unknown",
                known.outputCommodityId());

        assertThrows(IllegalArgumentException.class, () -> Stage20IndustrialInputReservationPlan.reserveEvidence(
                fixture.supply(),
                fixture.routes(),
                new SelectionAuthority(
                        "selection.test.v1",
                        7L,
                        List.of(new ProcessOutputRequest(unknown, 1d)))));
        assertThrows(IllegalArgumentException.class, () -> Stage20IndustrialInputReservationPlan.reserveEvidence(
                fixture.supply(),
                fixture.routes(),
                new SelectionAuthority(
                        "selection.test.v1",
                        7L,
                        List.of(new ProcessOutputRequest(known, 10.1d)))));
        assertThrows(IllegalArgumentException.class, () -> Stage20IndustrialInputReservationPlan.reserveEvidence(
                fixture.supply(),
                fixture.routes(),
                new SelectionAuthority(
                        "selection.test.v1",
                        8L,
                        List.of(new ProcessOutputRequest(known, 1d)))));
    }

    private static Fixture fixture() {
        ProcessInputRoutePlan first = process("a", PROCESSOR_A);
        ProcessInputRoutePlan second = process("b", PROCESSOR_B);
        SupplyThroughputReport supply = new SupplyThroughputReport(
                "supply.test.v1",
                Map.of(SHARED_SUPPLY, 10d),
                Set.of(),
                List.of(first.candidate().throughput(), second.candidate().throughput()));
        RouteEvidenceReport routes = new RouteEvidenceReport(
                Stage20IndustrialInputRouteEvidencePlan.CURRENT_VERSION,
                7L,
                "resolved.test.v1",
                "candidate.test.v1",
                supply.profileVersion(),
                List.of(second, first),
                EnumSet.allOf(MissingAuthority.class));
        return new Fixture(supply, routes, first, second);
    }

    private static ProcessInputRoutePlan process(String suffix, StarSystemId processor) {
        RouteAssessment route = new RouteAssessment(List.of(SOURCE, processor), 10d, 10d);
        InputSupplyRouteEvidence sourceRoute = new InputSupplyRouteEvidence(
                SHARED_SUPPLY,
                10d,
                Optional.of(route),
                RouteAdmissionStatus.ADMITTED,
                10d);
        ProcessInputThroughputEvidence input = new ProcessInputThroughputEvidence(
                INPUT,
                1d,
                100d,
                List.of(sourceRoute),
                10d,
                10d);
        StationProcessCapacity capacity = new StationProcessCapacity(
                processor,
                "station." + suffix,
                "facility." + suffix,
                ProcessKind.REFINING,
                "process." + suffix,
                "commodity.output." + suffix,
                10d,
                10d,
                10d);
        ProcessThroughputEvidence throughput = new ProcessThroughputEvidence(
                processor,
                capacity.stationPlacementId(),
                capacity.facilityDefinitionId(),
                capacity.processId(),
                capacity.outputCommodityId(),
                10d,
                List.of(input),
                10d);
        ProcessCandidate candidate = new ProcessCandidate(
                capacity,
                throughput,
                CandidateStatus.REACHABLE_UNRESERVED_UPPER_BOUND);
        return new ProcessInputRoutePlan(candidate, List.of(input));
    }

    private record Fixture(
            SupplyThroughputReport supply,
            RouteEvidenceReport routes,
            ProcessInputRoutePlan first,
            ProcessInputRoutePlan second) {}
}
