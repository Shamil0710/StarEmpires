package com.spacesim.ship;

import com.spacesim.ship.WeaponDefinition.GuidedWeapon;

import java.util.Objects;

/**
 * One authoritative Stage-17.5E guided missile/interceptor body.
 *
 * <p>Guidance and seeker availability are capabilities of the body, not its existence. Destroying
 * guidance therefore never deletes the physical mass, position, velocity or residual kinetic energy.
 * Propulsion consumes real propellant and changes velocity through the rocket equation.</p>
 *
 * @param bodyId stable simulation-local guided-body identity
 * @param sourceEntityId local identity of the launching entity
 * @param targetId current target hypothesis identity
 * @param definition immutable physical guided-weapon definition
 * @param xM current x position in meters
 * @param yM current y position in meters
 * @param velocityXMps current x velocity in meters per second
 * @param velocityYMps current y velocity in meters per second
 * @param remainingPropellantKg remaining onboard propellant in kilograms
 * @param seekerAvailable whether the seeker can currently produce measurements
 * @param guidanceAvailable whether guidance/control can currently command maneuvers
 */
public record GuidedWeaponBody(
        long bodyId,
        long sourceEntityId,
        long targetId,
        GuidedWeapon definition,
        double xM,
        double yM,
        double velocityXMps,
        double velocityYMps,
        double remainingPropellantKg,
        boolean seekerAvailable,
        boolean guidanceAvailable) {
    private static final double EPSILON = 1e-9d;

    /**
     * Validates one authoritative guided physical body.
     *
     * @param bodyId stable simulation-local guided-body identity
     * @param sourceEntityId local identity of the launching entity
     * @param targetId current target hypothesis identity
     * @param definition immutable physical guided-weapon definition
     * @param xM current x position in meters
     * @param yM current y position in meters
     * @param velocityXMps current x velocity in meters per second
     * @param velocityYMps current y velocity in meters per second
     * @param remainingPropellantKg remaining onboard propellant in kilograms
     * @param seekerAvailable whether the seeker can currently produce measurements
     * @param guidanceAvailable whether guidance/control can currently command maneuvers
     */
    public GuidedWeaponBody {
        if (bodyId <= 0L) {
            throw new IllegalArgumentException("bodyId must be positive");
        }
        if (sourceEntityId <= 0L) {
            throw new IllegalArgumentException("sourceEntityId must be positive");
        }
        if (targetId <= 0L) {
            throw new IllegalArgumentException("targetId must be positive");
        }
        Objects.requireNonNull(definition, "definition");
        requireFinite(xM, "xM");
        requireFinite(yM, "yM");
        requireFinite(velocityXMps, "velocityXMps");
        requireFinite(velocityYMps, "velocityYMps");
        requireNonNegativeFinite(remainingPropellantKg, "remainingPropellantKg");
        if (remainingPropellantKg > definition.propellantMassKg() + EPSILON) {
            throw new IllegalArgumentException("remaining propellant exceeds authored capacity");
        }
    }

    /**
     * Creates a freshly launched body with a full authored propellant load.
     *
     * @param bodyId new guided-body identity
     * @param sourceEntityId launching entity identity
     * @param targetId target hypothesis identity
     * @param definition physical guided-weapon definition
     * @param xM launch x position
     * @param yM launch y position
     * @param velocityXMps launch x velocity
     * @param velocityYMps launch y velocity
     * @return fully fueled guided body with seeker/guidance available
     */
    public static GuidedWeaponBody launch(
            long bodyId,
            long sourceEntityId,
            long targetId,
            GuidedWeapon definition,
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps) {
        GuidedWeapon checked = Objects.requireNonNull(definition, "definition");
        return new GuidedWeaponBody(
                bodyId,
                sourceEntityId,
                targetId,
                checked,
                xM,
                yM,
                velocityXMps,
                velocityYMps,
                checked.propellantMassKg(),
                true,
                true);
    }

    /** @return current wet mass in kilograms */
    public double currentMassKg() {
        return definition.dryMassKg() + remainingPropellantKg;
    }

    /** @return current speed magnitude in meters per second */
    public double speedMps() {
        return Math.hypot(velocityXMps, velocityYMps);
    }

    /** @return current kinetic energy in joules */
    public double kineticEnergyJ() {
        double speed = speedMps();
        return 0.5d * currentMassKg() * speed * speed;
    }

    /** @return ideal remaining propulsion delta-v in meters per second */
    public double remainingDeltaVMps() {
        if (remainingPropellantKg <= EPSILON) {
            return 0d;
        }
        return definition.exhaustVelocityMps()
                * Math.log(currentMassKg() / definition.dryMassKg());
    }

    /**
     * Returns the same physical body after loss of guidance/control capability.
     *
     * @return body with guidance disabled and all physical state preserved
     */
    public GuidedWeaponBody disableGuidance() {
        return new GuidedWeaponBody(
                bodyId,
                sourceEntityId,
                targetId,
                definition,
                xM,
                yM,
                velocityXMps,
                velocityYMps,
                remainingPropellantKg,
                seekerAvailable,
                false);
    }

    /**
     * Returns the same physical body after loss of seeker capability.
     *
     * @return body with seeker disabled and all physical state preserved
     */
    public GuidedWeaponBody disableSeeker() {
        return new GuidedWeaponBody(
                bodyId,
                sourceEntityId,
                targetId,
                definition,
                xM,
                yM,
                velocityXMps,
                velocityYMps,
                remainingPropellantKg,
                false,
                guidanceAvailable);
    }

    /**
     * Executes a propulsion burn in an explicitly supplied unit direction.
     *
     * <p>The method consumes only the propellant available during the requested interval and applies
     * rocket-equation delta-v. It does not choose a guidance law; Stage-17.5E guidance supplies the
     * direction through the same player/AI-neutral path.</p>
     *
     * @param directionX x component of requested thrust direction
     * @param directionY y component of requested thrust direction
     * @param deltaSeconds positive commanded burn interval
     * @return body after physical propellant consumption and velocity change
     */
    public GuidedWeaponBody burn(double directionX, double directionY, double deltaSeconds) {
        requireFinite(directionX, "directionX");
        requireFinite(directionY, "directionY");
        requirePositiveFinite(deltaSeconds, "deltaSeconds");
        if (!guidanceAvailable || remainingPropellantKg <= EPSILON) {
            return this;
        }
        double magnitude = Math.hypot(directionX, directionY);
        if (magnitude <= EPSILON) {
            throw new IllegalArgumentException("thrust direction must be non-zero");
        }
        double unitX = directionX / magnitude;
        double unitY = directionY / magnitude;
        double massFlow = definition.massFlowKgPerS();
        double requestedPropellant = massFlow * deltaSeconds;
        double consumed = Math.min(remainingPropellantKg, requestedPropellant);
        if (consumed <= EPSILON) {
            return this;
        }
        double initialMass = currentMassKg();
        double finalPropellant = Math.max(0d, remainingPropellantKg - consumed);
        double finalMass = definition.dryMassKg() + finalPropellant;
        double deltaV = definition.exhaustVelocityMps() * Math.log(initialMass / finalMass);
        return new GuidedWeaponBody(
                bodyId,
                sourceEntityId,
                targetId,
                definition,
                xM,
                yM,
                velocityXMps + unitX * deltaV,
                velocityYMps + unitY * deltaV,
                finalPropellant,
                seekerAvailable,
                guidanceAvailable);
    }

    /**
     * Advances the current body ballistically without inventing a guidance correction.
     *
     * @param deltaSeconds positive simulation interval
     * @return advanced body with unchanged velocity and subsystem availability
     */
    public GuidedWeaponBody advanceBallistic(double deltaSeconds) {
        requirePositiveFinite(deltaSeconds, "deltaSeconds");
        return new GuidedWeaponBody(
                bodyId,
                sourceEntityId,
                targetId,
                definition,
                xM + velocityXMps * deltaSeconds,
                yM + velocityYMps * deltaSeconds,
                velocityXMps,
                velocityYMps,
                remainingPropellantKg,
                seekerAvailable,
                guidanceAvailable);
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
