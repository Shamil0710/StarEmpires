package com.spacesim.ship;

import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalContext;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalIntent;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalPosture;
import com.spacesim.ship.ObservedThreatAssessmentService.ContactDisposition;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservedTacticalIntentPlannerTest {
    private static final double EPSILON = 1e-9d;
    private final ObservedTacticalIntentPlanner planner = new ObservedTacticalIntentPlanner();

    @Test
    void interceptClosesOnObservedHostilePositionAndRequestsFireFromTrackedState() {
        TacticalIntent intent = planner.plan(
                List.of(contact(101L, 1_000d, 0d, TrackState.InformationState.TRACKED,
                        ContactDisposition.HOSTILE)),
                context(TacticalPosture.INTERCEPT, 0d, 0d, false, 0d, 0d, 0d));

        assertTrue(intent.targetSelected());
        assertEquals(101L, intent.targetId());
        assertEquals(1d, intent.movementAxisX(), EPSILON);
        assertEquals(0d, intent.movementAxisY(), EPSILON);
        assertTrue(intent.fireRequested());
        assertTrue(intent.observedPriority() > 0d);
    }

    @Test
    void unknownDispositionCanDriveCautiousInterceptButNeverAutonomousFire() {
        TacticalIntent intent = planner.plan(
                List.of(contact(201L, 0d, 1_000d, TrackState.InformationState.FIRE_CONTROL,
                        ContactDisposition.UNKNOWN)),
                context(TacticalPosture.INTERCEPT, 0d, 0d, false, 0d, 0d, 0d));

        assertTrue(intent.targetSelected());
        assertEquals(0d, intent.movementAxisX(), EPSILON);
        assertEquals(1d, intent.movementAxisY(), EPSILON);
        assertFalse(intent.fireRequested());
    }

    @Test
    void screenMovesTowardPointBetweenProtectedAssetAndObservedThreat() {
        TacticalIntent intent = planner.plan(
                List.of(contact(301L, 1_000d, 0d, TrackState.InformationState.TRACKED,
                        ContactDisposition.HOSTILE)),
                context(TacticalPosture.SCREEN, 0d, 500d, true, 0d, 0d, 200d));

        double length = Math.hypot(200d, -500d);
        assertEquals(200d / length, intent.movementAxisX(), EPSILON);
        assertEquals(-500d / length, intent.movementAxisY(), EPSILON);
        assertTrue(intent.fireRequested());
    }

    @Test
    void interceptDoesNotInventPositionFromUnknownTrackPlaceholders() {
        TrackState unknownPosition = new TrackState(
                401L,
                TrackState.InformationState.CLASSIFIED,
                false,
                0d,
                0d,
                new TrackCovariance(null, 0.04d, null),
                0.7d,
                100d,
                1,
                2);

        TacticalIntent intent = planner.plan(
                List.of(new ObservedContact(unknownPosition, ContactDisposition.HOSTILE)),
                context(TacticalPosture.INTERCEPT, 50d, 60d, false, 0d, 0d, 0d));

        assertTrue(intent.targetSelected());
        assertEquals(0d, intent.movementAxisX(), EPSILON);
        assertEquals(0d, intent.movementAxisY(), EPSILON);
        assertFalse(intent.fireRequested());
    }

    @Test
    void holdKeepsPositionWhileRetainingKnownHostileEngagementIntent() {
        TacticalIntent intent = planner.plan(
                List.of(contact(501L, 500d, 500d, TrackState.InformationState.FIRE_CONTROL,
                        ContactDisposition.HOSTILE)),
                context(TacticalPosture.HOLD, 0d, 0d, false, 0d, 0d, 0d));

        assertTrue(intent.targetSelected());
        assertEquals(0d, intent.movementAxisX(), EPSILON);
        assertEquals(0d, intent.movementAxisY(), EPSILON);
        assertTrue(intent.fireRequested());
    }

    @Test
    void emptyOrFriendlyKnowledgeProducesCanonicalNoTargetIntent() {
        TacticalContext hold = context(TacticalPosture.HOLD, 0d, 0d, false, 0d, 0d, 0d);

        TacticalIntent empty = planner.plan(List.of(), hold);
        TacticalIntent friendly = planner.plan(
                List.of(contact(601L, 100d, 0d, TrackState.InformationState.FIRE_CONTROL,
                        ContactDisposition.FRIENDLY)),
                hold);

        assertFalse(empty.targetSelected());
        assertEquals(0L, empty.targetId());
        assertFalse(friendly.targetSelected());
        assertEquals(empty, friendly);
    }

    @Test
    void screenRequiresExplicitProtectedGeometry() {
        assertThrows(IllegalArgumentException.class,
                () -> context(TacticalPosture.SCREEN, 0d, 0d, false, 0d, 0d, 200d));
        assertThrows(IllegalArgumentException.class,
                () -> context(TacticalPosture.HOLD, 0d, 0d, false, 10d, 0d, 0d));
    }

    private static TacticalContext context(
            TacticalPosture posture,
            double actorX,
            double actorY,
            boolean protectedKnown,
            double protectedX,
            double protectedY,
            double screenRadiusM) {
        return new TacticalContext(
                posture,
                actorX,
                actorY,
                protectedKnown,
                protectedX,
                protectedY,
                screenRadiusM,
                100d,
                2_000d,
                20d);
    }

    private static ObservedContact contact(
            long id,
            double x,
            double y,
            TrackState.InformationState informationState,
            ContactDisposition disposition) {
        return new ObservedContact(
                new TrackState(
                        id,
                        informationState,
                        true,
                        x,
                        y,
                        new TrackCovariance(100d, 0.0001d, 100d),
                        1d,
                        100d,
                        2,
                        4),
                disposition);
    }
}
