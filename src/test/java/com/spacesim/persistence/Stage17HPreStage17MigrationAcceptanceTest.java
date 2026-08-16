package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.player.PlayableWorldState;
import com.spacesim.player.PlayerState;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stage-17H historical-save gate proving a Stage-16 playable save migrates without invented authority. */
class Stage17HPreStage17MigrationAcceptanceTest {
    private static final int WORLD_MAGIC = 0x53544757;
    private static final int WORLD_STAGE16_FILE_FORMAT_VERSION = 2;
    private static final int PLAYABLE_MAGIC = 0x53545053;
    private static final int PLAYABLE_FILE_FORMAT_VERSION = 1;

    @Test
    void stage16PlayableFixtureMigratesIndependentAndPreservesPhysicalWorldAndPlayerAssets() throws IOException {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState sourceWorld = DemoGalaxyFactory.createState(17_800_101L, content);
        FleetPlacementState ownedFleet = sourceWorld.fleets().get(0);
        PlayerState sourcePlayer = new PlayerState(
                42_000_000L,
                null,
                List.of(),
                List.of(ownedFleet.id()),
                ownedFleet.id(),
                List.of(ownedFleet.systemId()),
                List.of(),
                ownedFleet.systemId());
        PlayableWorldState sourcePlayable = new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                sourceWorld,
                sourcePlayer);

        byte[] stage16WorldBytes = encodeStage16World(sourceWorld);
        byte[] stage16PlayableBytes = replaceEmbeddedWorld(sourcePlayable, stage16WorldBytes);
        PlayableWorldState migrated = PlayableWorldStateCodec.decode(stage16PlayableBytes);

        assertEquals(PlayableWorldState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(WorldState.CURRENT_VERSION, migrated.worldState().schemaVersion());
        assertFalse(migrated.playerState().affiliated(),
                "A pre-Stage17 player must remain independent after migration");
        assertEquals(sourcePlayer.walletMilliCredits(), migrated.playerState().walletMilliCredits());
        assertEquals(sourcePlayer.ownedFleetIds(), migrated.playerState().ownedFleetIds());
        assertEquals(sourcePlayer.activeFleetId(), migrated.playerState().activeFleetId());
        assertEquals(sourceWorld.systems(), migrated.worldState().systems(),
                "Local physical GameState snapshots must migrate byte-semantically unchanged");
        assertEquals(sourceWorld.fleets(), migrated.worldState().fleets());
        assertEquals(sourceWorld.fleetJumps(), migrated.worldState().fleetJumps());
        assertEquals(sourceWorld.nextFleetIdValue(), migrated.worldState().nextFleetIdValue());
        assertEquals(sourceWorld.constructionProjects(), migrated.worldState().constructionProjects());
        assertEquals(sourceWorld.nextConstructionProjectIdValue(),
                migrated.worldState().nextConstructionProjectIdValue());
        assertTrue(migrated.worldState().factionIdentities().isEmpty(),
                "Stage-16 migration must not manufacture a dynamic player faction identity");
        assertEquals(sourceWorld.factions().size(), migrated.worldState().factions().size(),
                "Migration must not create an economic faction account");
        assertEquals(sourceWorld.factionStrategies().size(), migrated.worldState().factionStrategies().size(),
                "Migration must not create strategic actors");
        for (FactionDiplomacyState diplomacy : migrated.worldState().factionDiplomacyStates()) {
            assertEquals(FactionDiplomacyState.neutral(diplomacy.factionContentId()), diplomacy,
                    "Missing Stage-17 diplomacy must migrate to explicit neutral state only");
        }

        byte[] canonical = PlayableWorldStateCodec.encode(migrated);
        PlayableWorldState decodedAgain = PlayableWorldStateCodec.decode(canonical);
        assertEquals(migrated, decodedAgain);
        assertArrayEquals(canonical, PlayableWorldStateCodec.encode(decodedAgain),
                "Migrated Stage-16 save must settle into one deterministic current encoding");
    }

    @Test
    void futureOrCorruptStage17TransitionFormatsFailBeforeRuntimeRestore() throws IOException {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState world = DemoGalaxyFactory.createState(17_800_102L, content);
        FleetPlacementState fleet = world.fleets().get(0);
        PlayerState player = new PlayerState(
                1_000_000L,
                null,
                List.of(),
                List.of(fleet.id()),
                fleet.id(),
                List.of(fleet.systemId()),
                List.of(),
                fleet.systemId());
        PlayableWorldState source = new PlayableWorldState(PlayableWorldState.CURRENT_VERSION, world, player);
        byte[] legacyWorld = encodeStage16World(world);
        byte[] legacyPlayable = replaceEmbeddedWorld(source, legacyWorld);

        byte[] futurePlayable = legacyPlayable.clone();
        writeInt(futurePlayable, 8, PlayableWorldState.CURRENT_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> PlayableWorldStateCodec.decode(futurePlayable));

        byte[] futureWorld = legacyWorld.clone();
        writeInt(futureWorld, 8, WorldState.CURRENT_VERSION + 1);
        byte[] futureEmbeddedWorld = replaceEmbeddedWorld(source, futureWorld);
        assertThrows(IllegalArgumentException.class, () -> PlayableWorldStateCodec.decode(futureEmbeddedWorld));

        byte[] truncated = java.util.Arrays.copyOf(legacyPlayable, legacyPlayable.length - 7);
        assertThrows(IllegalArgumentException.class, () -> PlayableWorldStateCodec.decode(truncated));

        byte[] trailing = java.util.Arrays.copyOf(legacyPlayable, legacyPlayable.length + 1);
        trailing[trailing.length - 1] = 1;
        assertThrows(IllegalArgumentException.class, () -> PlayableWorldStateCodec.decode(trailing));
    }

    private static byte[] encodeStage16World(WorldState state) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(WORLD_MAGIC);
            output.writeInt(WORLD_STAGE16_FILE_FORMAT_VERSION);
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
            WorldStrategicGrowthBinary.write(output, state.factionStrategies());
        }
        return buffer.toByteArray();
    }

    private static byte[] replaceEmbeddedWorld(
            PlayableWorldState source,
            byte[] replacementWorldBytes) throws IOException {
        byte[] current = PlayableWorldStateCodec.encode(source);
        byte[] playerTail;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(current))) {
            assertEquals(PLAYABLE_MAGIC, input.readInt());
            assertEquals(PLAYABLE_FILE_FORMAT_VERSION, input.readInt());
            assertEquals(PlayableWorldState.CURRENT_VERSION, input.readInt());
            int currentWorldLength = input.readInt();
            input.skipNBytes(currentWorldLength);
            playerTail = input.readAllBytes();
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(PLAYABLE_MAGIC);
            output.writeInt(PLAYABLE_FILE_FORMAT_VERSION);
            output.writeInt(PlayableWorldState.CURRENT_VERSION);
            output.writeInt(replacementWorldBytes.length);
            output.write(replacementWorldBytes);
            output.write(playerTail);
        }
        return buffer.toByteArray();
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
