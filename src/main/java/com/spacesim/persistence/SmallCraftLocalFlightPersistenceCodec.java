package com.spacesim.persistence;

import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftLocalFlightState;
import com.spacesim.world.StarSystemId;
import java.io.*;
import java.util.ArrayList;
import java.util.Objects;

/** Bounded lossless local-flight sidecar; decoding never materializes a craft or advances time. */
public final class SmallCraftLocalFlightPersistenceCodec {
    private static final int MAGIC = 0x53324346;
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private SmallCraftLocalFlightPersistenceCodec() { }

    /**
     * @param state exact physical state
     * @return independently versioned bounded payload
     */
    public static byte[] encode(SmallCraftLocalFlightState state) {
        Objects.requireNonNull(state);
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(state.lastProcessedTick());
                out.writeInt(state.flights().size());
                for (var flight : state.flights()) {
                    out.writeLong(flight.craftId().value()); out.writeLong(flight.systemId().value());
                    if (flight.hostStableId().length() > 512) throw new IllegalArgumentException("Physical host identity too long");
                    out.writeUTF(flight.hostStableId()); out.writeLong(flight.missionId());
                    var p = flight.physical().position();
                    out.writeLong(p.cellX()); out.writeLong(p.cellY()); out.writeDouble(p.offsetXM()); out.writeDouble(p.offsetYM());
                    out.writeDouble(flight.physical().velocityXMps()); out.writeDouble(flight.physical().velocityYMps());
                    out.writeLong(flight.deployedTick());
                    if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Local flight payload too large");
                }
            }
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Local flight payload too large");
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalArgumentException("Cannot encode local flight", failure); }
    }

    /**
     * @param bytes exact bounded sidecar
     * @return validated physical values without live mutation
     */
    public static SmallCraftLocalFlightState decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Local flight payload too large");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IllegalArgumentException("Unknown local flight framing");
            long watermark = in.readLong(); int count = in.readInt();
            if (count < 0 || count > SmallCraftLocalFlightState.MAX_FLIGHTS) throw new IllegalArgumentException("Invalid local flight row count");
            if ((long) count * 83 > in.available()) throw new IllegalArgumentException("Truncated local flight rows");
            var flights = new ArrayList<SmallCraftLocalFlightState.Flight>(count);
            for (int i = 0; i < count; i++) {
                var craft = new SmallCraftId(in.readLong()); var system = new StarSystemId(in.readLong());
                String host = in.readUTF();
                if (host.length() > 512) throw new IllegalArgumentException("Physical host identity too long");
                long mission = in.readLong();
                var position = new LocalPhysicalPosition(in.readLong(), in.readLong(), in.readDouble(), in.readDouble());
                var physical = new LocalPhysicalKinematics(position, in.readDouble(), in.readDouble());
                flights.add(new SmallCraftLocalFlightState.Flight(craft, system, host, mission, physical, in.readLong()));
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing local flight data");
            return new SmallCraftLocalFlightState(watermark, flights);
        } catch (IOException failure) { throw new IllegalArgumentException("Truncated local flight payload", failure); }
    }
}
