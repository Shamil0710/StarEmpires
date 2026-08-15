package com.spacesim.world;

import java.util.Objects;

/**
 * Directed diplomatic recognition of another faction's territorial position.
 *
 * <p>The recognizing faction is implicit in the enclosing {@link FactionStrategicState}. Recognition
 * records political/legal acknowledgement only; it cannot create control or physical presence.</p>
 *
 * @param targetFactionContentId stable faction whose position is recognized
 * @param systemId affected star system
 * @param kind whether a claim or established control is recognized
 */
public record TerritorialRecognitionState(
        String targetFactionContentId,
        StarSystemId systemId,
        Kind kind) implements Comparable<TerritorialRecognitionState> {

    /** Kind of territorial position acknowledged by the recognizing faction. */
    public enum Kind {
        /** Recognition of the target faction's political claim. */
        CLAIM,
        /** Recognition of the target faction's established control. */
        CONTROL
    }

    /**
     * Canonicalizes the target stable ID.
     *
     * @param targetFactionContentId stable faction whose position is recognized
     * @param systemId affected star system
     * @param kind whether a claim or established control is recognized
     */
    public TerritorialRecognitionState {
        String target = Objects.requireNonNull(
                targetFactionContentId, "Territorial recognition target faction not set").strip();
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Territorial recognition target faction cannot be blank");
        }
        targetFactionContentId = target;
        systemId = Objects.requireNonNull(systemId, "Territorial recognition StarSystemId not set");
        kind = Objects.requireNonNull(kind, "Territorial recognition kind not set");
    }

    /** @param other another recognition @return canonical system/target/kind ordering */
    @Override
    public int compareTo(TerritorialRecognitionState other) {
        TerritorialRecognitionState checked = Objects.requireNonNull(other, "Territorial recognition not set");
        int systemComparison = systemId.compareTo(checked.systemId);
        if (systemComparison != 0) {
            return systemComparison;
        }
        int factionComparison = targetFactionContentId.compareTo(checked.targetFactionContentId);
        return factionComparison != 0 ? factionComparison : kind.compareTo(checked.kind);
    }
}
