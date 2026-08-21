package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CommodityFrontier;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CommodityOption;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.DemandPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.ProducerUsage;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.StartPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.SupplierCommitment;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bounded deterministic Stage-20E generator for one commodity's whole-placement freight frontier.
 *
 * <p>The generator preserves the same physical route, producer-capacity, local-service and integer
 * freighter semantics as the coordinated whole-placement planner, but isolates one commodity at a
 * time. This removes cross-commodity fleet coupling from the search. The remaining coupling inside
 * one commodity is authoritative producer capacity shared between all placed faction starts.</p>
 *
 * <p>Frontier completeness is proved by solving every per-start freight-cap vector from zero through
 * the caller-supplied physical fleet budget. Cap vectors are visited by increasing total ships and
 * then lexicographically by canonical faction order. A nondominated feasible usage vector must be
 * rediscovered when its own vector is used as the cap; if a different solution used no more ships at
 * every start, the original vector would be dominated. Therefore complete resolution of all cap
 * vectors proves the returned nondominated frontier complete without enumerating the Cartesian
 * product of water and ore route-prefix decisions together.</p>
 *
 * <p>The caller supplies one shared search-node budget across all cap-vector solves. If that budget is
 * exhausted, already discovered physical options are retained and the frontier is explicitly marked
 * {@link FrontierStatus#UNRESOLVED_SEARCH_BUDGET}. Search incompleteness is never converted into
 * physical infeasibility.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20CommodityWholePlacementFrontierGenerator {
    /** Stable frontier-generator version. */
    public static final String CURRENT_VERSION = "stage20e.commodity-whole-placement-frontier-generator.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20CommodityWholePlacementFrontierGenerator() {
        throw new AssertionError("No instances");
    }

    /**
     * One physically reconstructed nondominated whole-placement option for the commodity.
     *
     * @param optionId deterministic option identifier derived from the canonical ship vector
     * @param commodityId stable commodity identifier
     * @param remoteFreightersByFaction exact remote freighters used at every placed start
     * @param starts complete physical service commitments for every placed start
     * @param producerUsage authoritative shared producer-capacity reservations
     */
    public record FrontierOption(
            String optionId,
            String commodityId,
            Map<String, Integer> remoteFreightersByFaction,
            List<StartPlan> starts,
            List<ProducerUsage> producerUsage) {
        /**
         * Validates and canonicalizes one physical frontier option.
         *
         * @param optionId deterministic option identifier derived from the canonical ship vector
         * @param commodityId stable commodity identifier
         * @param remoteFreightersByFaction exact remote freighters used at every placed start
         * @param starts complete physical service commitments for every placed start
         * @param producerUsage authoritative shared producer-capacity reservations
         */
        public FrontierOption {
            optionId = requireText(optionId, "optionId");
            commodityId = requireText(commodityId, "commodityId");
            remoteFreightersByFaction = canonicalPositiveKeyNonNegativeValueMap(
                    remoteFreightersByFaction,
                    "remoteFreightersByFaction");
            ArrayList<StartPlan> startCopy = new ArrayList<>(Objects.requireNonNull(starts, "starts"));
            ArrayList<ProducerUsage> producerCopy = new ArrayList<>(
                    Objects.requireNonNull(producerUsage, "producerUsage"));
            if (startCopy.isEmpty()
                    || producerCopy.isEmpty()
                    || startCopy.stream().anyMatch(Objects::isNull)
                    || producerCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("frontier option must expose non-empty physical evidence");
            }
            startCopy.sort(Comparator.comparing(StartPlan::stableFactionId));
            producerCopy.sort(Comparator.comparing(ProducerUsage::supplyKey));
            starts = List.copyOf(startCopy);
            producerUsage = List.copyOf(producerCopy);

            TreeMap<String, Integer> usageFromStarts = new TreeMap<>();
            for (StartPlan start : starts) {
                if (usageFromStarts.put(start.stableFactionId(), start.remoteFreightersUsed()) != null) {
                    throw new IllegalArgumentException("frontier option start plans must be unique by faction");
                }
                if (start.demands().size() != 1
                        || !start.demands().get(0).commodityId().equals(commodityId)) {
                    throw new IllegalArgumentException("frontier option must contain exactly one matching demand per start");
                }
            }
            if (!usageFromStarts.equals(remoteFreightersByFaction)) {
                throw new IllegalArgumentException("frontier option ship vector must equal reconstructed start usage");
            }
        }

        /**
         * Projects this rich physical option to the ship-vector contract consumed by the exact combiner.
         *
         * @return exact combiner option preserving the stable identifier and usage vector
         */
        public CommodityOption toCombinerOption() {
            return new CommodityOption(optionId, commodityId, remoteFreightersByFaction);
        }
    }

    /**
     * Complete bounded generation evidence for one commodity.
     *
     * @param version frontier-generator version
     * @param placementVersion accepted faction-placement authority version
     * @param supplyProfileVersion authoritative supply-profile version
     * @param commodityId generated commodity identifier
     * @param searchNodeBudget caller-authorized shared search-node budget
     * @param searchNodesVisited total discrete route-prefix states inspected across cap vectors
     * @param status complete or unresolved frontier status
     * @param remoteFreighterBudgetByFaction authoritative maximum remote freight fleet at each start
     * @param options known nondominated physically valid options
     */
    public record FrontierReport(
            String version,
            String placementVersion,
            String supplyProfileVersion,
            String commodityId,
            int searchNodeBudget,
            int searchNodesVisited,
            FrontierStatus status,
            Map<String, Integer> remoteFreighterBudgetByFaction,
            List<FrontierOption> options) {
        /**
         * Validates and canonicalizes one bounded frontier report.
         *
         * @param version frontier-generator version
         * @param placementVersion accepted faction-placement authority version
         * @param supplyProfileVersion authoritative supply-profile version
         * @param commodityId generated commodity identifier
         * @param searchNodeBudget caller-authorized shared search-node budget
         * @param searchNodesVisited total discrete route-prefix states inspected across cap vectors
         * @param status complete or unresolved frontier status
         * @param remoteFreighterBudgetByFaction authoritative maximum remote freight fleet at each start
         * @param options known nondominated physically valid options
         */
        public FrontierReport {
            version = requireText(version, "version");
            placementVersion = requireText(placementVersion, "placementVersion");
            supplyProfileVersion = requireText(supplyProfileVersion, "supplyProfileVersion");
            commodityId = requireText(commodityId, "commodityId");
            if (searchNodeBudget <= 0 || searchNodesVisited < 0 || searchNodesVisited > searchNodeBudget) {
                throw new IllegalArgumentException("frontier search budgets/counts must be valid");
            }
            Objects.requireNonNull(status, "status");
            remoteFreighterBudgetByFaction = Collections.unmodifiableMap(canonicalPositiveMap(
                    remoteFreighterBudgetByFaction,
                    "remoteFreighterBudgetByFaction"));

            ArrayList<FrontierOption> optionCopy = new ArrayList<>(Objects.requireNonNull(options, "options"));
            if (optionCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("frontier options cannot contain nulls");
            }
            optionCopy.sort(FRONTIER_OPTION_ORDER);
            Set<String> ids = new HashSet<>();
            for (FrontierOption option : optionCopy) {
                if (!commodityId.equals(option.commodityId())
                        || !option.remoteFreightersByFaction().keySet().equals(remoteFreighterBudgetByFaction.keySet())
                        || !ids.add(option.optionId())) {
                    throw new IllegalArgumentException("frontier options must match commodity/faction set and have unique IDs");
                }
                for (Map.Entry<String, Integer> entry : option.remoteFreightersByFaction().entrySet()) {
                    if (entry.getValue() > remoteFreighterBudgetByFaction.get(entry.getKey())) {
                        throw new IllegalArgumentException("frontier option cannot exceed authoritative fleet budget");
                    }
                }
            }
            ensureNondominated(optionCopy);
            options = List.copyOf(optionCopy);
        }

        /**
         * Projects this report into the exact combiner's upstream frontier contract.
         *
         * @return combiner-ready frontier preserving completeness semantics
         */
        public CommodityFrontier toCombinerFrontier() {
            return new CommodityFrontier(
                    commodityId,
                    version,
                    status,
                    options.stream().map(FrontierOption::toCombinerOption).toList());
        }
    }

    /**
     * Generates one commodity's nondominated whole-placement physical freight frontier.
     *
     * @param topology authoritative explicit-neighbor topology
     * @param placement accepted non-empty faction-start placement
     * @param supply authoritative physical producer capacities
     * @param requirement one essential commodity bootstrap requirement
     * @param remoteFreighterBudgetByFaction finite physical remote-freighter budget for every start
     * @param searchNodeBudget shared bound across all per-cap-vector route-prefix searches
     * @param routes authoritative route evaluator parameterized by an allocated integer ship count
     * @return complete or explicitly unresolved physical commodity frontier evidence
     */
    public static FrontierReport generate(
            GalaxyTopology topology,
            PlacementResult placement,
            SupplyThroughputReport supply,
            CommodityRequirement requirement,
            Map<String, Integer> remoteFreighterBudgetByFaction,
            int searchNodeBudget,
            AllocatedRouteEvaluator routes) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        PlacementResult checkedPlacement = requireAcceptedPlacement(placement);
        SupplyThroughputReport checkedSupply = Objects.requireNonNull(supply, "supply");
        CommodityRequirement checkedRequirement = Objects.requireNonNull(requirement, "requirement");
        AllocatedRouteEvaluator checkedRoutes = Objects.requireNonNull(routes, "routes");
        if (searchNodeBudget <= 0) {
            throw new IllegalArgumentException("searchNodeBudget must be positive");
        }

        ArrayList<Assignment> assignments = new ArrayList<>(checkedPlacement.assignments());
        assignments.sort(Comparator.comparing(Assignment::stableFactionId));
        TreeMap<String, Integer> budgets = validateBudgets(
                assignments,
                remoteFreighterBudgetByFaction);
        for (Assignment assignment : assignments) {
            if (checkedTopology.findSystem(assignment.systemId()).isEmpty()) {
                throw new IllegalArgumentException("placed start is outside authoritative topology");
            }
        }

        double supplyTotal = checkedSupply.capacityKgPerSecondBySupply().entrySet().stream()
                .filter(entry -> entry.getKey().commodityId().equals(checkedRequirement.commodityId()))
                .mapToDouble(Map.Entry::getValue)
                .sum();
        double demandTotal = finiteMultiply(
                checkedRequirement.minSupplierThroughputKgPerSecond(),
                assignments.size());
        if (supplyTotal + EPSILON < demandTotal) {
            return report(
                    checkedPlacement,
                    checkedSupply,
                    checkedRequirement,
                    budgets,
                    searchNodeBudget,
                    0,
                    FrontierStatus.COMPLETE,
                    List.of());
        }

        for (Assignment assignment : assignments) {
            int budget = budgets.get(assignment.stableFactionId());
            var single = Stage20FreightPortfolioAllocator.allocate(
                    checkedTopology,
                    checkedSupply,
                    assignment.systemId(),
                    List.of(checkedRequirement),
                    budget,
                    checkedRoutes);
            if (!single.accepted()) {
                return report(
                        checkedPlacement,
                        checkedSupply,
                        checkedRequirement,
                        budgets,
                        searchNodeBudget,
                        0,
                        FrontierStatus.COMPLETE,
                        List.of());
            }
        }

        PlanningModel model = buildModel(
                checkedTopology,
                checkedSupply,
                assignments,
                checkedRequirement,
                budgets,
                checkedRoutes);

        List<ShipVector> capVectors = capVectors(model.maxShipsByStart());
        LinkedHashMap<ShipVector, FrontierOption> knownByUsage = new LinkedHashMap<>();
        int totalNodesVisited = 0;
        FrontierStatus finalStatus = FrontierStatus.COMPLETE;

        for (ShipVector capVector : capVectors) {
            if (totalNodesVisited >= searchNodeBudget) {
                finalStatus = FrontierStatus.UNRESOLVED_SEARCH_BUDGET;
                break;
            }

            int remainingBudget = searchNodeBudget - totalNodesVisited;
            SearchContext search = new SearchContext(model, capVector.toIntArray(), remainingBudget);
            SearchSolution solution = search.find(
                    new int[model.routes().size()],
                    new int[model.assignments().size()]);
            totalNodesVisited = Math.addExact(totalNodesVisited, search.nodesVisited());

            if (solution != null) {
                FrontierOption option = optionFromSolution(
                        model,
                        checkedSupply,
                        budgets,
                        solution);
                ShipVector usage = vectorForOption(option, model.assignments());
                knownByUsage.putIfAbsent(usage, option);
                if (usage.isZero()) {
                    return report(
                            checkedPlacement,
                            checkedSupply,
                            checkedRequirement,
                            budgets,
                            searchNodeBudget,
                            totalNodesVisited,
                            FrontierStatus.COMPLETE,
                            List.of(option));
                }
            } else if (search.exhausted()) {
                finalStatus = FrontierStatus.UNRESOLVED_SEARCH_BUDGET;
                break;
            }
        }

        List<FrontierOption> nondominated = nondominatedOptions(
                knownByUsage.values(),
                assignments);
        return report(
                checkedPlacement,
                checkedSupply,
                checkedRequirement,
                budgets,
                searchNodeBudget,
                totalNodesVisited,
                finalStatus,
                nondominated);
    }

    private static FrontierReport report(
            PlacementResult placement,
            SupplyThroughputReport supply,
            CommodityRequirement requirement,
            Map<String, Integer> budgets,
            int searchNodeBudget,
            int nodesVisited,
            FrontierStatus status,
            List<FrontierOption> options) {
        return new FrontierReport(
                CURRENT_VERSION,
                placement.version(),
                supply.profileVersion(),
                requirement.commodityId(),
                searchNodeBudget,
                nodesVisited,
                status,
                budgets,
                options);
    }

    private static PlanningModel buildModel(
            GalaxyTopology topology,
            SupplyThroughputReport supply,
            List<Assignment> assignments,
            CommodityRequirement requirement,
            Map<String, Integer> budgets,
            AllocatedRouteEvaluator routes) {
        ArrayList<Demand> demands = new ArrayList<>();
        int[] maxShipsByStart = new int[assignments.size()];
        for (int startIndex = 0; startIndex < assignments.size(); startIndex++) {
            Assignment assignment = assignments.get(startIndex);
            demands.add(new Demand(
                    demands.size(),
                    startIndex,
                    assignment.stableFactionId(),
                    assignment.systemId(),
                    requirement));
            maxShipsByStart[startIndex] = budgets.get(assignment.stableFactionId());
        }

        ArrayList<Producer> producers = new ArrayList<>();
        supply.capacityKgPerSecondBySupply().entrySet().stream()
                .filter(entry -> entry.getKey().commodityId().equals(requirement.commodityId()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> producers.add(new Producer(
                        producers.size(),
                        entry.getKey(),
                        entry.getValue())));

        ArrayList<RouteCurve> curves = new ArrayList<>();
        for (Demand demand : demands) {
            for (Producer producer : producers) {
                if (producer.key().systemId().equals(demand.startSystemId())) {
                    continue;
                }
                Optional<RouteAssessment> firstMaybe = routes.assess(
                        producer.key().systemId(),
                        demand.startSystemId(),
                        1);
                if (firstMaybe.isEmpty()) {
                    continue;
                }
                RouteAssessment first = validateRoute(
                        topology,
                        producer.key().systemId(),
                        demand.startSystemId(),
                        firstMaybe.orElseThrow());
                if (first.travelTimeS() > requirement.maxSupplierRouteTimeS()) {
                    continue;
                }
                RouteCurve curve = buildCurve(
                        topology,
                        curves.size(),
                        producer,
                        demand,
                        maxShipsByStart[demand.startIndex()],
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
                    curve.supplierSystemId(),
                    curve.consumerSystemId(),
                    curve.points()));
        }

        return new PlanningModel(
                List.copyOf(assignments),
                List.copyOf(demands),
                List.copyOf(producers),
                List.copyOf(reindexed),
                maxShipsByStart.clone());
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

            double cumulative = Math.min(
                    producer.capacityKgPerSecond(),
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
                producer.key().systemId(),
                demand.startSystemId(),
                List.copyOf(points));
    }

    private static FrontierOption optionFromSolution(
            PlanningModel model,
            SupplyThroughputReport supply,
            Map<String, Integer> budgets,
            SearchSolution solution) {
        FlowResult flow = evaluateFlow(model, solution.routeCounts());
        if (flow.totalFlowKgPerSecond() + EPSILON < model.totalDemandKgPerSecond()) {
            throw new IllegalStateException("accepted commodity search state no longer satisfies whole-placement demand");
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
            usedByProducer.merge(
                    producer.key(),
                    arc.flowKgPerSecond(),
                    Stage20CommodityWholePlacementFrontierGenerator::finiteAdd);
        }

        ArrayList<StartPlan> starts = new ArrayList<>();
        TreeMap<String, Integer> usage = new TreeMap<>();
        for (int startIndex = 0; startIndex < model.assignments().size(); startIndex++) {
            Assignment assignment = model.assignments().get(startIndex);
            Demand demand = model.demands().get(startIndex);
            ArrayList<SupplierCommitment> commitments = commitmentsByDemand.get(demand.index());
            double delivered = commitments.stream()
                    .mapToDouble(SupplierCommitment::deliveredKgPerSecond)
                    .sum();
            int ships = commitments.stream()
                    .mapToInt(SupplierCommitment::allocatedFreighters)
                    .sum();
            int budget = budgets.get(assignment.stableFactionId());
            if (ships > budget) {
                throw new IllegalStateException("reconstructed commodity option exceeds authoritative fleet budget");
            }
            DemandPlan demandPlan = new DemandPlan(
                    demand.requirement().commodityId(),
                    demand.requirement().minSupplierThroughputKgPerSecond(),
                    delivered,
                    ships,
                    commitments);
            starts.add(new StartPlan(
                    assignment.stableFactionId(),
                    assignment.systemId(),
                    budget,
                    ships,
                    List.of(demandPlan)));
            usage.put(assignment.stableFactionId(), ships);
        }

        ArrayList<ProducerUsage> producerUsage = new ArrayList<>();
        for (Map.Entry<SupplyKey, Double> entry : usedByProducer.entrySet()) {
            double capacity = supply.capacityKgPerSecondBySupply().getOrDefault(entry.getKey(), 0d);
            producerUsage.add(new ProducerUsage(entry.getKey(), capacity, entry.getValue()));
        }
        if (producerUsage.isEmpty()) {
            throw new IllegalStateException("accepted commodity option must reserve physical producer throughput");
        }

        return new FrontierOption(
                optionId(usage),
                model.demands().get(0).requirement().commodityId(),
                usage,
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
                if (!producer.key().systemId().equals(demand.startSystemId())) {
                    continue;
                }
                EdgeRef edge = network.addEdge(
                        producerBase + producer.index(),
                        demandBase + demand.index(),
                        producer.capacityKgPerSecond());
                bindings.add(new ArcBinding(
                        producer.index(),
                        demand.index(),
                        -1,
                        true,
                        edge));
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
                    curve.producerIndex(),
                    curve.demandIndex(),
                    curve.index(),
                    false,
                    edge));
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

    private static List<ShipVector> capVectors(int[] maxShipsByStart) {
        ArrayList<ShipVector> vectors = new ArrayList<>();
        buildCapVectors(maxShipsByStart, 0, new int[maxShipsByStart.length], vectors);
        vectors.sort(SHIP_VECTOR_ORDER);
        return List.copyOf(vectors);
    }

    private static void buildCapVectors(
            int[] maxima,
            int index,
            int[] current,
            List<ShipVector> target) {
        if (index == maxima.length) {
            target.add(new ShipVector(current));
            return;
        }
        for (int value = 0; value <= maxima[index]; value++) {
            current[index] = value;
            buildCapVectors(maxima, index + 1, current, target);
        }
    }

    private static List<FrontierOption> nondominatedOptions(
            Iterable<FrontierOption> source,
            List<Assignment> assignments) {
        ArrayList<FrontierOption> ordered = new ArrayList<>();
        source.forEach(ordered::add);
        ordered.sort(FRONTIER_OPTION_ORDER);
        ArrayList<FrontierOption> result = new ArrayList<>();
        for (FrontierOption candidate : ordered) {
            ShipVector candidateVector = vectorForOption(candidate, assignments);
            boolean dominated = false;
            for (FrontierOption other : ordered) {
                if (candidate == other) {
                    continue;
                }
                ShipVector otherVector = vectorForOption(other, assignments);
                if (dominates(otherVector, candidateVector)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                result.add(candidate);
            }
        }
        result.sort(FRONTIER_OPTION_ORDER);
        return List.copyOf(result);
    }

    private static boolean dominates(ShipVector left, ShipVector right) {
        boolean strict = false;
        int[] leftCounts = left.counts();
        int[] rightCounts = right.counts();
        for (int index = 0; index < leftCounts.length; index++) {
            if (leftCounts[index] > rightCounts[index]) {
                return false;
            }
            strict |= leftCounts[index] < rightCounts[index];
        }
        return strict;
    }

    private static ShipVector vectorForOption(
            FrontierOption option,
            List<Assignment> assignments) {
        int[] counts = new int[assignments.size()];
        for (int index = 0; index < assignments.size(); index++) {
            counts[index] = option.remoteFreightersByFaction().get(assignments.get(index).stableFactionId());
        }
        return new ShipVector(counts);
    }

    private static String optionId(Map<String, Integer> usage) {
        StringBuilder builder = new StringBuilder("ships");
        for (Map.Entry<String, Integer> entry : usage.entrySet()) {
            builder.append('|').append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static PlacementResult requireAcceptedPlacement(PlacementResult placement) {
        PlacementResult checked = Objects.requireNonNull(placement, "placement");
        if (checked.status() != PlacementStatus.ACCEPTED || checked.assignments().isEmpty()) {
            throw new IllegalArgumentException("frontier generator requires an accepted non-empty placement");
        }
        return checked;
    }

    private static TreeMap<String, Integer> validateBudgets(
            List<Assignment> assignments,
            Map<String, Integer> source) {
        TreeMap<String, Integer> canonical = canonicalPositiveMap(
                source,
                "remoteFreighterBudgetByFaction");
        TreeMap<String, Assignment> byFaction = new TreeMap<>();
        for (Assignment assignment : assignments) {
            String faction = WorldFactionIdentityState.normalizeStableId(assignment.stableFactionId());
            if (byFaction.putIfAbsent(faction, assignment) != null) {
                throw new IllegalArgumentException("accepted placement contains duplicate stable faction IDs");
            }
        }
        if (!canonical.keySet().equals(byFaction.keySet())) {
            throw new IllegalArgumentException("freight budgets must cover exactly the placed faction starts");
        }
        return canonical;
    }

    private static TreeMap<String, Integer> canonicalPositiveMap(
            Map<String, Integer> source,
            String field) {
        Objects.requireNonNull(source, field);
        if (source.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = WorldFactionIdentityState.normalizeStableId(entry.getKey());
            Integer value = Objects.requireNonNull(entry.getValue(), field + " value");
            if (value <= 0) {
                throw new IllegalArgumentException(field + " values must be positive");
            }
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(field + " contains duplicate canonical faction IDs");
            }
        }
        return result;
    }

    private static Map<String, Integer> canonicalPositiveKeyNonNegativeValueMap(
            Map<String, Integer> source,
            String field) {
        Objects.requireNonNull(source, field);
        if (source.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = WorldFactionIdentityState.normalizeStableId(entry.getKey());
            Integer value = Objects.requireNonNull(entry.getValue(), field + " value");
            if (value < 0) {
                throw new IllegalArgumentException(field + " values must be non-negative");
            }
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(field + " contains duplicate canonical faction IDs");
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static RouteAssessment validateRoute(
            GalaxyTopology topology,
            StarSystemId origin,
            StarSystemId destination,
            RouteAssessment route) {
        Objects.requireNonNull(route, "route");
        List<StarSystemId> path = route.orderedSystems();
        if (!path.get(0).equals(origin) || !path.get(path.size() - 1).equals(destination)) {
            throw new IllegalArgumentException("route endpoints do not match commodity frontier request");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("remote commodity allocation cannot use a same-system route");
        }
        for (int index = 0; index < path.size() - 1; index++) {
            if (!topology.neighbors(path.get(index)).contains(path.get(index + 1))) {
                throw new IllegalArgumentException("commodity frontier route contains a non-neighbor shortcut");
            }
        }
        return route;
    }

    private static void ensureNondominated(List<FrontierOption> options) {
        for (int first = 0; first < options.size(); first++) {
            FrontierOption left = options.get(first);
            for (int second = 0; second < options.size(); second++) {
                if (first == second) {
                    continue;
                }
                FrontierOption right = options.get(second);
                boolean noMoreEverywhere = true;
                boolean strict = false;
                for (String faction : left.remoteFreightersByFaction().keySet()) {
                    int leftCount = left.remoteFreightersByFaction().get(faction);
                    int rightCount = right.remoteFreightersByFaction().get(faction);
                    if (rightCount > leftCount) {
                        noMoreEverywhere = false;
                        break;
                    }
                    strict |= rightCount < leftCount;
                }
                if (noMoreEverywhere && strict) {
                    throw new IllegalArgumentException("frontier report cannot contain dominated options");
                }
            }
        }
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

    private static final Comparator<RouteCurve> ROUTE_ORDER = Comparator
            .comparingInt(RouteCurve::startIndex)
            .thenComparing(RouteCurve::supplierSystemId)
            .thenComparing(RouteCurve::consumerSystemId);

    private static final Comparator<ShipVector> SHIP_VECTOR_ORDER = (left, right) -> {
        int total = Integer.compare(left.total(), right.total());
        if (total != 0) {
            return total;
        }
        int[] leftCounts = left.counts();
        int[] rightCounts = right.counts();
        for (int index = 0; index < leftCounts.length; index++) {
            int compared = Integer.compare(leftCounts[index], rightCounts[index]);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    };

    private static final Comparator<FrontierOption> FRONTIER_OPTION_ORDER = Comparator
            .comparingInt((FrontierOption value) -> value.remoteFreightersByFaction().values().stream()
                    .mapToInt(Integer::intValue)
                    .sum())
            .thenComparing(FrontierOption::optionId);

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
            int[] maxShipsByStart) {
        private PlanningModel {
            maxShipsByStart = maxShipsByStart.clone();
        }

        double totalDemandKgPerSecond() {
            return demands.stream()
                    .mapToDouble(value -> value.requirement().minSupplierThroughputKgPerSecond())
                    .sum();
        }

        public int[] maxShipsByStart() {
            return maxShipsByStart.clone();
        }
    }

    private record SearchSolution(int[] routeCounts) {
        private SearchSolution {
            routeCounts = routeCounts.clone();
        }

        public int[] routeCounts() {
            return routeCounts.clone();
        }
    }

    private static final class ShipVector {
        private final int[] counts;
        private final int hash;

        private ShipVector(int[] counts) {
            this.counts = counts.clone();
            this.hash = Arrays.hashCode(this.counts);
        }

        private int[] counts() {
            return counts.clone();
        }

        private int total() {
            int total = 0;
            for (int count : counts) {
                total = Math.addExact(total, count);
            }
            return total;
        }

        private boolean isZero() {
            for (int count : counts) {
                if (count != 0) {
                    return false;
                }
            }
            return true;
        }

        private int[] toIntArray() {
            return counts.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ShipVector value && Arrays.equals(counts, value.counts);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class SearchContext {
        private final PlanningModel model;
        private final int[] capByStart;
        private final int nodeBudget;
        private final Set<StateKey> failedStates = new HashSet<>();
        private int nodesVisited;
        private boolean exhausted;

        private SearchContext(PlanningModel model, int[] capByStart, int nodeBudget) {
            this.model = model;
            this.capByStart = capByStart.clone();
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
            if (!canStillReachTotalDemand(flow, routeCounts, shipsByStart)) {
                failedStates.add(key);
                return null;
            }

            ArrayList<RouteCurve> candidates = new ArrayList<>();
            int[] candidateCountsByDemand = new int[model.demands().size()];
            for (RouteCurve curve : model.routes()) {
                int count = routeCounts[curve.index()];
                if (count >= curve.points().size()
                        || shipsByStart[curve.startIndex()] >= capByStart[curve.startIndex()]) {
                    continue;
                }
                int producerNode = flow.producerBase() + curve.producerIndex();
                int demandNode = flow.demandBase() + curve.demandIndex();
                if (flow.reachable()[producerNode] && !flow.reachable()[demandNode]) {
                    candidates.add(curve);
                    candidateCountsByDemand[curve.demandIndex()]++;
                }
            }

            candidates.sort(Comparator
                    .comparingInt((RouteCurve value) -> candidateCountsByDemand[value.demandIndex()])
                    .thenComparingDouble(value -> -demandDeficit(flow, value.demandIndex()))
                    .thenComparingDouble(value -> -value.nextMarginal(routeCounts[value.index()]))
                    .thenComparingInt(RouteCurve::startIndex)
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

        private double demandDeficit(FlowResult flow, int demandIndex) {
            Demand demand = model.demands().get(demandIndex);
            return Math.max(
                    0d,
                    demand.requirement().minSupplierThroughputKgPerSecond()
                            - flow.deliveredByDemand()[demandIndex]);
        }

        private boolean canStillReachTotalDemand(
                FlowResult flow,
                int[] routeCounts,
                int[] shipsByStart) {
            double optimisticTotal = flow.totalFlowKgPerSecond();
            for (int startIndex = 0; startIndex < model.assignments().size(); startIndex++) {
                int remainingShips = capByStart[startIndex] - shipsByStart[startIndex];
                if (remainingShips <= 0) {
                    continue;
                }
                ArrayList<Double> remainingMarginals = new ArrayList<>();
                for (RouteCurve curve : model.routes()) {
                    if (curve.startIndex() != startIndex) {
                        continue;
                    }
                    int currentCount = routeCounts[curve.index()];
                    for (int pointIndex = currentCount; pointIndex < curve.points().size(); pointIndex++) {
                        remainingMarginals.add(curve.points().get(pointIndex).marginalKgPerSecond());
                    }
                }
                remainingMarginals.sort(Comparator.reverseOrder());
                int admitted = Math.min(remainingShips, remainingMarginals.size());
                for (int index = 0; index < admitted; index++) {
                    optimisticTotal = finiteAdd(optimisticTotal, remainingMarginals.get(index));
                    if (optimisticTotal + EPSILON >= model.totalDemandKgPerSecond()) {
                        return true;
                    }
                }
            }
            return optimisticTotal + EPSILON >= model.totalDemandKgPerSecond();
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
