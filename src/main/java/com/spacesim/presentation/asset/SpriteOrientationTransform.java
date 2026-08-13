package com.spacesim.presentation.asset;

import java.util.Objects;

/**
 * Pure presentation transform that normalizes authored ship sprites to runtime forward = right.
 *
 * <p>A left-authored texture is mirrored horizontally around its declared pivot. Hardpoint local
 * positions and visual directions use the exact same mirror so engine exhaust, weapon muzzles and
 * sprite pixels stay aligned.</p>
 */
public final class SpriteOrientationTransform {
    private SpriteOrientationTransform() {
    }

    /**
     * Returns the horizontal render scale required to normalize the authored texture.
     *
     * @param spec sprite contract whose authored facing is being normalized
     * @return positive one for right-facing source art or negative one for left-facing source art
     */
    public static float horizontalScale(ShipSpriteSpec spec) {
        return Objects.requireNonNull(spec, "Sprite spec must not be null")
                .sourceFacing()
                .horizontalScale();
    }

    /**
     * Converts an authored normalized X coordinate into pivot-relative runtime local X.
     *
     * @param spec sprite contract
     * @param normalizedX authored normalized X in [0,1]
     * @param renderScale positive additional preview/render scale
     * @return runtime local X relative to the pivot
     */
    public static float localX(ShipSpriteSpec spec, float normalizedX, float renderScale) {
        validateNormalized(normalizedX, "normalized X");
        validateScale(renderScale);
        return (normalizedX - spec.pivotX())
                * spec.worldWidth()
                * renderScale
                * horizontalScale(spec);
    }

    /**
     * Converts an authored normalized Y coordinate into pivot-relative runtime local Y.
     *
     * @param spec sprite contract
     * @param normalizedY authored normalized Y in [0,1]
     * @param renderScale positive additional preview/render scale
     * @return runtime local Y relative to the pivot
     */
    public static float localY(ShipSpriteSpec spec, float normalizedY, float renderScale) {
        validateNormalized(normalizedY, "normalized Y");
        validateScale(renderScale);
        return (normalizedY - spec.pivotY()) * spec.worldHeight() * renderScale;
    }

    /**
     * Mirrors an authored visual direction when necessary and normalizes it into [0,360).
     *
     * @param spec sprite contract whose authored facing is being normalized
     * @param authoredDirectionDegrees direction expressed in authored sprite space
     * @return runtime-normalized direction in degrees in [0,360)
     */
    public static float directionDegrees(ShipSpriteSpec spec, float authoredDirectionDegrees) {
        Objects.requireNonNull(spec, "Sprite spec must not be null");
        if (!Float.isFinite(authoredDirectionDegrees)) {
            throw new IllegalArgumentException("Direction must be finite");
        }
        float oriented = spec.sourceFacing() == SourceFacing.LEFT
                ? 180f - authoredDirectionDegrees
                : authoredDirectionDegrees;
        float normalized = oriented % 360f;
        return normalized < 0f ? normalized + 360f : normalized;
    }

    private static void validateNormalized(float value, String label) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException(label + " must be finite and in [0,1]");
        }
    }

    private static void validateScale(float renderScale) {
        if (!Float.isFinite(renderScale) || renderScale <= 0f) {
            throw new IllegalArgumentException("Render scale must be finite and positive");
        }
    }
}
