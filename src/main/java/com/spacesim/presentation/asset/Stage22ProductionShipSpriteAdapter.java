package com.spacesim.presentation.asset;

import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.AtlasRegion;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ResolvedSprite;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ScaleAuthority;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.VisualRole;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.ResolvedVisual;

import java.util.List;
import java.util.Objects;

/**
 * Presentation-only compatibility adapter from M22.7 production visuals to the current generated-world
 * sprite renderer contract.
 *
 * <p>The one-pixel source region is an explicit sentinel. The texture renderer recognizes Stage-22
 * production paths and replaces this sentinel with the real full-texture alpha-visible region after
 * loading the image. No image dimensions, pixels or presentation metadata feed simulation authority.</p>
 */
public final class Stage22ProductionShipSpriteAdapter {
    /** Versioned handoff contract between the Stage-22 resolver and the existing generated-world UI. */
    public static final String CURRENT_VERSION = "stage22_7.production-ship-sprite-adapter.v1";

    private Stage22ProductionShipSpriteAdapter() {
        throw new AssertionError("utility class");
    }

    /**
     * Adapts one validated production visual into the legacy UI sprite carrier without introducing a
     * legacy-art fallback.
     *
     * @param resolved exact validated Stage-22 production visual
     * @return exact-scale renderer-compatible sprite metadata
     */
    public static ResolvedSprite adapt(ResolvedVisual resolved) {
        ResolvedVisual visual = Objects.requireNonNull(resolved, "resolved");
        if (!isProductionPath(visual.assetRef())) {
            throw new IllegalArgumentException(
                    "Stage-22 production adapter refuses non-production asset: " + visual.assetRef());
        }
        SpriteBinding binding = new SpriteBinding(
                "stage22.production:" + visual.key().visualBindingId(),
                visualRole(visual.key().roleId()),
                visual.assetRef(),
                new AtlasRegion(0, 0, 1, 1),
                0.5f,
                0.5f,
                SourceFacing.RIGHT,
                visual.worldLengthM(),
                visual.worldWidthM(),
                List.of());
        return new ResolvedSprite(
                binding,
                visual.worldLengthM(),
                visual.worldWidthM(),
                ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                CURRENT_VERSION + '#' + visual.key().fitFingerprint());
    }

    /** @return whether a classpath path is inside an accepted Stage-22 ship production tree. */
    public static boolean isProductionPath(String path) {
        if (path == null) {
            return false;
        }
        String value = path.strip();
        return value.startsWith("assets/ships/")
                && value.contains("/production/")
                && value.endsWith("_base.png");
    }

    private static VisualRole visualRole(String roleId) {
        String role = Objects.requireNonNull(roleId, "roleId");
        if (role.startsWith("role.support.")) {
            return VisualRole.CARGO_TRANSPORT_SHIP;
        }
        if (role.endsWith(".corvette") || role.endsWith(".frigate")) {
            return VisualRole.LIGHT_COMBAT_ESCORT_SHIP;
        }
        if (role.startsWith("role.military.")) {
            return VisualRole.MEDIUM_COMBAT_SHIP;
        }
        throw new IllegalArgumentException("Unsupported Stage-22 ship visual role: " + role);
    }
}
