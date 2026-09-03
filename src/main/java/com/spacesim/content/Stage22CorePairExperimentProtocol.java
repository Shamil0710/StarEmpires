package com.spacesim.content;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic M22.6 seed/permutation scheduler for paired core-faction evidence.
 *
 * <p>The scheduler owns no gameplay randomness. It only produces the paired experiment coordinates
 * required by the balance framework so common scenario authorities receive the same seed twice with
 * mirrored slot/topology assignment.</p>
 */
public final class Stage22CorePairExperimentProtocol {
    /** Minimum paired seed count for a tuning candidate. */
    public static final int TUNING_SEED_COUNT = 30;
    /** Minimum paired seed count for materially stochastic release-candidate regression. */
    public static final int RELEASE_CANDIDATE_SEED_COUNT = 100;
    /** Stable first core-pair experiment seed. */
    public static final long FIRST_SEED = 22_600_001L;

    private Stage22CorePairExperimentProtocol() {
        throw new AssertionError("utility class");
    }

    /**
     * Builds the canonical two-permutation run schedule.
     *
     * @param seedCount number of distinct paired seeds
     * @return immutable schedule containing default and mirrored run for every seed
     */
    public static List<RunCoordinate> pairedSchedule(int seedCount) {
        if (seedCount <= 0) {
            throw new IllegalArgumentException("seedCount must be positive");
        }
        ArrayList<RunCoordinate> runs = new ArrayList<>(Math.multiplyExact(seedCount, 2));
        for (int index = 0; index < seedCount; index++) {
            long seed = Math.addExact(FIRST_SEED, index);
            runs.add(new RunCoordinate(seed, Permutation.DEFAULT));
            runs.add(new RunCoordinate(seed, Permutation.MIRRORED));
        }
        return List.copyOf(runs);
    }

    /** @return canonical 30-seed tuning schedule */
    public static List<RunCoordinate> tuningSchedule() {
        return pairedSchedule(TUNING_SEED_COUNT);
    }

    /** @return canonical 100-seed release-candidate schedule */
    public static List<RunCoordinate> releaseCandidateSchedule() {
        return pairedSchedule(RELEASE_CANDIDATE_SEED_COUNT);
    }

    /** Mirrored assignment while faction identity and doctrine stay attached to the faction. */
    public enum Permutation {
        /** Empire slot A / Union slot B / default topology side. */ DEFAULT,
        /** Union slot A / Empire slot B / swapped topology and hazard side. */ MIRRORED
    }

    /**
     * One deterministic experimental coordinate.
     *
     * @param seed common authoritative seed shared by both permutations
     * @param permutation slot/topology assignment for this run
     */
    public record RunCoordinate(long seed, Permutation permutation) {
        /** Validates one run coordinate. */
        public RunCoordinate {
            if (seed < 0L) {
                throw new IllegalArgumentException("seed must be non-negative");
            }
            java.util.Objects.requireNonNull(permutation, "permutation");
        }
    }
}
