package com.spacesim.player;

import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerJumpNavigationModelTest {

    @Test
    void everySelectableDestinationIsAnImmediateTopologyNeighbor() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        GalaxyTopology topology = LargeDemoGalaxyFactory.createState(20_001L, content).topology();

        for (StarSystemNode system : topology.systems()) {
            List<StarSystemId> neighbors = PlayerJumpNavigationModel.neighbors(topology, system.id());
            for (int index = -neighbors.size() * 2; index <= neighbors.size() * 2; index++) {
                StarSystemId selected = PlayerJumpNavigationModel.selectedDestination(topology, system.id(), index);
                if (neighbors.isEmpty()) {
                    assertNull(selected);
                    assertEquals(-1,
                            PlayerJumpNavigationModel.normalizeSelectionIndex(topology, system.id(), index));
                } else {
                    assertTrue(neighbors.contains(selected));
                    assertEquals(Math.floorMod(index, neighbors.size()),
                            PlayerJumpNavigationModel.normalizeSelectionIndex(topology, system.id(), index));
                }
            }
        }
    }

    @Test
    void selectionCyclesAcrossBranchesInsteadOfInventingLongRangeDestinations() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        GalaxyTopology topology = LargeDemoGalaxyFactory.createState(20_002L, content).topology();
        StarSystemNode branch = topology.systems().stream()
                .filter(system -> topology.neighbors(system.id()).size() >= 2)
                .findFirst()
                .orElseThrow();

        StarSystemId first = PlayerJumpNavigationModel.selectedDestination(topology, branch.id(), 0);
        StarSystemId second = PlayerJumpNavigationModel.selectedDestination(topology, branch.id(), 1);

        assertNotEquals(first, second);
        assertTrue(topology.neighbors(branch.id()).contains(first));
        assertTrue(topology.neighbors(branch.id()).contains(second));
        assertEquals(first,
                PlayerJumpNavigationModel.selectedDestination(
                        topology, branch.id(), topology.neighbors(branch.id()).size()));
    }
}
