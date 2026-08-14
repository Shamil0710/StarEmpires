package com.spacesim.player;

import java.util.Objects;

/**
 * Read-only deterministic Stage-16 supplier choice for one construction-supply order.
 *
 * @param supplier discovered physical market selected for purchase
 * @param route cumulative Stage-15 travel/risk plan from the supply fleet to supplier system
 * @param rawSellPriceCredits current public raw market sell price per unit
 * @param availableUnits current physical supplier stock of the requested material
 */
public record PlayerSupplyProjectPlan(
        DiscoveredObjectRef supplier,
        PlayerRouteRiskView route,
        float rawSellPriceCredits,
        int availableUnits) {

    /**
     * Validates one immutable supplier plan.
     *
     * @param supplier discovered physical market selected for purchase
     * @param route cumulative route/risk diagnostics
     * @param rawSellPriceCredits positive finite current raw price
     * @param availableUnits positive physical stock
     */
    public PlayerSupplyProjectPlan {
        Objects.requireNonNull(supplier, "Supply supplier not set");
        Objects.requireNonNull(route, "Supply supplier route not set");
        if (!Float.isFinite(rawSellPriceCredits) || rawSellPriceCredits <= 0f) {
            throw new IllegalArgumentException("Supply supplier price must be positive and finite");
        }
        if (availableUnits <= 0) {
            throw new IllegalArgumentException("Supply supplier stock must be positive");
        }
    }
}
