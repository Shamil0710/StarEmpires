package com.spacesim.persistence;

import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Stage10AWorldMigrationTest {
    private static final int MAGIC = 0x53544757;
    private static final int FILE_VERSION = 1;
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);

    @Test
    void schemaV6KeepsFleetIdsAndAddsNoInventedJumpActivity() throws IOException {
        WorldState current = world();

        WorldState migrated = WorldStateCodec.decode(encodeV6(current));

        assertEquals(WorldState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(current.topology(), migrated.topology());
        assertEquals(current.systems(), migrated.systems());
        assertEquals(current.fleets(), migrated.fleets());
        assertEquals(current.nextFleetIdValue(), migrated.nextFleetIdValue());
        assertFalse(migrated.fleets().isEmpty());
        assertEquals(List.of(), migrated.fleetJumps());
    }

    private static byte[] encodeV6(WorldState state) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeInt(FILE_VERSION);
            output.writeInt(WorldState.LEGACY_STAGE10A_VERSION);
            WorldTopologyBinary.write(output, state.topology());
            WorldSystemBinary.write(output, state.systems());
            WorldFactionBinary.writeEconomic(output, state.factions());
            WorldFactionBinary.writeStrategies(output, state.factionStrategies());
            output.writeLong(state.nextConstructionProjectIdValue());
            WorldConstructionBinary.write(output, state.constructionProjects());
            WorldFactionBinary.writePressures(output, state.factionEconomicPressures());
            output.writeLong(state.nextFleetIdValue());
            WorldFleetBinary.write(output, state.fleets());
        }
        return buffer.toByteArray();
    }

    private static WorldState world() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "Legacy Stage 10A",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta))),
                List.of(new JumpConnection(ALPHA, BETA)));
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(
                        new StarSystemSimulationState(ALPHA, SimulationSession.createDemo(101L).snapshot()),
                        new StarSystemSimulationState(BETA, SimulationSession.createDemo(202L).snapshot())));
    }
}
