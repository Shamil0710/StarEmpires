package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicGoalTaxonomyTest {
    @Test
    void exposesAllCanonicalStage21BGoalFamilies() {
        assertEquals(Set.of(
                        "secure-route", "escort", "claim", "deter", "coerce", "raid", "blockade", "invade",
                        "stockpile", "explore", "recover", "obtain-access", "defend"),
                java.util.Arrays.stream(StrategicGoalType.values())
                        .map(StrategicGoalType::wireId)
                        .collect(Collectors.toSet()));
        assertEquals(13, StrategicGoalType.values().length);
    }

    @Test
    void neutralDoctrineCannotInventEscalatoryIntent() {
        FactionStrategicDoctrineProfile neutral = FactionStrategicDoctrineProfile.neutral();

        assertFalse(neutral.enables(StrategicGoalType.COERCE));
        assertFalse(neutral.enables(StrategicGoalType.RAID));
        assertFalse(neutral.enables(StrategicGoalType.BLOCKADE));
        assertFalse(neutral.enables(StrategicGoalType.INVADE));
        assertTrue(neutral.enables(StrategicGoalType.DEFEND));
        assertTrue(neutral.enables(StrategicGoalType.OBTAIN_ACCESS));
    }

    @Test
    void explicitDoctrineCanEnableEscalatoryFamilyWithoutGrantingExecutionAuthority() {
        FactionStrategicDoctrineProfile profile = FactionStrategicDoctrineProfile.neutral()
                .withPreference(StrategicGoalType.RAID, 6_000);

        assertEquals(6_000, profile.preferenceBasisPoints(StrategicGoalType.RAID));
        assertEquals(0, FactionStrategicDoctrineProfile.neutral()
                .preferenceBasisPoints(StrategicGoalType.RAID));
    }

    @Test
    void roadmapScoreIncludesUrgencyValueFeasibilityAndDoctrine() {
        StrategicGoalEvidence evidence = new StrategicGoalEvidence(
                InterestKind.BORDER_SECURITY,
                "border:alpha",
                8_000,
                List.of(new ObservationEvidence(
                        ObservationChannel.LOCAL_SENSOR_REPORT,
                        "report:alpha",
                        10L,
                        20L)));
        StrategicGoalCandidate candidate = new StrategicGoalCandidate(
                StrategicGoalType.DEFEND,
                "border:alpha",
                evidence,
                8_000,
                9_000,
                5_000,
                5_000,
                StrategicPlanningEnvelope.balanced(1L),
                List.of(),
                -1L,
                12L,
                StrategicGoalOutcomeSignal.NONE);

        assertEquals(1_800, candidate.effectivePriorityBasisPoints());
    }
}
