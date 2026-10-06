package com.spacesim.persistence;

import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.economy.ShipyardRefitQueueState;
import com.spacesim.economy.ShipyardRefitQueueState.Order;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.*;
import java.io.*;
import java.util.*;

/** Bounded refit schemas 1/2 and standalone schema-3 held service payment payload. */
public final class ShipyardRefitQueuePersistenceCodec {
    /** Maximum payload size before decoding allocation. */
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    private ShipyardRefitQueuePersistenceCodec() { }
    /**
     * Encodes exact physical equipment escrow and incomplete work.
     * @param state pending refits
     * @return deterministic bounded native bytes
     */
    public static byte[] encode(ShipyardRefitQueueState state) {
        Objects.requireNonNull(state);
        try {
            var buffer = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(buffer)) {
                int schema = state.orders().stream().anyMatch(o -> o.servicePayment() != null) ? 3 : 2;
                out.writeInt(schema); out.writeLong(state.lastProcessedTick()); out.writeInt(state.orders().size());
                for (var o : state.orders()) {
                    out.writeUTF(o.orderId()); out.writeLong(o.fleetId()); out.writeLong(o.assetId());
                    out.writeUTF(o.stationId()); out.writeUTF(o.yardInstanceId()); out.writeUTF(o.yardDefinitionId()); out.writeLong(o.startedAtTick());
                    fit(out, o.sourceFit()); fit(out, o.targetFit());
                    map(out, o.sourceDamage().compartmentIntegrityById()); map(out, o.sourceDamage().moduleDamage().moduleIntegrityByMount());
                    out.writeDouble(o.requiredWorkSeconds()); out.writeDouble(o.completedWorkSeconds());
                    out.writeInt(o.reservedProductCounts().size());
                    for (var item : o.reservedProductCounts().entrySet()) { out.writeUTF(item.getKey()); out.writeInt(item.getValue()); }
                    out.writeInt(o.reservedUsedModulesByTargetMount().size());
                    for (var item : o.reservedUsedModulesByTargetMount().entrySet()) {
                        out.writeUTF(item.getKey());
                        byte[] row = ShipyardModuleCustodyPersistenceCodec.encode(new com.spacesim.economy.ShipyardModuleCustodyState(List.of(item.getValue())));
                        out.writeInt(row.length); out.write(row);
                    }
                    if (schema == 3) {
                        out.writeBoolean(o.servicePayment() != null);
                        if (o.servicePayment() != null) {
                            out.writeUTF(o.servicePayment().sellerFactionId()); out.writeLong(o.servicePayment().reservedMilliCredits());
                        }
                    }
                    if (buffer.size() > MAX_BYTES) throw new IllegalArgumentException("Refit payload exceeds bounds");
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Cannot encode refit checkpoint", e); }
    }
    /**
     * Decodes bounded physical evidence; rejects unsupported, duplicate, truncated and trailing framing.
     * Authored recipe/work validation occurs in the work queue before live use.
     * @param bytes standalone schema-1, schema-2 or schema-3 payload
     * @return validated structural checkpoint
     */
    public static ShipyardRefitQueueState decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length < 16 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Refit payload size outside bounds");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int schema = in.readInt();
            if (schema != 1 && schema != 2 && schema != 3) throw new IllegalArgumentException("Unknown refit schema");
            long tick = in.readLong(); int rows = in.readInt();
            if (rows < 0 || rows > ShipyardRefitQueueState.CAPACITY || rows > in.available() / 80) throw new IllegalArgumentException("Invalid refit job count");
            var orders = new ArrayList<Order>(rows);
            for (int i = 0; i < rows; i++) {
                String id = in.readUTF(); long fleet = in.readLong(), asset = in.readLong();
                String station = in.readUTF(), yard = in.readUTF(), design = in.readUTF(); long started = in.readLong();
                var source = fit(in); var target = fit(in); var damage = new Snapshot(map(in), new DamageState(map(in)));
                double required = in.readDouble(), work = in.readDouble(); int items = count(in, 6); var stock = new TreeMap<String, Integer>();
                for (int item = 0; item < items; item++) if (stock.putIfAbsent(in.readUTF(), in.readInt()) != null) throw new IllegalArgumentException("Duplicate refit escrow");
                var used = new TreeMap<String, com.spacesim.economy.ShipyardModuleCustodyState.StoredModule>();
                if (schema >= 2) {
                    int usedCount = count(in, 6);
                    for (int item = 0; item < usedCount; item++) {
                        String mount = in.readUTF(); int length = in.readInt();
                        if (length <= 0 || length > ShipyardModuleCustodyPersistenceCodec.MAX_BYTES || length > in.available())
                            throw new IllegalArgumentException("Invalid used refit input size");
                        var decoded = ShipyardModuleCustodyPersistenceCodec.decode(in.readNBytes(length));
                        if (decoded.modules().size() != 1 || used.putIfAbsent(mount, decoded.modules().get(0)) != null)
                            throw new IllegalArgumentException("Invalid or duplicate used refit input");
                    }
                }
                var payment = schema == 3 && in.readBoolean()
                        ? new com.spacesim.economy.ShipyardRefitServicePayment(in.readUTF(), in.readLong()) : null;
                orders.add(new Order(id, fleet, asset, station, yard, design, started, source, target, damage, required, work, stock, used, payment));
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing refit payload");
            return new ShipyardRefitQueueState(tick, orders);
        } catch (IOException e) { throw new IllegalArgumentException("Truncated refit payload", e); }
    }
    private static void fit(DataOutputStream out, InstalledFit fit) throws IOException {
        out.writeUTF(fit.hullId()); out.writeInt(fit.installedModules().size());
        for (var module : fit.installedModules()) { out.writeUTF(module.mountId()); out.writeUTF(module.moduleId()); }
    }
    private static InstalledFit fit(DataInputStream in) throws IOException {
        String hull = in.readUTF(); int size = count(in, 4); var modules = new ArrayList<InstalledModuleDefinition>(size);
        for (int i = 0; i < size; i++) modules.add(new InstalledModuleDefinition(in.readUTF(), in.readUTF()));
        return new InstalledFit(hull, modules);
    }
    private static void map(DataOutputStream out, Map<String, Double> values) throws IOException {
        out.writeInt(values.size()); for (var entry : new TreeMap<>(values).entrySet()) { out.writeUTF(entry.getKey()); out.writeDouble(entry.getValue()); }
    }
    private static Map<String, Double> map(DataInputStream in) throws IOException {
        int size = count(in, 10); var result = new TreeMap<String, Double>();
        for (int i = 0; i < size; i++) if (result.putIfAbsent(in.readUTF(), in.readDouble()) != null) throw new IllegalArgumentException("Duplicate refit damage key");
        return result;
    }
    private static int count(DataInputStream in, int minimumBytes) throws IOException {
        int size = in.readInt(); if (size < 0 || size > 4096 || size > in.available() / minimumBytes) throw new IllegalArgumentException("Invalid refit evidence count"); return size;
    }
}
