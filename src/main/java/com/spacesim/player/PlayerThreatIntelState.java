package com.spacesim.player;

import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Persistent non-omniscient danger observation available to player fleet planning.
 *
 * <p>{@code dangerScore} is an arbitrary non-negative exposure score, explicitly not a
 * probability. Confidence and observation age are applied by the route planner. LINK records use
 * canonical endpoint ordering so the same undirected corridor cannot be stored twice.</p>
 *
 * @param kind system or link observation
 * @param systemA observed system or canonical first link endpoint
 * @param systemB canonical second link endpoint for LINK, otherwise {@code null}
 * @param dangerScore non-negative raw danger score, not probability
 * @param confidence observation confidence in {@code [0,1]}
 * @param observedTick authoritative simulation tick at observation time
 */
public record PlayerThreatIntelState(
        PlayerThreatIntelKind kind,
        StarSystemId systemA,
        StarSystemId systemB,
        float dangerScore,
        float confidence,
        long observedTick) implements Comparable<PlayerThreatIntelState> {

    /**
     * Validates and canonicalizes one observation.
     *
     * @param kind system or link observation
     * @param systemA observed system or first link endpoint
     * @param systemB second link endpoint for LINK
     * @param dangerScore non-negative raw score
     * @param confidence confidence in {@code [0,1]}
     * @param observedTick non-negative authoritative tick
     */
    public PlayerThreatIntelState {
        kind = Objects.requireNonNull(kind, "Threat intel kind not set");
        systemA = Objects.requireNonNull(systemA, "Threat intel system not set");
        if (!Float.isFinite(dangerScore) || dangerScore < 0f) {
            throw new IllegalArgumentException("Threat danger score must be finite and non-negative");
        }
        if (!Float.isFinite(confidence) || confidence < 0f || confidence > 1f) {
            throw new IllegalArgumentException("Threat confidence must belong to [0,1]");
        }
        if (observedTick < 0L) {
            throw new IllegalArgumentException("Threat observation tick cannot be negative");
        }
        if (kind == PlayerThreatIntelKind.SYSTEM) {
            if (systemB != null) {
                throw new IllegalArgumentException("SYSTEM threat intel cannot contain second endpoint");
            }
        } else {
            systemB = Objects.requireNonNull(systemB, "LINK threat intel second endpoint not set");
            if (systemA.equals(systemB)) {
                throw new IllegalArgumentException("LINK threat intel requires two different systems");
            }
            if (systemA.compareTo(systemB) > 0) {
                StarSystemId swap = systemA;
                systemA = systemB;
                systemB = swap;
            }
        }
    }

    /**
     * Creates a system danger observation.
     *
     * @param systemId observed system
     * @param dangerScore raw danger score
     * @param confidence observation confidence
     * @param observedTick observation tick
     * @return validated system observation
     */
    public static PlayerThreatIntelState system(
            StarSystemId systemId,
            float dangerScore,
            float confidence,
            long observedTick) {
        return new PlayerThreatIntelState(
                PlayerThreatIntelKind.SYSTEM, systemId, null, dangerScore, confidence, observedTick);
    }

    /**
     * Creates a link/corridor danger observation.
     *
     * @param first first endpoint
     * @param second second endpoint
     * @param dangerScore raw danger score
     * @param confidence observation confidence
     * @param observedTick observation tick
     * @return validated canonical link observation
     */
    public static PlayerThreatIntelState link(
            StarSystemId first,
            StarSystemId second,
            float dangerScore,
            float confidence,
            long observedTick) {
        return new PlayerThreatIntelState(
                PlayerThreatIntelKind.LINK, first, second, dangerScore, confidence, observedTick);
    }

    /** @return true when this observation describes the supplied undirected corridor */
    public boolean matchesLink(StarSystemId first, StarSystemId second) {
        if (kind != PlayerThreatIntelKind.LINK || first == null || second == null) {
            return false;
        }
        StarSystemId low = first.compareTo(second) <= 0 ? first : second;
        StarSystemId high = first.compareTo(second) <= 0 ? second : first;
        return systemA.equals(low) && systemB.equals(high);
    }

    /** Stable canonical ordering for bounded persistence and deterministic planning. */
    @Override
    public int compareTo(PlayerThreatIntelState other) {
        PlayerThreatIntelState checked = Objects.requireNonNull(other, "Other threat intel not set");
        int byKind = kind.compareTo(checked.kind);
        if (byKind != 0) {
            return byKind;
        }
        int byA = systemA.compareTo(checked.systemA);
        if (byA != 0) {
            return byA;
        }
        if (systemB == null) {
            return checked.systemB == null ? 0 : -1;
        }
        return checked.systemB == null ? 1 : systemB.compareTo(checked.systemB);
    }
}
