package com.spacesim.trade;

import com.spacesim.persistence.EntityId;

import java.util.Objects;

/**
 * Неизменяемый план одного нового торгового рейса.
 *
 * <p>Value-object не содержит Ashley {@code Entity}: станции адресуются persistent
 * {@link EntityId}. Денежные значения выражены в milli-credits, расстояние — в мировых единицах,
 * время — в игровых секундах.</p>
 *
 * @param buyStationId станция-поставщик, у которой корабль покупает товар
 * @param sellStationId станция-потребитель, которой корабль продаёт товар
 * @param itemId плотный runtime ID товара
 * @param amount планируемое количество товара
 * @param purchaseCostMilliCredits полная стоимость закупки
 * @param saleRevenueMilliCredits полная ожидаемая выручка
 * @param grossProfitMilliCredits валовая прибыль до route costs
 * @param estimatedRouteCostMilliCredits внешние fuel/tariff/risk costs
 * @param netProfitMilliCredits ожидаемая прибыль после route costs
 * @param travelDistance полная дистанция fleet -> supplier -> consumer
 * @param travelSeconds оценка времени движения без docking/trade overhead
 */
public record TradeRoute(
        EntityId buyStationId,
        EntityId sellStationId,
        int itemId,
        int amount,
        long purchaseCostMilliCredits,
        long saleRevenueMilliCredits,
        long grossProfitMilliCredits,
        long estimatedRouteCostMilliCredits,
        long netProfitMilliCredits,
        float travelDistance,
        double travelSeconds) {

    /**
     * Проверяет структурные инварианты маршрута.
     *
     * @param buyStationId станция-поставщик, у которой корабль покупает товар
     * @param sellStationId станция-потребитель, которой корабль продаёт товар
     * @param itemId плотный runtime ID товара
     * @param amount планируемое количество товара
     * @param purchaseCostMilliCredits полная стоимость закупки
     * @param saleRevenueMilliCredits полная ожидаемая выручка
     * @param grossProfitMilliCredits валовая прибыль до route costs
     * @param estimatedRouteCostMilliCredits внешние fuel/tariff/risk costs
     * @param netProfitMilliCredits ожидаемая прибыль после route costs
     * @param travelDistance полная дистанция fleet -> supplier -> consumer
     * @param travelSeconds оценка времени движения без docking/trade overhead
     */
    public TradeRoute {
        Objects.requireNonNull(buyStationId, "buyStationId не задан");
        Objects.requireNonNull(sellStationId, "sellStationId не задан");
        if (buyStationId.equals(sellStationId)) {
            throw new IllegalArgumentException("Покупка и продажа не могут происходить на одной станции");
        }
        if (itemId < 0 || amount <= 0) {
            throw new IllegalArgumentException("Некорректный itemId/amount маршрута");
        }
        if (purchaseCostMilliCredits <= 0L
                || saleRevenueMilliCredits <= purchaseCostMilliCredits
                || grossProfitMilliCredits != saleRevenueMilliCredits - purchaseCostMilliCredits
                || estimatedRouteCostMilliCredits < 0L
                || netProfitMilliCredits != grossProfitMilliCredits - estimatedRouteCostMilliCredits
                || netProfitMilliCredits <= 0L) {
            throw new IllegalArgumentException("Денежные значения маршрута несогласованы");
        }
        if (!Float.isFinite(travelDistance) || travelDistance < 0f) {
            throw new IllegalArgumentException("Дистанция маршрута должна быть конечной и неотрицательной");
        }
        if (!Double.isFinite(travelSeconds) || travelSeconds < 0d) {
            throw new IllegalArgumentException("Время маршрута должно быть конечным и неотрицательным");
        }
    }

    /**
     * Возвращает валовую прибыль на игровую секунду движения.
     *
     * @return gross milli-credits/second либо positive infinity для нулевого времени
     */
    public double grossProfitPerSecond() {
        return travelSeconds == 0d
                ? Double.POSITIVE_INFINITY
                : grossProfitMilliCredits / travelSeconds;
    }

    /**
     * Возвращает ожидаемую чистую прибыль на игровую секунду движения.
     *
     * @return net milli-credits/second либо positive infinity для нулевого времени
     */
    public double netProfitPerSecond() {
        return travelSeconds == 0d
                ? Double.POSITIVE_INFINITY
                : netProfitMilliCredits / travelSeconds;
    }
}
