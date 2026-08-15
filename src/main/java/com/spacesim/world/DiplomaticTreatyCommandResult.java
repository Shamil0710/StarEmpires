package com.spacesim.world;

import java.util.Objects;

/**
 * Immutable result of one successful common treaty lifecycle command.
 *
 * @param operation lifecycle operation that completed
 * @param treatyOwnerFactionContentId faction directory containing {@code treaty}
 * @param treaty current/new treaty snapshot produced by the operation
 * @param relatedTreatyId prior treaty affected by counteroffer/renewal, otherwise empty
 * @param offendedFactionContentId faction that received a breach grievance, otherwise empty
 */
public record DiplomaticTreatyCommandResult(
        Operation operation,
        String treatyOwnerFactionContentId,
        DiplomaticTreatyState treaty,
        String relatedTreatyId,
        String offendedFactionContentId) {

    /** Supported successful lifecycle transitions. */
    public enum Operation {
        /** New proposal created. */
        OFFERED,
        /** Incoming proposal rejected and replacement proposal created. */
        COUNTEROFFERED,
        /** Proposal accepted and activated. */
        ACCEPTED,
        /** Proposal rejected without replacement. */
        REJECTED,
        /** Active treaty entered notice-period termination. */
        TERMINATING,
        /** Active treaty was breached and stopped providing ordinary treaty rights. */
        BREACHED,
        /** Consensual renewal proposal created from existing clauses. */
        RENEWAL_OFFERED
    }

    /**
     * Validates normalized lifecycle result metadata.
     *
     * @param operation lifecycle operation that completed
     * @param treatyOwnerFactionContentId faction directory containing {@code treaty}
     * @param treaty current/new treaty snapshot produced by the operation
     * @param relatedTreatyId prior treaty affected by counteroffer/renewal, otherwise empty
     * @param offendedFactionContentId faction that received a breach grievance, otherwise empty
     */
    public DiplomaticTreatyCommandResult {
        operation = Objects.requireNonNull(operation, "Treaty command result operation not set");
        treatyOwnerFactionContentId = requireId(
                treatyOwnerFactionContentId, "Treaty command result owner faction ID");
        treaty = Objects.requireNonNull(treaty, "Treaty command result treaty not set");
        relatedTreatyId = Objects.requireNonNull(
                relatedTreatyId, "Treaty command related ID not set").strip();
        offendedFactionContentId = Objects.requireNonNull(
                offendedFactionContentId, "Treaty command offended faction ID not set").strip();
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
