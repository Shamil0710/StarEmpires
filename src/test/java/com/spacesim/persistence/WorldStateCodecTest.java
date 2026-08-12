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
import com.spacesim.world.WorldTopologyDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStateCodecTest {
    private static final StarSystemId ALPHA_ID = new StarSystemId(1L);
    private static final StarSystemId BETA_ID = new StarSystemId(2L);

    @TempDir
    Path temporaryDirectory;

    @Test
    void multiSystemWorldДаётDeterministicExactRoundTrip() {
        WorldState state = twoSystemWorld(false);

        byte[] first = WorldStateCodec.encode(state);
        byte[] second = WorldStateCodec.encode(state);
        WorldState decoded = WorldStateCodec.decode(first);

        assertArrayEquals(first, second);
        assertEquals(state, decoded);
        assertArrayEquals(first, WorldStateCodec.encode(decoded));
        assertEquals(List.of(ALPHA_ID, BETA_ID), decoded.systems().stream()
                .map(StarSystemSimulationState::systemId)
                .toList());
    }

    @Test
    void canonicalOrderingДелаетРазныйВходнойПорядокОднимWorldSave() {
        WorldState canonical = twoSystemWorld(false);
        WorldState reversed = twoSystemWorld(true);

        assertEquals(canonical, reversed);
        assertEquals(canonical.topology().hashCode(), reversed.topology().hashCode());
        assertArrayEquals(
                WorldStateCodec.encode(canonical),
                WorldStateCodec.encode(reversed));
    }

    @Test
    void fileWriteReadБезопасноЗаменяетWorldSnapshot() throws IOException {
        Path save = temporaryDirectory.resolve("world-1.starsave");
        WorldState first = twoSystemWorld(false);
        WorldState second = new WorldState(
                WorldState.CURRENT_VERSION,
                first.topology(),
                List.of(
                        new StarSystemSimulationState(
                                ALPHA_ID,
                                SimulationSession.createDemo(303L).snapshot()),
                        first.systems().get(1)));

        WorldStateCodec.write(save, first);
        assertTrue(Files.isRegularFile(save));
        assertEquals(first, WorldStateCodec.read(save));

        WorldStateCodec.write(save, second);
        assertEquals(second, WorldStateCodec.read(save));
    }

    @Test
    void worldStateТребуетРовноОдинSnapshotНаКаждуюTopologySystem() {
        GalaxyTopology topology = topology(false);
        GameState alpha = SimulationSession.createDemo(101L).snapshot();
        GameState beta = SimulationSession.createDemo(202L).snapshot();

        assertThrows(IllegalArgumentException.class, () -> new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(new StarSystemSimulationState(ALPHA_ID, alpha))));
        assertThrows(IllegalArgumentException.class, () -> new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(
                        new StarSystemSimulationState(ALPHA_ID, alpha),
                        new StarSystemSimulationState(ALPHA_ID, beta))));
        assertThrows(IllegalArgumentException.class, () -> new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(
                        new StarSystemSimulationState(ALPHA_ID, alpha),
                        new StarSystemSimulationState(new StarSystemId(99L), beta))));
    }

    @Test
    void existingGameStateОборачиваетсяВDefaultWorldБезИзмененияEconomicSnapshot() {
        GameState state = SimulationSession.createDemo(0x7A6EL).snapshot();

        WorldState world = WorldState.singleSystem(state);

        assertEquals(WorldTopologyDefaults.singleSystem(), world.topology());
        assertEquals(1, world.systems().size());
        assertEquals(WorldTopologyDefaults.DEFAULT_SYSTEM_ID, world.systems().get(0).systemId());
        assertEquals(state, world.systems().get(0).simulationState());
        assertEquals(state, WorldStateCodec.decode(WorldStateCodec.encode(world))
                .systems().get(0).simulationState());
    }

    @Test
    void повреждённыйWorldHeaderTruncationИТrailingBytesОтклоняются() {
        byte[] valid = WorldStateCodec.encode(twoSystemWorld(false));

        byte[] badMagic = valid.clone();
        badMagic[0] ^= 0x7f;
        assertThrows(IllegalArgumentException.class, () -> WorldStateCodec.decode(badMagic));

        byte[] badFileVersion = valid.clone();
        ByteBuffer.wrap(badFileVersion).putInt(4, 999);
        assertThrows(IllegalArgumentException.class, () -> WorldStateCodec.decode(badFileVersion));

        byte[] badSchema = valid.clone();
        ByteBuffer.wrap(badSchema).putInt(8, WorldState.CURRENT_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> WorldStateCodec.decode(badSchema));

        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        assertThrows(IllegalArgumentException.class, () -> WorldStateCodec.decode(truncated));

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        trailing[trailing.length - 1] = 1;
        assertThrows(IllegalArgumentException.class, () -> WorldStateCodec.decode(trailing));
    }

    private WorldState twoSystemWorld(boolean reversed) {
        StarSystemSimulationState alpha = new StarSystemSimulationState(
                ALPHA_ID,
                SimulationSession.createDemo(101L).snapshot());
        StarSystemSimulationState beta = new StarSystemSimulationState(
                BETA_ID,
                SimulationSession.createDemo(202L).snapshot());
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology(reversed),
                reversed ? List.of(beta, alpha) : List.of(alpha, beta));
    }

    private GalaxyTopology topology(boolean reversed) {
        StarSystemNode alpha = new StarSystemNode(ALPHA_ID, "Alpha", -10d, 5d);
        StarSystemNode beta = new StarSystemNode(BETA_ID, "Beta", 20d, -7d);
        SectorNode inner = new SectorNode(
                new SectorId(1L),
                "Inner",
                List.of(alpha));
        SectorNode frontier = new SectorNode(
                new SectorId(2L),
                "Frontier",
                List.of(beta));
        List<SectorNode> sectors = reversed
                ? List.of(frontier, inner)
                : List.of(inner, frontier);
        return new GalaxyTopology(
                new GalaxyId(1L),
                "Test Galaxy",
                sectors,
                List.of(new JumpConnection(BETA_ID, ALPHA_ID)));
    }
}
