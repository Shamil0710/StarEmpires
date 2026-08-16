package com.spacesim.ui;

import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalaxyStrategicMapModelTest {

    @Test
    void largeDemoSnapshotMirrorsAuthoritativeTopologyTerritoryAndFactionState() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState state = LargeDemoGalaxyFactory.createState(24_001L, content);
        GalaxyTopology topology = state.topology();
        StarSystemId active = topology.systems().get(0).id();
        StarSystemId selected = topology.neighbors(active).get(0);
        WorldSimulation world = WorldSimulation.restore(state, content, active,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        GalaxyStrategicMapSnapshot snapshot = GalaxyStrategicMapModel.capture(
                world, content, active, selected);

        assertEquals(100, snapshot.systems().size());
        assertEquals(topology.connections().size(), snapshot.edges().size());
        assertEquals(8, snapshot.factions().size());
        assertEquals(100, snapshot.factions().stream()
                .mapToInt(GalaxyStrategicMapSnapshot.FactionView::controlledSystems)
                .sum());
        assertEquals(1L, snapshot.systems().stream().filter(GalaxyStrategicMapSnapshot.SystemView::active).count());
        assertEquals(1L, snapshot.systems().stream()
                .filter(GalaxyStrategicMapSnapshot.SystemView::selectedNeighbor).count());

        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.systems()) {
            assertEquals(topology.neighbors(system.id()).size(), system.neighborCount());
            assertEquals(world.controllingFaction(system.id()).orElse(null), system.controllerFactionId());
        }
        assertTrue(topology.neighbors(active).contains(snapshot.selectedNeighborId()));
    }

    @Test
    void selectedJumpMarkerCannotPointOutsideDirectNeighborGraph() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState state = LargeDemoGalaxyFactory.createState(24_002L, content);
        GalaxyTopology topology = state.topology();
        StarSystemId active = topology.systems().get(0).id();
        StarSystemId nonNeighbor = topology.systems().stream()
                .map(system -> system.id())
                .filter(id -> !id.equals(active) && !topology.neighbors(active).contains(id))
                .findFirst()
                .orElseThrow();
        WorldSimulation world = WorldSimulation.restore(state, content, active,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        assertThrows(IllegalArgumentException.class,
                () -> GalaxyStrategicMapModel.capture(world, content, active, nonNeighbor));
    }
}
