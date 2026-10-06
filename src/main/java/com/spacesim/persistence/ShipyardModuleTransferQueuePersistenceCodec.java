package com.spacesim.persistence;

import com.spacesim.economy.ShipyardModuleCustodyState;
import com.spacesim.economy.ShipyardModuleTransferWorkQueue.Order;
import com.spacesim.economy.ShipyardModuleTransferWorkQueue.State;
import java.io.*;
import java.util.*;

/** Bounded standalone handling checkpoint; campaign admission must compose it with all physical owners. */
public final class ShipyardModuleTransferQueuePersistenceCodec {
    /** Maximum bytes accepted before allocating a checkpoint. */
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    private ShipyardModuleTransferQueuePersistenceCodec() { }

    /**
     * Encodes exact incomplete handling without granting equipment.
     * @param state bounded pending work
     * @return deterministic schema-1 bytes
     */
    public static byte[] encode(State state) {
        Objects.requireNonNull(state);
        try {
            var buffer = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(buffer)) {
                out.writeInt(1); out.writeLong(state.lastProcessedTick()); out.writeInt(state.orders().size());
                for (var order : state.orders()) {
                    out.writeUTF(order.orderId()); out.writeUTF(order.destinationStorageId());
                    out.writeLong(order.startedAtTick()); out.writeDouble(order.completedHandlingKg());
                    var row = ShipyardModuleCustodyPersistenceCodec.encode(new ShipyardModuleCustodyState(List.of(order.source())));
                    out.writeInt(row.length); out.write(row);
                    if (buffer.size() > MAX_BYTES) throw new IllegalArgumentException("Handling checkpoint exceeds bounds");
                }
            }
            return buffer.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("Cannot encode handling checkpoint", failure); }
    }

    /**
     * Rejects unknown schemas, duplicates, invalid counts and incomplete or trailing framing.
     * Authored mass and joint source-custody validation remain the runtime owner's responsibility.
     * @param bytes bounded standalone checkpoint
     * @return structurally validated pending handling
     */
    public static State decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length < 16 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Handling checkpoint size outside bounds");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IllegalArgumentException("Unknown equipment handling schema");
            long tick = in.readLong(); int count = in.readInt();
            if (count < 0 || count > State.CAPACITY || count > in.available() / 24)
                throw new IllegalArgumentException("Invalid handling job count");
            var orders = new ArrayList<Order>(count);
            for (int i = 0; i < count; i++) {
                String id = in.readUTF(), destination = in.readUTF(); long started = in.readLong(); double handled = in.readDouble();
                int size = in.readInt();
                if (size <= 0 || size > ShipyardModuleCustodyPersistenceCodec.MAX_BYTES || size > in.available())
                    throw new IllegalArgumentException("Invalid handling source size");
                var source = ShipyardModuleCustodyPersistenceCodec.decode(in.readNBytes(size));
                if (source.modules().size() != 1) throw new IllegalArgumentException("Handling requires one exact source row");
                orders.add(new Order(id, source.modules().get(0), destination, started, handled));
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing handling checkpoint");
            return new State(tick, orders);
        } catch (IOException failure) { throw new IllegalArgumentException("Truncated handling checkpoint", failure); }
    }
}
