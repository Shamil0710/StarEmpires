package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic Stage-17/21F policy for political recognition during territorial stabilization.
 *
 * <p>Recognition never creates physical evidence, claims or control. It only lowers the amount of
 * uninterrupted qualifying physical stabilization time required for an already-declared claim.
 * The policy is derived entirely from persisted Stage-17 claim-recognition rows, so no new authority
 * or persistence state is introduced.</p>
 */
final class TerritorialRecognitionStabilizationPolicy {
    private static final long REDUCTION_PER_RECOGNITION_TICKS = 60L;
    private static final long MAXIMUM_REDUCTION_TICKS = 300L;

    private TerritorialRecognitionStabilizationPolicy() {
        throw new AssertionError("No instances");
    }

    /**
     * Resolves the physical stabilization duration after bounded political-recognition credit.
     *
     * @param strategies persistent Stage-17 strategic/recognition states
     * @param claimantFactionId stable claimant faction identity
     * @param systemId claimed system
     * @param baseRequiredTicks ordinary no-recognition physical stabilization duration
     * @return positive required qualifying ticks, never less than base-minus-max-credit
     */
    static long requiredTicks(
            List<FactionStrategicState> strategies,
            String claimantFactionId,
            StarSystemId systemId,
            long baseRequiredTicks) {
        Objects.requireNonNull(strategies, "strategies");
        String claimant = Objects.requireNonNull(claimantFactionId, "claimantFactionId").strip();
        StarSystemId system = Objects.requireNonNull(systemId, "systemId");
        if (claimant.isEmpty() || baseRequiredTicks <= 0L) {
            throw new IllegalArgumentException("invalid territorial stabilization policy input");
        }
        long recognitionCount = 0L;
        for (FactionStrategicState recognizer : strategies) {
            for (TerritorialRecognitionState recognition : recognizer.territorialRecognitions()) {
                if (recognition.kind() == TerritorialRecognitionState.Kind.CLAIM
                        && recognition.targetFactionContentId().equals(claimant)
                        && recognition.systemId().equals(system)) {
                    recognitionCount++;
                }
            }
        }
        long maximumCredit = Math.min(MAXIMUM_REDUCTION_TICKS, Math.max(0L, baseRequiredTicks - 1L));
        long credit = Math.min(
                maximumCredit,
                Math.multiplyExact(
                        Math.min(recognitionCount, MAXIMUM_REDUCTION_TICKS / REDUCTION_PER_RECOGNITION_TICKS),
                        REDUCTION_PER_RECOGNITION_TICKS));
        return baseRequiredTicks - credit;
    }
}
