package com.spacesim.presentation.asset;

import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;

import java.util.Objects;

/**
 * Presentation-only runtime effect metadata layered onto immutable authored sprite bindings.
 *
 * <p>The marker lives only in the presentation asset ID. It never changes the content ID, physical
 * dimensions, collision, simulation identity or texture path. Current Stage-20 propulsion authority
 * is binary, but the encoding retains a normalized fraction for later continuous-throttle runtimes.</p>
 */
public final class Stage22RuntimeSpriteEffects {
    private static final String PROPULSION_MARKER = "|runtime-propulsion=";

    private Stage22RuntimeSpriteEffects() {
        throw new AssertionError("utility class");
    }

    /**
     * Returns one runtime presentation binding carrying a normalized propulsion activity hint.
     * Existing runtime propulsion metadata is replaced, including when activity returns to zero.
     *
     * @param binding authored or already-resolved sprite binding
     * @param propulsionFraction normalized simulation-authoritative propulsion activity
     * @return equivalent binding carrying exactly the requested runtime propulsion state
     */
    public static SpriteBinding withPropulsion(SpriteBinding binding, double propulsionFraction) {
        SpriteBinding source = Objects.requireNonNull(binding, "binding");
        requireFraction(propulsionFraction);
        String authoredAssetId = stripRuntimePropulsion(source.assetId());
        if (propulsionFraction == 0d && authoredAssetId.equals(source.assetId())) {
            return source;
        }
        String runtimeAssetId = propulsionFraction == 0d
                ? authoredAssetId
                : authoredAssetId + PROPULSION_MARKER + Double.toHexString(propulsionFraction);
        return new SpriteBinding(
                runtimeAssetId,
                source.role(),
                source.texturePath(),
                source.region(),
                source.pivotX(),
                source.pivotY(),
                source.sourceFacing(),
                source.nominalLengthM(),
                source.nominalWidthM(),
                source.hardpoints());
    }

    /**
     * Reads normalized propulsion activity from a runtime binding.
     *
     * @param binding runtime or authored binding
     * @return normalized activity, zero when no runtime propulsion marker is present
     */
    public static double propulsionFraction(SpriteBinding binding) {
        String assetId = Objects.requireNonNull(binding, "binding").assetId();
        int marker = assetId.lastIndexOf(PROPULSION_MARKER);
        if (marker < 0) {
            return 0d;
        }
        String encoded = assetId.substring(marker + PROPULSION_MARKER.length());
        double value;
        try {
            value = Double.parseDouble(encoded);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid runtime propulsion marker: " + assetId, exception);
        }
        requireFraction(value);
        return value;
    }

    /**
     * Returns the stable authored asset ID without runtime propulsion metadata.
     *
     * @param binding runtime or authored binding
     * @return authored presentation identity
     */
    public static String authoredAssetId(SpriteBinding binding) {
        return stripRuntimePropulsion(Objects.requireNonNull(binding, "binding").assetId());
    }

    private static String stripRuntimePropulsion(String assetId) {
        int marker = assetId.lastIndexOf(PROPULSION_MARKER);
        return marker < 0 ? assetId : assetId.substring(0, marker);
    }

    private static void requireFraction(double value) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException("propulsionFraction must be finite and in [0,1]");
        }
    }
}
