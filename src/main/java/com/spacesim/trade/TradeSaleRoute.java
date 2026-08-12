package com.spacesim.trade;

import com.spacesim.persistence.EntityId;

import java.util.Objects;

/**
 * Неизменяемый маршрут реализации уже находящегося на борту груза.
 *
 * <p>Для такого решения закупочная цена является sunk cost. Внешние route costs могут учитывать
 * топливо, тарифы или ожидаемый risk penalty без изменения execution FSM.</p>
 *
 * @param sellStationId persistent ID станции-покупателя
 * @param itemId runtime ID товара
 * @param amount количество для продажи
 * @param saleRevenueMilliCredits валовая ожидаемая выручка
 * @param estimatedRouteCostMilliCredits внешние route costs
 * @param netRevenueMilliCredits ожидаемая выручка после route costs
 * @param travelDistance дистанция fleet -> consumer
 * @param travelSeconds ожидаемое время движения
 */
public record TradeSaleRoute(
        EntityId sellStationId,
        int itemId,
        int amount,
        long saleRevenueMilliCredits,
        long estimatedRouteCostMilliCredits,
        long netRevenueMilliCredits,
        float travelDistance,
        double travelSeconds) {

    /**
     * Проверяет структурные инварианты sale route.
     *
     * @param sellStationId persistent ID станции-покупателя
     * @param itemId runtime ID товара
     * @param amount количество для продажи
     * @param saleRevenueMilliCredits валовая ожидаемая выручка
     * @param estimatedRouteCostMilliCredits внешние route costs
     * @param netRevenueMilliCredits ожидаемая выручка после route costs
     * @param travelDistance дистанция fleet -> consumer
     * @param travelSeconds ожидаемое время движения
     */
    public TradeSaleRoute {
        Objects.requireNonNull(sellStationId, "sellStationId не задан");
        if (itemId < 0 || amount <= 0
                || saleRevenueMilliCredits <= 0L
                || estimatedRouteCostMilliCredits < 0L
                || netRevenueMilliCredits != saleRevenueMilliCredits - estimatedRouteCostMilliCredits
                || netRevenueMilliCredits <= 0L) {
            throw new IllegalArgumentException("Sale route содержит несогласованную экономику");
        }
        if (!Float.isFinite(travelDistance) || travelDistance < 0f) {
            throw new IllegalArgumentException("Дистанция sale route должна быть конечной и неотрицательной");
        }
        if (!Double.isFinite(travelSeconds) || travelSeconds < 0d) {
            throw new IllegalArgumentException("Время sale route должно быть конечным и неотрицательным");
        }
    }

    /**
     * Возвращает валовую выручку на секунду движения.
     *
     * @return gross milli-credits/second либо positive infinity для нулевого времени
     */
    public double revenuePerSecond() {
        return travelSeconds == 0d
                ? Double.POSITIVE_INFINITY
                : saleRevenueMilliCredits / travelSeconds;
    }

    /**
     * Возвращает ожидаемую чистую выручку на секунду движения.
     *
     * @return net milli-credits/second либо positive infinity для нулевого времени
     */
    public double netRevenuePerSecond() {
        return travelSeconds == 0d
                ? Double.POSITIVE_INFINITY
                : netRevenueMilliCredits / travelSeconds;
    }
}
