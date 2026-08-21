package com.spacesim.navigation;

/** Runtime state of a physical jump transit operation. */
public enum TravelState {
    PREPARING_JUMP,
    TRANSIT,
    COOLDOWN,
    ARRIVED,
    CANCELLED
}
