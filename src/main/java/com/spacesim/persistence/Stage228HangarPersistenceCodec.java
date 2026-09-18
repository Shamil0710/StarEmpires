package com.spacesim.persistence;

import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic bounded binary codec for M22.8B individual physical hangar occupancy. */
public final class Stage228HangarPersistenceCodec {
    private static final int MAGIC = 0x53384259; // S8BY
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ASSIGNMENTS = 100_000;
    private static final int MAX_STRING_BYTES = 64 * 1024;

    private Stage228HangarPersistenceCodec() {
        throw new AssertionError("utility class");
    }

    /**
     * Encodes exact deterministic hangar occupancy.
     *
     * @param state validated hangar sidecar
     * @return bounded deterministic bytes
     */
    public static byte[] encode(Stage228HangarPersistentState state) {
        Stage228HangarPersistentState checked = Objects.requireNonNull(state, "state");
        if (checked.assignments().size() > MAX_ASSIGNMENTS) {
            throw new IllegalArgumentException("Hangar assignment count exceeds codec bound");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(checked.schemaVersion());
                writeString(out, checked.runtimeVersion());
                writeString(out, checked.semanticContract());
                out.writeInt(checked.assignments().size());
                for (var assignment : checked.assignments()) {
                    out.writeLong(assignment.craftId().value());
                    writeString(out, assignment.hostStableId());
                    writeString(out, assignment.bayStableId());
                    out.writeInt(assignment.hostKind().ordinal());
                    out.writeInt(assignment.occupancyState().ordinal());
                }
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("Hangar sidecar exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unexpected in-memory M22.8B hangar encoding failure", exception);
        }
    }

    /**
     * Decodes one bounded hangar occupancy sidecar and fails closed on malformed data.
     *
     * @param bytes native M22.8B sidecar bytes
     * @return validated persistent hangar state
     */
    public static Stage228HangarPersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Hangar sidecar size outside bounds");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid M22.8B hangar sidecar magic");
            }
            int fileVersion = in.readInt();
            if (fileVersion != FILE_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported M22.8B hangar file version: " + fileVersion);
            }
            int schemaVersion = in.readInt();
            String runtimeVersion = readString(in);
            String semanticContract = readString(in);
            int count = in.readInt();
            if (count < 0 || count > MAX_ASSIGNMENTS) {
                throw new IllegalArgumentException("Hangar assignment count outside bounds");
            }
            ArrayList<Stage228HangarPersistentState.AssignmentState> assignments =
                    new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                SmallCraftId craftId = new SmallCraftId(in.readLong());
                String hostStableId = readString(in);
                String bayStableId = readString(in);
                HostKind hostKind = readEnum(in.readInt(), HostKind.values(), "host kind");
                OccupancyState state = readEnum(
                        in.readInt(), OccupancyState.values(), "occupancy state");
                assignments.add(new Stage228HangarPersistentState.AssignmentState(
                        craftId, hostStableId, bayStableId, hostKind, state));
            }
            if (in.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after M22.8B hangar sidecar");
            }
            return new Stage228HangarPersistentState(
                    schemaVersion,
                    runtimeVersion,
                    semanticContract,
                    List.copyOf(assignments));
        } catch (EOFException exception) {
            throw new IllegalArgumentException("M22.8B hangar sidecar is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("Cannot decode M22.8B hangar sidecar", exception);
        }
    }

    private static <E> E readEnum(int ordinal, E[] values, String label) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + label + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "string").getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0 || bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Hangar string length outside bounds");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Hangar string length outside bounds");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
