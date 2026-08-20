package com.spacesim.world.generation;

import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;
import com.spacesim.world.generation.Stage20JumpTopologyGenerationResult.Status;
import com.spacesim.world.generation.Stage20TopologyQualityReport.ViolationType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Pure deterministic Stage-20D v1 ordinary jump-topology generator.
 *
 * <p>The generator consumes already generated sector/system geometry and materializes only explicit
 * ordinary {@link JumpConnection} edges. It never introduces a second distance system or a direct
 * non-neighbor shortcut. Spatial coordinates rank candidate edges; the accepted Stage-20A profile
 * supplies graph-quality budgets. Failed candidates receive bounded deterministic edge-addition
 * repair and are rejected when no strictly improving physical candidate remains.</p>
 */
public final class Stage20JumpTopologyGenerator {
    private static final int MIN_FRONTIER_SECTOR_SIZE = 7;
    private static final int MAX_REPAIR_PASSES = 64;
    private static final int REPAIR_SCAN_PER_SCOPE = 256;

    private Stage20JumpTopologyGenerator() {
        throw new AssertionError("Stage20JumpTopologyGenerator has no instances");
    }

    /**
     * Generates one ordinary jump graph from immutable Stage-20 sector/system geometry.
     *
     * @param galaxyId stable galaxy ID
     * @param galaxyName stable display name
     * @param sectors generated sectors with immutable system coordinates
     * @param seed authoritative world/generation seed used only for deterministic tie-breaking
     * @param quality versioned Stage-20A topology-quality policy
     * @return accepted topology or explicit rejected-seed diagnostics
     */
    public static Stage20JumpTopologyGenerationResult generate(
            GalaxyId galaxyId,
            String galaxyName,
            List<SectorNode> sectors,
            long seed,
            Stage20TopologyQualityCalibrationProfile quality) {
        Objects.requireNonNull(galaxyId, "galaxyId");
        if (galaxyName == null || galaxyName.isBlank()) {
            throw new IllegalArgumentException("galaxyName must not be blank");
        }
        Objects.requireNonNull(sectors, "sectors");
        Objects.requireNonNull(quality, "quality");
        if (!quality.closesStage20BEntryCoverage()) {
            throw new IllegalArgumentException("Stage20D requires a closed accepted Stage-20A topology-quality profile");
        }
        if (sectors.isEmpty()) {
            throw new IllegalArgumentException("Stage20D ordinary galaxy must contain at least one sector");
        }

        List<SectorNode> orderedSectors = sectors.stream().sorted(Comparator.comparing(SectorNode::id)).toList();
        Set<StarSystemId> uniqueSystems = new HashSet<>();
        for (SectorNode sector : orderedSectors) {
            Objects.requireNonNull(sector, "sector");
            if (sector.systems().isEmpty()) {
                throw new IllegalArgumentException("Stage20D ordinary sector must contain systems: " + sector.id());
            }
            for (StarSystemNode system : sector.systems()) {
                if (!uniqueSystems.add(system.id())) {
                    throw new IllegalArgumentException("duplicate StarSystemId across sectors: " + system.id());
                }
            }
        }

        GenerationState state = initialState(orderedSectors, seed, quality);
        GalaxyTopology candidate = topology(galaxyId, galaxyName, orderedSectors, state.edges());
        Stage20TopologyQualityReport report = Stage20TopologyQualityAnalyzer.analyze(candidate, quality);
        if (report.accepted()) {
            return new Stage20JumpTopologyGenerationResult(seed, Status.ACCEPTED, candidate, report, 0);
        }

        List<EdgeCandidate> allCandidates = allMissingCandidates(orderedSectors, seed);
        int repairLimit = Math.min(MAX_REPAIR_PASSES, Math.max(4, uniqueSystems.size() * 2));
        int repairs = 0;
        while (repairs < repairLimit && !report.accepted()) {
            RepairChoice choice = bestRepair(
                    galaxyId,
                    galaxyName,
                    orderedSectors,
                    state,
                    report,
                    allCandidates,
                    quality);
            if (choice == null) {
                break;
            }
            state.edges().add(choice.edge());
            state.incrementDegree(choice.edge().first());
            state.incrementDegree(choice.edge().second());
            if (isCrossSector(choice.edge(), state.sectorBySystem())) {
                state.incrementSectorExit(state.sectorBySystem().get(choice.edge().first()));
                state.incrementSectorExit(state.sectorBySystem().get(choice.edge().second()));
            }
            candidate = choice.topology();
            report = choice.report();
            repairs++;
        }

        Status status = report.accepted() ? Status.ACCEPTED : Status.REJECTED_SEED;
        return new Stage20JumpTopologyGenerationResult(seed, status, candidate, report, repairs);
    }

    private static GenerationState initialState(
            List<SectorNode> sectors,
            long seed,
            Stage20TopologyQualityCalibrationProfile quality) {
        TreeSet<JumpConnection> edges = new TreeSet<>();
        TreeMap<StarSystemId, Integer> degrees = new TreeMap<>();
        TreeMap<SectorId, Integer> exits = new TreeMap<>();
        TreeMap<StarSystemId, SectorId> sectorBySystem = new TreeMap<>();
        LinkedHashMap<SectorId, SectorPlan> plans = new LinkedHashMap<>();
        for (SectorNode sector : sectors) {
            exits.put(sector.id(), 0);
            for (StarSystemNode system : sector.systems()) {
                degrees.put(system.id(), 0);
                sectorBySystem.put(system.id(), sector.id());
            }
            SectorPlan plan = buildSectorPlan(sector, seed);
            plans.put(sector.id(), plan);
            addInternalStructure(plan, edges, degrees, quality);
        }
        addInterSectorGateways(sectors, plans, edges, degrees, exits, seed, quality);
        return new GenerationState(edges, degrees, exits, sectorBySystem);
    }

    private static SectorPlan buildSectorPlan(SectorNode sector, long seed) {
        List<StarSystemNode> systems = sector.systems();
        Point centroid = centroid(systems);
        StarSystemNode selectedFrontier = systems.size() >= MIN_FRONTIER_SECTOR_SIZE
                ? systems.stream().max(Comparator.comparingDouble((StarSystemNode value) -> distanceSquared(value, centroid))
                        .thenComparingLong(value -> seededTie(seed, value.id().value())))
                        .orElseThrow()
                : null;
        List<StarSystemNode> core = systems.stream().filter(value -> value != selectedFrontier).toList();
        StarSystemNode frontier = selectedFrontier;
        if (core.isEmpty()) {
            core = systems;
            frontier = null;
        }
        List<StarSystemNode> ring = new ArrayList<>(core);
        ring.sort(Comparator.comparingDouble((StarSystemNode value) ->
                        StrictMath.atan2(value.y() - centroid.y(), value.x() - centroid.x()))
                .thenComparingDouble(value -> distanceSquared(value, centroid))
                .thenComparingLong(value -> seededTie(seed, value.id().value()))
                .thenComparing(StarSystemNode::id));
        StarSystemNode anchor = core.stream()
                .min(Comparator.comparingDouble((StarSystemNode value) -> distanceSquared(value, centroid))
                        .thenComparing(StarSystemNode::id))
                .orElseThrow();
        return new SectorPlan(sector, centroid, List.copyOf(ring), anchor, frontier);
    }

    private static void addInternalStructure(
            SectorPlan plan,
            Set<JumpConnection> edges,
            Map<StarSystemId, Integer> degrees,
            Stage20TopologyQualityCalibrationProfile quality) {
        List<StarSystemNode> core = plan.coreRing();
        if (core.size() == 2) {
            addEdge(new JumpConnection(core.get(0).id(), core.get(1).id()), edges, degrees);
        } else if (core.size() >= 3) {
            for (int index = 0; index < core.size(); index++) {
                addEdge(new JumpConnection(core.get(index).id(), core.get((index + 1) % core.size()).id()), edges, degrees);
            }
        }

        int targetAnchorDegree = Math.min(
                quality.hubDegreeBand().maxInclusive(),
                quality.hubDegreeBand().minInclusive() + 1);
        List<StarSystemNode> chordTargets = core.stream()
                .filter(value -> !value.id().equals(plan.anchor().id()))
                .sorted(Comparator.comparingDouble((StarSystemNode value) -> distanceSquared(plan.anchor(), value))
                        .thenComparing(StarSystemNode::id))
                .toList();
        for (StarSystemNode target : chordTargets) {
            if (degrees.get(plan.anchor().id()) >= targetAnchorDegree) {
                break;
            }
            if (degrees.get(target.id()) >= quality.hubDegreeBand().maxInclusive()) {
                continue;
            }
            addEdge(new JumpConnection(plan.anchor().id(), target.id()), edges, degrees);
        }

        if (plan.frontier() != null) {
            StarSystemNode attachment = core.stream()
                    .filter(value -> !value.id().equals(plan.anchor().id()))
                    .min(Comparator.comparingDouble((StarSystemNode value) -> distanceSquared(plan.frontier(), value))
                            .thenComparing(StarSystemNode::id))
                    .orElse(plan.anchor());
            addEdge(new JumpConnection(plan.frontier().id(), attachment.id()), edges, degrees);
        }
    }

    private static void addInterSectorGateways(
            List<SectorNode> sectors,
            Map<SectorId, SectorPlan> plans,
            Set<JumpConnection> edges,
            Map<StarSystemId, Integer> degrees,
            Map<SectorId, Integer> exits,
            long seed,
            Stage20TopologyQualityCalibrationProfile quality) {
        if (sectors.size() <= 1) {
            return;
        }
        Set<StarSystemId> usedGateways = new HashSet<>();
        if (sectors.size() == 2) {
            SectorPlan first = plans.get(sectors.get(0).id());
            SectorPlan second = plans.get(sectors.get(1).id());
            for (int index = 0; index < quality.sectorExitBand().minInclusive(); index++) {
                JumpConnection edge = selectGatewayEdge(
                        first, second, edges, degrees, usedGateways, seed + index, quality);
                if (edge == null) {
                    break;
                }
                addEdge(edge, edges, degrees);
                exits.merge(first.sector().id(), 1, Integer::sum);
                exits.merge(second.sector().id(), 1, Integer::sum);
                usedGateways.add(edge.first());
                usedGateways.add(edge.second());
            }
            return;
        }

        List<SectorPairCandidate> candidates = sectorPairCandidates(plans.values(), seed);
        TreeSet<SectorPairKey> selectedPairs = new TreeSet<>();
        TreeMap<SectorId, Integer> pairDegree = new TreeMap<>();
        TreeMap<SectorId, SectorId> parent = new TreeMap<>();
        for (SectorNode sector : sectors) {
            pairDegree.put(sector.id(), 0);
            parent.put(sector.id(), sector.id());
        }
        int maxExits = quality.sectorExitBand().maxInclusive();
        for (SectorPairCandidate candidate : candidates) {
            SectorId first = candidate.first().sector().id();
            SectorId second = candidate.second().sector().id();
            if (find(parent, first).equals(find(parent, second))) {
                continue;
            }
            if (pairDegree.get(first) >= maxExits || pairDegree.get(second) >= maxExits) {
                continue;
            }
            selectedPairs.add(new SectorPairKey(first, second));
            pairDegree.merge(first, 1, Integer::sum);
            pairDegree.merge(second, 1, Integer::sum);
            union(parent, first, second);
        }
        if (new HashSet<>(parent.keySet().stream().map(value -> find(parent, value)).toList()).size() > 1) {
            for (SectorPairCandidate candidate : candidates) {
                SectorId first = candidate.first().sector().id();
                SectorId second = candidate.second().sector().id();
                if (find(parent, first).equals(find(parent, second))) {
                    continue;
                }
                selectedPairs.add(new SectorPairKey(first, second));
                pairDegree.merge(first, 1, Integer::sum);
                pairDegree.merge(second, 1, Integer::sum);
                union(parent, first, second);
            }
        }

        int minExits = quality.sectorExitBand().minInclusive();
        boolean progress = true;
        while (progress && pairDegree.values().stream().anyMatch(value -> value < minExits)) {
            progress = false;
            for (SectorPairCandidate candidate : candidates) {
                SectorId first = candidate.first().sector().id();
                SectorId second = candidate.second().sector().id();
                SectorPairKey key = new SectorPairKey(first, second);
                if (selectedPairs.contains(key)
                        || pairDegree.get(first) >= maxExits
                        || pairDegree.get(second) >= maxExits
                        || (pairDegree.get(first) >= minExits && pairDegree.get(second) >= minExits)) {
                    continue;
                }
                selectedPairs.add(key);
                pairDegree.merge(first, 1, Integer::sum);
                pairDegree.merge(second, 1, Integer::sum);
                progress = true;
                if (pairDegree.values().stream().noneMatch(value -> value < minExits)) {
                    break;
                }
            }
        }

        for (SectorPairKey pair : selectedPairs) {
            SectorPlan first = plans.get(pair.first());
            SectorPlan second = plans.get(pair.second());
            JumpConnection edge = selectGatewayEdge(first, second, edges, degrees, usedGateways, seed, quality);
            if (edge == null) {
                continue;
            }
            addEdge(edge, edges, degrees);
            exits.merge(first.sector().id(), 1, Integer::sum);
            exits.merge(second.sector().id(), 1, Integer::sum);
            usedGateways.add(edge.first());
            usedGateways.add(edge.second());
        }
    }

    private static List<SectorPairCandidate> sectorPairCandidates(
            Collection<SectorPlan> plans,
            long seed) {
        List<SectorPlan> values = plans.stream().sorted(Comparator.comparing(value -> value.sector().id())).toList();
        List<SectorPairCandidate> result = new ArrayList<>();
        for (int first = 0; first < values.size(); first++) {
            for (int second = first + 1; second < values.size(); second++) {
                SectorPlan a = values.get(first);
                SectorPlan b = values.get(second);
                result.add(new SectorPairCandidate(
                        a,
                        b,
                        distanceSquared(a.centroid(), b.centroid()),
                        seededTie(seed, a.sector().id().value() * 31L + b.sector().id().value())));
            }
        }
        result.sort(Comparator.comparingDouble(SectorPairCandidate::distanceSquared)
                .thenComparingLong(SectorPairCandidate::tie)
                .thenComparing(value -> value.first().sector().id())
                .thenComparing(value -> value.second().sector().id()));
        return List.copyOf(result);
    }

    private static SectorId find(Map<SectorId, SectorId> parent, SectorId value) {
        SectorId current = value;
        while (!parent.get(current).equals(current)) {
            current = parent.get(current);
        }
        return current;
    }

    private static void union(Map<SectorId, SectorId> parent, SectorId first, SectorId second) {
        SectorId firstRoot = find(parent, first);
        SectorId secondRoot = find(parent, second);
        if (firstRoot.equals(secondRoot)) {
            return;
        }
        if (firstRoot.compareTo(secondRoot) < 0) {
            parent.put(secondRoot, firstRoot);
        } else {
            parent.put(firstRoot, secondRoot);
        }
    }

    private static JumpConnection selectGatewayEdge(
            SectorPlan first,
            SectorPlan second,
            Set<JumpConnection> existing,
            Map<StarSystemId, Integer> degrees,
            Set<StarSystemId> usedGateways,
            long seed,
            Stage20TopologyQualityCalibrationProfile quality) {
        List<StarSystemNode> firstCandidates = gatewayCandidates(first);
        List<StarSystemNode> secondCandidates = gatewayCandidates(second);
        int maxDegree = quality.hubDegreeBand().maxInclusive();
        return firstCandidates.stream()
                .flatMap(a -> secondCandidates.stream().map(b -> new GatewayCandidate(
                        a,
                        b,
                        usedGateways.contains(a.id()) ? 1 : 0,
                        usedGateways.contains(b.id()) ? 1 : 0,
                        distanceSquared(a, b),
                        seededTie(seed, a.id().value() * 31L + b.id().value()))))
                .filter(value -> degrees.get(value.first().id()) < maxDegree)
                .filter(value -> degrees.get(value.second().id()) < maxDegree)
                .filter(value -> !existing.contains(new JumpConnection(value.first().id(), value.second().id())))
                .min(Comparator.comparingInt(GatewayCandidate::firstReuse)
                        .thenComparingInt(GatewayCandidate::secondReuse)
                        .thenComparingDouble(GatewayCandidate::distanceSquared)
                        .thenComparingLong(GatewayCandidate::tie)
                        .thenComparing(value -> value.first().id())
                        .thenComparing(value -> value.second().id()))
                .map(value -> new JumpConnection(value.first().id(), value.second().id()))
                .orElse(null);
    }

    private static List<StarSystemNode> gatewayCandidates(SectorPlan plan) {
        List<StarSystemNode> result = plan.coreRing().stream()
                .filter(value -> !value.id().equals(plan.anchor().id()))
                .toList();
        return result.isEmpty() ? plan.coreRing() : result;
    }

    private static RepairChoice bestRepair(
            GalaxyId galaxyId,
            String galaxyName,
            List<SectorNode> sectors,
            GenerationState state,
            Stage20TopologyQualityReport currentReport,
            List<EdgeCandidate> allCandidates,
            Stage20TopologyQualityCalibrationProfile quality) {
        double currentPenalty = currentReport.repairPenalty();
        boolean needsCrossScope = currentReport.violations().stream().anyMatch(value -> switch (value.type()) {
            case DISCONNECTED,
                    INSUFFICIENT_CORE_ROUTE_REDUNDANCY,
                    EXCESS_GATEWAY_DEPENDENCY,
                    SECTOR_EXIT_BELOW_BAND,
                    REGIONAL_HOP_ABOVE_BAND -> true;
            default -> false;
        });

        List<EdgeCandidate> local = new ArrayList<>();
        List<EdgeCandidate> cross = new ArrayList<>();
        for (EdgeCandidate candidate : allCandidates) {
            if (state.edges().contains(candidate.edge())) {
                continue;
            }
            (candidate.crossSector() ? cross : local).add(candidate);
        }
        List<EdgeCandidate> scan = new ArrayList<>();
        if (needsCrossScope) {
            scan.addAll(cross.stream().limit(REPAIR_SCAN_PER_SCOPE).toList());
            scan.addAll(local.stream().limit(REPAIR_SCAN_PER_SCOPE).toList());
        } else {
            scan.addAll(local.stream().limit(REPAIR_SCAN_PER_SCOPE).toList());
            scan.addAll(cross.stream().limit(REPAIR_SCAN_PER_SCOPE).toList());
        }

        RepairChoice best = null;
        for (EdgeCandidate candidate : scan) {
            JumpConnection edge = candidate.edge();
            if (!canAdd(edge, state, quality)) {
                continue;
            }
            TreeSet<JumpConnection> testEdges = new TreeSet<>(state.edges());
            testEdges.add(edge);
            GalaxyTopology testTopology = topology(galaxyId, galaxyName, sectors, testEdges);
            Stage20TopologyQualityReport testReport = Stage20TopologyQualityAnalyzer.analyze(testTopology, quality);
            double penalty = testReport.repairPenalty();
            if (!(penalty + 1e-12d < currentPenalty)) {
                continue;
            }
            if (best == null || penalty + 1e-12d < best.report().repairPenalty()) {
                best = new RepairChoice(edge, testTopology, testReport);
                if (testReport.accepted()) {
                    return best;
                }
            }
        }
        return best;
    }

    private static boolean canAdd(
            JumpConnection edge,
            GenerationState state,
            Stage20TopologyQualityCalibrationProfile quality) {
        int maxDegree = quality.hubDegreeBand().maxInclusive();
        if (state.degrees().get(edge.first()) >= maxDegree || state.degrees().get(edge.second()) >= maxDegree) {
            return false;
        }
        SectorId first = state.sectorBySystem().get(edge.first());
        SectorId second = state.sectorBySystem().get(edge.second());
        if (!first.equals(second)) {
            int maxExits = quality.sectorExitBand().maxInclusive();
            return state.sectorExits().get(first) < maxExits && state.sectorExits().get(second) < maxExits;
        }
        return true;
    }

    private static List<EdgeCandidate> allMissingCandidates(List<SectorNode> sectors, long seed) {
        List<StarSystemNode> systems = sectors.stream().flatMap(value -> value.systems().stream()).toList();
        Map<StarSystemId, SectorId> sectorBySystem = new HashMap<>();
        for (SectorNode sector : sectors) {
            for (StarSystemNode system : sector.systems()) {
                sectorBySystem.put(system.id(), sector.id());
            }
        }
        List<EdgeCandidate> result = new ArrayList<>();
        for (int first = 0; first < systems.size(); first++) {
            for (int second = first + 1; second < systems.size(); second++) {
                StarSystemNode a = systems.get(first);
                StarSystemNode b = systems.get(second);
                JumpConnection edge = new JumpConnection(a.id(), b.id());
                boolean cross = !sectorBySystem.get(a.id()).equals(sectorBySystem.get(b.id()));
                result.add(new EdgeCandidate(
                        edge,
                        cross,
                        distanceSquared(a, b),
                        seededTie(seed, a.id().value() * 31L + b.id().value())));
            }
        }
        result.sort(Comparator.comparingDouble(EdgeCandidate::distanceSquared)
                .thenComparingLong(EdgeCandidate::tie)
                .thenComparing(EdgeCandidate::edge));
        return List.copyOf(result);
    }

    private static GalaxyTopology topology(
            GalaxyId id,
            String name,
            List<SectorNode> sectors,
            Collection<? extends JumpConnection> edges) {
        return new GalaxyTopology(id, name, sectors, List.copyOf(edges));
    }

    private static void addEdge(
            JumpConnection edge,
            Set<JumpConnection> edges,
            Map<StarSystemId, Integer> degrees) {
        if (edges.add(edge)) {
            degrees.merge(edge.first(), 1, Integer::sum);
            degrees.merge(edge.second(), 1, Integer::sum);
        }
    }

    private static boolean isCrossSector(
            JumpConnection edge,
            Map<StarSystemId, SectorId> sectorBySystem) {
        return !sectorBySystem.get(edge.first()).equals(sectorBySystem.get(edge.second()));
    }

    private static Point centroid(List<StarSystemNode> systems) {
        double x = 0d;
        double y = 0d;
        for (StarSystemNode system : systems) {
            x += system.x();
            y += system.y();
        }
        return new Point(x / systems.size(), y / systems.size());
    }

    private static Point centroid(Collection<Point> points) {
        double x = 0d;
        double y = 0d;
        for (Point point : points) {
            x += point.x();
            y += point.y();
        }
        return new Point(x / points.size(), y / points.size());
    }

    private static double distanceSquared(StarSystemNode first, StarSystemNode second) {
        double dx = second.x() - first.x();
        double dy = second.y() - first.y();
        return dx * dx + dy * dy;
    }

    private static double distanceSquared(StarSystemNode value, Point point) {
        double dx = point.x() - value.x();
        double dy = point.y() - value.y();
        return dx * dx + dy * dy;
    }

    private static double distanceSquared(Point first, Point second) {
        double dx = second.x() - first.x();
        double dy = second.y() - first.y();
        return dx * dx + dy * dy;
    }

    private static long seededTie(long seed, long value) {
        long z = seed ^ (value + 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private record Point(double x, double y) {
    }

    private record SectorPlan(
            SectorNode sector,
            Point centroid,
            List<StarSystemNode> coreRing,
            StarSystemNode anchor,
            StarSystemNode frontier) {
    }

    private record GatewayCandidate(
            StarSystemNode first,
            StarSystemNode second,
            int firstReuse,
            int secondReuse,
            double distanceSquared,
            long tie) {
    }

    private record SectorPairCandidate(
            SectorPlan first,
            SectorPlan second,
            double distanceSquared,
            long tie) {
    }

    private record SectorPairKey(SectorId first, SectorId second) implements Comparable<SectorPairKey> {
        private SectorPairKey {
            if (first.compareTo(second) > 0) {
                SectorId swap = first;
                first = second;
                second = swap;
            }
        }

        @Override
        public int compareTo(SectorPairKey other) {
            int firstOrder = first.compareTo(other.first);
            return firstOrder != 0 ? firstOrder : second.compareTo(other.second);
        }
    }

    private record EdgeCandidate(
            JumpConnection edge,
            boolean crossSector,
            double distanceSquared,
            long tie) {
    }

    private record RepairChoice(
            JumpConnection edge,
            GalaxyTopology topology,
            Stage20TopologyQualityReport report) {
    }

    private static final class GenerationState {
        private final TreeSet<JumpConnection> edges;
        private final TreeMap<StarSystemId, Integer> degrees;
        private final TreeMap<SectorId, Integer> sectorExits;
        private final TreeMap<StarSystemId, SectorId> sectorBySystem;

        private GenerationState(
                TreeSet<JumpConnection> edges,
                TreeMap<StarSystemId, Integer> degrees,
                TreeMap<SectorId, Integer> sectorExits,
                TreeMap<StarSystemId, SectorId> sectorBySystem) {
            this.edges = edges;
            this.degrees = degrees;
            this.sectorExits = sectorExits;
            this.sectorBySystem = sectorBySystem;
        }

        private TreeSet<JumpConnection> edges() {
            return edges;
        }

        private TreeMap<StarSystemId, Integer> degrees() {
            return degrees;
        }

        private TreeMap<SectorId, Integer> sectorExits() {
            return sectorExits;
        }

        private TreeMap<StarSystemId, SectorId> sectorBySystem() {
            return sectorBySystem;
        }

        private void incrementDegree(StarSystemId id) {
            degrees.merge(id, 1, Integer::sum);
        }

        private void incrementSectorExit(SectorId id) {
            sectorExits.merge(id, 1, Integer::sum);
        }
    }
}
