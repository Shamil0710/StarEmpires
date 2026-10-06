package com.spacesim.persistence;

import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.economy.ShipyardRepairQueueState;
import com.spacesim.economy.ShipyardRepairQueueState.RepairOrder;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.*;
import java.io.*;
import java.util.*;

/** Bounded deterministic repair custody/progress and schema-2 paid service reservation payload. */
public final class ShipyardRepairQueuePersistenceCodec {
    /** Maximum native payload before row allocation. */
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    private ShipyardRepairQueuePersistenceCodec() { }
    /**
     * Encodes exact pending repair evidence and custody; never repairs a ship.
     * @param state repair checkpoint
     * @return native deterministic bytes
     */
    public static byte[] encode(ShipyardRepairQueueState state) {
        Objects.requireNonNull(state);
        try {
            var buffer = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(buffer)) {
                int schema = state.orders().stream().anyMatch(o -> o.servicePayment() != null) ? 2 : 1;
                out.writeInt(schema); out.writeLong(state.lastProcessedTick()); out.writeInt(state.orders().size());
                for (var o : state.orders()) {
                    out.writeUTF(o.orderId()); out.writeLong(o.fleetId()); out.writeLong(o.assetId());
                    out.writeUTF(o.stationId()); out.writeUTF(o.yardInstanceId()); out.writeUTF(o.yardDefinitionId());
                    out.writeLong(o.startedAtTick()); out.writeUTF(o.sourceFit().hullId());
                    out.writeInt(o.sourceFit().installedModules().size());
                    for (var m : o.sourceFit().installedModules()) { out.writeUTF(m.mountId()); out.writeUTF(m.moduleId()); }
                    writeMap(out, o.sourceDamage().compartmentIntegrityById()); writeMap(out, o.sourceDamage().moduleDamage().moduleIntegrityByMount());
                    out.writeDouble(o.requiredWorkSeconds()); out.writeDouble(o.completedWorkSeconds());
                    writeMap(out, o.reservedCommodityMassByIdKg());
                    if (schema == 2) {
                        out.writeBoolean(o.servicePayment() != null);
                        if (o.servicePayment() != null) {
                            out.writeUTF(o.servicePayment().sellerFactionId());
                            out.writeLong(o.servicePayment().reservedMilliCredits());
                        }
                    }
                    if (buffer.size() > MAX_BYTES) throw new IllegalArgumentException("Repair payload exceeds bounds");
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Cannot encode repair custody", e); }
    }
    /**
     * Decodes bounded physical evidence and incomplete progress; rejects unknown framing and trailing data.
     * @param bytes native schema-1 or schema-2 payload
     * @return validated queue checkpoint
     */
    public static ShipyardRepairQueueState decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length < 16 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Repair payload size outside bounds");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int schema = in.readInt();
            if (schema != 1 && schema != 2) throw new IllegalArgumentException("Unknown repair schema");
            long tick = in.readLong(); int count = in.readInt();
            if (count < 0 || count > ShipyardRepairQueueState.CAPACITY || count > in.available() / 74)
                throw new IllegalArgumentException("Invalid repair count");
            var orders = new ArrayList<RepairOrder>(count);
            for (int i = 0; i < count; i++) {
                String id = in.readUTF(); long fleet = in.readLong(), asset = in.readLong();
                String station = in.readUTF(), yard = in.readUTF(), definition = in.readUTF();
                long started = in.readLong(); String hull = in.readUTF();
                int moduleCount = count(in, 4); var modules = new ArrayList<InstalledModuleDefinition>(moduleCount);
                for (int m = 0; m < moduleCount; m++) modules.add(new InstalledModuleDefinition(in.readUTF(), in.readUTF()));
                var damage = new Snapshot(readMap(in), new DamageState(readMap(in)));
                double required = in.readDouble(), completed = in.readDouble(); var reserved = readMap(in);
                var payment = schema == 2 && in.readBoolean()
                        ? new com.spacesim.economy.ShipyardRepairServicePayment(in.readUTF(), in.readLong()) : null;
                orders.add(new RepairOrder(id, fleet, asset, station, yard, definition, started,
                        new InstalledFit(hull, modules), damage, required, completed, reserved, payment));
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing repair payload");
            return new ShipyardRepairQueueState(tick, orders);
        } catch (IOException e) { throw new IllegalArgumentException("Truncated repair payload", e); }
    }
    private static int count(DataInputStream in, int minimumBytes) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 4096 || count > in.available() / minimumBytes) throw new IllegalArgumentException("Invalid repair evidence count");
        return count;
    }
    private static void writeMap(DataOutputStream out, Map<String, Double> map) throws IOException {
        out.writeInt(map.size());
        for (var row : new TreeMap<>(map).entrySet()) { out.writeUTF(row.getKey()); out.writeDouble(row.getValue()); }
    }
    private static Map<String, Double> readMap(DataInputStream in) throws IOException {
        int count = count(in, 10); var map = new TreeMap<String, Double>();
        for (int i = 0; i < count; i++) if (map.putIfAbsent(in.readUTF(), in.readDouble()) != null)
            throw new IllegalArgumentException("Duplicate repair evidence key");
        return map;
    }
}
