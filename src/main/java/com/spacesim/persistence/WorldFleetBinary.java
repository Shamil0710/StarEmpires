package com.spacesim.persistence;

import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.FleetJumpState;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetTransitState;
import com.spacesim.world.StarSystemId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class WorldFleetBinary {
    private static final int MAX_FLEETS = 1_000_000;

    private WorldFleetBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<FleetPlacementState> fleets) throws IOException {
        WorldIoSupport.writeCount(out, fleets.size(), MAX_FLEETS, "fleetPlacements");
        for (FleetPlacementState fleet : fleets) {
            out.writeLong(fleet.id().value());
            WorldIoSupport.writeString(out, fleet.locationKind().name());
            if (fleet.locationKind() == FleetLocationKind.IN_SYSTEM) {
                out.writeLong(fleet.systemId().value());
                out.writeLong(fleet.localEntityId().value());
                continue;
            }

            FleetTransitState transit = fleet.transitState();
            out.writeLong(transit.originSystemId().value());
            out.writeLong(transit.destinationSystemId().value());
            byte[] payload = FleetPayloadCodec.encode(transit.entityState());
            if (payload.length <= 0 || payload.length > WorldSystemBinary.MAX_GAMESTATE_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Fleet payload exceeds limit");
            }
            out.writeInt(payload.length);
            out.write(payload);
        }
    }

    static List<FleetPlacementState> read(DataInputStream in) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_FLEETS, "fleetPlacements");
        List<FleetPlacementState> fleets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            FleetId id = new FleetId(in.readLong());
            FleetLocationKind kind;
            try {
                kind = FleetLocationKind.valueOf(WorldIoSupport.readString(in));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown FleetLocationKind", exception);
            }

            if (kind == FleetLocationKind.IN_SYSTEM) {
                fleets.add(new FleetPlacementState(
                        id,
                        kind,
                        new StarSystemId(in.readLong()),
                        new EntityId(in.readLong()),
                        null));
                continue;
            }

            StarSystemId origin = new StarSystemId(in.readLong());
            StarSystemId destination = new StarSystemId(in.readLong());
            int payloadLength = in.readInt();
            if (payloadLength <= 0 || payloadLength > WorldSystemBinary.MAX_GAMESTATE_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Invalid fleet payload length");
            }
            byte[] payload = in.readNBytes(payloadLength);
            if (payload.length != payloadLength) {
                throw new EOFException("Fleet payload truncated");
            }
            fleets.add(new FleetPlacementState(
                    id,
                    kind,
                    null,
                    null,
                    new FleetTransitState(origin, destination, FleetPayloadCodec.decode(payload))));
        }
        return List.copyOf(fleets);
    }

    static void writeJumps(DataOutputStream out, List<FleetJumpState> jumps) throws IOException {
        WorldIoSupport.writeCount(out, jumps.size(), MAX_FLEETS, "fleetJumps");
        for (FleetJumpState jump : jumps) {
            out.writeLong(jump.fleetId().value());
            WorldIoSupport.writeString(out, jump.phase().name());
            out.writeLong(jump.originSystemId().value());
            out.writeLong(jump.destinationSystemId().value());
            out.writeLong(jump.phaseStartedTick());
            out.writeLong(jump.phaseEndsTick());
            out.writeFloat(jump.arrivalX());
            out.writeFloat(jump.arrivalY());
        }
    }

    static List<FleetJumpState> readJumps(DataInputStream in) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_FLEETS, "fleetJumps");
        List<FleetJumpState> jumps = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            FleetId fleetId = new FleetId(in.readLong());
            FleetJumpPhase phase;
            try {
                phase = FleetJumpPhase.valueOf(WorldIoSupport.readString(in));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown FleetJumpPhase", exception);
            }
            jumps.add(new FleetJumpState(
                    fleetId,
                    phase,
                    new StarSystemId(in.readLong()),
                    new StarSystemId(in.readLong()),
                    in.readLong(),
                    in.readLong(),
                    in.readFloat(),
                    in.readFloat()));
        }
        return List.copyOf(jumps);
    }
}
