package com.spacesim.world;

/**
 * Derived Stage-21D readiness of one ordinary physical fleet.
 *
 * <p>Every component is expressed in basis points in the inclusive range 0..10000. This is a
 * read-only decision aid: it never replaces Stage-17.5 damage/engineering state or Stage-18
 * resource authority. Overall readiness is deliberately the minimum component so a missing
 * physical prerequisite cannot be hidden by an unrelated strong component.</p>
 */
public record FleetReadinessState(
        int structuralBps,
        int ammunitionBps,
        int propellantBps,
        int crewBps,
        int sensorsBps,
        int maintenanceBps,
        int supplyAccessBps) {

    public static final int FULL = 10_000;

    public FleetReadinessState {
        requireBps(structuralBps, "structuralBps");
        requireBps(ammunitionBps, "ammunitionBps");
        requireBps(propellantBps, "propellantBps");
        requireBps(crewBps, "crewBps");
        requireBps(sensorsBps, "sensorsBps");
        requireBps(maintenanceBps, "maintenanceBps");
        requireBps(supplyAccessBps, "supplyAccessBps");
    }

    /** @return conservative readiness bounded by the weakest physical prerequisite. */
    public int overallBps() {
        return Math.min(structuralBps,
                Math.min(ammunitionBps,
                        Math.min(propellantBps,
                                Math.min(crewBps,
                                        Math.min(sensorsBps,
                                                Math.min(maintenanceBps, supplyAccessBps))))));
    }

    /** @return true when all prerequisites meet the supplied mission threshold. */
    public boolean missionCapable(int thresholdBps) {
        requireBps(thresholdBps, "thresholdBps");
        return overallBps() >= thresholdBps;
    }

    public static FleetReadinessState unavailable() {
        return new FleetReadinessState(0, 0, 0, 0, 0, 0, 0);
    }

    private static void requireBps(int value, String name) {
        if (value < 0 || value > FULL) {
            throw new IllegalArgumentException(name + " must be in 0..10000: " + value);
        }
    }
}
