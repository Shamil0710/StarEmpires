package com.spacesim.persistence;

import com.spacesim.world.FleetId;
import com.spacesim.world.Stage23FleetCrewManifest;
import java.io.*;
import java.util.ArrayList;
import java.util.Objects;

/** Bounded exact crew commissioning observation payload; decoding never commissions a fleet. */
public final class Stage23FleetCrewManifestCodec {
    /** Roster byte admission limit. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAGIC = 0x53324352;
    private Stage23FleetCrewManifestCodec() { }

    /**
     * @param state actual declared observations
     * @return exact bounded payload
     */
    public static byte[] encode(Stage23FleetCrewManifest state) {
        Objects.requireNonNull(state);
        try {
            var buffer = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC); out.writeInt(1); out.writeUTF(state.profileVersion()); out.writeInt(state.entries().size());
                for (var entry : state.entries()) { out.writeLong(entry.fleetId().value()); out.writeInt(entry.availableCrew()); }
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Crew roster payload exceeds bounds");
            return bytes;
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * @param bytes bounded saved observations
     * @return exact saved roster, without allocating personnel
     */
    public static Stage23FleetCrewManifest decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Crew roster payload outside bounds");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readInt() != 1) throw new IllegalArgumentException("Unknown crew roster framing");
            String profile = in.readUTF(); int count = in.readInt();
            if (count < 0 || count > Stage23FleetCrewManifest.MAX_ENTRIES || count > in.available() / 12)
                throw new IllegalArgumentException("Crew roster count outside available payload");
            var entries = new ArrayList<Stage23FleetCrewManifest.Entry>(count);
            for (int i = 0; i < count; i++) entries.add(new Stage23FleetCrewManifest.Entry(new FleetId(in.readLong()), in.readInt()));
            if (in.read() != -1) throw new IllegalArgumentException("Trailing crew roster bytes");
            return new Stage23FleetCrewManifest(profile, entries);
        } catch (IOException error) { throw new IllegalArgumentException("Malformed crew roster payload", error); }
    }
}
