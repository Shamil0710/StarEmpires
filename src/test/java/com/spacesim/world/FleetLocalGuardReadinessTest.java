package com.spacesim.world;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetLocalGuardReadinessTest {
    @Test void stationaryGuardUsesExistingCombatFloorsWithoutInventingServiceAccessOrTravelFuel() {
        var local = new FleetReadinessState(5000, 1, 0, 5000, 1, 0, 0);
        assertDoesNotThrow(() -> FleetOrderSubmissionService.validateLocalGuardReadiness(local, new FleetId(1)));
        assertFalse(local.missionCapable(1));
        assertEquals(0, local.supplyAccessBps()); assertEquals(0, local.propellantBps());
        for (var unready : List.of(
                new FleetReadinessState(4999, 1, 0, 5000, 1, 0, 0),
                new FleetReadinessState(5000, 0, 0, 5000, 1, 0, 0),
                new FleetReadinessState(5000, 1, 0, 4999, 1, 0, 0),
                new FleetReadinessState(5000, 1, 0, 5000, 0, 0, 0),
                FleetReadinessState.unavailable()))
            assertThrows(IllegalStateException.class, () ->
                    FleetOrderSubmissionService.validateLocalGuardReadiness(unready, new FleetId(1)));
    }
}
