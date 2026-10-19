package com.spacesim.presentation.asset;

/** Presentation role of a sprite-relative hardpoint. */
public enum VisualHardpointType {
    /** Engine or maneuvering exhaust origin. */
    ENGINE,
    /** Weapon muzzle or launcher origin. */
    WEAPON,
    /** Station docking port where a boarding arm attaches; the direction points out of the hull. */
    DOCKING,
    /** Reserved generic attachment point for future presentation features. */
    UTILITY
}
