package com.spacesim.persistence;

import com.spacesim.player.DiscoveredObjectRef;
import com.spacesim.player.PlayableWorldState;
import com.spacesim.player.PlayerReputationState;
import com.spacesim.player.PlayerState;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded binary codec for the Stage-12 playable save envelope.
 *
 * <p>The envelope embeds an unchanged {@link WorldStateCodec} payload plus player state. A raw
 * pre-Stage-12 WorldState save is accepted as a legacy input and migrates to a current
 * {@link PlayableWorldState} with no initialized player.</p>
 */
public final class PlayableWorldStateCodec {
    private static final int MAGIC = 0x53545053;
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_SAVE_BYTES = 260 * 1024 * 1024;
    private static final int MAX_WORLD_BYTES = 256 * 1024 * 1024;
    private static final int MAX_CONTENT_ID_CHARS = 1024;
    private static final int MAX_REPUTATIONS = 256;
    private static final int MAX_OWNED_FLEETS = 100_000;
    private static final int MAX_DISCOVERED_SYSTEMS = 100_000;
    private static final int MAX_DISCOVERED_OBJECTS = 1_000_000;

    private PlayableWorldStateCodec() {
        throw new AssertionError("PlayableWorldStateCodec does not create instances");
    }

    /**
     * Encodes a current playable save.
     *
     * @param state validated playable state
     * @return deterministic bounded byte array
     */
    public static byte[] encode(PlayableWorldState state) {
        PlayableWorldState checked = Objects.requireNonNull(state, "PlayableWorldState not set");
        if (checked.schemaVersion() != PlayableWorldState.CURRENT_VERSION) {
            throw new IllegalArgumentException("Cannot write playable schema: " + checked.schemaVersion());
        }
        byte[] worldBytes = WorldStateCodec.encode(checked.worldState());
        if (worldBytes.length <= 0 || worldBytes.length > MAX_WORLD_BYTES) {
            throw new IllegalArgumentException("Embedded WorldState size is outside supported bounds");
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.schemaVersion());
                output.writeInt(worldBytes.length);
                output.write(worldBytes);
                output.writeBoolean(checked.playerState() != null);
                if (checked.playerState() != null) {
                    writePlayer(output, checked.playerState());
                }
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length <= 0 || bytes.length > MAX_SAVE_BYTES) {
                throw new IllegalArgumentException("Playable save exceeds supported size");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory playable save encoding error", exception);
        }
    }

    /**
     * Decodes a current playable save or migrates a raw legacy WorldState save.
     *
     * @param bytes playable envelope or pre-Stage-12 WorldState bytes
     * @return current playable state
     */
    public static PlayableWorldState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Playable save bytes not set");
        if (bytes.length == 0 || bytes.length > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Playable save size is outside supported bounds");
        }
        if (!hasPlayableMagic(bytes)) {
            WorldState legacyWorld = WorldStateCodec.decode(bytes);
            return new PlayableWorldState(PlayableWorldState.CURRENT_VERSION, legacyWorld, null);
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid playable save magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported playable file version: " + fileVersion);
            }
            int schemaVersion = input.readInt();
            if (schemaVersion != PlayableWorldState.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported playable schema: " + schemaVersion);
            }
            int worldLength = input.readInt();
            if (worldLength <= 0 || worldLength > MAX_WORLD_BYTES || worldLength > input.available()) {
                throw new IllegalArgumentException("Embedded WorldState length is invalid");
            }
            byte[] worldBytes = input.readNBytes(worldLength);
            if (worldBytes.length != worldLength) {
                throw new IllegalArgumentException("Embedded WorldState is truncated");
            }
            WorldState world = WorldStateCodec.decode(worldBytes);
            PlayerState player = input.readBoolean() ? readPlayer(input) : null;
            if (input.read() != -1) {
                throw new IllegalArgumentException("Unexpected trailing bytes after playable state");
            }
            return new PlayableWorldState(schemaVersion, world, player);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Playable save is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Playable save cannot be decoded", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("Playable save contains invalid values", exception);
        }
    }

    /**
     * Atomically writes a playable save file.
     *
     * @param path target path
     * @param state playable state
     * @throws IOException when the file cannot be written
     */
    public static void write(Path path, PlayableWorldState state) throws IOException {
        Path target = Objects.requireNonNull(path, "Playable save path not set").toAbsolutePath();
        byte[] bytes = encode(Objects.requireNonNull(state, "PlayableWorldState not set"));
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "playable-" + prefix;
        }
        Path temp = Files.createTempFile(parent, prefix, ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Reads and decodes one bounded playable save file.
     *
     * @param path existing save path
     * @return decoded current playable state
     * @throws IOException when the file cannot be read
     */
    public static PlayableWorldState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "Playable save path not set").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Playable save file size is outside supported bounds");
        }
        return decode(Files.readAllBytes(source));
    }

    private static boolean hasPlayableMagic(byte[] bytes) {
        if (bytes.length < Integer.BYTES) {
            return false;
        }
        int value = ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
        return value == MAGIC;
    }

    private static void writePlayer(DataOutputStream output, PlayerState player) throws IOException {
        requireCount("reputations", player.reputations().size(), MAX_REPUTATIONS);
        requireCount("owned fleets", player.ownedFleetIds().size(), MAX_OWNED_FLEETS);
        requireCount("discovered systems", player.discoveredSystemIds().size(), MAX_DISCOVERED_SYSTEMS);
        requireCount("discovered objects", player.discoveredObjects().size(), MAX_DISCOVERED_OBJECTS);

        output.writeLong(player.walletMilliCredits());
        writeNullableContentId(output, player.factionContentId());

        output.writeInt(player.reputations().size());
        for (PlayerReputationState reputation : player.reputations()) {
            writeContentId(output, reputation.factionContentId());
            output.writeFloat(reputation.value());
        }

        output.writeInt(player.ownedFleetIds().size());
        for (FleetId fleetId : player.ownedFleetIds()) {
            output.writeLong(fleetId.value());
        }
        output.writeBoolean(player.activeFleetId() != null);
        if (player.activeFleetId() != null) {
            output.writeLong(player.activeFleetId().value());
        }

        output.writeInt(player.discoveredSystemIds().size());
        for (StarSystemId systemId : player.discoveredSystemIds()) {
            output.writeLong(systemId.value());
        }

        output.writeInt(player.discoveredObjects().size());
        for (DiscoveredObjectRef reference : player.discoveredObjects()) {
            output.writeLong(reference.systemId().value());
            output.writeLong(reference.entityId().value());
        }

        output.writeBoolean(player.homeSystemId() != null);
        if (player.homeSystemId() != null) {
            output.writeLong(player.homeSystemId().value());
        }
    }

    private static PlayerState readPlayer(DataInputStream input) throws IOException {
        long wallet = input.readLong();
        String affiliation = readNullableContentId(input);

        int reputationCount = readCount(input, "reputations", MAX_REPUTATIONS);
        List<PlayerReputationState> reputations = new ArrayList<>(reputationCount);
        for (int index = 0; index < reputationCount; index++) {
            reputations.add(new PlayerReputationState(readContentId(input), input.readFloat()));
        }

        int fleetCount = readCount(input, "owned fleets", MAX_OWNED_FLEETS);
        List<FleetId> fleets = new ArrayList<>(fleetCount);
        for (int index = 0; index < fleetCount; index++) {
            fleets.add(new FleetId(input.readLong()));
        }
        FleetId activeFleet = input.readBoolean() ? new FleetId(input.readLong()) : null;

        int systemCount = readCount(input, "discovered systems", MAX_DISCOVERED_SYSTEMS);
        List<StarSystemId> systems = new ArrayList<>(systemCount);
        for (int index = 0; index < systemCount; index++) {
            systems.add(new StarSystemId(input.readLong()));
        }

        int objectCount = readCount(input, "discovered objects", MAX_DISCOVERED_OBJECTS);
        List<DiscoveredObjectRef> objects = new ArrayList<>(objectCount);
        for (int index = 0; index < objectCount; index++) {
            objects.add(new DiscoveredObjectRef(
                    new StarSystemId(input.readLong()),
                    new EntityId(input.readLong())));
        }
        StarSystemId home = input.readBoolean() ? new StarSystemId(input.readLong()) : null;
        return new PlayerState(wallet, affiliation, reputations, fleets, activeFleet, systems, objects, home);
    }

    private static void writeNullableContentId(DataOutputStream output, String value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            writeContentId(output, value);
        }
    }

    private static String readNullableContentId(DataInputStream input) throws IOException {
        return input.readBoolean() ? readContentId(input) : null;
    }

    private static void writeContentId(DataOutputStream output, String value) throws IOException {
        String checked = Objects.requireNonNull(value, "Content ID not set").strip();
        if (checked.isEmpty() || checked.length() > MAX_CONTENT_ID_CHARS) {
            throw new IllegalArgumentException("Content ID length is invalid");
        }
        output.writeUTF(checked);
    }

    private static String readContentId(DataInputStream input) throws IOException {
        String value = input.readUTF().strip();
        if (value.isEmpty() || value.length() > MAX_CONTENT_ID_CHARS) {
            throw new IllegalArgumentException("Content ID length is invalid");
        }
        return value;
    }

    private static int readCount(DataInputStream input, String label, int maximum) throws IOException {
        int count = input.readInt();
        requireCount(label, count, maximum);
        return count;
    }

    private static void requireCount(String label, int count, int maximum) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Playable " + label + " count is outside supported bounds");
        }
    }
}
