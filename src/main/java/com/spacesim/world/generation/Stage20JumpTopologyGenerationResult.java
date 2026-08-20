package com.spacesim.world.generation;

import com.spacesim.world.GalaxyTopology;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable Stage-20D topology generation outcome.
 *
 * @param status accepted topology or bounded deterministic seed rejection
 * @param topology accepted authoritative topology, absent on rejection
 * @param qualityReport final quality diagnostics for the accepted/rejected candidate
 * @param repairPasses number of strictly improving repair edges committed
 * @param rejectionReason stable human-readable reason, empty on acceptance
 */
public record Stage20JumpTopologyGenerationResult(
        Status status,
        Optional<GalaxyTopology> topology,
        Stage20TopologyQualityReport qualityReport,
        int repairPasses,
        String rejectionReason) {
    /** Stable generation outcome. */
    public enum Status {
        /** Candidate satisfies the calibrated Stage-20D quality gates. */ ACCEPTED,
        /** Bounded deterministic repair could not produce an acceptable graph. */ REJECTED_SEED
    }

    /** Validates acceptance/rejection payload consistency. */
    public Stage20JumpTopologyGenerationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(qualityReport, "qualityReport");
        if (repairPasses < 0) {
            throw new IllegalArgumentException("repairPasses must be non-negative");
        }
        if (rejectionReason == null) {
            throw new IllegalArgumentException("rejectionReason must not be null");
        }
        if (status == Status.ACCEPTED) {
            if (topology.isEmpty() || !qualityReport.accepted() || !rejectionReason.isBlank()) {
                throw new IllegalArgumentException("accepted result requires accepted topology/report and no reason");
            }
        } else if (topology.isPresent() || qualityReport.accepted() || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejected result requires failing report, reason and no topology");
        }
    }

    /** Creates an accepted result. */
    public static Stage20JumpTopologyGenerationResult accepted(
            GalaxyTopology topology,
            Stage20TopologyQualityReport report,
            int repairPasses) {
        return new Stage20JumpTopologyGenerationResult(
                Status.ACCEPTED,
                Optional.of(Objects.requireNonNull(topology, "topology")),
                report,
                repairPasses,
                "");
    }

    /** Creates a bounded deterministic seed rejection result. */
    public static Stage20JumpTopologyGenerationResult rejected(
            Stage20TopologyQualityReport report,
            int repairPasses,
            String reason) {
        return new Stage20JumpTopologyGenerationResult(
                Status.REJECTED_SEED,
                Optional.empty(),
                report,
                repairPasses,
                reason);
    }
}
