package com.spacesim.ship;

import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponDefinition.KineticRound;

import java.util.Objects;

/**
 * Stage-17.5E deterministic fire-control mathematics over Stage-17.5D track state.
 *
 * <p>The solver returns geometry, time of flight and uncertainty envelopes. It never returns an
 * authoritative hit percentage and it has no arbitrary weapon-range wall. Whether the resulting
 * physical body later intersects a target is resolved by trajectory/geometry and Stage-17.5F
 * material/damage logic.</p>
 */
public final class WeaponFireControl {
    private static final double EPSILON = 1e-12d;

    /** Stable physical reason why a requested kinetic solution cannot currently be formed. */
    public enum FireFailure {
        /** A physical solution exists. */ NONE,
        /** The supplied track has no solved Cartesian position/range. */ TRACK_POSITION_UNKNOWN,
        /** The supplied track has not reached a tactical tracking state. */ TRACK_QUALITY_INSUFFICIENT,
        /** Constant-velocity intercept geometry has no positive-time solution. */ NO_INTERCEPT
    }

    /**
     * Two-dimensional inertial state of the firing platform.
     *
     * @param xM current x position in meters
     * @param yM current y position in meters
     * @param velocityXMps current x velocity in meters per second
     * @param velocityYMps current y velocity in meters per second
     */
    public record KinematicState(double xM, double yM, double velocityXMps, double velocityYMps) {
        /**
         * Validates one finite inertial state.
         *
         * @param xM current x position in meters
         * @param yM current y position in meters
         * @param velocityXMps current x velocity in meters per second
         * @param velocityYMps current y velocity in meters per second
         */
        public KinematicState {
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            requireFinite(velocityXMps, "velocityXMps");
            requireFinite(velocityYMps, "velocityYMps");
        }
    }

    /**
     * Weapon-side motion estimate associated with an existing target track.
     *
     * <p>Velocity is not silently invented from position-only track data. The caller must provide
     * the current estimated target velocity and its uncertainty/maneuver envelope explicitly.</p>
     *
     * @param velocityXMps estimated target x velocity in meters per second
     * @param velocityYMps estimated target y velocity in meters per second
     * @param oneSigmaVelocityMps scalar one-sigma velocity-estimate uncertainty
     * @param maneuverAccelerationMps2 bounded target maneuver acceleration used for an envelope, not a hit chance
     */
    public record TargetMotionEstimate(
            double velocityXMps,
            double velocityYMps,
            double oneSigmaVelocityMps,
            double maneuverAccelerationMps2) {
        /**
         * Validates explicit motion/uncertainty inputs.
         *
         * @param velocityXMps estimated target x velocity in meters per second
         * @param velocityYMps estimated target y velocity in meters per second
         * @param oneSigmaVelocityMps scalar one-sigma velocity-estimate uncertainty
         * @param maneuverAccelerationMps2 bounded target maneuver acceleration
         */
        public TargetMotionEstimate {
            requireFinite(velocityXMps, "velocityXMps");
            requireFinite(velocityYMps, "velocityYMps");
            requireNonNegativeFinite(oneSigmaVelocityMps, "oneSigmaVelocityMps");
            requireNonNegativeFinite(maneuverAccelerationMps2, "maneuverAccelerationMps2");
        }
    }

    /**
     * Deterministic kinetic fire solution without hit-probability abstraction.
     *
     * @param allowed whether a physical intercept solution exists
     * @param failure stable rejection reason or NONE
     * @param targetId target track identity
     * @param timeOfFlightSeconds nominal constant-velocity time of flight
     * @param aimXM predicted target x coordinate used by the nominal shot
     * @param aimYM predicted target y coordinate used by the nominal shot
     * @param projectileVelocityXMps inertial projectile x velocity at muzzle exit
     * @param projectileVelocityYMps inertial projectile y velocity at muzzle exit
     * @param oneSigmaAimUncertaintyM propagated track/velocity/pointing one-sigma spatial uncertainty
     * @param maneuverEnvelopeRadiusM additional bounded target-maneuver displacement envelope
     */
    public record KineticFireSolution(
            boolean allowed,
            FireFailure failure,
            long targetId,
            double timeOfFlightSeconds,
            double aimXM,
            double aimYM,
            double projectileVelocityXMps,
            double projectileVelocityYMps,
            double oneSigmaAimUncertaintyM,
            double maneuverEnvelopeRadiusM) {
        /**
         * Validates one immutable fire-control result.
         *
         * @param allowed whether a physical intercept solution exists
         * @param failure stable rejection reason or NONE
         * @param targetId target track identity
         * @param timeOfFlightSeconds nominal time of flight
         * @param aimXM predicted target x coordinate
         * @param aimYM predicted target y coordinate
         * @param projectileVelocityXMps inertial projectile x velocity
         * @param projectileVelocityYMps inertial projectile y velocity
         * @param oneSigmaAimUncertaintyM propagated one-sigma spatial uncertainty
         * @param maneuverEnvelopeRadiusM target-maneuver displacement envelope
         */
        public KineticFireSolution {
            Objects.requireNonNull(failure, "failure");
            if (targetId <= 0L) {
                throw new IllegalArgumentException("targetId must be positive");
            }
            requireNonNegativeFinite(timeOfFlightSeconds, "timeOfFlightSeconds");
            requireFinite(aimXM, "aimXM");
            requireFinite(aimYM, "aimYM");
            requireFinite(projectileVelocityXMps, "projectileVelocityXMps");
            requireFinite(projectileVelocityYMps, "projectileVelocityYMps");
            requireNonNegativeFinite(oneSigmaAimUncertaintyM, "oneSigmaAimUncertaintyM");
            requireNonNegativeFinite(maneuverEnvelopeRadiusM, "maneuverEnvelopeRadiusM");
            if (allowed != (failure == FireFailure.NONE)) {
                throw new IllegalArgumentException("allowed and failure must agree");
            }
            if (allowed && timeOfFlightSeconds <= 0d) {
                throw new IllegalArgumentException("allowed solution requires positive time of flight");
            }
        }
    }

    /**
     * Plans a kinetic shot against the current target track.
     *
     * <p>The nominal lead solution assumes constant estimated target velocity. Track age, velocity
     * uncertainty, pointing jitter and a bounded maneuver acceleration are propagated into explicit
     * spatial envelopes. They are not collapsed into {@code weaponAccuracy} or a hit chance.</p>
     *
     * @param round physical kinetic round
     * @param track Stage-17.5D target track
     * @param shooter firing-platform inertial state
     * @param targetMotion explicit target velocity/uncertainty estimate
     * @param pointingJitterRad one-sigma launcher pointing uncertainty in radians
     * @param nowSeconds authoritative current simulation time
     * @return deterministic physical fire solution or stable rejection reason
     */
    public KineticFireSolution planKinetic(
            KineticRound round,
            TrackState track,
            KinematicState shooter,
            TargetMotionEstimate targetMotion,
            double pointingJitterRad,
            double nowSeconds) {
        KineticRound checkedRound = Objects.requireNonNull(round, "round");
        TrackState checkedTrack = Objects.requireNonNull(track, "track");
        KinematicState checkedShooter = Objects.requireNonNull(shooter, "shooter");
        TargetMotionEstimate checkedMotion = Objects.requireNonNull(targetMotion, "targetMotion");
        requireNonNegativeFinite(pointingJitterRad, "pointingJitterRad");
        double trackAge = checkedTrack.ageSeconds(nowSeconds);

        if (!checkedTrack.positionKnown()) {
            return rejected(checkedTrack.targetId(), FireFailure.TRACK_POSITION_UNKNOWN);
        }
        if (checkedTrack.informationState() != InformationState.TRACKED
                && checkedTrack.informationState() != InformationState.FIRE_CONTROL) {
            return rejected(checkedTrack.targetId(), FireFailure.TRACK_QUALITY_INSUFFICIENT);
        }

        double relativeXM = checkedTrack.estimatedXM() - checkedShooter.xM();
        double relativeYM = checkedTrack.estimatedYM() - checkedShooter.yM();
        double relativeVelocityXMps = checkedMotion.velocityXMps() - checkedShooter.velocityXMps();
        double relativeVelocityYMps = checkedMotion.velocityYMps() - checkedShooter.velocityYMps();
        double time = interceptTime(
                relativeXM,
                relativeYM,
                relativeVelocityXMps,
                relativeVelocityYMps,
                checkedRound.muzzleVelocityMps());
        if (!(time > 0d) || !Double.isFinite(time)) {
            return rejected(checkedTrack.targetId(), FireFailure.NO_INTERCEPT);
        }

        double interceptRelativeX = relativeXM + relativeVelocityXMps * time;
        double interceptRelativeY = relativeYM + relativeVelocityYMps * time;
        double pathLengthM = Math.hypot(interceptRelativeX, interceptRelativeY);
        if (!(pathLengthM > 0d)) {
            return rejected(checkedTrack.targetId(), FireFailure.NO_INTERCEPT);
        }
        double directionX = interceptRelativeX / pathLengthM;
        double directionY = interceptRelativeY / pathLengthM;
        double projectileVelocityX = checkedShooter.velocityXMps() + directionX * checkedRound.muzzleVelocityMps();
        double projectileVelocityY = checkedShooter.velocityYMps() + directionY * checkedRound.muzzleVelocityMps();
        double aimX = checkedTrack.estimatedXM() + checkedMotion.velocityXMps() * time;
        double aimY = checkedTrack.estimatedYM() + checkedMotion.velocityYMps() * time;

        double predictionHorizon = trackAge + time;
        double positionVariance = checkedTrack.covariance().positionVarianceM2();
        double velocitySigmaM = checkedMotion.oneSigmaVelocityMps() * predictionHorizon;
        double pointingSigmaM = pointingJitterRad * pathLengthM;
        double oneSigmaM = Math.sqrt(
                positionVariance
                        + velocitySigmaM * velocitySigmaM
                        + pointingSigmaM * pointingSigmaM);
        double maneuverEnvelopeM = 0.5d
                * checkedMotion.maneuverAccelerationMps2()
                * predictionHorizon
                * predictionHorizon;

        return new KineticFireSolution(
                true,
                FireFailure.NONE,
                checkedTrack.targetId(),
                time,
                aimX,
                aimY,
                projectileVelocityX,
                projectileVelocityY,
                oneSigmaM,
                maneuverEnvelopeM);
    }

    /**
     * Materializes an accepted kinetic solution as one independent physical body.
     *
     * @param projectileId new simulation-local projectile identity
     * @param sourceEntityId firing entity local identity
     * @param spawnTick deterministic simulation tick at muzzle exit
     * @param round physical round definition
     * @param shooter firing-platform state at muzzle exit
     * @param solution previously accepted solution
     * @return authoritative projectile body
     */
    public ProjectileBody materializeKineticProjectile(
            long projectileId,
            long sourceEntityId,
            long spawnTick,
            KineticRound round,
            KinematicState shooter,
            KineticFireSolution solution) {
        KineticRound checkedRound = Objects.requireNonNull(round, "round");
        KinematicState checkedShooter = Objects.requireNonNull(shooter, "shooter");
        KineticFireSolution checkedSolution = Objects.requireNonNull(solution, "solution");
        if (spawnTick < 0L) {
            throw new IllegalArgumentException("spawnTick must be non-negative");
        }
        if (!checkedSolution.allowed()) {
            throw new IllegalArgumentException("cannot materialize a rejected fire solution");
        }
        return new ProjectileBody(
                projectileId,
                sourceEntityId,
                spawnTick,
                checkedRound.materialId(),
                checkedRound.shape(),
                checkedRound.lengthM(),
                checkedRound.diameterM(),
                checkedRound.massKg(),
                checkedShooter.xM(),
                checkedShooter.yM(),
                checkedSolution.projectileVelocityXMps(),
                checkedSolution.projectileVelocityYMps());
    }

    private static double interceptTime(
            double relativeXM,
            double relativeYM,
            double relativeVelocityXMps,
            double relativeVelocityYMps,
            double projectileSpeedMps) {
        double a = relativeVelocityXMps * relativeVelocityXMps
                + relativeVelocityYMps * relativeVelocityYMps
                - projectileSpeedMps * projectileSpeedMps;
        double b = 2d * (relativeXM * relativeVelocityXMps + relativeYM * relativeVelocityYMps);
        double c = relativeXM * relativeXM + relativeYM * relativeYM;
        if (Math.abs(a) <= EPSILON) {
            if (Math.abs(b) <= EPSILON) {
                return Double.NaN;
            }
            double linear = -c / b;
            return linear > EPSILON ? linear : Double.NaN;
        }
        double discriminant = b * b - 4d * a * c;
        if (discriminant < 0d) {
            return Double.NaN;
        }
        double root = Math.sqrt(Math.max(0d, discriminant));
        double t1 = (-b - root) / (2d * a);
        double t2 = (-b + root) / (2d * a);
        double best = Double.POSITIVE_INFINITY;
        if (t1 > EPSILON) {
            best = t1;
        }
        if (t2 > EPSILON && t2 < best) {
            best = t2;
        }
        return Double.isFinite(best) ? best : Double.NaN;
    }

    private static KineticFireSolution rejected(long targetId, FireFailure failure) {
        return new KineticFireSolution(false, failure, targetId, 0d, 0d, 0d, 0d, 0d, 0d, 0d);
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
