package com.spacesim.world;

import com.spacesim.world.TerritorialTransitionState.OccupationState;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

/** Deterministic bounded binary codec for persistent Stage-21F occupation-transition metadata. */
public final class TerritorialTransitionStateCodec {
    private static final int MAGIC = 0x54463231; // TF21
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_OCCUPATIONS = 65_536;
    private static final int MAX_TEXT_BYTES = 16_384;

    private TerritorialTransitionStateCodec() {
        throw new AssertionError("No instances");
    }

    /** Encodes canonical Stage-21F transition metadata deterministically. */
    public static byte[] encode(TerritorialTransitionState state) {
        TerritorialTransitionState checked = Objects.requireNonNull(state, "state");
        if (checked.occupations().size() > MAX_OCCUPATIONS) {
            throw new IllegalArgumentException("too many territorial occupation transitions");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(checked.occupations().size());
                for (OccupationState occupation : checked.occupations()) {
                    writeText(out, occupation.factionContentId());
                    out.writeLong(occupation.systemId().value());
                    out.writeLong(occupation.operationId());
                    out.writeLong(occupation.startedTick());
                    out.writeLong(occupation.lastEvaluatedTick());
                    out.writeLong(occupation.securedTicks());
                    out.writeLong(occupation.unsupportedSinceTick());
                    out.writeBoolean(occupation.controlEverEstablished());
                    writeText(out, occupation.status().name());
                }
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("territorial transition payload outside bounds");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("unexpected in-memory territorial transition encoding failure", exception);
        }
    }

    /** Decodes canonical Stage-21F state and fails closed on corrupt, future or trailing data. */
    public static TerritorialTransitionState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("territorial transition payload outside bounds");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("invalid territorial transition payload magic");
            int version = in.readInt();
            if (version != VERSION) {
                throw new IllegalArgumentException("unsupported territorial transition payload version: " + version);
            }
            int count = boundedCount(in.readInt(), MAX_OCCUPATIONS, "occupation count");
            ArrayList<OccupationState> occupations = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                occupations.add(new OccupationState(
                        readText(in),
                        new StarSystemId(in.readLong()),
                        in.readLong(),
                        in.readLong(),
                        in.readLong(),
                        in.readLong(),
                        in.readLong(),
                        in.readBoolean(),
                        enumValue(OccupationStatus.class, readText(in), "occupation status")));
            }
            if (in.read() != -1) throw new IllegalArgumentException("trailing bytes after territorial transition payload");
            return new TerritorialTransitionState(occupations);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("territorial transition payload is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("cannot decode territorial transition payload", exception);
        }
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] utf8 = Objects.requireNonNull(value, "text").getBytes(StandardCharsets.UTF_8);
        if (utf8.length > MAX_TEXT_BYTES) throw new IllegalArgumentException("text exceeds territorial transition codec bound");
        out.writeInt(utf8.length);
        out.write(utf8);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = boundedCount(in.readInt(), MAX_TEXT_BYTES, "text length");
        byte[] utf8 = in.readNBytes(length);
        if (utf8.length != length) throw new EOFException("territorial transition text is truncated");
        return new String(utf8, StandardCharsets.UTF_8);
    }

    private static int boundedCount(int value, int maximum, String label) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException(label + " outside bounds");
        return value;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown " + label + ": " + value, exception);
        }
    }
}
