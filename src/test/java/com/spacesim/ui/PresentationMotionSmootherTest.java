package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PresentationMotionSmootherTest {
    @Test
    void newObjectsStartAtAuthoritativePoint() {
        PresentationMotionSmoother smoother = new PresentationMotionSmoother();

        var initial = smoother.update("fleet:1", 10L, 0.75d, 10d, 20d);

        assertEquals(10d, initial.x(), 1e-9);
        assertEquals(20d, initial.y(), 1e-9);
    }

    @Test
    void fixedTickSamplesInterpolateAtConstantPresentationVelocity() {
        PresentationMotionSmoother smoother = new PresentationMotionSmoother();
        smoother.update("fleet:1", 10L, 0d, 0d, 0d);

        assertEquals(0d, smoother.update("fleet:1", 11L, 0d, 100d, 40d).x(), 1e-9);
        assertPoint(smoother.update("fleet:1", 11L, 0.25d, 100d, 40d), 25d, 10d);
        assertPoint(smoother.update("fleet:1", 11L, 0.50d, 100d, 40d), 50d, 20d);
        assertPoint(smoother.update("fleet:1", 11L, 0.75d, 100d, 40d), 75d, 30d);

        // The next fixed tick begins exactly from the previous authoritative endpoint. There is no
        // exponential speed pulse or camera-side catch-up phase.
        assertPoint(smoother.update("fleet:1", 12L, 0d, 200d, 80d), 100d, 40d);
        assertPoint(smoother.update("fleet:1", 12L, 0.50d, 200d, 80d), 150d, 60d);
    }

    @Test
    void resultDependsOnAuthoritativeClockAlphaNotRenderCallCount() {
        PresentationMotionSmoother sparse = new PresentationMotionSmoother();
        PresentationMotionSmoother dense = new PresentationMotionSmoother();
        sparse.update("fleet:1", 1L, 0d, 0d, 0d);
        dense.update("fleet:1", 1L, 0d, 0d, 0d);

        var sparseHalf = sparse.update("fleet:1", 2L, 0.5d, 120d, -60d);

        dense.update("fleet:1", 2L, 0.1d, 120d, -60d);
        dense.update("fleet:1", 2L, 0.2d, 120d, -60d);
        dense.update("fleet:1", 2L, 0.35d, 120d, -60d);
        var denseHalf = dense.update("fleet:1", 2L, 0.5d, 120d, -60d);

        assertEquals(sparseHalf.x(), denseHalf.x(), 1e-9);
        assertEquals(sparseHalf.y(), denseHalf.y(), 1e-9);
    }

    @Test
    void followCameraLocksInterpolatedShipToWholeScreenCenterEveryFrame() {
        PresentationMotionSmoother smoother = new PresentationMotionSmoother();
        MapCameraState camera = new MapCameraState();
        camera.inspect(100_000d);
        float screenCenterX = 960f;
        float screenCenterY = 540f;

        smoother.update("fleet:1", 100L, 0d, 100d, 200d);
        for (int tick = 101; tick <= 106; tick++) {
            double targetX = 100d + (tick - 100) * 300d;
            double targetY = 200d + (tick - 100) * 125d;
            for (int step = 0; step <= 8; step++) {
                double alpha = step / 8d;
                var displayed = smoother.update("fleet:1", tick, alpha, targetX, targetY);

                camera.focus(displayed.x(), displayed.y(), screenCenterX, screenCenterY);

                assertEquals(screenCenterX,
                        camera.transformX(displayed.x(), screenCenterX), 1e-3f);
                assertEquals(screenCenterY,
                        camera.transformY(displayed.y(), screenCenterY), 1e-3f);
            }
        }
    }

    @Test
    void sameTickDiscontinuitySnapsInsteadOfInventingMovement() {
        PresentationMotionSmoother smoother = new PresentationMotionSmoother();
        smoother.update("fleet:1", 7L, 0.4d, 10d, 20d);

        var replaced = smoother.update("fleet:1", 7L, 0.8d, 900d, -300d);

        assertPoint(replaced, 900d, -300d);
    }

    @Test
    void tickRegressionResetsPresentationHistory() {
        PresentationMotionSmoother smoother = new PresentationMotionSmoother();
        smoother.update("fleet:1", 50L, 0d, 0d, 0d);
        smoother.update("fleet:1", 51L, 0.8d, 100d, 0d);

        var restored = smoother.update("fleet:1", 12L, 0.3d, 500d, 25d);

        assertPoint(restored, 500d, 25d);
    }

    @Test
    void removedObjectDoesNotCarryOldPresentationPositionWhenIdentityReturns() {
        PresentationMotionSmoother smoother = new PresentationMotionSmoother();
        smoother.update("fleet:1", 10L, 0d, 0d, 0d);
        smoother.update("fleet:1", 11L, 0.5d, 100d, 0d);
        smoother.retain(Set.of());

        var returned = smoother.update("fleet:1", 12L, 0.6d, 500d, 25d);

        assertPoint(returned, 500d, 25d);
    }

    private static void assertPoint(PresentationMotionSmoother.Point point, double x, double y) {
        assertEquals(x, point.x(), 1e-9);
        assertEquals(y, point.y(), 1e-9);
    }
}
