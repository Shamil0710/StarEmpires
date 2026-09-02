package com.spacesim.persistence;

import com.spacesim.content.Stage22IndustrialUnionProductionState;
import com.spacesim.content.Stage22IndustrialUnionProductionState.YardSeriesState;

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

/** Deterministic fail-closed binary codec for M22.4 Union commonality/retool state. */
public final class Stage22IndustrialUnionProductionStateCodec {
    private static final int MAGIC = 0x55323234; // U224
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 128 * 1024;
    private static final int MAX_TEXT_BYTES = 1024;
    private static final int MAX_YARDS = 128;

    private Stage22IndustrialUnionProductionStateCodec() {
        throw new AssertionError("utility class");
    }

    /**
     * Encodes current M22.4 series qualification state deterministically.
     *
     * @param state validated current production-side state
     * @return bounded byte-stable representation
     */
    public static byte[] encode(Stage22IndustrialUnionProductionState state) {
        Stage22IndustrialUnionProductionState checked = Objects.requireNonNull(state, "state");
        requireYardCount(checked.yards().size());
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_VERSION);
                output.writeInt(checked.envelopeVersion());
                writeText(output, checked.stableFactionId());
                writeText(output, checked.packageFingerprint());
                output.writeLong(checked.sequence());
                output.writeInt(checked.yards().size());
                for (YardSeriesState yard : checked.yards()) {
                    writeText(output, yard.yardId());
                    writeText(output, yard.activeSeriesId());
                    writeText(output, yard.pendingSeriesId());
                    output.writeInt(yard.completedUnitsInSeries());
                    output.writeInt(yard.commonalityStreak());
                    output.writeLong(yard.retoolWorkRemainingSeconds());
                    output.writeLong(yard.retoolEnergyRemainingJ());
                }
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Industrial Union production state exceeds persistence limit");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Industrial Union state encoding error", exception);
        }
    }

    /**
     * Decodes one complete state and rejects corrupt, stale/future or trailing data.
     *
     * @param bytes complete state bytes
     * @return validated current state
     */
    public static Stage22IndustrialUnionProductionState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Industrial Union production-state size is outside limits");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid Industrial Union production-state magic");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_VERSION) {
                throw new IllegalArgumentException("Unsupported Industrial Union production-state file version: " + fileVersion);
            }
            int envelope = input.readInt();
            if (envelope != Stage22IndustrialUnionProductionState.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported Industrial Union production-state envelope: " + envelope);
            }
            String stableFactionId = readText(input, "stableFactionId");
            String fingerprint = readText(input, "packageFingerprint");
            long sequence = input.readLong();
            int count = input.readInt();
            requireYardCount(count);
            List<YardSeriesState> yards = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                yards.add(new YardSeriesState(
                        readText(input, "yardId"),
                        readText(input, "activeSeriesId"),
                        readText(input, "pendingSeriesId"),
                        input.readInt(),
                        input.readInt(),
                        input.readLong(),
                        input.readLong()));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Unexpected trailing bytes after Industrial Union production state");
            }
            return new Stage22IndustrialUnionProductionState(
                    envelope, stableFactionId, fingerprint, sequence, yards);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Industrial Union production state is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Industrial Union production state cannot be decoded", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("Industrial Union production state contains invalid values", exception);
        }
    }

    /**
     * Atomically writes one bounded current-state sidecar where the filesystem supports it.
     *
     * @param path target persistence path
     * @param state validated production-side state
     * @throws IOException when the sidecar cannot be written
     */
    public static void write(Path path, Stage22IndustrialUnionProductionState state) throws IOException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath();
        byte[] bytes = encode(Objects.requireNonNull(state, "state"));
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "union-production-" + prefix;
        }
        Path temp = Files.createTempFile(parent, prefix, ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Reads one complete bounded sidecar and fails closed on incompatible/corrupt content.
     *
     * @param path existing sidecar path
     * @return decoded current production-side state
     * @throws IOException when the file cannot be read
     */
    public static Stage22IndustrialUnionProductionState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "path").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_BYTES) {
            throw new IllegalArgumentException("Industrial Union production-state file size is outside limits");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void requireYardCount(int count) {
        if (count <= 0 || count > MAX_YARDS) {
            throw new IllegalArgumentException("Industrial Union yard-state count is outside limits");
        }
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "text").getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0 || bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Industrial Union state text length is outside limits");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, String field) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(field + " byte length is outside limits");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Truncated " + field);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
