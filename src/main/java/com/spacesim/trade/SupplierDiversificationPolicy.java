package com.spacesim.trade;

import java.util.Objects;

/**
 * Pure strategic preference seam for choosing between real supplier routes.
 *
 * <p>The policy never creates goods, changes prices, transfers money or mutates market state. It only
 * reports current supplier concentration plus the maximum real expected-profit sacrifice a faction is
 * willing to accept for diversification. The route planner still evaluates ordinary physical suppliers,
 * prices, tariffs, risk and travel time.</p>
 */
@FunctionalInterface
public interface SupplierDiversificationPolicy {
    /**
     * Assesses one real supplier candidate for a fleet and commodity.
     *
     * @param fleet immutable planning profile
     * @param supplierFactionId runtime faction ID of the physical supplier market, or {@code -1}
     * @param itemId runtime commodity ID
     * @return bounded strategic assessment
     */
    Assessment assess(FleetTradeProfile fleet, int supplierFactionId, int itemId);

    /** @return stateless policy that never changes ordinary economic route choice */
    static SupplierDiversificationPolicy none() {
        return (fleet, supplierFactionId, itemId) -> Assessment.inactive();
    }

    /**
     * One explainable supplier assessment.
     *
     * @param active whether diversification is currently recommended for this commodity
     * @param supplierShareBasisPoints measured supplier concentration, 0..10000
     * @param acceptableProfitSacrificeMilliCredits maximum real expected-profit sacrifice accepted for resilience
     */
    record Assessment(
            boolean active,
            int supplierShareBasisPoints,
            long acceptableProfitSacrificeMilliCredits) {

        /**
         * Validates one bounded immutable assessment.
         *
         * @param active whether diversification is active for the assessed commodity
         * @param supplierShareBasisPoints measured supplier concentration, 0..10000
         * @param acceptableProfitSacrificeMilliCredits maximum accepted expected-profit sacrifice
         */
        public Assessment {
            if (supplierShareBasisPoints < 0 || supplierShareBasisPoints > 10_000) {
                throw new IllegalArgumentException("Supplier share must be in range 0..10000 bps");
            }
            if (acceptableProfitSacrificeMilliCredits < 0L) {
                throw new IllegalArgumentException("Accepted profit sacrifice cannot be negative");
            }
        }

        /** @return canonical inactive assessment */
        public static Assessment inactive() {
            return new Assessment(false, 10_000, 0L);
        }

        /**
         * Validates a policy result returned by an implementation.
         *
         * @param assessment policy result
         * @return same non-null assessment
         */
        public static Assessment require(Assessment assessment) {
            return Objects.requireNonNull(assessment, "Supplier diversification assessment not set");
        }
    }
}
