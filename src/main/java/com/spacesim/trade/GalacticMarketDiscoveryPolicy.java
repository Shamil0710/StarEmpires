package com.spacesim.trade;

/**
 * Explicit complexity and horizon policy for cross-system market discovery.
 *
 * @param maxJumpHops maximum topology hops from the fleet's current system
 * @param maxSystems maximum reachable systems inspected, excluding the origin
 * @param maxConsumersPerSystemPerItem maximum remote consumers inspected per item and system
 * @param maxOpportunities maximum candidates returned to the pure route scorer
 * @param riskPerJumpBasisPoints planning-only expected risk added per jump hop
 */
public record GalacticMarketDiscoveryPolicy(
        int maxJumpHops,
        int maxSystems,
        int maxConsumersPerSystemPerItem,
        int maxOpportunities,
        int riskPerJumpBasisPoints) {

    /** Conservative default suitable for the current regional-scale simulation. */
    public static final GalacticMarketDiscoveryPolicy DEFAULT =
            new GalacticMarketDiscoveryPolicy(4, 24, 4, 128, 0);

    /** Validates all bounded-discovery limits. */
    public GalacticMarketDiscoveryPolicy {
        if (maxJumpHops <= 0) {
            throw new IllegalArgumentException("maxJumpHops должен быть положительным");
        }
        if (maxSystems <= 0) {
            throw new IllegalArgumentException("maxSystems должен быть положительным");
        }
        if (maxConsumersPerSystemPerItem <= 0) {
            throw new IllegalArgumentException("maxConsumersPerSystemPerItem должен быть положительным");
        }
        if (maxOpportunities <= 0) {
            throw new IllegalArgumentException("maxOpportunities должен быть положительным");
        }
        if (riskPerJumpBasisPoints < 0 || riskPerJumpBasisPoints > 10_000) {
            throw new IllegalArgumentException("riskPerJumpBasisPoints должен быть в диапазоне 0..10000");
        }
    }
}
