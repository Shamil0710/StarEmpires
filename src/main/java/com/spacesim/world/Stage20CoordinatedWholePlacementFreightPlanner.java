package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20FreightPortfolioAllocator.AllocatedRouteEvaluator;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic Stage-20E planner for a whole accepted faction-start placement under finite
 * per-start freight fleets and shared physical producer capacities.
 *
 * <p>The earlier single-start allocator proves that one start can satisfy its essential demand with
 * a finite freight pool, while whole-placement reservation proves whether an already selected set of
 * portfolios double-counts producer capacity. This planner closes the gap between those layers: it
 * may choose a different supplier mix for every placed start simultaneously.</p>
 *
 * <p>Integer freighter counts remain explicit search decisions. For every search state, actual kg/s
 * is assigned by a deterministic maximum-flow network whose source edges are capped by authoritative
 * {@link SupplyThroughputReport} producer capacities. Local and remote consumers therefore compete
 * for the same {@link SupplyKey}. Remote producer-to-demand arcs are capped by the real physical route
 * throughput at the currently allocated integer ship count. Local arcs consume no inter-system ship
 * but are not greedily reserved, allowing the global flow to divert a local producer to another start
 * when the local start can import an alternative supplier.</p>
 *
 * <p>The search adds only the next prefix freighter on a route and branches only over modifiable arcs
 * crossing the current residual minimum cut. Adding capacity outside that cut cannot increase the
 * current maximum flow, so this preserves completeness while avoiding the much larger Cartesian
 * product of all route allocations. The caller supplies a finite search-node budget. Exhausting that
 * budget is reported as unresolved authority and is never mislabeled as physical infeasibility.</p>
 *
 * <p>The planner does not create ships, stock, resources, topology edges, ownership or money. The
 * per-start freight count is a service-capacity bound supplied by the caller.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20CoordinatedWholePlacementFreightPlanner {
    /** Stable coordinated-planner version. */
    public static final String CURRENT_VERSION = "stage20e.coordinated-whole-placement-freight-planner.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20CoordinatedWholePlacementFreightPlanner() {
        throw new AssertionError("No instances");
    }

    /** Final bounded planning status. */
    public enum Status {
        /** A globally feasible physical supplier/freight allocation was found. */ ACCEPTED,
        /** The supplied physical state cannot satisfy the placement under the finite fleet bounds. */ INFEASIBLE,
        /** The caller-provided bounded search ended before feasibility or infeasibility was proved. */ UNRESOLVED_SEARCH_BUDGET
    }

    /** Causal failure or unresolved classification. */
    public enum FailureReason {
        /** At least one start is impossible even when it is analyzed independently. */ SINGLE_START_INFEASIBLE,
        /** Total authoritative producer capacity for an essential commodity is below whole-placement demand. */ GLOBAL_PRODUCER_CAPACITY_INSUFFICIENT,
        /** Exhaustive bounded-state exploration proved no coordinated route/fleet allocation exists. */ COORDINATED_ALLOCATION_INFEASIBLE,
        /** The caller-provided search-node budget was exhausted before a proof was reached. */ SEARCH_NODE_BUDGET_EXHAUSTED
    }

    /** One actual producer-to-demand service commitment in an accepted global plan. */
    public record SupplierCommitment(
            String commodityId,
            StarSystemId producerSystemId,
            boolean local,
            int allocatedFreighters,
            double deliveredKgPerSecond,
            Optional<RouteAssessment> route) {
        /**
         * Validates one supplier-to-demand commitment.
         *
         * @param commodityId stable commodity identifier
         * @param producerSystemId physical producer system
         * @param local whether producer and consumer are the same system
         * @param allocatedFreighters explicit remote freighters assigned to this commitment
         * @param deliveredKgPerSecond actual committed delivered throughput
         * @param route authoritative remote route, empty only for local service
         */
        public SupplierCommitment {
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(producerSystemId, "producerSystemId");
            if (allocatedFreighters < 0) {
                throw new IllegalArgumentException("allocatedFreighters must be non-negative");
            }
            requirePositiveFinite(deliveredKgPerSecond, "deliveredKgPerSecond");
            Objects.requireNonNull(route, "route");
            if (local != route.isEmpty()) {
                throw new IllegalArgumentException("local commitments must omit routes and remote commitments must expose one");
            }
            if (local != (allocatedFreighters == 0)) {
                throw new IllegalArgumentException("only local commitments may consume zero inter-system freighters");
            }
        }
    }

    /** Accepted service plan for one essential commodity at one placed start. */
    public record DemandPlan(
            String commodityId,
            double requiredKgPerSecond,
            double deliveredKgPerSecond,
            int remoteFreightersUsed,
            List<SupplierCommitment> commitments) {
        /**
         * Validates one accepted commodity-demand plan.
         *
         * @param commodityId stable commodity identifier
         * @param requiredKgPerSecond required bootstrap service throughput
         * @param deliveredKgPerSecond delivered throughput assigned by the global plan
         * @param remoteFreightersUsed explicit remote freighters consumed by this demand
         * @param commitments producer commitments that satisfy the demand
         */
        public DemandPlan {
            commodityId = requireText(commodityId, "commodityId");
            requirePositiveFinite(requiredKgPerSecond, "requiredKgPerSecond");
            requirePositiveFinite(deliveredKgPerSecond, "deliveredKgPerSecond");
            if (deliveredKgPerSecond + EPSILON < requiredKgPerSecond) {
                throw new IllegalArgumentException("accepted demand plan must satisfy required throughput");
            }
            if (remoteFreightersUsed < 0) {
                throw new IllegalArgumentException("remoteFreightersUsed must be non-negative");
            }
            ArrayList<SupplierCommitment> copy = new ArrayList<>(Objects.requireNonNull(commitments, "commitments"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("accepted demand plan must contain non-null commitments");
            }
            copy.sort(COMMITMENT_ORDER);
            commitments = List.copyOf(copy);
            int ships = commitments.stream().mapToInt(SupplierCommitment::allocatedFreighters).sum();
            if (ships != remoteFreightersUsed) {
                throw new IllegalArgumentException("demand remote ship count must equal commitment allocations");
            }
        }
    }

    /** Accepted coordinated plan for one placed faction start. */
    public record StartPlan(
            String stableFactionId,
            StarSystemId startSystemId,
            int remoteFreighterBudget,
            int remoteFreightersUsed,
            List<DemandPlan> demands) {
        /**
         * Validates one placed start's finite-fleet service plan.
         *
         * @param stableFactionId canonical faction identifier
         * @param startSystemId placed start system
         * @param remoteFreighterBudget finite remote-freighter capacity bound for the start
         * @param remoteFreightersUsed remote freighters actually used by accepted demands
         * @param demands accepted essential commodity plans for the start
         */
        public StartPlan {
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            Objects.requireNonNull(startSystemId, "startSystemId");
            if (remoteFreighterBudget <= 0 || remoteFreightersUsed < 0
                    || remoteFreightersUsed > remoteFreighterBudget) {
                throw new IllegalArgumentException("start freight counts must fit the positive budget");
            }
            ArrayList<DemandPlan> copy = new ArrayList<>(Objects.requireNonNull(demands, "demands"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("start demands must be non-empty and contain no nulls");
            }
            copy.sort(Comparator.comparing(DemandPlan::commodityId));
            demands = List.copyOf(copy);
            int ships = demands.stream().mapToInt(DemandPlan::remoteFreightersUsed).sum();
            if (ships != remoteFreightersUsed) {
                throw new IllegalArgumentException("start remote ship count must equal demand allocations");
            }
        }
    }

    /** Authoritative producer-capacity usage in an accepted global plan. */
    public record ProducerUsage(
            SupplyKey supplyKey,
            double capacityKgPerSecond,
            double reservedKgPerSecond) {
        /**
         * Validates one producer-capacity reservation summary.
         *
         * @param supplyKey authoritative commodity/producer key
         * @param capacityKgPerSecond physical producer capacity
         * @param reservedKgPerSecond producer throughput reserved by the accepted plan
         */
        public ProducerUsage {
            Objects.requireNonNull(supplyKey, "supplyKey");
            requirePositiveFinite(capacityKgPerSecond, "capacityKgPerSecond");
            requirePositiveFinite(reservedKgPerSecond, "reservedKgPerSecond");
            if (reservedKgPerSecond > capacityKgPerSecond + EPSILON) {
                throw new IllegalArgumentException("producer reservation cannot exceed authoritative capacity");
            }
        }
    }

    /** Complete deterministic coordinated planning result. */
    public record PlanReport(
            String version,
            String placementVersion,
            String supplyProfileVersion,
            int remoteFreighterBudgetPerStart,
            int searchNodeBudget,
            int searchNodesVisited,
            Status status,
            Optional<FailureReason> failureReason,
            int totalRemoteFreightersUsed,
            List<StartPlan> starts,
            List<ProducerUsage> producerUsage) {
        /**
         * Validates complete bounded coordinated-planning evidence.
         *
         * @param version planner version
         * @param placementVersion placement authority version
         * @param supplyProfileVersion physical supply profile version
         * @param remoteFreighterBudgetPerStart finite fleet bound applied independently to each start
         * @param searchNodeBudget caller-authorized discrete search-node budget
         * @param searchNodesVisited discrete allocation states actually inspected
         * @param status final planning status
         * @param failureReason explicit failure or unresolved reason when not accepted
         * @param totalRemoteFreightersUsed sum of remote freighters in accepted start plans
         * @param starts accepted per-start plans, empty for non-accepted outcomes
         * @param producerUsage accepted shared producer-capacity usage, empty for non-accepted outcomes
         */
        public PlanReport {
            version = requireText(version, "version");
            placementVersion = requireText(placementVersion, "placementVersion");
            supplyProfileVersion = requireText(supplyProfileVersion, "supplyProfileVersion");
            if (remoteFreighterBudgetPerStart <= 0 || searchNodeBudget <= 0
                    || searchNodesVisited < 0 || searchNodesVisited > searchNodeBudget
                    || totalRemoteFreightersUsed < 0) {
                throw new IllegalArgumentException("planner budgets/counts must be valid");
            }
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(failureReason, "failureReason");
            ArrayList<StartPlan> startCopy = new ArrayList<>(Objects.requireNonNull(starts, "starts"));
            ArrayList<ProducerUsage> producerCopy = new ArrayList<>(Objects.requireNonNull(producerUsage, "producerUsage"));
            if (startCopy.stream().anyMatch(Objects::isNull) || producerCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("planner evidence cannot contain nulls");
            }
            startCopy.sort(Comparator.comparing(StartPlan::stableFactionId));
            producerCopy.sort(Comparator.comparing(ProducerUsage::supplyKey));
            starts = List.copyOf(startCopy);
            producerUsage = List.copyOf(producerCopy);
            if (status == Status.ACCEPTED) {
                if (failureReason.isPresent() || starts.isEmpty() || producerUsage.isEmpty()) {
                    throw new IllegalArgumentException("accepted plan must expose starts/producer usage and no failure");
                }
                int ships = starts.stream().mapToInt(StartPlan::remoteFreightersUsed).sum();
                if (ships != totalRemoteFreightersUsed) {
                    throw new IllegalArgumentException("whole-plan ship count must equal start plans");
                }
            } else if (failureReason.isEmpty() || !starts.isEmpty() || !producerUsage.isEmpty()
                    || totalRemoteFreightersUsed != 0) {
                throw new IllegalArgumentException("non-accepted plans expose only explicit failure evidence");
            }
            if (status == Status.UNRESOLVED_SEARCH_BUDGET
                    != (failureReason.equals(Optional.of(FailureReason.SEARCH_NODE_BUDGET_EXHAUSTED)))) {
                throw new IllegalArgumentException("search-budget status/failure must agree");
            }
        }
    }

    /**
     * Finds a globally coordinated finite-freight plan for an accepted whole placement.
     *
     * @param topology authoritative explicit-neighbor topology
     * @param placement accepted faction-start placement
     * @param supply authoritative non-reserved physical producer capacities
     * @param requirements essential bootstrap service requirements
     * @param remoteFreighterBudgetPerStart finite inter-system service-capacity bound for each start
     * @param searchNodeBudget caller-authorized maximum number of discrete allocation states to inspect
     * @param routes physical route evaluator parameterized by an allocated integer ship count
     * @return deterministic accepted, infeasible or unresolved planning evidence
     */
    public static PlanReport plan(
            GalaxyTopology topology,
            PlacementResult placement,
            SupplyThroughputReport supply,
            List<CommodityRequirement> requirements,
            int remoteFreighterBudgetPerStart,
            int searchNodeBudget,
            AllocatedRouteEvaluator routes) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        PlacementResult checkedPlacement = requireAcceptedPlacement(placement);
        SupplyThroughputReport checkedSupply = Objects.requireNonNull(supply, "supply");
        List<CommodityRequirement> orderedRequirements = canonicalRequirements(requirements);
        AllocatedRouteEvaluator checkedRoutes = Objects.requireNonNull(routes, "routes");
        if (remoteFreighterBudgetPerStart <= 0 || searchNodeBudget <= 0) {
            throw new IllegalArgumentException("fleet/search budgets must be positive");
        }

        ArrayList<Assignment> assignments = new ArrayList<>(checkedPlacement.assignments());
        assignments.sort(Comparator.comparing(Assignment::stableFactionId));
        for (Assignment assignment : assignments) {
            if (checkedTopology.findSystem(assignment.systemId()).isEmpty()) {
                throw new IllegalArgumentException("placed start is outside authoritative topology");
            }
        }

        for (CommodityRequirement requirement : orderedRequirements) {
            double supplyTotal = checkedSupply.capacityKgPerSecondBySupply().entrySet().stream()
                    .filter(entry -> entry.getKey().commodityId().equals(requirement.commodityId()))
                    .mapToDouble(Map.Entry::getValue)
                    .sum();
            double demandTotal = finiteMultiply(
                    requirement.minSupplierThroughputKgPerSecond(), assignments.size());
            if (supplyTotal + EPSILON < demandTotal) {
                return failedReport(
                        checkedPlacement,
                        checkedSupply,
                        remoteFreighterBudgetPerStart,
                        searchNodeBudget,
                        0,
                        Status.INFEASIBLE,
                        FailureReason.GLOBAL_PRODUCER_CAPACITY_INSUFFICIENT);
            }
        }

        for (Assignment assignment : assignments) {
            var single = Stage20FreightPortfolioAllocator.allocate(
                    checkedTopology,
                    checkedSupply,
                    assignment.systemId(),
                    orderedRequirements,
                    remoteFreighterBudgetPerStart,
                    checkedRoutes);
            if (!single.accepted()) {
                return failedReport(
                        checkedPlacement,
                        checkedSupply,
                        remoteFreighterBudgetPerStart,
                        searchNodeBudget,
                        0,
                        Status.INFEASIBLE,
                        FailureReason.SINGLE_START_INFEASIBLE);
            }
        }

        PlanningModel model = buildModel(
                checkedTopology,
                checkedSupply,
                assignments,
                orderedRequirements,
                remoteFreighterBudgetPerStart,
                checkedRoutes);
        SearchContext search = new SearchContext(model, searchNodeBudget);
        int[] routeCounts = new int[model.routes().size()];
        int[] shipsByStart = new int[assignments.size()];
        SearchSolution solution = search.find(routeCounts, shipsByStart);
        if (solution != null) {
            return acceptedReport(
                    checkedPlacement,
                    checkedSupply,
                    remoteFreighterBudgetPerStart,
                    searchNodeBudget,
                    search.nodesVisited(),
                    model,
                    solution);
        }
        if (search.exhausted()) {
            return failedReport(
                    checkedPlacement,
                    checkedSupply,
                    remoteFreighterBudgetPerStart,
                    searchNodeBudget,
                    search.nodesVisited(),
                    Status.UNRESOLVED_SEARCH_BUDGET,
                    FailureReason.SEARCH_NODE_BUDGET_EXHAUSTED);
        }
        return failedReport(
                checkedPlacement,
                checkedSupply,
                remoteFreighterBudgetPerStart,
                searchNodeBudget,
                search.nodesVisited(),
                Status.INFEASIBLE,
                FailureReason.COORDINATED_ALLOCATION_INFEASIBLE);
    }

    private static PlanningModel buildModel(
            GalaxyTopology topology,
            SupplyThroughputReport supply,
            List<Assignment> assignments,
            List<CommodityRequirement> requirements,
            int perStartBudget,
            AllocatedRouteEvaluator routes) {
        ArrayList<Demand> demands = new ArrayList<>();
        for (int startIndex = 0; startIndex < assignments.size(); startIndex++) {
            Assignment assignment = assignments.get(startIndex);
            for (CommodityRequirement requirement : requirements) {
                demands.add(new Demand(
                        demands.size(),
                        startIndex,
                        assignment.stableFactionId(),
                        assignment.systemId(),
                        requirement));
            }
        }

        Set<String> requiredCommodityIds = new HashSet<>();
        requirements.forEach(value -> requiredCommodityIds.add(value.commodityId()));
        ArrayList<Producer> producers = new ArrayList<>();
        supply.capacityKgPerSecondBySupply().entrySet().stream()
                .filter(entry -> requiredCommodityIds.contains(entry.getKey().commodityId()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> producers.add(new Producer(
                        producers.size(), entry.getKey(), entry.getValue())));

        ArrayList<RouteCurve> curves = new ArrayList<>();
        for (Demand demand : demands) {
            for (Producer producer : producers) {
                if (!producer.key().commodityId().equals(demand.requirement().commodityId())
                        || producer.key().systemId().equals(demand.startSystemId())) {
                    continue;
                }
                Optional<RouteAssessment> firstMaybe = routes.assess(
                        producer.key().systemId(), demand.startSystemId(), 1);
                if (firstMaybe.isEmpty()) {
                    continue;
                }
                RouteAssessment first = validateRoute(
                        topology,
                        producer.key().systemId(),
                        demand.startSystemId(),
                        firstMaybe.orElseThrow());
                if (first.travelTimeS() > demand.requirement().maxSupplierRouteTimeS()) {
                    continue;
                }
                RouteCurve curve = buildCurve(
                        topology,
                        curves.size(),
                        producer,
                        demand,
                        perStartBudget,
                        first,
                        routes);
                if (!curve.points().isEmpty()) {
                    curves.add(curve);
                }
            }
        }
        curves.sort(ROUTE_ORDER);
        ArrayList<RouteCurve> reindexed = new ArrayList<>();
        for (int index = 0; index < curves.size(); index++) {
            RouteCurve curve = curves.get(index);
            reindexed.add(new RouteCurve(
                    index,
                    curve.producerIndex(),
                    curve.demandIndex(),
                    curve.startIndex(),
                    curve.commodityId(),
                    curve.supplierSystemId(),
                    curve.consumerSystemId(),
                    curve.points()));
        }
        return new PlanningModel(
                List.copyOf(assignments),
                List.copyOf(demands),
                List.copyOf(producers),
                List.copyOf(reindexed),
                perStartBudget);
    }

    private static RouteCurve buildCurve(
            GalaxyTopology topology,
            int initialIndex,
            Producer producer,
            Demand demand,
            int budget,
            RouteAssessment first,
            AllocatedRouteEvaluator routes) {
        ArrayList<RoutePoint> points = new ArrayList<>();
        double previousDelivered = 0d;
        double previousMarginal = Double.POSITIVE_INFINITY;
        RouteAssessment reference = first;
        for (int ships = 1; ships <= budget; ships++) {
            Optional<RouteAssessment> maybe = ships == 1
                    ? Optional.of(first)
                    : routes.assess(producer.key().systemId(), demand.startSystemId(), ships);
            if (maybe.isEmpty()) {
                throw new IllegalArgumentException("allocated route became unavailable after a smaller allocation");
            }
            RouteAssessment route = validateRoute(
                    topology,
                    producer.key().systemId(),
                    demand.startSystemId(),
                    maybe.orElseThrow());
            if (!route.orderedSystems().equals(reference.orderedSystems())
                    || Math.abs(route.travelTimeS() - reference.travelTimeS()) > EPSILON) {
                throw new IllegalArgumentException("allocated ship count cannot change route path or delivery time");
            }
            if (route.travelTimeS() > demand.requirement().maxSupplierRouteTimeS()) {
                throw new IllegalArgumentException("allocated route changed outside admitted service time");
            }
            double cumulative = Math.min(producer.capacityKgPerSecond(),
                    route.sustainableCargoThroughputKgPerSecond());
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
            points.add(new RoutePoint(ships, marginal, cumulative, route));
            previousDelivered = cumulative;
            previousMarginal = marginal;
        }
        return new RouteCurve(
                initialIndex,
                producer.index(),
                demand.index(),
                demand.startIndex(),
                demand.requirement().commodityId(),
                producer.key().systemId(),
                demand.startSystemId(),
                List.copyOf(points));
    }

    private static PlanReport acceptedReport(
            PlacementResult placement,
            SupplyThroughputReport supply,
            int perStartBudget,
            int searchNodeBudget,
            int searchNodesVisited,
            PlanningModel model,
            SearchSolution solution) {
        FlowResult flow = evaluateFlow(model, solution.routeCounts());
        if (flow.totalFlowKgPerSecond() + EPSILON < model.totalDemandKgPerSecond()) {
            throw new IllegalStateException("accepted search state no longer satisfies whole-placement demand");
        }

        ArrayList<ArrayList<SupplierCommitment>> commitmentsByDemand = new ArrayList<>();
        for (int ignored = 0; ignored < model.demands().size(); ignored++) {
            commitmentsByDemand.add(new ArrayList<>());
        }
        TreeMap<SupplyKey, Double> usedByProducer = new TreeMap<>();
        for (ArcFlow arc : flow.arcFlows()) {
            if (arc.flowKgPerSecond() <= EPSILON) {
                continue;
            }
            Demand demand = model.demands().get(arc.demandIndex());
            Producer producer = model.producers().get(arc.producerIndex());
            SupplierCommitment commitment;
            if (arc.local()) {
                commitment = new SupplierCommitment(
                        demand.requirement().commodityId(),
                        producer.key().systemId(),
                        true,
                        0,
                        arc.flowKgPerSecond(),
                        Optional.empty());
            } else {
                RouteCurve curve = model.routes().get(arc.routeIndex());
                int ships = minimumShipsForFlow(curve, arc.flowKgPerSecond());
                RoutePoint point = curve.points().get(ships - 1);
                commitment = new SupplierCommitment(
                        demand.requirement().commodityId(),
                        producer.key().systemId(),
                        false,
                        ships,
                        arc.flowKgPerSecond(),
                        Optional.of(point.route()));
            }
            commitmentsByDemand.get(demand.index()).add(commitment);
            usedByProducer.merge(producer.key(), arc.flowKgPerSecond(), Stage20CoordinatedWholePlacementFreightPlanner::finiteAdd);
        }

        ArrayList<StartPlan> starts = new ArrayList<>();
        int totalRemoteShips = 0;
        for (int startIndex = 0; startIndex < model.assignments().size(); startIndex++) {
            Assignment assignment = model.assignments().get(startIndex);
            ArrayList<DemandPlan> demandPlans = new ArrayList<>();
            int startShips = 0;
            for (Demand demand : model.demands()) {
                if (demand.startIndex() != startIndex) {
                    continue;
                }
                ArrayList<SupplierCommitment> commitments = commitmentsByDemand.get(demand.index());
                double delivered = commitments.stream().mapToDouble(SupplierCommitment::deliveredKgPerSecond).sum();
                int ships = commitments.stream().mapToInt(SupplierCommitment::allocatedFreighters).sum();
                startShips = Math.addExact(startShips, ships);
                demandPlans.add(new DemandPlan(
                        demand.requirement().commodityId(),
                        demand.requirement().minSupplierThroughputKgPerSecond(),
                        delivered,
                        ships,
                        commitments));
            }
            if (startShips > perStartBudget) {
                throw new IllegalStateException("trimmed accepted plan exceeds per-start freight budget");
            }
            totalRemoteShips = Math.addExact(totalRemoteShips, startShips);
            starts.add(new StartPlan(
                    assignment.stableFactionId(),
                    assignment.systemId(),
                    perStartBudget,
                    startShips,
                    demandPlans));
        }

        ArrayList<ProducerUsage> producerUsage = new ArrayList<>();
        for (Map.Entry<SupplyKey, Double> entry : usedByProducer.entrySet()) {
            double capacity = supply.capacityKgPerSecondBySupply().getOrDefault(entry.getKey(), 0d);
            producerUsage.add(new ProducerUsage(entry.getKey(), capacity, entry.getValue()));
        }
        return new PlanReport(
                CURRENT_VERSION,
                placement.version(),
                supply.profileVersion(),
                perStartBudget,
                searchNodeBudget,
                searchNodesVisited,
                Status.ACCEPTED,
                Optional.empty(),
                totalRemoteShips,
                starts,
                producerUsage);
    }

    private static int minimumShipsForFlow(RouteCurve curve, double flow) {
        for (RoutePoint point : curve.points()) {
            if (point.cumulativeDeliveredKgPerSecond() + EPSILON >= flow) {
                return point.freighters();
            }
        }
        throw new IllegalStateException("accepted route flow exceeds allocated route curve");
    }

    private static PlanReport failedReport(
            PlacementResult placement,
            SupplyThroughputReport supply,
            int perStartBudget,
            int searchNodeBudget,
            int nodesVisited,
            Status status,
            FailureReason reason) {
        return new PlanReport(
                CURRENT_VERSION,
                placement.version(),
                supply.profileVersion(),
                perStartBudget,
                searchNodeBudget,
                nodesVisited,
                status,
                Optional.of(reason),
                0,
                List.of(),
                List.of());
    }

    private static FlowResult evaluateFlow(PlanningModel model, int[] routeCounts) {
        int producerCount = model.producers().size();
        int demandCount = model.demands().size();
        int source = 0;
        int producerBase = 1;
        int demandBase = producerBase + producerCount;
        int sink = demandBase + demandCount;
        FlowNetwork network = new FlowNetwork(sink + 1);

        for (Producer producer : model.producers()) {
            network.addEdge(source, producerBase + producer.index(), producer.capacityKgPerSecond());
        }

        ArrayList<ArcBinding> bindings = new ArrayList<>();
        for (Producer producer : model.producers()) {
            for (Demand demand : model.demands()) {
                if (!producer.key().commodityId().equals(demand.requirement().commodityId())
                        || !producer.key().systemId().equals(demand.startSystemId())) {
                    continue;
                }
                EdgeRef edge = network.addEdge(
                        producerBase + producer.index(),
                        demandBase + demand.index(),
                        producer.capacityKgPerSecond());
                bindings.add(new ArcBinding(
                        producer.index(), demand.index(), -1, true, edge));
            }
        }

        for (RouteCurve curve : model.routes()) {
            int count = routeCounts[curve.index()];
            if (count <= 0) {
                continue;
            }
            if (count > curve.points().size()) {
                throw new IllegalArgumentException("route count exceeds available physical prefix curve");
            }
            double capacity = curve.points().get(count - 1).cumulativeDeliveredKgPerSecond();
            EdgeRef edge = network.addEdge(
                    producerBase + curve.producerIndex(),
                    demandBase + curve.demandIndex(),
                    capacity);
            bindings.add(new ArcBinding(
                    curve.producerIndex(), curve.demandIndex(), curve.index(), false, edge));
        }

        ArrayList<EdgeRef> demandEdges = new ArrayList<>();
        for (Demand demand : model.demands()) {
            demandEdges.add(network.addEdge(
                    demandBase + demand.index(),
                    sink,
                    demand.requirement().minSupplierThroughputKgPerSecond()));
        }

        double totalFlow = network.maxFlow(source, sink);
        boolean[] reachable = network.reachableFrom(source);
        ArrayList<ArcFlow> arcFlows = new ArrayList<>();
        for (ArcBinding binding : bindings) {
            double flow = network.flow(binding.edge());
            if (flow > EPSILON) {
                arcFlows.add(new ArcFlow(
                        binding.producerIndex(),
                        binding.demandIndex(),
                        binding.routeIndex(),
                        binding.local(),
                        flow));
            }
        }
        double[] deliveredByDemand = new double[demandCount];
        for (int demandIndex = 0; demandIndex < demandCount; demandIndex++) {
            deliveredByDemand[demandIndex] = network.flow(demandEdges.get(demandIndex));
        }
        return new FlowResult(
                totalFlow,
                producerBase,
                demandBase,
                reachable,
                List.copyOf(arcFlows),
                deliveredByDemand);
    }

    private static PlacementResult requireAcceptedPlacement(PlacementResult placement) {
        PlacementResult checked = Objects.requireNonNull(placement, "placement");
        if (checked.status() != PlacementStatus.ACCEPTED || checked.assignments().isEmpty()) {
            throw new IllegalArgumentException("coordinated planner requires an accepted non-empty placement");
        }
        return checked;
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
        Objects.requireNonNull(route, "route");
        List<StarSystemId> path = route.orderedSystems();
        if (!path.get(0).equals(origin) || !path.get(path.size() - 1).equals(destination)) {
            throw new IllegalArgumentException("route endpoints do not match coordinated allocation request");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("remote coordinated allocation cannot use a same-system route");
        }
        for (int index = 0; index < path.size() - 1; index++) {
            if (!topology.neighbors(path.get(index)).contains(path.get(index + 1))) {
                throw new IllegalArgumentException("coordinated route contains a non-neighbor shortcut");
            }
        }
        return route;
    }

    private static double finiteAdd(double first, double second) {
        double value = first + second;
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("throughput sum overflow");
        }
        return value;
    }

    private static double finiteMultiply(double first, int second) {
        double value = first * second;
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("throughput product overflow");
        }
        return value;
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

    private static final Comparator<SupplierCommitment> COMMITMENT_ORDER = Comparator
            .comparing(SupplierCommitment::commodityId)
            .thenComparing(SupplierCommitment::producerSystemId)
            .thenComparing(SupplierCommitment::local)
            .thenComparingInt(SupplierCommitment::allocatedFreighters);

    private static final Comparator<RouteCurve> ROUTE_ORDER = Comparator
            .comparingInt(RouteCurve::startIndex)
            .thenComparing(RouteCurve::commodityId)
            .thenComparing(RouteCurve::supplierSystemId)
            .thenComparing(RouteCurve::consumerSystemId);

    private record Demand(
            int index,
            int startIndex,
            String stableFactionId,
            StarSystemId startSystemId,
            CommodityRequirement requirement) {
    }

    private record Producer(int index, SupplyKey key, double capacityKgPerSecond) {
    }

    private record RoutePoint(
            int freighters,
            double marginalKgPerSecond,
            double cumulativeDeliveredKgPerSecond,
            RouteAssessment route) {
    }

    private record RouteCurve(
            int index,
            int producerIndex,
            int demandIndex,
            int startIndex,
            String commodityId,
            StarSystemId supplierSystemId,
            StarSystemId consumerSystemId,
            List<RoutePoint> points) {
        double nextMarginal(int currentCount) {
            return points.get(currentCount).marginalKgPerSecond();
        }
    }

    private record PlanningModel(
            List<Assignment> assignments,
            List<Demand> demands,
            List<Producer> producers,
            List<RouteCurve> routes,
            int perStartBudget) {
        double totalDemandKgPerSecond() {
            return demands.stream()
                    .mapToDouble(value -> value.requirement().minSupplierThroughputKgPerSecond())
                    .sum();
        }
    }

    private record SearchSolution(int[] routeCounts) {
        SearchSolution {
            routeCounts = routeCounts.clone();
        }
    }

    private static final class SearchContext {
        private final PlanningModel model;
        private final int nodeBudget;
        private final Set<StateKey> failedStates = new HashSet<>();
        private int nodesVisited;
        private boolean exhausted;

        private SearchContext(PlanningModel model, int nodeBudget) {
            this.model = model;
            this.nodeBudget = nodeBudget;
        }

        private int nodesVisited() {
            return nodesVisited;
        }

        private boolean exhausted() {
            return exhausted;
        }

        private SearchSolution find(int[] routeCounts, int[] shipsByStart) {
            StateKey key = new StateKey(routeCounts);
            if (failedStates.contains(key)) {
                return null;
            }
            if (nodesVisited >= nodeBudget) {
                exhausted = true;
                return null;
            }
            nodesVisited++;

            FlowResult flow = evaluateFlow(model, routeCounts);
            if (flow.totalFlowKgPerSecond() + EPSILON >= model.totalDemandKgPerSecond()) {
                return new SearchSolution(routeCounts);
            }

            ArrayList<RouteCurve> candidates = new ArrayList<>();
            for (RouteCurve curve : model.routes()) {
                int count = routeCounts[curve.index()];
                if (count >= curve.points().size() || shipsByStart[curve.startIndex()] >= model.perStartBudget()) {
                    continue;
                }
                int producerNode = flow.producerBase() + curve.producerIndex();
                int demandNode = flow.demandBase() + curve.demandIndex();
                if (flow.reachable()[producerNode] && !flow.reachable()[demandNode]) {
                    candidates.add(curve);
                }
            }
            candidates.sort(Comparator
                    .comparingDouble((RouteCurve value) -> -value.nextMarginal(routeCounts[value.index()]))
                    .thenComparingInt(RouteCurve::startIndex)
                    .thenComparing(RouteCurve::commodityId)
                    .thenComparing(RouteCurve::supplierSystemId)
                    .thenComparing(RouteCurve::consumerSystemId));

            for (RouteCurve candidate : candidates) {
                if (exhausted) {
                    return null;
                }
                int routeIndex = candidate.index();
                int startIndex = candidate.startIndex();
                routeCounts[routeIndex]++;
                shipsByStart[startIndex]++;
                SearchSolution solution = find(routeCounts, shipsByStart);
                routeCounts[routeIndex]--;
                shipsByStart[startIndex]--;
                if (solution != null) {
                    return solution;
                }
            }
            failedStates.add(key);
            return null;
        }
    }

    private static final class StateKey {
        private final int[] counts;
        private final int hash;

        private StateKey(int[] counts) {
            this.counts = counts.clone();
            this.hash = Arrays.hashCode(this.counts);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof StateKey value && Arrays.equals(counts, value.counts);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private record FlowResult(
            double totalFlowKgPerSecond,
            int producerBase,
            int demandBase,
            boolean[] reachable,
            List<ArcFlow> arcFlows,
            double[] deliveredByDemand) {
    }

    private record ArcFlow(
            int producerIndex,
            int demandIndex,
            int routeIndex,
            boolean local,
            double flowKgPerSecond) {
    }

    private record ArcBinding(
            int producerIndex,
            int demandIndex,
            int routeIndex,
            boolean local,
            EdgeRef edge) {
    }

    private record EdgeRef(int from, int edgeIndex, double initialCapacity) {
    }

    private static final class FlowNetwork {
        private final ArrayList<ArrayList<Edge>> adjacency;

        private FlowNetwork(int nodeCount) {
            adjacency = new ArrayList<>(nodeCount);
            for (int index = 0; index < nodeCount; index++) {
                adjacency.add(new ArrayList<>());
            }
        }

        private EdgeRef addEdge(int from, int to, double capacity) {
            if (!Double.isFinite(capacity) || capacity < 0d) {
                throw new IllegalArgumentException("flow capacity must be finite and non-negative");
            }
            int forwardIndex = adjacency.get(from).size();
            int reverseIndex = adjacency.get(to).size();
            adjacency.get(from).add(new Edge(to, reverseIndex, capacity));
            adjacency.get(to).add(new Edge(from, forwardIndex, 0d));
            return new EdgeRef(from, forwardIndex, capacity);
        }

        private double maxFlow(int source, int sink) {
            double total = 0d;
            while (true) {
                int[] parentNode = new int[adjacency.size()];
                int[] parentEdge = new int[adjacency.size()];
                Arrays.fill(parentNode, -1);
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                parentNode[source] = source;
                queue.addLast(source);
                while (!queue.isEmpty() && parentNode[sink] < 0) {
                    int node = queue.removeFirst();
                    ArrayList<Edge> edges = adjacency.get(node);
                    for (int edgeIndex = 0; edgeIndex < edges.size(); edgeIndex++) {
                        Edge edge = edges.get(edgeIndex);
                        if (edge.residual <= EPSILON || parentNode[edge.to] >= 0) {
                            continue;
                        }
                        parentNode[edge.to] = node;
                        parentEdge[edge.to] = edgeIndex;
                        queue.addLast(edge.to);
                        if (edge.to == sink) {
                            break;
                        }
                    }
                }
                if (parentNode[sink] < 0) {
                    return total;
                }
                double augment = Double.POSITIVE_INFINITY;
                for (int node = sink; node != source; node = parentNode[node]) {
                    Edge edge = adjacency.get(parentNode[node]).get(parentEdge[node]);
                    augment = Math.min(augment, edge.residual);
                }
                if (!Double.isFinite(augment) || augment <= EPSILON) {
                    throw new IllegalStateException("invalid positive augmenting flow");
                }
                for (int node = sink; node != source; node = parentNode[node]) {
                    int previous = parentNode[node];
                    Edge edge = adjacency.get(previous).get(parentEdge[node]);
                    edge.residual -= augment;
                    Edge reverse = adjacency.get(edge.to).get(edge.reverseIndex);
                    reverse.residual += augment;
                }
                total = finiteAdd(total, augment);
            }
        }

        private boolean[] reachableFrom(int source) {
            boolean[] reachable = new boolean[adjacency.size()];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            reachable[source] = true;
            queue.addLast(source);
            while (!queue.isEmpty()) {
                int node = queue.removeFirst();
                for (Edge edge : adjacency.get(node)) {
                    if (edge.residual > EPSILON && !reachable[edge.to]) {
                        reachable[edge.to] = true;
                        queue.addLast(edge.to);
                    }
                }
            }
            return reachable;
        }

        private double flow(EdgeRef ref) {
            Edge edge = adjacency.get(ref.from()).get(ref.edgeIndex());
            double value = ref.initialCapacity() - edge.residual;
            return value <= EPSILON ? 0d : value;
        }
    }

    private static final class Edge {
        private final int to;
        private final int reverseIndex;
        private double residual;

        private Edge(int to, int reverseIndex, double residual) {
            this.to = to;
            this.reverseIndex = reverseIndex;
            this.residual = residual;
        }
    }
}
