package com.spacesim.world;

/** Active phase of a scheduled inter-system fleet jump. */
public enum FleetJumpPhase {
    /** Fleet remains local while preparing to reach the jump boundary. */
    MOVING_TO_JUMP,
    /** Fleet remains local while the jump transition is committed. */
    JUMP_PENDING,
    /** Fleet is detached from every local simulation session. */
    IN_TRANSIT,
    /** Fleet has materialized in the destination and is completing arrival. */
    ARRIVING
}
