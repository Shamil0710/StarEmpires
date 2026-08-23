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
    public FleetOperationalAvailability {
        if (availableCrew < 0) {
            throw new IllegalArgumentException("availableCrew must be non-negative");
        }
        if (supplyAccessBps < 0 || supplyAccessBps > FleetReadinessState.FULL) {
            throw new IllegalArgumentException("supplyAccessBps must be in 0..10000");
        }
    }

    /** Missing observation deliberately means unavailable, never implicit full readiness. */
    public static FleetOperationalAvailability unavailable() {
        return new FleetOperationalAvailability(0, 0);
    }
}
