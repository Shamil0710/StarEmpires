package com.spacesim.player;

/** Persistent categories of player-observed route danger intelligence. */
public enum PlayerThreatIntelKind {
    /** Danger observed inside one StarSystem. */
    SYSTEM,
    /** Danger associated with traversing one topology edge/corridor. */
    LINK
}
