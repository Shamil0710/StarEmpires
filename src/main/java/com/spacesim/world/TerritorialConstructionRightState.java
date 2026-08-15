package com.spacesim.world;

import java.util.Objects;

/**
 * Explicit foreign construction concession granted by a territorial controller.
 *
 * <p>The grantor is implicit in the enclosing {@link FactionStrategicState}. This narrow Stage-17D
 * right is intentionally separate from relation thresholds; Stage 17E may later represent the same
 * legal effect as a treaty clause without changing construction authorization semantics.</p>
 *
 * @param granteeFactionContentId stable faction receiving the right
 * @param systemId controlled system where construction is permitted
 * @param grantedTick authoritative world tick when the right was granted
 * @param expiresTick exclusive expiry tick, or {@code -1} for an indefinite right
 */
public record TerritorialConstructionRightState(
        String granteeFactionContentId,
        StarSystemId systemId,
        long grantedTick,
        long expiresTick) implements Comparable<TerritorialConstructionRightState> {

    /** Canonicalizes IDs and validates the bounded/indefinite lifetime. */
    public TerritorialConstructionRightState {
        String grantee = Objects.requireNonNull(
                granteeFactionContentId, "Construction-right grantee faction not set").strip();
        if (grantee.isEmpty()) {
            throw new IllegalArgumentException("Construction-right grantee faction cannot be blank");
        }
        granteeFactionContentId = grantee;
        systemId = Objects.requireNonNull(systemId, "Construction-right StarSystemId not set");
        if (grantedTick < 0L || expiresTick < -1L || expiresTick != -1L && expiresTick <= grantedTick) {
            throw new IllegalArgumentException("Construction-right lifetime is invalid");
        }
    }

    /**
     * Checks whether the concession is effective at one authoritative world tick.
     *
     * @param worldTick non-negative authoritative world tick
     * @return true while the grant has not expired
     */
    public boolean activeAt(long worldTick) {
        if (worldTick < 0L) {
            throw new IllegalArgumentException("World tick cannot be negative");
        }
        return worldTick >= grantedTick && (expiresTick == -1L || worldTick < expiresTick);
    }

    /** @param other another right @return canonical system/grantee ordering */
    @Override
    public int compareTo(TerritorialConstructionRightState other) {
        TerritorialConstructionRightState checked = Objects.requireNonNull(other, "Construction right not set");
        int systemComparison = systemId.compareTo(checked.systemId);
        return systemComparison != 0
                ? systemComparison
                : granteeFactionContentId.compareTo(checked.granteeFactionContentId);
    }
}
