package com.spacesim.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalaxyTopologyTest {
    @Test
    void topologyСортируетИндексируетСектораСистемыИСоседейДетерминированно() {
        StarSystemNode alpha = system(10L, "Alpha", 0d, 0d);
        StarSystemNode beta = system(20L, "Beta", 10d, 5d);
        StarSystemNode gamma = system(30L, "Gamma", -4d, 8d);
        SectorNode outer = new SectorNode(new SectorId(2L), "Outer", List.of(gamma));
        SectorNode core = new SectorNode(new SectorId(1L), "Core", List.of(beta, alpha));

        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                " Milky Way ",
                List.of(outer, core),
                List.of(
                        new JumpConnection(gamma.id(), beta.id()),
                        new JumpConnection(beta.id(), alpha.id())));

        assertEquals("Milky Way", topology.name());
        assertEquals(List.of(core, outer), topology.sectors());
        assertEquals(List.of(alpha, beta, gamma), topology.systems());
        assertEquals(
                List.of(
                        new JumpConnection(alpha.id(), beta.id()),
                        new JumpConnection(beta.id(), gamma.id())),
                topology.connections());
        assertEquals(List.of(alpha.id(), gamma.id()), topology.neighbors(beta.id()));
        assertEquals(core, topology.sectorOf(alpha.id()).orElseThrow());
        assertEquals(gamma, topology.findSystem(gamma.id()).orElseThrow());
        assertTrue(topology.findSector(new SectorId(99L)).isEmpty());
        assertTrue(topology.neighbors(new StarSystemId(99L)).isEmpty());
    }

    @Test
    void topologyИSectorЗащищаютВходныеКоллекции() {
        StarSystemNode alpha = system(1L, "Alpha", 0d, 0d);
        List<StarSystemNode> mutableSystems = new ArrayList<>();
        mutableSystems.add(alpha);
        SectorNode sector = new SectorNode(new SectorId(1L), "Core", mutableSystems);
        mutableSystems.clear();

        List<SectorNode> mutableSectors = new ArrayList<>();
        mutableSectors.add(sector);
        List<JumpConnection> mutableConnections = new ArrayList<>();
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L), "Galaxy", mutableSectors, mutableConnections);
        mutableSectors.clear();
        mutableConnections.add(new JumpConnection(new StarSystemId(1L), new StarSystemId(2L)));

        assertEquals(List.of(alpha), sector.systems());
        assertEquals(List.of(sector), topology.sectors());
        assertTrue(topology.connections().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> topology.sectors().add(sector));
    }

    @Test
    void topologyОтклоняетПовторStarSystemIdМеждуСекторами() {
        StarSystemNode first = system(7L, "First", 0d, 0d);
        StarSystemNode duplicate = system(7L, "Duplicate", 1d, 1d);
        SectorNode sectorA = new SectorNode(new SectorId(1L), "A", List.of(first));
        SectorNode sectorB = new SectorNode(new SectorId(2L), "B", List.of(duplicate));

        assertThrows(IllegalArgumentException.class,
                () -> new GalaxyTopology(
                        new GalaxyId(1L), "Galaxy", List.of(sectorA, sectorB), List.of()));
    }

    @Test
    void topologyОтклоняетJumpНаНеизвестнуюСистемуИДублирующийОбратныйJump() {
        StarSystemNode alpha = system(1L, "Alpha", 0d, 0d);
        StarSystemNode beta = system(2L, "Beta", 1d, 0d);
        SectorNode sector = new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta));

        assertThrows(IllegalArgumentException.class,
                () -> new GalaxyTopology(
                        new GalaxyId(1L),
                        "Galaxy",
                        List.of(sector),
                        List.of(new JumpConnection(alpha.id(), new StarSystemId(99L)))));

        assertThrows(IllegalArgumentException.class,
                () -> new GalaxyTopology(
                        new GalaxyId(1L),
                        "Galaxy",
                        List.of(sector),
                        List.of(
                                new JumpConnection(alpha.id(), beta.id()),
                                new JumpConnection(beta.id(), alpha.id()))));
    }

    @Test
    void topologyValueObjectsОтклоняютНекорректныеДанные() {
        assertThrows(IllegalArgumentException.class, () -> new GalaxyId(0L));
        assertThrows(IllegalArgumentException.class, () -> new SectorId(-1L));
        assertThrows(IllegalArgumentException.class, () -> new StarSystemId(0L));
        assertThrows(IllegalArgumentException.class,
                () -> new StarSystemNode(new StarSystemId(1L), " ", 0d, 0d));
        assertThrows(IllegalArgumentException.class,
                () -> new StarSystemNode(new StarSystemId(1L), "Bad", Double.NaN, 0d));
        assertThrows(IllegalArgumentException.class,
                () -> new SectorNode(new SectorId(1L), " ", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new JumpConnection(new StarSystemId(1L), new StarSystemId(1L)));
        assertThrows(IllegalArgumentException.class,
                () -> new GalaxyTopology(new GalaxyId(1L), " ", List.of(), List.of()));
    }

    private StarSystemNode system(long id, String name, double x, double y) {
        return new StarSystemNode(new StarSystemId(id), name, x, y);
    }
}
