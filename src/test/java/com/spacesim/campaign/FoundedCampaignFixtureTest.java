package com.spacesim.campaign;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("slow")
class FoundedCampaignFixtureTest {
    @Test
    void runtimeMutationsCannotReachAnotherRestoreOrTheCachedCheckpoint() {
        var first = FoundedCampaignFixture.restore();
        var second = FoundedCampaignFixture.restore();
        var baseline = second.captureState();
        assertEquals(baseline, first.captureState());

        first.advanceFrame(first.coordinator().session().fixedStepSeconds());

        assertNotEquals(baseline, first.captureState());
        assertEquals(baseline, second.captureState());
        assertEquals(baseline, FoundedCampaignFixture.restore().captureState());
    }
}
