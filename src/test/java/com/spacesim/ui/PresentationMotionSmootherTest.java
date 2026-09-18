package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresentationMotionSmootherTest {
    @Test
    void newObjectsStartAtAuthoritativePointThenEaseTowardFixedTickUpdates() {
        PresentationMotionSmoother smoother = new PresentationMotionSmoother();

        var initial = smoother.update("fleet:1", 10d, 20d, 1f / 60f);
        assertEquals(10d, initial.x(), 1e-9);
        assertEquals(20d, initial.y(), 1e-9);

        var moved = smoother.update("fleet:1", 110d, 20d, 1f / 60f);
        assertTrue(moved.x() > 10d);
        assertTrue(moved.x() < 110d);

        PresentationMotionSmoother.Point settled = moved;
        for (int frame = 0; frame < 120; frame++) {
            settled = smoother.update("fleet:1", 110d, 20d, 1f / 60f);
        }
        assertEquals(110d, settled.x(), 1e-6);
        assertEquals(20d, settled.y(), 1e-9);
    }

    @Test
    void exponentialResponseIsEffectivelyFrameRateIndependent() {
        PresentationMotionSmoother thirtyFps = new PresentationMotionSmoother();
        PresentationMotionSmoother sixtyFps = new PresentationMotionSmoother();
        thirtyFps.update("fleet:1", 0d, 0d, 0f);
        sixtyFps.update("fleet:1", 0d, 0d, 0f);

        var oneStep = thirtyFps.update("fleet:1", 100d, -50d, 1f / 30f);
        sixtyFps.update("fleet:1", 100d, -50d, 1f / 60f);
        var twoSteps = sixtyFps.update("fleet:1", 100d, -50d, 1f / 60f);

        assertEquals(oneStep.x(), twoSteps.x(), 1e-5);
        assertEquals(oneStep.y(), twoSteps.y(), 1e-5);
    }

    @Test
    void removedObjectDoesNotCarryOldPresentationPositionWhenIdentityReturns() {
        PresentationMotionSmoother smoother = new PresentationMotionSmoother();
        smoother.update("fleet:1", 0d, 0d, 0f);
        smoother.update("fleet:1", 100d, 0d, 1f / 60f);
        smoother.retain(Set.of());

        var returned = smoother.update("fleet:1", 500d, 25d, 1f / 60f);
        assertEquals(500d, returned.x(), 1e-9);
        assertEquals(25d, returned.y(), 1e-9);
    }
}
