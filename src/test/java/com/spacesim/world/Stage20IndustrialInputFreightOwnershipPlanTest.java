package com.spacesim.world;

import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.OwnershipReport;
import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.PlanReport;
import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.SelectedCommodityPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.DemandPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.ProducerUsage;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.StartPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.SupplierCommitment;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.FreightCapacityProfile;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.IndustrialFreightReport;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnerAssignment;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnershipAuthority;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.CommodityReservationEvidence;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.InputDemandEvidence;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.InputReservation;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessOutputRequest;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ReservationReport;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.SelectionAuthority;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20IndustrialInputFreightOwnershipPlanTest {
    private static final String FACTION = "faction.alpha";
    private static final StarSystemId START = new StarSystemId(10L);
    private static final StarSystemId SOURCE_A = new StarSystemId(20L);
    private static final StarSystemId SOURCE_B = new StarSystemId(30L);
    private static final StarSystemId PROCESSOR_A = new StarSystemId(40L);
    private static final StarSystemId PROCESSOR_B = new StarSystemId(50L);
    private static final String INPUT_A = "commodity.input.a";
    private static final String INPUT_B = "commodity.input.b";

    @Test
    void remoteReservationsConsumeDistinctExistingReserveSlotsAndCloseOnlyFreightAuthority() {
        ReservationReport reservation = acceptedReservation(List.of(
                remoteInput("a", INPUT_A, SOURCE_A, PROCESSOR_A, 6d),
                remoteInput("b", INPUT_B, SOURCE_B, PROCESSOR_B, 4d)));
        OwnershipReport ownership = ownership(0, 3);

        IndustrialFreightReport report = Stage20IndustrialInputFreightOwnershipPlan.planEvidence(
                reservation,
                ownership,
                owners(reservation),
                capacity(3),
                Stage20IndustrialInputFreightOwnershipPlanTest::linearRoute);

        assertEquals(Stage20IndustrialInputFreightOwnershipPlan.Status.ACCEPTED, report.status());
        assertTrue(report.failureReason().isEmpty());
        assertEquals(List.of(2, 1), report.demands().stream()
                .map(value -> value.minimumRequiredFreighters().orElseThrow())
                .toList());
        assertEquals(3, report.totalAssignedIndustrialFreighters());
        assertEquals(List.of(0, 1, 2), report.allocations().stream()
                .flatMap(value -> value.assignedSlots().stream())
                .map(value -> value.ownershipOrdinal())
                .sorted()
                .toList());
        assertEquals(3L, report.allocations().stream()
                .flatMap(value -> value.assignedSlots().stream())
                .map(value -> value.stableFactionId() + ":" + value.ownershipOrdinal())
                .distinct()
                .count());
        assertTrue(report.freightOwnershipAuthoritative());
        assertFalse(report.operationallyAuthoritative());

        EnumSet<MissingAuthority> expected = EnumSet.allOf(MissingAuthority.class);
        expected.remove(MissingAuthority.RESERVED_INDUSTRIAL_INPUTS);
        expected.remove(MissingAuthority.OWNED_INDUSTRIAL_INPUT_FREIGHT);
        assertEquals(expected, report.missingAuthorities());
    }

    @Test
    void sharedOwnerReserveExhaustionRejectsWithoutPartialSlotCommitments() {
        ReservationReport reservation = acceptedReservation(List.of(
                remoteInput("a", INPUT_A, SOURCE_A, PROCESSOR_A, 6d),
                remoteInput("b", INPUT_B, SOURCE_B, PROCESSOR_B, 6d)));

        IndustrialFreightReport report = Stage20IndustrialInputFreightOwnershipPlan.planEvidence(
                reservation,
                ownership(0, 3),
                owners(reservation),
                capacity(3),
                Stage20IndustrialInputFreightOwnershipPlanTest::linearRoute);

        assertEquals(
                Stage20IndustrialInputFreightOwnershipPlan.Status.INSUFFICIENT_OWNED_FREIGHT,
                report.status());
        assertTrue(report.failureReason().isPresent());
        assertEquals(4, report.factions().get(0).requiredIndustrialFreighterCount());
        assertEquals(3, report.factions().get(0).availableReserveFreighterCount());
        assertTrue(report.allocations().isEmpty());
        assertFalse(report.freightOwnershipAuthoritative());
        assertTrue(report.missingAuthorities().contains(
                MissingAuthority.OWNED_INDUSTRIAL_INPUT_FREIGHT));
        assertFalse(report.missingAuthorities().contains(MissingAuthority.RESERVED_INDUSTRIAL_INPUTS));
    }

    @Test
    void existingStage20eCommitmentsCannotBeReusedByIndustrialRoutes() {
        ReservationReport reservation = acceptedReservation(List.of(
                remoteInput("a", INPUT_A, SOURCE_A, PROCESSOR_A, 10d)));

        IndustrialFreightReport report = Stage20IndustrialInputFreightOwnershipPlan.planEvidence(
                reservation,
                ownership(3, 5),
                owners(reservation),
                capacity(5),
                Stage20IndustrialInputFreightOwnershipPlanTest::linearRoute);

        assertEquals(3, report.demands().get(0).minimumRequiredFreighters().orElseThrow());
        assertEquals(5, report.factions().get(0).ownedFreighterCount());
        assertEquals(3, report.factions().get(0).alreadyCommittedFreighterCount());
        assertEquals(2, report.factions().get(0).availableReserveFreighterCount());
        assertEquals(
                Stage20IndustrialInputFreightOwnershipPlan.Status.INSUFFICIENT_OWNED_FREIGHT,
                report.status());
        assertTrue(report.allocations().isEmpty());
    }

    @Test
    void processOwnersAndReevaluatedPhysicalRoutesFailClosedOnAnyProvenanceMismatch() {
        ReservationReport reservation = acceptedReservation(List.of(
                remoteInput("a", INPUT_A, SOURCE_A, PROCESSOR_A, 4d)));
        OwnershipReport ownership = ownership(0, 3);
        ProcessSelectionKey selected = reservation.selection().requests().get(0).process();

        assertThrows(IllegalArgumentException.class, () ->
                Stage20IndustrialInputFreightOwnershipPlan.planEvidence(
                        reservation,
                        ownership,
                        new ProcessOwnershipAuthority(
                                "owners.test.v1",
                                1L,
                                List.of(new ProcessOwnerAssignment(
                                        new ProcessSelectionKey(
                                                selected.systemId(),
                                                selected.stationPlacementId(),
                                                selected.facilityDefinitionId(),
                                                selected.processId() + ".other",
                                                selected.outputCommodityId()),
                                        FACTION))),
                        capacity(3),
                        Stage20IndustrialInputFreightOwnershipPlanTest::linearRoute));

        assertThrows(IllegalArgumentException.class, () ->
                Stage20IndustrialInputFreightOwnershipPlan.planEvidence(
                        reservation,
                        ownership,
                        owners(reservation),
                        capacity(3),
                        (origin, destination, ships) -> Optional.of(new RouteAssessment(
                                List.of(origin, START, destination), 10d, ships * 4d))));
    }

    @Test
    void entirelyLocalSelectionClosesFreightAuthorityWithoutAssigningAFreeShip() {
        ReservationReport reservation = acceptedReservation(List.of(
                localInput("a", INPUT_A, PROCESSOR_A, 3d)));

        IndustrialFreightReport report = Stage20IndustrialInputFreightOwnershipPlan.planEvidence(
                reservation,
                ownership(1, 3),
                owners(reservation),
                capacity(3),
                (origin, destination, ships) -> {
                    throw new AssertionError("local input must not invoke inter-system freight");
                });

        assertEquals(Stage20IndustrialInputFreightOwnershipPlan.Status.ACCEPTED, report.status());
        assertTrue(report.demands().isEmpty());
        assertTrue(report.allocations().isEmpty());
        assertEquals(0, report.totalAssignedIndustrialFreighters());
        assertTrue(report.freightOwnershipAuthoritative());
    }

    private static Optional<RouteAssessment> linearRoute(
            StarSystemId origin,
            StarSystemId destination,
            int ships) {
        return Optional.of(new RouteAssessment(
                List.of(origin, destination),
                10d,
                ships * 4d));
    }

    private static FreightCapacityProfile capacity(int maximum) {
        return new FreightCapacityProfile(
                "freight-capacity.test.v1",
                "representative.test.v1",
                1_000d,
                "test.fixture",
                maximum);
    }

    private static ProcessOwnershipAuthority owners(ReservationReport report) {
        return new ProcessOwnershipAuthority(
                "owners.test.v1",
                report.rootSeed(),
                report.selection().requests().stream()
                        .map(value -> new ProcessOwnerAssignment(value.process(), FACTION))
                        .toList());
    }

    private static ReservationInput remoteInput(
            String suffix,
            String commodity,
            StarSystemId source,
            StarSystemId processor,
            double rate) {
        return reservationInput(suffix, commodity, source, processor, rate, false);
    }

    private static ReservationInput localInput(
            String suffix,
            String commodity,
            StarSystemId processor,
            double rate) {
        return reservationInput(suffix, commodity, processor, processor, rate, true);
    }

    private static ReservationInput reservationInput(
            String suffix,
            String commodity,
            StarSystemId source,
            StarSystemId processor,
            double rate,
            boolean local) {
        ProcessSelectionKey process = new ProcessSelectionKey(
                processor,
                "station." + suffix,
                "facility." + suffix,
                "process." + suffix,
                "commodity.output." + suffix);
        RouteAssessment route = new RouteAssessment(
                local ? List.of(processor) : List.of(source, processor),
                10d,
                100d);
        InputReservation reservation = new InputReservation(
                process,
                commodity,
                new SupplyKey(commodity, source),
                route,
                rate,
                local);
        return new ReservationInput(process, reservation, rate);
    }

    private static ReservationReport acceptedReservation(List<ReservationInput> inputs) {
        List<ProcessOutputRequest> requests = inputs.stream()
                .map(value -> new ProcessOutputRequest(value.process(), value.rate()))
                .toList();
        List<InputDemandEvidence> demands = inputs.stream()
                .map(value -> new InputDemandEvidence(
                        value.process(),
                        value.reservation().inputCommodityId(),
                        value.rate(),
                        1d,
                        value.rate(),
                        value.rate(),
                        Stage20IndustrialInputReservationPlan.Status.ACCEPTED))
                .toList();
        List<CommodityReservationEvidence> commodities = inputs.stream()
                .map(value -> new CommodityReservationEvidence(
                        value.reservation().inputCommodityId(),
                        value.rate(),
                        value.rate(),
                        value.rate(),
                        Stage20IndustrialInputReservationPlan.Status.ACCEPTED))
                .toList();
        EnumSet<MissingAuthority> missing = EnumSet.allOf(MissingAuthority.class);
        missing.remove(MissingAuthority.RESERVED_INDUSTRIAL_INPUTS);
        return new ReservationReport(
                Stage20IndustrialInputReservationPlan.CURRENT_VERSION,
                1L,
                "resolved.test.v1",
                "routes.test.v1",
                "candidates.test.v1",
                "supply.test.v1",
                new SelectionAuthority("selection.test.v1", 1L, requests),
                Stage20IndustrialInputReservationPlan.Status.ACCEPTED,
                Optional.empty(),
                inputs.stream().map(ReservationInput::reservation).toList(),
                demands,
                commodities,
                missing);
    }

    private static OwnershipReport ownership(int committed, int owned) {
        PlanReport physical = physicalPlan(committed, owned);
        return Stage20BootstrapFreightOwnershipPlan.planAccepted(placement(), physical);
    }

    private static PlanReport physicalPlan(int committed, int owned) {
        String commodity = "commodity.bootstrap";
        SelectedCommodityPlan selected;
        if (committed == 0) {
            double delivered = 1d;
            SupplierCommitment commitment = new SupplierCommitment(
                    commodity, START, true, 0, delivered, Optional.empty());
            DemandPlan demand = new DemandPlan(commodity, delivered, delivered, 0, List.of(commitment));
            selected = new SelectedCommodityPlan(
                    commodity,
                    "frontier.test.v1",
                    "option.local",
                    Map.of(FACTION, 0),
                    List.of(new StartPlan(FACTION, START, owned, 0, List.of(demand))),
                    List.of(new ProducerUsage(new SupplyKey(commodity, START), 2d, delivered)));
        } else {
            double delivered = committed * 4d;
            SupplierCommitment commitment = new SupplierCommitment(
                    commodity,
                    SOURCE_A,
                    false,
                    committed,
                    delivered,
                    Optional.of(new RouteAssessment(List.of(SOURCE_A, START), 10d, delivered)));
            DemandPlan demand = new DemandPlan(
                    commodity, delivered, delivered, committed, List.of(commitment));
            selected = new SelectedCommodityPlan(
                    commodity,
                    "frontier.test.v1",
                    "option.remote",
                    Map.of(FACTION, committed),
                    List.of(new StartPlan(FACTION, START, owned, committed, List.of(demand))),
                    List.of(new ProducerUsage(new SupplyKey(commodity, SOURCE_A), delivered * 2d, delivered)));
        }
        return new PlanReport(
                Stage20BootstrapFreightPhysicalPlan.CURRENT_VERSION,
                Stage20ResolvedFreightAcceptance.CURRENT_VERSION,
                1L,
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                "supply.test.v1",
                100,
                Map.of(FACTION, owned),
                Stage20CommodityFreightFrontierCombiner.CURRENT_VERSION,
                Map.of(FACTION, committed),
                List.of(selected));
    }

    private static PlacementResult placement() {
        return new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "placement.test.v1",
                PlacementStatus.ACCEPTED,
                List.of(new Assignment(FACTION, START, 0d)),
                1,
                Optional.empty());
    }

    private record ReservationInput(
            ProcessSelectionKey process,
            InputReservation reservation,
            double rate) {}
}
