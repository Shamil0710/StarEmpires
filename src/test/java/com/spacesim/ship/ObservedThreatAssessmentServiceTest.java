package com.spacesim.ship;

import com.spacesim.ship.ObservedThreatAssessmentService.Assessment;
import com.spacesim.ship.ObservedThreatAssessmentService.ContactDisposition;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservedThreatAssessmentServiceTest {
    private final ObservedThreatAssessmentService service = new ObservedThreatAssessmentService();

    @Test
    void sameVisibleSnapshotProducesSameDeterministicOrderingWithoutMutatingInput() {
        ObservedContact higherId = new ObservedContact(freshTrack(202L, 1_000d), ContactDisposition.HOSTILE);
        ObservedContact lowerId = new ObservedContact(freshTrack(101L, 1_000d), ContactDisposition.HOSTILE);
        List<ObservedContact> input = new ArrayList<>(List.of(higherId, lowerId));
        List<ObservedContact> originalOrder = List.copyOf(input);

        List<Assessment> first = service.assess(input, 0d, 0d, 100d, 2_000d, 20d);
        List<Assessment> second = service.assess(input, 0d, 0d, 100d, 2_000d, 20d);

        assertEquals(first, second);
        assertEquals(originalOrder, input);
        assertEquals(101L, first.get(0).targetId());
        assertEquals(202L, first.get(1).targetId());
    }

    @Test
    void freshPreciseFireControlTrackOutranksStaleUncertainTrack() {
        TrackState fresh = freshTrack(301L, 1_000d);
        TrackState staleUncertain = new TrackState(
                302L,
                TrackState.InformationState.FIRE_CONTROL,
                true,
                1_000d,
                0d,
                new TrackCovariance(4_000_000d, 0.01d, 4_000_000d),
                1d,
                0d,
                1,
                1);

        List<Assessment> assessments = service.assess(
                List.of(
                        new ObservedContact(staleUncertain, ContactDisposition.HOSTILE),
                        new ObservedContact(fresh, ContactDisposition.HOSTILE)),
                0d,
                0d,
                100d,
                2_000d,
                20d);

        assertEquals(301L, assessments.get(0).targetId());
        assertTrue(assessments.get(0).priorityScore() > assessments.get(1).priorityScore());
        assertTrue(assessments.get(0).trackAgeSeconds() < assessments.get(1).trackAgeSeconds());
        assertTrue(assessments.get(0).positionUncertaintyM() < assessments.get(1).positionUncertaintyM());
    }

    @Test
    void dispositionChangesBehavioralPriorityWithoutChangingObservedTrack() {
        TrackState track = freshTrack(401L, 1_500d);

        double friendly = assessOne(track, ContactDisposition.FRIENDLY).priorityScore();
        double unknown = assessOne(track, ContactDisposition.UNKNOWN).priorityScore();
        double hostile = assessOne(track, ContactDisposition.HOSTILE).priorityScore();

        assertEquals(0d, friendly);
        assertTrue(unknown > 0d);
        assertTrue(unknown < hostile);
        assertEquals(track, freshTrack(401L, 1_500d));
    }

    @Test
    void unknownPositionNeverTurnsCanonicalTrackPlaceholdersIntoExactRange() {
        TrackState detected = new TrackState(
                501L,
                TrackState.InformationState.DETECTED,
                false,
                0d,
                0d,
                new TrackCovariance(null, 0.04d, null),
                0.15d,
                95d,
                1,
                1);

        Assessment assessment = assessOne(detected, ContactDisposition.UNKNOWN);

        assertEquals(501L, assessment.targetId());
        assertEquals(TrackState.InformationState.DETECTED, assessment.informationState());
        assertTrue(!assessment.positionKnown());
        assertEquals(0d, assessment.estimatedRangeM());
        assertEquals(0d, assessment.positionUncertaintyM());
        assertTrue(assessment.priorityScore() > 0d);
    }

    @Test
    void actorKnowledgeBoundaryIsExactlyTheSuppliedContactSet() {
        ObservedContact actorAContact = new ObservedContact(freshTrack(601L, 900d), ContactDisposition.HOSTILE);
        ObservedContact actorBOnlyContact = new ObservedContact(freshTrack(602L, 700d), ContactDisposition.HOSTILE);

        List<Assessment> actorA = service.assess(
                List.of(actorAContact), 0d, 0d, 100d, 2_000d, 20d);
        List<Assessment> actorB = service.assess(
                List.of(actorAContact, actorBOnlyContact), 0d, 0d, 100d, 2_000d, 20d);

        assertEquals(List.of(601L), actorA.stream().map(Assessment::targetId).toList());
        assertEquals(2, actorB.size());
        assertTrue(actorB.stream().anyMatch(value -> value.targetId() == 602L));
    }

    @Test
    void validatesNormalizationInputsAndRejectsNullContacts() {
        ObservedContact contact = new ObservedContact(freshTrack(701L, 1_000d), ContactDisposition.HOSTILE);

        assertThrows(IllegalArgumentException.class,
                () -> service.assess(List.of(contact), 0d, 0d, 99d, 2_000d, 20d));
        assertThrows(IllegalArgumentException.class,
                () -> service.assess(List.of(contact), 0d, 0d, 100d, 0d, 20d));
        assertThrows(IllegalArgumentException.class,
                () -> service.assess(List.of(contact), 0d, 0d, 100d, 2_000d, 0d));
        assertThrows(NullPointerException.class,
                () -> new ObservedContact(null, ContactDisposition.HOSTILE));
    }

    private Assessment assessOne(TrackState track, ContactDisposition disposition) {
        return service.assess(
                List.of(new ObservedContact(track, disposition)),
                0d,
                0d,
                100d,
                2_000d,
                20d).get(0);
    }

    private static TrackState freshTrack(long targetId, double xM) {
        return new TrackState(
                targetId,
                TrackState.InformationState.FIRE_CONTROL,
                true,
                xM,
                0d,
                new TrackCovariance(100d, 0.0001d, 100d),
                1d,
                100d,
                2,
                4);
    }
}
