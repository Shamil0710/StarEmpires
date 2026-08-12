package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrategicLandmarkTopologyTest {
    @Test
    void demoLandmarksИхParentIndexesПереживаютWorldCodecRoundTrip() {
        WorldState original = DemoGalaxyFactory.createState(
                0x7711L,
                ContentCatalogLoader.loadDefault());
        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(original));

        assertEquals(original, decoded);
        PlanetId planetId = new PlanetId(3L);
        AsteroidFieldId fieldId = new AsteroidFieldId(3L);
        assertEquals("Corona II", decoded.topology().findPlanet(planetId).orElseThrow().name());
        assertEquals(
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                decoded.topology().systemOf(planetId).orElseThrow().id());
        assertEquals(
                "Frontier Field",
                decoded.topology().findAsteroidField(fieldId).orElseThrow().name());
        assertEquals(
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                decoded.topology().systemOf(fieldId).orElseThrow().id());
    }

    @Test
    void globalDuplicatePlanetIdМеждуСистемамиОтклоняется() {
        PlanetNode duplicateA = new PlanetNode(new PlanetId(1L), "A", 1d);
        PlanetNode duplicateB = new PlanetNode(new PlanetId(1L), "B", 2d);
        StarSystemNode first = new StarSystemNode(
                new StarSystemId(1L), "One", 0d, 0d, List.of(duplicateA), List.of());
        StarSystemNode second = new StarSystemNode(
                new StarSystemId(2L), "Two", 1d, 0d, List.of(duplicateB), List.of());

        assertThrows(IllegalArgumentException.class, () -> new GalaxyTopology(
                new GalaxyId(1L),
                "Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Sector", List.of(first, second))),
                List.of()));
    }

    @Test
    void globalDuplicateAsteroidFieldIdМеждуСистемамиОтклоняется() {
        AsteroidFieldNode duplicateA = new AsteroidFieldNode(
                new AsteroidFieldId(1L), "A", 0d, 0d, 1d);
        AsteroidFieldNode duplicateB = new AsteroidFieldNode(
                new AsteroidFieldId(1L), "B", 1d, 1d, 2d);
        StarSystemNode first = new StarSystemNode(
                new StarSystemId(1L), "One", 0d, 0d, List.of(), List.of(duplicateA));
        StarSystemNode second = new StarSystemNode(
                new StarSystemId(2L), "Two", 1d, 0d, List.of(), List.of(duplicateB));

        assertThrows(IllegalArgumentException.class, () -> new GalaxyTopology(
                new GalaxyId(1L),
                "Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Sector", List.of(first, second))),
                List.of()));
    }
}
