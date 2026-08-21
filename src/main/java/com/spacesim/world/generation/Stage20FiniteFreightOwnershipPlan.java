package com.spacesim.world.generation;

import java.util.List;
import java.util.Objects;

/**
 * Stage 20E ownership projection layer.
 *
 * <p>Converts reconstructed physical freight commitments into finite faction-owned
 * bootstrap obligations. This layer does not materialize runtime fleets or allocate
 * persistent FleetIds; it only preserves ownership intent before runtime bootstrap.</p>
 */
public final class Stage20FiniteFreightOwnershipPlan {

    /** Current ownership-plan schema version. */
    public static final String CURRENT_VERSION = "stage20e.finite-freight-ownership.v1";

    private Stage20FiniteFreightOwnershipPlan() {
        throw new AssertionError("No instances");
    }

    /**
     * One finite owned freight obligation.
     *
     * @param factionId owning faction identifier
     * @param commodity transported commodity
     * @param requiredFreighters finite required fleet count
     */
    public record OwnedFreightCommitment(String factionId, String commodity, int requiredFreighters) {
        public OwnedFreightCommitment {
            factionId = requireText(factionId, "factionId");
            commodity = requireText(commodity, "commodity");
            if (requiredFreighters <= 0) {
                throw new IllegalArgumentException("requiredFreighters must be positive");
            }
        }
    }

    /**
     * Immutable ownership bootstrap plan.
     *
     * @param version schema version
     * @param seed generated-world seed
     * @param commitments owned freight commitments
     */
    public record OwnershipPlan(String version, long seed, List<OwnedFreightCommitment> commitments) {
        public OwnershipPlan {
            version = requireText(version, "version");
            commitments = List.copyOf(Objects.requireNonNull(commitments, "commitments"));
            if (commitments.isEmpty()) {
                throw new IllegalArgumentException("ownership plan requires commitments");
            }
        }
    }

    /**
     * Creates a deterministic finite ownership projection.
     *
     * @param seed generated-world seed
     * @param commitments reconstructed owned commitments
     * @return immutable ownership plan
     */
    public static OwnershipPlan create(long seed, List<OwnedFreightCommitment> commitments) {
        return new OwnershipPlan(CURRENT_VERSION, seed, commitments);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
