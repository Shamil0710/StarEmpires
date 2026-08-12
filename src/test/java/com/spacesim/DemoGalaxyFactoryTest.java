package com.spacesim;

import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoGalaxyFactoryTest {
    @Test
    void demoGalaxyСодержитТриЭкономическиЖивыеСистемыИДваJump() {
        WorldState state = DemoGalaxyFactory.createState(
                0x5EED2026L,
                ContentCatalogLoader.loadDefault());

        assertEquals(3, state.topology().systems().size());
        assertEquals(2, state.topology().sectors().size());
        assertEquals(2, state.topology().connections().size());
        assertEquals(3, state.systems().size());
        assertEquals(
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                state.topology().neighbors(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).get(0));
    }

    @Test
    void runtimeDemoПродвигаетRemoteЭкономикиStrategicRate() {
        WorldSimulation world = DemoGalaxyFactory.create(0x5EED2026L);

        for (int tick = 0; tick < 100; tick++) {
            world.advanceFrame(0.1f);
        }

        assertEquals(100L, world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getClock().getTick());
        assertEquals(100L, world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID)
                .orElseThrow().getClock().getTick());
        assertEquals(100L, world.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow().getClock().getTick());
        assertEquals(20L, world.getTotalStrategicUpdatesExecuted());
        assertTrue(world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID)
                .orElseThrow().getLedger().size() > 0);
        assertTrue(world.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID)
                .orElseThrow().getLedger().size() > 0);
    }
}
