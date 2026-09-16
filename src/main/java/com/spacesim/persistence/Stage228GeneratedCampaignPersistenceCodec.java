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

/**
 * Deterministic bounded file codec for the M22.8 campaign envelope.
 *
 * <p>Native M22.8 files embed the accepted Stage-21I checkpoint bytes unchanged and add one
 * independently versioned small-craft sidecar. Supported Stage-20.5/21A-I files migrate through the
 * accepted Stage-21I migration chain and receive an empty, non-granting M22.8A sidecar.</p>
 */
public final class Stage228GeneratedCampaignPersistenceCodec {
    private static final int MAGIC = 0x53323843; // S28C
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 1_800 * 1024 * 1024;
    private static final int MAX_STAGE21_PAYLOAD_BYTES = 1_500 * 1024 * 1024;
    private static final int MAX_SMALL_CRAFT_PAYLOAD_BYTES = 256 * 1024 * 1024;

    private Stage228GeneratedCampaignPersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one complete M22.8 campaign checkpoint.
     *
     * @param state complete M22.8 campaign envelope
     * @return deterministic bounded bytes
     */
    public static byte[] encode(Stage228GeneratedCampaignPersistentState state) {
        Stage228GeneratedCampaignPersistentState checked = Objects.requireNonNull(state, "state");
        byte[] stage21 = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(checked.stage21Runtime());
        byte[] smallCraft = Stage228SmallCraftPersistenceCodec.encode(checked.smallCraft());
        requirePayload(stage21, MAX_STAGE21_PAYLOAD_BYTES, "Stage-21I runtime");
        requirePayload(smallCraft, MAX_SMALL_CRAFT_PAYLOAD_BYTES, "M22.8A small craft");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(checked.schemaVersion());
                out.writeUTF(checked.runtimeVersion());
                writePayload(out, stage21);
                writePayload(out, smallCraft);
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("M22.8 campaign checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory M22.8 encoding failure", exception);
        }
    }

    /**
     * Decodes one native M22.8 campaign checkpoint and fails closed on trailing/corrupt data.
     *
     * @param bytes native M22.8 checkpoint bytes
     * @return validated M22.8 campaign envelope
     */
    public static Stage228GeneratedCampaignPersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("M22.8 checkpoint size outside bounded range");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid M22.8 checkpoint magic");
            }
            int fileVersion = in.readInt();
            if (fileVersion != FILE_VERSION) {
                throw new IllegalArgumentException("Unsupported M22.8 file version: " + fileVersion);
            }
            int schemaVersion = in.readInt();
            String runtimeVersion = in.readUTF();
            Stage21IGeneratedWorldRuntimePersistentState stage21 =
                    Stage21IGeneratedWorldRuntimePersistenceCodec.decode(
                            readPayload(in, MAX_STAGE21_PAYLOAD_BYTES, "Stage-21I runtime"));
            Stage228SmallCraftPersistentState smallCraft = Stage228SmallCraftPersistenceCodec.decode(
                    readPayload(in, MAX_SMALL_CRAFT_PAYLOAD_BYTES, "M22.8A small craft"));
            if (in.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after M22.8 checkpoint");
            }
            return new Stage228GeneratedCampaignPersistentState(
                    schemaVersion,
                    runtimeVersion,
                    stage21,
                    smallCraft);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("M22.8 checkpoint is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("Cannot decode M22.8 checkpoint", exception);
        }
    }

    /**
     * Decodes a native M22.8 checkpoint or adopts any source supported by the final Stage-21 migration chain.
     *
     * @param bytes native M22.8 or supported Stage-20.5/21A-I bytes
     * @return current M22.8 envelope; legacy sources receive zero small craft
     */
    public static Stage228GeneratedCampaignPersistentState decodeOrMigrate(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length >= Integer.BYTES && readMagic(bytes) == MAGIC) {
            return decode(bytes);
        }
        Stage21IGeneratedWorldRuntimePersistentState stage21 =
                Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(bytes);
        return Stage228GeneratedCampaignPersistentState.adoptStage21(stage21);
    }

    /**
     * Atomically writes a current M22.8 campaign checkpoint when supported by the filesystem.
     *
     * @param path destination checkpoint path
     * @param state complete M22.8 campaign envelope
     * @throws IOException when persistence fails
     */
    public static void write(Path path, Stage228GeneratedCampaignPersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "m22-8-" + prefix;
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
     * Reads a bounded native M22.8 file.
     *
     * @param path native M22.8 checkpoint path
     * @return validated M22.8 checkpoint
     * @throws IOException when bytes cannot be read
     */
    public static Stage228GeneratedCampaignPersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("M22.8 checkpoint file size outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    /**
     * Reads a native M22.8 file or adopts any source supported by the final Stage-21 migration chain.
     *
     * @param path native or supported legacy checkpoint path
     * @return current M22.8 checkpoint
     * @throws IOException when bytes cannot be read
     */
    public static Stage228GeneratedCampaignPersistentState readOrMigrate(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("M22.8 checkpoint file size outside limits");
        }
        return decodeOrMigrate(Files.readAllBytes(source));
    }

    private static int readMagic(byte[] bytes) {
        return ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
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
