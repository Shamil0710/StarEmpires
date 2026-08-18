package com.spacesim.ship;

import com.spacesim.ship.ObservedThreatAssessmentService.Assessment;
import com.spacesim.ship.ObservedThreatAssessmentService.ContactDisposition;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic Stage-19 survival behavior for retreat, pursuit and disengagement.
 *
 * <p>Own-ship readiness is authoritative local physical information. Opponent information remains
 * actor-bounded {@link TrackState}. The planner never reads hidden enemy hull, fit, ammunition or
 * transform state and never manufactures movement when the own ship lacks physical maneuverability.</p>
 */
public final class TacticalSurvivalPlanner {
    /** High-level survival action selected for the current tactical tick. */
    public enum SurvivalAction {
        /** Higher-level engagement behavior may continue normally. */ CONTINUE,
        /** Continue closing on a fresh actor-visible hostile track. */ PURSUE,
        /** Leave the engagement using physically available maneuver capability. */ RETREAT,
        /** Cease pursuit/engagement movement because a safe actionable solution is unavailable. */ DISENGAGE
    }

    /** Stable decision reason for tests, telemetry and future UI explanation. */
    public enum DecisionReason {
        /** Own physical readiness remains above policy thresholds. */ READY,
        /** Local structural integrity crossed the retreat threshold. */ STRUCTURAL_DAMAGE,
        /** Local subsystem integrity crossed the retreat threshold. */ SUBSYSTEM_DAMAGE,
        /** Every operational weapon is finite-ammunition dependent and no physical rounds remain. */ AMMUNITION_DEPLETED,
        /** Physical reaction-mass reserve crossed the retreat threshold. */ REACTION_MASS_RESERVE,
        /** Remaining physical delta-v crossed the retreat threshold. */ DELTA_V_RESERVE,
        /** Available own-ship acceleration crossed the retreat threshold. */ PROPULSION_DEGRADED,
        /** A fresh positional hostile track supports continued pursuit. */ FRESH_HOSTILE_TRACK,
        /** No actor-visible hostile track currently supports pursuit. */ NO_PURSUIT_TRACK,
        /** A hostile track exists but has become too stale for pursuit policy. */ STALE_PURSUIT_TRACK,
        /** Retreat is required but current physical propulsion cannot create a maneuver. */ CANNOT_MANEUVER
    }

    /**
     * Own-ship physical readiness snapshot.
     *
     * @param meanCompartmentIntegrity mean local structural integrity in {@code [0,1]}
     * @param minimumModuleIntegrity lowest installed subsystem integrity in {@code [0,1]}
     * @param reactionMassKg current physical propulsion reaction mass
     * @param deltaVMps current physical remaining delta-v
     * @param accelerationMps2 current physically derived acceleration capability
     * @param finiteAmmunitionDependent whether all currently operational weapons require physical ammunition
     * @param ammunitionCount current total physical ammunition item count across installed interfaces
     */
    public record OwnReadiness(
            double meanCompartmentIntegrity,
            double minimumModuleIntegrity,
            double reactionMassKg,
            double deltaVMps,
            double accelerationMps2,
            boolean finiteAmmunitionDependent,
            long ammunitionCount) {
        /**
         * Validates one own-ship physical readiness snapshot.
         *
         * @param meanCompartmentIntegrity mean local structural integrity in {@code [0,1]}
         * @param minimumModuleIntegrity lowest installed subsystem integrity in {@code [0,1]}
         * @param reactionMassKg current physical propulsion reaction mass
         * @param deltaVMps current physical remaining delta-v
         * @param accelerationMps2 current physically derived acceleration capability
         * @param finiteAmmunitionDependent whether all currently operational weapons require physical ammunition
         * @param ammunitionCount current total physical ammunition item count across installed interfaces
         */
        public OwnReadiness {
            requireFraction(meanCompartmentIntegrity, "meanCompartmentIntegrity");
            requireFraction(minimumModuleIntegrity, "minimumModuleIntegrity");
            requireNonNegativeFinite(reactionMassKg, "reactionMassKg");
            requireNonNegativeFinite(deltaVMps, "deltaVMps");
            requireNonNegativeFinite(accelerationMps2, "accelerationMps2");
            if (ammunitionCount < 0L) {
                throw new IllegalArgumentException("ammunitionCount must be non-negative");
            }
        }

        /**
         * Backward-compatible readiness constructor for callers that do not model ammunition dependence.
         *
         * @param meanCompartmentIntegrity mean local structural integrity in {@code [0,1]}
         * @param minimumModuleIntegrity lowest installed subsystem integrity in {@code [0,1]}
         * @param reactionMassKg current physical propulsion reaction mass
         * @param deltaVMps current physical remaining delta-v
         * @param accelerationMps2 current physically derived acceleration capability
         */
        public OwnReadiness(
                double meanCompartmentIntegrity,
                double minimumModuleIntegrity,
                double reactionMassKg,
                double deltaVMps,
                double accelerationMps2) {
            this(
                    meanCompartmentIntegrity,
                    minimumModuleIntegrity,
                    reactionMassKg,
                    deltaVMps,
                    accelerationMps2,
                    false,
                    0L);
        }

        /** @return whether the own ship can currently create a non-zero propulsive maneuver */
        public boolean canManeuver() {
            return reactionMassKg > 0d && deltaVMps > 0d && accelerationMps2 > 0d;
        }
    }

    /**
     * Behavioral thresholds. Values are policy, never physical performance bonuses.
     *
     * @param minimumCompartmentIntegrity retreat below this mean structural integrity
     * @param minimumModuleIntegrity retreat below this lowest subsystem integrity
     * @param reactionMassReserveKg retreat below this physical reaction-mass reserve
     * @param deltaVReserveMps retreat below this physical delta-v reserve
     * @param minimumAccelerationMps2 retreat below this own-ship acceleration capability
     * @param maximumPursuitTrackAgeSeconds stop pursuit when the freshest hostile track is older
     */
    public record Policy(
            double minimumCompartmentIntegrity,
            double minimumModuleIntegrity,
            double reactionMassReserveKg,
            double deltaVReserveMps,
            double minimumAccelerationMps2,
            double maximumPursuitTrackAgeSeconds) {
        /**
         * Validates deterministic non-negative survival policy thresholds.
         *
         * @param minimumCompartmentIntegrity retreat below this mean structural integrity
         * @param minimumModuleIntegrity retreat below this lowest subsystem integrity
         * @param reactionMassReserveKg retreat below this physical reaction-mass reserve
         * @param deltaVReserveMps retreat below this physical delta-v reserve
         * @param minimumAccelerationMps2 retreat below this own-ship acceleration capability
         * @param maximumPursuitTrackAgeSeconds stop pursuit when the freshest hostile track is older
         */
        public Policy {
            requireFraction(minimumCompartmentIntegrity, "minimumCompartmentIntegrity");
            requireFraction(minimumModuleIntegrity, "minimumModuleIntegrity");
            requireNonNegativeFinite(reactionMassReserveKg, "reactionMassReserveKg");
            requireNonNegativeFinite(deltaVReserveMps, "deltaVReserveMps");
            requireNonNegativeFinite(minimumAccelerationMps2, "minimumAccelerationMps2");
            requireNonNegativeFinite(maximumPursuitTrackAgeSeconds, "maximumPursuitTrackAgeSeconds");
        }
    }

    /**
     * Optional known safe point used for explicit retreat routing.
     *
     * @param known whether the point is known to the actor
     * @param xM x coordinate or canonical zero when unknown
     * @param yM y coordinate or canonical zero when unknown
     */
    public record SafePoint(boolean known, double xM, double yM) {
        /**
         * Validates explicit known/unknown safe-point geometry.
         *
         * @param known whether the point is known to the actor
         * @param xM x coordinate or canonical zero when unknown
         * @param yM y coordinate or canonical zero when unknown
         */
        public SafePoint {
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            if (!known && (xM != 0d || yM != 0d)) {
                throw new IllegalArgumentException("unknown safe point must use canonical zero coordinates");
            }
        }

        /** @return canonical absence of a known retreat point */
        public static SafePoint unknown() {
            return new SafePoint(false, 0d, 0d);
        }
    }

    /**
     * Immutable survival decision before physical execution.
     *
     * @param action selected survival action
     * @param reason stable decision reason
     * @param targetSelected whether an actor-visible hostile target remains selected
     * @param targetId selected target ID or canonical zero
     * @param movementAxisX normalized horizontal maneuver intent
     * @param movementAxisY normalized vertical maneuver intent
     */
    public record Decision(
            SurvivalAction action,
            DecisionReason reason,
            boolean targetSelected,
            long targetId,
            double movementAxisX,
            double movementAxisY) {
        /**
         * Validates canonical target and normalized movement semantics.
         *
         * @param action selected survival action
         * @param reason stable decision reason
         * @param targetSelected whether an actor-visible hostile target remains selected
         * @param targetId selected target ID or canonical zero
         * @param movementAxisX normalized horizontal maneuver intent
         * @param movementAxisY normalized vertical maneuver intent
         */
        public Decision {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(reason, "reason");
            if (targetSelected != (targetId > 0L)) {
                throw new IllegalArgumentException("targetSelected must match positive targetId");
            }
            requireFinite(movementAxisX, "movementAxisX");
            requireFinite(movementAxisY, "movementAxisY");
            if (movementAxisX * movementAxisX + movementAxisY * movementAxisY > 1d + 1e-12d) {
                throw new IllegalArgumentException("movement intent must be normalized");
            }
        }
    }

    private final ObservedThreatAssessmentService threatAssessmentService;

    /** Creates a survival planner using the production Stage-19A assessment service. */
    public TacticalSurvivalPlanner() {
        this(new ObservedThreatAssessmentService());
    }

    /**
     * Creates a planner with an explicit actor-bounded threat dependency.
     *
     * @param threatAssessmentService Stage-19A threat service
     */
    public TacticalSurvivalPlanner(ObservedThreatAssessmentService threatAssessmentService) {
        this.threatAssessmentService = Objects.requireNonNull(
                threatAssessmentService, "threatAssessmentService");
    }

    /**
     * Resolves retreat first, then optional pursuit from actor-visible information.
     *
     * @param readiness own authoritative physical readiness
     * @param policy behavioral survival thresholds
     * @param contacts actor-visible contacts only
     * @param actorXM own known x position
     * @param actorYM own known y position
     * @param safePoint optional actor-known retreat point
     * @param pursueWhetherSafe whether higher-level mission intent currently requests pursuit
     * @param nowSeconds authoritative current time
     * @param tacticalReferenceRangeM Stage-19A range normalization scale
     * @param freshnessReferenceSeconds Stage-19A freshness normalization scale
     * @return deterministic survival decision
     */
    public Decision decide(
            OwnReadiness readiness,
            Policy policy,
            List<ObservedContact> contacts,
            double actorXM,
            double actorYM,
            SafePoint safePoint,
            boolean pursueWhetherSafe,
            double nowSeconds,
            double tacticalReferenceRangeM,
            double freshnessReferenceSeconds) {
        OwnReadiness checkedReadiness = Objects.requireNonNull(readiness, "readiness");
        Policy checkedPolicy = Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(contacts, "contacts");
        SafePoint checkedSafePoint = Objects.requireNonNull(safePoint, "safePoint");
        requireFinite(actorXM, "actorXM");
        requireFinite(actorYM, "actorYM");

        DecisionReason retreatReason = retreatReason(checkedReadiness, checkedPolicy);
        if (retreatReason != DecisionReason.READY) {
            return retreat(
                    checkedReadiness,
                    contacts,
                    actorXM,
                    actorYM,
                    checkedSafePoint,
                    retreatReason,
                    nowSeconds,
                    tacticalReferenceRangeM,
                    freshnessReferenceSeconds);
        }
        if (!pursueWhetherSafe) {
            return new Decision(SurvivalAction.CONTINUE, DecisionReason.READY, false, 0L, 0d, 0d);
        }
        return pursue(
                contacts,
                actorXM,
                actorYM,
                checkedPolicy.maximumPursuitTrackAgeSeconds(),
                nowSeconds,
                tacticalReferenceRangeM,
                freshnessReferenceSeconds);
    }

    private Decision retreat(
            OwnReadiness readiness,
            List<ObservedContact> contacts,
            double actorXM,
            double actorYM,
            SafePoint safePoint,
            DecisionReason reason,
            double nowSeconds,
            double tacticalReferenceRangeM,
            double freshnessReferenceSeconds) {
        ObservedContact threat = highestHostile(
                contacts, actorXM, actorYM, nowSeconds, tacticalReferenceRangeM, freshnessReferenceSeconds);
        long targetId = threat == null ? 0L : threat.track().targetId();
        if (!readiness.canManeuver()) {
            return new Decision(
                    SurvivalAction.DISENGAGE,
                    DecisionReason.CANNOT_MANEUVER,
                    threat != null,
                    targetId,
                    0d,
                    0d);
        }

        double[] movement;
        if (safePoint.known()) {
            movement = normalizedDirection(actorXM, actorYM, safePoint.xM(), safePoint.yM());
        } else if (threat != null && threat.track().positionKnown()) {
            movement = normalizedDirection(
                    threat.track().estimatedXM(), threat.track().estimatedYM(), actorXM, actorYM);
        } else {
            movement = new double[]{0d, 0d};
        }
        if (movement[0] == 0d && movement[1] == 0d) {
            return new Decision(
                    SurvivalAction.DISENGAGE,
                    reason,
                    threat != null,
                    targetId,
                    0d,
                    0d);
        }
        return new Decision(
                SurvivalAction.RETREAT,
                reason,
                threat != null,
                targetId,
                movement[0],
                movement[1]);
    }

    private Decision pursue(
            List<ObservedContact> contacts,
            double actorXM,
            double actorYM,
            double maximumTrackAgeSeconds,
            double nowSeconds,
            double tacticalReferenceRangeM,
            double freshnessReferenceSeconds) {
        ObservedContact threat = highestHostile(
                contacts, actorXM, actorYM, nowSeconds, tacticalReferenceRangeM, freshnessReferenceSeconds);
        if (threat == null || !threat.track().positionKnown()
                || !supportsPursuit(threat.track().informationState())) {
            return new Decision(
                    SurvivalAction.DISENGAGE,
                    DecisionReason.NO_PURSUIT_TRACK,
                    false,
                    0L,
                    0d,
                    0d);
        }
        double ageSeconds = threat.track().ageSeconds(nowSeconds);
        if (ageSeconds > maximumTrackAgeSeconds) {
            return new Decision(
                    SurvivalAction.DISENGAGE,
                    DecisionReason.STALE_PURSUIT_TRACK,
                    false,
                    0L,
                    0d,
                    0d);
        }
        double[] movement = normalizedDirection(
                actorXM,
                actorYM,
                threat.track().estimatedXM(),
                threat.track().estimatedYM());
        return new Decision(
                SurvivalAction.PURSUE,
                DecisionReason.FRESH_HOSTILE_TRACK,
                true,
                threat.track().targetId(),
                movement[0],
                movement[1]);
    }

    private ObservedContact highestHostile(
            List<ObservedContact> contacts,
            double actorXM,
            double actorYM,
            double nowSeconds,
            double tacticalReferenceRangeM,
            double freshnessReferenceSeconds) {
        List<Assessment> assessments = threatAssessmentService.assess(
                contacts,
                actorXM,
                actorYM,
                nowSeconds,
                tacticalReferenceRangeM,
                freshnessReferenceSeconds);
        Assessment hostile = assessments.stream()
                .filter(value -> value.disposition() == ContactDisposition.HOSTILE)
                .findFirst()
                .orElse(null);
        if (hostile == null) {
            return null;
        }
        return contacts.stream()
                .filter(value -> value.track().targetId() == hostile.targetId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("hostile assessment lost actor-visible contact"));
    }

    private static DecisionReason retreatReason(OwnReadiness readiness, Policy policy) {
        if (readiness.meanCompartmentIntegrity() < policy.minimumCompartmentIntegrity()) {
            return DecisionReason.STRUCTURAL_DAMAGE;
        }
        if (readiness.minimumModuleIntegrity() < policy.minimumModuleIntegrity()) {
            return DecisionReason.SUBSYSTEM_DAMAGE;
        }
        if (readiness.finiteAmmunitionDependent() && readiness.ammunitionCount() == 0L) {
            return DecisionReason.AMMUNITION_DEPLETED;
        }
        if (readiness.reactionMassKg() < policy.reactionMassReserveKg()) {
            return DecisionReason.REACTION_MASS_RESERVE;
        }
        if (readiness.deltaVMps() < policy.deltaVReserveMps()) {
            return DecisionReason.DELTA_V_RESERVE;
        }
        if (readiness.accelerationMps2() < policy.minimumAccelerationMps2()) {
            return DecisionReason.PROPULSION_DEGRADED;
        }
        return DecisionReason.READY;
    }

    private static boolean supportsPursuit(TrackState.InformationState state) {
        return state == TrackState.InformationState.TRACKED
                || state == TrackState.InformationState.FIRE_CONTROL;
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

    private static void requireFraction(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be finite in [0,1]");
        }
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
