package com.spacesim.campaign;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@Tag("slow")
class GeneratedCampaignFixtureTest {
    @Test
    void restoredOwnersCannotMutateAnotherRuntimeOrTheBaseline() {
        var first = GeneratedCampaignFixture.restoreAuthority();
        var second = GeneratedCampaignFixture.restoreAuthority();
        var baseline = GeneratedCampaignFixture.checkpoint();
        assertEquals(baseline, first.captureState());
        assertEquals(baseline, second.captureState());
        assertNotSame(first.coordinator().runtime().world(), second.coordinator().runtime().world());

        first.advanceFrame(first.coordinator().session().fixedStepSeconds());
        first.smallCraft().reserveIdentityForCompletedProduction();

        assertNotEquals(baseline, first.captureState());
        assertEquals(baseline, second.captureState());
        assertEquals(baseline, GeneratedCampaignFixture.checkpoint());
        assertEquals(baseline.stage21Runtime(),
                GeneratedCampaignFixture.restoreCoordinator().captureState());
    }
}
