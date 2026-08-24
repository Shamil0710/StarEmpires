package com.spacesim.persistence;

import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationStateCodec;

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

/** Deterministic bounded codec for the atomic Stage-21E generated-world checkpoint. */
public final class Stage21EGeneratedWorldRuntimePersistenceCodec {
    private static final int MAGIC = 0x53323145; // S21E
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 1024 * 1024 * 1024;
    private static final int MAX_STAGE21D_PAYLOAD_BYTES = 960 * 1024 * 1024;
    private static final int MAX_OPERATION_PAYLOAD_BYTES = 32 * 1024 * 1024;

    private Stage21EGeneratedWorldRuntimePersistenceCodec() { throw new AssertionError("No instances"); }

    /**
     * Encodes one complete Stage-21E checkpoint.
     *
     * @param state validated Stage-21E generated-world runtime state
     * @return deterministic bounded checkpoint bytes
     */
    public static byte[] encode(Stage21EGeneratedWorldRuntimePersistentState state) {
        Stage21EGeneratedWorldRuntimePersistentState checked = Objects.requireNonNull(state, "state");
        byte[] stage21D = Stage21DGeneratedWorldRuntimePersistenceCodec.encode(checked.stage21DRuntime());
        byte[] operations = StrategicOperationStateCodec.encode(checked.operationState());
        requirePayload(stage21D, MAX_STAGE21D_PAYLOAD_BYTES, "Stage-21D runtime");
        requirePayload(operations, MAX_OPERATION_PAYLOAD_BYTES, "Stage-21E operation state");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_FORMAT_VERSION);
                out.writeInt(checked.schemaVersion());
                out.writeUTF(checked.runtimeVersion());
                writePayload(out, stage21D);
                writePayload(out, operations);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21E checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21E encoding failure", exception);
        }
    }

    /**
     * Decodes and fail-closed validates one complete Stage-21E checkpoint.
     *
     * @param bytes encoded Stage-21E checkpoint bytes
     * @return decoded and cross-layer validated Stage-21E runtime state
     */
    public static Stage21EGeneratedWorldRuntimePersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21E checkpoint size outside bounded range");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("Invalid Stage-21E checkpoint magic");
            int version = in.readInt();
            if (version != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21E file version: " + version);
            }
            int schemaVersion = in.readInt();
            String runtimeVersion = in.readUTF();
            Stage21DGeneratedWorldRuntimePersistentState stage21D =
                    Stage21DGeneratedWorldRuntimePersistenceCodec.decode(
                            readPayload(in, MAX_STAGE21D_PAYLOAD_BYTES, "Stage-21D runtime"));
            StrategicOperationState operations = StrategicOperationStateCodec.decode(
                    readPayload(in, MAX_OPERATION_PAYLOAD_BYTES, "Stage-21E operation state"));
            if (in.read() != -1) throw new IllegalArgumentException("Trailing bytes after Stage-21E checkpoint");
            return new Stage21EGeneratedWorldRuntimePersistentState(
                    schemaVersion, runtimeVersion, stage21D, operations);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21E checkpoint is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("Cannot decode Stage-21E checkpoint", exception);
        }
    }

    /**
     * Atomically writes a Stage-21E checkpoint when supported by the filesystem.
     *
     * @param path destination checkpoint path
     * @param state validated Stage-21E generated-world runtime state
     * @throws IOException when the checkpoint cannot be written or replaced
     */
    public static void write(Path path, Stage21EGeneratedWorldRuntimePersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) prefix = "stage21e-" + prefix;
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
     * Reads and validates a bounded Stage-21E checkpoint file.
     *
     * @param path source checkpoint path
     * @return decoded and validated Stage-21E generated-world runtime state
     * @throws IOException when the checkpoint cannot be inspected or read
     */
    public static Stage21EGeneratedWorldRuntimePersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21E checkpoint file size outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writePayload(DataOutputStream out, byte[] payload) throws IOException {
        out.writeInt(payload.length);
        out.write(payload);
    }

    private static byte[] readPayload(DataInputStream in, int maximum, String label) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > maximum) throw new IllegalArgumentException(label + " payload size outside bounds");
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
