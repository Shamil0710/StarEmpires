package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.FailureReason;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20FreightPortfolioAllocator.AllocationReport;
import com.spacesim.world.Stage20FreightPortfolioAllocator.RequirementPlan;
import com.spacesim.world.Stage20FreightPortfolioAllocator.RequirementStatus;
import com.spacesim.world.Stage20FreightPortfolioAllocator.RouteAllocation;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.Stage20WholePlacementProducerCapacityReservation.ReservationReport;
import com.spacesim.world.Stage20WholePlacementProducerCapacityReservation.Status;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20WholePlacementProducerCapacityReservationTest {
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final StarSystemId START_A = new StarSystemId(1L);
    private static final StarSystemId START_B = new StarSystemId(2L);
    private static final StarSystemId SUPPLIER_C = new StarSystemId(3L);
    private static final StarSystemId SUPPLIER_D = new StarSystemId(4L);
    private static final String FACTION_A = "faction.alpha";
    private static final String FACTION_B = "faction.beta";

    @Test
    void rejectsSelectedPortfoliosThatOverbookOnePhysicalProducer() {
        CommodityRequirement requirement = requirement(6d);
        SupplyThroughputReport supply = supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 10d));

        ReservationReport report = Stage20WholePlacementProducerCapacityReservation.reserve(
                topology(), acceptedPlacement(), supply, List.of(requirement), Map.of(
                        FACTION_A, remoteReport(START_A, requirement,
                                allocation(SUPPLIER_C, START_A, 10d, 6d)),
                        FACTION_B, remoteReport(START_B, requirement,
                                allocation(SUPPLIER_C, START_B, 10d, 6d))));

        assertEquals(Status.SELECTED_PORTFOLIO_CONFLICT, report.status());
        assertTrue(report.failureReason().isPresent());
        assertEquals(12d, report.commodityEvidence().get(0).requiredKgPerSecond(), 1e-9);
        assertEquals(10d, report.commodityEvidence().get(0).reservedKgPerSecond(), 1e-9);
        assertEquals(10d, report.reservations().stream()
                .mapToDouble(value -> value.reservedKgPerSecond()).sum(), 1e-9);
    }

    @Test
    void acceptsSharedProducerWhenAuthoritativeCapacityCoversBothStarts() {
        CommodityRequirement requirement = requirement(6d);
        SupplyThroughputReport supply = supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 12d));

        ReservationReport report = Stage20WholePlacementProducerCapacityReservation.reserve(
                topology(), acceptedPlacement(), supply, List.of(requirement), Map.of(
                        FACTION_A, remoteReport(START_A, requirement,
                                allocation(SUPPLIER_C, START_A, 12d, 6d)),
                        FACTION_B, remoteReport(START_B, requirement,
                                allocation(SUPPLIER_C, START_B, 12d, 6d))));

        assertEquals(Status.ACCEPTED, report.status());
        assertTrue(report.failureReason().isEmpty());
        assertEquals(12d, report.reservations().stream()
                .mapToDouble(value -> value.reservedKgPerSecond()).sum(), 1e-9);
    }

    @Test
    void reservesActualDemandInsteadOfSurplusLastShipCapacity() {
        CommodityRequirement requirement = requirement(5d);
        SupplyThroughputReport supply = supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 20d));

        ReservationReport report = Stage20WholePlacementProducerCapacityReservation.reserve(
                topology(), acceptedPlacement(), supply, List.of(requirement), Map.of(
                        FACTION_A, remoteReport(START_A, requirement,
                                allocation(SUPPLIER_C, START_A, 20d, 9d)),
                        FACTION_B, remoteReport(START_B, requirement,
                                allocation(SUPPLIER_C, START_B, 20d, 9d))));

        assertEquals(Status.ACCEPTED, report.status());
        assertEquals(10d, report.reservations().stream()
                .mapToDouble(value -> value.reservedKgPerSecond()).sum(), 1e-9);
        assertEquals(10d, report.commodityEvidence().get(0).reservedKgPerSecond(), 1e-9);
    }

    @Test
    void localConsumptionCompetesWithRemoteConsumersForTheSameProducer() {
        CommodityRequirement requirement = requirement(6d);
        SupplyThroughputReport supply = supply(Map.of(new SupplyKey(WATER, START_A), 10d));

        ReservationReport report = Stage20WholePlacementProducerCapacityReservation.reserve(
                topology(), acceptedPlacement(), supply, List.of(requirement), Map.of(
                        FACTION_A, localReport(START_A, requirement, 10d, 6d),
                        FACTION_B, remoteReport(START_B, requirement,
                                allocation(START_A, START_B, 10d, 6d))));

        assertEquals(Status.SELECTED_PORTFOLIO_CONFLICT, report.status());
        assertEquals(10d, report.commodityEvidence().get(0).reservedKgPerSecond(), 1e-9);
        assertTrue(report.reservations().stream().anyMatch(value -> value.local()));
    }

    @Test
    void maximumFlowCanChooseACoexistingMixAcrossSelectedSupplierArcs() {
        CommodityRequirement requirement = requirement(6d);
        SupplyThroughputReport supply = supply(Map.of(
                new SupplyKey(WATER, SUPPLIER_C), 6d,
                new SupplyKey(WATER, SUPPLIER_D), 6d));

        ReservationReport report = Stage20WholePlacementProducerCapacityReservation.reserve(
                topology(), acceptedPlacement(), supply, List.of(requirement), Map.of(
                        FACTION_A, remoteReport(START_A, requirement,
                                allocation(SUPPLIER_C, START_A, 6d, 6d),
                                allocation(SUPPLIER_D, START_A, 6d, 6d)),
                        FACTION_B, remoteReport(START_B, requirement,
                                allocation(SUPPLIER_C, START_B, 6d, 6d),
                                allocation(SUPPLIER_D, START_B, 6d, 6d))));

        assertEquals(Status.ACCEPTED, report.status());
        assertEquals(12d, report.reservations().stream()
                .mapToDouble(value -> value.reservedKgPerSecond()).sum(), 1e-9);
        Map<StarSystemId, Double> byProducer = new java.util.TreeMap<>();
        report.reservations().forEach(value -> byProducer.merge(
                value.producerSystemId(), value.reservedKgPerSecond(), Double::sum));
        assertEquals(6d, byProducer.get(SUPPLIER_C), 1e-9);
        assertEquals(6d, byProducer.get(SUPPLIER_D), 1e-9);
    }

    @Test
    void inputMapOrderCannotChangeReservationResult() {
        CommodityRequirement requirement = requirement(6d);
        SupplyThroughputReport supply = supply(Map.of(
                new SupplyKey(WATER, SUPPLIER_C), 6d,
                new SupplyKey(WATER, SUPPLIER_D), 6d));
        AllocationReport alpha = remoteReport(START_A, requirement,
                allocation(SUPPLIER_C, START_A, 6d, 6d),
                allocation(SUPPLIER_D, START_A, 6d, 6d));
        AllocationReport beta = remoteReport(START_B, requirement,
                allocation(SUPPLIER_C, START_B, 6d, 6d),
                allocation(SUPPLIER_D, START_B, 6d, 6d));
        LinkedHashMap<String, AllocationReport> first = new LinkedHashMap<>();
        first.put(FACTION_B, beta);
        first.put(FACTION_A, alpha);
        LinkedHashMap<String, AllocationReport> second = new LinkedHashMap<>();
        second.put(FACTION_A, alpha);
        second.put(FACTION_B, beta);

        ReservationReport firstReport = Stage20WholePlacementProducerCapacityReservation.reserve(
                topology(), acceptedPlacement(), supply, List.of(requirement), first);
        ReservationReport secondReport = Stage20WholePlacementProducerCapacityReservation.reserve(
                topology(), acceptedPlacement(), supply, List.of(requirement), second);

        assertEquals(firstReport, secondReport);
    }

    @Test
    void requiresAcceptedPlacementAndExactFactionAllocationSet() {
        CommodityRequirement requirement = requirement(6d);
        SupplyThroughputReport supply = supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 12d));
        PlacementResult rejected = new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "test.profile",
                PlacementStatus.REJECTED_SEED,
                List.of(),
                0,
                Optional.of(FailureReason.INSUFFICIENT_ACCEPTED_CANDIDATES));

        assertThrows(IllegalArgumentException.class, () ->
                Stage20WholePlacementProducerCapacityReservation.reserve(
                        topology(), rejected, supply, List.of(requirement), Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                Stage20WholePlacementProducerCapacityReservation.reserve(
                        topology(), acceptedPlacement(), supply, List.of(requirement), Map.of(
                                FACTION_A, remoteReport(START_A, requirement,
                                        allocation(SUPPLIER_C, START_A, 12d, 6d)))));
    }

    @Test
    void revalidatesRemoteRoutesAgainstAuthoritativeTopology() {
        CommodityRequirement requirement = requirement(6d);
        SupplyThroughputReport supply = supply(Map.of(new SupplyKey(WATER, SUPPLIER_C), 12d));
        RouteAllocation shortcut = new RouteAllocation(
                WATER,
                SUPPLIER_C,
                1,
                12d,
                6d,
                new RouteAssessment(List.of(SUPPLIER_C, START_B), 100d, 6d));
        AllocationReport beta = remoteReport(START_B, requirement, shortcut);
        GalaxyTopology topologyWithoutDirectCtoB = topology(false);

        assertThrows(IllegalArgumentException.class, () ->
                Stage20WholePlacementProducerCapacityReservation.reserve(
                        topologyWithoutDirectCtoB,
                        acceptedPlacement(),
                        supply,
                        List.of(requirement),
                        Map.of(
                                FACTION_A, remoteReport(START_A, requirement,
                                        allocation(SUPPLIER_C, START_A, 12d, 6d)),
                                FACTION_B, beta)));
    }

    private static CommodityRequirement requirement(double rate) {
        return new CommodityRequirement(WATER, 1_000d, rate);
    }

    private static SupplyThroughputReport supply(Map<SupplyKey, Double> capacities) {
        return new SupplyThroughputReport("test.supply", capacities, Set.of(), List.of());
    }

    private static PlacementResult acceptedPlacement() {
        return new PlacementResult(
                Stage20FactionStartPlacementGenerator.CURRENT_VERSION,
                1L,
                "test.profile",
                PlacementStatus.ACCEPTED,
                List.of(
                        new Assignment(FACTION_B, START_B, 0d),
                        new Assignment(FACTION_A, START_A, 0d)),
                2,
                Optional.empty());
    }

    private static AllocationReport localReport(
            StarSystemId start,
            CommodityRequirement requirement,
            double localAvailable,
            double localDelivered) {
        RequirementPlan plan = new RequirementPlan(
                WATER,
                requirement.minSupplierThroughputKgPerSecond(),
                localAvailable,
                localDelivered,
                0d,
                0d,
                localDelivered,
                0,
                RequirementStatus.SATISFIED,
                List.of());
        return new AllocationReport(
                Stage20FreightPortfolioAllocator.CURRENT_VERSION,
                start,
                13,
                0,
                true,
                Optional.empty(),
                List.of(plan));
    }

    private static AllocationReport remoteReport(
            StarSystemId start,
            CommodityRequirement requirement,
            RouteAllocation... allocations) {
        double admitted = 0d;
        double delivered = 0d;
        int freighters = 0;
        for (RouteAllocation allocation : allocations) {
            admitted += allocation.supplierCapacityKgPerSecond();
            delivered += allocation.deliveredCapacityKgPerSecond();
            freighters += allocation.allocatedFreighters();
        }
        RequirementPlan plan = new RequirementPlan(
                WATER,
                requirement.minSupplierThroughputKgPerSecond(),
                0d,
                0d,
                admitted,
                delivered,
                delivered,
                freighters,
                RequirementStatus.SATISFIED,
                List.of(allocations));
        return new AllocationReport(
                Stage20FreightPortfolioAllocator.CURRENT_VERSION,
                start,
                13,
                freighters,
                true,
                Optional.empty(),
                List.of(plan));
    }

    private static RouteAllocation allocation(
            StarSystemId supplier,
            StarSystemId consumer,
            double supplierCapacity,
            double deliveredCapacity) {
        return new RouteAllocation(
                WATER,
                supplier,
                1,
                supplierCapacity,
                deliveredCapacity,
                new RouteAssessment(List.of(supplier, consumer), 100d, deliveredCapacity));
    }

    private static GalaxyTopology topology() {
        return topology(true);
    }

    private static GalaxyTopology topology(boolean directCtoB) {
        StarSystemNode a = new StarSystemNode(START_A, "A", 0d, 0d);
        StarSystemNode b = new StarSystemNode(START_B, "B", 1d, 0d);
        StarSystemNode c = new StarSystemNode(SUPPLIER_C, "C", 2d, 0d);
        StarSystemNode d = new StarSystemNode(SUPPLIER_D, "D", 3d, 0d);
        java.util.ArrayList<JumpConnection> edges = new java.util.ArrayList<>();
        edges.add(new JumpConnection(START_A, START_B));
        edges.add(new JumpConnection(START_A, SUPPLIER_C));
        if (directCtoB) {
            edges.add(new JumpConnection(START_B, SUPPLIER_C));
        }
        edges.add(new JumpConnection(START_A, SUPPLIER_D));
        edges.add(new JumpConnection(START_B, SUPPLIER_D));
        return new GalaxyTopology(
                new GalaxyId(1L),
                "reservation-test",
                List.of(new SectorNode(
                        new SectorId(1L),
                        "sector",
                        List.of(a, b, c, d))),
                edges);
    }
}