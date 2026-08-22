package com.spacesim.persistence;

import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FactionStrategicIntentStateCodec;

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

/** Deterministic bounded codec for the atomic Stage-21B generated-world runtime checkpoint. */
public final class Stage21BGeneratedWorldRuntimePersistenceCodec {
    private static final int MAGIC = 0x53323142; // S21B
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 768 * 1024 * 1024;
    private static final int MAX_STAGE21A_PAYLOAD_BYTES = 640 * 1024 * 1024;
    private static final int MAX_INTENT_PAYLOAD_BYTES = 128 * 1024 * 1024;

    private Stage21BGeneratedWorldRuntimePersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /** Encodes Stage 21B without rewriting any embedded Stage-21A/20 persistence schema. */
    public static byte[] encode(Stage21BGeneratedWorldRuntimePersistentState state) {
        Stage21BGeneratedWorldRuntimePersistentState checked = Objects.requireNonNull(state, "state");
        byte[] stage21A = Stage21AGeneratedWorldRuntimePersistenceCodec.encode(checked.stage21ARuntime());
        byte[] intents = FactionStrategicIntentStateCodec.encode(checked.strategicIntents());
        if (stage21A.length <= 0 || stage21A.length > MAX_STAGE21A_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Embedded Stage-21A checkpoint exceeds Stage-21B bounds");
        }
        if (intents.length <= 0 || intents.length > MAX_INTENT_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Stage-21B strategic intent payload size is outside bounds");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.schemaVersion());
                output.writeUTF(checked.runtimeVersion());
                writePayload(output, stage21A);
                writePayload(output, intents);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-21B checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21B encoding failure", exception);
        }
    }

    /** Decodes and cross-validates one complete Stage-21B generated-world checkpoint. */
    public static Stage21BGeneratedWorldRuntimePersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21B checkpoint size is outside bounded range");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-21B checkpoint magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-21B file version: " + fileVersion);
            }
            int schemaVersion = input.readInt();
            String runtimeVersion = input.readUTF();
            Stage21AGeneratedWorldRuntimePersistentState stage21A =
                    Stage21AGeneratedWorldRuntimePersistenceCodec.decode(
                            readPayload(input, MAX_STAGE21A_PAYLOAD_BYTES, "Stage-21A runtime"));
            List<FactionStrategicIntentState> intents = FactionStrategicIntentStateCodec.decode(
                    readPayload(input, MAX_INTENT_PAYLOAD_BYTES, "strategic intents"));
            if (input.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after Stage-21B checkpoint");
            }
            return new Stage21BGeneratedWorldRuntimePersistentState(
                    schemaVersion,
                    runtimeVersion,
                    stage21A,
                    intents);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21B checkpoint is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot decode Stage-21B checkpoint", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid Stage-21B checkpoint", exception);
        }
    }

    /** Atomically writes one Stage-21B checkpoint where the filesystem supports replacement. */
    public static void write(Path path, Stage21BGeneratedWorldRuntimePersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "stage21b-" + prefix;
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

    /** Reads one bounded Stage-21B checkpoint file. */
    public static Stage21BGeneratedWorldRuntimePersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-21B checkpoint file size is outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writePayload(DataOutputStream output, byte[] payload) throws IOException {
        output.writeInt(payload.length);
        output.write(payload);
    }

    private static byte[] readPayload(DataInputStream input, int maxBytes, String field) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maxBytes) {
            throw new IllegalArgumentException(field + " payload size is outside bounds");
        }
        byte[] payload = new byte[length];
        input.readFully(payload);
        return payload;
    }
}
