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
 * Deterministic bounded file codec for the evolving M22.8 campaign envelope.
 *
 * <p>Version 2 embeds the accepted Stage-21I checkpoint bytes unchanged plus independently versioned
 * small-craft and physical-hangar sidecars. Native M22.8A version-1 files migrate by preserving every
 * individual craft and adding an empty hangar sidecar. Supported Stage-20.5/21A-I files still migrate
 * through the accepted Stage-21I chain and receive empty non-granting M22.8 sidecars.</p>
 */
public final class Stage228GeneratedCampaignPersistenceCodec {
    private static final int MAGIC = 0x53323843; // S28C
    private static final int FILE_VERSION = 2;
    private static final int M22_8A_FILE_VERSION = 1;
    private static final int M22_8A_SCHEMA_VERSION = 1;
    private static final String M22_8A_RUNTIME_VERSION = "m22.8.generated-campaign.v1";
    private static final int MAX_BYTES = 1_900 * 1024 * 1024;
    private static final int MAX_STAGE21_PAYLOAD_BYTES = 1_500 * 1024 * 1024;
    private static final int MAX_SMALL_CRAFT_PAYLOAD_BYTES = 256 * 1024 * 1024;
    private static final int MAX_HANGAR_PAYLOAD_BYTES = 64 * 1024 * 1024;

    private Stage228GeneratedCampaignPersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes one complete current M22.8 campaign checkpoint.
     *
     * @param state complete current M22.8 campaign envelope
     * @return deterministic bounded bytes
     */
    public static byte[] encode(Stage228GeneratedCampaignPersistentState state) {
        Stage228GeneratedCampaignPersistentState checked = Objects.requireNonNull(state, "state");
        byte[] stage21 = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(checked.stage21Runtime());
        byte[] smallCraft = Stage228SmallCraftPersistenceCodec.encode(checked.smallCraft());
        byte[] hangars = Stage228HangarPersistenceCodec.encode(checked.hangars());
        requirePayload(stage21, MAX_STAGE21_PAYLOAD_BYTES, "Stage-21I runtime");
        requirePayload(smallCraft, MAX_SMALL_CRAFT_PAYLOAD_BYTES, "M22.8A small craft");
        requirePayload(hangars, MAX_HANGAR_PAYLOAD_BYTES, "M22.8B hangars");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(checked.schemaVersion());
                out.writeUTF(checked.runtimeVersion());
                writePayload(out, stage21);
                writePayload(out, smallCraft);
                writePayload(out, hangars);
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
     * Decodes a native M22.8A/B campaign checkpoint and migrates A to the current B envelope.
     *
     * @param bytes native M22.8 checkpoint bytes
     * @return validated current M22.8 campaign envelope
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
            return switch (fileVersion) {
                case M22_8A_FILE_VERSION -> decodeM22_8A(in);
                case FILE_VERSION -> decodeCurrent(in);
                default -> throw new IllegalArgumentException(
                        "Unsupported M22.8 file version: " + fileVersion);
            };
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
     * Decodes native M22.8 or adopts any source supported by the final Stage-21 migration chain.
     *
     * @param bytes native M22.8 or supported Stage-20.5/21A-I bytes
     * @return current M22.8 envelope; older sources never gain craft or occupancy
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
     * @param state complete current M22.8 campaign envelope
     * @throws IOException when persistence fails
     */
    public static void write(Path path, Stage228GeneratedCampaignPersistentState state)
            throws IOException {
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
     * Reads a bounded native M22.8A/B file into the current envelope.
     *
     * @param path native M22.8 checkpoint path
     * @return validated current M22.8 checkpoint
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
     * Reads native M22.8 or adopts any source supported by the final Stage-21 migration chain.
     *
     * @param path native or supported legacy checkpoint path
     * @return current M22.8 checkpoint
     * @throws IOException when bytes cannot be read
     */
    public static Stage228GeneratedCampaignPersistentState readOrMigrate(Path path)
            throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("M22.8 checkpoint file size outside limits");
        }
        return decodeOrMigrate(Files.readAllBytes(source));
    }

    private static Stage228GeneratedCampaignPersistentState decodeM22_8A(DataInputStream in)
            throws IOException {
        int schemaVersion = in.readInt();
        String runtimeVersion = in.readUTF();
        if (schemaVersion != M22_8A_SCHEMA_VERSION
                || !M22_8A_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported native M22.8A envelope identity: "
                            + schemaVersion + "/" + runtimeVersion);
        }
        Stage21IGeneratedWorldRuntimePersistentState stage21 =
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(
                        readPayload(in, MAX_STAGE21_PAYLOAD_BYTES, "Stage-21I runtime"));
        Stage228SmallCraftPersistentState smallCraft = Stage228SmallCraftPersistenceCodec.decode(
                readPayload(in, MAX_SMALL_CRAFT_PAYLOAD_BYTES, "M22.8A small craft"));
        requireEnd(in);
        return Stage228GeneratedCampaignPersistentState.adoptM22_8A(stage21, smallCraft);
    }

    private static Stage228GeneratedCampaignPersistentState decodeCurrent(DataInputStream in)
            throws IOException {
        int schemaVersion = in.readInt();
        String runtimeVersion = in.readUTF();
        if (schemaVersion != Stage228GeneratedCampaignPersistentState.CURRENT_VERSION
                || !Stage228GeneratedCampaignPersistentState.CURRENT_RUNTIME_VERSION.equals(
                        runtimeVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported current M22.8 envelope identity: "
                            + schemaVersion + "/" + runtimeVersion);
        }
        Stage21IGeneratedWorldRuntimePersistentState stage21 =
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(
                        readPayload(in, MAX_STAGE21_PAYLOAD_BYTES, "Stage-21I runtime"));
        Stage228SmallCraftPersistentState smallCraft = Stage228SmallCraftPersistenceCodec.decode(
                readPayload(in, MAX_SMALL_CRAFT_PAYLOAD_BYTES, "M22.8A small craft"));
        Stage228HangarPersistentState hangars = Stage228HangarPersistenceCodec.decode(
                readPayload(in, MAX_HANGAR_PAYLOAD_BYTES, "M22.8B hangars"));
        requireEnd(in);
        return Stage228GeneratedCampaignPersistentState.compose(stage21, smallCraft, hangars);
    }

    private static void requireEnd(DataInputStream in) throws IOException {
        if (in.read() != -1) {
            throw new IllegalArgumentException("Trailing bytes after M22.8 checkpoint");
        }
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

    private static byte[] readPayload(DataInputStream in, int maximum, String label)
            throws IOException {
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
