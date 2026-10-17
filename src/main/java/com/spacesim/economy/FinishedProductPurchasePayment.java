package com.spacesim.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Personal money held until reserved civilian products physically reach the buyer's hold.
 * @param sellerFactionId original legal operator
 * @param reservedMilliCredits exact positive purchase balance
 */
public record FinishedProductPurchasePayment(String sellerFactionId, long reservedMilliCredits) {
    /**
     * Validates the operator and held balance.
     * @param sellerFactionId original legal operator
     * @param reservedMilliCredits positive held balance
     */
    public FinishedProductPurchasePayment {
        sellerFactionId = com.spacesim.world.WorldFactionIdentityState.normalizeStableId(sellerFactionId);
        if (reservedMilliCredits <= 0) throw new IllegalArgumentException("Invalid product purchase payment");
    }

    /**
     * Uses the same disclosed equipment mass tariff as civilian refit, without refit work.
     * @param productId authored physical product
     * @param count whole units
     * @return exact total milli-credits, rounded upward once
     */
    public static long quote(String productId, int count) {
        var product = com.spacesim.content.Stage23SmallCraftProductionCatalogs.products().findProduct(productId);
        if (product == null || count <= 0 || count > 4096) throw new IllegalArgumentException("Invalid product purchase bill");
        try {
            return BigDecimal.valueOf(product.unitMassKg()).multiply(BigDecimal.valueOf(count))
                    .multiply(BigDecimal.valueOf(ShipyardRepairServicePayment.MATERIAL_MILLI_CREDITS_PER_KG))
                    .setScale(0, RoundingMode.CEILING).longValueExact();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Product purchase exceeds currency bounds", overflow);
        }
    }
}
