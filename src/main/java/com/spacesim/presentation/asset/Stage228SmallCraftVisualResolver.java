package com.spacesim.presentation.asset;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection.DesignBinding;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * M22.8J presentation-only small-craft visual/marker resolver.
 *
 * <p>Stable production fit IDs select a marker family and faction palette. Exact hull dimensions
 * always come from the ordinary engineering catalog; the resolver cannot enlarge simulation/world
 * geometry. A tiny screen-space marker may be used by presentation code only when the exact sprite
 * would become sub-pixel at distant zoom. Unknown or unbound design IDs fail visibly instead of
 * silently borrowing another ship sprite.</p>
 */
public final class Stage228SmallCraftVisualResolver {
    /** Semantic version of the small-craft presentation binding. */
    public static final String VERSION = "m22.8j.small_craft_visual_binding.v1";
    /** Maximum presentation-only minimum marker footprint; never a world-size override. */
    public static final double MINIMUM_MARKER_PIXELS = 3d;

    private static final ShipEngineeringCatalog ENGINEERING =
            Stage22CorePairEngineeringCatalogLoader.loadDefault();
    private static final Map<String, VisualBinding> BINDINGS = loadBindings();

    private Stage228SmallCraftVisualResolver() {
        throw new AssertionError("utility class");
    }

    /**
     * Resolves one exact production small-craft fit to presentation metadata.
     *
     * @param stableEntityId persistent craft identity string used only for presentation keys
     * @param fitId exact authored production small-craft fit ID
     * @return exact-scale presentation binding
     */
    public static ResolvedMarker resolve(String stableEntityId, String fitId) {
        String entity = requireText(stableEntityId, "stableEntityId");
        String design = requireText(fitId, "fitId");
        VisualBinding binding = BINDINGS.get(design);
        if (binding == null) {
            throw new IllegalArgumentException(
                    "no M22.8J production small-craft visual binding for fit: " + design);
        }
        var fit = ENGINEERING.findDemonstratorFit(design);
        if (fit == null) {
            throw new IllegalStateException(
                    "small-craft visual binding references absent engineering fit: " + design);
        }
        var hull = ENGINEERING.findHull(fit.hullId());
        if (hull == null) {
            throw new IllegalStateException(
                    "small-craft visual binding references absent hull: " + fit.hullId());
        }
        return new ResolvedMarker(
                entity,
                binding,
                hull.boundingDimensionsM().lengthM(),
                hull.boundingDimensionsM().widthM(),
                MINIMUM_MARKER_PIXELS,
                ScaleAuthority.EXACT_PHYSICAL_CONTENT);
    }

    /** Visual silhouette family used only by presentation. */
    public enum MarkerKind {
        /** Narrow interception wedge emphasizing velocity vector. */ INTERCEPTOR_WEDGE,
        /** Compact defensive diamond emphasizing screen duty. */ DEFENCE_DIAMOND,
        /** Longer strike spear emphasizing attack-vector readability. */ STRIKE_SPEAR
    }

    /** Presentation-only scale authority. */
    public enum ScaleAuthority {
        /** World dimensions are taken exactly from the production engineering hull. */
        EXACT_PHYSICAL_CONTENT
    }

    /**
     * Stable fit-to-marker binding.
     *
     * @param fitId exact production fit ID
     * @param stableFactionId owning core faction identity
     * @param roleId authored small-craft role label
     * @param markerKind presentation-only marker family
     * @param paletteKey faction presentation palette key
     */
    public record VisualBinding(
            String fitId,
            String stableFactionId,
            String roleId,
            MarkerKind markerKind,
            String paletteKey) {
        /**
         * Validates one presentation binding.
         *
         * @param fitId exact production fit ID
         * @param stableFactionId owning core faction identity
         * @param roleId authored small-craft role label
         * @param markerKind presentation-only marker family
         * @param paletteKey faction presentation palette key
         */
        public VisualBinding {
            fitId = requireText(fitId, "fitId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            roleId = requireText(roleId, "roleId");
            Objects.requireNonNull(markerKind, "markerKind");
            paletteKey = requireText(paletteKey, "paletteKey");
        }
    }

    /**
     * Exact resolved marker data for one persistent craft.
     *
     * @param stableEntityId persistent craft presentation identity
     * @param binding exact stable content binding
     * @param worldLengthM exact physical hull length
     * @param worldWidthM exact physical hull width
     * @param minimumMarkerPixels optional distant-zoom marker footprint only
     * @param scaleAuthority proof that world dimensions remain physical-content authoritative
     */
    public record ResolvedMarker(
            String stableEntityId,
            VisualBinding binding,
            double worldLengthM,
            double worldWidthM,
            double minimumMarkerPixels,
            ScaleAuthority scaleAuthority) {
        /**
         * Validates one resolved marker.
         *
         * @param stableEntityId persistent craft presentation identity
         * @param binding exact stable content binding
         * @param worldLengthM exact physical hull length
         * @param worldWidthM exact physical hull width
         * @param minimumMarkerPixels optional distant-zoom marker footprint only
         * @param scaleAuthority exact physical scale authority
         */
        public ResolvedMarker {
            stableEntityId = requireText(stableEntityId, "stableEntityId");
            Objects.requireNonNull(binding, "binding");
            requirePositive(worldLengthM, "worldLengthM");
            requirePositive(worldWidthM, "worldWidthM");
            requirePositive(minimumMarkerPixels, "minimumMarkerPixels");
            Objects.requireNonNull(scaleAuthority, "scaleAuthority");
        }
    }

    private static Map<String, VisualBinding> loadBindings() {
        LinkedHashMap<String, VisualBinding> result = new LinkedHashMap<>();
        for (DesignBinding design : Stage228SmallCraftProductionProjection.designBindings()) {
            MarkerKind kind = switch (design.roleId()) {
                case Stage228SmallCraftProductionProjection.ROLE_INTERCEPTION ->
                        MarkerKind.INTERCEPTOR_WEDGE;
                case Stage228SmallCraftProductionProjection.ROLE_DEFENCE ->
                        MarkerKind.DEFENCE_DIAMOND;
                case Stage228SmallCraftProductionProjection.ROLE_STRIKE ->
                        MarkerKind.STRIKE_SPEAR;
                default -> throw new IllegalStateException(
                        "unsupported M22.8J small-craft role: " + design.roleId());
            };
            String palette = switch (design.stableFactionId()) {
                case "faction.imperial_directorate" -> "palette.empire.graphite_ivory_burgundy";
                case "faction.industrial_union" -> "palette.industrial_union.graphite_steel_ochre";
                default -> throw new IllegalStateException(
                        "unsupported M22.8J core faction: " + design.stableFactionId());
            };
            VisualBinding binding = new VisualBinding(
                    design.fitId(),
                    design.stableFactionId(),
                    design.roleId(),
                    kind,
                    palette);
            if (result.put(binding.fitId(), binding) != null) {
                throw new IllegalStateException(
                        "duplicate M22.8J small-craft visual binding: " + binding.fitId());
            }
        }
        return Map.copyOf(result);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be positive and finite");
        }
    }
}
