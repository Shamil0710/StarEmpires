package com.spacesim.persistence;

import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.Stage21HNpcMissionStateCodec;

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

/** Deterministic bounded codec for the atomic Stage-21H generated-world checkpoint. */
public final class Stage21HGeneratedWorldRuntimePersistenceCodec {
    private static final int MAGIC = 0x53324857; // S2HW
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 1_400 * 1024 * 1024;
    private static final int MAX_STAGE21G_PAYLOAD_BYTES = 1_300 * 1024 * 1024;
    private static final int MAX_NPC_MISSION_PAYLOAD_BYTES = 64 * 1024 * 1024;

    private Stage21HGeneratedWorldRuntimePersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one complete Stage-21H checkpoint.
     *
     * @param state validated Stage-21H generated-world runtime state
     * @return deterministic bounded checkpoint bytes
     */
    public static byte[] encode(Stage21HGeneratedWorldRuntimePersistentState state) {
        Stage21HGeneratedWorldRuntimePersistentState checked = Objects.requireNonNull(state, "state");
        byte[] stage21G = Stage21GGeneratedWorldRuntimePersistenceCodec.encode(checked.stage21GRuntime());
        byte[] npcMissions = Stage21HNpcMissionStateCodec.encode(checked.npcMissionState());
        requirePayload(stage21G, MAX_STAGE21G_PAYLOAD_BYTES, "Stage-21G runtime");
        requirePayload(npcMissions, MAX_NPC_MISSION_PAYLOAD_BYTES, "Stage-21H NPC/mission state");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_FORMAT_VERSION);
                out.writeInt(checked.schemaVersion());
                out.writeUTF(checked.runtimeVersion());
                writePayload(out, stage21G);
                writePayload(out, npcMissions);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21H checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21H encoding failure", exception);
        }
    }

    /**
     * Decodes and fail-closed validates one complete Stage-21H checkpoint.
     *
     * @param bytes encoded Stage-21H checkpoint bytes
     * @return decoded and cross-layer validated Stage-21H runtime state
     */
    public static Stage21HGeneratedWorldRuntimePersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21H checkpoint size outside bounded range");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-21H checkpoint magic");
            }
            int version = in.readInt();
            if (version != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21H file version: " + version);
            }
            int schemaVersion = in.readInt();
            String runtimeVersion = in.readUTF();
            Stage21GGeneratedWorldRuntimePersistentState stage21G =
                    Stage21GGeneratedWorldRuntimePersistenceCodec.decode(
                            readPayload(in, MAX_STAGE21G_PAYLOAD_BYTES, "Stage-21G runtime"));
            Stage21HNpcMissionState npcMissions = Stage21HNpcMissionStateCodec.decode(
                    readPayload(in, MAX_NPC_MISSION_PAYLOAD_BYTES, "Stage-21H NPC/mission state"));
            if (in.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after Stage-21H checkpoint");
            }
            return new Stage21HGeneratedWorldRuntimePersistentState(
                    schemaVersion, runtimeVersion, stage21G, npcMissions);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21H checkpoint is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("Cannot decode Stage-21H checkpoint", exception);
        }
    }

    /**
     * Atomically writes a Stage-21H checkpoint when supported by the filesystem.
     *
     * @param path destination checkpoint path
     * @param state validated Stage-21H generated-world runtime state
     * @throws IOException when the checkpoint cannot be written or replaced
     */
    public static void write(Path path, Stage21HGeneratedWorldRuntimePersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "stage21h-" + prefix;
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

    /**
     * Reads and validates a bounded Stage-21H checkpoint file.
     *
     * @param path source checkpoint path
     * @return decoded and validated Stage-21H generated-world runtime state
     * @throws IOException when the checkpoint cannot be inspected or read
     */
    public static Stage21HGeneratedWorldRuntimePersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21H checkpoint file size outside limits");
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
