package com.spacesim.persistence;

import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.GenerationIdentity;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.MaterializedWorldSnapshot;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.OpenRuntimeBoundary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic bounded binary codec for the complete Stage-20K generated-campaign envelope. */
@SuppressWarnings("doclint:missing")
public final class Stage20GeneratedCampaignPersistenceCodec {
    private static final int MAGIC = 0x5332304B; // S20K
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_BYTES = 192 * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 1024 * 1024;
    private static final int MAX_ROWS = 1_000_000;
    private static final int MAX_VALUES_PER_ROW = 100_000;
    private static final int MAX_EMBEDDED_PAYLOAD_BYTES = 64 * 1024 * 1024;

    private Stage20GeneratedCampaignPersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes a complete current Stage-20K campaign deterministically.
     *
     * @param state validated campaign state
     * @return new deterministic binary bytes
     */
    public static byte[] encode(Stage20GeneratedCampaignPersistentState state) {
        Stage20GeneratedCampaignPersistentState checked = Objects.requireNonNull(state, "state");
        byte[] materialization = Stage20MaterializationPersistenceCodec.encode(
                checked.materializationState());
        byte[] industrial = Stage18IndustrialStateCodec.encode(checked.industrialState());
        byte[] discovery = Stage20DiscoveryPersistenceCodec.encode(checked.discoveryState());
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.schemaVersion());
                writeIdentity(output, checked.generationIdentity());
                writeSnapshot(output, checked.materializedWorld());
                writePayload(output, materialization, "materialization");
                writePayload(output, industrial, "industrial");
                writePayload(output, discovery, "discovery");
                requireCount("open runtime boundaries", checked.openRuntimeBoundaries().size(), 32);
                output.writeInt(checked.openRuntimeBoundaries().size());
                for (OpenRuntimeBoundary boundary : checked.openRuntimeBoundaries()) {
                    writeText(output, boundary.name(), false);
                }
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Stage-20K campaign exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-20K encoding error", exception);
        }
    }

    /**
     * Decodes and cross-validates one complete current Stage-20K campaign.
     *
     * @param bytes complete encoded envelope
     * @return immutable decoded campaign state
     */
    public static Stage20GeneratedCampaignPersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-20K campaign size is outside bounded range");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Stage-20K campaign magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-20K file version: " + fileVersion);
            }
            int schemaVersion = input.readInt();
            if (schemaVersion != Stage20GeneratedCampaignPersistentState.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported Stage-20K campaign schema: " + schemaVersion);
            }
            GenerationIdentity identity = readIdentity(input);
            MaterializedWorldSnapshot snapshot = readSnapshot(input);
            Stage20MaterializationPersistentState materialization = Stage20MaterializationPersistenceCodec.decode(
                    readPayload(input, "materialization"));
            Stage18IndustrialState industrial = Stage18IndustrialStateCodec.decodeAgainstFingerprint(
                    readPayload(input, "industrial"), identity.contentFingerprint());
            Stage20DiscoveryPersistentState discovery = Stage20DiscoveryPersistenceCodec.decode(
                    readPayload(input, "discovery"));
            int boundaryCount = readCount(input, "open runtime boundaries", 32);
            ArrayList<OpenRuntimeBoundary> boundaries = new ArrayList<>(boundaryCount);
            for (int index = 0; index < boundaryCount; index++) {
                boundaries.add(readEnum(input, OpenRuntimeBoundary.class, "open runtime boundary"));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Unexpected trailing bytes after Stage-20K campaign");
            }
            return new Stage20GeneratedCampaignPersistentState(
                    schemaVersion,
                    identity,
                    snapshot,
                    materialization,
                    industrial,
                    discovery,
                    boundaries);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-20K campaign is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Stage-20K campaign cannot be decoded", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("Stage-20K campaign contains invalid values", exception);
        }
    }

    /**
     * Atomically writes one Stage-20K campaign where supported by the filesystem.
     *
     * @param path target campaign file
     * @param state complete validated campaign state
     * @throws IOException when the file cannot be written or replaced
     */
    public static void write(Path path, Stage20GeneratedCampaignPersistentState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "stage20k-" + prefix;
        }
        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Reads one bounded Stage-20K campaign file.
     *
     * @param path existing campaign file
     * @return decoded and cross-validated campaign state
     * @throws IOException when the file cannot be read
     */
    public static Stage20GeneratedCampaignPersistentState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Stage-20K campaign file size is outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writeIdentity(DataOutputStream output, GenerationIdentity identity) throws IOException {
        output.writeLong(identity.worldSeed());
        writeText(output, identity.generatorVersion(), false);
        writeText(output, identity.sourceGeneratorVersion(), false);
        writeText(output, identity.generationProfile(), false);
        writeText(output, identity.contentFingerprint(), false);
    }

    private static GenerationIdentity readIdentity(DataInputStream input) throws IOException {
        return new GenerationIdentity(
                input.readLong(),
                readText(input, "generatorVersion", false),
                readText(input, "sourceGeneratorVersion", false),
                readText(input, "generationProfile", false),
                readText(input, "contentFingerprint", false));
    }

    private static void writeSnapshot(DataOutputStream output, MaterializedWorldSnapshot snapshot)
            throws IOException {
        writeText(output, snapshot.snapshotVersion(), false);
        writeRows(output, snapshot.worldRows(), "world rows");
        writeRows(output, snapshot.qualityRows(), "quality rows");
        writeText(output, snapshot.worldFingerprint(), false);
        writeText(output, snapshot.qualityFingerprint(), false);
    }

    private static MaterializedWorldSnapshot readSnapshot(DataInputStream input) throws IOException {
        String version = readText(input, "snapshotVersion", false);
        List<CanonicalRow> worldRows = readRows(input, "world rows");
        List<CanonicalRow> qualityRows = readRows(input, "quality rows");
        String worldFingerprint = readText(input, "worldFingerprint", false);
        String qualityFingerprint = readText(input, "qualityFingerprint", false);
        return new MaterializedWorldSnapshot(
                version, worldRows, qualityRows, worldFingerprint, qualityFingerprint);
    }

    private static void writeRows(DataOutputStream output, List<CanonicalRow> rows, String field)
            throws IOException {
        requireCount(field, rows.size(), MAX_ROWS);
        output.writeInt(rows.size());
        for (CanonicalRow row : rows) {
            writeText(output, row.domain(), false);
            writeText(output, row.stableId(), false);
            requireCount(field + " values", row.values().size(), MAX_VALUES_PER_ROW);
            output.writeInt(row.values().size());
            for (String value : row.values()) {
                writeText(output, value, true);
            }
        }
    }

    private static List<CanonicalRow> readRows(DataInputStream input, String field) throws IOException {
        int rowCount = readCount(input, field, MAX_ROWS);
        ArrayList<CanonicalRow> rows = new ArrayList<>(rowCount);
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            String domain = readText(input, field + " domain", false);
            String stableId = readText(input, field + " stableId", false);
            int valueCount = readCount(input, field + " values", MAX_VALUES_PER_ROW);
            ArrayList<String> values = new ArrayList<>(valueCount);
            for (int valueIndex = 0; valueIndex < valueCount; valueIndex++) {
                values.add(readText(input, field + " value", true));
            }
            rows.add(new CanonicalRow(domain, stableId, values));
        }
        return List.copyOf(rows);
    }

    private static void writePayload(DataOutputStream output, byte[] payload, String field) throws IOException {
        if (payload.length <= 0 || payload.length > MAX_EMBEDDED_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(field + " payload size is outside limits");
        }
        output.writeInt(payload.length);
        output.write(payload);
    }

    private static byte[] readPayload(DataInputStream input, String field) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_EMBEDDED_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(field + " payload length is outside limits");
        }
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) {
            throw new EOFException(field + " payload is truncated");
        }
        return payload;
    }

    private static void writeText(DataOutputStream output, String value, boolean allowEmpty) throws IOException {
        Objects.requireNonNull(value, "persisted text");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if ((!allowEmpty && bytes.length == 0) || bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("persisted text is empty or exceeds size limit");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, String field, boolean allowEmpty) throws IOException {
        int length = input.readInt();
        if (length < 0 || (!allowEmpty && length == 0) || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(field + " length is outside limits");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException(field + " is truncated");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readCount(DataInputStream input, String field, int maximum) throws IOException {
        int count = input.readInt();
        requireCount(field, count, maximum);
        return count;
    }

    private static void requireCount(String field, int count, int maximum) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(field + " count is outside limits");
        }
    }

    private static <T extends Enum<T>> T readEnum(
            DataInputStream input, Class<T> type, String field) throws IOException {
        String name = readText(input, field, false);
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + field + ": " + name, exception);
        }
    }
}
