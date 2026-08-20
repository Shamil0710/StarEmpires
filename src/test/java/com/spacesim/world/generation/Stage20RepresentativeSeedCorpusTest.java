package com.spacesim.world.generation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentativeSeedCorpusTest {
    private static final Path EVIDENCE_DIRECTORY = Path.of("target", "stage20e-evidence");

    @Test
    void corpusIsFixedContiguousAndChosenIndependentlyOfResults() {
        assertEquals(
                LongStream.rangeClosed(1L, 16L).boxed().toList(),
                Stage20RepresentativeSeedCorpus.seeds());
        assertEquals(16, Stage20RepresentativeSeedCorpus.seeds().stream().distinct().count());
    }

    @Test
    void currentCorpusProducesMachineReadableMeasuredEvidenceWithoutPassQuota() throws IOException {
        var evidence = Stage20RepresentativeSeedCorpus.evaluateCurrent();
        var report = evidence.batch();

        assertEquals(Stage20RepresentativeSeedCorpus.EVIDENCE_SCHEMA_VERSION, evidence.schemaVersion());
        assertEquals(Stage20RepresentativeSeedCorpus.CURRENT_VERSION, evidence.corpusVersion());
        assertEquals(Stage20RepresentativeGeneratedWorldProbeProfile.CURRENT_VERSION,
                evidence.representativeProfileVersion());
        assertEquals(Stage20GeneratedWorldProductionProbe.CURRENT_VERSION, evidence.productionProbeVersion());
        assertEquals(Stage20RepresentativeSeedCorpus.seeds(), report.requestedSeeds());
        assertEquals(16, report.seedResults().size());
        assertEquals(16,
                report.acceptedSeedCount()
                        + report.rejectedSeedCount()
                        + report.unresolvedAuthoritySeedCount());
        assertEquals(1d,
                report.acceptedFraction()
                        + report.rejectedFraction()
                        + report.unresolvedAuthorityFraction(),
                1e-12d);
        assertTrue(evidence.stage22ReviewRequired());

        String json = Stage20RepresentativeSeedCorpus.toJson(evidence);
        assertTrue(json.contains("\"corpusVersion\": \"stage20e.representative-seed-corpus.v1\""));
        assertTrue(json.contains("\"rootSeed\": 1"));
        assertTrue(json.contains("\"rootSeed\": 16"));
        assertTrue(json.contains("\"failureReasonCounts\""));
        assertTrue(json.contains("\"seedResults\""));

        Files.createDirectories(EVIDENCE_DIRECTORY);
        Path evidenceFile = EVIDENCE_DIRECTORY.resolve(Stage20RepresentativeSeedCorpus.EVIDENCE_FILE_NAME);
        Files.writeString(evidenceFile, json, StandardCharsets.UTF_8);
        assertEquals(json, Files.readString(evidenceFile, StandardCharsets.UTF_8));
    }
}
