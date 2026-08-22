package com.spacesim.world;

/** Explainable reasons a Stage-21B strategic goal cannot currently progress. */
public enum StrategicGoalBlocker {
    /** Available treasury planning capacity is insufficient. */
    TREASURY_CAPACITY,
    /** Available logistics planning capacity is insufficient. */
    LOGISTICS_CAPACITY,
    /** Available construction planning capacity is insufficient. */
    CONSTRUCTION_CAPACITY,
    /** Available fleet/readiness planning capacity is insufficient. */
    READINESS_CAPACITY,
    /** Actor-bounded feasibility is below the acceptance threshold. */
    FEASIBILITY,
    /** Delivered intelligence confidence or coverage is insufficient. */
    INSUFFICIENT_INTELLIGENCE,
    /** Current diplomatic constraints prevent lawful progress. */
    DIPLOMATIC_CONSTRAINT,
    /** The target is temporarily unavailable through actor-known information. */
    TARGET_UNAVAILABLE
}
