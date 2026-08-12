package com.spacesim.trade;

import com.spacesim.persistence.EntityId;

import java.util.Objects;

/**
 * Общий market-level кандидат supplier -> consumer для одного товара.
 *
 * <p>Opportunity не зависит от конкретного флота: он содержит raw market prices и расстояние между
 * станциями. Репутация, доступный капитал, cargo capacity, начальная дистанция fleet -> supplier и
 * итоговая scoring policy применяются позднее {@link TradeRoutePlanner}.</p>
 *
 * @param buyStationId persistent ID станции-поставщика
 * @param sellStationId persistent ID станции-покупателя
 * @param itemId runtime ID товара
 * @param rawPurchasePrice raw sell price поставщика в credits/unit
 * @param rawSalePrice raw buy price покупателя в credits/unit
 * @param stationDistance дистанция supplier -> consumer в мировых единицах
 */
public record TradeOpportunity(
        EntityId buyStationId,
        EntityId sellStationId,
        int itemId,
        float rawPurchasePrice,
        float rawSalePrice,
        float stationDistance) {

    /**
     * Проверяет структурные инварианты market opportunity.
     *
     * @param buyStationId persistent ID станции-поставщика
     * @param sellStationId persistent ID станции-покупателя
     * @param itemId runtime ID товара
     * @param rawPurchasePrice raw sell price поставщика в credits/unit
     * @param rawSalePrice raw buy price покупателя в credits/unit
     * @param stationDistance дистанция supplier -> consumer в мировых единицах
     */
    public TradeOpportunity {
        Objects.requireNonNull(buyStationId, "buyStationId не задан");
        Objects.requireNonNull(sellStationId, "sellStationId не задан");
        if (buyStationId.equals(sellStationId)) {
            throw new IllegalArgumentException("Opportunity требует две разные станции");
        }
        if (itemId < 0) {
            throw new IllegalArgumentException("itemId не может быть отрицательным");
        }
        if (!Float.isFinite(rawPurchasePrice) || rawPurchasePrice <= 0f
                || !Float.isFinite(rawSalePrice) || rawSalePrice <= 0f) {
            throw new IllegalArgumentException("Raw market prices должны быть конечными и положительными");
        }
        if (!Float.isFinite(stationDistance) || stationDistance < 0f) {
            throw new IllegalArgumentException("Station distance должна быть конечной и неотрицательной");
        }
    }
}
