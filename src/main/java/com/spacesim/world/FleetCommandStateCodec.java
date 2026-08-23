package com.spacesim.world;

import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

/** Deterministic bounded codec for Stage-21D command-group and strategic-order state. */
public final class FleetCommandStateCodec {
    private static final int MAGIC = 0x46323144;
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final int MAX_GROUPS = 100_000;
    private static final int MAX_ORDERS = 1_000_000;
    private static final int MAX_MEMBERS = 100_000;
    private static final int MAX_ROUTE_SYSTEMS = 100_000;

    private FleetCommandStateCodec() { throw new AssertionError("No instances"); }

    public static byte[] encode(FleetCommandState state) {
        FleetCommandState checked = Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC); out.writeInt(VERSION);
                out.writeLong(checked.nextCommandGroupId()); out.writeLong(checked.nextOrderId());
                requireCount(checked.groups().size(), MAX_GROUPS, "groups");
                out.writeInt(checked.groups().size());
                for (CommandGroupState group : checked.groups()) {
                    out.writeLong(group.id()); out.writeInt(group.factionId()); out.writeUTF(group.name());
                    out.writeLong(group.homeSystemId().value());
                    out.writeBoolean(group.reserve()); out.writeBoolean(group.homeDefense());
                    out.writeInt(group.maxStrategicRiskBps());
                    requireCount(group.memberFleetIds().size(), MAX_MEMBERS, "group members");
                    out.writeInt(group.memberFleetIds().size());
                    for (FleetId fleetId : group.memberFleetIds()) out.writeLong(fleetId.value());
                }
                requireCount(checked.orders().size(), MAX_ORDERS, "orders");
                out.writeInt(checked.orders().size());
                for (FleetOrderState order : checked.orders()) {
                    out.writeLong(order.id()); out.writeLong(order.commandGroupId());
                    out.writeUTF(order.type().name()); out.writeUTF(order.source().name());
                    out.writeLong(order.targetSystemId().value()); out.writeInt(order.routeCursor());
                    out.writeLong(order.submittedTick()); out.writeLong(order.stagingDeadlineTick());
                    out.writeUTF(order.status().name());
                    requireCount(order.route().size(), MAX_ROUTE_SYSTEMS, "route systems");
                    out.writeInt(order.route().size());
                    for (StarSystemId systemId : order.route()) out.writeLong(systemId.value());
                }
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length <= 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Stage-21D command payload exceeds bounded size");
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory Stage-21D command encoding failure", exception);
        }
    }

    public static FleetCommandState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length <= 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Stage-21D command payload size outside bounds");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("Invalid Stage-21D command magic");
            int version = in.readInt();
            if (version != VERSION) throw new IllegalArgumentException("Unsupported Stage-21D command version: " + version);
            long nextGroup = in.readLong(); long nextOrder = in.readLong();
            int groupCount = readCount(in, MAX_GROUPS, "groups");
            ArrayList<CommandGroupState> groups = new ArrayList<>(groupCount);
            for (int i = 0; i < groupCount; i++) {
                long id = in.readLong(); int factionId = in.readInt(); String name = in.readUTF();
                StarSystemId home = new StarSystemId(in.readLong());
                boolean reserve = in.readBoolean(); boolean homeDefense = in.readBoolean();
                int maxRisk = in.readInt();
                int memberCount = readCount(in, MAX_MEMBERS, "group members");
                ArrayList<FleetId> members = new ArrayList<>(memberCount);
                for (int m = 0; m < memberCount; m++) members.add(new FleetId(in.readLong()));
                groups.add(new CommandGroupState(id, factionId, name, members, home, reserve, homeDefense, maxRisk));
            }
            int orderCount = readCount(in, MAX_ORDERS, "orders");
            ArrayList<FleetOrderState> orders = new ArrayList<>(orderCount);
            for (int i = 0; i < orderCount; i++) {
                long id = in.readLong(); long groupId = in.readLong();
                OrderType type = OrderType.valueOf(in.readUTF()); OrderSource source = OrderSource.valueOf(in.readUTF());
                StarSystemId target = new StarSystemId(in.readLong()); int cursor = in.readInt();
                long submitted = in.readLong(); long deadline = in.readLong(); OrderStatus status = OrderStatus.valueOf(in.readUTF());
                int routeCount = readCount(in, MAX_ROUTE_SYSTEMS, "route systems");
                ArrayList<StarSystemId> route = new ArrayList<>(routeCount);
                for (int r = 0; r < routeCount; r++) route.add(new StarSystemId(in.readLong()));
                orders.add(new FleetOrderState(id, groupId, type, source, target, route, cursor, submitted, deadline, status));
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing bytes after Stage-21D command payload");
            return new FleetCommandState(nextGroup, nextOrder, groups, orders);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Stage-21D command payload is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("Cannot decode Stage-21D command payload", exception);
        }
    }

    private static void requireCount(int value, int maximum, String label) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException(label + " count outside bounds");
    }
    private static int readCount(DataInputStream input, int maximum, String label) throws IOException {
        int value = input.readInt(); requireCount(value, maximum, label); return value;
    }
}
