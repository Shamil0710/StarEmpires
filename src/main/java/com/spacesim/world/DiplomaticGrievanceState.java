package com.spacesim.world;

import java.util.Objects;

/**
 * One explicit directed grievance used by diplomacy instead of hiding conflict in a relation score.
 *
 * @param grievanceId stable grievance ID unique within the owning faction
 * @param targetFactionContentId faction blamed by the owner
 * @param kind semantic grievance category
 * @param severity bounded severity in range [1, 100]
 * @param createdTick authoritative creation tick
 * @param expiresTick exclusive expiry tick or -1 for indefinite
 * @param subjectKey optional stable subject/context key, empty when not applicable
 */
public record DiplomaticGrievanceState(
        String grievanceId,
        String targetFactionContentId,
        Kind kind,
        int severity,
        long createdTick,
        long expiresTick,
        String subjectKey) implements Comparable<DiplomaticGrievanceState> {

    /** Stable grievance categories used by later utility/escalation logic. */
    public enum Kind {
        /** Competing territorial claim/control position. */
        TERRITORIAL_DISPUTE,
        /** A formal treaty obligation was violated. */
        TREATY_BREACH,
        /** Economic access was deliberately restricted. */
        EMBARGO,
        /** Material economic harm not covered by a more specific category. */
        ECONOMIC_HARM,
        /** Bounded fallback for authored or future diplomatic causes. */
        OTHER
    }

    /** Validates and normalizes one grievance. */
    public DiplomaticGrievanceState {
        grievanceId = requireId(grievanceId, "Grievance ID");
        targetFactionContentId = requireId(targetFactionContentId, "Target faction content ID");
        kind = Objects.requireNonNull(kind, "Grievance kind not set");
        if (severity < 1 || severity > 100) {
            throw new IllegalArgumentException("Grievance severity must be in [1, 100]");
        }
        if (createdTick < 0L) {
            throw new IllegalArgumentException("Grievance creation tick cannot be negative");
        }
        if (expiresTick != -1L && expiresTick <= createdTick) {
            throw new IllegalArgumentException("Grievance expiry must be after creation or -1");
        }
        subjectKey = Objects.requireNonNull(subjectKey, "Grievance subject key not set").strip();
    }

    /** @return true when the grievance is legally/currently active at the supplied tick */
    public boolean activeAt(long worldTick) {
        return worldTick >= createdTick && (expiresTick < 0L || worldTick < expiresTick);
    }

    @Override
    public int compareTo(DiplomaticGrievanceState other) {
        return grievanceId.compareTo(
                Objects.requireNonNull(other, "DiplomaticGrievanceState not set").grievanceId);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
