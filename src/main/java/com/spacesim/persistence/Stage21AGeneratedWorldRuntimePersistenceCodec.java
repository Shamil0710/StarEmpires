package com.spacesim.persistence;

import com.spacesim.world.FactionLivingActorState;
import com.spacesim.world.FactionLivingActorStateCodec;

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
import java.util.List;
import java.util.Objects;

/** Deterministic bounded codec for the atomic Stage-21A generated-world runtime checkpoint. */
public final class Stage21AGeneratedWorldRuntimePersistenceCodec {
    private static final int MAGIC = 0x53323141; // S21A
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 640 * 1024 * 1024;
    private static final int MAX_STAGE20_PAYLOAD_BYTES = 576 * 1024 * 1024;
    private static final int MAX_ACTOR_PAYLOAD_BYTES = 64 * 1024 * 1024;

    private Stage21AGeneratedWorldRuntimePersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one complete Stage-21A checkpoint without rewriting embedded Stage-20 schemas.
     *
     * @param state validated Stage-21A composition state
     * @return deterministic binary payload
     */
    public static byte[] encode(Stage21AGeneratedWorldRuntimePersistentState state) {
        Stage21AGeneratedWorldRuntimePersistentState checked = Objects.requireNonNull(state, "state");
        byte[] stage20 = Stage20GeneratedWorldRuntimePersistenceCodec.encode(checked.stage20Runtime());
        byte[] actors = FactionLivingActorStateCodec.encode(checked.livingActors());
        if (stage20.length > MAX_STAGE20_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Embedded Stage-20 checkpoint exceeds Stage-21A bounds");
        }
        if (actors.length <= 0 || actors.length > MAX_ACTOR_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Stage-21A actor payload size is outside bounds");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.schemaVersion());
                output.writeUTF(checked.runtimeVersion());
                writePayload(output, stage20);
                writePayload(output, actors);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21A checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21A encoding failure", exception);
        }
    }

    /**
     * Decodes and cross-validates one complete Stage-21A generated-world checkpoint.
     *
     * @param bytes encoded checkpoint
     * @return immutable validated composition state
     */
    public static Stage21AGeneratedWorldRuntimePersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21A checkpoint size is outside bounded range");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-21A checkpoint magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21A file version: " + fileVersion);
            }
            int schemaVersion = input.readInt();
            String runtimeVersion = input.readUTF();
            Stage20GeneratedWorldRuntimePersistentState stage20 =
                    Stage20GeneratedWorldRuntimePersistenceCodec.decode(
                            readPayload(input, MAX_STAGE20_PAYLOAD_BYTES, "Stage-20 runtime"));
            List<FactionLivingActorState> actors = FactionLivingActorStateCodec.decode(
                    readPayload(input, MAX_ACTOR_PAYLOAD_BYTES, "living actors"));
            if (input.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after Stage-21A checkpoint");
            }
            return new Stage21AGeneratedWorldRuntimePersistentState(
                    schemaVersion,
                    runtimeVersion,
                    stage20,
                    actors);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21A checkpoint is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot decode Stage-21A checkpoint", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid Stage-21A checkpoint", exception);
        }
    }

    /**
     * Atomically writes one Stage-21A checkpoint where the filesystem supports atomic replacement.
     *
     * @param path target checkpoint file
     * @param state complete validated state
     * @throws IOException when the file cannot be written or replaced
     */
    public static void write(Path path, Stage21AGeneratedWorldRuntimePersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "stage21a-" + prefix;
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
     * Reads one bounded Stage-21A checkpoint file.
     *
     * @param path existing checkpoint path
     * @return decoded validated state
     * @throws IOException when the file cannot be read
     */
    public static Stage21AGeneratedWorldRuntimePersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21A checkpoint file size is outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writePayload(DataOutputStream output, byte[] payload) throws IOException {
        output.writeInt(payload.length);
        output.write(payload);
    }

    private static byte[] readPayload(
            DataInputStream input,
            int maxBytes,
            String field) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maxBytes) {
            throw new IllegalArgumentException(field + " payload size is outside bounds");
        }
        byte[] payload = new byte[length];
        input.readFully(payload);
        return payload;
    }
}
