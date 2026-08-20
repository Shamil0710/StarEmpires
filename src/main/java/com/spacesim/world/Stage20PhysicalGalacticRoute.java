package com.spacesim.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable fitted-physics Stage-20D inter-system route estimate over explicit ordinary edges.
 *
 * @param systems ordered systems including origin and destination
 * @param edges ordered explicit ordinary edge estimates
 * @param estimatedArrivalSeconds time until final destination arrival
 * @param estimatedReadyAgainSeconds time until FTL is ready after final arrival
 * @param grossJumpEnergyJ gross jump energy required across all hops
 * @param cumulativeJumpHeatJ jump heat generated across all hops
 * @param translatedMassKg translated mass from the fitted planning snapshot
 * @param perHopRevalidationRequired whether execution must revalidate between hops
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
     * One explicit physical route edge estimate.
     *
     * @param connection exact ordinary topology edge
     * @param transitSeconds physical fitted edge transit
     */
    public record EdgeEstimate(JumpConnection connection, double transitSeconds) {
        /** Validates one explicit physical edge estimate. */
        public EdgeEstimate {
            Objects.requireNonNull(connection, "connection");
            requirePositiveFinite(transitSeconds, "transitSeconds");
        }
    }

    /** Validates route connectivity, scalar consequences and zero-hop semantics. */
    public Stage20PhysicalGalacticRoute {
        Objects.requireNonNull(systems, "systems");
        Objects.requireNonNull(edges, "edges");
        if (systems.isEmpty() || systems.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("systems must contain at least one non-null system");
        }
        if (edges.stream().anyMatch(Objects::isNull) || edges.size() != systems.size() - 1) {
            throw new IllegalArgumentException("edges must exactly connect consecutive route systems");
        }
        ArrayList<StarSystemId> systemsCopy = new ArrayList<>(systems);
        ArrayList<EdgeEstimate> edgeCopy = new ArrayList<>(edges);
        for (int index = 0; index < edgeCopy.size(); index++) {
            JumpConnection connection = edgeCopy.get(index).connection();
            StarSystemId from = systemsCopy.get(index);
            StarSystemId to = systemsCopy.get(index + 1);
            if (!connection.other(from).equals(to)) {
                throw new IllegalArgumentException("route edge does not connect consecutive systems at index " + index);
            }
        }
        systems = List.copyOf(systemsCopy);
        edges = List.copyOf(edgeCopy);
        requireNonNegativeFinite(estimatedArrivalSeconds, "estimatedArrivalSeconds");
        requireNonNegativeFinite(estimatedReadyAgainSeconds, "estimatedReadyAgainSeconds");
        requireNonNegativeFinite(grossJumpEnergyJ, "grossJumpEnergyJ");
        requireNonNegativeFinite(cumulativeJumpHeatJ, "cumulativeJumpHeatJ");
        requireNonNegativeFinite(translatedMassKg, "translatedMassKg");
        if (estimatedReadyAgainSeconds < estimatedArrivalSeconds) {
            throw new IllegalArgumentException("ready-again ETA cannot precede final arrival");
        }
        if (edges.isEmpty()) {
            if (estimatedArrivalSeconds != 0d
                    || estimatedReadyAgainSeconds != 0d
                    || grossJumpEnergyJ != 0d
                    || cumulativeJumpHeatJ != 0d
                    || perHopRevalidationRequired) {
                throw new IllegalArgumentException("zero-hop route must have zero consequences and no revalidation");
            }
        } else if (!perHopRevalidationRequired) {
            throw new IllegalArgumentException("non-zero ordinary route must require per-hop revalidation");
        }
    }

    /** @return route origin */
    public StarSystemId origin() {
        return systems.get(0);
    }

    /** @return final route destination */
    public StarSystemId destination() {
        return systems.get(systems.size() - 1);
    }

    /** @return explicit ordinary hop count */
    public int jumpCount() {
        return edges.size();
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
}
