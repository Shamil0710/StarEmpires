package com.spacesim.trade;

import java.util.Objects;

/**
 * Pure strategic preference seam for choosing a real edge-disjoint jump route.
 *
 * <p>The policy never creates topology, changes jump timing, transfers money or alters market state.
 * It only states whether resilience currently justifies using a physical redundant route and the
 * maximum real expected-profit sacrifice accepted for that choice.</p>
 */
@FunctionalInterface
public interface RouteRedundancyPolicy {
    /**
     * Assesses redundancy preference for one fleet commodity.
     *
     * @param fleet immutable planning profile
     * @param itemId runtime commodity ID
     * @return bounded strategic assessment
     */
    Assessment assess(FleetTradeProfile fleet, int itemId);

    /** @return stateless policy that never changes ordinary shortest-route choice */
    static RouteRedundancyPolicy none() {
        return (fleet, itemId) -> Assessment.inactive();
    }

    /**
     * One explainable route-redundancy assessment.
     *
     * @param active whether a physical redundant route is currently desired
     * @param acceptableProfitSacrificeMilliCredits maximum real expected-profit sacrifice accepted
     */
    record Assessment(boolean active, long acceptableProfitSacrificeMilliCredits) {
        /**
         * Validates one immutable route-redundancy assessment.
         *
         * @param active whether route redundancy is active
         * @param acceptableProfitSacrificeMilliCredits maximum accepted expected-profit sacrifice
         */
        public Assessment {
            if (acceptableProfitSacrificeMilliCredits < 0L) {
                throw new IllegalArgumentException("Accepted route-redundancy profit sacrifice cannot be negative");
            }
        }

        /** @return canonical inactive assessment */
        public static Assessment inactive() {
            return new Assessment(false, 0L);
        }

        /**
         * Validates a policy result returned by an implementation.
         *
         * @param assessment policy result
         * @return same non-null assessment
         */
        public static Assessment require(Assessment assessment) {
            return Objects.requireNonNull(assessment, "Route redundancy assessment not set");
        }
    }
}
