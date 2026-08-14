package com.spacesim.player;

import com.spacesim.world.ConstructionProjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16ConstructionOwnershipMutationRegressionTest {
    @Test
    void fleetOrdersAndThreatIntelPreserveOwnedConstructionProjects() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_303L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        ConstructionProjectId projectId = construction.createProject("station.mining_base", 640f, 560f);

        PlayerFleetOrderService orders = new PlayerFleetOrderService(runtime);
        assertTrue(orders.issue(PlayerFleetOrderState.hold(runtime.player().activeFleetId())));
        assertEquals(java.util.List.of(projectId), runtime.player().ownedConstructionProjectIds());

        PlayerThreatIntelService intel = new PlayerThreatIntelService(runtime);
        assertTrue(intel.observeSystem(
                runtime.world().getActiveSystemId(),
                2.5f,
                0.8f,
                runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow().getClock().getTick()));
        assertEquals(java.util.List.of(projectId), runtime.player().ownedConstructionProjectIds());
    }
}
