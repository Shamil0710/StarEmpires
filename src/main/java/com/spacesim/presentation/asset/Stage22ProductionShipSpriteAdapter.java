package com.spacesim.presentation.asset;

import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.AtlasRegion;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ResolvedSprite;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ScaleAuthority;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.VisualRole;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.ResolvedVisual;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.RuntimeVisualState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

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
    /** Versioned handoff contract between the Stage-22 resolver and existing compatibility clients. */
    public static final String CURRENT_VERSION = "stage22_7.production-ship-sprite-adapter.v3";

    private static final ShipEngineeringCatalog STAGE21_TACTICAL_ENGINEERING =
            Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();

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
        return adaptAtScale(
                visual,
                visual.worldLengthM(),
                visual.worldWidthM(),
                ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                CURRENT_VERSION + '#' + visual.key().fitFingerprint());
    }

    /**
     * Upgrades a governed core-faction compatibility projection to the corresponding Stage-22
     * production artwork while preserving the current runtime's physical scale authority.
     *
     * <p>Current generated-world compatibility mappings are intentionally narrow: cargo transport maps
     * to the authored freight family and the provisional Stage-21 medium combat hull maps to the
     * authored destroyer family. No mapping is inferred for world-generated compatibility factions such
     * as faction.alpha or faction.beta, and unsupported roles retain their supplied projection.</p>
     *
     * @param stableEntityId persistent local/fleet identity used by the production binding key
     * @param stableFactionId authoritative stable owning faction
     * @param legacy current runtime physical projection
     * @param runtimeState current presentation state
     * @return production artwork at unchanged simulation-authoritative world dimensions, or legacy when not governed
     */
    public static ResolvedSprite upgradeCoreProjection(
            String stableEntityId,
            String stableFactionId,
            ResolvedSprite legacy,
            RuntimeVisualState runtimeState) {
        ResolvedSprite physical = Objects.requireNonNull(legacy, "legacy");
        if (!isCoreProductionFaction(stableFactionId)) {
            return physical;
        }
        String roleId = switch (physical.binding().role()) {
            case CARGO_TRANSPORT_SHIP -> "role.support.freight";
            case MEDIUM_COMBAT_SHIP -> "role.military.destroyer";
            default -> null;
        };
        if (roleId == null) {
            return physical;
        }
        ResolvedVisual visual = Stage22ProductionShipVisualResolver.resolveRole(
                stableEntityId,
                stableFactionId,
                roleId,
                Objects.requireNonNull(runtimeState, "runtimeState"));
        return adaptAtScale(
                visual,
                physical.worldLengthM(),
                physical.worldWidthM(),
                physical.scaleAuthority(),
                physical.authorityId() + "|visual=" + visual.key().visualBindingId()
                        + ':' + visual.key().fitFingerprint());
    }

    /**
     * Upgrades one exact Stage-21 strategic tactical destroyer fit to faction-authored Stage-22 art.
     *
     * <p>This path deliberately ignores the old tactical schematic role. The supplied installed fit must
     * exactly equal one of the five registered Stage-21 strategic variants from the accepted engineering
     * catalogue. For a governed core faction, missing or altered fit identity fails closed rather than
     * selecting artwork from ALPHA/BETA side, doctrine name, hull-name substring or visual role.</p>
     *
     * @param stableEntityId stable tactical/campaign combatant identity
     * @param stableFactionId authoritative stable faction identity, or a non-core identity
     * @param installedFit exact detached engineering fit carried through strategic-to-tactical import
     * @param legacy current tactical sprite projection
     * @param runtimeState current presentation state
     * @return faction destroyer artwork at the Stage-21 physical hull envelope, or legacy for non-core factions
     */
    public static ResolvedSprite upgradeStage21TacticalProjection(
            String stableEntityId,
            String stableFactionId,
            InstalledFit installedFit,
            ResolvedSprite legacy,
            RuntimeVisualState runtimeState) {
        ResolvedSprite physical = Objects.requireNonNull(legacy, "legacy");
        if (!isCoreProductionFaction(stableFactionId)) {
            return physical;
        }
        InstalledFit fit = Objects.requireNonNull(installedFit,
                "core tactical production visual requires exact installedFit");
        DemonstratorFitDefinition strategic = STAGE21_TACTICAL_ENGINEERING.getDemonstratorFits().stream()
                .filter(Stage175ICombatTestContentPack::isStage21StrategicFit)
                .filter(candidate -> InstalledFit.fromDemonstrator(candidate).equals(fit))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "core tactical production visual requires an exact Stage-21 strategic fit"));
        HullDefinition hull = STAGE21_TACTICAL_ENGINEERING.findHull(strategic.hullId());
        if (hull == null) {
            throw new IllegalStateException("Stage-21 strategic fit references absent hull: " + strategic.hullId());
        }
        ResolvedVisual visual = Stage22ProductionShipVisualResolver.resolveRole(
                stableEntityId,
                stableFactionId,
                "role.military.destroyer",
                Objects.requireNonNull(runtimeState, "runtimeState"));
        return adaptAtScale(
                visual,
                hull.boundingDimensionsM().lengthM(),
                hull.boundingDimensionsM().widthM(),
                ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                STAGE21_TACTICAL_ENGINEERING.getFingerprint() + '#' + strategic.id()
                        + "|visual=" + visual.key().visualBindingId()
                        + ':' + visual.key().fitFingerprint());
    }

    /**
     * Backwards-compatible cargo-only entry point retained for callers that explicitly require freight.
     *
     * @param stableEntityId persistent local/fleet identity
     * @param stableFactionId authoritative stable owning faction
     * @param legacy current runtime physical projection
     * @param runtimeState current presentation state
     * @return cargo production artwork when governed; otherwise the supplied projection
     */
    public static ResolvedSprite upgradeCoreCargoProjection(
            String stableEntityId,
            String stableFactionId,
            ResolvedSprite legacy,
            RuntimeVisualState runtimeState) {
        ResolvedSprite physical = Objects.requireNonNull(legacy, "legacy");
        if (physical.binding().role() != VisualRole.CARGO_TRANSPORT_SHIP) {
            return physical;
        }
        return upgradeCoreProjection(stableEntityId, stableFactionId, physical, runtimeState);
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

    private static ResolvedSprite adaptAtScale(
            ResolvedVisual visual,
            double worldLengthM,
            double worldWidthM,
            ScaleAuthority scaleAuthority,
            String scaleAuthorityId) {
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
                worldLengthM,
                worldWidthM,
                Objects.requireNonNull(scaleAuthority, "scaleAuthority"),
                Objects.requireNonNull(scaleAuthorityId, "scaleAuthorityId"));
    }

    private static boolean isCoreProductionFaction(String factionId) {
        return Stage22EmpirePackageCatalog.STABLE_FACTION_ID.equals(factionId)
                || Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID.equals(factionId);
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
