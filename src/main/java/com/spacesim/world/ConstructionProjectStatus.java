package com.spacesim.world;

/** Persistent deterministic state machine of a construction project. */
public enum ConstructionProjectStatus {
    /** Project exists but has not yet received its minimum working budget. */
    PLANNED,
    /** Minimum project funding is present; the next deterministic update exposes active demand. */
    FUNDED,
    /** Physical construction-site market is waiting for all required materials. */
    AWAITING_MATERIALS,
    /** All materials are present and the build timer is advancing in target-system ticks. */
    BUILDING,
    /** Target station was created and construction resources were consumed. */
    COMPLETED,
    /** Project was cancelled before any construction material was delivered. */
    CANCELLED,
    /** Physical construction site was destroyed before completion. */
    FAILED
}
