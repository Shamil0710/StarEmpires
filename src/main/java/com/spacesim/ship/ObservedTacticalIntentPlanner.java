package com.spacesim.ship;

import com.spacesim.ship.ObservedThreatAssessmentService.Assessment;
import com.spacesim.ship.ObservedThreatAssessmentService.ContactDisposition;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;

import java.util.List;
import java.util.Objects;

/**
 * Converts actor-visible Stage-19A threat information into deterministic tactical intent.
 *
 * <p>The planner is deliberately pure. It does not read ECS/world state and does not integrate
 * motion, consume reaction mass, fire weapons or resolve damage. It chooses a target and normalized
 * movement/fire intent from the supplied production {@link TrackState} view. Physical execution
 * remains owned by the existing flight and combat runtimes.</p>
 */
public final class ObservedTacticalIntentPlanner {
    /** Mission-level tactical posture supplied by higher-level fleet behavior. */
    public enum TacticalPosture {
        /** Remain at the current position while maintaining observed threat awareness. */
        HOLD,
        /** Close on the highest-priority observed contact using its current estimated position. */
        INTERCEPT,
        /** Occupy a screening point between a protected point and the highest-priority contact. */
        SCREEN
    }

    /**
     * Immutable actor-local tactical context.
     *
     * @param posture requested mission posture
     * @param actorXM actor's own known x position in meters
     * @param actorYM actor's own known y position in meters
     * @param protectedPointKnown whether a protected point is available to SCREEN behavior
     * @param protectedXM protected x position or canonical zero when unknown
     * @param protectedYM protected y position or canonical zero when unknown
     * @param screenRadiusM desired physical offset from the protected point toward an observed threat
     * @param nowSeconds authoritative current simulation time
     * @param tacticalReferenceRangeM positive Stage-19A range normalization scale
     * @param freshnessReferenceSeconds positive Stage-19A freshness normalization scale
     */
    public record TacticalContext(
            TacticalPosture posture,
            double actorXM,
            double actorYM,
            boolean protectedPointKnown,
            double protectedXM,
            double protectedYM,
            double screenRadiusM,
            double nowSeconds,
            double tacticalReferenceRangeM,
            double freshnessReferenceSeconds) {
        /**
         * Validates explicit known/unknown geometry and positive normalization scales.
         *
         * @param posture requested mission posture
         * @param actorXM actor x position
         * @param actorYM actor y position
         * @param protectedPointKnown whether the protected point is known
         * @param protectedXM protected x position or canonical zero
         * @param protectedYM protected y position or canonical zero
         * @param screenRadiusM non-negative desired screening offset
         * @param nowSeconds authoritative current simulation time
         * @param tacticalReferenceRangeM positive tactical range scale
         * @param freshnessReferenceSeconds positive track freshness scale
         */
        public TacticalContext {
            Objects.requireNonNull(posture, "posture");
            requireFinite(actorXM, "actorXM");
            requireFinite(actorYM, "actorYM");
            requireFinite(protectedXM, "protectedXM");
            requireFinite(protectedYM, "protectedYM");
            if (!protectedPointKnown && (protectedXM != 0d || protectedYM != 0d)) {
                throw new IllegalArgumentException(
                        "unknown protected point must use canonical zero coordinates");
            }
            requireNonNegativeFinite(screenRadiusM, "screenRadiusM");
            requireFinite(nowSeconds, "nowSeconds");
            requirePositiveFinite(tacticalReferenceRangeM, "tacticalReferenceRangeM");
            requirePositiveFinite(freshnessReferenceSeconds, "freshnessReferenceSeconds");
            if (posture == TacticalPosture.SCREEN && !protectedPointKnown) {
                throw new IllegalArgumentException("SCREEN posture requires a known protected point");
            }
        }
    }

    /**
     * Immutable command-level tactical intent before physical execution.
     *
     * @param posture mission posture that produced this intent
     * @param targetSelected whether an observed contact was selected
     * @param targetId selected stable target ID, or canonical zero when no target exists
     * @param movementAxisX normalized horizontal movement intent
     * @param movementAxisY normalized vertical movement intent
     * @param fireRequested whether known-hostile track quality permits a fire request
     * @param observedPriority Stage-19A behavioral priority of the selected target, or zero
     */
    public record TacticalIntent(
            TacticalPosture posture,
            boolean targetSelected,
            long targetId,
            double movementAxisX,
            double movementAxisY,
            boolean fireRequested,
            double observedPriority) {
        /**
         * Validates canonical no-target state and normalized movement axes.
         *
         * @param posture mission posture
         * @param targetSelected whether a target exists
         * @param targetId target ID or canonical zero
         * @param movementAxisX normalized horizontal movement intent
         * @param movementAxisY normalized vertical movement intent
         * @param fireRequested whether firing is requested
         * @param observedPriority selected target priority or zero
         */
        public TacticalIntent {
            Objects.requireNonNull(posture, "posture");
            if (targetSelected != (targetId > 0L)) {
                throw new IllegalArgumentException("targetSelected must match positive targetId");
            }
            requireFinite(movementAxisX, "movementAxisX");
            requireFinite(movementAxisY, "movementAxisY");
            double lengthSquared = movementAxisX * movementAxisX + movementAxisY * movementAxisY;
            if (lengthSquared > 1d + 1e-12d) {
                throw new IllegalArgumentException("movement intent must be normalized");
            }
            requireNonNegativeFinite(observedPriority, "observedPriority");
            if (!targetSelected && (fireRequested || observedPriority != 0d)) {
                throw new IllegalArgumentException("no-target intent cannot fire or retain priority");
            }
        }

        /**
         * Creates a deterministic no-target hold result.
         *
         * @param posture requested mission posture
         * @return canonical empty tactical intent
         */
        public static TacticalIntent noTarget(TacticalPosture posture) {
            return new TacticalIntent(posture, false, 0L, 0d, 0d, false, 0d);
        }
    }

    private final ObservedThreatAssessmentService threatAssessmentService;

    /** Creates a planner using the production Stage-19A threat assessment service. */
    public ObservedTacticalIntentPlanner() {
        this(new ObservedThreatAssessmentService());
    }

    /**
     * Creates a planner with an explicit assessment dependency for deterministic composition.
     *
     * @param threatAssessmentService Stage-19A actor-bounded threat assessment service
     */
    public ObservedTacticalIntentPlanner(ObservedThreatAssessmentService threatAssessmentService) {
        this.threatAssessmentService = Objects.requireNonNull(
                threatAssessmentService, "threatAssessmentService");
    }

    /**
     * Plans one tactical intent from actor-visible contacts only.
     *
     * <p>Unknown-disposition contacts may drive cautious movement/screening, but autonomous fire is
     * requested only against a contact already known hostile to the actor and carrying at least a
     * {@link TrackState.InformationState#TRACKED} solution. Because production TrackState currently
     * has no velocity estimate, INTERCEPT deliberately closes on the current observed position; it
     * does not invent predictive target kinematics.</p>
     *
     * @param contacts actor-visible contacts only
     * @param context actor-local mission and geometry context
     * @return immutable deterministic tactical intent
     */
    public TacticalIntent plan(List<ObservedContact> contacts, TacticalContext context) {
        Objects.requireNonNull(contacts, "contacts");
        Objects.requireNonNull(context, "context");
        List<Assessment> assessments = threatAssessmentService.assess(
                contacts,
                context.actorXM(),
                context.actorYM(),
                context.nowSeconds(),
                context.tacticalReferenceRangeM(),
                context.freshnessReferenceSeconds());
        Assessment selectedAssessment = assessments.stream()
                .filter(value -> value.disposition() != ContactDisposition.FRIENDLY)
                .filter(value -> value.priorityScore() > 0d)
                .findFirst()
                .orElse(null);
        if (selectedAssessment == null) {
            return TacticalIntent.noTarget(context.posture());
        }

        ObservedContact selectedContact = contacts.stream()
                .filter(value -> value.track().targetId() == selectedAssessment.targetId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Stage-19A assessment returned a target absent from actor-visible contacts"));
        TrackState track = selectedContact.track();
        boolean fireRequested = selectedContact.disposition() == ContactDisposition.HOSTILE
                && supportsFireRequest(track.informationState());
        double[] movement = movementFor(context, track);
        return new TacticalIntent(
                context.posture(),
                true,
                track.targetId(),
                movement[0],
                movement[1],
                fireRequested,
                selectedAssessment.priorityScore());
    }

    private static boolean supportsFireRequest(TrackState.InformationState informationState) {
        return informationState == TrackState.InformationState.TRACKED
                || informationState == TrackState.InformationState.FIRE_CONTROL;
    }

    private static double[] movementFor(TacticalContext context, TrackState track) {
        return switch (context.posture()) {
            case HOLD -> new double[]{0d, 0d};
            case INTERCEPT -> track.positionKnown()
                    ? normalizedDirection(
                            context.actorXM(), context.actorYM(),
                            track.estimatedXM(), track.estimatedYM())
                    : new double[]{0d, 0d};
            case SCREEN -> screenMovement(context, track);
        };
    }

    private static double[] screenMovement(TacticalContext context, TrackState track) {
        if (!track.positionKnown()) {
            return normalizedDirection(
                    context.actorXM(), context.actorYM(),
                    context.protectedXM(), context.protectedYM());
        }
        double threatDx = track.estimatedXM() - context.protectedXM();
        double threatDy = track.estimatedYM() - context.protectedYM();
        double threatDistance = Math.hypot(threatDx, threatDy);
        double screenX = context.protectedXM();
        double screenY = context.protectedYM();
        if (threatDistance > 0d) {
            double offset = Math.min(context.screenRadiusM(), threatDistance);
            screenX += threatDx / threatDistance * offset;
            screenY += threatDy / threatDistance * offset;
        }
        return normalizedDirection(
                context.actorXM(), context.actorYM(),
                screenX, screenY);
    }

    private static double[] normalizedDirection(double fromX, double fromY, double toX, double toY) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double length = Math.hypot(dx, dy);
        if (length == 0d) {
            return new double[]{0d, 0d};
        }
        return new double[]{dx / length, dy / length};
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
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
}
