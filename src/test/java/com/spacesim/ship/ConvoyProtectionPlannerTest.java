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

class ConvoyProtectionPlannerTest {
    @Test
    void hostileObservedTrackProducesDeterministicScreenIntentBetweenConvoyAndThreat() {
        ConvoyProtectionPlanner planner = new ConvoyProtectionPlanner();
        ObservedContact hostile = new ObservedContact(
                track(41L, TrackState.InformationState.TRACKED, 100d, 0d, 9d),
                ContactDisposition.HOSTILE);

        TacticalIntent first = planner.screen(
                List.of(hostile), -40d, 0d, 0d, 0d, 20d, 10d, 1_000d, 30d);
        TacticalIntent second = planner.screen(
                List.of(hostile), -40d, 0d, 0d, 0d, 20d, 10d, 1_000d, 30d);

        assertEquals(first, second);
        assertEquals(TacticalPosture.SCREEN, first.posture());
        assertTrue(first.targetSelected());
        assertEquals(41L, first.targetId());
        assertTrue(first.movementAxisX() > 0d,
                "escort must move toward the screen point on the threat-facing side of the convoy");
        assertEquals(0d, first.movementAxisY(), 1e-12d);
        assertTrue(first.fireRequested(),
                "known-hostile TRACKED contact may request fire through the existing Stage-19B rule");
    }

    @Test
    void contactsAbsentFromActorKnowledgeCannotInfluenceConvoyScreen() {
        ConvoyProtectionPlanner planner = new ConvoyProtectionPlanner();
        TacticalIntent noKnowledge = planner.screen(
                List.of(), -40d, 0d, 0d, 0d, 20d, 10d, 1_000d, 30d);

        assertEquals(TacticalPosture.SCREEN, noKnowledge.posture());
        assertFalse(noKnowledge.targetSelected());
        assertFalse(noKnowledge.fireRequested());
        assertEquals(0d, noKnowledge.movementAxisX(), 1e-12d);
        assertEquals(0d, noKnowledge.movementAxisY(), 1e-12d);
    }

    @Test
    void unknownDispositionCanScreenButCannotRequestFire() {
        ConvoyProtectionPlanner planner = new ConvoyProtectionPlanner();
        ObservedContact unknown = new ObservedContact(
                track(42L, TrackState.InformationState.FIRE_CONTROL, 80d, 20d, 9d),
                ContactDisposition.UNKNOWN);

        TacticalIntent intent = planner.screen(
                List.of(unknown), -20d, -10d, 0d, 0d, 25d, 10d, 1_000d, 30d);

        assertTrue(intent.targetSelected());
        assertFalse(intent.fireRequested(),
                "Stage-19D must not turn an actor-unknown contact into an autonomous hostile target");
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
