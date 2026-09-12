package com.spacesim.presentation.asset;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Presentation-only catalogue for tactical projectile and missile artwork.
 *
 * <p>The catalogue deliberately carries no physical dimensions, damage values, guidance data,
 * collision radius or other simulation authority. Render size always comes from the projected
 * {@code BodyGlyph.lengthM()/widthM()} values. Multiple visual variants are selected only from the
 * stable body identity, so replaying the same snapshot produces the same artwork without adding
 * simulation randomness.</p>
 */
public final class OrdnanceSpriteCatalog {
    /** Stable contract version for the first generated ordnance sprite pack. */
    public static final String CURRENT_VERSION = "stage22_7.tactical-ordnance-sprites.v1";

    private static final String ROOT = "assets/ordnance/";

    /** One immutable authored ordnance image. */
    public record SpriteVariant(String assetId, BodyKind kind, String texturePath) {
        /** Validates presentation metadata only. */
        public SpriteVariant {
            assetId = requireText(assetId, "assetId");
            Objects.requireNonNull(kind, "kind");
            texturePath = requireText(texturePath, "texturePath");
            if (!supports(kind)) {
                throw new IllegalArgumentException("unsupported ordnance body kind: " + kind);
            }
        }
    }

    private static final List<SpriteVariant> KINETIC = List.of(
            variant("kinetic_penetrator_a", BodyKind.KINETIC_PROJECTILE),
            variant("kinetic_shell", BodyKind.KINETIC_PROJECTILE),
            variant("fragmentation_shell", BodyKind.KINETIC_PROJECTILE),
            variant("kinetic_penetrator_b", BodyKind.KINETIC_PROJECTILE));

    private static final List<SpriteVariant> GUIDED = List.of(
            variant("guided_rocket_a", BodyKind.GUIDED_MISSILE),
            variant("guided_rocket_b", BodyKind.GUIDED_MISSILE),
            variant("guided_missile_a", BodyKind.GUIDED_MISSILE),
            variant("guided_torpedo", BodyKind.GUIDED_MISSILE),
            variant("guided_micro_missile", BodyKind.GUIDED_MISSILE));

    private static final List<SpriteVariant> INTERCEPTORS = List.of(
            variant("interceptor_missile", BodyKind.INTERCEPTOR));

    private static final Map<BodyKind, List<SpriteVariant>> VARIANTS = createVariants();

    private OrdnanceSpriteCatalog() {
        throw new AssertionError("No instances");
    }

    /**
     * Returns whether the body kind has authored ordnance art.
     *
     * @param kind tactical presentation body kind
     * @return true only for kinetic projectiles, guided missiles and interceptors
     */
    public static boolean supports(BodyKind kind) {
        return kind == BodyKind.KINETIC_PROJECTILE
                || kind == BodyKind.GUIDED_MISSILE
                || kind == BodyKind.INTERCEPTOR;
    }

    /**
     * Resolves a deterministic presentation variant from authoritative body identity.
     *
     * @param kind supported presentation category
     * @param bodyId positive stable body identity
     * @return immutable authored variant
     */
    public static SpriteVariant resolve(BodyKind kind, long bodyId) {
        Objects.requireNonNull(kind, "kind");
        if (bodyId <= 0L) {
            throw new IllegalArgumentException("bodyId must be positive");
        }
        List<SpriteVariant> variants = VARIANTS.get(kind);
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("body kind has no ordnance sprite: " + kind);
        }
        int index = (int) Math.floorMod(bodyId - 1L, variants.size());
        return variants.get(index);
    }

    /** @return deterministic immutable list of every authored ordnance variant */
    public static List<SpriteVariant> allVariants() {
        return List.of(
                KINETIC.get(0), KINETIC.get(1), KINETIC.get(2), KINETIC.get(3),
                GUIDED.get(0), GUIDED.get(1), GUIDED.get(2), GUIDED.get(3), GUIDED.get(4),
                INTERCEPTORS.get(0));
    }

    /** @return immutable classpath paths used by the ordnance pack */
    public static List<String> allTexturePaths() {
        return allVariants().stream().map(SpriteVariant::texturePath).distinct().sorted().toList();
    }

    /**
     * Converts runtime heading (+X at zero radians) to source-art rotation (nose authored toward +Y).
     *
     * @param headingRad finite runtime heading in radians
     * @return counter-clockwise sprite rotation in degrees
     */
    public static float rotationDegrees(double headingRad) {
        if (!Double.isFinite(headingRad)) {
            throw new IllegalArgumentException("headingRad must be finite");
        }
        return (float) Math.toDegrees(headingRad) - 90f;
    }

    private static Map<BodyKind, List<SpriteVariant>> createVariants() {
        Map<BodyKind, List<SpriteVariant>> result = new EnumMap<>(BodyKind.class);
        result.put(BodyKind.KINETIC_PROJECTILE, KINETIC);
        result.put(BodyKind.GUIDED_MISSILE, GUIDED);
        result.put(BodyKind.INTERCEPTOR, INTERCEPTORS);
        return Map.copyOf(result);
    }

    private static SpriteVariant variant(String id, BodyKind kind) {
        return new SpriteVariant(
                "sprite.ordnance." + id,
                kind,
                ROOT + id + ".png");
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
