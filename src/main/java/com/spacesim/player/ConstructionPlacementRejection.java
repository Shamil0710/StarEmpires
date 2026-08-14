package com.spacesim.player;

/** Stable rejection reasons returned by the authoritative construction placement boundary. */
public enum ConstructionPlacementRejection {
    /** Placement is valid under the current authoritative policy. */
    NONE,
    /** Coordinates are not finite. */
    NON_FINITE_COORDINATES,
    /** Coordinates leave the bounded local-system construction area. */
    OUTSIDE_LOCAL_BOUNDS,
    /** Placement blocks the canonical inter-system arrival area. */
    JUMP_ARRIVAL_EXCLUSION,
    /** Placement is too close to an existing station or construction site. */
    STATION_CLEARANCE,
    /** Placement is too close to a finite resource object. */
    RESOURCE_CLEARANCE,
    /** The player lacks current strategic access to build in the controlling faction's territory. */
    TERRITORY_ACCESS_DENIED
}
