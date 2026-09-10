package com.spacesim.content;

import java.util.List;

/** Test-only batch size selection; cannot alter gameplay seeds, policies or acceptance assertions. */
final class Stage22CorePairEvidenceProfile {
    private Stage22CorePairEvidenceProfile() { }

    static int seedCount(int normalCount) {
        String profile = System.getProperty("stage22.evidenceProfile", "normal");
        return switch (profile) {
            case "normal" -> normalCount;
            case "rc" -> Math.max(normalCount, Stage22CorePairExperimentProtocol.RELEASE_CANDIDATE_SEED_COUNT);
            default -> throw new IllegalArgumentException("Unknown Stage-22 evidence profile: " + profile);
        };
    }

    static List<Stage22CorePairExperimentProtocol.RunCoordinate> schedule(int normalCount) {
        return Stage22CorePairExperimentProtocol.pairedSchedule(seedCount(normalCount));
    }
}
