package com.spacesim.ship;

import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;

import java.util.Objects;

/**
 * Deterministic Stage-17.5E guided-body navigation runtime over physical tracks and propulsion.
 *
 * <p>The runtime produces a thrust direction and bounded burn duration; it never resolves a missile
 * hit chance. An onboard seeker and a datalink are distinct information sources, and propulsion is
 * limited by the body's real remaining propellant/delta-v including its authored terminal reserve.</p>
 */
public final class GuidanceRuntime {
    private static final double EPSILON = 1e-9d;

    /** Source of the track used by the guidance command. */
    public enum TrackSource {
        /** Track is supplied by the weapon's own seeker and therefore requires a live seeker. */ ONBOARD_SEEKER,
        /** Track is supplied through an external sensor/datalink path. */ DATALINK
    }

    /** Stable reason why a guidance command cannot currently burn. */
    public enum Failure {
        /** Physical guidance command exists. */ NONE,
        /** Guidance/control hardware is unavailable. */ GUIDANCE_DISABLED,
        /** Onboard-seeker mode was requested after seeker loss. */ SEEKER_DISABLED,
        /** Track has no solved Cartesian position. */ TRACK_POSITION_UNKNOWN,
        /** Track quality is insufficient for guided interception. */ TRACK_QUALITY_INSUFFICIENT,
        /** No propellant/delta-v remains above the protected terminal reserve. */ TERMINAL_RESERVE_PROTECTED,
        /** No positive lead/intercept geometry can be estimated. */ NO_INTERCEPT_GEOMETRY
    }

    /**
     * One deterministic guidance command.
     *
     * @param allowed whether a propulsion command may execute
     * @param failure stable rejection reason or NONE
     * @param targetId target track identity
     * @param thrustDirectionX normalized x thrust direction
     * @param thrustDirectionY normalized y thrust direction
     * @param burnSeconds bounded physical burn duration
     * @param predictedInterceptSeconds nominal lead-pursuit intercept horizon
     * @param predictedAimXM predicted target x coordinate
     * @param predictedAimYM predicted target y coordinate
     */
    public record GuidanceCommand(
            boolean allowed,
            Failure failure,
            long targetId,
            double thrustDirectionX,
            double thrustDirectionY,
            double burnSeconds,
            double predictedInterceptSeconds,
            double predictedAimXM,
            double predictedAimYM) {
        /**
         * Validates one immutable guidance command.
         *
         * @param allowed whether a propulsion command may execute
         * @param failure stable rejection reason or NONE
         * @param targetId target track identity
         * @param thrustDirectionX normalized x thrust direction
         * @param thrustDirectionY normalized y thrust direction
         * @param burnSeconds bounded physical burn duration
         * @param predictedInterceptSeconds nominal intercept horizon
         * @param predictedAimXM predicted target x coordinate
         * @param predictedAimYM predicted target y coordinate
         */
        public GuidanceCommand {
            Objects.requireNonNull(failure, "failure");
            if (targetId <= 0L) {
                throw new IllegalArgumentException("targetId must be positive");
            }
            requireFinite(thrustDirectionX, "thrustDirectionX");
            requireFinite(thrustDirectionY, "thrustDirectionY");
            requireNonNegativeFinite(burnSeconds, "burnSeconds");
            requireNonNegativeFinite(predictedInterceptSeconds, "predictedInterceptSeconds");
            requireFinite(predictedAimXM, "predictedAimXM");
            requireFinite(predictedAimYM, "predictedAimYM");
            if (allowed != (failure == Failure.NONE)) {
                throw new IllegalArgumentException("allowed and failure must agree");
            }
            if (allowed) {
                double directionMagnitude = Math.hypot(thrustDirectionX, thrustDirectionY);
                if (Math.abs(directionMagnitude - 1d) > 1e-9d || burnSeconds <= 0d) {
                    throw new IllegalArgumentException("allowed guidance requires unit direction and positive burn");
                }
            }
        }
    }

    /**
     * Plans a bounded lead-pursuit propulsion command.
     *
     * <p>The nominal intercept horizon is a deterministic navigation estimate, not a hit decision.
     * The actual body still propagates and can miss. The command cannot consume delta-v protected by
     * {@code terminalReserveMps}.</p>
     *
     * @param body current physical guided body
     * @param track current target track
     * @param targetMotion explicit target velocity/uncertainty estimate
     * @param source track information source
     * @param requestedBurnSeconds maximum burn requested for this simulation step
     * @return bounded deterministic guidance command
     */
    public GuidanceCommand planLeadPursuit(
            GuidedWeaponBody body,
            TrackState track,
            TargetMotionEstimate targetMotion,
            TrackSource source,
            double requestedBurnSeconds) {
        GuidedWeaponBody checkedBody = Objects.requireNonNull(body, "body");
        TrackState checkedTrack = Objects.requireNonNull(track, "track");
        TargetMotionEstimate checkedMotion = Objects.requireNonNull(targetMotion, "targetMotion");
        TrackSource checkedSource = Objects.requireNonNull(source, "source");
        requirePositiveFinite(requestedBurnSeconds, "requestedBurnSeconds");
        if (!checkedBody.guidanceAvailable()) {
            return rejected(checkedTrack.targetId(), Failure.GUIDANCE_DISABLED);
        }
        if (checkedSource == TrackSource.ONBOARD_SEEKER && !checkedBody.seekerAvailable()) {
            return rejected(checkedTrack.targetId(), Failure.SEEKER_DISABLED);
        }
        if (!checkedTrack.positionKnown()) {
            return rejected(checkedTrack.targetId(), Failure.TRACK_POSITION_UNKNOWN);
        }
        if (checkedTrack.informationState() != InformationState.TRACKED
                && checkedTrack.informationState() != InformationState.FIRE_CONTROL) {
            return rejected(checkedTrack.targetId(), Failure.TRACK_QUALITY_INSUFFICIENT);
        }
        double remainingDeltaV = checkedBody.remainingDeltaVMps();
        double burnableDeltaV = remainingDeltaV - checkedBody.definition().terminalReserveMps();
        if (burnableDeltaV <= EPSILON || checkedBody.remainingPropellantKg() <= EPSILON) {
            return rejected(checkedTrack.targetId(), Failure.TERMINAL_RESERVE_PROTECTED);
        }

        double relativeX = checkedTrack.estimatedXM() - checkedBody.xM();
        double relativeY = checkedTrack.estimatedYM() - checkedBody.yM();
        double targetRelativeVx = checkedMotion.velocityXMps() - checkedBody.velocityXMps();
        double targetRelativeVy = checkedMotion.velocityYMps() - checkedBody.velocityYMps();
        double nominalSpeed = Math.max(1d, checkedBody.speedMps() + 0.5d * burnableDeltaV);
        double interceptSeconds = positiveInterceptTime(relativeX, relativeY, targetRelativeVx, targetRelativeVy, nominalSpeed);
        if (!Double.isFinite(interceptSeconds)) {
            return rejected(checkedTrack.targetId(), Failure.NO_INTERCEPT_GEOMETRY);
        }
        double aimX = checkedTrack.estimatedXM() + checkedMotion.velocityXMps() * interceptSeconds;
        double aimY = checkedTrack.estimatedYM() + checkedMotion.velocityYMps() * interceptSeconds;
        double aimDx = aimX - checkedBody.xM();
        double aimDy = aimY - checkedBody.yM();
        double aimMagnitude = Math.hypot(aimDx, aimDy);
        if (aimMagnitude <= EPSILON) {
            return rejected(checkedTrack.targetId(), Failure.NO_INTERCEPT_GEOMETRY);
        }

        double maximumReserveSafeBurn = burnDurationForDeltaV(checkedBody, burnableDeltaV);
        double burnSeconds = Math.min(requestedBurnSeconds, maximumReserveSafeBurn);
        if (burnSeconds <= EPSILON) {
            return rejected(checkedTrack.targetId(), Failure.TERMINAL_RESERVE_PROTECTED);
        }
        return new GuidanceCommand(
                true,
                Failure.NONE,
                checkedTrack.targetId(),
                aimDx / aimMagnitude,
                aimDy / aimMagnitude,
                burnSeconds,
                interceptSeconds,
                aimX,
                aimY);
    }

    /**
     * Executes a previously accepted guidance command against the same physical body.
     *
     * @param body current physical guided body
     * @param command accepted guidance command
     * @return body after real propellant consumption and velocity change
     */
    public GuidedWeaponBody execute(GuidedWeaponBody body, GuidanceCommand command) {
        GuidedWeaponBody checkedBody = Objects.requireNonNull(body, "body");
        GuidanceCommand checkedCommand = Objects.requireNonNull(command, "command");
        if (!checkedCommand.allowed()) {
            throw new IllegalArgumentException("cannot execute rejected guidance command");
        }
        if (checkedBody.targetId() != checkedCommand.targetId()) {
            throw new IllegalArgumentException("guidance command target does not match body target");
        }
        return checkedBody.burn(
                checkedCommand.thrustDirectionX(),
                checkedCommand.thrustDirectionY(),
                checkedCommand.burnSeconds());
    }

    private static double burnDurationForDeltaV(GuidedWeaponBody body, double deltaVMps) {
        double initialMass = body.currentMassKg();
        double finalMassForBudget = initialMass / Math.exp(deltaVMps / body.definition().exhaustVelocityMps());
        double propellantBudgetKg = Math.min(
                body.remainingPropellantKg(),
                Math.max(0d, initialMass - finalMassForBudget));
        return propellantBudgetKg / body.definition().massFlowKgPerS();
    }

    private static double positiveInterceptTime(
            double rx,
            double ry,
            double rvx,
            double rvy,
            double speed) {
        double a = rvx * rvx + rvy * rvy - speed * speed;
        double b = 2d * (rx * rvx + ry * rvy);
        double c = rx * rx + ry * ry;
        if (Math.abs(a) <= 1e-12d) {
            if (Math.abs(b) <= 1e-12d) {
                return Double.NaN;
            }
            double result = -c / b;
            return result > EPSILON ? result : Double.NaN;
        }
        double discriminant = b * b - 4d * a * c;
        if (discriminant < 0d) {
            return Double.NaN;
        }
        double root = Math.sqrt(Math.max(0d, discriminant));
        double first = (-b - root) / (2d * a);
        double second = (-b + root) / (2d * a);
        double best = Double.POSITIVE_INFINITY;
        if (first > EPSILON) {
            best = first;
        }
        if (second > EPSILON && second < best) {
            best = second;
        }
        return Double.isFinite(best) ? best : Double.NaN;
    }

    private static GuidanceCommand rejected(long targetId, Failure failure) {
        return new GuidanceCommand(false, failure, targetId, 0d, 0d, 0d, 0d, 0d, 0d);
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
