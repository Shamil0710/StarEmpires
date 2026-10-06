package com.spacesim.persistence;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage23YardConstructionCatalog;
import com.spacesim.economy.Stage23YardConstructionWorkQueue;
import com.spacesim.economy.Stage23YardConstructionWorkQueue.Order;
import com.spacesim.economy.Stage23YardConstructionWorkQueue.State;
import java.io.*;
import java.util.ArrayList;
import java.util.Objects;

/** Bounded physical yard structures with exact schema-1 warehouse and schema-2 site custody. */
public final class Stage23YardConstructionPersistenceCodec {
    /** Maximum accepted standalone construction bytes. */
    public static final int MAX_BYTES = 1024 * 1024;
    private Stage23YardConstructionPersistenceCodec() { }

    /**
     * Encodes exact authored construction evidence without creating inventory or installed yards.
     * @param state retained physical work
     * @return bounded deterministic framing, retaining schema 1 for historical warehouse-only custody
     */
    public static byte[] encode(State state) {
        state = validate(Objects.requireNonNull(state));
        int version = state.orders().stream().anyMatch(Order::stagedAtSite) ? 2 : 1;
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(version); out.writeUTF(state.specificationFingerprint()); out.writeLong(state.lastProcessedTick());
                out.writeInt(state.orders().size());
                for (var order : state.orders()) {
                    out.writeUTF(order.orderId()); out.writeUTF(order.yardInstanceId()); out.writeUTF(order.yardDefinitionId());
                    out.writeUTF(order.stationId()); out.writeUTF(order.locationTag());
                    out.writeLong(order.startedAtTick()); out.writeDouble(order.completedWorkSeconds());
                    if (version >= 2) {
                        out.writeBoolean(order.stagedAtSite()); out.writeInt(order.deliveredMassByCommodityKg().size());
                        for (var entry : order.deliveredMassByCommodityKg().entrySet()) {
                            out.writeUTF(entry.getKey()); out.writeDouble(entry.getValue());
                        }
                    }
                }
            }
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Yard construction exceeds byte bounds");
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("Cannot encode yard construction", failure); }
    }

    /**
     * Rejects unknown versions, truncated/trailing data and incompatible physical specifications.
     * @param bytes bounded standalone construction evidence
     * @return immutable validated work, without binding inventory or granting resources
     */
    public static State decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length < 82 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Yard construction size outside bounds");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int version = in.readInt();
            if (version != 1 && version != 2) throw new IllegalArgumentException("Unsupported yard construction schema");
            String fingerprint = in.readUTF(); long tick = in.readLong(); int count = in.readInt();
            if (count < 0 || count > State.CAPACITY || count > in.available() / 26)
                throw new IllegalArgumentException("Yard construction count outside bounds");
            var orders = new ArrayList<Order>(count);
            for (int i = 0; i < count; i++) {
                String order = in.readUTF(), yard = in.readUTF(), design = in.readUTF(), station = in.readUTF(), location = in.readUTF();
                long started = in.readLong(); double work = in.readDouble();
                if (version == 1) orders.add(new Order(order, yard, design, station, location, started, work));
                else {
                    boolean atSite = in.readBoolean(); int materials = in.readInt();
                    if (materials < 0 || materials > 64 || materials > in.available() / 10)
                        throw new IllegalArgumentException("Yard material custody count outside bounds");
                    var delivered = new java.util.TreeMap<String, Double>();
                    for (int j = 0; j < materials; j++)
                        if (delivered.putIfAbsent(in.readUTF(), in.readDouble()) != null)
                            throw new IllegalArgumentException("Duplicate yard site material");
                    orders.add(new Order(order, yard, design, station, location, started, work, atSite, delivered));
                }
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing yard construction data");
            return validate(new State(fingerprint, tick, orders));
        } catch (IOException failure) { throw new IllegalArgumentException("Truncated yard construction data", failure); }
    }

    private static State validate(State state) {
        return new Stage23YardConstructionWorkQueue(Stage23YardConstructionCatalog.loadDefault(),
                Stage18ResourceOntologyLoader.loadDefault(), state).capture();
    }
}
