package com.spacesim.world;

/**
 * Stage-21D observation of prerequisites that are not yet represented by a dedicated ship-local
 * persistent component.
 *
 * <p>The required crew count still comes from the Stage-17.5 engineering catalog. The available
 * crew and current Stage-18 supply access are explicit observed inputs and therefore fail closed
 * when absent instead of creating a second crew or logistics authority.</p>
 *
 * @param availableCrew currently available trained crew members
 * @param supplyAccessBps observed physical supply/service access, 0..10000
 */
public record FleetOperationalAvailability(int availableCrew, int supplyAccessBps) {
    /**
     * Validates the bounded external readiness observations.
     *
     * @param availableCrew currently available trained crew members; never negative
     * @param supplyAccessBps observed physical supply/service access in basis points, 0..10000
     */
    public FleetOperationalAvailability {
        if (availableCrew < 0) {
            throw new IllegalArgumentException("availableCrew must be non-negative");
        }
        if (supplyAccessBps < 0 || supplyAccessBps > FleetReadinessState.FULL) {
            throw new IllegalArgumentException("supplyAccessBps must be in 0..10000");
        }
    }

    /**
     * Creates the fail-closed value used when no operational availability observation exists.
     *
     * @return zero crew and zero supply-access observation
     */
    public static FleetOperationalAvailability unavailable() {
        return new FleetOperationalAvailability(0, 0);
    }
}
