package com.spacesim.player;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeighborOnlyJumpInvariantTest {

    @Test
    void playerAndWorldRejectNonNeighborJumpRequests() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(20_101L);
        PlayerRuntime runtime = scenario.runtime();
        FleetId activeFleet = runtime.player().activeFleetId();

        assertFalse(runtime.world().getTopology().neighbors(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .contains(DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
        assertFalse(runtime.requestJump(DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
        assertTrue(runtime.world().findFleetJump(activeFleet).isEmpty());

        assertThrows(IllegalArgumentException.class, () -> runtime.world().requestFleetJump(
                activeFleet,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                0f,
                0f));
        assertTrue(runtime.world().findFleetJump(activeFleet).isEmpty());

        StarSystemId directNeighbor = DemoGalaxyFactory.INNER_SYSTEM_ID;
        assertTrue(runtime.world().getTopology().neighbors(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .contains(directNeighbor));
        assertTrue(runtime.requestJump(directNeighbor));
        assertTrue(runtime.world().findFleetJump(activeFleet).isPresent());
    }
}
