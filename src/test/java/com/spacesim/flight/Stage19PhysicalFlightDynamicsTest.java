package com.spacesim.flight;

import com.spacesim.components.TransformComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage19PhysicalFlightDynamicsTest {
    @Test
    void zeroPhysicalThrustPreservesInertialDriftWithoutCreatingAcceleration() {
        TransformComponent transform = new TransformComponent();
        transform.position.set(100f, 50f);
        transform.velocity.set(10f, -4f);

        FlightDynamics.advancePhysical(transform, 20_000_000d, 0d, 500f, 1f, 0f, 2f);

        assertEquals(120f, transform.position.x, 1e-6f);
        assertEquals(42f, transform.position.y, 1e-6f);
        assertEquals(10f, transform.velocity.x, 0f);
        assertEquals(-4f, transform.velocity.y, 0f);
    }

    @Test
    void externallyResolvedPhysicalThrustDrivesExistingInertialIntegrator() {
        TransformComponent transform = new TransformComponent();

        FlightDynamics.advancePhysical(transform, 1_000d, 1_000d, 100f, 1f, 0f, 1f);

        assertEquals(1f, transform.velocity.x, 1e-6f);
        assertEquals(0f, transform.velocity.y, 0f);
        assertEquals(1f, transform.position.x, 1e-6f);
        assertEquals(0f, transform.position.y, 0f);
    }

    @Test
    void invalidPhysicalMassOrNegativeThrustIsRejected() {
        TransformComponent transform = new TransformComponent();

        assertThrows(IllegalArgumentException.class,
                () -> FlightDynamics.advancePhysical(transform, 0d, 1d, 100f, 1f, 0f, 1f));
        assertThrows(IllegalArgumentException.class,
                () -> FlightDynamics.advancePhysical(transform, 1_000d, -1d, 100f, 1f, 0f, 1f));
    }
}
