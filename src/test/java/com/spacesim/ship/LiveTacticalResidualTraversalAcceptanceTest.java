package com.spacesim.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalResidualTraversalAcceptanceTest {
    @Test
    void nativeAndExternalResidualsResolveOncePerCrossingAndCanHitAgainAfterExit() {
        for (boolean external : new boolean[] {true, false}) {
            var result = Stage22ResidualTraversalProbe.run(external);
            assertEquals(1L, result.firstCrossingImpacts());
            assertEquals(2L, result.afterReentryImpacts());
            assertTrue(result.releasedAfterExit());
            assertEquals(result, Stage22ResidualTraversalProbe.run(external));
        }
    }
}
