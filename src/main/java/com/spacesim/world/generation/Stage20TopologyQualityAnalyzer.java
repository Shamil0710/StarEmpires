package com.spacesim.world.generation;

import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;
import com.spacesim.world.generation.Stage20TopologyQualityReport.Violation;
import com.spacesim.world.generation.Stage20TopologyQualityReport.ViolationType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Pure deterministic Stage-20D structural quality analysis over {@link GalaxyTopology}. */
public final class Stage20TopologyQualityAnalyzer {
    private Stage20TopologyQualityAnalyzer() {
        throw new AssertionError("Stage20TopologyQualityAnalyzer has no instances");
    }

    /**
     * Measures one ordinary generated jump graph against the current versioned Stage-20A budgets.
     *
     * <p>For the v1 {@code minCoreEdgeDisjointRoutes == 2} budget, deterministic regional core nodes
     * are the highest-degree system of each sector (lowest stable ID breaks ties). Two such nodes
     * satisfy the redundancy gate exactly when they occupy the same bridge-free component. Regional
     * hop probes are measured only between sectors that share at least one explicit ordinary edge.
     * This operationalizes the contract's "major regional nodes" and "representative route bands"
     * without inventing an economic hub tag before Stage 20E.</p>
     *
     * @param topology immutable ordinary jump topology
     * @param quality versioned Stage-20A topology-quality budget
     * @return deterministic machine-readable report
     */
    public static Stage20TopologyQualityReport analyze(
            GalaxyTopology topology,
            Stage20TopologyQualityCalibrationProfile quality) {
        GalaxyTopology graph = Objects.requireNonNull(topology, "topology");
        Stage20TopologyQualityCalibrationProfile policy = Objects.requireNonNull(quality, "quality");
        if (policy.structuralBudget().minCoreEdgeDisjointRoutes() != 2) {
            throw new IllegalArgumentException(
                    "Stage20D v1 analyzer supports the calibrated two-edge-disjoint core budget only");
        }

        List<StarSystemId> ids = graph.systems().stream().map(StarSystemNode::id).toList();
        Map<StarSystemId, List<StarSystemId>> adjacency = adjacency(graph, ids);
        Connectivity connectivity = connectivity(ids, adjacency, null);
        Criticality criticality = criticality(ids, adjacency);

        TreeMap<Integer, Integer> degreeHistogram = new TreeMap<>();
        List<Integer> degrees = new ArrayList<>(ids.size());
        List<StarSystemId> hubs = new ArrayList<>();
        int degreeOne = 0;
        int degreeTwo = 0;
        int hubMin = policy.hubDegreeBand().minInclusive();
        int hubMax = policy.hubDegreeBand().maxInclusive();
        List<Violation> violations = new ArrayList<>();
        for (StarSystemId id : ids) {
            int degree = adjacency.get(id).size();
            degrees.add(degree);
            degreeHistogram.merge(degree, 1, Integer::sum);
            if (degree == 1) {
                degreeOne++;
            }
            if (degree == 2) {
                degreeTwo++;
            }
            if (degree >= hubMin && degree <= hubMax) {
                hubs.add(id);
            }
            if (degree > hubMax) {
                violations.add(violation(
                        ViolationType.HUB_DEGREE_ABOVE_BAND,
                        id.toString(),
                        degree,
                        hubMax));
            }
        }
        Collections.sort(degrees);
        double meanDegree = degrees.isEmpty()
                ? 0d
                : degrees.stream().mapToInt(Integer::intValue).average().orElse(0d);
        double medianDegree = medianInt(degrees);
        double degreeOneFraction = fraction(degreeOne, ids.size());
        double degreeTwoFraction = fraction(degreeTwo, ids.size());

        Set<JumpConnection> bridgeSet = Set.copyOf(criticality.bridges());
        List<Integer> corridors = corridorLengths(ids, adjacency, bridgeSet);
        int longestCorridor = corridors.isEmpty() ? 0 : corridors.get(corridors.size() - 1);
        double p90Corridor = nearestRankPercentile(corridors, 0.90d);

        Set<StarSystemId> cycleSystems = new HashSet<>();
        for (JumpConnection edge : graph.connections()) {
            if (!bridgeSet.contains(edge)) {
                cycleSystems.add(edge.first());
                cycleSystems.add(edge.second());
            }
        }
        double cycleCoverage = fraction(cycleSystems.size(), ids.size());

        SectorDiagnostics sectorDiagnostics = sectorDiagnostics(graph, policy);
        List<StarSystemId> coreSystems = deterministicSectorCores(graph, adjacency);
        CoreRedundancy coreRedundancy = coreRedundancy(coreSystems, adjacency, bridgeSet);
        RegionalHops regionalHops = regionalHops(graph, adjacency, coreSystems);
        double gatewayDependency = gatewayDependency(graph, adjacency);

        if (connectivity.componentCount() > 1) {
            violations.add(violation(
                    ViolationType.DISCONNECTED,
                    "galaxy",
                    connectivity.componentCount(),
                    1d));
        }
        if (degreeOneFraction > policy.structuralBudget().maxDegreeOneFraction()) {
            violations.add(violation(
                    ViolationType.EXCESS_DEGREE_ONE_FRACTION,
                    "galaxy",
                    degreeOneFraction,
                    policy.structuralBudget().maxDegreeOneFraction()));
        }
        if (longestCorridor > policy.structuralBudget().maxLinearCorridorLengthEdges()) {
            violations.add(violation(
                    ViolationType.EXCESS_LINEAR_CORRIDOR,
                    "galaxy",
                    longestCorridor,
                    policy.structuralBudget().maxLinearCorridorLengthEdges()));
        }
        if (cycleCoverage < policy.structuralBudget().minRegionalCycleCoverage()) {
            violations.add(violationLowerBound(
                    ViolationType.INSUFFICIENT_CYCLE_COVERAGE,
                    "galaxy",
                    cycleCoverage,
                    policy.structuralBudget().minRegionalCycleCoverage()));
        }
        if (coreRedundancy.checkedPairs() > 0
                && coreRedundancy.redundantPairs() < coreRedundancy.checkedPairs()) {
            violations.add(violationLowerBound(
                    ViolationType.INSUFFICIENT_CORE_ROUTE_REDUNDANCY,
                    "regional-cores",
                    coreRedundancy.coverage(),
                    1d));
        }
        if (gatewayDependency > policy.structuralBudget().maxSingleGatewayDependency()) {
            violations.add(violation(
                    ViolationType.EXCESS_GATEWAY_DEPENDENCY,
                    "galaxy",
                    gatewayDependency,
                    policy.structuralBudget().maxSingleGatewayDependency()));
        }
        violations.addAll(sectorDiagnostics.violations());
        for (int hops : regionalHops.distances()) {
            if (hops < policy.regionalHopDistanceBand().minInclusive()) {
                violations.add(violationLowerBound(
                        ViolationType.REGIONAL_HOP_BELOW_BAND,
                        "regional-hop-" + hops,
                        hops,
                        policy.regionalHopDistanceBand().minInclusive()));
            } else if (hops > policy.regionalHopDistanceBand().maxInclusive()) {
                violations.add(violation(
                        ViolationType.REGIONAL_HOP_ABOVE_BAND,
                        "regional-hop-" + hops,
                        hops,
                        policy.regionalHopDistanceBand().maxInclusive()));
            }
        }

        return new Stage20TopologyQualityReport(
                connectivity.componentCount(),
                connectivity.unreachableSystems(),
                unreachableSectors(graph, connectivity.primaryComponent()),
                degreeHistogram,
                degreeOneFraction,
                degreeTwoFraction,
                meanDegree,
                medianDegree,
                hubs,
                corridors,
                longestCorridor,
                p90Corridor,
                cycleCoverage,
                coreRedundancy.checkedPairs(),
                coreRedundancy.redundantPairs(),
                coreRedundancy.coverage(),
                criticality.articulations(),
                criticality.bridges(),
                sectorDiagnostics.exitCounts(),
                sectorDiagnostics.internalCycleCoverage(),
                sectorDiagnostics.internalBridgeCounts(),
                gatewayDependency,
                regionalHops.distances(),
                regionalHops.median(),
                sectorDiagnostics.motifs(),
                violations);
    }

    private static Map<StarSystemId, List<StarSystemId>> adjacency(
            GalaxyTopology graph,
            List<StarSystemId> ids) {
        LinkedHashMap<StarSystemId, List<StarSystemId>> result = new LinkedHashMap<>();
        for (StarSystemId id : ids) {
            result.put(id, graph.neighbors(id));
        }
        return Map.copyOf(result);
    }

    private static Connectivity connectivity(
            List<StarSystemId> ids,
            Map<StarSystemId, List<StarSystemId>> adjacency,
            StarSystemId removed) {
        List<Set<StarSystemId>> components = new ArrayList<>();
        Set<StarSystemId> visited = new HashSet<>();
        for (StarSystemId start : ids) {
            if (start.equals(removed) || !visited.add(start)) {
                continue;
            }
            TreeSet<StarSystemId> component = new TreeSet<>();
            Deque<StarSystemId> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                StarSystemId current = queue.removeFirst();
                component.add(current);
                for (StarSystemId next : adjacency.get(current)) {
                    if (!next.equals(removed) && visited.add(next)) {
                        queue.addLast(next);
                    }
                }
            }
            components.add(Set.copyOf(component));
        }
        components.sort(Comparator.comparing(component -> component.stream().min(Comparator.naturalOrder()).orElseThrow()));
        Set<StarSystemId> primary = components.isEmpty() ? Set.of() : components.get(0);
        List<StarSystemId> unreachable = ids.stream()
                .filter(id -> !id.equals(removed) && !primary.contains(id))
                .toList();
        return new Connectivity(components.size(), primary, unreachable, components);
    }

    private static Criticality criticality(
            List<StarSystemId> ids,
            Map<StarSystemId, List<StarSystemId>> adjacency) {
        Map<StarSystemId, Integer> discovery = new HashMap<>();
        Map<StarSystemId, Integer> low = new HashMap<>();
        TreeSet<StarSystemId> articulations = new TreeSet<>();
        TreeSet<JumpConnection> bridges = new TreeSet<>();
        int[] time = {0};
        for (StarSystemId id : ids) {
            if (!discovery.containsKey(id)) {
                tarjan(id, null, adjacency, discovery, low, articulations, bridges, time);
            }
        }
        return new Criticality(List.copyOf(articulations), List.copyOf(bridges));
    }

    private static void tarjan(
            StarSystemId current,
            StarSystemId parent,
            Map<StarSystemId, List<StarSystemId>> adjacency,
            Map<StarSystemId, Integer> discovery,
            Map<StarSystemId, Integer> low,
            Set<StarSystemId> articulations,
            Set<JumpConnection> bridges,
            int[] time) {
        int discovered = ++time[0];
        discovery.put(current, discovered);
        low.put(current, discovered);
        int children = 0;
        for (StarSystemId next : adjacency.get(current)) {
            if (next.equals(parent)) {
                continue;
            }
            if (!discovery.containsKey(next)) {
                children++;
                tarjan(next, current, adjacency, discovery, low, articulations, bridges, time);
                low.put(current, Math.min(low.get(current), low.get(next)));
                if (parent != null && low.get(next) >= discovery.get(current)) {
                    articulations.add(current);
                }
                if (low.get(next) > discovery.get(current)) {
                    bridges.add(new JumpConnection(current, next));
                }
            } else {
                low.put(current, Math.min(low.get(current), discovery.get(next)));
            }
        }
        if (parent == null && children > 1) {
            articulations.add(current);
        }
    }

    private static List<Integer> corridorLengths(
            List<StarSystemId> ids,
            Map<StarSystemId, List<StarSystemId>> adjacency,
            Set<JumpConnection> bridges) {
        if (bridges.isEmpty()) {
            return List.of();
        }
        Map<StarSystemId, List<StarSystemId>> bridgeNeighbors = new HashMap<>();
        for (StarSystemId id : ids) {
            bridgeNeighbors.put(id, new ArrayList<>());
        }
        for (JumpConnection edge : bridges) {
            bridgeNeighbors.get(edge.first()).add(edge.second());
            bridgeNeighbors.get(edge.second()).add(edge.first());
        }
        for (List<StarSystemId> values : bridgeNeighbors.values()) {
            values.sort(Comparator.naturalOrder());
        }

        Set<JumpConnection> visited = new HashSet<>();
        List<Integer> result = new ArrayList<>();
        for (StarSystemId start : ids) {
            if (isCorridorIntermediate(start, adjacency, bridgeNeighbors)) {
                continue;
            }
            for (StarSystemId next : bridgeNeighbors.get(start)) {
                JumpConnection first = new JumpConnection(start, next);
                if (visited.contains(first)) {
                    continue;
                }
                int length = walkCorridor(start, next, adjacency, bridgeNeighbors, visited);
                if (length > 0) {
                    result.add(length);
                }
            }
        }
        for (JumpConnection edge : new TreeSet<>(bridges)) {
            if (!visited.contains(edge)) {
                int length = walkCorridor(edge.first(), edge.second(), adjacency, bridgeNeighbors, visited);
                if (length > 0) {
                    result.add(length);
                }
            }
        }
        result.sort(Integer::compareTo);
        return List.copyOf(result);
    }

    private static int walkCorridor(
            StarSystemId previous,
            StarSystemId current,
            Map<StarSystemId, List<StarSystemId>> adjacency,
            Map<StarSystemId, List<StarSystemId>> bridgeNeighbors,
            Set<JumpConnection> visited) {
        int length = 0;
        StarSystemId from = previous;
        StarSystemId at = current;
        while (true) {
            JumpConnection edge = new JumpConnection(from, at);
            if (!visited.add(edge)) {
                return length;
            }
            length++;
            if (!isCorridorIntermediate(at, adjacency, bridgeNeighbors)) {
                return length;
            }
            StarSystemId next = bridgeNeighbors.get(at).get(0).equals(from)
                    ? bridgeNeighbors.get(at).get(1)
                    : bridgeNeighbors.get(at).get(0);
            from = at;
            at = next;
        }
    }

    private static boolean isCorridorIntermediate(
            StarSystemId id,
            Map<StarSystemId, List<StarSystemId>> adjacency,
            Map<StarSystemId, List<StarSystemId>> bridgeNeighbors) {
        return adjacency.get(id).size() == 2 && bridgeNeighbors.get(id).size() == 2;
    }

    private static SectorDiagnostics sectorDiagnostics(
            GalaxyTopology graph,
            Stage20TopologyQualityCalibrationProfile policy) {
        TreeMap<SectorId, Integer> exits = new TreeMap<>();
        TreeMap<SectorId, Double> internalCycles = new TreeMap<>();
        TreeMap<SectorId, Integer> internalBridges = new TreeMap<>();
        TreeMap<SectorId, String> motifs = new TreeMap<>();
        List<Violation> violations = new ArrayList<>();
        for (SectorNode sector : graph.sectors()) {
            exits.put(sector.id(), 0);
        }
        for (JumpConnection edge : graph.connections()) {
            SectorId first = graph.sectorOf(edge.first()).orElseThrow().id();
            SectorId second = graph.sectorOf(edge.second()).orElseThrow().id();
            if (!first.equals(second)) {
                exits.merge(first, 1, Integer::sum);
                exits.merge(second, 1, Integer::sum);
            }
        }

        for (SectorNode sector : graph.sectors()) {
            InternalSectorMetrics internal = internalSectorMetrics(graph, sector);
            internalCycles.put(sector.id(), internal.cycleCoverage());
            internalBridges.put(sector.id(), internal.bridgeCount());
            motifs.put(sector.id(), motif(internal));
            if (graph.sectors().size() > 1) {
                int exitCount = exits.get(sector.id());
                if (exitCount < policy.sectorExitBand().minInclusive()) {
                    violations.add(violationLowerBound(
                            ViolationType.SECTOR_EXIT_BELOW_BAND,
                            sector.id().toString(),
                            exitCount,
                            policy.sectorExitBand().minInclusive()));
                }
                if (exitCount > policy.sectorExitBand().maxInclusive()) {
                    violations.add(violation(
                            ViolationType.SECTOR_EXIT_ABOVE_BAND,
                            sector.id().toString(),
                            exitCount,
                            policy.sectorExitBand().maxInclusive()));
                }
            }
        }
        return new SectorDiagnostics(exits, internalCycles, internalBridges, motifs, violations);
    }

    private static InternalSectorMetrics internalSectorMetrics(GalaxyTopology graph, SectorNode sector) {
        Set<StarSystemId> sectorIds = new HashSet<>();
        for (StarSystemNode system : sector.systems()) {
            sectorIds.add(system.id());
        }
        Map<StarSystemId, List<StarSystemId>> adjacency = new HashMap<>();
        for (StarSystemId id : sectorIds) {
            List<StarSystemId> neighbors = graph.neighbors(id).stream().filter(sectorIds::contains).toList();
            adjacency.put(id, neighbors);
        }
        List<StarSystemId> ordered = sector.systems().stream().map(StarSystemNode::id).toList();
        Criticality criticality = criticality(ordered, adjacency);
        Set<JumpConnection> bridgeSet = Set.copyOf(criticality.bridges());
        Set<StarSystemId> cycle = new HashSet<>();
        int edgeCount = 0;
        int maxDegree = 0;
        int degreeOne = 0;
        for (StarSystemId id : ordered) {
            int degree = adjacency.get(id).size();
            maxDegree = Math.max(maxDegree, degree);
            if (degree == 1) {
                degreeOne++;
            }
            edgeCount += degree;
            for (StarSystemId next : adjacency.get(id)) {
                JumpConnection edge = new JumpConnection(id, next);
                if (!bridgeSet.contains(edge)) {
                    cycle.add(id);
                    cycle.add(next);
                }
            }
        }
        return new InternalSectorMetrics(
                ordered.size(),
                edgeCount / 2,
                criticality.bridges().size(),
                fraction(cycle.size(), ordered.size()),
                degreeOne,
                maxDegree);
    }

    private static String motif(InternalSectorMetrics value) {
        if (value.systemCount() <= 2) {
            return "SMALL_REGION";
        }
        if (value.cycleCoverage() >= 0.75d && value.degreeOneCount() > 0) {
            return "CYCLIC_CORE_WITH_FRONTIER";
        }
        if (value.cycleCoverage() >= 0.75d && value.maxDegree() >= 3) {
            return "HUB_MESH";
        }
        if (value.cycleCoverage() >= 0.75d) {
            return "RING";
        }
        if (value.bridgeCount() == value.edgeCount()) {
            return value.degreeOneCount() >= 2 ? "CORRIDOR_TREE" : "TREE";
        }
        return "MIXED";
    }

    private static List<StarSystemId> deterministicSectorCores(
            GalaxyTopology graph,
            Map<StarSystemId, List<StarSystemId>> adjacency) {
        List<StarSystemId> result = new ArrayList<>();
        for (SectorNode sector : graph.sectors()) {
            StarSystemId core = sector.systems().stream()
                    .map(StarSystemNode::id)
                    .max(Comparator.comparingInt((StarSystemId id) -> adjacency.get(id).size())
                            .thenComparing(Comparator.reverseOrder()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Stage20D ordinary sector must contain at least one system: " + sector.id()));
            result.add(core);
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static CoreRedundancy coreRedundancy(
            List<StarSystemId> cores,
            Map<StarSystemId, List<StarSystemId>> adjacency,
            Set<JumpConnection> bridges) {
        int checked = 0;
        int redundant = 0;
        for (int firstIndex = 0; firstIndex < cores.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < cores.size(); secondIndex++) {
                checked++;
                if (connectedWithoutBridges(cores.get(firstIndex), cores.get(secondIndex), adjacency, bridges)) {
                    redundant++;
                }
            }
        }
        return new CoreRedundancy(checked, redundant, checked == 0 ? 1d : (double) redundant / checked);
    }

    private static boolean connectedWithoutBridges(
            StarSystemId origin,
            StarSystemId destination,
            Map<StarSystemId, List<StarSystemId>> adjacency,
            Set<JumpConnection> bridges) {
        if (origin.equals(destination)) {
            return true;
        }
        Set<StarSystemId> visited = new HashSet<>();
        Deque<StarSystemId> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            for (StarSystemId next : adjacency.get(current)) {
                if (bridges.contains(new JumpConnection(current, next)) || !visited.add(next)) {
                    continue;
                }
                if (next.equals(destination)) {
                    return true;
                }
                queue.addLast(next);
            }
        }
        return false;
    }

    private static RegionalHops regionalHops(
            GalaxyTopology graph,
            Map<StarSystemId, List<StarSystemId>> adjacency,
            List<StarSystemId> cores) {
        Map<SectorId, StarSystemId> coreBySector = new TreeMap<>();
        for (StarSystemId core : cores) {
            coreBySector.put(graph.sectorOf(core).orElseThrow().id(), core);
        }
        TreeSet<SectorPair> adjacentSectorPairs = new TreeSet<>();
        for (JumpConnection edge : graph.connections()) {
            SectorId first = graph.sectorOf(edge.first()).orElseThrow().id();
            SectorId second = graph.sectorOf(edge.second()).orElseThrow().id();
            if (!first.equals(second)) {
                adjacentSectorPairs.add(new SectorPair(first, second));
            }
        }
        List<Integer> distances = new ArrayList<>();
        for (SectorPair pair : adjacentSectorPairs) {
            int hops = shortestHops(coreBySector.get(pair.first()), coreBySector.get(pair.second()), adjacency);
            if (hops >= 0) {
                distances.add(hops);
            }
        }
        distances.sort(Integer::compareTo);
        OptionalDouble median = distances.isEmpty()
                ? OptionalDouble.empty()
                : OptionalDouble.of(medianInt(distances));
        return new RegionalHops(List.copyOf(distances), median);
    }

    private static int shortestHops(
            StarSystemId origin,
            StarSystemId destination,
            Map<StarSystemId, List<StarSystemId>> adjacency) {
        if (origin.equals(destination)) {
            return 0;
        }
        Map<StarSystemId, Integer> distance = new HashMap<>();
        Deque<StarSystemId> queue = new ArrayDeque<>();
        distance.put(origin, 0);
        queue.add(origin);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            int nextDistance = distance.get(current) + 1;
            for (StarSystemId next : adjacency.get(current)) {
                if (distance.putIfAbsent(next, nextDistance) != null) {
                    continue;
                }
                if (next.equals(destination)) {
                    return nextDistance;
                }
                queue.addLast(next);
            }
        }
        return -1;
    }

    private static double gatewayDependency(
            GalaxyTopology graph,
            Map<StarSystemId, List<StarSystemId>> adjacency) {
        TreeSet<StarSystemId> gateways = new TreeSet<>();
        for (JumpConnection edge : graph.connections()) {
            SectorId first = graph.sectorOf(edge.first()).orElseThrow().id();
            SectorId second = graph.sectorOf(edge.second()).orElseThrow().id();
            if (!first.equals(second)) {
                gateways.add(edge.first());
                gateways.add(edge.second());
            }
        }
        if (gateways.isEmpty() || graph.systems().size() <= 1) {
            return 0d;
        }
        List<StarSystemId> ids = graph.systems().stream().map(StarSystemNode::id).toList();
        double maximum = 0d;
        for (StarSystemId gateway : gateways) {
            Connectivity removed = connectivity(ids, adjacency, gateway);
            int remaining = ids.size() - 1;
            int largest = removed.components().stream().mapToInt(Set::size).max().orElse(0);
            double dependent = remaining <= 0 ? 0d : (double) (remaining - largest) / ids.size();
            maximum = Math.max(maximum, dependent);
        }
        return maximum;
    }

    private static List<SectorId> unreachableSectors(
            GalaxyTopology graph,
            Set<StarSystemId> primaryComponent) {
        List<SectorId> result = new ArrayList<>();
        for (SectorNode sector : graph.sectors()) {
            boolean anyReachable = sector.systems().stream().map(StarSystemNode::id).anyMatch(primaryComponent::contains);
            if (!anyReachable) {
                result.add(sector.id());
            }
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static Violation violation(
            ViolationType type,
            String subject,
            double observed,
            double upperLimit) {
        double denominator = Math.max(Math.abs(upperLimit), 1d);
        return new Violation(type, subject, observed, upperLimit, Math.max(0d, observed - upperLimit) / denominator);
    }

    private static Violation violationLowerBound(
            ViolationType type,
            String subject,
            double observed,
            double lowerLimit) {
        double denominator = Math.max(Math.abs(lowerLimit), 1d);
        return new Violation(type, subject, observed, lowerLimit, Math.max(0d, lowerLimit - observed) / denominator);
    }

    private static double fraction(int numerator, int denominator) {
        return denominator <= 0 ? 0d : (double) numerator / denominator;
    }

    private static double medianInt(List<Integer> sorted) {
        if (sorted.isEmpty()) {
            return 0d;
        }
        int middle = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2d;
    }

    private static double nearestRankPercentile(List<Integer> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0d;
        }
        int rank = (int) Math.ceil(percentile * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }

    private record Connectivity(
            int componentCount,
            Set<StarSystemId> primaryComponent,
            List<StarSystemId> unreachableSystems,
            List<Set<StarSystemId>> components) {
    }

    private record Criticality(List<StarSystemId> articulations, List<JumpConnection> bridges) {
    }

    private record InternalSectorMetrics(
            int systemCount,
            int edgeCount,
            int bridgeCount,
            double cycleCoverage,
            int degreeOneCount,
            int maxDegree) {
    }

    private record SectorDiagnostics(
            Map<SectorId, Integer> exitCounts,
            Map<SectorId, Double> internalCycleCoverage,
            Map<SectorId, Integer> internalBridgeCounts,
            Map<SectorId, String> motifs,
            List<Violation> violations) {
    }

    private record CoreRedundancy(int checkedPairs, int redundantPairs, double coverage) {
    }

    private record RegionalHops(List<Integer> distances, OptionalDouble median) {
    }

    private record SectorPair(SectorId first, SectorId second) implements Comparable<SectorPair> {
        private SectorPair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (first.compareTo(second) > 0) {
                SectorId swap = first;
                first = second;
                second = swap;
            }
            if (first.equals(second)) {
                throw new IllegalArgumentException("sector pair must contain distinct sectors");
            }
        }

        @Override
        public int compareTo(SectorPair other) {
            int firstOrder = first.compareTo(other.first);
            return firstOrder != 0 ? firstOrder : second.compareTo(other.second);
        }
    }
}
