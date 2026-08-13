package com.spacesim.presentation.asset;

import java.util.List;

/**
 * Production-facing presentation specifications for project ship art.
 *
 * <p>These definitions describe how authored textures are placed and decorated. They are not
 * gameplay archetypes: combat statistics, persistence identity and economic semantics remain in
 * their authoritative systems.</p>
 */
public final class ProjectShipSprites {
    /** Presentation asset ID for the first heavy corvette visual. */
    public static final String WHITE_HEAVY_CORVETTE_01_ID =
            "ship.heavy_corvette.white_01";

    /** Base texture expected by the Stage-8.5 real-art validation. */
    public static final String WHITE_HEAVY_CORVETTE_01_BASE =
            "assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_base.png";

    /** Optional emissive mask expected beside the base texture. */
    public static final String WHITE_HEAVY_CORVETTE_01_EMISSIVE =
            "assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_emissive.png";

    private ProjectShipSprites() {
    }

    /**
     * Returns the initial production-like heavy-corvette visual contract.
     *
     * <p>The source art is authored nose-left / exhaust-right. Hardpoint coordinates use the
     * package convention: normalized origin at the sprite bottom-left. Direction {@code 0} points
     * right and {@code 180} points left. The footprint is deliberately narrower than the visible
     * armor silhouette and engine exhaust.</p>
     *
     * @return immutable-by-construction heavy-corvette sprite specification
     */
    public static ShipSpriteSpec whiteHeavyCorvette01() {
        return new ShipSpriteSpec(
                WHITE_HEAVY_CORVETTE_01_ID,
                WHITE_HEAVY_CORVETTE_01_BASE,
                WHITE_HEAVY_CORVETTE_01_EMISSIVE,
                120f,
                72f,
                0.50f,
                0.50f,
                86.4f,
                41.8f,
                List.of(
                        new VisualHardpoint(
                                "engine_main_top",
                                VisualHardpointType.ENGINE,
                                0.855f,
                                0.674f,
                                0f),
                        new VisualHardpoint(
                                "engine_main_mid",
                                VisualHardpointType.ENGINE,
                                0.866f,
                                0.500f,
                                0f),
                        new VisualHardpoint(
                                "engine_main_bottom",
                                VisualHardpointType.ENGINE,
                                0.855f,
                                0.326f,
                                0f),
                        new VisualHardpoint(
                                "weapon_nose_primary",
                                VisualHardpointType.WEAPON,
                                0.055f,
                                0.500f,
                                180f),
                        new VisualHardpoint(
                                "weapon_forward_upper",
                                VisualHardpointType.WEAPON,
                                0.165f,
                                0.620f,
                                180f),
                        new VisualHardpoint(
                                "weapon_forward_lower",
                                VisualHardpointType.WEAPON,
                                0.165f,
                                0.380f,
                                180f),
                        new VisualHardpoint(
                                "weapon_mid_upper",
                                VisualHardpointType.WEAPON,
                                0.440f,
                                0.720f,
                                180f),
                        new VisualHardpoint(
                                "weapon_mid_lower",
                                VisualHardpointType.WEAPON,
                                0.440f,
                                0.280f,
                                180f),
                        new VisualHardpoint(
                                "utility_center",
                                VisualHardpointType.UTILITY,
                                0.560f,
                                0.500f,
                                180f)));
    }
}
