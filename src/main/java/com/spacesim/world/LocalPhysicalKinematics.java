package com.spacesim.world;

import java.util.Objects;

/**
 * Authoritative Stage-20 local physical kinematics kept outside legacy global-float ECS transforms.
 *
 * <p>The position uses the accepted hierarchical far-coordinate representation. Velocity remains
 * double-precision SI and is not camera-relative. This value object is suitable for exact runtime
 * materialization round-trips; persistent save/load integration is a separate closure slice.</p>
 *
 * @param position authoritative hierarchical local-system position
 * @param velocityXMps authoritative local X velocity in meters per second
 * @param velocityYMps authoritative local Y velocity in meters per second
 */
public record LocalPhysicalKinematics(
        LocalPhysicalPosition position,
        double velocityXMps,
        double velocityYMps) {
    /**
     * Validates one physical kinematic state.
     *
     * @param position authoritative physical position
     * @param velocityXMps X velocity in meters per second
     * @param velocityYMps Y velocity in meters per second
     */
    public LocalPhysicalKinematics {
        Objects.requireNonNull(position, "position");
        requireFinite(velocityXMps, "velocityXMps");
        requireFinite(velocityYMps, "velocityYMps");
    }

    /**
     * Creates a zero-velocity state at an explicit physical position.
     *
     * @param position authoritative physical position
     * @return immutable zero-velocity kinematic state
     */
    public static LocalPhysicalKinematics stationary(LocalPhysicalPosition position) {
        return new LocalPhysicalKinematics(position, 0d, 0d);
    }

    /**
     * Returns the same velocity at another authoritative physical position.
     *
     * @param newPosition replacement physical position
     * @return translated immutable kinematic state
     */
    public LocalPhysicalKinematics atPosition(LocalPhysicalPosition newPosition) {
        return new LocalPhysicalKinematics(newPosition, velocityXMps, velocityYMps);
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
