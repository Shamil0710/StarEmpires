package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Deterministic Stage-20E finite freight allocation for one ordinary faction-start system.
 *
 * <p>The allocator closes the specific accounting gap exposed by the representative portfolio
 * diagnostics: several physically reachable suppliers may contribute to one essential commodity,
 * but a finite freighter cannot be counted simultaneously on two supplier routes or for two
 * commodities. Local supply is credited from the existing system-level physical supply closure and
 * consumes no <em>inter-system</em> freighter. Remote supply consumes an integer number of ships from
 * one shared per-start budget.</p>
 *
 * <p>For every remote supplier the caller provides the same physical route calculation for one,
 * two, ... allocated freighters. The allocator requires the resulting cumulative throughput curve
 * to be monotone with non-increasing marginal capacity. That is the shape produced by the current
 * physical freight model ({@code min(k * payload / cycle, handling ceiling, producer ceiling)}) and
 * makes the deterministic largest-next-marginal allocation minimal in ship count for each commodity.
 * Requirements are then combined by summing those independent minimum ship counts. Therefore no
 * ship is silently reused between commodities.</p>
 *
 * <p>This class deliberately allocates only one start at a time. It does not claim that the same
 * producer capacity may simultaneously serve several faction starts. Whole-placement producer
 * reservation/ownership remains a later Stage-20E authority and must be resolved before this result
 * can become whole-seed economic acceptance.</p>
 */
public final class Stage20FreightPortfolioAllocator {
    /** Current deterministic allocator result version. */
    public static final String CURRENT_VERSION = "stage20e.freight-portfolio-allocator.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20FreightPortfolioAllocator() {
        throw new AssertionError("No instances");
    }

    /** Evaluates one physical supplier route for an explicit integer freighter allocation. */
    @FunctionalInterface
    public interface AllocatedRouteEvaluator {
        /**
         * Evaluates one remote route with the requested already-authorized ships.
         *
         * @param origin physical producer system
         * @param destination ordinary faction-start consumer system
         * @param allocatedFreighterCount positive number of ships assigned to this route
         * @return physical route assessment, or empty when the route is unavailable
         */
        Optional<RouteAssessment> assess(
                StarSystemId origin,
                StarSystemId destination,
                int allocatedFreighterCount);
    }

    /** Per-essential-commodity planning status before the shared start-fleet gate is applied. */
    public enum RequirementStatus {
        /** Local plus remote portfolio capacity can satisfy the requirement within the full budget. */ SATISFIED,
        /** Time-admitted local/remote producer capacity is below the requested service rate. */ INSUFFICIENT_ADMITTED_SUPPLY,
        /** Producer capacity exists, but the finite remote fleet cannot transport enough of it. */ INSUFFICIENT_FREIGHT_CAPACITY
    }

    /** Final single-start allocation failure. */
    public enum FailureReason {
        /** At least one commodity is individually impossible within the full per-start fleet budget. */ REQUIREMENT_UNSATISFIED,
        /** Every commodity is individually feasible, but their minimum ship counts exceed the shared pool. */ SHARED_FLEET_EXHAUSTED
    }

    /**
     * One remote supplier-route allocation selected for an essential commodity.
     *
     * @param commodityId authoritative commodity ID
     * @param supplierSystemId physical producer system
     * @param allocatedFreighters integer ships dedicated to this commodity/supplier route
     * @param supplierCapacityKgPerSecond non-reserved producer capacity before freight
     * @param deliveredCapacityKgPerSecond producer-capped route capacity from the allocated ships
     * @param route physical route assessment at the selected allocation count
     */
    public record RouteAllocation(
            String commodityId,
            StarSystemId supplierSystemId,
            int allocatedFreighters,
            double supplierCapacityKgPerSecond,
            double deliveredCapacityKgPerSecond,
            RouteAssessment route) {
        /**
         * Validates one immutable route allocation.
         *
         * @param commodityId authoritative commodity ID
         * @param supplierSystemId physical producer system
         * @param allocatedFreighters positive dedicated ship count
         * @param supplierCapacityKgPerSecond physical producer capacity
         * @param deliveredCapacityKgPerSecond producer-capped delivered capacity
         * @param route selected physical route
         */
        public RouteAllocation {
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(supplierSystemId, "supplierSystemId");
            if (allocatedFreighters <= 0) {
                throw new IllegalArgumentException("allocatedFreighters must be positive");
            }
            requirePositiveFinite(supplierCapacityKgPerSecond, "supplierCapacityKgPerSecond");
            requirePositiveFinite(deliveredCapacityKgPerSecond, "deliveredCapacityKgPerSecond");
            if (deliveredCapacityKgPerSecond > supplierCapacityKgPerSecond + EPSILON) {
                throw new IllegalArgumentException("delivered capacity cannot exceed supplier capacity");
            }
            Objects.requireNonNull(route, "route");
        }
    }

    /**
     * Minimum finite-fleet plan for one essential commodity.
     *
     * @param commodityId authoritative commodity ID
     * @param requiredKgPerSecond required service rate
     * @param localAvailableKgPerSecond local producer capacity visible in the supply closure
     * @param localDeliveredKgPerSecond local capacity credited without inter-system freighters
     * @param timeAdmittedRemoteProducerKgPerSecond remote producer capacity whose route satisfies time policy
     * @param remoteDeliveredCapacityKgPerSecond selected remote portfolio capacity
     * @param totalDeliveredCapacityKgPerSecond local plus selected remote capacity
     * @param minimumRemoteFreightersRequired minimum ships required by this commodity when feasible
     * @param status individual commodity planning result
     * @param remoteAllocations deterministic selected supplier allocations
     */
    public record RequirementPlan(
            String commodityId,
            double requiredKgPerSecond,
            double localAvailableKgPerSecond,
            double localDeliveredKgPerSecond,
            double timeAdmittedRemoteProducerKgPerSecond,
            double remoteDeliveredCapacityKgPerSecond,
            double totalDeliveredCapacityKgPerSecond,
            int minimumRemoteFreightersRequired,
            RequirementStatus status,
            List<RouteAllocation> remoteAllocations) {
        /**
         * Validates one immutable requirement plan.
         *
         * @param commodityId authoritative commodity ID
         * @param requiredKgPerSecond required service rate
         * @param localAvailableKgPerSecond local supply capacity
         * @param localDeliveredKgPerSecond credited local supply
         * @param timeAdmittedRemoteProducerKgPerSecond route-time-admitted remote producer capacity
         * @param remoteDeliveredCapacityKgPerSecond selected remote delivered capacity
         * @param totalDeliveredCapacityKgPerSecond total selected capacity
         * @param minimumRemoteFreightersRequired minimum remote ship count, zero for local-only or failure
         * @param status requirement status
         * @param remoteAllocations selected remote supplier allocations
         */
        public RequirementPlan {
            commodityId = requireText(commodityId, "commodityId");
            requirePositiveFinite(requiredKgPerSecond, "requiredKgPerSecond");
            requireNonNegativeFinite(localAvailableKgPerSecond, "localAvailableKgPerSecond");
            requireNonNegativeFinite(localDeliveredKgPerSecond, "localDeliveredKgPerSecond");
            requireNonNegativeFinite(timeAdmittedRemoteProducerKgPerSecond,
                    "timeAdmittedRemoteProducerKgPerSecond");
            requireNonNegativeFinite(remoteDeliveredCapacityKgPerSecond, "remoteDeliveredCapacityKgPerSecond");
            requireNonNegativeFinite(totalDeliveredCapacityKgPerSecond, "totalDeliveredCapacityKgPerSecond");
            if (localDeliveredKgPerSecond > localAvailableKgPerSecond + EPSILON) {
                throw new IllegalArgumentException("local delivered capacity cannot exceed local available capacity");
            }
            if (minimumRemoteFreightersRequired < 0) {
                throw new IllegalArgumentException("minimumRemoteFreightersRequired must be non-negative");
            }
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(remoteAllocations, "remoteAllocations");
            ArrayList<RouteAllocation> allocations = new ArrayList<>(remoteAllocations);
            if (allocations.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("remoteAllocations cannot contain null");
            }
            allocations.sort(Comparator.comparing(RouteAllocation::supplierSystemId));
            remoteAllocations = List.copyOf(allocations);
            int allocated = remoteAllocations.stream().mapToInt(RouteAllocation::allocatedFreighters).sum();
            if (status == RequirementStatus.SATISFIED) {
                if (totalDeliveredCapacityKgPerSecond + EPSILON < requiredKgPerSecond) {
                    throw new IllegalArgumentException("satisfied requirement must meet required throughput");
                }
                if (allocated != minimumRemoteFreightersRequired) {
                    throw new IllegalArgumentException("minimum ship count must equal selected route allocations");
                }
            } else if (minimumRemoteFreightersRequired != 0 || !remoteAllocations.isEmpty()) {
                throw new IllegalArgumentException("unsatisfied requirement cannot expose a committed allocation");
            }
        }
    }

    /**
     * Deterministic single-start finite-fleet allocation result.
     *
     * @param version allocator result version
     * @param startSystemId evaluated ordinary faction-start system
     * @param remoteFreighterBudget finite inter-system freighter pool available to this start
     * @param minimumRemoteFreightersRequired sum of individual minimum commodity allocations
     * @param accepted true only when every commodity is feasible and the shared pool suffices
     * @param failureReason absent only for accepted reports
     * @param requirementPlans deterministic commodity plans
     */
    public record AllocationReport(
            String version,
            StarSystemId startSystemId,
            int remoteFreighterBudget,
            int minimumRemoteFreightersRequired,
            boolean accepted,
            Optional<FailureReason> failureReason,
            List<RequirementPlan> requirementPlans) {
        /**
         * Validates one immutable allocation report.
         *
         * @param version allocator result version
         * @param startSystemId evaluated start system
         * @param remoteFreighterBudget finite inter-system fleet budget
         * @param minimumRemoteFreightersRequired minimum ships required across all essential commodities
         * @param accepted final single-start result
         * @param failureReason rejection reason, absent when accepted
         * @param requirementPlans deterministic commodity plans
         */
        public AllocationReport {
            version = requireText(version, "version");
            Objects.requireNonNull(startSystemId, "startSystemId");
            if (remoteFreighterBudget <= 0 || minimumRemoteFreightersRequired < 0) {
                throw new IllegalArgumentException("fleet counts must be valid");
            }
            Objects.requireNonNull(failureReason, "failureReason");
            Objects.requireNonNull(requirementPlans, "requirementPlans");
            ArrayList<RequirementPlan> plans = new ArrayList<>(requirementPlans);
            if (plans.isEmpty() || plans.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("requirementPlans must be non-empty and contain no nulls");
            }
            plans.sort(Comparator.comparing(RequirementPlan::commodityId));
            requirementPlans = List.copyOf(plans);
            if (accepted != failureReason.isEmpty()) {
                throw new IllegalArgumentException("accepted must equal failureReason.isEmpty()");
            }
            if (accepted && minimumRemoteFreightersRequired > remoteFreighterBudget) {
                throw new IllegalArgumentException("accepted allocation cannot exceed the shared fleet budget");
            }
        }
    }

    /**
     * Finds the minimum deterministic remote-freighter portfolio for one ordinary start.
     *
     * @param topology authoritative explicit-neighbor topology
     * @param supply non-reserved physical supply-throughput closure
     * @param startSystemId evaluated ordinary faction-start system
     * @param requirements essential commodity requirements
     * @param remoteFreighterBudget finite shared inter-system freighter pool for this start
     * @param routes physical route evaluator parameterized by allocated ship count
     * @return accepted minimum allocation or explicit deterministic rejection
     */
    public static AllocationReport allocate(
            GalaxyTopology topology,
            SupplyThroughputReport supply,
            StarSystemId startSystemId,
            List<CommodityRequirement> requirements,
            int remoteFreighterBudget,
            AllocatedRouteEvaluator routes) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        SupplyThroughputReport checkedSupply = Objects.requireNonNull(supply, "supply");
        StarSystemId checkedStart = Objects.requireNonNull(startSystemId, "startSystemId");
        AllocatedRouteEvaluator checkedRoutes = Objects.requireNonNull(routes, "routes");
        if (checkedTopology.findSystem(checkedStart).isEmpty()) {
            throw new IllegalArgumentException("start system is outside authoritative topology");
        }
        if (remoteFreighterBudget <= 0) {
            throw new IllegalArgumentException("remoteFreighterBudget must be positive");
        }
        List<CommodityRequirement> orderedRequirements = canonicalRequirements(requirements);

        ArrayList<RequirementPlan> plans = new ArrayList<>();
        boolean allSatisfied = true;
        int minimumShips = 0;
        for (CommodityRequirement requirement : orderedRequirements) {
            RequirementPlan plan = planRequirement(
                    checkedTopology,
                    checkedSupply,
                    checkedStart,
                    requirement,
                    remoteFreighterBudget,
                    checkedRoutes);
            plans.add(plan);
            if (plan.status() != RequirementStatus.SATISFIED) {
                allSatisfied = false;
            } else {
                minimumShips = checkedAdd(minimumShips, plan.minimumRemoteFreightersRequired());
            }
        }

        if (!allSatisfied) {
            return new AllocationReport(
                    CURRENT_VERSION,
                    checkedStart,
                    remoteFreighterBudget,
                    minimumShips,
                    false,
                    Optional.of(FailureReason.REQUIREMENT_UNSATISFIED),
                    plans);
        }
        if (minimumShips > remoteFreighterBudget) {
            return new AllocationReport(
                    CURRENT_VERSION,
                    checkedStart,
                    remoteFreighterBudget,
                    minimumShips,
                    false,
                    Optional.of(FailureReason.SHARED_FLEET_EXHAUSTED),
                    plans);
        }
        return new AllocationReport(
                CURRENT_VERSION,
                checkedStart,
                remoteFreighterBudget,
                minimumShips,
                true,
                Optional.empty(),
                plans);
    }

    private static RequirementPlan planRequirement(
            GalaxyTopology topology,
            SupplyThroughputReport supply,
            StarSystemId start,
            CommodityRequirement requirement,
            int budget,
            AllocatedRouteEvaluator routes) {
        double localAvailable = supply.capacityKgPerSecond(requirement.commodityId(), start);
        double localDelivered = Math.min(localAvailable, requirement.minSupplierThroughputKgPerSecond());
        double remaining = requirement.minSupplierThroughputKgPerSecond() - localDelivered;
        if (remaining <= EPSILON) {
            return satisfiedPlan(requirement, localAvailable, localDelivered, 0d, 0d, List.of());
        }

        ArrayList<SupplierCurve> curves = new ArrayList<>();
        double admittedRemoteProducer = 0d;
        for (Map.Entry<SupplyKey, Double> entry : supply.capacityKgPerSecondBySupply().entrySet()) {
            SupplyKey key = entry.getKey();
            if (!key.commodityId().equals(requirement.commodityId()) || key.systemId().equals(start)) {
                continue;
            }
            Optional<RouteAssessment> firstMaybe = routes.assess(key.systemId(), start, 1);
            if (firstMaybe.isEmpty()) {
                continue;
            }
            RouteAssessment first = validateRoute(topology, key.systemId(), start, firstMaybe.orElseThrow());
            if (first.travelTimeS() > requirement.maxSupplierRouteTimeS()) {
                continue;
            }
            admittedRemoteProducer = finiteAdd(admittedRemoteProducer, entry.getValue());
            SupplierCurve curve = buildCurve(
                    topology,
                    key,
                    entry.getValue(),
                    start,
                    budget,
                    requirement.maxSupplierRouteTimeS(),
                    first,
                    routes);
            if (!curve.marginals().isEmpty()) {
                curves.add(curve);
            }
        }
        curves.sort(Comparator.comparing(SupplierCurve::supplierSystemId));

        if (localAvailable + admittedRemoteProducer + EPSILON < requirement.minSupplierThroughputKgPerSecond()) {
            return failedPlan(
                    requirement,
                    localAvailable,
                    localDelivered,
                    admittedRemoteProducer,
                    RequirementStatus.INSUFFICIENT_ADMITTED_SUPPLY);
        }

        TreeMap<StarSystemId, Integer> selectedCounts = new TreeMap<>();
        HashMap<StarSystemId, Integer> nextIndex = new HashMap<>();
        double selectedRemoteCapacity = 0d;
        int usedShips = 0;
        while (selectedRemoteCapacity + EPSILON < remaining && usedShips < budget) {
            NextMarginal best = null;
            for (SupplierCurve curve : curves) {
                int index = nextIndex.getOrDefault(curve.supplierSystemId(), 0);
                if (index >= curve.marginals().size()) {
                    continue;
                }
                Marginal marginal = curve.marginals().get(index);
                NextMarginal candidate = new NextMarginal(curve, index, marginal);
                if (best == null || NEXT_ORDER.compare(candidate, best) < 0) {
                    best = candidate;
                }
            }
            if (best == null) {
                break;
            }
            SupplierCurve curve = best.curve();
            int index = best.index();
            Marginal marginal = best.marginal();
            nextIndex.put(curve.supplierSystemId(), index + 1);
            selectedCounts.merge(curve.supplierSystemId(), 1, Integer::sum);
            selectedRemoteCapacity = finiteAdd(selectedRemoteCapacity, marginal.incrementKgPerSecond());
            usedShips++;
        }

        if (selectedRemoteCapacity + EPSILON < remaining) {
            return failedPlan(
                    requirement,
                    localAvailable,
                    localDelivered,
                    admittedRemoteProducer,
                    RequirementStatus.INSUFFICIENT_FREIGHT_CAPACITY);
        }

        ArrayList<RouteAllocation> allocations = new ArrayList<>();
        for (SupplierCurve curve : curves) {
            int count = selectedCounts.getOrDefault(curve.supplierSystemId(), 0);
            if (count == 0) {
                continue;
            }
            Marginal selected = curve.marginals().get(count - 1);
            allocations.add(new RouteAllocation(
                    requirement.commodityId(),
                    curve.supplierSystemId(),
                    count,
                    curve.supplierCapacityKgPerSecond(),
                    selected.cumulativeDeliveredKgPerSecond(),
                    selected.route()));
        }
        double remoteCapacity = allocations.stream()
                .mapToDouble(RouteAllocation::deliveredCapacityKgPerSecond)
                .sum();
        return satisfiedPlan(
                requirement,
                localAvailable,
                localDelivered,
                admittedRemoteProducer,
                remoteCapacity,
                allocations);
    }

    private static SupplierCurve buildCurve(
            GalaxyTopology topology,
            SupplyKey key,
            double supplierCapacity,
            StarSystemId start,
            int budget,
            double maxRouteTime,
            RouteAssessment first,
            AllocatedRouteEvaluator routes) {
        ArrayList<Marginal> marginals = new ArrayList<>();
        double previousDelivered = 0d;
        double previousMarginal = Double.POSITIVE_INFINITY;
        RouteAssessment referenceRoute = first;
        for (int ships = 1; ships <= budget; ships++) {
            Optional<RouteAssessment> maybe = ships == 1
                    ? Optional.of(first)
                    : routes.assess(key.systemId(), start, ships);
            if (maybe.isEmpty()) {
                throw new IllegalArgumentException(
                        "allocated route became unavailable after a smaller allocation for supplier " + key.systemId());
            }
            RouteAssessment route = validateRoute(topology, key.systemId(), start, maybe.orElseThrow());
            if (!route.orderedSystems().equals(referenceRoute.orderedSystems())
                    || Math.abs(route.travelTimeS() - referenceRoute.travelTimeS()) > EPSILON) {
                throw new IllegalArgumentException("allocated freighter count cannot change route path or delivery time");
            }
            if (route.travelTimeS() > maxRouteTime) {
                throw new IllegalArgumentException("allocated route time changed outside the admitted requirement boundary");
            }
            double cumulative = Math.min(supplierCapacity, route.sustainableCargoThroughputKgPerSecond());
            if (cumulative + EPSILON < previousDelivered) {
                throw new IllegalArgumentException("allocated route throughput must be monotone");
            }
            double marginal = cumulative - previousDelivered;
            if (marginal <= EPSILON) {
                break;
            }
            if (marginal > previousMarginal + EPSILON) {
                throw new IllegalArgumentException("allocated route marginal throughput must be non-increasing");
            }
            marginals.add(new Marginal(ships, marginal, cumulative, route));
            previousDelivered = cumulative;
            previousMarginal = marginal;
        }
        return new SupplierCurve(key.systemId(), supplierCapacity, List.copyOf(marginals));
    }

    private static RequirementPlan satisfiedPlan(
            CommodityRequirement requirement,
            double localAvailable,
            double localDelivered,
            double admittedRemoteProducer,
            double remoteCapacity,
            List<RouteAllocation> allocations) {
        int ships = allocations.stream().mapToInt(RouteAllocation::allocatedFreighters).sum();
        double total = finiteAdd(localDelivered, remoteCapacity);
        return new RequirementPlan(
                requirement.commodityId(),
                requirement.minSupplierThroughputKgPerSecond(),
                localAvailable,
                localDelivered,
                admittedRemoteProducer,
                remoteCapacity,
                total,
                ships,
                RequirementStatus.SATISFIED,
                allocations);
    }

    private static RequirementPlan failedPlan(
            CommodityRequirement requirement,
            double localAvailable,
            double localDelivered,
            double admittedRemoteProducer,
            RequirementStatus status) {
        return new RequirementPlan(
                requirement.commodityId(),
                requirement.minSupplierThroughputKgPerSecond(),
                localAvailable,
                localDelivered,
                admittedRemoteProducer,
                0d,
                localDelivered,
                0,
                status,
                List.of());
    }

    private static List<CommodityRequirement> canonicalRequirements(List<CommodityRequirement> source) {
        Objects.requireNonNull(source, "requirements");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("requirements must not be empty");
        }
        ArrayList<CommodityRequirement> result = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        for (CommodityRequirement requirement : source) {
            CommodityRequirement checked = Objects.requireNonNull(requirement, "requirement");
            if (!ids.add(checked.commodityId())) {
                throw new IllegalArgumentException("duplicate requirement: " + checked.commodityId());
            }
            result.add(checked);
        }
        result.sort(Comparator.comparing(CommodityRequirement::commodityId));
        return List.copyOf(result);
    }

    private static RouteAssessment validateRoute(
            GalaxyTopology topology,
            StarSystemId origin,
            StarSystemId destination,
            RouteAssessment route) {
        List<StarSystemId> path = route.orderedSystems();
        if (!path.get(0).equals(origin) || !path.get(path.size() - 1).equals(destination)) {
            throw new IllegalArgumentException("route endpoints do not match allocation request");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("remote freight allocation cannot use a same-system route");
        }
        for (int index = 0; index < path.size() - 1; index++) {
            if (!topology.neighbors(path.get(index)).contains(path.get(index + 1))) {
                throw new IllegalArgumentException("allocated route contains a non-neighbor shortcut");
            }
        }
        return route;
    }

    private static int checkedAdd(int first, int second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("freighter count overflow", exception);
        }
    }

    private static double finiteAdd(double first, double second) {
        double result = first + second;
        if (!Double.isFinite(result)) {
            throw new IllegalStateException("throughput sum overflow");
        }
        return result;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private record Marginal(
            int freighterOrdinal,
            double incrementKgPerSecond,
            double cumulativeDeliveredKgPerSecond,
            RouteAssessment route) {
    }

    private record SupplierCurve(
            StarSystemId supplierSystemId,
            double supplierCapacityKgPerSecond,
            List<Marginal> marginals) {
    }

    private record NextMarginal(SupplierCurve curve, int index, Marginal marginal) {
    }

    private static final Comparator<NextMarginal> NEXT_ORDER = Comparator
            .comparingDouble((NextMarginal value) -> -value.marginal().incrementKgPerSecond())
            .thenComparing(value -> value.curve().supplierSystemId())
            .thenComparingInt(value -> value.marginal().freighterOrdinal());
}
