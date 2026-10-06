package com.spacesim.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * Money held for one civilian repair; the campaign owns debit, refund and settlement.
 * @param sellerFactionId actual original civilian station operator
 * @param reservedMilliCredits positive money held by this order
 */
public record ShipyardRepairServicePayment(String sellerFactionId, long reservedMilliCredits) {
    /** Published civilian tariff: one credit per engineering work-second. */
    public static final long WORK_MILLI_CREDITS_PER_SECOND = 1_000L;
    /** Published composite material tariff: fifty credits per physical input kilogram. */
    public static final long MATERIAL_MILLI_CREDITS_PER_KG = 50_000L;

    /**
     * Validates a real seller reference and positive reserved money.
     * @param sellerFactionId actual station operator faction
     * @param reservedMilliCredits money removed from the personal wallet and held by its repair order
     */
    public ShipyardRepairServicePayment {
        Objects.requireNonNull(sellerFactionId);
        sellerFactionId = com.spacesim.world.WorldFactionIdentityState.normalizeStableId(sellerFactionId);
        if (sellerFactionId.isBlank() || sellerFactionId.length() > 512
                || sellerFactionId.contains("\n") || sellerFactionId.contains("\r") || reservedMilliCredits <= 0)
            throw new IllegalArgumentException("Invalid paid repair reservation");
    }

    /**
     * Quotes the disclosed tariff against exact authored work and actual reserved material.
     * Rounds once upward to the smallest currency unit; overflowing or invalid quotes are rejected.
     * @param workSeconds required positive engineering work
     * @param materialsKg exact physical repair bill
     * @return full reservation in milli-credits
     */
    public static long quote(double workSeconds, Map<String, Double> materialsKg) {
        if (!Double.isFinite(workSeconds) || workSeconds <= 0 || materialsKg == null || materialsKg.isEmpty())
            throw new IllegalArgumentException("Missing physical paid repair requirements");
        BigDecimal amount = BigDecimal.valueOf(workSeconds).multiply(BigDecimal.valueOf(WORK_MILLI_CREDITS_PER_SECOND));
        for (var entry : materialsKg.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
                    || !Double.isFinite(entry.getValue()) || entry.getValue() <= 0)
                throw new IllegalArgumentException("Invalid paid repair input");
            amount = amount.add(BigDecimal.valueOf(entry.getValue()).multiply(BigDecimal.valueOf(MATERIAL_MILLI_CREDITS_PER_KG)));
        }
        try { return amount.setScale(0, RoundingMode.CEILING).longValueExact(); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException("Paid repair quote exceeds currency bounds", overflow); }
    }
}
