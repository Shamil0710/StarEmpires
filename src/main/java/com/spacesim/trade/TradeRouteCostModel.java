package com.spacesim.trade;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.GalacticPath;
import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Extension seam для внешних логистических расходов route planner-а.
 *
 * <p>Fuel/tariff/risk policies подключаются через этот единый cost seam и участвуют в net scoring.
 * Local routes используют legacy constructor контекста; galactic routes дополнительно передают
 * system/path/risk metadata, не создавая параллельный scoring stack.</p>
 */
@FunctionalInterface
public interface TradeRouteCostModel {
    /**
     * @param fleet immutable профиль конкретного флота
     * @param context immutable контекст маршрута
     * @return неотрицательная стоимость в milli-credits
     */
    long estimateCostMilliCredits(FleetTradeProfile fleet, Context context);

    /** @return stateless zero-cost policy */
    static TradeRouteCostModel none() {
        return (fleet, context) -> 0L;
    }

    /**
     * Planner context shared by local and galactic routes.
     *
     * @param buyStationId supplier ID либо {@code null} для уже купленного cargo
     * @param sellStationId consumer ID
     * @param buyFactionId supplier faction ID либо {@code -1}
     * @param sellFactionId consumer faction ID либо {@code -1}
     * @param itemId runtime item ID
     * @param amount количество товара
     * @param purchaseCostMilliCredits стоимость закупки; 0 для уже купленного cargo
     * @param saleRevenueMilliCredits ожидаемая выручка
     * @param travelDistance explicit local/in-system distance used by this plan
     * @param travelSeconds full expected movement duration
     * @param buySystemId supplier system for galactic routes, otherwise {@code null}
     * @param sellSystemId consumer system for galactic routes, otherwise {@code null}
     * @param jumpPath galactic jump path, otherwise {@code null}
     * @param routeRiskBasisPoints expected route risk in basis points
     */
    record Context(
            EntityId buyStationId,
            EntityId sellStationId,
            int buyFactionId,
            int sellFactionId,
            int itemId,
            int amount,
            long purchaseCostMilliCredits,
            long saleRevenueMilliCredits,
            float travelDistance,
            double travelSeconds,
            StarSystemId buySystemId,
            StarSystemId sellSystemId,
            GalacticPath jumpPath,
            int routeRiskBasisPoints) {

        /**
         * Source-compatible local-route constructor.
         *
         * @param buyStationId supplier ID либо {@code null}
         * @param sellStationId consumer ID
         * @param buyFactionId supplier faction ID либо {@code -1}
         * @param sellFactionId consumer faction ID либо {@code -1}
         * @param itemId runtime item ID
         * @param amount quantity
         * @param purchaseCostMilliCredits purchase cost
         * @param saleRevenueMilliCredits sale revenue
         * @param travelDistance local route distance
         * @param travelSeconds route duration
         */
        public Context(
                EntityId buyStationId,
                EntityId sellStationId,
                int buyFactionId,
                int sellFactionId,
                int itemId,
                int amount,
                long purchaseCostMilliCredits,
                long saleRevenueMilliCredits,
                float travelDistance,
                double travelSeconds) {
            this(
                    buyStationId,
                    sellStationId,
                    buyFactionId,
                    sellFactionId,
                    itemId,
                    amount,
                    purchaseCostMilliCredits,
                    saleRevenueMilliCredits,
                    travelDistance,
                    travelSeconds,
                    null,
                    null,
                    null,
                    0);
        }

        /**
         * @param buyStationId supplier ID or {@code null} for existing cargo
         * @param sellStationId consumer ID
         * @param buyFactionId supplier runtime faction ID or {@code -1}
         * @param sellFactionId consumer runtime faction ID or {@code -1}
         * @param itemId runtime item ID
         * @param amount cargo amount
         * @param purchaseCostMilliCredits purchase cost
         * @param saleRevenueMilliCredits sale revenue
         * @param travelDistance explicit local distance estimate
         * @param travelSeconds full expected movement duration
         * @param buySystemId supplier system for galactic route or {@code null}
         * @param sellSystemId consumer system for galactic route or {@code null}
         * @param jumpPath galactic path or {@code null}
         * @param routeRiskBasisPoints expected route risk in basis points
         */
        public Context {
            Objects.requireNonNull(sellStationId, "sellStationId не задан");
            if (itemId < 0 || amount <= 0
                    || purchaseCostMilliCredits < 0L
                    || saleRevenueMilliCredits <= 0L) {
                throw new IllegalArgumentException("Route cost context содержит некорректную экономику");
            }
            if (!Float.isFinite(travelDistance) || travelDistance < 0f
                    || !Double.isFinite(travelSeconds) || travelSeconds < 0d) {
                throw new IllegalArgumentException("Route cost context содержит некорректную логистику");
            }
            if (routeRiskBasisPoints < 0 || routeRiskBasisPoints > 10_000) {
                throw new IllegalArgumentException("Route risk должен быть в диапазоне 0..10000 bps");
            }
            boolean hasBuySystem = buySystemId != null;
            boolean hasSellSystem = sellSystemId != null;
            if (hasBuySystem != hasSellSystem) {
                throw new IllegalArgumentException("Galactic context требует обе system IDs");
            }
            if (jumpPath != null) {
                if (!hasBuySystem
                        || !buySystemId.equals(jumpPath.origin())
                        || !sellSystemId.equals(jumpPath.destination())) {
                    throw new IllegalArgumentException("Galactic context не согласован с jump path");
                }
            } else if (hasBuySystem || routeRiskBasisPoints != 0) {
                throw new IllegalArgumentException("Galactic metadata требует jump path");
            }
        }

        /** @return whether this context represents a cross-system route */
        public boolean isGalactic() {
            return jumpPath != null;
        }
    }
}
