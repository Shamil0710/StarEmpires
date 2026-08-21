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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Completeness-safe optimistic Stage-20E cap-vector bound for one commodity.
 *
 * <p>The bound keeps authoritative producer capacities, local supply, route reachability,
 * route-time admission and cumulative physical route throughput. It deliberately relaxes only one
 * constraint: every remote producer-to-start route may independently consume the start's entire cap.
 * Real plans must share that cap between routes, so this network can only overestimate deliverable
 * throughput. Consequently {@link Status#PROVED_INFEASIBLE} is a sound pruning proof, while
 * {@link Status#POSSIBLY_FEASIBLE} is not an acceptance decision.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20CommodityFrontierSharedProducerBound {
    /** Stable bound version. */
    public static final String CURRENT_VERSION = "stage20e.commodity-frontier-shared-producer-bound.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20CommodityFrontierSharedProducerBound() {
        throw new AssertionError("No instances");
    }

    /** Result semantics for one optimistic cap-vector assessment. */
    public enum Status {
        /** Even the relaxed network cannot satisfy all commodity demands. */
        PROVED_INFEASIBLE,
        /** The relaxed network can satisfy demand; exact route-prefix search is still required. */
        POSSIBLY_FEASIBLE
    }

    /**
     * Bounded optimistic evidence for one commodity cap vector.
     *
     * @param version bound version
     * @param commodityId authoritative commodity identifier
     * @param remoteFreighterCapByFaction assessed per-start fleet caps
     * @param requiredTotalKgPerSecond total whole-placement demand
     * @param optimisticMaxFlowKgPerSecond maximum flow in the relaxed shared-producer network
     * @param status sound infeasibility proof or unresolved optimistic feasibility
     */
    public record Assessment(
            String version,
            String commodityId,
            Map<String, Integer> remoteFreighterCapByFaction,
            double requiredTotalKgPerSecond,
            double optimisticMaxFlowKgPerSecond,
            Status status) {
        /**
         * Validates and canonicalizes one bound result.
         *
         * @param version bound version
         * @param commodityId authoritative commodity identifier
         * @param remoteFreighterCapByFaction assessed per-start fleet caps
         * @param requiredTotalKgPerSecond total whole-placement demand
         * @param optimisticMaxFlowKgPerSecond maximum flow in the relaxed shared-producer network
         * @param status sound infeasibility proof or unresolved optimistic feasibility
         */
        public Assessment {
            version = requireText(version, "version");
            commodityId = requireText(commodityId, "commodityId");
            remoteFreighterCapByFaction = Collections.unmodifiableMap(
                    canonicalCaps(remoteFreighterCapByFaction));
            if (!Double.isFinite(requiredTotalKgPerSecond) || requiredTotalKgPerSecond <= 0d
                    || !Double.isFinite(optimisticMaxFlowKgPerSecond)
                    || optimisticMaxFlowKgPerSecond < 0d
                    || optimisticMaxFlowKgPerSecond > requiredTotalKgPerSecond + EPSILON) {
                throw new IllegalArgumentException("bound throughput values are invalid");
            }
            Objects.requireNonNull(status, "status");
            boolean proven = optimisticMaxFlowKgPerSecond + EPSILON < requiredTotalKgPerSecond;
            if (proven != (status == Status.PROVED_INFEASIBLE)) {
                throw new IllegalArgumentException("bound status must match optimistic max-flow evidence");
            }
        }
    }

    /**
     * Computes a producer-sharing-aware optimistic upper bound for one commodity and cap vector.
     *
     * @param topology authoritative explicit-neighbor topology
     * @param placement accepted non-empty faction-start placement
     * @param supply authoritative physical producer capacities
     * @param requirement one commodity bootstrap requirement
     * @param remoteFreighterCapByFaction non-negative fleet cap for every placed start
     * @param routes authoritative route evaluator parameterized by allocated integer freighters
     * @return sound optimistic cap-vector evidence
     */
    public static Assessment assess(
            GalaxyTopology topology,
            PlacementResult placement,
            SupplyThroughputReport supply,
            CommodityRequirement requirement,
            Map<String, Integer> remoteFreighterCapByFaction,
            AllocatedRouteEvaluator routes) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        PlacementResult checkedPlacement = Objects.requireNonNull(placement, "placement");
        SupplyThroughputReport checkedSupply = Objects.requireNonNull(supply, "supply");
        CommodityRequirement checkedRequirement = Objects.requireNonNull(requirement, "requirement");
        AllocatedRouteEvaluator checkedRoutes = Objects.requireNonNull(routes, "routes");
        if (checkedPlacement.status() != PlacementStatus.ACCEPTED || checkedPlacement.assignments().isEmpty()) {
            throw new IllegalArgumentException("shared-producer bound requires accepted non-empty placement");
        }

        ArrayList<Assignment> assignments = new ArrayList<>(checkedPlacement.assignments());
        assignments.sort(Comparator.comparing(Assignment::stableFactionId));
        TreeMap<String, Integer> caps = validateCaps(assignments, remoteFreighterCapByFaction);
        for (Assignment assignment : assignments) {
            if (checkedTopology.findSystem(assignment.systemId()).isEmpty()) {
                throw new IllegalArgumentException("placed start is outside authoritative topology");
            }
        }

        ArrayList<Producer> producers = new ArrayList<>();
        TreeMap<SupplyKey, Double> orderedSupply = new TreeMap<>(checkedSupply.capacityKgPerSecondBySupply());
        for (Map.Entry<SupplyKey, Double> entry : orderedSupply.entrySet()) {
            if (!entry.getKey().commodityId().equals(checkedRequirement.commodityId())) {
                continue;
            }
            double capacity = entry.getValue();
            if (!Double.isFinite(capacity) || capacity < 0d) {
                throw new IllegalArgumentException("producer capacity must be finite and non-negative");
            }
            if (capacity > EPSILON) {
                producers.add(new Producer(producers.size(), entry.getKey(), capacity));
            }
        }

        int source = 0;
        int producerBase = 1;
        int demandBase = producerBase + producers.size();
        int sink = demandBase + assignments.size();
        FlowNetwork network = new FlowNetwork(sink + 1);
        for (Producer producer : producers) {
            network.addEdge(source, producerBase + producer.index(), producer.capacityKgPerSecond());
        }

        for (Producer producer : producers) {
            for (int demandIndex = 0; demandIndex < assignments.size(); demandIndex++) {
                Assignment assignment = assignments.get(demandIndex);
                double arcCapacity;
                if (producer.key().systemId().equals(assignment.systemId())) {
                    arcCapacity = producer.capacityKgPerSecond();
                } else {
                    int cap = caps.get(assignment.stableFactionId());
                    arcCapacity = remoteArcUpperBound(
                            checkedTopology,
                            producer,
                            assignment.systemId(),
                            checkedRequirement,
                            cap,
                            checkedRoutes);
                }
                if (arcCapacity > EPSILON) {
                    network.addEdge(
                            producerBase + producer.index(),
                            demandBase + demandIndex,
                            arcCapacity);
                }
            }
        }

        double perStartDemand = checkedRequirement.minSupplierThroughputKgPerSecond();
        if (!Double.isFinite(perStartDemand) || perStartDemand <= 0d) {
            throw new IllegalArgumentException("commodity requirement throughput must be finite and positive");
        }
        for (int demandIndex = 0; demandIndex < assignments.size(); demandIndex++) {
            network.addEdge(demandBase + demandIndex, sink, perStartDemand);
        }
        double requiredTotal = finiteMultiply(perStartDemand, assignments.size());
        double optimistic = Math.min(requiredTotal, network.maxFlow(source, sink));
        Status status = optimistic + EPSILON < requiredTotal
                ? Status.PROVED_INFEASIBLE
                : Status.POSSIBLY_FEASIBLE;
        return new Assessment(
                CURRENT_VERSION,
                checkedRequirement.commodityId(),
                caps,
                requiredTotal,
                optimistic,
                status);
    }

    private static double remoteArcUpperBound(
            GalaxyTopology topology,
            Producer producer,
            StarSystemId destination,
            CommodityRequirement requirement,
            int cap,
            AllocatedRouteEvaluator routes) {
        if (cap <= 0) {
            return 0d;
        }
        Optional<RouteAssessment> firstMaybe = routes.assess(producer.key().systemId(), destination, 1);
        if (firstMaybe.isEmpty()) {
            return 0d;
        }
        RouteAssessment reference = validateRoute(
                topology,
                producer.key().systemId(),
                destination,
                firstMaybe.orElseThrow());
        if (reference.travelTimeS() > requirement.maxSupplierRouteTimeS()) {
            return 0d;
        }

        double previousDelivered = 0d;
        double previousMarginal = Double.POSITIVE_INFINITY;
        double upperBound = 0d;
        for (int ships = 1; ships <= cap; ships++) {
            Optional<RouteAssessment> maybe = ships == 1
                    ? Optional.of(reference)
                    : routes.assess(producer.key().systemId(), destination, ships);
            if (maybe.isEmpty()) {
                throw new IllegalArgumentException("allocated route became unavailable after a smaller allocation");
            }
            RouteAssessment route = validateRoute(
                    topology,
                    producer.key().systemId(),
                    destination,
                    maybe.orElseThrow());
            if (!route.orderedSystems().equals(reference.orderedSystems())
                    || Math.abs(route.travelTimeS() - reference.travelTimeS()) > EPSILON) {
                throw new IllegalArgumentException("allocated ship count cannot change route path or delivery time");
            }
            if (route.travelTimeS() > requirement.maxSupplierRouteTimeS()) {
                throw new IllegalArgumentException("allocated route changed outside admitted service time");
            }
            double cumulative = Math.min(
                    producer.capacityKgPerSecond(),
                    route.sustainableCargoThroughputKgPerSecond());
            if (!Double.isFinite(cumulative) || cumulative < 0d) {
                throw new IllegalArgumentException("allocated route throughput must be finite and non-negative");
            }
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
            upperBound = cumulative;
            previousDelivered = cumulative;
            previousMarginal = marginal;
        }
        return upperBound;
    }

    private static RouteAssessment validateRoute(
            GalaxyTopology topology,
            StarSystemId origin,
            StarSystemId destination,
            RouteAssessment route) {
        Objects.requireNonNull(route, "route");
        List<StarSystemId> path = route.orderedSystems();
        if (!path.get(0).equals(origin) || !path.get(path.size() - 1).equals(destination)) {
            throw new IllegalArgumentException("route endpoints do not match shared-producer bound request");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("remote bound route cannot use a same-system path");
        }
        for (int index = 0; index < path.size() - 1; index++) {
            if (!topology.neighbors(path.get(index)).contains(path.get(index + 1))) {
                throw new IllegalArgumentException("shared-producer bound route contains a non-neighbor shortcut");
            }
        }
        return route;
    }

    private static TreeMap<String, Integer> validateCaps(
            List<Assignment> assignments,
            Map<String, Integer> source) {
        TreeMap<String, Integer> caps = canonicalCaps(source);
        TreeMap<String, Assignment> byFaction = new TreeMap<>();
        for (Assignment assignment : assignments) {
            String faction = WorldFactionIdentityState.normalizeStableId(assignment.stableFactionId());
            if (byFaction.putIfAbsent(faction, assignment) != null) {
                throw new IllegalArgumentException("accepted placement contains duplicate stable faction IDs");
            }
        }
        if (!caps.keySet().equals(byFaction.keySet())) {
            throw new IllegalArgumentException("cap vector must cover exactly the placed faction starts");
        }
        return caps;
    }

    private static TreeMap<String, Integer> canonicalCaps(Map<String, Integer> source) {
        Objects.requireNonNull(source, "remoteFreighterCapByFaction");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("remoteFreighterCapByFaction must not be empty");
        }
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = WorldFactionIdentityState.normalizeStableId(entry.getKey());
            Integer value = Objects.requireNonNull(entry.getValue(), "remoteFreighterCapByFaction value");
            if (value < 0) {
                throw new IllegalArgumentException("remote freight caps must be non-negative");
            }
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("cap vector contains duplicate canonical faction IDs");
            }
        }
        return result;
    }

    private static double finiteMultiply(double value, int count) {
        double result = value * count;
        if (!Double.isFinite(result)) {
            throw new IllegalStateException("throughput product overflow");
        }
        return result;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private record Producer(int index, SupplyKey key, double capacityKgPerSecond) {
    }

    private static final class FlowNetwork {
        private final ArrayList<ArrayList<Edge>> adjacency;

        private FlowNetwork(int nodeCount) {
            adjacency = new ArrayList<>(nodeCount);
            for (int index = 0; index < nodeCount; index++) {
                adjacency.add(new ArrayList<>());
            }
        }

        private void addEdge(int from, int to, double capacity) {
            if (!Double.isFinite(capacity) || capacity < 0d) {
                throw new IllegalArgumentException("flow capacity must be finite and non-negative");
            }
            int forwardIndex = adjacency.get(from).size();
            int reverseIndex = adjacency.get(to).size();
            adjacency.get(from).add(new Edge(to, reverseIndex, capacity));
            adjacency.get(to).add(new Edge(from, forwardIndex, 0d));
        }

        private double maxFlow(int source, int sink) {
            double total = 0d;
            while (true) {
                int[] parentNode = new int[adjacency.size()];
                int[] parentEdge = new int[adjacency.size()];
                java.util.Arrays.fill(parentNode, -1);
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
                    throw new IllegalStateException("invalid positive max-flow augment");
                }
                for (int node = sink; node != source; node = parentNode[node]) {
                    int previous = parentNode[node];
                    Edge edge = adjacency.get(previous).get(parentEdge[node]);
                    edge.residual -= augment;
                    adjacency.get(node).get(edge.reverseIndex).residual += augment;
                }
                total += augment;
                if (!Double.isFinite(total)) {
                    throw new IllegalStateException("max-flow overflow");
                }
            }
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
