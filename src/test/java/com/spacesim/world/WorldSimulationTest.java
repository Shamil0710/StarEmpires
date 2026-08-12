package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSimulationTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static final StarSystemId ACTIVE_ID = new StarSystemId(1L);

    @Test
    void remoteSystemsИсполняютсяCoarseRateАLocalОстаётсяFixedRate() {
        WorldSimulation world = WorldSimulation.restore(
                worldState(3),
                CONTENT,
                ACTIVE_ID,
                10,
                2);

        WorldSimulation.AdvanceReport report = null;
        for (int tick = 0; tick < 10; tick++) {
            report = world.advanceFrame(0.1f);
        }

        assertEquals(10L, session(world, 1).getClock().getTick());
        assertEquals(10L, session(world, 2).getClock().getTick());
        assertEquals(10L, session(world, 3).getClock().getTick());
        assertEquals(10L, world.getTotalLocalFixedTicksExecuted());
        assertEquals(2L, world.getTotalStrategicUpdatesExecuted());
        assertEquals(2, report.strategicUpdates());
        assertEquals(0L, report.maximumRemoteLagTicks());
        assertTrue(session(world, 2).getLedger().size() > 0);
        assertTrue(session(world, 3).getLedger().size() > 0);
    }

    @Test
    void remoteBudgetОграничиваетРаботуИLargestLagSchedulerДетерминирован() {
        WorldSimulation world = WorldSimulation.restore(
                worldState(4),
                CONTENT,
                ACTIVE_ID,
                10,
                1);

        for (int tick = 0; tick < 10; tick++) {
            world.advanceFrame(0.1f);
        }

        assertEquals(10L, session(world, 2).getClock().getTick());
        assertEquals(0L, session(world, 3).getClock().getTick());
        assertEquals(0L, session(world, 4).getClock().getTick());
        assertEquals(10L, world.getMaximumRemoteLagTicks());
        assertEquals(1L, world.getTotalStrategicUpdatesExecuted());

        WorldSimulation.AdvanceReport second = world.advanceFrame(0f);
        assertEquals(10L, session(world, 3).getClock().getTick());
        assertEquals(0L, session(world, 4).getClock().getTick());
        assertEquals(1, second.strategicUpdates());

        WorldSimulation.AdvanceReport third = world.advanceFrame(0f);
        assertEquals(10L, session(world, 4).getClock().getTick());
        assertEquals(0L, third.maximumRemoteLagTicks());
        assertEquals(3L, world.getTotalStrategicUpdatesExecuted());
    }

    @Test
    void worldSaveRestoreContinuationОстаётсяExactПриStrategicScheduling() {
        WorldState initial = worldState(4);
        WorldSimulation uninterrupted = WorldSimulation.restore(
                initial, CONTENT, ACTIVE_ID, 10, 2);
        WorldSimulation saveSource = WorldSimulation.restore(
                initial, CONTENT, ACTIVE_ID, 10, 2);
        float[] beforeSave = {0.13f, 0.27f, 0.05f, 0.4f, 0.11f};

        for (int cycle = 0; cycle < 45; cycle++) {
            for (float delta : beforeSave) {
                uninterrupted.advanceFrame(delta);
                saveSource.advanceFrame(delta);
            }
        }
        assertEquals(uninterrupted.snapshot(), saveSource.snapshot());

        byte[] savedBytes = WorldStateCodec.encode(saveSource.snapshot());
        WorldSimulation loaded = WorldSimulation.restore(
                WorldStateCodec.decode(savedBytes),
                CONTENT,
                ACTIVE_ID,
                10,
                2);
        assertEquals(saveSource.snapshot(), loaded.snapshot());

        float[] afterLoad = {0.07f, 0.33f, 0.19f, 0.5f, 0.02f};
        for (int cycle = 0; cycle < 35; cycle++) {
            for (float delta : afterLoad) {
                uninterrupted.advanceFrame(delta);
                loaded.advanceFrame(delta);
            }
            assertEquals(
                    uninterrupted.snapshot(),
                    loaded.snapshot(),
                    "World continuation разошёлся после cycle " + cycle);
        }
    }

    @Test
    void remoteEconomyЖивётПриМеньшемЧислеObjectLevelUpdates() {
        WorldSimulation world = WorldSimulation.restore(
                worldState(3),
                CONTENT,
                ACTIVE_ID,
                10,
                2);

        for (int tick = 0; tick < 200; tick++) {
            world.advanceFrame(0.1f);
        }

        assertEquals(200L, world.getTotalLocalFixedTicksExecuted());
        assertEquals(40L, world.getTotalStrategicUpdatesExecuted());
        assertEquals(200L, session(world, 2).getClock().getTick());
        assertEquals(200L, session(world, 3).getClock().getTick());
        assertTrue(session(world, 2).getLedger().size() > 0);
        assertTrue(session(world, 3).getLedger().size() > 0);
        assertNonNegativeInventories(session(world, 2));
        assertNonNegativeInventories(session(world, 3));
    }

    @Test
    void restoreОтклоняетRemoteClockКоторыйУжеВпередиActive() {
        WorldState baseline = worldState(2);
        SimulationSession active = SimulationSession.restore(
                baseline.systems().get(0).simulationState(), CONTENT);
        SimulationSession remote = SimulationSession.restore(
                baseline.systems().get(1).simulationState(), CONTENT);
        remote.advanceFrame(1f);
        WorldState malformed = new WorldState(
                WorldState.CURRENT_VERSION,
                baseline.topology(),
                List.of(
                        new StarSystemSimulationState(ACTIVE_ID, active.snapshot()),
                        new StarSystemSimulationState(new StarSystemId(2L), remote.snapshot())));

        assertThrows(IllegalArgumentException.class, () -> WorldSimulation.restore(
                malformed,
                CONTENT,
                ACTIVE_ID,
                10,
                2));
    }

    private static WorldState worldState(int systemCount) {
        List<StarSystemNode> systems = new ArrayList<>(systemCount);
        List<StarSystemSimulationState> states = new ArrayList<>(systemCount);
        List<JumpConnection> connections = new ArrayList<>();
        for (int index = 1; index <= systemCount; index++) {
            StarSystemId id = new StarSystemId(index);
            systems.add(new StarSystemNode(
                    id,
                    "System " + index,
                    index * 10d,
                    index * -5d));
            states.add(new StarSystemSimulationState(
                    id,
                    SimulationSession.createDemo(0x7000L + index, CONTENT).snapshot()));
            if (index > 1) {
                connections.add(new JumpConnection(new StarSystemId(index - 1L), id));
            }
        }
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "Scheduler Test Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Core", List.copyOf(systems))),
                List.copyOf(connections));
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.copyOf(states));
    }

    private static SimulationSession session(WorldSimulation world, long id) {
        return world.findSession(new StarSystemId(id)).orElseThrow();
    }

    private static void assertNonNegativeInventories(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (inventory == null) {
                continue;
            }
            for (int amount : inventory.stock) {
                assertTrue(amount >= 0, "Strategic update создал отрицательный inventory");
            }
        }
    }
}
