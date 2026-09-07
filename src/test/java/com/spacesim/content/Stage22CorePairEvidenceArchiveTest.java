package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression coverage for deterministic M22.6 evidence JSON serialization. */
class Stage22CorePairEvidenceArchiveTest {
    @Test
    void serializesPrivateNestedRecordsWithoutChangingTheirVisibility() {
        PrivateEvidence evidence = new PrivateEvidence(
                "prepared-defense",
                List.of(new PrivateMetric("participants", 2), new PrivateMetric("byteStable", 1)));

        assertEquals(
                "{\"metrics\":[{\"name\":\"participants\",\"value\":2},{\"name\":\"byteStable\",\"value\":1}],\"scenario\":\"prepared-defense\"}",
                Stage22CorePairEvidenceArchive.json(evidence));
    }

    private record PrivateEvidence(String scenario, List<PrivateMetric> metrics) { }

    private record PrivateMetric(String name, int value) { }
}
