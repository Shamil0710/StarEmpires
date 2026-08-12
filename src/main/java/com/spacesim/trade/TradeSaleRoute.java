package com.spacesim.trade;

import com.spacesim.persistence.EntityId;

import java.util.Objects;

/**
 * Неизменяемый маршрут реализации уже находящегося на борту груза.
 *
 * <p>Для такого решения закупочная цена является sunk cost, поэтому planner сравнивает доступную
 * выручку либо выручку на секунду движения в зависимости от scoring policy.</p>
 *
 * @param sellStationId persistent ID станции-покупателя
 * @param itemId runtime ID товара
 * @param amount количество для продажи
 * @param saleRevenueMilliCredits ожидаемая выручка
 * @param travelDistance дистанция fleet -> consumer
 * @param travelSeconds ожидаемое время движения
 */
public record TradeSaleRoute(
        EntityId sellStationId,
        int itemId,
        int amount,
        long saleRevenueMilliCredits,
        float travelDistance,
        double travelSeconds) {

    /**
     * Проверяет структурные инварианты sale route.
     *
     * @param sellStationId persistent ID станции-покупателя
     * @param itemId runtime ID товара
     * @param amount количество для продажи
     * @param saleRevenueMilliCredits ожидаемая выручка
     * @param travelDistance дистанция fleet -> consumer
     * @param travelSeconds ожидаемое время движения
     */
    public TradeSaleRoute {
        Objects.requireNonNull(sellStationId, "sellStationId не задан");
        if (itemId < 0 || amount <= 0 || saleRevenueMilliCredits <= 0L) {
            throw new IllegalArgumentException("Sale route содержит некорректный товар/объём/выручку");
        }
        if (!Float.isFinite(travelDistance) || travelDistance < 0f) {
            throw new IllegalArgumentException("Дистанция sale route должна быть конечной и неотрицательной");
        }
        if (!Double.isFinite(travelSeconds) || travelSeconds < 0d) {
            throw new IllegalArgumentException("Время sale route должно быть конечным и неотрицательным");
        }
    }

    /**
     * Возвращает ожидаемую выручку на секунду движения.
     *
     * @return milli-credits/second либо positive infinity для нулевого времени
     */
    public double revenuePerSecond() {
        return travelSeconds == 0d
                ? Double.POSITIVE_INFINITY
                : saleRevenueMilliCredits / travelSeconds;
    }
}
