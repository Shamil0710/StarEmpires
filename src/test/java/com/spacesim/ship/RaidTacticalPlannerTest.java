package com.spacesim.ship;

import com.spacesim.ship.ObservedThreatAssessmentService.ContactDisposition;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalIntent;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalPosture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidTacticalPlannerTest {
    @Test
    void knownHostileTrackedContactUsesExistingInterceptAndFireAdmission() {
        RaidTacticalPlanner planner = new RaidTacticalPlanner();
        ObservedContact hostile = new ObservedContact(
                track(51L, TrackState.InformationState.TRACKED, 100d, 25d, 9d),
                ContactDisposition.HOSTILE);

        TacticalIntent first = planner.intercept(List.of(hostile), 0d, 0d, 10d, 1_000d, 30d);
        TacticalIntent second = planner.intercept(List.of(hostile), 0d, 0d, 10d, 1_000d, 30d);

        assertEquals(first, second);
        assertEquals(TacticalPosture.INTERCEPT, first.posture());
        assertTrue(first.targetSelected());
        assertEquals(51L, first.targetId());
        assertTrue(first.movementAxisX() > 0d);
        assertTrue(first.movementAxisY() > 0d);
        assertTrue(first.fireRequested());
    }

    @Test
    void absentOrUnknownContactCannotBecomeOmniscientRaidFireTarget() {
        RaidTacticalPlanner planner = new RaidTacticalPlanner();
        TacticalIntent absent = planner.intercept(List.of(), 0d, 0d, 10d, 1_000d, 30d);
        assertFalse(absent.targetSelected());
        assertFalse(absent.fireRequested());

        ObservedContact unknown = new ObservedContact(
                track(52L, TrackState.InformationState.FIRE_CONTROL, 80d, 0d, 9d),
                ContactDisposition.UNKNOWN);
        TacticalIntent cautious = planner.intercept(List.of(unknown), 0d, 0d, 10d, 1_000d, 30d);
        assertTrue(cautious.targetSelected());
        assertFalse(cautious.fireRequested(),
                "raid wrapper must not convert unknown disposition into autonomous hostile fire");
    }

    private static TrackState track(
            long targetId,
            TrackState.InformationState informationState,
            double x,
            double y,
            double measurementSeconds) {
        return new TrackState(
                targetId,
                informationState,
                true,
                x,
                y,
                new TrackCovariance(25d, 0.01d, 25d),
                0.95d,
                measurementSeconds,
                1,
                1);
    }
}
