package com.spacesim.world.generation;

import com.spacesim.world.JumpConnection;
import com.spacesim.world.SectorId;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
 * Immutable deterministic Stage-20D structural quality diagnostics for one generated jump graph.
 *
 * <p>The report is intentionally machine-readable: generation repair consumes the same violations
 * that diagnostics expose, so quality budgets cannot silently diverge from observability.</p>
 *
 * @param connectedComponentCount number of connected components
 * @param unreachableSystems systems outside the deterministic primary component
 * @param unreachableSectors sectors with no system in the primary component
 * @param degreeHistogram graph degree -> number of systems
 * @param degreeOneFraction fraction of systems with degree one
 * @param degreeTwoFraction fraction of systems with degree two
 * @param meanDegree arithmetic mean graph degree
 * @param medianDegree median graph degree
 * @param hubSystems systems inside the calibrated hub-degree band
 * @param linearCorridorLengthsEdges sorted bridge-only corridor lengths
 * @param longestLinearCorridorEdges longest bridge-only corridor
 * @param p90LinearCorridorEdges nearest-rank p90 bridge-only corridor
 * @param cycleParticipationFraction fraction of systems incident to at least one non-bridge edge
 * @param checkedCorePairs number of deterministic regional-core pairs checked
 * @param redundantCorePairs core pairs with two edge-disjoint routes
 * @param coreRedundancyCoverage redundant-core pair fraction
 * @param articulationSystems articulation systems
 * @param bridgeConnections graph bridges
 * @param sectorExitCounts explicit cross-sector edges incident to each sector
 * @param sectorInternalCycleCoverage per-sector fraction incident to an internal non-bridge edge
 * @param sectorInternalBridgeCounts internal bridge count per sector
 * @param maxSingleGatewayDependency maximum disconnected-share proxy after one gateway removal
 * @param regionalHopDistances sorted deterministic regional-core hop probes
 * @param medianRegionalHopDistance median regional hop probe, empty when not applicable
 * @param sectorMotifFingerprint deterministic structural motif label per sector
 * @param violations calibrated quality violations
 */
public record Stage20TopologyQualityReport(
        int connectedComponentCount,
        List<StarSystemId> unreachableSystems,
        List<SectorId> unreachableSectors,
        Map<Integer, Integer> degreeHistogram,
        double degreeOneFraction,
        double degreeTwoFraction,
        double meanDegree,
        double medianDegree,
        List<StarSystemId> hubSystems,
        List<Integer> linearCorridorLengthsEdges,
        int longestLinearCorridorEdges,
        double p90LinearCorridorEdges,
        double cycleParticipationFraction,
        int checkedCorePairs,
        int redundantCorePairs,
        double coreRedundancyCoverage,
        List<StarSystemId> articulationSystems,
        List<JumpConnection> bridgeConnections,
        Map<SectorId, Integer> sectorExitCounts,
        Map<SectorId, Double> sectorInternalCycleCoverage,
        Map<SectorId, Integer> sectorInternalBridgeCounts,
        double maxSingleGatewayDependency,
        List<Integer> regionalHopDistances,
        OptionalDouble medianRegionalHopDistance,
        Map<SectorId, String> sectorMotifFingerprint,
        List<Violation> violations) {

    /** Stable calibrated failure types used by deterministic repair and diagnostics. */
    public enum ViolationType {
        /** Generated ordinary graph has more than one connected component. */ DISCONNECTED,
        /** Degree-one share exceeds accepted budget. */ EXCESS_DEGREE_ONE_FRACTION,
        /** A bridge-only linear corridor exceeds the accepted maximum. */ EXCESS_LINEAR_CORRIDOR,
        /** Global cycle participation is below the accepted budget. */ INSUFFICIENT_CYCLE_COVERAGE,
        /** Deterministic regional cores do not all have two edge-disjoint routes. */ INSUFFICIENT_CORE_ROUTE_REDUNDANCY,
        /** Removing one gateway disconnects more than the accepted share. */ EXCESS_GATEWAY_DEPENDENCY,
        /** A sector exposes too few ordinary exits. */ SECTOR_EXIT_BELOW_BAND,
        /** A sector exposes too many ordinary exits. */ SECTOR_EXIT_ABOVE_BAND,
        /** A system exceeds the calibrated hub-degree ceiling. */ HUB_DEGREE_ABOVE_BAND,
        /** A representative regional-core route is shorter than the accepted band. */ REGIONAL_HOP_BELOW_BAND,
        /** A representative regional-core route is longer than the accepted band. */ REGIONAL_HOP_ABOVE_BAND
    }

    /**
     * One normalized quality violation.
     *
     * @param type stable failure type
     * @param subject stable system/sector/diagnostic subject
     * @param observed observed value
     * @param limit calibrated comparison boundary
     * @param normalizedSeverity non-negative normalized deficit/excess used by repair ranking
     */
    public record Violation(
            ViolationType type,
            String subject,
            double observed,
            double limit,
            double normalizedSeverity) {
        /** Validates one machine-readable quality violation. */
        public Violation {
            Objects.requireNonNull(type, "type");
            requireText(subject, "subject");
            requireFinite(observed, "observed");
            requireFinite(limit, "limit");
            requireNonNegativeFinite(normalizedSeverity, "normalizedSeverity");
        }
    }

    /** Freezes and validates all deterministic report collections and scalar diagnostics. */
    public Stage20TopologyQualityReport {
        if (connectedComponentCount < 0) {
            throw new IllegalArgumentException("connectedComponentCount must be non-negative");
        }
        requireFraction(degreeOneFraction, "degreeOneFraction");
        requireFraction(degreeTwoFraction, "degreeTwoFraction");
        requireNonNegativeFinite(meanDegree, "meanDegree");
        requireNonNegativeFinite(medianDegree, "medianDegree");
        if (longestLinearCorridorEdges < 0) {
            throw new IllegalArgumentException("longestLinearCorridorEdges must be non-negative");
        }
        requireNonNegativeFinite(p90LinearCorridorEdges, "p90LinearCorridorEdges");
        requireFraction(cycleParticipationFraction, "cycleParticipationFraction");
        if (checkedCorePairs < 0 || redundantCorePairs < 0 || redundantCorePairs > checkedCorePairs) {
            throw new IllegalArgumentException("core pair counts are invalid");
        }
        requireFraction(coreRedundancyCoverage, "coreRedundancyCoverage");
        requireFraction(maxSingleGatewayDependency, "maxSingleGatewayDependency");
        Objects.requireNonNull(medianRegionalHopDistance, "medianRegionalHopDistance");
        if (medianRegionalHopDistance.isPresent()) {
            requireNonNegativeFinite(medianRegionalHopDistance.getAsDouble(), "medianRegionalHopDistance");
        }

        unreachableSystems = orderedCopy(unreachableSystems, "unreachableSystems");
        unreachableSectors = orderedCopy(unreachableSectors, "unreachableSectors");
        degreeHistogram = immutableIntegerMap(degreeHistogram, "degreeHistogram");
        hubSystems = orderedCopy(hubSystems, "hubSystems");
        linearCorridorLengthsEdges = orderedIntegerList(linearCorridorLengthsEdges, "linearCorridorLengthsEdges");
        articulationSystems = orderedCopy(articulationSystems, "articulationSystems");
        bridgeConnections = orderedCopy(bridgeConnections, "bridgeConnections");
        sectorExitCounts = immutableSectorIntegerMap(sectorExitCounts, "sectorExitCounts");
        sectorInternalCycleCoverage = immutableSectorDoubleMap(sectorInternalCycleCoverage, "sectorInternalCycleCoverage", true);
        sectorInternalBridgeCounts = immutableSectorIntegerMap(sectorInternalBridgeCounts, "sectorInternalBridgeCounts");
        regionalHopDistances = orderedIntegerList(regionalHopDistances, "regionalHopDistances");
        sectorMotifFingerprint = immutableSectorStringMap(sectorMotifFingerprint, "sectorMotifFingerprint");
        violations = immutableViolations(violations);
    }

    /** @return true when no calibrated quality violation exists */
    public boolean passes() {
        return violations.isEmpty();
    }

    /**
     * Deterministic scalar repair objective; zero means the graph passes all calibrated gates.
     *
     * @return sum of normalized violation severities
     */
    public double repairPenalty() {
        return violations.stream().mapToDouble(Violation::normalizedSeverity).sum();
    }

    /** @return number of explicit unreachable systems */
    public int unreachableSystemCount() {
        return unreachableSystems.size();
    }

    /** @return number of explicit unreachable sectors */
    public int unreachableSectorCount() {
        return unreachableSectors.size();
    }

    private static <T extends Comparable<? super T>> List<T> orderedCopy(List<T> input, String field) {
        Objects.requireNonNull(input, field);
        ArrayList<T> copy = new ArrayList<>(input);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        copy.sort(Comparator.naturalOrder());
        return List.copyOf(copy);
    }

    private static List<Integer> orderedIntegerList(List<Integer> input, String field) {
        Objects.requireNonNull(input, field);
        ArrayList<Integer> copy = new ArrayList<>(input);
        for (Integer value : copy) {
            if (value == null || value < 0) {
                throw new IllegalArgumentException(field + " must contain non-negative integers");
            }
        }
        copy.sort(Integer::compareTo);
        return List.copyOf(copy);
    }

    private static Map<Integer, Integer> immutableIntegerMap(Map<Integer, Integer> input, String field) {
        Objects.requireNonNull(input, field);
        TreeMap<Integer, Integer> result = new TreeMap<>();
        for (Map.Entry<Integer, Integer> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 0 || entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException(field + " contains invalid entry");
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<SectorId, Integer> immutableSectorIntegerMap(Map<SectorId, Integer> input, String field) {
        Objects.requireNonNull(input, field);
        TreeMap<SectorId, Integer> result = new TreeMap<>();
        for (Map.Entry<SectorId, Integer> entry : input.entrySet()) {
            Objects.requireNonNull(entry.getKey(), field + " key");
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException(field + " contains invalid entry");
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<SectorId, Double> immutableSectorDoubleMap(
            Map<SectorId, Double> input,
            String field,
            boolean fraction) {
        Objects.requireNonNull(input, field);
        TreeMap<SectorId, Double> result = new TreeMap<>();
        for (Map.Entry<SectorId, Double> entry : input.entrySet()) {
            Objects.requireNonNull(entry.getKey(), field + " key");
            Double value = Objects.requireNonNull(entry.getValue(), field + " value");
            if (fraction) {
                requireFraction(value, field + " value");
            } else {
                requireFinite(value, field + " value");
            }
            result.put(entry.getKey(), value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<SectorId, String> immutableSectorStringMap(Map<SectorId, String> input, String field) {
        Objects.requireNonNull(input, field);
        LinkedHashMap<SectorId, String> result = new LinkedHashMap<>();
        input.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Objects.requireNonNull(entry.getKey(), field + " key");
            requireText(entry.getValue(), field + " value");
            result.put(entry.getKey(), entry.getValue());
        });
        return Collections.unmodifiableMap(result);
    }

    private static List<Violation> immutableViolations(List<Violation> input) {
        Objects.requireNonNull(input, "violations");
        ArrayList<Violation> result = new ArrayList<>(input);
        if (result.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("violations must not contain null");
        }
        result.sort(Comparator.comparing(Violation::type)
                .thenComparing(Violation::subject)
                .thenComparingDouble(Violation::observed)
                .thenComparingDouble(Violation::limit));
        return List.copyOf(result);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        requireFinite(value, field);
        if (value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static void requireFraction(double value, String field) {
        requireFinite(value, field);
        if (value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }
}
