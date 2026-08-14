package com.spacesim.player;

import com.spacesim.world.StarSystemId;

import java.util.List;
import java.util.Objects;

/**
 * Read-only deterministic diagnostic of one player fleet route choice.
 *
 * @param path complete discovered-system path including origin and destination
 * @param travelTicks physical Stage-10 jump timing cost across all links
 * @param systemExposure cumulative effective observed system danger
 * @param linkExposure cumulative effective observed link danger
 * @param uncertaintyExposure cumulative unknown-segment uncertainty premium
 * @param vulnerability actor-specific cargo/damage/mobility multiplier
 * @param riskCostTicks route exposure converted to planner cost, not probability
 * @param totalCost travel plus risk cost used for route comparison
 */
public record PlayerRouteRiskView(
        List<StarSystemId> path,
        long travelTicks,
        double systemExposure,
        double linkExposure,
        double uncertaintyExposure,
        double vulnerability,
        double riskCostTicks,
        double totalCost) {

    /**
     * Validates one immutable route diagnostic.
     *
     * @param path complete path
     * @param travelTicks non-negative physical travel ticks
     * @param systemExposure non-negative system exposure
     * @param linkExposure non-negative link exposure
     * @param uncertaintyExposure non-negative uncertainty exposure
     * @param vulnerability positive actor multiplier
     * @param riskCostTicks non-negative risk cost
     * @param totalCost non-negative comparison cost
     */
    public PlayerRouteRiskView {
        path = List.copyOf(Objects.requireNonNull(path, "Route path not set"));
        if (path.isEmpty() || travelTicks < 0L
                || !nonNegative(systemExposure) || !nonNegative(linkExposure)
                || !nonNegative(uncertaintyExposure) || !positive(vulnerability)
                || !nonNegative(riskCostTicks) || !nonNegative(totalCost)) {
            throw new IllegalArgumentException("Invalid route risk diagnostics");
        }
    }

    /** @return true when the route contains at least one traversed link */
    public boolean travels() {
        return path.size() > 1;
    }

    private static boolean nonNegative(double value) {
        return Double.isFinite(value) && value >= 0d;
    }

    private static boolean positive(double value) {
        return Double.isFinite(value) && value > 0d;
    }
}
