package com.spacesim.trade;

import java.util.Objects;

/**
 * Pure hard procurement guard for structurally critical faction imports.
 *
 * <p>The policy does not create cargo, change prices, move money, alter legal market access or
 * manufacture historical trade shares. It only decides whether one already-discovered physical
 * foreign supplier is compatible with the faction's current structural concentration ceiling for a
 * critical commodity. Ordinary market access, tariffs, route costs and transaction settlement remain
 * authoritative elsewhere.</p>
 */
@FunctionalInterface
public interface CriticalImportLimitPolicy {
    /**
     * Assesses one physical supplier for autonomous procurement of one commodity.
     *
     * @param fleet immutable fleet planning profile
     * @param supplierFactionId runtime faction ID of the physical supplier, or {@code -1}
     * @param itemId runtime commodity ID
     * @return bounded import-limit assessment
     */
    Assessment assess(FleetTradeProfile fleet, int supplierFactionId, int itemId);

    /** @return stateless policy that never restricts ordinary procurement */
    static CriticalImportLimitPolicy none() {
        return (fleet, supplierFactionId, itemId) -> Assessment.inactive();
    }

    /**
     * One explainable hard-import assessment.
     *
     * @param active whether a hard concentration limit applies to this commodity and supplier
     * @param supplierShareBasisPoints measured structural foreign-supply concentration, 0..10000
     * @param maximumSupplierShareBasisPoints maximum concentration accepted by current doctrine, 0..10000
     */
    record Assessment(
            boolean active,
            int supplierShareBasisPoints,
            int maximumSupplierShareBasisPoints) {

        /**
         * Validates one immutable assessment.
         *
         * @param active whether the hard import limit is active
         * @param supplierShareBasisPoints measured supplier concentration
         * @param maximumSupplierShareBasisPoints current doctrine-derived ceiling
         */
        public Assessment {
            requireBasisPoints(supplierShareBasisPoints, "supplierShareBasisPoints");
            requireBasisPoints(maximumSupplierShareBasisPoints, "maximumSupplierShareBasisPoints");
        }

        /** @return whether this already-existing supplier remains authorized by the hard policy */
        public boolean authorized() {
            return !active || supplierShareBasisPoints <= maximumSupplierShareBasisPoints;
        }

        /** @return canonical unrestricted assessment */
        public static Assessment inactive() {
            return new Assessment(false, 0, 10_000);
        }

        /**
         * Validates a policy result returned by an implementation.
         *
         * @param assessment policy result
         * @return same non-null assessment
         */
        public static Assessment require(Assessment assessment) {
            return Objects.requireNonNull(assessment, "Critical import assessment not set");
        }

        private static void requireBasisPoints(int value, String label) {
            if (value < 0 || value > 10_000) {
                throw new IllegalArgumentException(label + " must be in range 0..10000");
            }
        }
    }
}
