package com.spacesim.ship;

import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;

import java.util.Objects;

/**
 * One authoritative Stage-17.5E guided missile/interceptor body.
 *
 * <p>Guidance and seeker availability are capabilities of the body, not its existence. Destroying
 * guidance therefore never deletes the physical mass, geometry, position, velocity or residual
 * kinetic energy. Propulsion consumes real propellant, consumes the authored powered-burn lifetime
 * and changes velocity through the rocket equation.</p>
 *
 * @param bodyId stable simulation-local guided-body identity
 * @param sourceEntityId local identity of the launching entity
 * @param targetId current target hypothesis identity
 * @param definition immutable physical guided-weapon propulsion/seeker definition
 * @param materialId stable material content ID of the residual physical body
 * @param shape physical body shape category used by later impact processing
 * @param lengthM physical body length in meters
 * @param diameterM physical body diameter in meters
 * @param impactPayloadId optional stable warhead/impact-payload content seam; null means kinetic body only
 * @param xM current x position in meters
 * @param yM current y position in meters
 * @param velocityXMps current x velocity in meters per second
 * @param velocityYMps current y velocity in meters per second
 * @param remainingPropellantKg remaining onboard propellant in kilograms
 * @param remainingPoweredBurnSeconds remaining authored powered-burn lifetime in seconds
 * @param seekerAvailable whether the seeker can currently produce measurements
 * @param guidanceAvailable whether guidance/control can currently command maneuvers
 */
public record GuidedWeaponBody(
        long bodyId,
        long sourceEntityId,
        long targetId,
        GuidedWeapon definition,
        String materialId,
        ProjectileShape shape,
        double lengthM,
        double diameterM,
        String impactPayloadId,
        double xM,
        double yM,
        double velocityXMps,
        double velocityYMps,
        double remainingPropellantKg,
        double remainingPoweredBurnSeconds,
        boolean seekerAvailable,
        boolean guidanceAvailable) {
    private static final double EPSILON = 1e-9d;

    /**
     * Validates one authoritative guided physical body.
     *
     * @param bodyId stable simulation-local guided-body identity
     * @param sourceEntityId local identity of the launching entity
     * @param targetId current target hypothesis identity
     * @param definition immutable physical guided-weapon propulsion/seeker definition
     * @param materialId stable material content ID of the residual physical body
     * @param shape physical body shape category
     * @param lengthM physical body length in meters
     * @param diameterM physical body diameter in meters
     * @param impactPayloadId optional stable warhead/impact-payload content seam
     * @param xM current x position in meters
     * @param yM current y position in meters
     * @param velocityXMps current x velocity in meters per second
     * @param velocityYMps current y velocity in meters per second
     * @param remainingPropellantKg remaining onboard propellant in kilograms
     * @param remainingPoweredBurnSeconds remaining authored powered-burn lifetime in seconds
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
        requireNonBlank(materialId, "materialId");
        Objects.requireNonNull(shape, "shape");
        requirePositiveFinite(lengthM, "lengthM");
        requirePositiveFinite(diameterM, "diameterM");
        if (impactPayloadId != null && impactPayloadId.isBlank()) {
            throw new IllegalArgumentException("impactPayloadId must be null or non-blank");
        }
        requireFinite(xM, "xM");
        requireFinite(yM, "yM");
        requireFinite(velocityXMps, "velocityXMps");
        requireFinite(velocityYMps, "velocityYMps");
        requireNonNegativeFinite(remainingPropellantKg, "remainingPropellantKg");
        requireNonNegativeFinite(remainingPoweredBurnSeconds, "remainingPoweredBurnSeconds");
        if (remainingPropellantKg > definition.propellantMassKg() + EPSILON) {
            throw new IllegalArgumentException("remaining propellant exceeds authored capacity");
        }
        if (remainingPoweredBurnSeconds > definition.burnTimeSeconds() + EPSILON) {
            throw new IllegalArgumentException("remaining powered burn exceeds authored duration");
        }
    }

    /**
     * Creates a freshly launched body with full authored propellant and powered-burn lifetime.
     *
     * @param bodyId new guided-body identity
     * @param sourceEntityId launching entity identity
     * @param targetId target hypothesis identity
     * @param definition physical guided-weapon definition
     * @param materialId stable material content ID of the body
     * @param shape physical body shape category
     * @param lengthM physical body length in meters
     * @param diameterM physical body diameter in meters
     * @param impactPayloadId optional warhead/impact-payload content seam
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
            String materialId,
            ProjectileShape shape,
            double lengthM,
            double diameterM,
            String impactPayloadId,
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
                materialId,
                shape,
                lengthM,
                diameterM,
                impactPayloadId,
                xM,
                yM,
                velocityXMps,
                velocityYMps,
                checked.propellantMassKg(),
                checked.burnTimeSeconds(),
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

    /**
     * Returns physically deliverable remaining propulsion delta-v inside both fuel and burn-time limits.
     *
     * @return remaining deliverable delta-v in meters per second
     */
    public double remainingDeltaVMps() {
        if (remainingPropellantKg <= EPSILON || remainingPoweredBurnSeconds <= EPSILON) {
            return 0d;
        }
        double burnablePropellantKg = Math.min(
                remainingPropellantKg,
                definition.massFlowKgPerS() * remainingPoweredBurnSeconds);
        if (burnablePropellantKg <= EPSILON) {
            return 0d;
        }
        double finalMassKg = currentMassKg() - burnablePropellantKg;
        return definition.exhaustVelocityMps() * Math.log(currentMassKg() / finalMassKg);
    }

    /**
     * Returns the same physical body after loss of guidance/control capability.
     *
     * @return body with guidance disabled and all physical state preserved
     */
    public GuidedWeaponBody disableGuidance() {
        return copyWith(
                seekerAvailable,
                false,
                xM,
                yM,
                velocityXMps,
                velocityYMps,
                remainingPropellantKg,
                remainingPoweredBurnSeconds);
    }

    /**
     * Returns the same physical body after loss of seeker capability.
     *
     * @return body with seeker disabled and all physical state preserved
     */
    public GuidedWeaponBody disableSeeker() {
        return copyWith(
                false,
                guidanceAvailable,
                xM,
                yM,
                velocityXMps,
                velocityYMps,
                remainingPropellantKg,
                remainingPoweredBurnSeconds);
    }

    /**
     * Executes a propulsion burn in an explicitly supplied unit direction.
     *
     * <p>The method consumes only propellant and powered-burn lifetime physically available during
     * the requested interval and applies rocket-equation delta-v. It does not choose a guidance law;
     * Stage-17.5E guidance supplies the direction through the same player/AI-neutral path.</p>
     *
     * @param directionX x component of requested thrust direction
     * @param directionY y component of requested thrust direction
     * @param deltaSeconds positive commanded burn interval
     * @return body after physical propellant/burn-time consumption and velocity change
     */
    public GuidedWeaponBody burn(double directionX, double directionY, double deltaSeconds) {
        requireFinite(directionX, "directionX");
        requireFinite(directionY, "directionY");
        requirePositiveFinite(deltaSeconds, "deltaSeconds");
        if (!guidanceAvailable
                || remainingPropellantKg <= EPSILON
                || remainingPoweredBurnSeconds <= EPSILON) {
            return this;
        }
        double magnitude = Math.hypot(directionX, directionY);
        if (magnitude <= EPSILON) {
            throw new IllegalArgumentException("thrust direction must be non-zero");
        }
        double unitX = directionX / magnitude;
        double unitY = directionY / magnitude;
        double massFlow = definition.massFlowKgPerS();
        double fuelLimitedSeconds = remainingPropellantKg / massFlow;
        double actualBurnSeconds = Math.min(
                deltaSeconds,
                Math.min(remainingPoweredBurnSeconds, fuelLimitedSeconds));
        if (actualBurnSeconds <= EPSILON) {
            return this;
        }
        double consumed = massFlow * actualBurnSeconds;
        double initialMass = currentMassKg();
        double finalPropellant = Math.max(0d, remainingPropellantKg - consumed);
        double finalMass = definition.dryMassKg() + finalPropellant;
        double deltaV = definition.exhaustVelocityMps() * Math.log(initialMass / finalMass);
        return copyWith(
                seekerAvailable,
                guidanceAvailable,
                xM,
                yM,
                velocityXMps + unitX * deltaV,
                velocityYMps + unitY * deltaV,
                finalPropellant,
                Math.max(0d, remainingPoweredBurnSeconds - actualBurnSeconds));
    }

    /**
     * Advances the current body ballistically without inventing a guidance correction.
     *
     * @param deltaSeconds positive simulation interval
     * @return advanced body with unchanged velocity and subsystem/propulsion availability
     */
    public GuidedWeaponBody advanceBallistic(double deltaSeconds) {
        requirePositiveFinite(deltaSeconds, "deltaSeconds");
        return copyWith(
                seekerAvailable,
                guidanceAvailable,
                xM + velocityXMps * deltaSeconds,
                yM + velocityYMps * deltaSeconds,
                velocityXMps,
                velocityYMps,
                remainingPropellantKg,
                remainingPoweredBurnSeconds);
    }

    private GuidedWeaponBody copyWith(
            boolean nextSeekerAvailable,
            boolean nextGuidanceAvailable,
            double nextXM,
            double nextYM,
            double nextVelocityXMps,
            double nextVelocityYMps,
            double nextRemainingPropellantKg,
            double nextRemainingPoweredBurnSeconds) {
        return new GuidedWeaponBody(
                bodyId,
                sourceEntityId,
                targetId,
                definition,
                materialId,
                shape,
                lengthM,
                diameterM,
                impactPayloadId,
                nextXM,
                nextYM,
                nextVelocityXMps,
                nextVelocityYMps,
                nextRemainingPropellantKg,
                nextRemainingPoweredBurnSeconds,
                nextSeekerAvailable,
                nextGuidanceAvailable);
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
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
