package com.spacesim.world;

/**
 * Derived Stage-21D readiness of one ordinary physical fleet.
 *
 * <p>Every component is expressed in basis points in the inclusive range 0..10000. This is a
 * read-only decision aid: it never replaces Stage-17.5 damage/engineering state or Stage-18
 * resource authority. Overall readiness is deliberately the minimum component so a missing
 * physical prerequisite cannot be hidden by an unrelated strong component.</p>
 *
 * @param structuralBps conservative structural integrity readiness in basis points
 * @param ammunitionBps ammunition-load readiness in basis points
 * @param propellantBps reaction-mass readiness in basis points
 * @param crewBps observed available-crew readiness in basis points
 * @param sensorsBps fitted sensor/fire-control readiness in basis points
 * @param maintenanceBps maintenance/service-age readiness in basis points
 * @param supplyAccessBps observed access to existing service/logistics capability in basis points
 */
public record FleetReadinessState(
        int structuralBps,
        int ammunitionBps,
        int propellantBps,
        int crewBps,
        int sensorsBps,
        int maintenanceBps,
        int supplyAccessBps) {

    /** Full readiness expressed in basis points. */
    public static final int FULL = 10_000;

    /**
     * Validates all readiness dimensions as inclusive basis-point values.
     *
     * @param structuralBps structural readiness in basis points
     * @param ammunitionBps ammunition readiness in basis points
     * @param propellantBps reaction-mass readiness in basis points
     * @param crewBps crew readiness in basis points
     * @param sensorsBps sensor readiness in basis points
     * @param maintenanceBps maintenance readiness in basis points
     * @param supplyAccessBps service/logistics access readiness in basis points
     */
    public FleetReadinessState {
        requireBps(structuralBps, "structuralBps");
        requireBps(ammunitionBps, "ammunitionBps");
        requireBps(propellantBps, "propellantBps");
        requireBps(crewBps, "crewBps");
        requireBps(sensorsBps, "sensorsBps");
        requireBps(maintenanceBps, "maintenanceBps");
        requireBps(supplyAccessBps, "supplyAccessBps");
    }

    /**
     * Returns conservative readiness bounded by the weakest physical prerequisite.
     *
     * @return minimum readiness component in basis points
     */
    public int overallBps() {
        return Math.min(structuralBps,
                Math.min(ammunitionBps,
                        Math.min(propellantBps,
                                Math.min(crewBps,
                                        Math.min(sensorsBps,
                                                Math.min(maintenanceBps, supplyAccessBps))))));
    }

    /**
     * Tests whether every prerequisite, via the conservative minimum, meets a mission threshold.
     *
     * @param thresholdBps required mission readiness in basis points, 0..10000
     * @return {@code true} when conservative overall readiness meets or exceeds the threshold
     */
    public boolean missionCapable(int thresholdBps) {
        requireBps(thresholdBps, "thresholdBps");
        return overallBps() >= thresholdBps;
    }

    /**
     * Creates the fail-closed readiness projection used for unknown or unsupported physical state.
     *
     * @return readiness with every prerequisite set to zero
     */
    public static FleetReadinessState unavailable() {
        return new FleetReadinessState(0, 0, 0, 0, 0, 0, 0);
    }

    private static void requireBps(int value, String name) {
        if (value < 0 || value > FULL) {
            throw new IllegalArgumentException(name + " must be in 0..10000: " + value);
        }
    }
}
