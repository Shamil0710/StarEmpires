package com.spacesim.world;

/**
 * Persistent economic settlement mode for a construction project.
 *
 * <p>The value describes who settles project funding/refunds; it deliberately does not encode a
 * human-player identity into {@link WorldState}. Player ownership remains in the playable envelope.
 * Legal/faction affiliation is stored separately on {@link ConstructionProjectState}.</p>
 */
public enum ConstructionSettlementKind {
    /** Existing faction-owned project funded and settled against that faction treasury. */
    FACTION_TREASURY,
    /** Project economically owned by an external actor such as the player. */
    EXTERNAL_OWNER
}
