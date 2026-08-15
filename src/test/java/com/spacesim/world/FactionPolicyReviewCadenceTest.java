package com.spacesim.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionPolicyReviewCadenceTest {
    @Test
    void stableFactionIdProducesDeterministicStaggerWithoutRandomState() {
        FactionPolicyReviewCadence first = FactionPolicyReviewCadence.staggered(1_000L, "faction.alpha");
        FactionPolicyReviewCadence repeat = FactionPolicyReviewCadence.staggered(1_000L, "faction.alpha");
        FactionPolicyReviewCadence other = FactionPolicyReviewCadence.staggered(1_000L, "faction.beta");

        assertEquals(first, repeat);
        assertTrue(first.firstReviewOffsetTicks() >= 0L);
        assertTrue(first.firstReviewOffsetTicks() < first.intervalTicks());
        assertNotEquals(first.firstReviewOffsetTicks(), other.firstReviewOffsetTicks());
    }

    @Test
    void claimedReviewCannotRepeatUntilCompleteAuthoritativeInterval() {
        FactionPolicyReviewCadence cadence = new FactionPolicyReviewCadence(10L, 3L);
        FactionPolicyReviewState initial = FactionPolicyReviewState.INITIAL;

        assertFalse(cadence.isDue(initial, 2L));
        assertTrue(cadence.isDue(initial, 3L));
        FactionPolicyReviewState claimed = cadence.claim(initial, 3L);
        assertEquals(3L, claimed.lastPolicyReviewTick());
        assertFalse(cadence.isDue(claimed, 3L));
        assertFalse(cadence.isDue(claimed, 12L));
        assertTrue(cadence.isDue(claimed, 13L));
        assertThrows(IllegalStateException.class, () -> cadence.claim(claimed, 12L));
    }

    @Test
    void binaryHysteresisHoldsDecisionInsideDeadband() {
        assertFalse(FactionPolicyHysteresis.binaryDecision(false, 699L, 700L, 500L));
        assertTrue(FactionPolicyHysteresis.binaryDecision(false, 700L, 700L, 500L));
        assertTrue(FactionPolicyHysteresis.binaryDecision(true, 501L, 700L, 500L));
        assertFalse(FactionPolicyHysteresis.binaryDecision(true, 500L, 700L, 500L));
    }

    @Test
    void numericPolicyMovesAtMostOneBoundedStepPerReview() {
        assertEquals(1_250, FactionPolicyHysteresis.boundedBasisPointStep(1_000, 5_000, 250));
        assertEquals(4_750, FactionPolicyHysteresis.boundedBasisPointStep(5_000, 1_000, 250));
        assertEquals(1_100, FactionPolicyHysteresis.boundedBasisPointStep(1_000, 1_100, 250));
        assertEquals(1_000, FactionPolicyHysteresis.boundedBasisPointStep(1_000, 1_000, 250));
    }
}
