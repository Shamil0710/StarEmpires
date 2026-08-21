package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20FreightPortfolioAllocator.AllocationReport;
import com.spacesim.world.Stage20FreightPortfolioAllocator.RequirementPlan;
import com.spacesim.world.Stage20FreightPortfolioAllocator.RequirementStatus;
import com.spacesim.world.Stage20FreightPortfolioAllocator.RouteAllocation;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Whole-placement Stage-20E reservation of finite producer capacity across selected faction-start
 * freight portfolios.
 *
 * <p>The single-start freight allocator proves that one ordinary start can satisfy its essential
 * requirements without reusing its finite inter-system freighters. That proof is intentionally
 * local: two independently accepted starts may still attempt to consume the same producer capacity.
 * This class closes that second accounting boundary for an already selected set of single-start
 * portfolios. For every commodity it solves one deterministic maximum-flow problem whose producer
 * source edges are capped by the authoritative {@link SupplyThroughputReport} capacities.</p>
 *
 * <p>The flow reserves exactly the service rate that is needed. A route whose last allocated ship
 * exposes more capacity than the requirement does not reserve that surplus. Local supply is not free:
 * a local producer uses the same finite producer edge as remote consumers and therefore competes with
 * them normally.</p>
 *
 * <p>This class does not search alternative single-start freight portfolios. A conflict means that
 * the supplied selected portfolios cannot coexist under shared producer capacity; it is not proof
 * that no different global supplier mix could work. It also does not materialize ships, assign
 * ownership, create stock, alter topology or mutate generated supply.</p>
 */
public final class Stage20WholePlacementProducerCapacityReservation {
    /** Stable result version for this reservation contract. */
    public static final String CURRENT_VERSION = "stage20e.whole-placement-producer-reservation.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20WholePlacementProducerCapacityReservation() {
        throw new AssertionError("No instances");
    }

    /** Final reservation status for the supplied selected portfolios. */
    public enum Status {
        /** Every placed start receives every essential service rate without producer double-counting. */
        ACCEPTED,
        /** Selected individually valid portfolios compete for more shared producer capacity than exists. */
        SELECTED_PORTFOLIO_CONFLICT
    }

    /** Explicit reservation failure class. */
    public enum FailureReason {
        /** At least one commodity cannot satisfy all selected starts under shared producer ceilings. */
        SHARED_PRODUCER_CAPACITY_CONFLICT
    }

    /**
     * One exact producer reservation produced by the global flow.
     *
     * @param stableFactionId canonical placed faction identity
     * @param consumerSystemId faction start receiving the service
     * @param commodityId essential commodity
     * @param producerSystemId physical producer whose finite capacity is reserved
     * @param reservedKgPerSecond exact reserved service rate
     * @param local true only when producer and consumer are the same system
     */
    public record Reservation(
            String stableFactionId,
            StarSystemId consumerSystemId,
            String commodityId,
            StarSystemId producerSystemId,
            double reservedKgPerSecond,
            boolean local) {
        /**
         * Validates one immutable producer reservation.
         *
         * @param stableFactionId canonical placed faction identity
         * @param consumerSystemId faction start receiving the service
         * @param commodityId essential commodity
         * @param producerSystemId physical producer whose capacity is reserved
         * @param reservedKgPerSecond exact reserved service rate
         * @param local whether producer and consumer are the same system
         */
        public Reservation {
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            Objects.requireNonNull(consumerSystemId, "consumerSystemId");
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(producerSystemId, "producerSystemId");
            requirePositiveFinite(reservedKgPerSecond, "reservedKgPerSecond");
            if (local != producerSystemId.equals(consumerSystemId)) {
                throw new IllegalArgumentException("local flag must match producer/consumer identity");
            }
        }
    }

    /**
     * Whole-placement evidence for one essential commodity.
     *
     * @param commodityId authoritative commodity ID
     * @param requiredKgPerSecond sum of all placed-start requirements
     * @param reservedKgPerSecond maximum physically reservable service over selected portfolios
     * @param producerCapacityKgPerSecond authoritative total capacity of producers participating in selected arcs
     * @param status final commodity reservation status
     */
    public record CommodityEvidence(
            String commodityId,
            double requiredKgPerSecond,
            double reservedKgPerSecond,
            double producerCapacityKgPerSecond,
            Status status) {
        /**
         * Validates one immutable commodity evidence row.
         *
         * @param commodityId authoritative commodity ID
         * @param requiredKgPerSecond summed placed-start requirement
         * @param reservedKgPerSecond physically reserved service rate
         * @param producerCapacityKgPerSecond participating authoritative producer capacity
         * @param status commodity reservation status
         */
        public CommodityEvidence {
            commodityId = requireText(commodityId, "commodityId");
            requirePositiveFinite(requiredKgPerSecond, "requiredKgPerSecond");
            requireNonNegativeFinite(reservedKgPerSecond, "reservedKgPerSecond");
            requireNonNegativeFinite(producerCapacityKgPerSecond, "producerCapacityKgPerSecond");
            Objects.requireNonNull(status, "status");
            if (reservedKgPerSecond > requiredKgPerSecond + EPSILON) {
                throw new IllegalArgumentException("reserved throughput cannot exceed required throughput");
            }
            if (status == Status.ACCEPTED && reservedKgPerSecond + EPSILON < requiredKgPerSecond) {
                throw new IllegalArgumentException("accepted commodity evidence must satisfy its requirement");
            }
        }
    }

    /**
     * Deterministic whole-placement reservation result.
     *
     * @param version stable reservation-result version
     * @param placementVersion exact placement version consumed
     * @param supplyProfileVersion exact supply-throughput profile consumed
     * @param allocatorVersions sorted allocator result versions consumed
     * @param status final whole-placement reservation status
     * @param failureReason absent only when accepted
     * @param reservations exact non-zero producer reservations
     * @param commodityEvidence deterministic per-commodity evidence
     */
    public record ReservationReport(
            String version,
            String placementVersion,
            String supplyProfileVersion,
            List<String> allocatorVersions,
            Status status,
            Optional<FailureReason> failureReason,
            List<Reservation> reservations,
            List<CommodityEvidence> commodityEvidence) {
        /**
         * Validates and freezes one reservation report.
         *
         * @param version stable reservation-result version
         * @param placementVersion exact placement version consumed
         * @param supplyProfileVersion exact supply-throughput profile consumed
         * @param allocatorVersions allocator result versions consumed
         * @param status final whole-placement reservation status
         * @param failureReason absent only when accepted
         * @param reservations exact non-zero reservations
         * @param commodityEvidence per-commodity reservation evidence
         */
        public ReservationReport {
            version = requireText(version, "version");
            placementVersion = requireText(placementVersion, "placementVersion");
            supplyProfileVersion = requireText(supplyProfileVersion, "supplyProfileVersion");
            Objects.requireNonNull(allocatorVersions, "allocatorVersions");
            TreeSet<String> versions = new TreeSet<>();
            for (String allocatorVersion : allocatorVersions) {
                versions.add(requireText(allocatorVersion, "allocatorVersion"));
            }
            if (versions.isEmpty()) {
                throw new IllegalArgumentException("allocatorVersions must not be empty");
            }
            allocatorVersions = List.copyOf(versions);
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(failureReason, "failureReason");
            Objects.requireNonNull(reservations, "reservations");
            Objects.requireNonNull(commodityEvidence, "commodityEvidence");
            ArrayList<Reservation> reservationCopy = new ArrayList<>(reservations);
            if (reservationCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("reservations cannot contain null");
            }
            reservationCopy.sort(RESERVATION_ORDER);
            reservations = List.copyOf(reservationCopy);
            ArrayList<CommodityEvidence> evidenceCopy = new ArrayList<>(commodityEvidence);
            if (evidenceCopy.isEmpty() || evidenceCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("commodityEvidence must be non-empty and contain no nulls");
            }
            evidenceCopy.sort(Comparator.comparing(CommodityEvidence::commodityId));
            commodityEvidence = List.copyOf(evidenceCopy);
            if ((status == Status.ACCEPTED) != failureReason.isEmpty()) {
                throw new IllegalArgumentException("failureReason must be absent exactly when accepted");
            }
        }
    }

    /**
     * Reserves shared producer capacity for an accepted whole placement and accepted single-start
     * freight portfolios.
     *
     * @param topology authoritative explicit-neighbor topology
     * @param placement accepted ordinary faction-start placement
     * @param supply authoritative non-reserved physical producer-capacity closure
     * @param requirements essential bootstrap service requirements
     * @param allocationsByFaction one accepted single-start allocation report per placed faction
     * @return deterministic reservation evidence; generated state is not mutated
     */
    public static ReservationReport reserve(
            GalaxyTopology topology,
            PlacementResult placement,
            SupplyThroughputReport supply,
            List<CommodityRequirement> requirements,
            Map<String, AllocationReport> allocationsByFaction) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        PlacementResult checkedPlacement = requireAcceptedPlacement(placement);
        SupplyThroughputReport checkedSupply = Objects.requireNonNull(supply, "supply");
        List<CommodityRequirement> orderedRequirements = canonicalRequirements(requirements);
        TreeMap<String, AllocationReport> allocations = canonicalAllocations(
                checkedPlacement,
                allocationsByFaction,
                orderedRequirements,
                checkedTopology,
                checkedSupply);

        ArrayList<Reservation> reservations = new ArrayList<>();
        ArrayList<CommodityEvidence> evidence = new ArrayList<>();
        boolean accepted = true;
        for (CommodityRequirement requirement : orderedRequirements) {
            CommodityFlowResult commodity = reserveCommodity(
                    checkedPlacement,
                    checkedSupply,
                    allocations,
                    requirement);
            reservations.addAll(commodity.reservations());
            evidence.add(commodity.evidence());
            if (commodity.evidence().status() != Status.ACCEPTED) {
                accepted = false;
            }
        }

        TreeSet<String> allocatorVersions = new TreeSet<>();
        allocations.values().forEach(report -> allocatorVersions.add(report.version()));
        return new ReservationReport(
                CURRENT_VERSION,
                checkedPlacement.version(),
                checkedSupply.profileVersion(),
                List.copyOf(allocatorVersions),
                accepted ? Status.ACCEPTED : Status.SELECTED_PORTFOLIO_CONFLICT,
                accepted
                        ? Optional.empty()
                        : Optional.of(FailureReason.SHARED_PRODUCER_CAPACITY_CONFLICT),
                reservations,
                evidence);
    }

    private static CommodityFlowResult reserveCommodity(
            PlacementResult placement,
            SupplyThroughputReport supply,
            Map<String, AllocationReport> allocations,
            CommodityRequirement requirement) {
        ArrayList<DemandArcSet> demandSets = new ArrayList<>();
        TreeSet<SupplyKey> participatingProducers = new TreeSet<>();
        for (Assignment assignment : placement.assignments()) {
            AllocationReport report = allocations.get(assignment.stableFactionId());
            RequirementPlan plan = report.requirementPlans().stream()
                    .filter(value -> value.commodityId().equals(requirement.commodityId()))
                    .findFirst()
                    .orElseThrow();
            ArrayList<ServiceArc> arcs = new ArrayList<>();
            if (plan.localDeliveredKgPerSecond() > EPSILON) {
                SupplyKey localKey = new SupplyKey(requirement.commodityId(), assignment.systemId());
                arcs.add(new ServiceArc(localKey, plan.localDeliveredKgPerSecond(), true));
                participatingProducers.add(localKey);
            }
            for (RouteAllocation allocation : plan.remoteAllocations()) {
                SupplyKey key = new SupplyKey(requirement.commodityId(), allocation.supplierSystemId());
                arcs.add(new ServiceArc(key, allocation.deliveredCapacityKgPerSecond(), false));
                participatingProducers.add(key);
            }
            arcs.sort(Comparator.comparing(ServiceArc::supplyKey));
            demandSets.add(new DemandArcSet(
                    assignment.stableFactionId(),
                    assignment.systemId(),
                    requirement.minSupplierThroughputKgPerSecond(),
                    List.copyOf(arcs)));
        }
        demandSets.sort(Comparator.comparing(DemandArcSet::stableFactionId));

        FlowNetwork network = FlowNetwork.build(supply, demandSets, participatingProducers);
        double required = finiteMultiply(requirement.minSupplierThroughputKgPerSecond(), demandSets.size());
        double reserved = network.maxFlow();
        ArrayList<Reservation> reservations = network.reservations(requirement.commodityId(), demandSets);
        double producerCapacity = 0d;
        for (SupplyKey key : participatingProducers) {
            producerCapacity = finiteAdd(producerCapacity, supply.capacityKgPerSecondBySupply().getOrDefault(key, 0d));
        }
        Status status = reserved + EPSILON >= required
                ? Status.ACCEPTED
                : Status.SELECTED_PORTFOLIO_CONFLICT;
        return new CommodityFlowResult(
                List.copyOf(reservations),
                new CommodityEvidence(
                        requirement.commodityId(),
                        required,
                        Math.min(required, reserved),
                        producerCapacity,
                        status));
    }

    private static PlacementResult requireAcceptedPlacement(PlacementResult placement) {
        PlacementResult checked = Objects.requireNonNull(placement, "placement");
        if (checked.status() != PlacementStatus.ACCEPTED || checked.assignments().isEmpty()) {
            throw new IllegalArgumentException("whole-placement reservation requires an accepted non-empty placement");
        }
        return checked;
    }

    private static TreeMap<String, AllocationReport> canonicalAllocations(
            PlacementResult placement,
            Map<String, AllocationReport> allocationsByFaction,
            List<CommodityRequirement> requirements,
            GalaxyTopology topology,
            SupplyThroughputReport supply) {
        Objects.requireNonNull(allocationsByFaction, "allocationsByFaction");
        TreeMap<String, AllocationReport> canonical = new TreeMap<>();
        for (Map.Entry<String, AllocationReport> entry : allocationsByFaction.entrySet()) {
            String faction = WorldFactionIdentityState.normalizeStableId(entry.getKey());
            AllocationReport previous = canonical.put(faction, Objects.requireNonNull(entry.getValue(), "allocation report"));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate canonical faction allocation: " + faction);
            }
        }
        Set<String> expectedFactions = new HashSet<>();
        for (Assignment assignment : placement.assignments()) {
            expectedFactions.add(assignment.stableFactionId());
            AllocationReport report = canonical.get(assignment.stableFactionId());
            if (report == null) {
                throw new IllegalArgumentException("missing allocation for placed faction: " + assignment.stableFactionId());
            }
            if (!report.accepted()) {
                throw new IllegalArgumentException("reservation requires accepted single-start allocation reports");
            }
            if (!report.startSystemId().equals(assignment.systemId())) {
                throw new IllegalArgumentException("allocation start does not match faction placement");
            }
            validatePlans(report, requirements, topology, supply);
        }
        if (!canonical.keySet().equals(expectedFactions)) {
            throw new IllegalArgumentException("allocations must contain exactly the placed factions");
        }
        return canonical;
    }

    private static void validatePlans(
            AllocationReport report,
            List<CommodityRequirement> requirements,
            GalaxyTopology topology,
            SupplyThroughputReport supply) {
        TreeMap<String, RequirementPlan> plans = new TreeMap<>();
        for (RequirementPlan plan : report.requirementPlans()) {
            if (plans.put(plan.commodityId(), plan) != null) {
                throw new IllegalArgumentException("duplicate commodity plan: " + plan.commodityId());
            }
        }
        if (plans.size() != requirements.size()) {
            throw new IllegalArgumentException("allocation report must contain exactly the essential requirements");
        }
        for (CommodityRequirement requirement : requirements) {
            RequirementPlan plan = plans.get(requirement.commodityId());
            if (plan == null || plan.status() != RequirementStatus.SATISFIED) {
                throw new IllegalArgumentException("allocation report lacks a satisfied required commodity plan");
            }
            if (Math.abs(plan.requiredKgPerSecond() - requirement.minSupplierThroughputKgPerSecond()) > EPSILON) {
                throw new IllegalArgumentException("allocation requirement rate does not match reservation authority");
            }
            double authoritativeLocal = supply.capacityKgPerSecond(requirement.commodityId(), report.startSystemId());
            if (plan.localAvailableKgPerSecond() > authoritativeLocal + EPSILON
                    || plan.localDeliveredKgPerSecond() > authoritativeLocal + EPSILON) {
                throw new IllegalArgumentException("allocation local supply exceeds authoritative producer capacity");
            }
            for (RouteAllocation allocation : plan.remoteAllocations()) {
                if (!allocation.commodityId().equals(requirement.commodityId())) {
                    throw new IllegalArgumentException("route allocation commodity mismatch");
                }
                SupplyKey key = new SupplyKey(requirement.commodityId(), allocation.supplierSystemId());
                double authoritative = supply.capacityKgPerSecondBySupply().getOrDefault(key, 0d);
                if (allocation.supplierCapacityKgPerSecond() > authoritative + EPSILON
                        || allocation.deliveredCapacityKgPerSecond() > authoritative + EPSILON) {
                    throw new IllegalArgumentException("route allocation exceeds authoritative producer capacity");
                }
                validateRoute(topology, allocation.supplierSystemId(), report.startSystemId(), allocation.route());
                if (allocation.route().travelTimeS() > requirement.maxSupplierRouteTimeS() + EPSILON) {
                    throw new IllegalArgumentException("route allocation exceeds authoritative service-time boundary");
                }
            }
        }
    }

    private static RouteAssessment validateRoute(
            GalaxyTopology topology,
            StarSystemId origin,
            StarSystemId destination,
            RouteAssessment route) {
        Objects.requireNonNull(route, "route");
        List<StarSystemId> path = route.orderedSystems();
        if (!path.get(0).equals(origin) || !path.get(path.size() - 1).equals(destination)) {
            throw new IllegalArgumentException("route endpoints mismatch");
        }
        if (origin.equals(destination)) {
            if (path.size() != 1) {
                throw new IllegalArgumentException("same-system route must contain one system");
            }
            return route;
        }
        for (int index = 0; index < path.size() - 1; index++) {
            if (!topology.neighbors(path.get(index)).contains(path.get(index + 1))) {
                throw new IllegalArgumentException("route contains a non-neighbor shortcut");
            }
        }
        return route;
    }

    private static List<CommodityRequirement> canonicalRequirements(List<CommodityRequirement> requirements) {
        Objects.requireNonNull(requirements, "requirements");
        ArrayList<CommodityRequirement> copy = new ArrayList<>(requirements);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("requirements must be non-empty and contain no nulls");
        }
        copy.sort(Comparator.comparing(CommodityRequirement::commodityId));
        Set<String> ids = new HashSet<>();
        for (CommodityRequirement requirement : copy) {
            if (!ids.add(requirement.commodityId())) {
                throw new IllegalArgumentException("duplicate requirement: " + requirement.commodityId());
            }
        }
        return List.copyOf(copy);
    }

    private record ServiceArc(SupplyKey supplyKey, double capacityKgPerSecond, boolean local) {
        private ServiceArc {
            Objects.requireNonNull(supplyKey, "supplyKey");
            requirePositiveFinite(capacityKgPerSecond, "capacityKgPerSecond");
        }
    }

    private record DemandArcSet(
            String stableFactionId,
            StarSystemId consumerSystemId,
            double requiredKgPerSecond,
            List<ServiceArc> serviceArcs) {
        private DemandArcSet {
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            Objects.requireNonNull(consumerSystemId, "consumerSystemId");
            requirePositiveFinite(requiredKgPerSecond, "requiredKgPerSecond");
            serviceArcs = List.copyOf(Objects.requireNonNull(serviceArcs, "serviceArcs"));
        }
    }

    private record CommodityFlowResult(List<Reservation> reservations, CommodityEvidence evidence) {}

    private static final class FlowNetwork {
        private final List<List<Edge>> graph;
        private final int source;
        private final int sink;
        private final Map<FlowArcKey, Edge> serviceEdges;

        private FlowNetwork(List<List<Edge>> graph, int source, int sink, Map<FlowArcKey, Edge> serviceEdges) {
            this.graph = graph;
            this.source = source;
            this.sink = sink;
            this.serviceEdges = serviceEdges;
        }

        private static FlowNetwork build(
                SupplyThroughputReport supply,
                List<DemandArcSet> demands,
                Set<SupplyKey> participatingProducers) {
            ArrayList<SupplyKey> producers = new ArrayList<>(participatingProducers);
            producers.sort(Comparator.naturalOrder());
            int source = 0;
            int producerOffset = 1;
            int demandOffset = producerOffset + producers.size();
            int sink = demandOffset + demands.size();
            ArrayList<List<Edge>> graph = new ArrayList<>();
            for (int index = 0; index <= sink; index++) {
                graph.add(new ArrayList<>());
            }
            HashMap<SupplyKey, Integer> producerNodes = new HashMap<>();
            for (int index = 0; index < producers.size(); index++) {
                SupplyKey key = producers.get(index);
                int node = producerOffset + index;
                producerNodes.put(key, node);
                double capacity = supply.capacityKgPerSecondBySupply().getOrDefault(key, 0d);
                if (capacity > EPSILON) {
                    addEdge(graph, source, node, capacity);
                }
            }
            LinkedHashMap<FlowArcKey, Edge> serviceEdges = new LinkedHashMap<>();
            for (int demandIndex = 0; demandIndex < demands.size(); demandIndex++) {
                DemandArcSet demand = demands.get(demandIndex);
                int demandNode = demandOffset + demandIndex;
                addEdge(graph, demandNode, sink, demand.requiredKgPerSecond());
                for (ServiceArc arc : demand.serviceArcs()) {
                    Integer producerNode = producerNodes.get(arc.supplyKey());
                    if (producerNode == null) {
                        throw new IllegalArgumentException("selected service arc references missing producer capacity");
                    }
                    Edge edge = addEdge(graph, producerNode, demandNode, arc.capacityKgPerSecond());
                    FlowArcKey key = new FlowArcKey(arc.supplyKey(), demand.stableFactionId(), demand.consumerSystemId(), arc.local());
                    if (serviceEdges.put(key, edge) != null) {
                        throw new IllegalArgumentException("duplicate selected producer-to-start service arc");
                    }
                }
            }
            return new FlowNetwork(graph, source, sink, Collections.unmodifiableMap(serviceEdges));
        }

        private double maxFlow() {
            double total = 0d;
            while (true) {
                Edge[] parentEdge = new Edge[graph.size()];
                int[] parentNode = new int[graph.size()];
                java.util.Arrays.fill(parentNode, -1);
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                queue.add(source);
                parentNode[source] = source;
                while (!queue.isEmpty() && parentNode[sink] < 0) {
                    int node = queue.removeFirst();
                    List<Edge> edges = graph.get(node);
                    for (Edge edge : edges) {
                        if (edge.residualCapacity <= EPSILON || parentNode[edge.to] >= 0) {
                            continue;
                        }
                        parentNode[edge.to] = node;
                        parentEdge[edge.to] = edge;
                        queue.addLast(edge.to);
                        if (edge.to == sink) {
                            break;
                        }
                    }
                }
                if (parentNode[sink] < 0) {
                    break;
                }
                double augmentation = Double.POSITIVE_INFINITY;
                for (int node = sink; node != source; node = parentNode[node]) {
                    augmentation = Math.min(augmentation, parentEdge[node].residualCapacity);
                }
                requirePositiveFinite(augmentation, "flow augmentation");
                for (int node = sink; node != source; node = parentNode[node]) {
                    Edge edge = parentEdge[node];
                    edge.residualCapacity -= augmentation;
                    graph.get(edge.to).get(edge.reverseIndex).residualCapacity += augmentation;
                }
                total = finiteAdd(total, augmentation);
            }
            return total;
        }

        private ArrayList<Reservation> reservations(String commodityId, List<DemandArcSet> demands) {
            HashMap<String, DemandArcSet> demandByFaction = new HashMap<>();
            for (DemandArcSet demand : demands) {
                demandByFaction.put(demand.stableFactionId(), demand);
            }
            ArrayList<Reservation> result = new ArrayList<>();
            for (Map.Entry<FlowArcKey, Edge> entry : serviceEdges.entrySet()) {
                Edge edge = entry.getValue();
                double used = edge.originalCapacity - edge.residualCapacity;
                if (used <= EPSILON) {
                    continue;
                }
                FlowArcKey key = entry.getKey();
                DemandArcSet demand = demandByFaction.get(key.stableFactionId());
                result.add(new Reservation(
                        key.stableFactionId(),
                        demand.consumerSystemId(),
                        commodityId,
                        key.supplyKey().systemId(),
                        used,
                        key.local()));
            }
            result.sort(RESERVATION_ORDER);
            return result;
        }
    }

    private record FlowArcKey(
            SupplyKey supplyKey,
            String stableFactionId,
            StarSystemId consumerSystemId,
            boolean local) {}

    private static final class Edge {
        private final int to;
        private final int reverseIndex;
        private final double originalCapacity;
        private double residualCapacity;

        private Edge(int to, int reverseIndex, double capacity) {
            this.to = to;
            this.reverseIndex = reverseIndex;
            this.originalCapacity = capacity;
            this.residualCapacity = capacity;
        }
    }

    private static Edge addEdge(List<List<Edge>> graph, int from, int to, double capacity) {
        requirePositiveFinite(capacity, "flow edge capacity");
        Edge forward = new Edge(to, graph.get(to).size(), capacity);
        Edge reverse = new Edge(from, graph.get(from).size(), 0d);
        graph.get(from).add(forward);
        graph.get(to).add(reverse);
        return forward;
    }

    private static double finiteAdd(double first, double second) {
        requireNonNegativeFinite(first, "first addend");
        requireNonNegativeFinite(second, "second addend");
        double result = first + second;
        if (!Double.isFinite(result)) {
            throw new IllegalStateException("throughput sum overflow");
        }
        return result;
    }

    private static double finiteMultiply(double value, int count) {
        requirePositiveFinite(value, "multiplicand");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
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

    private static final Comparator<Reservation> RESERVATION_ORDER = Comparator
            .comparing(Reservation::commodityId)
            .thenComparing(Reservation::producerSystemId)
            .thenComparing(Reservation::stableFactionId)
            .thenComparing(Reservation::consumerSystemId);
}
