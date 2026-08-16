package com.spacesim;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Acceptance for the pre-17.5 100-system manual-test galaxy. */
class LargeDemoGalaxyFactoryTest {

    @Test
    void largeDemoHasOneHundredConnectedVariedSystemsAndEightPhysicalFactions() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState state = LargeDemoGalaxyFactory.createState(0x1005_17L, content);

        assertEquals(100, state.topology().systems().size());
        assertEquals(100, state.systems().size());
        assertEquals(12, state.topology().sectors().size());
        assertEquals("Anchor", state.topology().findSystem(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow().name());
        assertEquals("Corona", state.topology().findSystem(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow().name());
        assertEquals("Frontier", state.topology().findSystem(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).orElseThrow().name());
        assertTrue(state.topology().neighbors(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .contains(DemoGalaxyFactory.INNER_SYSTEM_ID));
        assertTrue(state.topology().neighbors(DemoGalaxyFactory.INNER_SYSTEM_ID)
                .contains(DemoGalaxyFactory.FRONTIER_SYSTEM_ID));
        assertEquals(100, reachableSystemCount(state));
        assertTrue(state.topology().systems().stream().allMatch(system -> !state.topology().neighbors(system.id()).isEmpty()));

        Set<Integer> planetCounts = new HashSet<>();
        Set<Integer> fieldCounts = new HashSet<>();
        for (StarSystemNode system : state.topology().systems()) {
            planetCounts.add(system.planets().size());
            fieldCounts.add(system.asteroidFields().size());
        }
        assertTrue(planetCounts.size() >= 4, "The large demo must visibly vary planetary system structure");
        assertTrue(fieldCounts.size() >= 3, "The large demo must vary strategic asteroid-field structure");

        assertEquals(8, state.factions().size());
        assertEquals(8, state.factionStrategies().size());
        assertEquals(5, state.factionIdentities().size());
        assertTrue(state.factionIdentities().stream()
                .allMatch(identity -> identity.origin() == WorldFactionIdentityState.Origin.WORLD_BOOTSTRAP));

        Set<Integer> physicalFactionRuntimeIds = new TreeSet<>();
        Set<String> marketFingerprints = new HashSet<>();
        for (StarSystemSimulationState system : state.systems()) {
            for (EntityState entity : system.simulationState().entities()) {
                if (entity.faction() != null) {
                    physicalFactionRuntimeIds.add(entity.faction().factionId());
                }
                if (entity.market() != null && entity.inventory() != null) {
                    marketFingerprints.add(entity.inventory().stock().toString()
                            + ":" + entity.market().configuredTargetStock());
                }
            }
        }
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7), physicalFactionRuntimeIds,
                "Every demo faction must own ordinary physical entities somewhere in the galaxy");
        assertTrue(marketFingerprints.size() >= 20,
                "System profiles must create materially different initial stock/demand states");

        Set<StarSystemId> controlled = new HashSet<>();
        state.factionStrategies().forEach(strategy -> controlled.addAll(strategy.controlledSystems()));
        assertEquals(100, controlled.size(), "Every demo system must begin under exactly one strategic controller");
    }

    @Test
    void largeDemoRoundTripsAndCompactFixtureRemainsThreeSystems() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState compact = DemoGalaxyFactory.createState(0x1005_18L, content);
        assertEquals(3, compact.topology().systems().size(),
                "Focused automated tests must retain the compact fixture");

        WorldState large = LargeDemoGalaxyFactory.createState(0x1005_18L, content);
        byte[] encoded = WorldStateCodec.encode(large);
        WorldState decoded = WorldStateCodec.decode(encoded);
        assertEquals(large, decoded);
        assertArrayEquals(encoded, WorldStateCodec.encode(decoded),
                "The 100-system demo must have deterministic canonical persistence");
    }

    private static int reachableSystemCount(WorldState state) {
        Set<StarSystemId> visited = new HashSet<>();
        ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
        queue.add(DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (StarSystemId neighbor : state.topology().neighbors(current)) {
                if (!visited.contains(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }
        return visited.size();
    }
}
