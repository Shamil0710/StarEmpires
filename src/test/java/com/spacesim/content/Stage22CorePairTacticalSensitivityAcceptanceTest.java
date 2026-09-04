package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic controls precede any normalization/tuning of the core pair. */
class Stage22CorePairTacticalSensitivityAcceptanceTest {
    @Test
    void physicalStartRoundTripHasIdenticalContinuationInEveryMirroredControl() {
        for (var variant : Stage22CorePairTacticalProbe.Variant.values()) {
            for (var coordinate : Stage22CorePairExperimentProtocol.pairedSchedule(1)) {
                var direct = Stage22CorePairTacticalProbe.run(variant, coordinate, false);
                assertTrue(direct.valid(), direct.toString());
                assertEquals(direct, Stage22CorePairTacticalProbe.run(variant, coordinate, true));
            }
        }
    }

    @Test
    void thirtyPairedGeometriesExposeFiniteMagazinesAndActorBoundedSensorLoss() {
        for (var variant : Stage22CorePairTacticalProbe.Variant.values()) {
            var observations = new ArrayList<Stage22CorePairTacticalProbe.Evidence>();
            for (var coordinate : Stage22CorePairExperimentProtocol.tuningSchedule()) {
                var row = Stage22CorePairTacticalProbe.run(variant, coordinate, false);
                observations.add(row);
                assertTrue(row.valid(), row.toString());
                if (variant == Stage22CorePairTacticalProbe.Variant.LIMITED_MAGAZINES) {
                    row.last().weapons().forEach(source -> {
                        assertEquals(4L, source.shotsFired());
                        assertEquals(0L, source.ammunitionRounds());
                    });
                    assertTrue(row.last().control().stream().noneMatch(control -> control.fireAuthorized()));
                }
            }
            assertEquals(60, observations.size());
            Stage22CorePairEvidenceArchive.write("tactical-" + variant.name().toLowerCase(java.util.Locale.ROOT), observations,
                    "30 paired deterministic geometry controls, common Stage-19 tactical policy and exact Stage-22 destroyers. Equal role does not normalize economic burden. Start-state save/replay is tested; mid-flight battle persistence, faction-specific strategic AI and campaign victory are not established by these controls.");
        }
    }
}
