package com.spacesim.components;

import com.badlogic.ashley.core.Component;

/**
 * Transient normalized movement intent for autonomous/local flight executors.
 *
 * <p>The component contains no acceleration or mass values. AI and future fleet-order code express
 * intent here; {@link com.spacesim.systems.AutonomousFlightSystem} applies the same
 * {@link com.spacesim.flight.FlightDynamics} limits used by direct player control.</p>
 */
public final class FlightCommandComponent implements Component {
    /** Desired normalized horizontal movement axis. */
    public float axisX;
    /** Desired normalized vertical movement axis. */
    public float axisY;
    /** Positive assisted speed cap requested by the caller. */
    public float speedCap;

    /** Creates a stopped unconfigured transient command. */
    public FlightCommandComponent() {
    }

    /**
     * Sets normalized movement intent and its physical hull speed cap.
     *
     * @param x finite horizontal input
     * @param y finite vertical input
     * @param maximumSpeed finite positive assisted speed cap
     */
    public void set(float x, float y, float maximumSpeed) {
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(maximumSpeed) || maximumSpeed <= 0f) {
            throw new IllegalArgumentException("Flight command values must be finite and speed positive");
        }
        float lengthSquared = x * x + y * y;
        if (lengthSquared > 1f) {
            float inverse = 1f / (float) Math.sqrt(lengthSquared);
            x *= inverse;
            y *= inverse;
        }
        axisX = Math.max(-1f, Math.min(1f, x));
        axisY = Math.max(-1f, Math.min(1f, y));
        speedCap = maximumSpeed;
    }

    /** Clears desired movement while retaining the configured speed cap. */
    public void stop() {
        axisX = 0f;
        axisY = 0f;
    }
}
