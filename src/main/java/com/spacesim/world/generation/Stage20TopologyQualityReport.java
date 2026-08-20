package com.spacesim.world.generation;

import com.spacesim.world.JumpConnection;
import com.spacesim.world.SectorId;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
 * Immutable machine-readable Stage-20D jump-topology quality diagnostics.
 *
 * <p>The report intentionally contains structural observations rather than presentation labels so
 * generation, CI and later Stage-22 playability review can compare the same physical graph.</p>
 *
 * @param connectedComponents number of connected components in the ordinary jump graph
 * @param unreachableSystems systems outside the deterministic primary component
 * @param unreachableSectors sectors with no system in the deterministic primary component
 * @param degreeHistogram graph degree to system-count histogram
 * @param degreeOneFraction fraction of systems with exactly one ordinary jump neighbor
 * @param degreeTwoFraction fraction of systems with exactly two ordinary jump neighbors
 * @param meanDegree arithmetic mean ordinary jump degree
 * @param medianDegree median ordinary jump degree
 * @param hubSystems systems whose degree lies inside the calibrated local-hub band
 * @param linearCorridorLengths deterministic sorted bridge-only choice-free corridor lengths
 * @param longestLinearCorridorEdges longest bridge-only choice-free degree-two corridor in edges
 * @param p90LinearCorridorEdges nearest-rank 90th percentile corridor length
 * @param cycleParticipationFraction fraction of systems participating in at least one cycle
 * @param corePairsChecked number of deterministic regional-core pairs checked for redundancy
 * @param corePairsWithAlternateRoute number of core pairs retaining at least two edge-disjoint routes
 * @param coreRouteRedundancyCoverage fraction of checked core pairs with an alternate edge-disjoint route
 * @param articulationSystems articulation systems in deterministic ID order
 * @param bridgeEdges bridge edges in deterministic edge order
 * @param sectorExitCounts number of ordinary inter-sector edges incident to each sector
 * @param sectorInternalCycleCoverage per-sector fraction of systems participating in an internal cycle
 * @param sectorInternalBridgeCounts per-sector count of bridge edges in the induced internal graph
 * @param maxSingleGatewayDependency maximum structural dependency proxy caused by removing one gateway system
 * @param regionalHubHopDistances shortest hop counts between directly adjacent sectors' deterministic hubs
 * @param medianRegionalHubHopDistance median of {@code regionalHubHopDistances}, absent when not applicable
 * @param sectorMotifFingerprints deterministic structural fingerprints for diagnostics/presentation
 * @param violations calibrated quality-gate violations
 */
public record Stage20TopologyQualityReport(
        int connectedComponents,
        List<StarSystemId> unreachableSystems,
        List<SectorId> unreachableSectors,
        Map<Integer, Integer> degreeHistogram,
        double degreeOneFraction,
        double degreeTwoFraction,
        double meanDegree,
        double medianDegree,
        List<StarSystemId> hubSystems,
        List<Integer> linearCorridorLengths,
        int longestLinearCorridorEdges,
        double p90LinearCorridorEdges,
        double cycleParticipationFraction,
        int corePairsChecked,
        int corePairsWithAlternateRoute,
        double coreRouteRedundancyCoverage,
        List<StarSystemId> articulationSystems,
        List<JumpConnection> bridgeEdges,
        Map<SectorId, Integer> sectorExitCounts,
        Map<SectorId, Double> sectorInternalCycleCoverage,
        Map<SectorId, Integer> sectorInternalBridgeCounts,
        double maxSingleGatewayDependency,
        List<Integer> regionalHubHopDistances,
        OptionalDouble medianRegionalHubHopDistance,
        Map<SectorId, String> sectorMotifFingerprints,
        List<Violation> violations) {

    /** Stable v1 violation classes consumed by deterministic repair and CI. */
    public enum ViolationType {
        /** Ordinary graph contains more than one connected component. */
        DISCONNECTED,
        /** Degree-one system share exceeds the calibrated maximum. */
        EXCESS_DEGREE_ONE_FRACTION,
        /** A bridge-only choice-free corridor exceeds the calibrated maximum. */
        EXCESS_LINEAR_CORRIDOR,
        /** Cycle participation falls below the calibrated minimum. */
        INSUFFICIENT_CYCLE_COVERAGE,
        /** Deterministic regional cores do not retain the required alternate route. */
        INSUFFICIENT_CORE_ROUTE_REDUNDANCY,
        /** One gateway system carries too much structural dependency. */
        EXCESS_GATEWAY_DEPENDENCY,
        /** A sector has fewer ordinary exits than the calibrated band. */
        SECTOR_EXIT_BELOW_BAND,
        /** A sector has more ordinary exits than the calibrated band. */
        SECTOR_EXIT_ABOVE_BAND,
        /** A system exceeds the calibrated upper local-hub degree. */
        HUB_DEGREE_ABOVE_BAND,
        /** An adjacent-sector regional hub probe is shorter than the calibrated band. */
        REGIONAL_HOP_BELOW_BAND,
        /** An adjacent-sector regional hub probe is longer than the calibrated band. */
        REGIONAL_HOP_ABOVE_BAND
    }

    /**
     * One normalized quality-gate failure.
     *
     * @param type stable machine-readable violation type
     * @param subject stable human-readable subject, for example a sector/system ID
     * @param observed observed numeric value
     * @param limit calibrated bound that was violated
     * @param normalizedSeverity non-negative dimensionless repair ordering severity
     */
    public record Violation(
            ViolationType type,
            String subject,
            double observed,
            double limit,
            double normalizedSeverity) {
        /** Validates one immutable violation. */
        public Violation {
            Objects.requireNonNull(type, "type");
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("subject must not be blank");
            }
            if (!Double.isFinite(observed) || !Double.isFinite(limit)) {
                throw new IllegalArgumentException("observed/limit must be finite");
            }
            if (!Double.isFinite(normalizedSeverity) || normalizedSeverity < 0d) {
                throw new IllegalArgumentException("normalizedSeverity must be non-negative and finite");
            }
        }
    }

    /** Canonicalizes deterministic collection ordering and validates scalar ranges. */
    public Stage20TopologyQualityReport {
        if (connectedComponents < 0 || longestLinearCorridorEdges < 0
                || corePairsChecked < 0 || corePairsWithAlternateRoute < 0
                || corePairsWithAlternateRoute > corePairsChecked) {
            throw new IllegalArgumentException("quality-report counts must be non-negative and ordered");
        }
        requireUnitFraction(degreeOneFraction, "degreeOneFraction");
        requireUnitFraction(degreeTwoFraction, "degreeTwoFraction");
        if (!Double.isFinite(meanDegree) || meanDegree < 0d
                || !Double.isFinite(medianDegree) || medianDegree < 0d
                || !Double.isFinite(p90LinearCorridorEdges) || p90LinearCorridorEdges < 0d) {
            throw new IllegalArgumentException("degree/corridor statistics must be non-negative and finite");
        }
        requireUnitFraction(cycleParticipationFraction, "cycleParticipationFraction");
        requireUnitFraction(coreRouteRedundancyCoverage, "coreRouteRedundancyCoverage");
        requireUnitFraction(maxSingleGatewayDependency, "maxSingleGatewayDependency");

        unreachableSystems = sortedSystems(unreachableSystems, "unreachableSystems");
        unreachableSectors = sortedValues(unreachableSectors, "unreachableSectors");
        hubSystems = sortedSystems(hubSystems, "hubSystems");
        articulationSystems = sortedSystems(articulationSystems, "articulationSystems");

        Objects.requireNonNull(linearCorridorLengths, "linearCorridorLengths");
        ArrayList<Integer> corridors = new ArrayList<>(linearCorridorLengths);
        if (corridors.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException("linearCorridorLengths must contain positive values");
        }
        corridors.sort(Integer::compareTo);
        linearCorridorLengths = List.copyOf(corridors);
        int expectedLongest = corridors.isEmpty() ? 0 : corridors.get(corridors.size() - 1);
        if (longestLinearCorridorEdges != expectedLongest) {
            throw new IllegalArgumentException("longestLinearCorridorEdges must match corridor distribution");
        }

        Objects.requireNonNull(bridgeEdges, "bridgeEdges");
        ArrayList<JumpConnection> sortedBridges = new ArrayList<>(bridgeEdges);
        if (sortedBridges.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("bridgeEdges must not contain null");
        }
        sortedBridges.sort(Comparator.naturalOrder());
        bridgeEdges = List.copyOf(sortedBridges);

        degreeHistogram = sortedMap(degreeHistogram, "degreeHistogram");
        sectorExitCounts = sortedMap(sectorExitCounts, "sectorExitCounts");
        sectorInternalCycleCoverage = sortedMap(sectorInternalCycleCoverage, "sectorInternalCycleCoverage");
        for (double value : sectorInternalCycleCoverage.values()) {
            requireUnitFraction(value, "sectorInternalCycleCoverage");
        }
        sectorInternalBridgeCounts = sortedMap(sectorInternalBridgeCounts, "sectorInternalBridgeCounts");
        if (sectorInternalBridgeCounts.values().stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("sectorInternalBridgeCounts must be non-negative");
        }
        sectorMotifFingerprints = sortedMap(sectorMotifFingerprints, "sectorMotifFingerprints");

        Objects.requireNonNull(regionalHubHopDistances, "regionalHubHopDistances");
        ArrayList<Integer> hops = new ArrayList<>(regionalHubHopDistances);
        if (hops.stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("regionalHubHopDistances must contain non-negative values");
        }
        hops.sort(Integer::compareTo);
        regionalHubHopDistances = List.copyOf(hops);
        Objects.requireNonNull(medianRegionalHubHopDistance, "medianRegionalHubHopDistance");
        if (regionalHubHopDistances.isEmpty() != medianRegionalHubHopDistance.isEmpty()) {
            throw new IllegalArgumentException("regional hop median presence must match samples");
        }

        Objects.requireNonNull(violations, "violations");
        ArrayList<Violation> sortedViolations = new ArrayList<>(violations);
        if (sortedViolations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("violations must not contain null");
        }
        sortedViolations.sort(Comparator.comparing((Violation value) -> value.type().name())
                .thenComparing(Violation::subject));
        violations = List.copyOf(sortedViolations);
    }

    /**
     * Reports whether the candidate satisfies every ordinary Stage-20D v1 quality budget.
     *
     * @return true when no calibrated violation remains
     */
    public boolean accepted() {
        return violations.isEmpty();
    }

    /**
     * Deterministic scalar used only to select a strictly improving bounded repair candidate.
     *
     * @return zero for an accepted graph, otherwise sum of normalized violation severities
     */
    public double repairPenalty() {
        double result = 0d;
        for (Violation violation : violations) {
            result += 1d + violation.normalizedSeverity();
        }
        return result;
    }

    private static List<StarSystemId> sortedSystems(List<StarSystemId> source, String field) {
        Objects.requireNonNull(source, field);
        ArrayList<StarSystemId> result = new ArrayList<>(source);
        if (result.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static <T extends Comparable<T>> List<T> sortedValues(List<T> source, String field) {
        Objects.requireNonNull(source, field);
        ArrayList<T> result = new ArrayList<>(source);
        if (result.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static <K extends Comparable<K>, V> Map<K, V> sortedMap(Map<K, V> source, String field) {
        Objects.requireNonNull(source, field);
        TreeMap<K, V> result = new TreeMap<>();
        for (Map.Entry<K, V> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(field + " must not contain null keys/values");
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    private static void requireUnitFraction(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be finite and in [0,1]");
        }
    }
}
