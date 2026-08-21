package com.spacesim.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JumpTransitOperationTest {

    @Test
    void transitCompletesOnlyThroughAuthoritativeSimulationTime() {
        JumpConnection connection = new JumpConnection(
                "jump-a-b",
                "system-a",
                "system-b",
                1000,
                50);

        JumpTransitOperation operation = new JumpTransitOperation(
                "operation-1",
                "ship-1",
                connection,
                100);

        operation.beginTransit();
        operation.update(149);
        assertEquals(TravelState.TRANSIT, operation.state());

        operation.update(150);
        assertEquals(TravelState.COOLDOWN, operation.state());

        operation.arrive();
        assertEquals(TravelState.ARRIVED, operation.state());
    }
}
