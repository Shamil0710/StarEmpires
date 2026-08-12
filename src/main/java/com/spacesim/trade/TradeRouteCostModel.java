package com.spacesim.trade;

import com.spacesim.persistence.EntityId;

import java.util.Objects;

/**
 * Extension seam для внешних логистических расходов route planner-а.
 *
 * <p>Stage 5 не вводит искусственный fuel/tariff/risk баланс, но planner уже умеет вычесть
 * оценённую стоимость из ожидаемой прибыли. Будущая реализация может учитывать топливо по
 * distance/time, тарифы по faction IDs и ожидаемый риск как денежный penalty, не меняя FSM или
 * структуру market search.</p>
 */
@FunctionalInterface
public interface TradeRouteCostModel {
    /**
     * Оценивает дополнительные расходы маршрута.
     *
     * @param fleet immutable профиль конкретного флота
     * @param context immutable контекст маршрута
     * @return неотрицательная стоимость в milli-credits
     */
    long estimateCostMilliCredits(FleetTradeProfile fleet, Context context);

    /**
     * Возвращает нейтральную модель без дополнительных расходов.
     *
     * @return singleton-compatible stateless zero-cost policy
     */
    static TradeRouteCostModel none() {
        return (fleet, context) -> 0L;
    }

    /**
     * Данные, достаточные для будущих fuel/tariff/risk policies.
     *
     * @param buyStationId supplier ID либо {@code null} для уже купленного cargo
     * @param sellStationId consumer ID
     * @param buyFactionId supplier faction ID либо {@code -1}
     * @param sellFactionId consumer faction ID либо {@code -1}
     * @param itemId runtime item ID
     * @param amount количество товара
     * @param purchaseCostMilliCredits стоимость закупки; 0 для уже купленного cargo
     * @param saleRevenueMilliCredits ожидаемая выручка
     * @param travelDistance полная планируемая дистанция
     * @param travelSeconds полное планируемое время движения
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
            double travelSeconds) {

        /**
         * Проверяет структурные инварианты cost context.
         *
         * @param buyStationId supplier ID либо {@code null} для уже купленного cargo
         * @param sellStationId consumer ID
         * @param buyFactionId supplier faction ID либо {@code -1}
         * @param sellFactionId consumer faction ID либо {@code -1}
         * @param itemId runtime item ID
         * @param amount количество товара
         * @param purchaseCostMilliCredits стоимость закупки; 0 для уже купленного cargo
         * @param saleRevenueMilliCredits ожидаемая выручка
         * @param travelDistance полная планируемая дистанция
         * @param travelSeconds полное планируемое время движения
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
        }
    }
}
