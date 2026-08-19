package com.spacesim.ui;

/**
 * Presentation-only schematic ship role used by the Stage-19J tactical viewer.
 *
 * <p>Roles describe authored fit/doctrine identity for visual differentiation only. They must never
 * be consumed by combat, AI, movement, sensors, damage, weapon or engineering calculations.</p>
 */
public enum ShipVisualRole {
    /** Axial kinetic-line fit with a narrow gun-oriented silhouette. */ KINETIC,
    /** Guided strike fit with broad launcher/pod visual language. */ MISSILE,
    /** High-mobility directed-energy fit with a slender pronged silhouette. */ BEAM,
    /** Defensive shield/EW fit with compact body and lateral defensive nodes. */ DEFENSIVE_EW,
    /** Mixed general-purpose fit with a neutral multipurpose silhouette. */ BALANCED,
    /** Legacy or unavailable role metadata; never inferred from entity identity. */ UNCLASSIFIED
}
