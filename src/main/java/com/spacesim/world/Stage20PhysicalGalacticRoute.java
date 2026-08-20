package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable Stage-20D physical/operational estimate for one explicit multi-hop jump route.
 *
 * <p>The estimate is deliberately not an atomic execution reservation. Every edge remains an
 * ordinary neighbor jump and must be revalidated by the authoritative world runtime after each
 * intermediate arrival. Gross energy/heat values describe the physical consequence of repeatedly
 * using the fitted capability snapshot used for planning; they do not promise that later hops will
 * remain feasible after damage, access, thermal, storage or topology state changes.</p>
 *
 * @param systems ordered systems including origin and destination
 * @param edges ordered ordinary neighbor-edge estimates
 * @param estimatedArrivalSeconds calibrated FTL arrival cadence through the final edge
 * @param estimatedReadyAgainSeconds arrival cadence plus the final post-jump cooldown
 * @param grossJumpEnergyJ sum of required fitted jump energy across all planned hops
 * @param cumulativeJumpHeatJ sum of fitted jump heat across all planned hops
 * @param translatedMassKg translated mass captured by the fitted planning snapshot
 * @param perHopRevalidationRequired always true for non-zero-hop ordinary routes
 */
public record Stage20PhysicalGalacticRoute(
        List<StarSystemId> systems,
        List<EdgeEstimate> edges,
        double estimatedArrivalSeconds,
        double estimatedReadyAgainSeconds,
        double grossJumpEnergyJ,
        double cumulativeJumpHeatJ,
        double translatedMassKg,
        boolean perHopRevalidationRequired) {

    /**
     * Validates one immutable route estimate and its ordered explicit edges.
     *
     * @param systems ordered systems
     * @param edges ordered edge estimates
     * @param estimatedArrivalSeconds final-arrival cadence
     * @param estimatedReadyAgainSeconds final-ready cadence
     * @param grossJumpEnergyJ gross energy consequence
     * @param cumulativeJumpHeatJ gross heat consequence
     * @param translatedMassKg fitted translated mass
     * @param perHopRevalidationRequired whether authoritative execution must revalidate every hop
     */
    public Stage20PhysicalGalacticRoute {
        Objects.requireNonNull(systems, "systems");
        Objects.requireNonNull(edges, "edges");
        systems = List.copyOf(systems);
        edges = List.copyOf(edges);
        if (systems.isEmpty() || systems.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("systems must be non-empty and contain no null entries");
        }
        if (edges.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("edges must contain no null entries");
        }
        if (edges.size() != systems.size() - 1) {
            throw new IllegalArgumentException("edge count must equal systems.size()-1");
        }
        for (int index = 0; index < edges.size(); index++) {
            JumpConnection expected = new JumpConnection(systems.get(index), systems.get(index + 1));
            if (!expected.equals(edges.get(index).connection())) {
                throw new IllegalArgumentException("edge order does not match ordered systems at index " + index);
            }
        }
        requireNonNegativeFinite(estimatedArrivalSeconds, "estimatedArrivalSeconds");
        requireNonNegativeFinite(estimatedReadyAgainSeconds, "estimatedReadyAgainSeconds");
        requireNonNegativeFinite(grossJumpEnergyJ, "grossJumpEnergyJ");
        requireNonNegativeFinite(cumulativeJumpHeatJ, "cumulativeJumpHeatJ");
        requireNonNegativeFinite(translatedMassKg, "translatedMassKg");
        if (estimatedReadyAgainSeconds < estimatedArrivalSeconds) {
            throw new IllegalArgumentException("estimatedReadyAgainSeconds cannot precede arrival");
        }
        if (edges.isEmpty()) {
            if (Double.compare(estimatedArrivalSeconds, 0d) != 0
                    || Double.compare(estimatedReadyAgainSeconds, 0d) != 0
                    || Double.compare(grossJumpEnergyJ, 0d) != 0
                    || Double.compare(cumulativeJumpHeatJ, 0d) != 0
                    || perHopRevalidationRequired) {
                throw new IllegalArgumentException("zero-hop route must have zero consequences and no revalidation");
            }
        } else if (!perHopRevalidationRequired) {
            throw new IllegalArgumentException("ordinary multi-hop route must require per-hop revalidation");
        }
    }

    /** Returns the route origin.
     * @return route origin
     */
    public StarSystemId origin() {
        return systems.get(0);
    }

    /** Returns the route destination.
     * @return route destination
     */
    public StarSystemId destination() {
        return systems.get(systems.size() - 1);
    }

    /** Returns the number of explicit ordinary jump edges.
     * @return number of explicit ordinary jump edges
     */
    public int jumpCount() {
        return edges.size();
    }

    /**
     * One explicit ordinary edge inside a physical route estimate.
     *
     * @param connection canonical production topology connection
     * @param transitSeconds edge-transit time used by the fitted planning snapshot/provider
     * @param cooldownBeforeSeconds cooldown wait before this edge; zero only on the first edge
     * @param arrivalOffsetSeconds cumulative FTL time at arrival through this edge
     */
    public record EdgeEstimate(
            JumpConnection connection,
            double transitSeconds,
            double cooldownBeforeSeconds,
            double arrivalOffsetSeconds) {
        /** Validates one ordered edge estimate. */
        public EdgeEstimate {
            Objects.requireNonNull(connection, "connection");
            requirePositiveFinite(transitSeconds, "transitSeconds");
            requireNonNegativeFinite(cooldownBeforeSeconds, "cooldownBeforeSeconds");
            requirePositiveFinite(arrivalOffsetSeconds, "arrivalOffsetSeconds");
        }
    }

    static List<EdgeEstimate> canonicalEdgeCopy(List<EdgeEstimate> source) {
        ArrayList<EdgeEstimate> copy = new ArrayList<>(Objects.requireNonNull(source, "source"));
        copy.sort(Comparator.comparingDouble(EdgeEstimate::arrivalOffsetSeconds));
        return List.copyOf(copy);
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
