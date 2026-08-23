package com.spacesim.persistence;

import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.DiplomaticLifecycleStateCodec;

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

/** Deterministic bounded codec for the atomic Stage-21C generated-world runtime checkpoint. */
public final class Stage21CGeneratedWorldRuntimePersistenceCodec {
    private static final int MAGIC = 0x53323152; // S21R, outer runtime composition
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 896 * 1024 * 1024;
    private static final int MAX_STAGE21B_PAYLOAD_BYTES = 768 * 1024 * 1024;
    private static final int MAX_DIPLOMACY_PAYLOAD_BYTES = 64 * 1024 * 1024;
    private static final int MAX_WARFARE_PAYLOAD_BYTES = 16 * 1024 * 1024;

    private Stage21CGeneratedWorldRuntimePersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes Stage 21C while preserving the complete embedded Stage-21B and Stage-19 authorities.
     *
     * @param state complete validated Stage-21C checkpoint composition
     * @return deterministic bounded binary payload
     */
    public static byte[] encode(Stage21CGeneratedWorldRuntimePersistentState state) {
        Stage21CGeneratedWorldRuntimePersistentState checked = Objects.requireNonNull(state, "state");
        byte[] stage21B = Stage21BGeneratedWorldRuntimePersistenceCodec.encode(checked.stage21BRuntime());
        byte[] diplomacy = DiplomaticLifecycleStateCodec.encode(checked.diplomacyLifecycle());
        byte[] warfare = Stage19ConflictStateCodec.encode(checked.warfareState());
        requirePayload(stage21B, MAX_STAGE21B_PAYLOAD_BYTES, "Stage-21B runtime");
        requirePayload(diplomacy, MAX_DIPLOMACY_PAYLOAD_BYTES, "Stage-21C diplomacy");
        requirePayload(warfare, MAX_WARFARE_PAYLOAD_BYTES, "Stage-19 warfare");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.schemaVersion());
                output.writeUTF(checked.runtimeVersion());
                writePayload(output, stage21B);
                writePayload(output, diplomacy);
                writePayload(output, warfare);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21C checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21C encoding failure", exception);
        }
    }

    /**
     * Decodes and cross-validates one complete Stage-21C generated-world checkpoint.
     *
     * @param bytes encoded Stage-21C checkpoint bytes
     * @return immutable validated Stage-21C checkpoint composition
     */
    public static Stage21CGeneratedWorldRuntimePersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21C checkpoint size is outside bounded range");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-21C checkpoint magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21C file version: " + fileVersion);
            }
            int schemaVersion = input.readInt();
            String runtimeVersion = input.readUTF();
            Stage21BGeneratedWorldRuntimePersistentState stage21B =
                    Stage21BGeneratedWorldRuntimePersistenceCodec.decode(
                            readPayload(input, MAX_STAGE21B_PAYLOAD_BYTES, "Stage-21B runtime"));
            DiplomaticLifecycleState diplomacy = DiplomaticLifecycleStateCodec.decode(
                    readPayload(input, MAX_DIPLOMACY_PAYLOAD_BYTES, "Stage-21C diplomacy"));
            Stage19ConflictState warfare = Stage19ConflictStateCodec.decode(
                    readPayload(input, MAX_WARFARE_PAYLOAD_BYTES, "Stage-19 warfare"));
            if (input.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after Stage-21C checkpoint");
            }
            return new Stage21CGeneratedWorldRuntimePersistentState(
                    schemaVersion,
                    runtimeVersion,
                    stage21B,
                    diplomacy,
                    warfare);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21C checkpoint is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot decode Stage-21C checkpoint", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid Stage-21C checkpoint", exception);
        }
    }

    /**
     * Atomically writes one complete Stage-21C checkpoint where the filesystem supports replacement.
     *
     * @param path target checkpoint path
     * @param state complete validated Stage-21C state
     * @throws IOException when the file cannot be written or replaced
     */
    public static void write(Path path, Stage21CGeneratedWorldRuntimePersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "stage21c-" + prefix;
        }
        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Reads one bounded complete Stage-21C checkpoint file.
     *
     * @param path existing Stage-21C checkpoint path
     * @return decoded validated Stage-21C state
     * @throws IOException when the file cannot be read
     */
    public static Stage21CGeneratedWorldRuntimePersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21C checkpoint file size is outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writePayload(DataOutputStream output, byte[] payload) throws IOException {
        output.writeInt(payload.length);
        output.write(payload);
    }

    private static byte[] readPayload(DataInputStream input, int maximum, String label) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum) {
            throw new IllegalArgumentException(label + " payload size is outside bounds");
        }
        byte[] payload = new byte[length];
        input.readFully(payload);
        return payload;
    }

    private static void requirePayload(byte[] payload, int maximum, String label) {
        if (payload.length <= 0 || payload.length > maximum) {
            throw new IllegalArgumentException(label + " payload size is outside bounds");
        }
    }
}