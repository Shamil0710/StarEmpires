package com.spacesim.persistence;

import com.spacesim.economy.ShipyardSmallCraftBuildQueueState;
import com.spacesim.economy.ShipyardSmallCraftBuildQueueState.Order;
import com.spacesim.economy.ShipyardSmallCraftBuildWorkQueue;
import com.spacesim.content.Stage23SmallCraftProductionCatalogs;
import java.io.*;
import java.util.*;

/** Bounded deterministic paid-build custody; content declarations do not create stock or work. */
public final class ShipyardSmallCraftBuildQueuePersistenceCodec {
    /** Maximum standalone payload size. */
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private ShipyardSmallCraftBuildQueuePersistenceCodec() { }
    /**
     * Encodes exact validated work and escrow with the authored content fingerprint.
     * @param state retained queue owner
     * @return deterministic schema-1 bytes
     */
    public static byte[] encode(ShipyardSmallCraftBuildQueueState state) {
        new ShipyardSmallCraftBuildWorkQueue(Objects.requireNonNull(state));
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(1); out.writeUTF(fingerprint()); out.writeLong(state.lastProcessedTick()); out.writeInt(state.orders().size());
                for (var o : state.orders()) {
                    for (var s : List.of(o.orderId(), o.ownerActorId(), o.builderFactionId(), o.stationId(),
                            o.yardInstanceId(), o.yardDefinitionId(), o.designId())) out.writeUTF(s);
                    out.writeLong(o.startedAtTick()); out.writeDouble(o.requiredWorkSeconds()); out.writeDouble(o.completedWorkSeconds());
                    out.writeInt(o.reservedCommodityMassKg().size());
                    for (var e : o.reservedCommodityMassKg().entrySet()) { out.writeUTF(e.getKey()); out.writeDouble(e.getValue()); }
                    out.writeInt(o.reservedProductCounts().size());
                    for (var e : o.reservedProductCounts().entrySet()) { out.writeUTF(e.getKey()); out.writeInt(e.getValue()); }
                }
            }
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Build payload too large");
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    /**
     * Decodes strict bounded framing and revalidates the exact authored bill/work.
     * @param bytes standalone schema-1 bytes
     * @return exact retained queue
     */
    public static ShipyardSmallCraftBuildQueueState decode(byte[] bytes) {
        if (bytes == null || bytes.length < 82 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid build payload size");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1 || !in.readUTF().equals(fingerprint())) throw new IllegalArgumentException("Build schema/content mismatch");
            long tick = in.readLong(); int rows = count(in, ShipyardSmallCraftBuildQueueState.CAPACITY);
            var orders = new ArrayList<Order>();
            for (int i = 0; i < rows; i++) {
                String id = in.readUTF(), actor = in.readUTF(), faction = in.readUTF(), station = in.readUTF(),
                        yard = in.readUTF(), definition = in.readUTF(), design = in.readUTF();
                long started = in.readLong(); double required = in.readDouble(), work = in.readDouble();
                var raw = new TreeMap<String, Double>(); int n = count(in, 128);
                for (int j = 0; j < n; j++) if (raw.putIfAbsent(in.readUTF(), in.readDouble()) != null)
                    throw new IllegalArgumentException("Duplicate build material");
                var stock = new TreeMap<String, Integer>(); n = count(in, 128);
                for (int j = 0; j < n; j++) if (stock.putIfAbsent(in.readUTF(), in.readInt()) != null)
                    throw new IllegalArgumentException("Duplicate build equipment");
                orders.add(new Order(id, actor, faction, station, yard, definition, design, started, required, work, raw, stock));
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing build payload");
            var state = new ShipyardSmallCraftBuildQueueState(tick, orders); new ShipyardSmallCraftBuildWorkQueue(state); return state;
        } catch (IOException e) { throw new IllegalArgumentException("Truncated build payload", e); }
    }
    private static int count(DataInputStream in, int max) throws IOException {
        int n = in.readInt(); if (n < 0 || n > max || n > in.available()) throw new IllegalArgumentException("Invalid build count"); return n;
    }
    private static String fingerprint() { return Declaration.VALUE; }
    private static final class Declaration { private static final String VALUE = computeFingerprint(); }
    private static String computeFingerprint() {
        try {
            String text = Stage23SmallCraftProductionCatalogs.products().getProducts().toString() + '|'
                    + Stage23SmallCraftProductionCatalogs.manufacturing().getFingerprint() + '|'
                    + Stage23SmallCraftProductionCatalogs.shipyards().getFingerprint() + '|'
                    + new com.spacesim.world.SmallCraftFitAuthority(com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader.loadDefault()).catalogFingerprint();
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
