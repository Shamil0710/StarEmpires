package com.spacesim.ship;

import com.spacesim.ship.ObservedThreatAssessmentService.ContactDisposition;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import com.spacesim.ship.TacticalSurvivalPlanner.Decision;
import com.spacesim.ship.TacticalSurvivalPlanner.DecisionReason;
import com.spacesim.ship.TacticalSurvivalPlanner.OwnReadiness;
import com.spacesim.ship.TacticalSurvivalPlanner.Policy;
import com.spacesim.ship.TacticalSurvivalPlanner.SafePoint;
import com.spacesim.ship.TacticalSurvivalPlanner.SurvivalAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalSurvivalPlannerTest {
    private static final double EPSILON = 1e-9d;
    private final TacticalSurvivalPlanner planner = new TacticalSurvivalPlanner();
    private final Policy policy = new Policy(0.45d, 0.30d, 200d, 500d, 0.10d, 5d);

    @Test
    void healthyShipPursuesOnlyFreshTrackedHostileUsingObservedPosition() {
        Decision decision = decide(
                new OwnReadiness(0.9d, 0.8d, 2_000d, 4_000d, 0.5d),
                List.of(contact(101L, 1_000d, 0d, 100d, TrackState.InformationState.TRACKED)),
                SafePoint.unknown(),
                true,
                102d);

        assertEquals(SurvivalAction.PURSUE, decision.action());
        assertEquals(DecisionReason.FRESH_HOSTILE_TRACK, decision.reason());
        assertTrue(decision.targetSelected());
        assertEquals(101L, decision.targetId());
        assertEquals(1d, decision.movementAxisX(), EPSILON);
        assertEquals(0d, decision.movementAxisY(), EPSILON);
    }

    @Test
    void staleTrackEndsPursuitAndDropsTargetInsteadOfRetainingMagicalKnowledge() {
        Decision decision = decide(
                new OwnReadiness(0.9d, 0.8d, 2_000d, 4_000d, 0.5d),
                List.of(contact(201L, 1_000d, 0d, 90d, TrackState.InformationState.FIRE_CONTROL)),
                SafePoint.unknown(),
                true,
                100d);

        assertEquals(SurvivalAction.DISENGAGE, decision.action());
        assertEquals(DecisionReason.STALE_PURSUIT_TRACK, decision.reason());
        assertFalse(decision.targetSelected());
        assertEquals(0L, decision.targetId());
        assertEquals(0d, decision.movementAxisX(), 0d);
        assertEquals(0d, decision.movementAxisY(), 0d);
    }

    @Test
    void structuralDamageRetreatsTowardKnownSafePoint() {
        Decision decision = decide(
                new OwnReadiness(0.30d, 0.8d, 2_000d, 4_000d, 0.5d),
                List.of(contact(301L, 1_000d, 0d, 100d, TrackState.InformationState.TRACKED)),
                new SafePoint(true, 0d, -1_000d),
                true,
                100d);

        assertEquals(SurvivalAction.RETREAT, decision.action());
        assertEquals(DecisionReason.STRUCTURAL_DAMAGE, decision.reason());
        assertEquals(0d, decision.movementAxisX(), EPSILON);
        assertEquals(-1d, decision.movementAxisY(), EPSILON);
        assertTrue(decision.targetSelected());
        assertEquals(301L, decision.targetId());
    }

    @Test
    void damagedShipWithoutSafePointMovesAwayFromObservedHostilePosition() {
        Decision decision = decide(
                new OwnReadiness(0.9d, 0.20d, 2_000d, 4_000d, 0.5d),
                List.of(contact(401L, 1_000d, 0d, 100d, TrackState.InformationState.TRACKED)),
                SafePoint.unknown(),
                true,
                100d);

        assertEquals(SurvivalAction.RETREAT, decision.action());
        assertEquals(DecisionReason.SUBSYSTEM_DAMAGE, decision.reason());
        assertEquals(-1d, decision.movementAxisX(), EPSILON);
        assertEquals(0d, decision.movementAxisY(), EPSILON);
    }

    @Test
    void emptyReactionMassCannotProduceFakeRetreatMovement() {
        Decision decision = decide(
                new OwnReadiness(0.3d, 0.8d, 0d, 0d, 0.5d),
                List.of(contact(501L, 1_000d, 0d, 100d, TrackState.InformationState.TRACKED)),
                new SafePoint(true, -1_000d, 0d),
                true,
                100d);

        assertEquals(SurvivalAction.DISENGAGE, decision.action());
        assertEquals(DecisionReason.CANNOT_MANEUVER, decision.reason());
        assertEquals(0d, decision.movementAxisX(), 0d);
        assertEquals(0d, decision.movementAxisY(), 0d);
    }

    @Test
    void nonPursuitMissionContinuesWhenReadinessIsHealthy() {
        Decision decision = decide(
                new OwnReadiness(0.9d, 0.8d, 2_000d, 4_000d, 0.5d),
                List.of(contact(601L, 1_000d, 0d, 100d, TrackState.InformationState.TRACKED)),
                SafePoint.unknown(),
                false,
                100d);

        assertEquals(SurvivalAction.CONTINUE, decision.action());
        assertEquals(DecisionReason.READY, decision.reason());
        assertFalse(decision.targetSelected());
    }

    @Test
    void classifiedOrUnknownDispositionContactDoesNotSupportAggressivePursuit() {
        TrackState classified = track(701L, 1_000d, 0d, 100d, TrackState.InformationState.CLASSIFIED);
        ObservedContact unknown = new ObservedContact(
                track(702L, 800d, 0d, 100d, TrackState.InformationState.FIRE_CONTROL),
                ContactDisposition.UNKNOWN);

        Decision classifiedDecision = decide(
                new OwnReadiness(0.9d, 0.8d, 2_000d, 4_000d, 0.5d),
                List.of(new ObservedContact(classified, ContactDisposition.HOSTILE)),
                SafePoint.unknown(),
                true,
                100d);
        Decision unknownDecision = decide(
                new OwnReadiness(0.9d, 0.8d, 2_000d, 4_000d, 0.5d),
                List.of(unknown),
                SafePoint.unknown(),
                true,
                100d);

        assertEquals(DecisionReason.NO_PURSUIT_TRACK, classifiedDecision.reason());
        assertEquals(DecisionReason.NO_PURSUIT_TRACK, unknownDecision.reason());
    }

    @Test
    void physicalReserveThresholdsArePolicyOnlyAndValidated() {
        Decision lowReactionMass = decide(
                new OwnReadiness(0.9d, 0.8d, 100d, 4_000d, 0.5d),
                List.of(),
                new SafePoint(true, -1_000d, 0d),
                false,
                100d);
        Decision lowDeltaV = decide(
                new OwnReadiness(0.9d, 0.8d, 2_000d, 100d, 0.5d),
                List.of(),
                new SafePoint(true, -1_000d, 0d),
                false,
                100d);
        Decision lowAcceleration = decide(
                new OwnReadiness(0.9d, 0.8d, 2_000d, 4_000d, 0.05d),
                List.of(),
                new SafePoint(true, -1_000d, 0d),
                false,
                100d);

        assertEquals(DecisionReason.REACTION_MASS_RESERVE, lowReactionMass.reason());
        assertEquals(DecisionReason.DELTA_V_RESERVE, lowDeltaV.reason());
        assertEquals(DecisionReason.PROPULSION_DEGRADED, lowAcceleration.reason());
        assertThrows(IllegalArgumentException.class,
                () -> new Policy(1.1d, 0.3d, 0d, 0d, 0d, 0d));
        assertThrows(IllegalArgumentException.class,
                () -> new SafePoint(false, 1d, 0d));
    }

    private Decision decide(
            OwnReadiness readiness,
            List<ObservedContact> contacts,
            SafePoint safePoint,
            boolean pursue,
            double nowSeconds) {
        return planner.decide(
                readiness,
                policy,
                contacts,
                0d,
                0d,
                safePoint,
                pursue,
                nowSeconds,
                2_000d,
                20d);
    }

    private static ObservedContact contact(
            long id,
            double x,
            double y,
            double measuredAt,
            TrackState.InformationState state) {
        return new ObservedContact(track(id, x, y, measuredAt, state), ContactDisposition.HOSTILE);
    }

    private static TrackState track(
            long id,
            double x,
            double y,
            double measuredAt,
            TrackState.InformationState state) {
        return new TrackState(
                id,
                state,
                true,
                x,
                y,
                new TrackCovariance(100d, 0.0001d, 100d),
                1d,
                measuredAt,
                2,
                4);
    }
}
