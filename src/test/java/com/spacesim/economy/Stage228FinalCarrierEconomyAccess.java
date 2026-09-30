package com.spacesim.economy;

/**
 * Test-only package bridge used by the cross-package M22.8N integration corpus.
 *
 * <p>Production storage mutation remains package-scoped. The final acceptance fixture must seed
 * exact finite Stage-18 inputs without widening the production API solely for a test.</p>
 */
public final class Stage228FinalCarrierEconomyAccess {
    private Stage228FinalCarrierEconomyAccess() {
        throw new AssertionError("test utility");
    }

    /** Seeds finite commodity stock through the existing Stage-18 package authority. */
    public static void addCommodity(
            Stage18StationStorage storage,
            String commodityId,
            double massKg) {
        storage.addCommodity(commodityId, massKg);
    }

    /** Seeds finite manufactured-product stock through the existing Stage-18 package authority. */
    public static void addProduct(
            Stage18StationStorage storage,
            String productId,
            int count) {
        storage.addProduct(productId, count);
    }
}
