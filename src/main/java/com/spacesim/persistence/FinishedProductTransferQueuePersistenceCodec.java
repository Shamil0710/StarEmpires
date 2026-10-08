package com.spacesim.persistence;

import com.spacesim.economy.FinishedProductTransferWorkQueue;
import com.spacesim.economy.FinishedProductTransferWorkQueue.Order;
import com.spacesim.economy.FinishedProductTransferWorkQueue.State;
import com.spacesim.world.FleetId;
import java.io.*;
import java.util.ArrayList;
import java.util.Objects;

/** Bounded schema-2 pending handling and held purchases; schema 1 grants no payment. */
public final class FinishedProductTransferQueuePersistenceCodec {
    /** Maximum accepted encoded pending work before allocating rows. */
    public static final int MAX_BYTES = 1024 * 1024;
    private FinishedProductTransferQueuePersistenceCodec() { }

    /**
     * Encodes actual unfinished work without adding product inventory.
     * @param state bounded pending handling
     * @return deterministic schema-2 bytes
     */
    public static byte[] encode(State state) {
        Objects.requireNonNull(state);
        try {
            var buffer = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(buffer)) {
                out.writeInt(2); out.writeLong(state.lastProcessedTick()); out.writeInt(state.orders().size());
                for (var order : state.orders()) {
                    out.writeUTF(order.orderId()); out.writeLong(order.fleetId().value());
                    out.writeUTF(order.sourceStorageId()); out.writeUTF(order.destinationStorageId());
                    out.writeUTF(order.productId()); out.writeInt(order.count()); out.writeLong(order.startedAtTick());
                    out.writeDouble(order.completedHandlingKg());
                    out.writeBoolean(order.purchasePayment() != null);
                    if (order.purchasePayment() != null) {
                        out.writeUTF(order.purchasePayment().sellerFactionId());
                        out.writeLong(order.purchasePayment().reservedMilliCredits());
                    }
                }
            }
            if (buffer.size() > MAX_BYTES) throw new IllegalArgumentException("Product handling checkpoint exceeds bounds");
            return buffer.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("Cannot encode product handling", failure); }
    }

    /**
     * Rejects unknown versions, invalid bounds, duplicate owners and incomplete/trailing framing.
     * @param bytes bounded standalone product handling
     * @return structurally and authored-mass validated pending work
     */
    public static State decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length < 16 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Product handling size outside bounds");
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int schema = input.readInt();
            if (schema != 1 && schema != 2) throw new IllegalArgumentException("Unknown product handling schema");
            long tick = input.readLong(); int count = input.readInt();
            if (count < 0 || count > State.CAPACITY || count > input.available() / 36)
                throw new IllegalArgumentException("Product handling row count outside bounds");
            var orders = new ArrayList<Order>(count);
            for (int i = 0; i < count; i++) {
                String id = input.readUTF(); var fleet = new FleetId(input.readLong());
                String source = input.readUTF(), destination = input.readUTF(), product = input.readUTF();
                int units = input.readInt(); long started = input.readLong(); double work = input.readDouble();
                var payment = schema >= 2 && input.readBoolean()
                        ? new com.spacesim.economy.FinishedProductPurchasePayment(input.readUTF(), input.readLong()) : null;
                orders.add(new Order(id, fleet, source, destination, product, units, started, work, payment));
            }
            if (input.read() != -1) throw new IllegalArgumentException("Trailing product handling checkpoint");
            var state = new State(tick, orders);
            return new FinishedProductTransferWorkQueue(com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts(), state).capture();
        } catch (IOException failure) { throw new IllegalArgumentException("Truncated product handling", failure); }
    }
}
