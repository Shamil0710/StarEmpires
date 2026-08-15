package com.spacesim.persistence;

import com.spacesim.constants.Constants;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage17WorldFactionIdentityPersistenceTest {
    private static final int MAGIC = 0x53544757;
    private static final int LEGACY_FILE_VERSION = 1;

    @Test
    void v9RoundTripPreservesCanonicalDynamicFactionDirectory() {
        WorldState base = WorldState.singleSystem(SimulationSession.createDemo(0x17A2C9L).snapshot());
        WorldFactionIdentityState zeta = new WorldFactionIdentityState(
                "faction.player.zeta",
                4,
                "Zeta Cooperative",
                WorldFactionIdentityState.Origin.PLAYER_CREATED);
        WorldFactionIdentityState alpha = new WorldFactionIdentityState(
                "faction.player.alpha",
                3,
                "Alpha Union",
                WorldFactionIdentityState.Origin.PLAYER_CREATED);
        WorldState state = withIdentities(base, List.of(zeta, alpha));

        byte[] first = WorldStateCodec.encode(state);
        WorldState decoded = WorldStateCodec.decode(first);

        assertEquals(List.of(alpha, zeta), state.factionIdentities());
        assertEquals(state, decoded);
        assertArrayEquals(first, WorldStateCodec.encode(decoded));
    }

    @Test
    void duplicateStableOrRuntimeIdentityIsRejected() {
        WorldState base = WorldState.singleSystem(SimulationSession.createDemo(0x17A2D9L).snapshot());
        WorldFactionIdentityState first = identity("faction.player.first", 3);
        WorldFactionIdentityState sameStable = identity("faction.player.first", 4);
        WorldFactionIdentityState sameRuntime = identity("faction.player.second", 3);

        assertThrows(IllegalArgumentException.class,
                () -> withIdentities(base, List.of(first, sameStable)));
        assertThrows(IllegalArgumentException.class,
                () -> withIdentities(base, List.of(first, sameRuntime)));
    }

    @Test
    void identityRuntimeSlotMustFitBoundedEcsCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new WorldFactionIdentityState(
                "faction.player.invalid-slot",
                Constants.FACTION_RUNTIME_CAPACITY,
                "Invalid Slot",
                WorldFactionIdentityState.Origin.PLAYER_CREATED));
    }

    @Test
    void stage16V8MigratesWithoutInventingDynamicFactions() throws IOException {
        WorldState current = WorldState.singleSystem(SimulationSession.createDemo(0x17A2E9L).snapshot());

        WorldState migrated = WorldStateCodec.decode(encodeStage16V8(current));

        assertEquals(WorldState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(current.topology(), migrated.topology());
        assertEquals(current.systems(), migrated.systems());
        assertEquals(current.factions(), migrated.factions());
        assertEquals(current.factionStrategies(), migrated.factionStrategies());
        assertEquals(current.constructionProjects(), migrated.constructionProjects());
        assertEquals(current.fleets(), migrated.fleets());
        assertEquals(current.fleetJumps(), migrated.fleetJumps());
        assertEquals(List.of(), migrated.factionIdentities());
    }

    private static WorldFactionIdentityState identity(String stableId, int runtimeId) {
        return new WorldFactionIdentityState(
                stableId,
                runtimeId,
                stableId,
                WorldFactionIdentityState.Origin.PLAYER_CREATED);
    }

    private static WorldState withIdentities(
            WorldState source,
            List<WorldFactionIdentityState> identities) {
        return new WorldState(
                WorldState.CURRENT_VERSION,
                source.topology(),
                source.systems(),
                source.factions(),
                source.factionStrategies(),
                source.nextConstructionProjectIdValue(),
                source.constructionProjects(),
                source.factionEconomicPressures(),
                source.nextFleetIdValue(),
                source.fleets(),
                source.fleetJumps(),
                identities);
    }

    private static byte[] encodeStage16V8(WorldState state) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeInt(LEGACY_FILE_VERSION);
            output.writeInt(WorldState.LEGACY_STAGE16_VERSION);
            WorldTopologyBinary.write(output, state.topology());
            WorldSystemBinary.write(output, state.systems());
            WorldFactionBinary.writeEconomic(output, state.factions());
            WorldFactionBinary.writeStrategies(output, state.factionStrategies());
            output.writeLong(state.nextConstructionProjectIdValue());
            WorldConstructionBinary.write(output, state.constructionProjects());
            WorldFactionBinary.writePressures(output, state.factionEconomicPressures());
            output.writeLong(state.nextFleetIdValue());
            WorldFleetBinary.write(output, state.fleets());
            WorldFleetBinary.writeJumps(output, state.fleetJumps());
        }
        return buffer.toByteArray();
    }
}
