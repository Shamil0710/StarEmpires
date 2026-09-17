package com.spacesim.presentation.asset;

import java.util.Optional;

/** Deterministic presentation-only geometry for a restrained engine exhaust plume behind a ship. */
public final class Stage22EnginePlumeGeometry {
    private Stage22EnginePlumeGeometry() {
        throw new AssertionError("utility class");
    }

    /**
     * Resolved screen-space plume transform.
     *
     * @param centerX plume center X
     * @param centerY plume center Y
     * @param lengthPixels plume length along the ship forward axis
     * @param widthPixels plume transverse width
     * @param rotationDegrees same counter-clockwise runtime rotation as the hull
     * @param alpha additive overlay alpha
     */
    public record PlumeTransform(
            float centerX,
            float centerY,
            float lengthPixels,
            float widthPixels,
            float rotationDegrees,
            float alpha) {
        /**
         * Validates finite positive effect geometry.
         *
         * @param centerX plume center X
         * @param centerY plume center Y
         * @param lengthPixels plume length along the ship forward axis
         * @param widthPixels plume transverse width
         * @param rotationDegrees same counter-clockwise runtime rotation as the hull
         * @param alpha additive overlay alpha
         */
        public PlumeTransform {
            if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                    || !Float.isFinite(lengthPixels) || !Float.isFinite(widthPixels)
                    || !Float.isFinite(rotationDegrees) || !Float.isFinite(alpha)
                    || lengthPixels <= 0f || widthPixels <= 0f || alpha <= 0f || alpha > 1f) {
                throw new IllegalArgumentException("invalid plume transform");
            }
        }
    }

    /**
     * Resolves a plume behind a rendered hull. Zero propulsion intentionally produces no effect.
     * The effect remains proportional to physical on-screen hull size so zoom never turns it into a
     * fixed-size UI marker.
     *
     * @param hullCenterX rendered hull center X
     * @param hullCenterY rendered hull center Y
     * @param hullLengthPixels rendered physical hull length
     * @param hullWidthPixels rendered physical hull width
     * @param rotationDegrees counter-clockwise runtime hull rotation
     * @param propulsionFraction normalized authoritative propulsion activity
     * @return empty for engine-off, otherwise deterministic plume transform
     */
    public static Optional<PlumeTransform> resolve(
            float hullCenterX,
            float hullCenterY,
            float hullLengthPixels,
            float hullWidthPixels,
            float rotationDegrees,
            double propulsionFraction) {
        requireFinite(hullCenterX, "hullCenterX");
        requireFinite(hullCenterY, "hullCenterY");
        requirePositive(hullLengthPixels, "hullLengthPixels");
        requirePositive(hullWidthPixels, "hullWidthPixels");
        requireFinite(rotationDegrees, "rotationDegrees");
        if (!Double.isFinite(propulsionFraction) || propulsionFraction < 0d || propulsionFraction > 1d) {
            throw new IllegalArgumentException("propulsionFraction must be finite and in [0,1]");
        }
        if (propulsionFraction == 0d) {
            return Optional.empty();
        }

        float duty = (float) propulsionFraction;
        float length = hullLengthPixels * (0.18f + 0.24f * duty);
        float width = Math.max(1.5f, hullWidthPixels * (0.18f + 0.10f * duty));
        float alpha = 0.38f + 0.42f * duty;
        double radians = Math.toRadians(rotationDegrees);
        float forwardX = (float) Math.cos(radians);
        float forwardY = (float) Math.sin(radians);
        float offset = hullLengthPixels * 0.5f + length * 0.40f;
        return Optional.of(new PlumeTransform(
                hullCenterX - forwardX * offset,
                hullCenterY - forwardY * offset,
                length,
                width,
                rotationDegrees,
                alpha));
    }

    private static void requireFinite(float value, String field) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    private static void requirePositive(float value, String field) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
