package com.spacesim.ship;

import com.spacesim.ship.WeaponDefinition.ProjectileShape;

import java.util.Objects;

/**
 * One authoritative Stage-17.5E kinetic body with independent position and velocity.
 *
 * <p>This state is deliberately independent from render representation. A tracer, sprite, particle
 * or complete absence of a rendered marker cannot change the physical body, trajectory, mass,
 * momentum or energy represented here.</p>
 *
 * @param projectileId stable simulation-local projectile identity
 * @param sourceEntityId local identity of the firing entity
 * @param materialId stable engineering material content ID
 * @param shape physical projectile shape category
 * @param lengthM projectile length in meters
 * @param diameterM projectile diameter in meters
 * @param massKg current projectile mass in kilograms
 * @param xM current x position in meters
 * @param yM current y position in meters
 * @param velocityXMps current x velocity in meters per second
 * @param velocityYMps current y velocity in meters per second
 */
public record ProjectileBody(
        long projectileId,
        long sourceEntityId,
        String materialId,
        ProjectileShape shape,
        double lengthM,
        double diameterM,
        double massKg,
        double xM,
        double yM,
        double velocityXMps,
        double velocityYMps) {
    /**
     * Validates one independent physical projectile body.
     *
     * @param projectileId stable simulation-local projectile identity
     * @param sourceEntityId local identity of the firing entity
     * @param materialId stable engineering material content ID
     * @param shape physical projectile shape category
     * @param lengthM projectile length in meters
     * @param diameterM projectile diameter in meters
     * @param massKg current projectile mass in kilograms
     * @param xM current x position in meters
     * @param yM current y position in meters
     * @param velocityXMps current x velocity in meters per second
     * @param velocityYMps current y velocity in meters per second
     */
    public ProjectileBody {
        if (projectileId <= 0L) {
            throw new IllegalArgumentException("projectileId must be positive");
        }
        if (sourceEntityId <= 0L) {
            throw new IllegalArgumentException("sourceEntityId must be positive");
        }
        if (materialId == null || materialId.isBlank()) {
            throw new IllegalArgumentException("materialId must be non-blank");
        }
        Objects.requireNonNull(shape, "shape");
        requirePositiveFinite(lengthM, "lengthM");
        requirePositiveFinite(diameterM, "diameterM");
        requirePositiveFinite(massKg, "massKg");
        requireFinite(xM, "xM");
        requireFinite(yM, "yM");
        requireFinite(velocityXMps, "velocityXMps");
        requireFinite(velocityYMps, "velocityYMps");
    }

    /** @return current speed magnitude in meters per second */
    public double speedMps() {
        return Math.hypot(velocityXMps, velocityYMps);
    }

    /** @return current x momentum component in newton-seconds */
    public double momentumXNs() {
        return massKg * velocityXMps;
    }

    /** @return current y momentum component in newton-seconds */
    public double momentumYNs() {
        return massKg * velocityYMps;
    }

    /** @return current kinetic energy in joules */
    public double kineticEnergyJ() {
        double speed = speedMps();
        return 0.5d * massKg * speed * speed;
    }

    /**
     * Advances the body ballistically for one deterministic interval.
     *
     * <p>No lifetime or render-distance culling is applied here. A missed projectile therefore
     * remains a physical body until an explicit higher-level simulation/LOD policy transforms or
     * removes it while preserving the intended physical consequence.</p>
     *
     * @param deltaSeconds positive simulation interval
     * @return advanced immutable body
     */
    public ProjectileBody advance(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0d) {
            throw new IllegalArgumentException("deltaSeconds must be finite and positive");
        }
        return new ProjectileBody(
                projectileId,
                sourceEntityId,
                materialId,
                shape,
                lengthM,
                diameterM,
                massKg,
                xM + velocityXMps * deltaSeconds,
                yM + velocityYMps * deltaSeconds,
                velocityXMps,
                velocityYMps);
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
