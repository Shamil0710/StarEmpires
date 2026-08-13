package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.trade.TradeRoutePlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorldGalacticPlannerFactoryTest {
    @Test
    void worldFactoriesUseCurrentTopologyAndStage10BTiming() {
        WorldSimulation world = DemoGalaxyFactory.create(0x10C0L);

        GalacticPath path = world.createGalacticPathPlanner()
                .findPath(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow();
        TradeRoutePlanner tradePlanner = world.createGalacticTradeRoutePlanner(
                TradeRoutePlanner.ScoringMode.PROFIT_PER_SECOND);

        assertEquals(
                List.of(
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        DemoGalaxyFactory.INNER_SYSTEM_ID,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
                path.systems());
        assertEquals(2, path.jumpCount());
        assertNotNull(tradePlanner);
    }
}
