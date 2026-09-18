package com.spacesim.persistence;

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

/** Deterministic bounded binary codec for the M22.8A individual small-craft sidecar. */
public final class Stage228SmallCraftPersistenceCodec {
    private static final int MAGIC = 0x53384352; // S8CR
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 256 * 1024 * 1024;
    private static final int MAX_CRAFT = 100_000;
    private static final int MAX_STRING_BYTES = 64 * 1024;
    private static final int MAX_ENGINEERING_BYTES = 4 * 1024 * 1024;

    private Stage228SmallCraftPersistenceCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes an exact M22.8A sidecar deterministically.
     *
     * @param state validated individual-craft sidecar
     * @return bounded deterministic bytes
     */
    public static byte[] encode(Stage228SmallCraftPersistentState state) {
        Stage228SmallCraftPersistentState checked = Objects.requireNonNull(state, "state");
        if (checked.craft().size() > MAX_CRAFT) {
            throw new IllegalArgumentException("Small-craft count exceeds codec bound");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(checked.schemaVersion());
                writeString(out, checked.runtimeVersion());
                writeString(out, checked.semanticContract());
                out.writeLong(checked.nextCraftId());
                out.writeInt(checked.craft().size());
                for (Stage228SmallCraftPersistentState.CraftState craft : checked.craft()) {
                    out.writeLong(craft.id().value());
                    writeString(out, craft.stableFactionId());
                    writeString(out, craft.designId());
                    byte[] engineering = Stage228EngineeringStateBinaryCodec.encode(craft.engineering());
                    if (engineering.length <= 0 || engineering.length > MAX_ENGINEERING_BYTES) {
                        throw new IllegalArgumentException("Engineering payload outside M22.8A bounds");
                    }
                    out.writeInt(engineering.length);
                    out.write(engineering);
                }
            }
            byte[] result = buffer.toByteArray();
            if (result.length <= 0 || result.length > MAX_BYTES) {
                throw new IllegalArgumentException("M22.8A sidecar exceeds bounded size");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory M22.8A sidecar encoding failure", exception);
        }
    }

    /**
     * Decodes and fail-closed validates one native M22.8A sidecar.
     *
     * @param bytes native sidecar bytes
     * @return validated individual-craft sidecar
     */
    public static Stage228SmallCraftPersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("M22.8A sidecar size outside bounds");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid M22.8A sidecar magic");
            }
            int fileVersion = in.readInt();
            if (fileVersion != FILE_VERSION) {
                throw new IllegalArgumentException("Unsupported M22.8A sidecar file version: " + fileVersion);
            }
            int schemaVersion = in.readInt();
            String runtimeVersion = readString(in);
            String semanticContract = readString(in);
            long nextCraftId = in.readLong();
            int count = in.readInt();
            if (count < 0 || count > MAX_CRAFT) {
                throw new IllegalArgumentException("Small-craft count outside codec bounds");
            }
            ArrayList<Stage228SmallCraftPersistentState.CraftState> craft = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                SmallCraftId id = new SmallCraftId(in.readLong());
                String factionId = readString(in);
                String designId = readString(in);
                int engineeringLength = in.readInt();
                if (engineeringLength <= 0 || engineeringLength > MAX_ENGINEERING_BYTES) {
                    throw new IllegalArgumentException("Engineering payload size outside bounds");
                }
                byte[] engineering = new byte[engineeringLength];
                in.readFully(engineering);
                craft.add(new Stage228SmallCraftPersistentState.CraftState(
                        id,
                        factionId,
                        designId,
                        Stage228EngineeringStateBinaryCodec.decode(engineering)));
            }
            if (in.read() != -1) {
                throw new IllegalArgumentException("Trailing bytes after M22.8A sidecar");
            }
            return new Stage228SmallCraftPersistentState(
                    schemaVersion,
                    runtimeVersion,
                    semanticContract,
                    nextCraftId,
                    List.copyOf(craft));
        } catch (EOFException exception) {
            throw new IllegalArgumentException("M22.8A sidecar is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("Cannot decode M22.8A sidecar", exception);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "string").getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0 || bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("M22.8A string length outside bounds");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("M22.8A string length outside bounds");
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
