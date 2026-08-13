package com.spacesim.persistence;

import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemSimulationState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class WorldSystemBinary {
    private static final int MAX_SYSTEMS = 100_000;
    static final int MAX_GAMESTATE_PAYLOAD_BYTES = 32 * 1024 * 1024;

    private WorldSystemBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<StarSystemSimulationState> systems) throws IOException {
        WorldIoSupport.writeCount(out, systems.size(), MAX_SYSTEMS, "systemStates");
        for (StarSystemSimulationState system : systems) {
            out.writeLong(system.systemId().value());
            byte[] payload = GameStateCodec.encode(system.simulationState());
            if (payload.length <= 0 || payload.length > MAX_GAMESTATE_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("GameState payload exceeds limit");
            }
            out.writeInt(payload.length);
            out.write(payload);
        }
    }

    static List<StarSystemSimulationState> read(DataInputStream in) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_SYSTEMS, "systemStates");
        List<StarSystemSimulationState> systems = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            StarSystemId systemId = new StarSystemId(in.readLong());
            int payloadLength = in.readInt();
            if (payloadLength <= 0 || payloadLength > MAX_GAMESTATE_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Invalid GameState payload length");
            }
            byte[] payload = in.readNBytes(payloadLength);
            if (payload.length != payloadLength) {
                throw new EOFException("GameState payload truncated");
            }
            systems.add(new StarSystemSimulationState(systemId, GameStateCodec.decode(payload)));
        }
        return List.copyOf(systems);
    }
}
