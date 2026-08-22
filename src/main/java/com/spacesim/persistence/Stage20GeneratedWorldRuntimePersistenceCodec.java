package com.spacesim.persistence;

import com.spacesim.world.StarSystemId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Deterministic bounded codec for one atomic Stage-20.5 generated-world runtime checkpoint. */
@SuppressWarnings("doclint:missing")
public final class Stage20GeneratedWorldRuntimePersistenceCodec {
    private static final int MAGIC = 0x53323552; // S25R
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 512 * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 1024 * 1024;
    private static final int MAX_PAYLOAD_BYTES = 256 * 1024 * 1024;

    private Stage20GeneratedWorldRuntimePersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes the generated campaign, ordinary world and freight state as one deterministic payload.
     *
     * @param state complete validated runtime checkpoint
     * @return new binary payload
     */
    public static byte[] encode(Stage20GeneratedWorldRuntimePersistentState state) {
        Stage20GeneratedWorldRuntimePersistentState checked = Objects.requireNonNull(state, "state");
        byte[] campaign = Stage20GeneratedCampaignPersistenceCodec.encode(checked.campaign());
        byte[] world = WorldStateCodec.encode(checked.worldState());
        byte[] freight = Stage20FreightPersistenceCodec.encode(checked.freight());
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.schemaVersion());
                writeText(output, checked.bridgeVersion());
                output.writeLong(checked.activeSystemId().value());
                writePayload(output, campaign, "campaign");
                writePayload(output, world, "world");
                writePayload(output, freight, "freight");
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-20.5 runtime checkpoint exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory runtime checkpoint encoding failure", exception);
        }
    }

    /**
     * Decodes and cross-validates one atomic Stage-20.5 generated-world runtime checkpoint.
     *
     * @param bytes encoded checkpoint
     * @return immutable validated checkpoint
     */
    public static Stage20GeneratedWorldRuntimePersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-20.5 runtime checkpoint size is outside bounded range");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-20.5 runtime checkpoint magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported Stage-20.5 runtime file version: " + fileVersion);
            }
            int schemaVersion = input.readInt();
            String bridgeVersion = readText(input, "bridgeVersion");
            StarSystemId activeSystemId = new StarSystemId(input.readLong());
            Stage20GeneratedCampaignPersistentState campaign =
                    Stage20GeneratedCampaignPersistenceCodec.decode(readPayload(input, "campaign"));
            var world = WorldStateCodec.decode(readPayload(input, "world"));
            Stage20FreightPersistentState freight =
                    Stage20FreightPersistenceCodec.decode(readPayload(input, "freight"));
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "Trailing bytes after Stage-20.5 runtime checkpoint");
            }
            return new Stage20GeneratedWorldRuntimePersistentState(
                    schemaVersion,
                    bridgeVersion,
                    campaign,
                    world,
                    activeSystemId,
                    freight);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-20.5 runtime checkpoint is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot decode Stage-20.5 runtime checkpoint", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid Stage-20.5 runtime checkpoint", exception);
        }
    }

    private static void writePayload(DataOutputStream output, byte[] payload, String label)
            throws IOException {
        if (payload.length <= 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(label + " payload size is outside bounded range");
        }
        output.writeInt(payload.length);
        output.write(payload);
    }

    private static byte[] readPayload(DataInputStream input, String label) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(label + " payload size is outside bounded range");
        }
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) {
            throw new EOFException(label + " payload is truncated");
        }
        return payload;
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "text").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("checkpoint text exceeds bounded size");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, String label) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(label + " text size is outside bounded range");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException(label + " text is truncated");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
