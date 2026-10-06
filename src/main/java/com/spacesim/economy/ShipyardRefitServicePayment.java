package com.spacesim.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Held payment for actual refit work and pristine equipment supplied by a civilian operator.
 * Used equipment remains player property and is not charged as a new product.
 * @param sellerFactionId actual operator
 * @param reservedMilliCredits money held by the refit order
 */
public record ShipyardRefitServicePayment(String sellerFactionId, long reservedMilliCredits) {
    /**
     * Validates a stable operator and positive held balance.
     * @param sellerFactionId actual seller identity
     * @param reservedMilliCredits positive held money
     */
    public ShipyardRefitServicePayment {
        sellerFactionId = com.spacesim.world.WorldFactionIdentityState.normalizeStableId(sellerFactionId);
        if (reservedMilliCredits <= 0) throw new IllegalArgumentException("Invalid refit service payment");
    }

    /**
     * Quotes work and actual new-product mass using the published civilian service tariff.
     * Empty fresh input is valid for removal or installation of existing used equipment.
     * @param requiredWorkSeconds exact authored work
     * @param freshProducts actual new products provided by the operator
     * @return total held milli-credits, rounded upward once
     */
    public static long quote(double requiredWorkSeconds, Map<String, Integer> freshProducts) {
        if (!Double.isFinite(requiredWorkSeconds) || requiredWorkSeconds <= 0
                || freshProducts == null || freshProducts.size() > 4096)
            throw new IllegalArgumentException("Invalid paid refit work or product bill");
        var amount = BigDecimal.valueOf(requiredWorkSeconds)
                .multiply(BigDecimal.valueOf(ShipyardRepairServicePayment.WORK_MILLI_CREDITS_PER_SECOND));
        for (var entry : freshProducts.entrySet()) {
            var product = entry.getKey() == null ? null : Products.CATALOG.findProduct(entry.getKey());
            if (product == null || entry.getValue() == null || entry.getValue() <= 0 || entry.getValue() > 4096)
                throw new IllegalArgumentException("Unknown or invalid paid refit product");
            amount = amount.add(BigDecimal.valueOf(product.unitMassKg()).multiply(BigDecimal.valueOf(entry.getValue()))
                    .multiply(BigDecimal.valueOf(ShipyardRepairServicePayment.MATERIAL_MILLI_CREDITS_PER_KG)));
        }
        try { return amount.setScale(0, RoundingMode.CEILING).longValueExact(); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException("Refit price exceeds currency bounds", overflow); }
    }

    private static final class Products {
        private static final com.spacesim.content.Stage18ManufacturingProductRegistry CATALOG =
                com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts();
    }
}
