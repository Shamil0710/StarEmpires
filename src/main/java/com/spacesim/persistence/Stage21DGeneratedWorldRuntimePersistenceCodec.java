package com.spacesim.persistence;

import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandStateCodec;

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
import java.util.Objects;

/** Deterministic bounded codec for the atomic Stage-21D generated-world runtime checkpoint. */
public final class Stage21DGeneratedWorldRuntimePersistenceCodec {
    private static final int MAGIC = 0x53323144; // S21D
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 960 * 1024 * 1024;
    private static final int MAX_STAGE21C_PAYLOAD_BYTES = 896 * 1024 * 1024;
    private static final int MAX_COMMAND_PAYLOAD_BYTES = 32 * 1024 * 1024;

    private Stage21DGeneratedWorldRuntimePersistenceCodec() { throw new AssertionError("No instances"); }

    /**
     * Encodes one atomic Stage-21D generated-world checkpoint.
     *
     * <p>The Stage-21C runtime is embedded through its existing codec unchanged, while the Stage-21D
     * command payload is encoded separately through {@link FleetCommandStateCodec}.</p>
     *
     * @param state validated Stage-21D persistent runtime wrapper
     * @return deterministic bounded checkpoint bytes
     * @throws IllegalArgumentException when an embedded or aggregate payload exceeds configured bounds
     */
    public static byte[] encode(Stage21DGeneratedWorldRuntimePersistentState state) {
        Stage21DGeneratedWorldRuntimePersistentState checked = Objects.requireNonNull(state, "state");
        byte[] stage21C = Stage21CGeneratedWorldRuntimePersistenceCodec.encode(checked.stage21CRuntime());
        byte[] command = FleetCommandStateCodec.encode(checked.fleetCommandState());
        requirePayload(stage21C, MAX_STAGE21C_PAYLOAD_BYTES, "Stage-21C runtime");
        requirePayload(command, MAX_COMMAND_PAYLOAD_BYTES, "Stage-21D command state");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_FORMAT_VERSION);
                out.writeInt(checked.schemaVersion());
                out.writeUTF(checked.runtimeVersion());
                writePayload(out, stage21C);
                writePayload(out, command);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21D checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21D encoding failure", exception);
        }
    }

    /**
     * Decodes and validates an atomic Stage-21D generated-world checkpoint.
     *
     * @param bytes encoded checkpoint bytes
     * @return validated Stage-21D persistent runtime wrapper
     * @throws IllegalArgumentException when magic, version, bounds, embedded state or trailing bytes are invalid
     */
    public static Stage21DGeneratedWorldRuntimePersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21D checkpoint size outside bounded range");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("Invalid Stage-21D checkpoint magic");
            int version = in.readInt();
            if (version != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21D file version: " + version);
            }
            int schemaVersion = in.readInt();
            String runtimeVersion = in.readUTF();
            Stage21CGeneratedWorldRuntimePersistentState stage21C =
                    Stage21CGeneratedWorldRuntimePersistenceCodec.decode(
                            readPayload(in, MAX_STAGE21C_PAYLOAD_BYTES, "Stage-21C runtime"));
            FleetCommandState commandState = FleetCommandStateCodec.decode(
                    readPayload(in, MAX_COMMAND_PAYLOAD_BYTES, "Stage-21D command state"));
            if (in.read() != -1) throw new IllegalArgumentException("Trailing bytes after Stage-21D checkpoint");
            return new Stage21DGeneratedWorldRuntimePersistentState(
                    schemaVersion, runtimeVersion, stage21C, commandState);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21D checkpoint is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("Cannot decode Stage-21D checkpoint", exception);
        }
    }

    /**
     * Atomically writes a Stage-21D checkpoint when the filesystem supports atomic replacement.
     *
     * @param path destination checkpoint path
     * @param state validated Stage-21D persistent runtime wrapper
     * @throws IOException when temporary-file creation, writing or replacement fails
     */
    public static void write(Path path, Stage21DGeneratedWorldRuntimePersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) prefix = "stage21d-" + prefix;
        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Reads and validates a bounded Stage-21D checkpoint file.
     *
     * @param path source checkpoint path
     * @return decoded validated Stage-21D persistent runtime wrapper
     * @throws IOException when the file cannot be inspected or read
     * @throws IllegalArgumentException when the file or decoded payload is outside accepted bounds
     */
    public static Stage21DGeneratedWorldRuntimePersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21D checkpoint file size outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writePayload(DataOutputStream out, byte[] payload) throws IOException {
        out.writeInt(payload.length);
        out.write(payload);
    }

    private static byte[] readPayload(DataInputStream in, int maximum, String label) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > maximum) {
            throw new IllegalArgumentException(label + " payload size outside bounds");
        }
        byte[] payload = new byte[length];
        in.readFully(payload);
        return payload;
    }

    private static void requirePayload(byte[] payload, int maximum, String label) {
        if (payload.length <= 0 || payload.length > maximum) {
            throw new IllegalArgumentException(label + " payload size outside bounds");
        }
    }
}
