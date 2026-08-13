package com.spacesim.trade;

import com.spacesim.constants.Constants;
import com.spacesim.persistence.EntityId;
import com.spacesim.world.GalacticPath;
import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Pure value result of one cross-system cargo plan.
 *
 * @param buySystemId supplier StarSystem
 * @param buyStationId supplier local EntityId
 * @param sellSystemId consumer StarSystem
 * @param sellStationId consumer local EntityId
 * @param itemId runtime item ID
 * @param amount planned cargo amount
 * @param purchaseCostMilliCredits full expected purchase cost
 * @param saleRevenueMilliCredits full expected sale revenue
 * @param grossProfitMilliCredits revenue minus purchase cost
 * @param routeCostMilliCredits external fuel/tariff/risk cost
 * @param netProfitMilliCredits gross profit minus route cost
 * @param localTravelDistance explicitly estimated in-system distance
 * @param strategicJumpDistance topology distance of the jump path
 * @param expectedDurationSeconds local plus jump time
 * @param routeRiskBasisPoints route risk input in basis points
 * @param jumpPath deterministic supplier-to-consumer path
 */
public record GalacticTradeRoute(
        StarSystemId buySystemId,
        EntityId buyStationId,
        StarSystemId sellSystemId,
        EntityId sellStationId,
        int itemId,
        int amount,
        long purchaseCostMilliCredits,
        long saleRevenueMilliCredits,
        long grossProfitMilliCredits,
        long routeCostMilliCredits,
        long netProfitMilliCredits,
        float localTravelDistance,
        double strategicJumpDistance,
        double expectedDurationSeconds,
        int routeRiskBasisPoints,
        GalacticPath jumpPath) {

    /** Validates the immutable galactic route result. */
    public GalacticTradeRoute {
        Objects.requireNonNull(buySystemId, "buySystemId не задан");
        Objects.requireNonNull(buyStationId, "buyStationId не задан");
        Objects.requireNonNull(sellSystemId, "sellSystemId не задан");
        Objects.requireNonNull(sellStationId, "sellStationId не задан");
        Objects.requireNonNull(jumpPath, "jumpPath не задан");
        if (itemId < 0 || itemId >= Constants.MAX_ITEMS || amount <= 0) {
            throw new IllegalArgumentException("Некорректный товар или amount galactic route");
        }
        if (purchaseCostMilliCredits <= 0L
                || saleRevenueMilliCredits <= purchaseCostMilliCredits
                || grossProfitMilliCredits != saleRevenueMilliCredits - purchaseCostMilliCredits
                || routeCostMilliCredits < 0L
                || routeCostMilliCredits >= grossProfitMilliCredits
                || netProfitMilliCredits != grossProfitMilliCredits - routeCostMilliCredits) {
            throw new IllegalArgumentException("Некорректная экономика galactic route");
        }
        if (!Float.isFinite(localTravelDistance) || localTravelDistance < 0f
                || !Double.isFinite(strategicJumpDistance) || strategicJumpDistance < 0d
                || !Double.isFinite(expectedDurationSeconds) || expectedDurationSeconds < 0d) {
            throw new IllegalArgumentException("Некорректная логистика galactic route");
        }
        if (routeRiskBasisPoints < 0 || routeRiskBasisPoints > 10_000) {
            throw new IllegalArgumentException("Некорректный route risk");
        }
        if (!buySystemId.equals(jumpPath.origin()) || !sellSystemId.equals(jumpPath.destination())) {
            throw new IllegalArgumentException("Galactic route path endpoints не совпадают с рынками");
        }
    }

    /** @return net expected profit per second, or positive infinity for zero duration */
    public double netProfitPerSecond() {
        return expectedDurationSeconds == 0d
                ? Double.POSITIVE_INFINITY
                : netProfitMilliCredits / expectedDurationSeconds;
    }
}
