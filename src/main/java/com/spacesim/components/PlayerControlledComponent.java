package com.spacesim.components;

import com.badlogic.ashley.core.Component;

/**
 * Transient Stage-12 direct-control intent attached only to the currently controlled player ship.
 *
 * <p>The component is deliberately absent from persistence mapping. UI/controller code writes only
 * normalized intent here; {@link com.spacesim.systems.PlayerDirectControlSystem} is the sole owner
 * of physical Transform mutation and runs inside the fixed-tick Ashley pipeline.</p>
 */
public final class PlayerControlledComponent implements Component {
    /** Normalized horizontal movement intent in {@code [-1, 1]}. */
    public float axisX;
    /** Normalized vertical movement intent in {@code [-1, 1]}. */
    public float axisY;
    /** Positive physical movement speed in world units per simulation second. */
    public float movementSpeed;
    /** Whether the ship is currently docked and therefore movement-locked. */
    public boolean docked;

    /** Creates a zero-input transient control component. */
    public PlayerControlledComponent() {
    }

    /**
     * Updates movement intent, normalizing vectors whose magnitude exceeds one.
     *
     * @param x finite horizontal input
     * @param y finite vertical input
     */
    public void setIntent(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Player control axes must be finite");
        }
        float lengthSquared = x * x + y * y;
        if (!Float.isFinite(lengthSquared)) {
            throw new IllegalArgumentException("Player control vector magnitude is invalid");
        }
        if (lengthSquared > 1f) {
            float inverse = 1f / (float) Math.sqrt(lengthSquared);
            x *= inverse;
            y *= inverse;
        }
        axisX = Math.max(-1f, Math.min(1f, x));
        axisY = Math.max(-1f, Math.min(1f, y));
    }

    /** Clears movement intent without changing speed or docking state. */
    public void stop() {
        axisX = 0f;
        axisY = 0f;
    }
}
