package com.spacesim.persistence;

import com.spacesim.world.AsteroidFieldId;
import com.spacesim.world.AsteroidFieldNode;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.PlanetId;
import com.spacesim.world.PlanetNode;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class WorldTopologyBinary {
    private static final int MAX_SECTORS = 10_000;
    private static final int MAX_SYSTEMS = 100_000;
    private static final int MAX_PLANETS = 1_000_000;
    private static final int MAX_ASTEROID_FIELDS = 1_000_000;
    private static final int MAX_CONNECTIONS = 500_000;

    private WorldTopologyBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, GalaxyTopology topology) throws IOException {
        out.writeLong(topology.id().value());
        WorldIoSupport.writeString(out, topology.name());
        WorldIoSupport.writeCount(out, topology.sectors().size(), MAX_SECTORS, "sectors");
        int totalSystems = 0;
        int totalPlanets = 0;
        int totalFields = 0;
        for (SectorNode sector : topology.sectors()) {
            out.writeLong(sector.id().value());
            WorldIoSupport.writeString(out, sector.name());
            int systemCount = sector.systems().size();
            if (systemCount > MAX_SYSTEMS - totalSystems) {
                throw new IllegalArgumentException("Too many StarSystems");
            }
            totalSystems += systemCount;
            WorldIoSupport.writeCount(out, systemCount, MAX_SYSTEMS, "sectorSystems");
            for (StarSystemNode system : sector.systems()) {
                out.writeLong(system.id().value());
                WorldIoSupport.writeString(out, system.name());
                out.writeDouble(system.x());
                out.writeDouble(system.y());

                int planetCount = system.planets().size();
                if (planetCount > MAX_PLANETS - totalPlanets) {
                    throw new IllegalArgumentException("Too many planets");
                }
                totalPlanets += planetCount;
                WorldIoSupport.writeCount(out, planetCount, MAX_PLANETS, "planets");
                for (PlanetNode planet : system.planets()) {
                    out.writeLong(planet.id().value());
                    WorldIoSupport.writeString(out, planet.name());
                    out.writeDouble(planet.orbitRadius());
                }

                int fieldCount = system.asteroidFields().size();
                if (fieldCount > MAX_ASTEROID_FIELDS - totalFields) {
                    throw new IllegalArgumentException("Too many asteroid fields");
                }
                totalFields += fieldCount;
                WorldIoSupport.writeCount(out, fieldCount, MAX_ASTEROID_FIELDS, "asteroidFields");
                for (AsteroidFieldNode field : system.asteroidFields()) {
                    out.writeLong(field.id().value());
                    WorldIoSupport.writeString(out, field.name());
                    out.writeDouble(field.x());
                    out.writeDouble(field.y());
                    out.writeDouble(field.radius());
                }
            }
        }

        WorldIoSupport.writeCount(out, topology.connections().size(), MAX_CONNECTIONS, "connections");
        for (JumpConnection connection : topology.connections()) {
            out.writeLong(connection.first().value());
            out.writeLong(connection.second().value());
        }
    }

    static GalaxyTopology read(DataInputStream in) throws IOException {
        GalaxyId galaxyId = new GalaxyId(in.readLong());
        String galaxyName = WorldIoSupport.readString(in);
        int sectorCount = WorldIoSupport.readCount(in, MAX_SECTORS, "sectors");
        List<SectorNode> sectors = new ArrayList<>(sectorCount);
        int totalSystems = 0;
        int totalPlanets = 0;
        int totalFields = 0;
        for (int sectorIndex = 0; sectorIndex < sectorCount; sectorIndex++) {
            SectorId sectorId = new SectorId(in.readLong());
            String sectorName = WorldIoSupport.readString(in);
            int systemCount = WorldIoSupport.readCount(in, MAX_SYSTEMS, "sectorSystems");
            if (systemCount > MAX_SYSTEMS - totalSystems) {
                throw new IllegalArgumentException("Too many StarSystems");
            }
            totalSystems += systemCount;
            List<StarSystemNode> systems = new ArrayList<>(systemCount);
            for (int systemIndex = 0; systemIndex < systemCount; systemIndex++) {
                StarSystemId systemId = new StarSystemId(in.readLong());
                String systemName = WorldIoSupport.readString(in);
                double x = in.readDouble();
                double y = in.readDouble();

                int planetCount = WorldIoSupport.readCount(in, MAX_PLANETS, "planets");
                if (planetCount > MAX_PLANETS - totalPlanets) {
                    throw new IllegalArgumentException("Too many planets");
                }
                totalPlanets += planetCount;
                List<PlanetNode> planets = new ArrayList<>(planetCount);
                for (int planetIndex = 0; planetIndex < planetCount; planetIndex++) {
                    planets.add(new PlanetNode(
                            new PlanetId(in.readLong()),
                            WorldIoSupport.readString(in),
                            in.readDouble()));
                }

                int fieldCount = WorldIoSupport.readCount(in, MAX_ASTEROID_FIELDS, "asteroidFields");
                if (fieldCount > MAX_ASTEROID_FIELDS - totalFields) {
                    throw new IllegalArgumentException("Too many asteroid fields");
                }
                totalFields += fieldCount;
                List<AsteroidFieldNode> fields = new ArrayList<>(fieldCount);
                for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                    fields.add(new AsteroidFieldNode(
                            new AsteroidFieldId(in.readLong()),
                            WorldIoSupport.readString(in),
                            in.readDouble(),
                            in.readDouble(),
                            in.readDouble()));
                }
                systems.add(new StarSystemNode(
                        systemId, systemName, x, y, List.copyOf(planets), List.copyOf(fields)));
            }
            sectors.add(new SectorNode(sectorId, sectorName, List.copyOf(systems)));
        }

        int connectionCount = WorldIoSupport.readCount(in, MAX_CONNECTIONS, "connections");
        List<JumpConnection> connections = new ArrayList<>(connectionCount);
        for (int index = 0; index < connectionCount; index++) {
            connections.add(new JumpConnection(
                    new StarSystemId(in.readLong()),
                    new StarSystemId(in.readLong())));
        }
        return new GalaxyTopology(galaxyId, galaxyName, List.copyOf(sectors), List.copyOf(connections));
    }
}
