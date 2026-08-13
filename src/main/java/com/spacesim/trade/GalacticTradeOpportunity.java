package com.spacesim.trade;

import com.spacesim.constants.Constants;
import com.spacesim.world.GalacticPath;

import java.util.Objects;

/**
 * Bounded cross-system supplier-consumer candidate prepared for pure economic scoring.
 *
 * @param supplier supplier market in the fleet's current system
 * @param consumer remote consumer market
 * @param itemId runtime item ID
 * @param jumpPath deterministic supplier-system to consumer-system path
 * @param localTravelDistance explicitly estimated in-system travel distance
 * @param localTravelSeconds explicitly estimated in-system travel time
 * @param routeRiskBasisPoints expected route risk in basis points
 */
public record GalacticTradeOpportunity(
        SystemMarketRef supplier,
        SystemMarketRef consumer,
        int itemId,
        GalacticPath jumpPath,
        float localTravelDistance,
        double localTravelSeconds,
        int routeRiskBasisPoints) {

    /**
     * @param supplier supplier market in the fleet's current system
     * @param consumer remote consumer market
     * @param itemId runtime item ID
     * @param jumpPath deterministic supplier-to-consumer jump path
     * @param localTravelDistance explicit in-system travel distance estimate
     * @param localTravelSeconds explicit in-system travel time estimate
     * @param routeRiskBasisPoints expected route risk in basis points
     */
    public GalacticTradeOpportunity {
        Objects.requireNonNull(supplier, "Galactic supplier не задан");
        Objects.requireNonNull(consumer, "Galactic consumer не задан");
        Objects.requireNonNull(jumpPath, "Galactic jump path не задан");
        if (itemId < 0 || itemId >= Constants.MAX_ITEMS) {
            throw new IllegalArgumentException("Некорректный runtime item ID galactic opportunity");
        }
        if (!supplier.systemId().equals(jumpPath.origin())
                || !consumer.systemId().equals(jumpPath.destination())
                || jumpPath.jumpCount() <= 0) {
            throw new IllegalArgumentException("Galactic opportunity не согласована с jump path");
        }
        if (!Float.isFinite(localTravelDistance) || localTravelDistance < 0f
                || !Double.isFinite(localTravelSeconds) || localTravelSeconds < 0d) {
            throw new IllegalArgumentException("Некорректная local travel estimate");
        }
        if (routeRiskBasisPoints < 0 || routeRiskBasisPoints > 10_000) {
            throw new IllegalArgumentException("Route risk должен быть в диапазоне 0..10000 bps");
        }
    }

    /** @return local travel plus authoritative jump time */
    public double totalExpectedSeconds() {
        return localTravelSeconds + jumpPath.totalJumpSeconds();
    }
}
