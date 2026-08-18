package com.spacesim.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage19GuidedImpactOrderingAuditTest {
    @Test
    void movingBodyDetectorFindsAnEarlierInterceptorCrossingBeforeLaterShipImpactFraction() {
        var contact = Stage19GuidedImpactOrderingAudit.firstMovingBodyContact(
                0d, 0d,
                100d, 0d,
                2d,
                50d, 40d,
                50d, -40d,
                2d);

        assertTrue(contact.isPresent());
        assertEquals(0.5d, contact.getAsDouble(), 0.06d);
        double hypotheticalLaterShipImpactFraction = 0.8d;
        assertTrue(contact.getAsDouble() < hypotheticalLaterShipImpactFraction,
                "audit helper must be capable of detecting a physically earlier interceptor contact");
    }
}