package com.spacesim.world;

/** Explicit fate of inventory resources owned by a destroyed entity. */
public enum ResourceDestructionFate {
    /** Inventory is physically destroyed and recorded as resource sinks. */
    DESTROY,
    /** Inventory is transferred into a new persistent salvage entity at the destruction position. */
    SALVAGE,
    /** Inventory is atomically transferred into an explicitly supplied local recipient entity. */
    TRANSFER_TO_ENTITY
}
