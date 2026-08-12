package com.spacesim.presentation.asset;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Presentation-only contract for one ship sprite asset.
 *
 * <p>The contract explicitly separates art dimensions from gameplay geometry. Collision/selection
 * footprint is declared explicitly rather than inferred from transparent PNG pixels. Texture paths
 * and hardpoints are presentation metadata and therefore do not participate in save identity or
 * economic/content semantics.</p>
 */
public final class ShipSpriteSpec {
    private final String assetId;
    private final String baseTexturePath;
    private final String emissiveTexturePath;
    private final float worldWidth;
    private final float worldHeight;
    private final float pivotX;
    private final float pivotY;
    private final float collisionWidth;
    private final float collisionHeight;
    private final List<VisualHardpoint> hardpoints;

    /**
     * Creates a validated sprite specification with a circular footprint.
     *
     * <p>This compatibility constructor is convenient for compact ships. Broad or elongated ships
     * should use the width/height footprint constructor instead.</p>
     *
     * @param assetId stable presentation asset identifier
     * @param baseTexturePath non-blank classpath/resource path for the base sprite
     * @param emissiveTexturePath optional emissive resource path; null/blank means none
     * @param worldWidth positive intended rendered width in world units
     * @param worldHeight positive intended rendered height in world units
     * @param pivotX normalized horizontal pivot in [0,1]
     * @param pivotY normalized vertical pivot in [0,1]
     * @param collisionRadius positive circular footprint radius in world units
     * @param hardpoints immutable-by-copy presentation attachment points
     */
    public ShipSpriteSpec(
            String assetId,
            String baseTexturePath,
            String emissiveTexturePath,
            float worldWidth,
            float worldHeight,
            float pivotX,
            float pivotY,
            float collisionRadius,
            List<VisualHardpoint> hardpoints) {
        this(
                assetId,
                baseTexturePath,
                emissiveTexturePath,
                worldWidth,
                worldHeight,
                pivotX,
                pivotY,
                validatedDiameter(collisionRadius),
                validatedDiameter(collisionRadius),
                hardpoints);
    }

    /**
     * Creates a validated sprite specification with an explicit elliptical footprint.
     *
     * @param assetId stable presentation asset identifier
     * @param baseTexturePath non-blank classpath/resource path for the base sprite
     * @param emissiveTexturePath optional emissive resource path; null/blank means none
     * @param worldWidth positive intended rendered width in world units
     * @param worldHeight positive intended rendered height in world units
     * @param pivotX normalized horizontal pivot in [0,1]
     * @param pivotY normalized vertical pivot in [0,1]
     * @param collisionWidth positive footprint width in world units
     * @param collisionHeight positive footprint height in world units
     * @param hardpoints immutable-by-copy presentation attachment points
     */
    public ShipSpriteSpec(
            String assetId,
            String baseTexturePath,
            String emissiveTexturePath,
            float worldWidth,
            float worldHeight,
            float pivotX,
            float pivotY,
            float collisionWidth,
            float collisionHeight,
            List<VisualHardpoint> hardpoints) {
        this.assetId = requireText(assetId, "Asset ID");
        this.baseTexturePath = requireText(baseTexturePath, "Base texture path");
        this.emissiveTexturePath = normalizeOptionalText(emissiveTexturePath);
        if (!Float.isFinite(worldWidth)
                || !Float.isFinite(worldHeight)
                || worldWidth <= 0f
                || worldHeight <= 0f) {
            throw new IllegalArgumentException("Sprite world dimensions must be finite and positive");
        }
        if (!Float.isFinite(pivotX)
                || !Float.isFinite(pivotY)
                || pivotX < 0f
                || pivotX > 1f
                || pivotY < 0f
                || pivotY > 1f) {
            throw new IllegalArgumentException("Sprite pivot must be finite and in [0,1]");
        }
        if (!Float.isFinite(collisionWidth)
                || !Float.isFinite(collisionHeight)
                || collisionWidth <= 0f
                || collisionHeight <= 0f) {
            throw new IllegalArgumentException("Collision footprint dimensions must be finite and positive");
        }
        Objects.requireNonNull(hardpoints, "Hardpoints must not be null");
        this.hardpoints = List.copyOf(hardpoints);
        Set<String> ids = new HashSet<>();
        for (VisualHardpoint hardpoint : this.hardpoints) {
            Objects.requireNonNull(hardpoint, "Hardpoint list must not contain null");
            if (!ids.add(hardpoint.id())) {
                throw new IllegalArgumentException("Duplicate hardpoint ID: " + hardpoint.id());
            }
        }
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.collisionWidth = collisionWidth;
        this.collisionHeight = collisionHeight;
    }

    /** @return stable presentation asset ID */
    public String assetId() {
        return assetId;
    }

    /** @return base sprite resource path */
    public String baseTexturePath() {
        return baseTexturePath;
    }

    /** @return optional emissive resource path, or null */
    public String emissiveTexturePath() {
        return emissiveTexturePath;
    }

    /** @return intended rendered width in world units */
    public float worldWidth() {
        return worldWidth;
    }

    /** @return intended rendered height in world units */
    public float worldHeight() {
        return worldHeight;
    }

    /** @return normalized horizontal pivot */
    public float pivotX() {
        return pivotX;
    }

    /** @return normalized vertical pivot */
    public float pivotY() {
        return pivotY;
    }

    /** @return explicit elliptical collision/selection footprint width in world units */
    public float collisionWidth() {
        return collisionWidth;
    }

    /** @return explicit elliptical collision/selection footprint height in world units */
    public float collisionHeight() {
        return collisionHeight;
    }

    /**
     * Returns a conservative circular bound for compatibility with callers that only support a
     * radius. New presentation/selection code should prefer {@link #collisionWidth()} and
     * {@link #collisionHeight()}.
     *
     * @return half of the larger explicit footprint dimension
     */
    public float collisionRadius() {
        return Math.max(collisionWidth, collisionHeight) * 0.5f;
    }

    /** @return immutable ordered hardpoint list */
    public List<VisualHardpoint> hardpoints() {
        return hardpoints;
    }

    private static float validatedDiameter(float radius) {
        if (!Float.isFinite(radius) || radius <= 0f) {
            throw new IllegalArgumentException("Collision radius must be finite and positive");
        }
        return radius * 2f;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
