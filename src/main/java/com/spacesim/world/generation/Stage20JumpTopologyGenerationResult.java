package com.spacesim.world.generation;

import com.spacesim.world.GalaxyTopology;

import java.util.Objects;

/**
 * Deterministic Stage-20D topology-generation outcome.
 *
 * <p>A rejected candidate remains available for diagnostics but must not be materialized as the
 * production ordinary galaxy.</p>
 *
 * @param seed world/generation seed used by deterministic tie-breaking
 * @param status accepted or rejected quality-gate result
 * @param candidateTopology generated candidate graph
 * @param qualityReport final post-repair diagnostics
 * @param repairPasses number of committed deterministic repair additions
 */
public record Stage20JumpTopologyGenerationResult(
        long seed,
        Status status,
        GalaxyTopology candidateTopology,
        Stage20TopologyQualityReport qualityReport,
        int repairPasses) {

    /** Final Stage-20D v1 generation status. */
    public enum Status {
        /** Candidate satisfies every ordinary Stage-20D v1 quality budget. */
        ACCEPTED,
        /** Bounded deterministic repair could not satisfy the ordinary quality gate. */
        REJECTED_SEED
    }

    /**
     * Validates one immutable generation outcome.
     *
     * @param seed world/generation seed used by deterministic tie-breaking
     * @param status accepted or rejected quality-gate result
     * @param candidateTopology generated candidate graph
     * @param qualityReport final post-repair diagnostics
     * @param repairPasses number of committed deterministic repair additions
     */
    public Stage20JumpTopologyGenerationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(candidateTopology, "candidateTopology");
        Objects.requireNonNull(qualityReport, "qualityReport");
        if (repairPasses < 0) {
            throw new IllegalArgumentException("repairPasses must be non-negative");
        }
        if ((status == Status.ACCEPTED) != qualityReport.accepted()) {
            throw new IllegalArgumentException("status must match final quality report");
        }
    }

    /**
     * Returns the candidate only when the ordinary Stage-20D quality gate accepted it.
     *
     * @return accepted topology
     * @throws IllegalStateException when the seed failed the ordinary quality gate
     */
    public GalaxyTopology requireAcceptedTopology() {
        if (status != Status.ACCEPTED) {
            throw new IllegalStateException("Stage-20D topology seed was rejected: " + seed);
        }
        return candidateTopology;
    }
}
