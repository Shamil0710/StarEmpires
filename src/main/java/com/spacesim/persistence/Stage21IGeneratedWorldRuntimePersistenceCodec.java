package com.spacesim.persistence;

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

/** Deterministic bounded codec and backward-migration entry point for final Stage-21 checkpoints. */
public final class Stage21IGeneratedWorldRuntimePersistenceCodec {
    private static final int MAGIC = 0x53324957; // S2IW
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 1_500 * 1024 * 1024;
    private static final int MAX_STAGE21H_PAYLOAD_BYTES = 1_400 * 1024 * 1024;

    private Stage21IGeneratedWorldRuntimePersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /** Encodes one complete final Stage-21 checkpoint deterministically. */
    public static byte[] encode(Stage21IGeneratedWorldRuntimePersistentState state) {
        Stage21IGeneratedWorldRuntimePersistentState checked = Objects.requireNonNull(state, "state");
        byte[] stage21H = Stage21HGeneratedWorldRuntimePersistenceCodec.encode(checked.stage21HRuntime());
        requirePayload(stage21H, MAX_STAGE21H_PAYLOAD_BYTES, "Stage-21H runtime");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_FORMAT_VERSION);
                out.writeInt(checked.schemaVersion());
                out.writeUTF(checked.runtimeVersion());
                out.writeUTF(checked.migrationProvenance().sourceFormat());
                out.writeBoolean(checked.migrationProvenance().migrated());
                out.writeLong(checked.migrationProvenance().migrationTick());
                writePayload(out, stage21H);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21I checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21I encoding failure", exception);
        }
    }

    /** Decodes and fail-closed validates one native Stage-21I checkpoint. */
    public static Stage21IGeneratedWorldRuntimePersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21I checkpoint size outside bounded range");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-21I checkpoint magic");
            }
            int fileVersion = in.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21I file version: " + fileVersion);
            }
            int schemaVersion = in.readInt();
            String runtimeVersion = in.readUTF();
            String sourceFormat = in.readUTF();
            boolean migrated = in.readBoolean();
            long migrationTick = in.readLong();
            Stage21HGeneratedWorldRuntimePersistentState stage21H = Stage21HGeneratedWorldRuntimePersistenceCodec.decode(
                    readPayload(in, MAX_STAGE21H_PAYLOAD_BYTES, "Stage-21H runtime"));
            if (in.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after Stage-21I checkpoint");
            }
            return new Stage21IGeneratedWorldRuntimePersistentState(
                    schemaVersion,
                    runtimeVersion,
                    stage21H,
                    new Stage21IGeneratedWorldRuntimePersistentState.MigrationProvenance(
                            sourceFormat, migrated, migrationTick));
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21I checkpoint is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("Cannot decode Stage-21I checkpoint", exception);
        }
    }

    /**
     * Decodes a native final checkpoint or explicitly migrates a supported Stage-20.5/21A-H source.
     *
     * @param bytes native or supported legacy checkpoint bytes
     * @return current final Stage-21 checkpoint
     */
    public static Stage21IGeneratedWorldRuntimePersistentState decodeOrMigrate(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length >= Integer.BYTES) {
            int magic = ((bytes[0] & 0xff) << 24)
                    | ((bytes[1] & 0xff) << 16)
                    | ((bytes[2] & 0xff) << 8)
                    | (bytes[3] & 0xff);
            if (magic == MAGIC) {
                return decode(bytes);
            }
        }
        return Stage21IGeneratedWorldRuntimeMigration.migrateSupported(bytes);
    }

    /** Atomically writes a final Stage-21 checkpoint when the filesystem supports atomic replace. */
    public static void write(Path path, Stage21IGeneratedWorldRuntimePersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "stage21i-" + prefix;
        }
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

    /** Reads a bounded checkpoint file and accepts supported migration sources. */
    public static Stage21IGeneratedWorldRuntimePersistentState readOrMigrate(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21I checkpoint file size outside limits");
        }
        return decodeOrMigrate(Files.readAllBytes(source));
    }

    /** Reads only a native final Stage-21I checkpoint file. */
    public static Stage21IGeneratedWorldRuntimePersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21I checkpoint file size outside limits");
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
