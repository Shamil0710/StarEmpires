package com.spacesim.ship;

import com.spacesim.ship.GuidanceRuntime.TrackSource;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.LiveTacticalOrdnanceObservationRuntime.ObservedOrdnanceTrack;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
 * Read-only Stage-19 audit for the known ship-impact-before-interceptor phase ordering.
 *
 * <p>The production resolver intentionally remains unchanged while this audit proves whether that
 * ordering suppresses a physically earlier interceptor contact in accepted scaled scenarios. The
 * audit snapshots only already-active strike/interceptor bodies at tick start, reproduces the same
 * deterministic guidance equations from actor-bounded tracks after sensing, and compares swept
 * contact fractions with the exact same circumscribed body radius used by production interception.
 * It never deletes, moves, damages or retargets a body.</p>
 */
public final class Stage19GuidedImpactOrderingAudit {
    private static final double TICK_SECONDS = LiveTacticalBattleControlRuntime.TICK_SECONDS;
    private static final double EPSILON = 1e-9d;
    private final GuidanceRuntime guidanceRuntime = new GuidanceRuntime();

    /**
     * Captures the physical bodies and ship positions needed to audit the next authoritative tick.
     *
     * @param ordnanceRuntime authoritative offensive guided-body runtime before advancement
     * @param defenseRuntime authoritative interceptor runtime before advancement
     * @return immutable start-of-tick ordering snapshot
     */
    public Snapshot capture(
            LiveTacticalBattleOrdnanceRuntime ordnanceRuntime,
            LiveTacticalBattleDefenseRuntime defenseRuntime) {
        Objects.requireNonNull(ordnanceRuntime, "ordnanceRuntime");
        Objects.requireNonNull(defenseRuntime, "defenseRuntime");
        TreeMap<Long, Position> ships = new TreeMap<>();
        for (CombatantRuntime combatant : ordnanceRuntime.battleState().combatants()) {
            ships.put(combatant.spec().entityId(), new Position(
                    combatant.transform().position.x,
                    combatant.transform().position.y));
        }
        return new Snapshot(
                ordnanceRuntime.tick(),
                ordnanceRuntime.guidedBodies(),
                defenseRuntime.interceptorBodies(),
                ships);
    }

    /**
     * Evaluates the completed tick without changing authority.
     *
     * @param snapshot start-of-tick audit snapshot
     * @param ordnanceRuntime authoritative ordnance state after the tick
     * @param defenseRuntime authoritative defense state after the tick
     * @return deterministic ordering evidence
     */
    public Result evaluate(
            Snapshot snapshot,
            LiveTacticalBattleOrdnanceRuntime ordnanceRuntime,
            LiveTacticalBattleDefenseRuntime defenseRuntime) {
        Snapshot start = Objects.requireNonNull(snapshot, "snapshot");
        LiveTacticalBattleOrdnanceRuntime ordnance = Objects.requireNonNull(ordnanceRuntime, "ordnanceRuntime");
        LiveTacticalBattleDefenseRuntime defense = Objects.requireNonNull(defenseRuntime, "defenseRuntime");
        if (ordnance.tick() != start.tick() + 1L) {
            throw new IllegalArgumentException("ordering audit must evaluate exactly one authoritative tick");
        }

        Map<Long, GuidedWeaponBody> survivingThreats = indexBodies(ordnance.guidedBodies());
        List<ThreatPath> shipImpactCandidates = new ArrayList<>();
        for (GuidedWeaponBody threatStart : start.threats()) {
            if (survivingThreats.containsKey(threatStart.bodyId())) {
                continue;
            }
            GuidedWeaponBody guidedStart = reproduceStrikeGuidance(threatStart, ordnance);
            GuidedWeaponBody threatEnd = guidedStart.advanceBallistic(TICK_SECONDS);
            ShipContact shipContact = firstShipContact(guidedStart, threatEnd, start.shipStartPositions(), ordnance);
            if (shipContact != null) {
                shipImpactCandidates.add(new ThreatPath(guidedStart, threatEnd, shipContact));
            }
        }

        List<EarlierInterceptorContact> earlier = new ArrayList<>();
        for (ThreatPath threat : shipImpactCandidates) {
            for (GuidedWeaponBody interceptorStart : start.interceptors()) {
                GuidedWeaponBody interceptorEnd = reproduceInterceptorEnd(interceptorStart, defense);
                OptionalDouble fraction = firstMovingBodyContact(
                        interceptorStart.xM(), interceptorStart.yM(),
                        interceptorEnd.xM(), interceptorEnd.yM(), bodyRadius(interceptorStart),
                        threat.start().xM(), threat.start().yM(),
                        threat.end().xM(), threat.end().yM(), bodyRadius(threat.start()));
                if (fraction.isPresent() && fraction.getAsDouble() < threat.shipContact().fraction() - EPSILON) {
                    earlier.add(new EarlierInterceptorContact(
                            threat.start().bodyId(),
                            interceptorStart.bodyId(),
                            threat.shipContact().targetEntityId(),
                            threat.shipContact().fraction(),
                            fraction.getAsDouble()));
                }
            }
        }
        return new Result(
                ordnance.tick(),
                shipImpactCandidates.size(),
                List.copyOf(earlier));
    }

    private GuidedWeaponBody reproduceStrikeGuidance(
            GuidedWeaponBody body,
            LiveTacticalBattleOrdnanceRuntime ordnance) {
        TrackState track = ordnance.battleState().visibleContacts(body.sourceEntityId()).stream()
                .map(ObservedThreatAssessmentService.ObservedContact::track)
                .filter(value -> value.targetId() == body.targetId())
                .findFirst()
                .orElse(null);
        if (track == null) {
            return body;
        }
        GuidanceRuntime.GuidanceCommand command = guidanceRuntime.planLeadPursuit(
                body,
                track,
                new TargetMotionEstimate(0d, 0d, 0d, 0d),
                TrackSource.DATALINK,
                TICK_SECONDS);
        return command.allowed() ? guidanceRuntime.execute(body, command) : body;
    }

    private GuidedWeaponBody reproduceInterceptorEnd(
            GuidedWeaponBody body,
            LiveTacticalBattleDefenseRuntime defense) {
        ObservedOrdnanceTrack observed = defense.observationRuntime().track(
                body.sourceEntityId(),
                body.targetId());
        GuidedWeaponBody guided = body;
        if (actionableTrack(observed)) {
            GuidanceRuntime.GuidanceCommand command = guidanceRuntime.planLeadPursuit(
                    body,
                    observed.track(),
                    new TargetMotionEstimate(
                            observed.estimatedVelocityXMps(),
                            observed.estimatedVelocityYMps(),
                            observed.oneSigmaVelocityMps(),
                            0d),
                    TrackSource.DATALINK,
                    TICK_SECONDS);
            if (command.allowed()) {
                guided = guidanceRuntime.execute(body, command);
            }
        }
        return guided.advanceBallistic(TICK_SECONDS);
    }

    private static boolean actionableTrack(ObservedOrdnanceTrack observed) {
        return observed != null
                && observed.track().positionKnown()
                && observed.velocityKnown();
    }

    private static ShipContact firstShipContact(
            GuidedWeaponBody start,
            GuidedWeaponBody end,
            Map<Long, Position> shipStarts,
            LiveTacticalBattleOrdnanceRuntime ordnance) {
        ShipContact best = null;
        for (CombatantRuntime target : ordnance.battleState().combatants()) {
            if (target.spec().entityId() == start.sourceEntityId()) {
                continue;
            }
            Position shipStart = shipStarts.get(target.spec().entityId());
            if (shipStart == null) {
                continue;
            }
            Position shipEnd = new Position(
                    target.transform().position.x,
                    target.transform().position.y);
            OptionalDouble fraction = TacticalCollisionGeometry.firstSegmentAabbHitFraction(
                    start.xM() - shipStart.xM(),
                    start.yM() - shipStart.yM(),
                    end.xM() - shipEnd.xM(),
                    end.yM() - shipEnd.yM(),
                    target.hull().boundingDimensionsM().lengthM() * 0.5d,
                    target.hull().boundingDimensionsM().widthM() * 0.5d);
            if (fraction.isEmpty()) {
                continue;
            }
            double value = fraction.getAsDouble();
            if (best == null
                    || value < best.fraction() - EPSILON
                    || (Math.abs(value - best.fraction()) <= EPSILON
                    && target.spec().entityId() < best.targetEntityId())) {
                best = new ShipContact(target.spec().entityId(), value);
            }
        }
        return best;
    }

    /**
     * Pure swept moving-circle helper used by the audit and its non-vacuity unit test.
     *
     * @return first physical contact fraction in [0,1], or empty
     */
    static OptionalDouble firstMovingBodyContact(
            double firstStartX,
            double firstStartY,
            double firstEndX,
            double firstEndY,
            double firstRadius,
            double secondStartX,
            double secondStartY,
            double secondEndX,
            double secondEndY,
            double secondRadius) {
        return TacticalCollisionGeometry.firstSegmentCircleHitFraction(
                firstStartX - secondStartX,
                firstStartY - secondStartY,
                firstEndX - secondEndX,
                firstEndY - secondEndY,
                firstRadius + secondRadius);
    }

    private static double bodyRadius(GuidedWeaponBody body) {
        return 0.5d * Math.hypot(body.lengthM(), body.diameterM());
    }

    private static Map<Long, GuidedWeaponBody> indexBodies(List<GuidedWeaponBody> bodies) {
        TreeMap<Long, GuidedWeaponBody> result = new TreeMap<>();
        for (GuidedWeaponBody body : bodies) {
            result.put(body.bodyId(), body);
        }
        return result;
    }

    private record ThreatPath(GuidedWeaponBody start, GuidedWeaponBody end, ShipContact shipContact) { }

    private record ShipContact(long targetEntityId, double fraction) { }

    /**
     * Start-of-tick read-only ordering snapshot.
     *
     * @param tick authoritative tick before advancement
     * @param threats active offensive guided bodies
     * @param interceptors active physical interceptor bodies
     * @param shipStartPositions physical ship positions at tick start
     */
    public record Snapshot(
            long tick,
            List<GuidedWeaponBody> threats,
            List<GuidedWeaponBody> interceptors,
            Map<Long, Position> shipStartPositions) {
        /**
         * Validates and freezes one ordering snapshot.
         *
         * @param tick authoritative tick before advancement
         * @param threats active offensive guided bodies
         * @param interceptors active physical interceptor bodies
         * @param shipStartPositions physical ship positions at tick start
         */
        public Snapshot {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            threats = List.copyOf(Objects.requireNonNull(threats, "threats"));
            interceptors = List.copyOf(Objects.requireNonNull(interceptors, "interceptors"));
            shipStartPositions = Map.copyOf(new TreeMap<>(Objects.requireNonNull(
                    shipStartPositions, "shipStartPositions")));
        }
    }

    /**
     * Physical position used only by ordering audit snapshots.
     *
     * @param xM x position in meters
     * @param yM y position in meters
     */
    public record Position(double xM, double yM) {
        /**
         * Validates one finite position.
         *
         * @param xM x position in meters
         * @param yM y position in meters
         */
        public Position {
            if (!Double.isFinite(xM) || !Double.isFinite(yM)) {
                throw new IllegalArgumentException("ordering audit position must be finite");
            }
        }
    }

    /**
     * One physically earlier interceptor contact suppressed by ship-priority phase ordering.
     *
     * @param threatBodyId strike body identity
     * @param interceptorBodyId interceptor body identity
     * @param shipTargetEntityId ship that the strike path reaches later in the same tick
     * @param shipImpactFraction ship contact fraction
     * @param interceptorContactFraction earlier body-body contact fraction
     */
    public record EarlierInterceptorContact(
            long threatBodyId,
            long interceptorBodyId,
            long shipTargetEntityId,
            double shipImpactFraction,
            double interceptorContactFraction) {
        /**
         * Validates one ordering ambiguity.
         *
         * @param threatBodyId strike body identity
         * @param interceptorBodyId interceptor body identity
         * @param shipTargetEntityId ship reached later in the same tick
         * @param shipImpactFraction ship contact fraction
         * @param interceptorContactFraction earlier body-body contact fraction
         */
        public EarlierInterceptorContact {
            if (threatBodyId <= 0L || interceptorBodyId <= 0L || shipTargetEntityId <= 0L
                    || !Double.isFinite(shipImpactFraction)
                    || !Double.isFinite(interceptorContactFraction)
                    || shipImpactFraction < 0d || shipImpactFraction > 1d
                    || interceptorContactFraction < 0d || interceptorContactFraction > 1d
                    || interceptorContactFraction >= shipImpactFraction) {
                throw new IllegalArgumentException("invalid earlier interceptor contact");
            }
        }
    }

    /**
     * Deterministic evidence from one completed authoritative tick.
     *
     * @param tick completed authoritative tick
     * @param shipImpactCandidates number of previously active strike paths ending in ship contact
     * @param earlierInterceptorContacts physically earlier contacts suppressed by current ordering
     */
    public record Result(
            long tick,
            int shipImpactCandidates,
            List<EarlierInterceptorContact> earlierInterceptorContacts) {
        /**
         * Validates and freezes one audit result.
         *
         * @param tick completed authoritative tick
         * @param shipImpactCandidates number of previously active strike paths ending in ship contact
         * @param earlierInterceptorContacts physically earlier contacts suppressed by current ordering
         */
        public Result {
            if (tick <= 0L || shipImpactCandidates < 0) {
                throw new IllegalArgumentException("invalid ordering audit result counters");
            }
            earlierInterceptorContacts = List.copyOf(Objects.requireNonNull(
                    earlierInterceptorContacts, "earlierInterceptorContacts"));
        }

        /** @return number of physically earlier contacts detected on this tick */
        public int ambiguityCount() {
            return earlierInterceptorContacts.size();
        }
    }
}