package com.spacesim.world;

import java.util.Objects;

/**
 * Explicit unilateral legal prohibition. Embargoes alter access rules; they never create abstract
 * economic damage, money or resources by themselves.
 *
 * @param targetFactionContentId sanctioned faction
 * @param scope embargo scope
 * @param imposedTick authoritative imposition tick
 * @param expiresTick exclusive expiry tick or -1 for indefinite
 * @param reasonKey stable diagnostic/political reason key, possibly empty
 */
public record DiplomaticEmbargoState(
        String targetFactionContentId,
        Scope scope,
        long imposedTick,
        long expiresTick,
        String reasonKey) implements Comparable<DiplomaticEmbargoState> {

    /** Bounded embargo scopes. More granular sanctions may be added with matching physical rules. */
    public enum Scope {
        /** Ordinary market access between the two factions is legally prohibited. */
        MARKET_ACCESS
    }

    /** Validates and normalizes one embargo. */
    public DiplomaticEmbargoState {
        targetFactionContentId = requireId(targetFactionContentId, "Embargo target faction ID");
        scope = Objects.requireNonNull(scope, "Embargo scope not set");
        if (imposedTick < 0L) {
            throw new IllegalArgumentException("Embargo imposition tick cannot be negative");
        }
        if (expiresTick != -1L && expiresTick <= imposedTick) {
            throw new IllegalArgumentException("Embargo expiry must follow imposition or be -1");
        }
        reasonKey = Objects.requireNonNull(reasonKey, "Embargo reason key not set").strip();
    }

    /** @return true while the embargo is legally active at the supplied tick */
    public boolean activeAt(long worldTick) {
        return worldTick >= imposedTick && (expiresTick < 0L || worldTick < expiresTick);
    }

    @Override
    public int compareTo(DiplomaticEmbargoState other) {
        DiplomaticEmbargoState value = Objects.requireNonNull(other, "DiplomaticEmbargoState not set");
        int byTarget = targetFactionContentId.compareTo(value.targetFactionContentId);
        return byTarget != 0 ? byTarget : scope.compareTo(value.scope);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
