package com.spacesim.presentation.asset;

import java.util.Objects;

/**
 * One presentation-space attachment point expressed in normalized sprite coordinates.
 *
 * <p>{@code x=0,y=0} is the sprite bottom-left and {@code x=1,y=1} is the top-right. The
 * direction is presentation metadata only and does not define authoritative weapon or thrust
 * physics.</p>
 */
public final class VisualHardpoint {
    private final String id;
    private final VisualHardpointType type;
    private final float normalizedX;
    private final float normalizedY;
    private final float directionDegrees;

    /**
     * Creates a hardpoint.
     *
     * @param id non-blank local identifier
     * @param type visual role
     * @param normalizedX horizontal normalized coordinate in [0,1]
     * @param normalizedY vertical normalized coordinate in [0,1]
     * @param directionDegrees finite presentation direction in degrees
     */
    public VisualHardpoint(
            String id,
            VisualHardpointType type,
            float normalizedX,
            float normalizedY,
            float directionDegrees) {
        Objects.requireNonNull(id, "Hardpoint ID must not be null");
        this.type = Objects.requireNonNull(type, "Hardpoint type must not be null");
        String normalizedId = id.trim();
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("Hardpoint ID must not be blank");
        }
        if (!Float.isFinite(normalizedX)
                || !Float.isFinite(normalizedY)
                || normalizedX < 0f
                || normalizedX > 1f
                || normalizedY < 0f
                || normalizedY > 1f) {
            throw new IllegalArgumentException("Hardpoint coordinates must be finite and in [0,1]");
        }
        if (!Float.isFinite(directionDegrees)) {
            throw new IllegalArgumentException("Hardpoint direction must be finite");
        }
        this.id = normalizedId;
        this.normalizedX = normalizedX;
        this.normalizedY = normalizedY;
        this.directionDegrees = directionDegrees;
    }

    /** @return local stable hardpoint identifier */
    public String id() {
        return id;
    }

    /** @return presentation hardpoint role */
    public VisualHardpointType type() {
        return type;
    }

    /** @return horizontal normalized coordinate */
    public float normalizedX() {
        return normalizedX;
    }

    /** @return vertical normalized coordinate */
    public float normalizedY() {
        return normalizedY;
    }

    /** @return presentation direction in degrees */
    public float directionDegrees() {
        return directionDegrees;
    }
}
