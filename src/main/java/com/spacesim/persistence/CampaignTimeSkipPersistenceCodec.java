package com.spacesim.persistence;

import com.spacesim.simulation.CampaignTimeSkipState;
import java.io.*;
import java.util.TreeMap;

/** Bounded schema-1 control request; it carries no assets or elapsed-time grants. */
public final class CampaignTimeSkipPersistenceCodec {
    /** Maximum standalone control payload. */
    public static final int MAX_BYTES = 1024 * 1024;
    private CampaignTimeSkipPersistenceCodec() { }
    /**
     * Encodes an exact outstanding request or canonical idle state.
     * @param state timing control state
     * @return deterministic bytes
     */
    public static byte[] encode(CampaignTimeSkipState state) {
        try {
            var buffer = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(buffer)) {
                out.writeInt(1); out.writeByte(state.mode().ordinal()); out.writeLong(state.startedTick());
                out.writeLong(state.targetTick()); out.writeInt(state.requestedMinutes()); out.writeLong(state.journalSequence());
                out.writeLong(state.fleetId()); out.writeUTF(state.jumpPhase()); out.writeDouble(state.resumeScale());
                out.writeDouble(state.hullIntegrity()); out.writeInt(state.threatSignature()); out.writeInt(state.moduleIntegrity().size());
                for (var e : state.moduleIntegrity().entrySet()) { out.writeUTF(e.getKey()); out.writeDouble(e.getValue()); }
            }
            if (buffer.size() > MAX_BYTES) throw new IllegalArgumentException("Time skip payload exceeds bounds");
            return buffer.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("Cannot encode time skip", failure); }
    }
    /**
     * Rejects malformed, excessive or trailing control data without advancing any clock.
     * @param bytes bounded request
     * @return exact control state
     */
    public static CampaignTimeSkipState decode(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Time skip payload exceeds bounds");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IllegalArgumentException("Unknown time skip schema");
            int mode = in.readUnsignedByte();
            if (mode >= CampaignTimeSkipState.Mode.values().length) throw new IllegalArgumentException("Unknown time skip mode");
            long started = in.readLong(), target = in.readLong(); int minutes = in.readInt();
            long journal = in.readLong(), fleet = in.readLong(); String phase = in.readUTF();
            double scale = in.readDouble(), hull = in.readDouble(); int threat = in.readInt(), count = in.readInt();
            if (count < 0 || count > CampaignTimeSkipState.MAX_MOUNTS || count > in.available() / 11)
                throw new IllegalArgumentException("Time skip mount count exceeds bounds");
            var mounts = new TreeMap<String, Double>();
            for (int i = 0; i < count; i++) if (mounts.putIfAbsent(in.readUTF(), in.readDouble()) != null)
                throw new IllegalArgumentException("Duplicate personal mount condition");
            if (in.read() != -1) throw new IllegalArgumentException("Trailing time skip payload");
            return new CampaignTimeSkipState(CampaignTimeSkipState.Mode.values()[mode], started, target, minutes, journal,
                    fleet, phase, scale, hull, mounts, threat);
        } catch (IOException failure) { throw new IllegalArgumentException("Truncated time skip payload", failure); }
    }
}
