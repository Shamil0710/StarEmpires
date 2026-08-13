package com.spacesim.world;

/** Brings one local system simulation to an exact jump transition tick. */
@FunctionalInterface
interface JumpTickCoordinator {
    void reach(StarSystemId systemId, long targetTick);
}
