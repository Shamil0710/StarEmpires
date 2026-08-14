package com.spacesim.persistence;

import com.spacesim.player.DiscoveredObjectRef;
import com.spacesim.player.FleetOrderType;
import com.spacesim.player.PlayableWorldState;
import com.spacesim.player.PlayerFleetOrderState;
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
 * Bounded binary codec for the playable save envelope.
 *
 * <p>The envelope embeds an unchanged {@link WorldStateCodec} payload plus player state. Raw
 * pre-player WorldState saves migrate with no player. Playable schema v1 migrates undocked,
 * schema v2 migrates with docking but no Stage-15 fleet orders, and current schema v3 persists
 * declarative delegated orders without serializing transient execution objects.</p>
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
    private static final int MAX_FLEET_ORDERS = 100_000;
    private static final int MAX_PATROL_SYSTEMS = 4096;

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
     * Decodes a current playable save or migrates supported legacy formats.
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
            requireSupportedSchema(schemaVersion);
            int worldLength = input.readInt();
            if (worldLength <= 0 || worldLength > MAX_WORLD_BYTES || worldLength > input.available()) {
                throw new IllegalArgumentException("Embedded WorldState length is invalid");
            }
            byte[] worldBytes = input.readNBytes(worldLength);
            if (worldBytes.length != worldLength) {
                throw new IllegalArgumentException("Embedded WorldState is truncated");
            }
            WorldState world = WorldStateCodec.decode(worldBytes);
            PlayerState player = input.readBoolean() ? readPlayer(input, schemaVersion) : null;
            if (input.read() != -1) {
                throw new IllegalArgumentException("Unexpected trailing bytes after playable state");
            }
            return new PlayableWorldState(PlayableWorldState.CURRENT_VERSION, world, player);
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

    private static void requireSupportedSchema(int schemaVersion) {
        if (schemaVersion != PlayableWorldState.CURRENT_VERSION
                && schemaVersion != PlayableWorldState.LEGACY_DOCKING_VERSION
                && schemaVersion != PlayableWorldState.LEGACY_STAGE12A_VERSION) {
            throw new IllegalArgumentException("Unsupported playable schema: " + schemaVersion);
        }
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
        requireCount("fleet orders", player.fleetOrders().size(), MAX_FLEET_ORDERS);

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
            writeObjectRef(output, reference);
        }

        output.writeBoolean(player.homeSystemId() != null);
        if (player.homeSystemId() != null) {
            output.writeLong(player.homeSystemId().value());
        }
        output.writeBoolean(player.dockedAt() != null);
        if (player.dockedAt() != null) {
            writeObjectRef(output, player.dockedAt());
        }

        output.writeInt(player.fleetOrders().size());
        for (PlayerFleetOrderState order : player.fleetOrders()) {
            writeFleetOrder(output, order);
        }
    }

    private static PlayerState readPlayer(DataInputStream input, int schemaVersion) throws IOException {
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
            objects.add(readObjectRef(input));
        }
        StarSystemId home = input.readBoolean() ? new StarSystemId(input.readLong()) : null;
        DiscoveredObjectRef dockedAt = schemaVersion >= PlayableWorldState.LEGACY_DOCKING_VERSION
                && input.readBoolean() ? readObjectRef(input) : null;

        List<PlayerFleetOrderState> orders = List.of();
        if (schemaVersion >= PlayableWorldState.CURRENT_VERSION) {
            int orderCount = readCount(input, "fleet orders", MAX_FLEET_ORDERS);
            List<PlayerFleetOrderState> decodedOrders = new ArrayList<>(orderCount);
            for (int index = 0; index < orderCount; index++) {
                decodedOrders.add(readFleetOrder(input));
            }
            orders = List.copyOf(decodedOrders);
        }
        return new PlayerState(
                wallet, affiliation, reputations, fleets, activeFleet, systems, objects, home, dockedAt, orders);
    }

    private static void writeFleetOrder(DataOutputStream output, PlayerFleetOrderState order) throws IOException {
        output.writeLong(order.fleetId().value());
        output.writeUTF(order.type().name());
        writeNullableSystemId(output, order.targetSystemId());
        writeNullableEntityId(output, order.targetEntityId());
        writeNullableSystemId(output, order.secondarySystemId());
        writeNullableEntityId(output, order.secondaryEntityId());
        output.writeBoolean(order.targetFleetId() != null);
        if (order.targetFleetId() != null) {
            output.writeLong(order.targetFleetId().value());
        }
        writeNullableContentId(output, order.itemContentId());
        output.writeFloat(order.targetX());
        output.writeFloat(order.targetY());
        requireCount("patrol systems", order.patrolSystemIds().size(), MAX_PATROL_SYSTEMS);
        output.writeInt(order.patrolSystemIds().size());
        for (StarSystemId systemId : order.patrolSystemIds()) {
            output.writeLong(systemId.value());
        }
    }

    private static PlayerFleetOrderState readFleetOrder(DataInputStream input) throws IOException {
        FleetId fleetId = new FleetId(input.readLong());
        FleetOrderType type;
        try {
            type = FleetOrderType.valueOf(input.readUTF());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown persisted fleet order type", exception);
        }
        StarSystemId targetSystem = readNullableSystemId(input);
        EntityId targetEntity = readNullableEntityId(input);
        StarSystemId secondarySystem = readNullableSystemId(input);
        EntityId secondaryEntity = readNullableEntityId(input);
        FleetId targetFleet = input.readBoolean() ? new FleetId(input.readLong()) : null;
        String itemContentId = readNullableContentId(input);
        float targetX = input.readFloat();
        float targetY = input.readFloat();
        int patrolCount = readCount(input, "patrol systems", MAX_PATROL_SYSTEMS);
        List<StarSystemId> patrol = new ArrayList<>(patrolCount);
        for (int index = 0; index < patrolCount; index++) {
            patrol.add(new StarSystemId(input.readLong()));
        }
        return new PlayerFleetOrderState(
                fleetId, type, targetSystem, targetEntity, secondarySystem, secondaryEntity,
                targetFleet, itemContentId, targetX, targetY, patrol);
    }

    private static void writeObjectRef(DataOutputStream output, DiscoveredObjectRef reference) throws IOException {
        output.writeLong(reference.systemId().value());
        output.writeLong(reference.entityId().value());
    }

    private static DiscoveredObjectRef readObjectRef(DataInputStream input) throws IOException {
        return new DiscoveredObjectRef(
                new StarSystemId(input.readLong()),
                new EntityId(input.readLong()));
    }

    private static void writeNullableSystemId(DataOutputStream output, StarSystemId value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeLong(value.value());
        }
    }

    private static StarSystemId readNullableSystemId(DataInputStream input) throws IOException {
        return input.readBoolean() ? new StarSystemId(input.readLong()) : null;
    }

    private static void writeNullableEntityId(DataOutputStream output, EntityId value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeLong(value.value());
        }
    }

    private static EntityId readNullableEntityId(DataInputStream input) throws IOException {
        return input.readBoolean() ? new EntityId(input.readLong()) : null;
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
