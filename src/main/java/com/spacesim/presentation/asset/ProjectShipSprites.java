package com.spacesim.presentation.asset;

import java.util.List;

/** Production-facing presentation specifications for project ship art. */
public final class ProjectShipSprites {
    /** Presentation asset ID for the first heavy corvette visual. */
    public static final String WHITE_HEAVY_CORVETTE_01_ID = "ship.heavy_corvette.white_01";

    /** Base texture expected by the Stage-8.5 real-art validation. */
    public static final String WHITE_HEAVY_CORVETTE_01_BASE =
            "assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_base.png";

    /** Emissive mask expected beside the base texture. */
    public static final String WHITE_HEAVY_CORVETTE_01_EMISSIVE =
            "assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_emissive.png";

    /** Damage overlay expected beside the base texture. */
    public static final String WHITE_HEAVY_CORVETTE_01_DAMAGE =
            "assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_damage.png";

    /** Ship-specific idle exhaust texture expected beside the base texture. */
    public static final String WHITE_HEAVY_CORVETTE_01_ENGINE_IDLE =
            "assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_engine_idle.png";

    /** Ship-specific full-thrust exhaust texture expected beside the base texture. */
    public static final String WHITE_HEAVY_CORVETTE_01_ENGINE_THRUST =
            "assets/ships/heavy_corvette/heavy_corvette_white_01/heavy_corvette_white_01_engine_thrust.png";

    private ProjectShipSprites() {
    }

    /**
     * Returns the complete production-like resource pack for the first heavy corvette.
     *
     * @return immutable visual asset-pack contract
     */
    public static ShipVisualAssetSet whiteHeavyCorvette01Assets() {
        return new ShipVisualAssetSet(
                WHITE_HEAVY_CORVETTE_01_BASE,
                WHITE_HEAVY_CORVETTE_01_EMISSIVE,
                WHITE_HEAVY_CORVETTE_01_DAMAGE,
                WHITE_HEAVY_CORVETTE_01_ENGINE_IDLE,
                WHITE_HEAVY_CORVETTE_01_ENGINE_THRUST);
    }

    /**
     * Returns the initial production-like heavy-corvette visual contract.
     *
     * <p>The source art is authored nose-left / exhaust-right, while the runtime presentation
     * convention is forward-right. {@link SpriteOrientationTransform} therefore mirrors the sprite,
     * hardpoint positions and visual directions as one transform. Hardpoint coordinates retain the
     * authored-source convention with normalized origin at bottom-left.</p>
     *
     * @return immutable heavy-corvette presentation specification
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
                SourceFacing.LEFT,
                List.of(
                        new VisualHardpoint("engine_main_top", VisualHardpointType.ENGINE, 0.855f, 0.674f, 0f),
                        new VisualHardpoint("engine_main_mid", VisualHardpointType.ENGINE, 0.866f, 0.500f, 0f),
                        new VisualHardpoint("engine_main_bottom", VisualHardpointType.ENGINE, 0.855f, 0.326f, 0f),
                        new VisualHardpoint("weapon_nose_primary", VisualHardpointType.WEAPON, 0.055f, 0.500f, 180f),
                        new VisualHardpoint("weapon_forward_upper", VisualHardpointType.WEAPON, 0.165f, 0.620f, 180f),
                        new VisualHardpoint("weapon_forward_lower", VisualHardpointType.WEAPON, 0.165f, 0.380f, 180f),
                        new VisualHardpoint("weapon_mid_upper", VisualHardpointType.WEAPON, 0.440f, 0.720f, 180f),
                        new VisualHardpoint("weapon_mid_lower", VisualHardpointType.WEAPON, 0.440f, 0.280f, 180f),
                        new VisualHardpoint("utility_center", VisualHardpointType.UTILITY, 0.560f, 0.500f, 180f)));
    }
}
