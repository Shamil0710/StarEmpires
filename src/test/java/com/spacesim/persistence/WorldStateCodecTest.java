package com.spacesim.persistence;

import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionRelationState;
import com.spacesim.world.FactionStrategicState;
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
        WorldState state = strategicWorld();

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
    void factionLayersСохраняютсяВCanonicalContentIdПорядке() {
        WorldState base = twoSystemWorld(false);
        WorldState state = new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                List.of(
                        new FactionEconomicState("faction.neutral", 500L, 300L, 100L),
                        new FactionEconomicState("faction.miners", 750L, 300L, 100L)),
                List.of(
                        new FactionStrategicState(
                                "faction.neutral", -20,
                                List.of(new FactionRelationState("faction.miners", 5)),
                                List.of(BETA_ID)),
                        new FactionStrategicState(
                                "faction.miners", 0,
                                List.of(new FactionRelationState("faction.neutral", 10)),
                                List.of(ALPHA_ID))));

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(state));

        assertEquals(state, decoded);
        assertEquals(
                List.of("faction.miners", "faction.neutral"),
                decoded.factions().stream().map(FactionEconomicState::factionContentId).toList());
        assertEquals(
                List.of("faction.miners", "faction.neutral"),
                decoded.factionStrategies().stream().map(FactionStrategicState::factionContentId).toList());
    }

    @Test
    void stage7SchemaV1МигрируетБезСозданияFactionState() {
        WorldState current = twoSystemWorld(false);
        byte[] currentBytes = WorldStateCodec.encode(current);

        // Current v3 with empty faction layers appends two zero counts: factions + strategies.
        byte[] legacyBytes = Arrays.copyOf(currentBytes, currentBytes.length - 2 * Integer.BYTES);
        ByteBuffer.wrap(legacyBytes).putInt(8, WorldState.LEGACY_STAGE7_VERSION);

        WorldState migrated = WorldStateCodec.decode(legacyBytes);

        assertEquals(WorldState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(current.topology(), migrated.topology());
        assertEquals(current.systems(), migrated.systems());
        assertEquals(List.of(), migrated.factions());
        assertEquals(List.of(), migrated.factionStrategies());
    }

    @Test
    void treasurySchemaV2МигрируетСДеньгамиНоБезВыдуманнойDiplomacy() {
        WorldState base = twoSystemWorld(false);
        WorldState treasuryOnly = new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                List.of(new FactionEconomicState("faction.miners", 123_456L, 9_000L, 1_000L)),
                List.of());
        byte[] currentBytes = WorldStateCodec.encode(treasuryOnly);

        // v3 appends only the empty strategic-state count after the exact v2 layout.
        byte[] v2Bytes = Arrays.copyOf(currentBytes, currentBytes.length - Integer.BYTES);
        ByteBuffer.wrap(v2Bytes).putInt(8, WorldState.LEGACY_FACTION_TREASURY_VERSION);

        WorldState migrated = WorldStateCodec.decode(v2Bytes);

        assertEquals(WorldState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(treasuryOnly.factions(), migrated.factions());
        assertEquals(List.of(), migrated.factionStrategies());
    }

    @Test
    void canonicalOrderingДелаетРазныйВходнойПорядокОднимWorldSave() {
        WorldState canonical = twoSystemWorld(false);
        WorldState reversed = twoSystemWorld(true);

        assertEquals(canonical, reversed);
        assertArrayEquals(WorldStateCodec.encode(canonical), WorldStateCodec.encode(reversed));
    }

    @Test
    void fileWriteReadБезопасноЗаменяетWorldSnapshot() throws IOException {
        Path save = temporaryDirectory.resolve("world-1.starsave");
        WorldState first = strategicWorld();
        WorldState second = new WorldState(
                WorldState.CURRENT_VERSION,
                first.topology(),
                List.of(
                        new StarSystemSimulationState(ALPHA_ID, SimulationSession.createDemo(303L).snapshot()),
                        first.systems().get(1)),
                first.factions(),
                first.factionStrategies());

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
    }

    @Test
    void duplicateFactionИDoubleTerritoryОтклоняются() {
        WorldState base = twoSystemWorld(false);
        FactionEconomicState economic = new FactionEconomicState("faction.miners", 1L, 2L, 3L);
        assertThrows(IllegalArgumentException.class, () -> new WorldState(
                WorldState.CURRENT_VERSION, base.topology(), base.systems(),
                List.of(economic, economic), List.of()));

        FactionStrategicState miners = new FactionStrategicState(
                "faction.miners", -100, List.of(), List.of(ALPHA_ID));
        FactionStrategicState neutral = new FactionStrategicState(
                "faction.neutral", -100, List.of(), List.of(ALPHA_ID));
        assertThrows(IllegalArgumentException.class, () -> new WorldState(
                WorldState.CURRENT_VERSION, base.topology(), base.systems(),
                List.of(), List.of(miners, neutral)));
    }

    @Test
    void existingGameStateОборачиваетсяВDefaultWorldБезИзмененияEconomicSnapshot() {
        GameState state = SimulationSession.createDemo(0x7A6EL).snapshot();

        WorldState world = WorldState.singleSystem(state);

        assertEquals(WorldTopologyDefaults.singleSystem(), world.topology());
        assertEquals(1, world.systems().size());
        assertEquals(state, world.systems().get(0).simulationState());
        assertEquals(List.of(), world.factions());
        assertEquals(List.of(), world.factionStrategies());
        assertEquals(state, WorldStateCodec.decode(WorldStateCodec.encode(world))
                .systems().get(0).simulationState());
    }

    @Test
    void повреждённыйWorldHeaderTruncationИТrailingBytesОтклоняются() {
        byte[] valid = WorldStateCodec.encode(strategicWorld());

        byte[] badMagic = valid.clone();
        badMagic[0] ^= 0x7f;
        assertThrows(IllegalArgumentException.class, () -> WorldStateCodec.decode(badMagic));

        byte[] badFileVersion = valid.clone();
        ByteBuffer.wrap(badFileVersion).putInt(4, 999);
        assertThrows(IllegalArgumentException.class, () -> WorldStateCodec.decode(badFileVersion));

        byte[] badSchema = valid.clone();
        ByteBuffer.wrap(badSchema).putInt(8, WorldState.CURRENT_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> WorldStateCodec.decode(badSchema));

        assertThrows(IllegalArgumentException.class,
                () -> WorldStateCodec.decode(Arrays.copyOf(valid, valid.length - 1)));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        trailing[trailing.length - 1] = 1;
        assertThrows(IllegalArgumentException.class, () -> WorldStateCodec.decode(trailing));
    }

    private WorldState strategicWorld() {
        WorldState base = twoSystemWorld(false);
        return new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                List.of(new FactionEconomicState("faction.miners", 1000L, 300L, 100L)),
                List.of(new FactionStrategicState(
                        "faction.miners", 0,
                        List.of(new FactionRelationState("faction.neutral", 20)),
                        List.of(ALPHA_ID))));
    }

    private WorldState twoSystemWorld(boolean reversed) {
        StarSystemSimulationState alpha = new StarSystemSimulationState(
                ALPHA_ID, SimulationSession.createDemo(101L).snapshot());
        StarSystemSimulationState beta = new StarSystemSimulationState(
                BETA_ID, SimulationSession.createDemo(202L).snapshot());
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology(reversed),
                reversed ? List.of(beta, alpha) : List.of(alpha, beta));
    }

    private GalaxyTopology topology(boolean reversed) {
        StarSystemNode alpha = new StarSystemNode(ALPHA_ID, "Alpha", -10d, 5d);
        StarSystemNode beta = new StarSystemNode(BETA_ID, "Beta", 20d, -7d);
        SectorNode inner = new SectorNode(new SectorId(1L), "Inner", List.of(alpha));
        SectorNode frontier = new SectorNode(new SectorId(2L), "Frontier", List.of(beta));
        return new GalaxyTopology(
                new GalaxyId(1L),
                "Test Galaxy",
                reversed ? List.of(frontier, inner) : List.of(inner, frontier),
                List.of(new JumpConnection(BETA_ID, ALPHA_ID)));
    }
}
