package com.spacesim.ship;

import java.util.Objects;

/**
 * Deterministic Stage-19 tactical formation policy over one authored cross-axis line objective.
 *
 * <p>The planner is deliberately geometry/policy only. It never mutates transforms, grants thrust,
 * consumes reaction mass or reads hostile state. It converts the actor's own position/velocity and an
 * authored formation objective into a normalized cross-axis correction. Physical execution remains
 * owned by the shared engineering and {@link com.spacesim.flight.FlightDynamics} path.</p>
 */
public final class TacticalFormationPlanner {
    private static final double EPSILON = 1e-9d;

    /** Authored spacing family used by Stage-19 scenario variants. */
    public enum FormationMode {
        /** Close line spacing for a compact acceptance formation. */ COMPACT,
        /** Wider line spacing for a dispersed acceptance formation. */ DISPERSED
    }

    /** Observable state of one actor relative to its authored formation slot. */
    public enum FormationStatus {
        /** No formation objective is assigned to this actor. */ NO_OBJECTIVE,
        /** Actor is inside the authored slot tolerance with bounded lateral motion. */ KEEPING,
        /** Actor is physically correcting or braking toward its slot. */ RECOVERING,
        /** Formation is materially broken or a higher-priority survival action overrides it. */ BROKEN
    }

    /** Stable diagnostic reason for the current formation state. */
    public enum FormationReason {
        /** No objective exists for the actor's side. */ NO_OBJECTIVE,
        /** Slot error and lateral motion are within the authored tolerance. */ IN_TOLERANCE,
        /** Actor is correcting a recoverable slot error. */ SLOT_ERROR,
        /** Actor exceeded the authored break distance and is recovering physically. */ LARGE_SLOT_ERROR,
        /** Retreat/disengagement/pursuit policy currently owns maneuver authority. */ SURVIVAL_OVERRIDE,
        /** Physical propulsion cannot create the requested formation correction. */ CANNOT_MANEUVER
    }

    /**
     * Authored line-abreast formation objective.
     *
     * <p>The objective controls only the world-Y cross axis. World-X remains available to production
     * range-control/intercept policy, matching the current Stage-19 acceptance geometry where opposing
     * fleets close primarily along X. All distances are explicit physical scenario values rather than
     * doctrine bonuses.</p>
     *
     * @param mode scenario formation variant
     * @param centerYM authored center of the line in meters
     * @param spacingM center-to-center slot spacing in meters
     * @param slotToleranceM allowed absolute cross-axis slot error in meters
     * @param breakDistanceM error above which formation is observably broken
     */
    public record Objective(
            FormationMode mode,
            double centerYM,
            double spacingM,
            double slotToleranceM,
            double breakDistanceM) {
        /** Validates explicit physical formation geometry. */
        public Objective {
            Objects.requireNonNull(mode, "mode");
            requireFinite(centerYM, "centerYM");
            requirePositive(spacingM, "spacingM");
            requirePositive(slotToleranceM, "slotToleranceM");
            requirePositive(breakDistanceM, "breakDistanceM");
            if (breakDistanceM <= slotToleranceM) {
                throw new IllegalArgumentException("breakDistanceM must exceed slotToleranceM");
            }
        }
    }

    /**
     * Immutable actor-local formation command before production flight execution.
     *
     * @param objectiveKnown whether a side objective exists
     * @param mode formation mode, or {@code null} when no objective exists
     * @param slotIndex zero-based stable slot index
     * @param slotCount number of ships assigned to the line
     * @param desiredYM authored slot center on the cross axis
     * @param errorM signed desired-minus-current cross-axis error
     * @param status observable formation state
     * @param reason diagnostic reason for that state
     * @param correctionAxisY normalized cross-axis acceleration request in [-1, 1]
     */
    public record Command(
            boolean objectiveKnown,
            FormationMode mode,
            int slotIndex,
            int slotCount,
            double desiredYM,
            double errorM,
            FormationStatus status,
            FormationReason reason,
            double correctionAxisY) {
        /** Validates one immutable command. */
        public Command {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
            requireFinite(desiredYM, "desiredYM");
            requireFinite(errorM, "errorM");
            requireFinite(correctionAxisY, "correctionAxisY");
            if (Math.abs(correctionAxisY) > 1d + 1e-12d) {
                throw new IllegalArgumentException("correctionAxisY must be normalized");
            }
            if (!objectiveKnown) {
                if (mode != null || slotIndex != -1 || slotCount != 0 || desiredYM != 0d || errorM != 0d
                        || status != FormationStatus.NO_OBJECTIVE
                        || reason != FormationReason.NO_OBJECTIVE
                        || correctionAxisY != 0d) {
                    throw new IllegalArgumentException("unknown objective must use canonical no-objective state");
                }
            } else {
                Objects.requireNonNull(mode, "mode");
                if (slotCount <= 0 || slotIndex < 0 || slotIndex >= slotCount) {
                    throw new IllegalArgumentException("formation slot must be inside the assigned roster");
                }
            }
        }

        /** @return canonical command for an actor with no authored formation objective */
        public static Command none() {
            return new Command(
                    false,
                    null,
                    -1,
                    0,
                    0d,
                    0d,
                    FormationStatus.NO_OBJECTIVE,
                    FormationReason.NO_OBJECTIVE,
                    0d);
        }
    }

    /**
     * Plans one cross-axis formation correction from actor-local kinematics only.
     *
     * <p>Braking is derived from current lateral speed and available acceleration using
     * {@code v^2/(2a)}. This avoids an arbitrary velocity threshold and bounds repeated sign-flip
     * correction around the slot. A survival override never receives formation thrust.</p>
     *
     * @param objective authored side objective
     * @param slotIndex stable zero-based actor slot
     * @param slotCount number of assigned actors
     * @param actorYM actor's own current cross-axis position
     * @param actorVelocityYMps actor's own current cross-axis velocity
     * @param accelerationMps2 current physically derived acceleration capability
     * @param formationControlAllowed false when a higher-priority survival maneuver owns control
     * @return deterministic formation command
     */
    public Command plan(
            Objective objective,
            int slotIndex,
            int slotCount,
            double actorYM,
            double actorVelocityYMps,
            double accelerationMps2,
            boolean formationControlAllowed) {
        Objective checked = Objects.requireNonNull(objective, "objective");
        if (slotCount <= 0 || slotIndex < 0 || slotIndex >= slotCount) {
            throw new IllegalArgumentException("formation slot must be inside the assigned roster");
        }
        requireFinite(actorYM, "actorYM");
        requireFinite(actorVelocityYMps, "actorVelocityYMps");
        requireNonNegative(accelerationMps2, "accelerationMps2");

        double centeredIndex = slotIndex - (slotCount - 1d) / 2d;
        double desiredY = checked.centerYM() + centeredIndex * checked.spacingM();
        double error = desiredY - actorYM;
        double distance = Math.abs(error);

        if (!formationControlAllowed) {
            return command(
                    checked,
                    slotIndex,
                    slotCount,
                    desiredY,
                    error,
                    FormationStatus.BROKEN,
                    FormationReason.SURVIVAL_OVERRIDE,
                    0d);
        }
        if (accelerationMps2 <= EPSILON) {
            FormationStatus status = distance <= checked.slotToleranceM()
                    ? FormationStatus.KEEPING
                    : FormationStatus.BROKEN;
            FormationReason reason = distance <= checked.slotToleranceM()
                    ? FormationReason.IN_TOLERANCE
                    : FormationReason.CANNOT_MANEUVER;
            return command(checked, slotIndex, slotCount, desiredY, error, status, reason, 0d);
        }

        double brakingDistance = actorVelocityYMps * actorVelocityYMps / (2d * accelerationMps2);
        boolean movingTowardSlot = error * actorVelocityYMps > 0d;
        double remainingToTolerance = Math.max(0d, distance - checked.slotToleranceM());
        double correction;

        if (distance <= checked.slotToleranceM()) {
            double remainingInsideTolerance = Math.max(0d, checked.slotToleranceM() - distance);
            if (Math.abs(actorVelocityYMps) > EPSILON && brakingDistance >= remainingInsideTolerance) {
                correction = -Math.signum(actorVelocityYMps);
                return command(
                        checked,
                        slotIndex,
                        slotCount,
                        desiredY,
                        error,
                        FormationStatus.RECOVERING,
                        FormationReason.SLOT_ERROR,
                        correction);
            }
            return command(
                    checked,
                    slotIndex,
                    slotCount,
                    desiredY,
                    error,
                    FormationStatus.KEEPING,
                    FormationReason.IN_TOLERANCE,
                    0d);
        }

        if (movingTowardSlot && brakingDistance >= remainingToTolerance) {
            correction = -Math.signum(actorVelocityYMps);
        } else {
            correction = Math.signum(error);
        }
        FormationStatus status = distance > checked.breakDistanceM()
                ? FormationStatus.BROKEN
                : FormationStatus.RECOVERING;
        FormationReason reason = status == FormationStatus.BROKEN
                ? FormationReason.LARGE_SLOT_ERROR
                : FormationReason.SLOT_ERROR;
        return command(checked, slotIndex, slotCount, desiredY, error, status, reason, correction);
    }

    private static Command command(
            Objective objective,
            int slotIndex,
            int slotCount,
            double desiredY,
            double error,
            FormationStatus status,
            FormationReason reason,
            double correction) {
        return new Command(
                true,
                objective.mode(),
                slotIndex,
                slotCount,
                desiredY,
                error,
                status,
                reason,
                correction);
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
