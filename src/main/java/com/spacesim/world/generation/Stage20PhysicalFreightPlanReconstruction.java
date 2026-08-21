package com.spacesim.world.generation;

import java.util.List;
import java.util.Objects;

/**
 * Stage 20E reconstruction layer for turning resolved freight evidence into an explicit
 * causal physical plan. This slice only records provenance; it does not create runtime fleets.
 */
public final class Stage20PhysicalFreightPlanReconstruction {

    /** Current reconstruction schema version. */
    public static final String CURRENT_VERSION = "stage20e.physical-freight-plan-reconstruction.v1";

    private Stage20PhysicalFreightPlanReconstruction() {
        throw new AssertionError("No instances");
    }

    /**
     * One reconstructed physical freight commitment.
     *
     * @param commodity stable commodity identifier
     * @param producer stable producer identifier
     * @param sourceNode physical origin node
     * @param destinationNode physical destination node
     * @param routeId evaluated physical route identifier
     * @param requiredFreighters committed freight capacity
     */
    public record FreightCommitment(
            String commodity,
            String producer,
            String sourceNode,
            String destinationNode,
            String routeId,
            int requiredFreighters) {

        public FreightCommitment {
            commodity = requireText(commodity, "commodity");
            producer = requireText(producer, "producer");
            sourceNode = requireText(sourceNode, "sourceNode");
            destinationNode = requireText(destinationNode, "destinationNode");
            routeId = requireText(routeId, "routeId");
            if (requiredFreighters <= 0) {
                throw new IllegalArgumentException("requiredFreighters must be positive");
            }
        }
    }

    /**
     * Immutable reconstruction result.
     *
     * @param version reconstruction schema version
     * @param seed source generated-world seed
     * @param commitments reconstructed physical commitments
     */
    public record ReconstructionResult(
            String version,
            long seed,
            List<FreightCommitment> commitments) {

        public ReconstructionResult {
            version = requireText(version, "version");
            commitments = List.copyOf(Objects.requireNonNull(commitments, "commitments"));
            if (commitments.isEmpty()) {
                throw new IllegalArgumentException("reconstruction requires at least one commitment");
            }
        }
    }

    /**
     * Creates a deterministic reconstruction from already accepted freight commitments.
     *
     * @param seed generated-world seed
     * @param commitments resolved commitments
     * @return immutable reconstruction result
     */
    public static ReconstructionResult reconstruct(long seed, List<FreightCommitment> commitments) {
        return new ReconstructionResult(CURRENT_VERSION, seed, commitments);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
