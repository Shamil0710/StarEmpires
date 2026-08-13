package com.spacesim.presentation.asset;

import java.util.List;
import java.util.Objects;

/**
 * Presentation-only resource paths that make up one production ship visual asset pack.
 *
 * <p>Base, emissive and damage textures are full-frame ship layers and are expected to share the
 * same canvas alignment. Engine idle/thrust textures are ship-specific exhaust VFX resources. None
 * of these paths participate in authoritative gameplay or save identity.</p>
 */
public final class ShipVisualAssetSet {
    private final String baseTexturePath;
    private final String emissiveTexturePath;
    private final String damageTexturePath;
    private final String engineIdleTexturePath;
    private final String engineThrustTexturePath;

    /**
     * Creates a complete ship visual asset pack contract.
     *
     * @param baseTexturePath base hull texture resource path
     * @param emissiveTexturePath emissive-mask resource path
     * @param damageTexturePath damage-overlay resource path
     * @param engineIdleTexturePath idle engine-exhaust resource path
     * @param engineThrustTexturePath thrust engine-exhaust resource path
     */
    public ShipVisualAssetSet(
            String baseTexturePath,
            String emissiveTexturePath,
            String damageTexturePath,
            String engineIdleTexturePath,
            String engineThrustTexturePath) {
        this.baseTexturePath = requireText(baseTexturePath, "Base texture path");
        this.emissiveTexturePath = requireText(emissiveTexturePath, "Emissive texture path");
        this.damageTexturePath = requireText(damageTexturePath, "Damage texture path");
        this.engineIdleTexturePath = requireText(engineIdleTexturePath, "Engine idle texture path");
        this.engineThrustTexturePath = requireText(engineThrustTexturePath, "Engine thrust texture path");

        List<String> paths = allTexturePaths();
        if (paths.stream().distinct().count() != paths.size()) {
            throw new IllegalArgumentException("Ship visual asset paths must be unique");
        }
    }

    /** @return base hull texture resource path */
    public String baseTexturePath() {
        return baseTexturePath;
    }

    /** @return emissive-mask resource path */
    public String emissiveTexturePath() {
        return emissiveTexturePath;
    }

    /** @return damage-overlay resource path */
    public String damageTexturePath() {
        return damageTexturePath;
    }

    /** @return idle engine-exhaust resource path */
    public String engineIdleTexturePath() {
        return engineIdleTexturePath;
    }

    /** @return thrust engine-exhaust resource path */
    public String engineThrustTexturePath() {
        return engineThrustTexturePath;
    }

    /** @return immutable ordered list of every texture path in the pack */
    public List<String> allTexturePaths() {
        return List.of(
                baseTexturePath,
                emissiveTexturePath,
                damageTexturePath,
                engineIdleTexturePath,
                engineThrustTexturePath);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
