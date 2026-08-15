package com.spacesim.world;

import java.util.Objects;

/**
 * Result of one successful unilateral embargo command.
 *
 * @param operation completed legal transition
 * @param actorFactionContentId faction owning the embargo policy
 * @param targetFactionContentId sanctioned/unsanctioned faction
 * @param embargo imposed embargo or the embargo that was revoked
 * @param grievanceId grievance created on imposition, otherwise empty
 */
public record DiplomaticEmbargoCommandResult(
        Operation operation,
        String actorFactionContentId,
        String targetFactionContentId,
        DiplomaticEmbargoState embargo,
        String grievanceId) {

    /** Supported successful embargo transitions. */
    public enum Operation {
        /** Market-access embargo became active. */
        IMPOSED,
        /** Current market-access embargo was removed. */
        REVOKED
    }

    /**
     * Validates one successful embargo command result.
     *
     * @param operation completed legal transition
     * @param actorFactionContentId embargo-policy owner
     * @param targetFactionContentId sanctioned/unsanctioned faction
     * @param embargo imposed embargo or the embargo that was revoked
     * @param grievanceId grievance created on imposition, otherwise empty
     */
    public DiplomaticEmbargoCommandResult {
        operation = Objects.requireNonNull(operation, "Embargo result operation not set");
        actorFactionContentId = requireId(actorFactionContentId, "Embargo result actor faction ID");
        targetFactionContentId = requireId(targetFactionContentId, "Embargo result target faction ID");
        embargo = Objects.requireNonNull(embargo, "Embargo result state not set");
        grievanceId = Objects.requireNonNull(grievanceId, "Embargo result grievance ID not set").strip();
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
