package com.spacesim.persistence;

import com.spacesim.persistence.Stage20MaterializationPersistentState.PhysicalEntityState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;

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
 * Deterministic binary codec for the Stage-20 materialization persistence envelope.
 *
 * <p>The envelope embeds one unchanged ordinary {@link GameStateCodec} payload and appends an
 * explicitly versioned Stage-20 hierarchical physical-state sidecar. It does not modify the legacy
 * GameState v4 file format, so existing saves and migration tests remain byte-compatible.</p>
 */
public final class Stage20MaterializationPersistenceCodec {
    private static final int MAGIC = 0x5332304D; // S20M
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_ENVELOPE_BYTES = 40 * 1024 * 1024;
    private static final int MAX_EMBEDDED_GAME_STATE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_PHYSICAL_ENTITIES = 100_000;

    private Stage20MaterializationPersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes a validated Stage-20 persistence envelope deterministically.
     *
     * @param state Stage-20 materialization persistence envelope
     * @return new binary envelope bytes
     */
    public static byte[] encode(Stage20MaterializationPersistentState state) {
        Stage20MaterializationPersistentState checked = Objects.requireNonNull(state, "state");
        byte[] gameStateBytes = GameStateCodec.encode(checked.gameState());
        if (gameStateBytes.length <= 0 || gameStateBytes.length > MAX_EMBEDDED_GAME_STATE_BYTES) {
            throw new IllegalArgumentException("Embedded GameState size is outside Stage-20 envelope limits");
        }
        if (checked.physicalEntities().size() > MAX_PHYSICAL_ENTITIES) {
            throw new IllegalArgumentException("Too many Stage-20 physical entities");
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.envelopeVersion());
                output.writeInt(gameStateBytes.length);
                output.write(gameStateBytes);
                output.writeInt(checked.physicalEntities().size());
                for (PhysicalEntityState physical : checked.physicalEntities()) {
                    writePhysical(output, physical);
                }
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_ENVELOPE_BYTES) {
                throw new IllegalArgumentException("Stage-20 materialization envelope exceeds size limit");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-20 persistence encoding error", exception);
        }
    }

    /**
     * Decodes and validates a Stage-20 persistence envelope.
     *
     * @param bytes complete binary Stage-20 envelope
     * @return immutable decoded persistence state
     */
    public static Stage20MaterializationPersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Stage-20 materialization envelope size is outside limits");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-20 materialization persistence magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-20 materialization file version: " + fileVersion);
            }
            int envelopeVersion = input.readInt();
            if (envelopeVersion != Stage20MaterializationPersistentState.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-20 materialization envelope version: " + envelopeVersion);
            }
            int gameStateLength = input.readInt();
            if (gameStateLength <= 0 || gameStateLength > MAX_EMBEDDED_GAME_STATE_BYTES) {
                throw new IllegalArgumentException("Embedded GameState length is outside limits");
            }
            byte[] gameStateBytes = input.readNBytes(gameStateLength);
            if (gameStateBytes.length != gameStateLength) {
                throw new EOFException("Embedded GameState is truncated");
            }
            GameState gameState = GameStateCodec.decode(gameStateBytes);

            int physicalCount = input.readInt();
            if (physicalCount < 0 || physicalCount > MAX_PHYSICAL_ENTITIES) {
                throw new IllegalArgumentException("Stage-20 physical entity count is outside limits");
            }
            List<PhysicalEntityState> physical = new ArrayList<>(physicalCount);
            for (int index = 0; index < physicalCount; index++) {
                physical.add(readPhysical(input));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Unexpected trailing bytes after Stage-20 persistence envelope");
            }
            return new Stage20MaterializationPersistentState(envelopeVersion, gameState, physical);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-20 materialization persistence envelope is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Stage-20 materialization persistence envelope cannot be decoded", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("Stage-20 persistence envelope contains invalid values", exception);
        }
    }

    /**
     * Atomically writes a Stage-20 persistence envelope where supported by the filesystem.
     *
     * @param path target file path
     * @param state persistence envelope to write
     * @throws IOException when the file cannot be written/replaced
     */
    public static void write(Path path, Stage20MaterializationPersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Reads and decodes one bounded Stage-20 persistence envelope file.
     *
     * @param path source file path
     * @return decoded Stage-20 persistence envelope
     * @throws IOException when the file cannot be read
     */
    public static Stage20MaterializationPersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Stage-20 persistence file size is outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writePhysical(DataOutputStream output, PhysicalEntityState physical) throws IOException {
        LocalPhysicalKinematics state = physical.physicalState();
        LocalPhysicalPosition position = state.position();
        output.writeLong(physical.id().value());
        output.writeLong(position.cellX());
        output.writeLong(position.cellY());
        output.writeDouble(position.offsetXM());
        output.writeDouble(position.offsetYM());
        output.writeDouble(state.velocityXMps());
        output.writeDouble(state.velocityYMps());
    }

    private static PhysicalEntityState readPhysical(DataInputStream input) throws IOException {
        EntityId id = new EntityId(input.readLong());
        long cellX = input.readLong();
        long cellY = input.readLong();
        double offsetX = input.readDouble();
        double offsetY = input.readDouble();
        double velocityX = input.readDouble();
        double velocityY = input.readDouble();
        LocalPhysicalPosition position = new LocalPhysicalPosition(cellX, cellY, offsetX, offsetY);
        return new PhysicalEntityState(id, new LocalPhysicalKinematics(position, velocityX, velocityY));
    }
}
