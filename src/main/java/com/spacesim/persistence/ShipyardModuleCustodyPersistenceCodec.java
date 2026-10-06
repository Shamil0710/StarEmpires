package com.spacesim.persistence;

import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.economy.ShipyardModuleCustodyState;
import com.spacesim.economy.ShipyardModuleCustodyState.StoredModule;
import com.spacesim.ship.ShipyardRefitContinuity.RemovedModuleState;
import java.io.*;
import java.util.ArrayList;
import java.util.Objects;

/** Bounded schemas 1/2 for individual equipment, condition and explicit actor ownership. */
public final class ShipyardModuleCustodyPersistenceCodec {
    /** Maximum native payload size, including worst-case bounded UTF identifiers. */
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    private ShipyardModuleCustodyPersistenceCodec() { }

    /**
     * Encodes exact equipment identities and condition without converting them to products.
     * @param state physical equipment inventory
     * @return deterministic native bytes
     */
    public static byte[] encode(ShipyardModuleCustodyState state) {
        Objects.requireNonNull(state, "state");
        try {
            var buffer = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(buffer)) {
                int schema = state.modules().stream().anyMatch(module -> module.ownerActorId() != null) ? 2 : 1;
                out.writeInt(schema); out.writeInt(state.modules().size());
                for (var module : state.modules()) {
                    out.writeUTF(module.custodyId()); out.writeUTF(module.stationId());
                    out.writeLong(module.sourceAssetId()); out.writeLong(module.removedAtTick());
                    out.writeUTF(module.condition().assignment().mountId());
                    out.writeUTF(module.condition().assignment().moduleId());
                    out.writeDouble(module.condition().integrity());
                    out.writeDouble(module.condition().secondsSinceService());
                    if (schema == 2) {
                        out.writeBoolean(module.ownerActorId() != null);
                        if (module.ownerActorId() != null) out.writeUTF(module.ownerActorId());
                    }
                }
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Module custody payload too large");
            return bytes;
        } catch (IOException e) { throw new IllegalStateException("Cannot encode module custody", e); }
    }

    /**
     * Restores exact condition, rejecting unknown schema, duplicates, truncation and trailing data.
     * @param bytes bounded native payload
     * @return validated physical module inventory
     */
    public static ShipyardModuleCustodyState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length < 8 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid custody payload size");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int schema = in.readInt();
            if (schema != 1 && schema != 2) throw new IllegalArgumentException("Unknown module custody schema");
            int count = in.readInt();
            if (count < 0 || count > ShipyardModuleCustodyState.CAPACITY || count > in.available() / 40)
                throw new IllegalArgumentException("Invalid module custody count");
            var rows = new ArrayList<StoredModule>(count);
            for (int i = 0; i < count; i++) {
                String id = in.readUTF(), station = in.readUTF();
                long source = in.readLong(), tick = in.readLong();
                var assignment = new InstalledModuleDefinition(in.readUTF(), in.readUTF());
                var condition = new RemovedModuleState(assignment, in.readDouble(), in.readDouble());
                String owner = schema == 2 && in.readBoolean() ? in.readUTF() : null;
                rows.add(new StoredModule(id, station, source, tick, condition, owner));
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing module custody bytes");
            return new ShipyardModuleCustodyState(rows);
        } catch (IOException e) { throw new IllegalArgumentException("Truncated module custody payload", e); }
    }
}
