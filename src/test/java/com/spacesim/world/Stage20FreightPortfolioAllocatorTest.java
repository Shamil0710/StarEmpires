package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FreightPortfolioAllocator.AllocationReport;
import com.spacesim.world.Stage20FreightPortfolioAllocator.FailureReason;
import com.spacesim.world.Stage20FreightPortfolioAllocator.RequirementStatus;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
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

class Stage20FreightPortfolioAllocatorTest {
    private static final String WATER = "commodity.feedstock.water_ice";
    private static final String ORE = "commodity.feedstock.metallic_ore";
    private static final StarSystemId START = new StarSystemId(1L);
    private static final StarSystemId SUPPLIER_A = new StarSystemId(2L);
    private static final StarSystemId SUPPLIER_B = new StarSystemId(3L);

    @Test
    void localSupplyConsumesNoInterSystemFreighters() {
        AllocationReport report = Stage20FreightPortfolioAllocator.allocate(
                topology(true, true),
                supply(Map.of(new SupplyKey(WATER, START), 60d)),
                START,
                List.of(requirement(WATER, 50d, 1_000d)),
                1,
                (origin, destination, ships) -> {
                    throw new AssertionError("local supply must not call the inter-system route allocator");
                });

        assertTrue(report.accepted());
        assertEquals(0, report.minimumRemoteFreightersRequired());
        var water = report.requirementPlans().get(0);
        assertEquals(50d, water.localDeliveredKgPerSecond(), 1e-9);
        assertEquals(0, water.minimumRemoteFreightersRequired());
        assertTrue(water.remoteAllocations().isEmpty());
    }

    @Test
    void combinesTwoRemoteSuppliersWithoutDoubleCountingShips() {
        LinkedHashMap<SupplyKey, Double> capacities = new LinkedHashMap<>();
        capacities.put(new SupplyKey(WATER, SUPPLIER_B), 6d);
        capacities.put(new SupplyKey(WATER, SUPPLIER_A), 6d);

        AllocationReport report = Stage20FreightPortfolioAllocator.allocate(
                topology(true, true),
                supply(capacities),
                START,
                List.of(requirement(WATER, 10d, 1_000d)),
                2,
                linearRoutes(Map.of(SUPPLIER_A, 6d, SUPPLIER_B, 6d), 100d));

        assertTrue(report.accepted());
        assertEquals(2, report.minimumRemoteFreightersRequired());
        var water = report.requirementPlans().get(0);
        assertEquals(RequirementStatus.SATISFIED, water.status());
        assertEquals(2, water.remoteAllocations().size());
        assertEquals(SUPPLIER_A, water.remoteAllocations().get(0).supplierSystemId());
        assertEquals(SUPPLIER_B, water.remoteAllocations().get(1).supplierSystemId());
        assertEquals(1, water.remoteAllocations().get(0).allocatedFreighters());
        assertEquals(1, water.remoteAllocations().get(1).allocatedFreighters());
    }

    @Test
    void essentialCommoditiesShareOneFiniteStartFleetInsteadOfReusingTheSameShip() {
        AllocationReport report = Stage20FreightPortfolioAllocator.allocate(
                topology(true, true),
                supply(Map.of(
                        new SupplyKey(WATER, SUPPLIER_A), 5d,
                        new SupplyKey(ORE, SUPPLIER_B), 5d)),
                START,
                List.of(
                        requirement(WATER, 5d, 1_000d),
                        requirement(ORE, 5d, 1_000d)),
                1,
                linearRoutes(Map.of(SUPPLIER_A, 5d, SUPPLIER_B, 5d), 100d));

        assertFalse(report.accepted());
        assertEquals(Optional.of(FailureReason.SHARED_FLEET_EXHAUSTED), report.failureReason());
        assertEquals(2, report.minimumRemoteFreightersRequired());
        assertTrue(report.requirementPlans().stream()
                .allMatch(plan -> plan.minimumRemoteFreightersRequired() == 1));
    }

    @Test
    void localSupplyReducesTheRemoteFleetRequirement() {
        AllocationReport report = Stage20FreightPortfolioAllocator.allocate(
                topology(true, false),
                supply(Map.of(
                        new SupplyKey(WATER, START), 4d,
                        new SupplyKey(WATER, SUPPLIER_A), 10d)),
                START,
                List.of(requirement(WATER, 10d, 1_000d)),
                1,
                linearRoutes(Map.of(SUPPLIER_A, 6d), 100d));

        assertTrue(report.accepted());
        assertEquals(1, report.minimumRemoteFreightersRequired());
        var water = report.requirementPlans().get(0);
        assertEquals(4d, water.localDeliveredKgPerSecond(), 1e-9);
        assertEquals(6d, water.remoteDeliveredCapacityKgPerSecond(), 1e-9);
    }

    @Test
    void producerCapacityCapsLaterFreighterMarginals() {
        AllocationReport report = Stage20FreightPortfolioAllocator.allocate(
                topology(true, false),
                supply(Map.of(new SupplyKey(WATER, SUPPLIER_A), 7d)),
                START,
                List.of(requirement(WATER, 7d, 1_000d)),
                2,
                linearRoutes(Map.of(SUPPLIER_A, 5d), 100d));

        assertTrue(report.accepted());
        var allocation = report.requirementPlans().get(0).remoteAllocations().get(0);
        assertEquals(2, allocation.allocatedFreighters());
        assertEquals(7d, allocation.deliveredCapacityKgPerSecond(), 1e-9);
    }

    @Test
    void routeOutsideServiceTimeIsNotAdmittedSupply() {
        AllocationReport report = Stage20FreightPortfolioAllocator.allocate(
                topology(true, false),
                supply(Map.of(new SupplyKey(WATER, SUPPLIER_A), 50d)),
                START,
                List.of(requirement(WATER, 10d, 50d)),
                2,
                linearRoutes(Map.of(SUPPLIER_A, 10d), 100d));

        assertFalse(report.accepted());
        assertEquals(Optional.of(FailureReason.REQUIREMENT_UNSATISFIED), report.failureReason());
        assertEquals(RequirementStatus.INSUFFICIENT_ADMITTED_SUPPLY,
                report.requirementPlans().get(0).status());
    }

    @Test
    void insufficientFreightCapacityRemainsExplicitEvenWhenProducerSupplyExists() {
        AllocationReport report = Stage20FreightPortfolioAllocator.allocate(
                topology(true, false),
                supply(Map.of(new SupplyKey(WATER, SUPPLIER_A), 100d)),
                START,
                List.of(requirement(WATER, 30d, 1_000d)),
                2,
                linearRoutes(Map.of(SUPPLIER_A, 10d), 100d));

        assertFalse(report.accepted());
        assertEquals(RequirementStatus.INSUFFICIENT_FREIGHT_CAPACITY,
                report.requirementPlans().get(0).status());
    }

    @Test
    void allocatedRouteCannotHideANonNeighborShortcut() {
        GalaxyTopology chain = topology(false, true);
        assertThrows(IllegalArgumentException.class, () -> Stage20FreightPortfolioAllocator.allocate(
                chain,
                supply(Map.of(new SupplyKey(WATER, SUPPLIER_B), 10d)),
                START,
                List.of(requirement(WATER, 5d, 1_000d)),
                1,
                (origin, destination, ships) -> Optional.of(
                        new RouteAssessment(List.of(origin, destination), 100d, 10d))));
    }

    @Test
    void routeCurveMustPreservePhysicalConcavityAndPrefixAllocation() {
        assertThrows(IllegalArgumentException.class, () -> Stage20FreightPortfolioAllocator.allocate(
                topology(true, false),
                supply(Map.of(new SupplyKey(WATER, SUPPLIER_A), 100d)),
                START,
                List.of(requirement(WATER, 5d, 1_000d)),
                2,
                (origin, destination, ships) -> Optional.of(new RouteAssessment(
                        List.of(origin, destination),
                        100d,
                        ships == 1 ? 2d : 5d))));
    }

    @Test
    void equalMarginalsChooseLowerSupplierIdDeterministically() {
        LinkedHashMap<SupplyKey, Double> firstOrder = new LinkedHashMap<>();
        firstOrder.put(new SupplyKey(WATER, SUPPLIER_B), 10d);
        firstOrder.put(new SupplyKey(WATER, SUPPLIER_A), 10d);
        LinkedHashMap<SupplyKey, Double> secondOrder = new LinkedHashMap<>();
        secondOrder.put(new SupplyKey(WATER, SUPPLIER_A), 10d);
        secondOrder.put(new SupplyKey(WATER, SUPPLIER_B), 10d);

        AllocationReport first = Stage20FreightPortfolioAllocator.allocate(
                topology(true, true), supply(firstOrder), START,
                List.of(requirement(WATER, 5d, 1_000d)), 1,
                linearRoutes(Map.of(SUPPLIER_A, 5d, SUPPLIER_B, 5d), 100d));
        AllocationReport second = Stage20FreightPortfolioAllocator.allocate(
                topology(true, true), supply(secondOrder), START,
                List.of(requirement(WATER, 5d, 1_000d)), 1,
                linearRoutes(Map.of(SUPPLIER_A, 5d, SUPPLIER_B, 5d), 100d));

        assertEquals(first, second);
        assertEquals(SUPPLIER_A,
                first.requirementPlans().get(0).remoteAllocations().get(0).supplierSystemId());
    }

    private static Stage20FreightPortfolioAllocator.AllocatedRouteEvaluator linearRoutes(
            Map<StarSystemId, Double> throughputPerFreighter,
            double travelTimeS) {
        return (origin, destination, ships) -> {
            Double oneShip = throughputPerFreighter.get(origin);
            if (oneShip == null) {
                return Optional.empty();
            }
            return Optional.of(new RouteAssessment(
                    List.of(origin, destination),
                    travelTimeS,
                    oneShip * ships));
        };
    }

    private static CommodityRequirement requirement(String commodityId, double rate, double maxTime) {
        return new CommodityRequirement(commodityId, maxTime, rate);
    }

    private static SupplyThroughputReport supply(Map<SupplyKey, Double> capacities) {
        return new SupplyThroughputReport("test.supply", capacities, Set.of(), List.of());
    }

    private static GalaxyTopology topology(boolean directA, boolean directB) {
        StarSystemNode start = new StarSystemNode(START, "Start", 0d, 0d);
        StarSystemNode supplierA = new StarSystemNode(SUPPLIER_A, "Supplier A", 1d, 0d);
        StarSystemNode supplierB = new StarSystemNode(SUPPLIER_B, "Supplier B", 2d, 0d);
        java.util.ArrayList<JumpConnection> edges = new java.util.ArrayList<>();
        if (directA) {
            edges.add(new JumpConnection(START, SUPPLIER_A));
        }
        if (directB) {
            edges.add(new JumpConnection(START, SUPPLIER_B));
        }
        if (!directB) {
            edges.add(new JumpConnection(SUPPLIER_A, SUPPLIER_B));
        }
        return new GalaxyTopology(
                new GalaxyId(1L),
                "allocator-test",
                List.of(new SectorNode(new SectorId(1L), "sector", List.of(start, supplierA, supplierB))),
                edges);
    }
}
