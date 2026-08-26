package com.spacesim.persistence;

import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.SettlementRecoveryStateCodec;

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

/** Deterministic bounded codec for the atomic Stage-21G generated-world checkpoint. */
public final class Stage21GGeneratedWorldRuntimePersistenceCodec {
    private static final int MAGIC = 0x53324757; // S2GW
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 1_300 * 1024 * 1024;
    private static final int MAX_STAGE21F_PAYLOAD_BYTES = 1_200 * 1024 * 1024;
    private static final int MAX_RECOVERY_PAYLOAD_BYTES = 64 * 1024 * 1024;

    private Stage21GGeneratedWorldRuntimePersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one complete Stage-21G checkpoint.
     *
     * @param state validated Stage-21G generated-world runtime state
     * @return deterministic bounded checkpoint bytes
     */
    public static byte[] encode(Stage21GGeneratedWorldRuntimePersistentState state) {
        Stage21GGeneratedWorldRuntimePersistentState checked = Objects.requireNonNull(state, "state");
        byte[] stage21F = Stage21FGeneratedWorldRuntimePersistenceCodec.encode(checked.stage21FRuntime());
        byte[] recovery = SettlementRecoveryStateCodec.encode(checked.settlementRecovery());
        requirePayload(stage21F, MAX_STAGE21F_PAYLOAD_BYTES, "Stage-21F runtime");
        requirePayload(recovery, MAX_RECOVERY_PAYLOAD_BYTES, "Stage-21G recovery state");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_FORMAT_VERSION);
                out.writeInt(checked.schemaVersion());
                out.writeUTF(checked.runtimeVersion());
                writePayload(out, stage21F);
                writePayload(out, recovery);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21G checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21G encoding failure", exception);
        }
    }

    /**
     * Decodes and fail-closed validates one complete Stage-21G checkpoint.
     *
     * @param bytes encoded Stage-21G checkpoint bytes
     * @return decoded and cross-layer validated Stage-21G runtime state
     */
    public static Stage21GGeneratedWorldRuntimePersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21G checkpoint size outside bounded range");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("Invalid Stage-21G checkpoint magic");
            int version = in.readInt();
            if (version != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21G file version: " + version);
            }
            int schemaVersion = in.readInt();
            String runtimeVersion = in.readUTF();
            Stage21FGeneratedWorldRuntimePersistentState stage21F =
                    Stage21FGeneratedWorldRuntimePersistenceCodec.decode(
                            readPayload(in, MAX_STAGE21F_PAYLOAD_BYTES, "Stage-21F runtime"));
            SettlementRecoveryState recovery = SettlementRecoveryStateCodec.decode(
                    readPayload(in, MAX_RECOVERY_PAYLOAD_BYTES, "Stage-21G recovery state"));
            if (in.read() != -1) throw new IllegalArgumentException("Trailing bytes after Stage-21G checkpoint");
            return new Stage21GGeneratedWorldRuntimePersistentState(
                    schemaVersion, runtimeVersion, stage21F, recovery);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21G checkpoint is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("Cannot decode Stage-21G checkpoint", exception);
        }
    }

    /**
     * Atomically writes a Stage-21G checkpoint when supported by the filesystem.
     *
     * @param path destination checkpoint path
     * @param state validated Stage-21G generated-world runtime state
     * @throws IOException when the checkpoint cannot be written or replaced
     */
    public static void write(Path path, Stage21GGeneratedWorldRuntimePersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) prefix = "stage21g-" + prefix;
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
     * Reads and validates a bounded Stage-21G checkpoint file.
     *
     * @param path source checkpoint path
     * @return decoded and validated Stage-21G generated-world runtime state
     * @throws IOException when the checkpoint cannot be inspected or read
     */
    public static Stage21GGeneratedWorldRuntimePersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21G checkpoint file size outside limits");
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
