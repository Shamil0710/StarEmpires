package com.spacesim.campaign;

import com.spacesim.content.*;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import java.io.*;

/** Converts content declarations only after proving that no new structural content is discarded. */
final class HistoricalCampaignContentFixture {
    private HistoricalCampaignContentFixture() { }
    static byte[] historical(byte[] declaration, byte[] stage21) throws IOException {
        var runtime = Stage21IGeneratedWorldRuntimePersistenceCodec.decode(stage21).stage21HRuntime().stage21GRuntime()
                .stage21FRuntime().stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        var industrial = runtime.campaign().industrialState();
        if (industrial.constructionOrders().stream().anyMatch(o -> o.facilityDefinitionId().equals(Stage23StationStorageCatalog.FACILITY_ID))
                || industrial.facilities().stream().anyMatch(f -> f.state().definitionId().equals(Stage23StationStorageCatalog.FACILITY_ID)))
            throw new AssertionError("Historical fixture cannot discard storage construction or installation");
        try (var in = new DataInputStream(new ByteArrayInputStream(declaration))) {
            if (in.readInt() != 2 || !in.readUTF().equals(Stage23StationStorageCatalog.facilities().getFingerprint())
                    || !in.readUTF().equals(Stage23StationStorageCatalog.construction().getFingerprint())
                    || !in.readUTF().equals(Stage23StationStorageCatalog.effectFingerprint()) || in.read() != -1)
                throw new AssertionError("Unknown current structural content declaration");
        }
        var buffer = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(buffer)) {
            out.writeInt(1); out.writeUTF(Stage23BootstrapMaterialsCatalog.facilities().getFingerprint());
            out.writeUTF(Stage23BootstrapMaterialsCatalog.construction().getFingerprint());
        }
        return buffer.toByteArray();
    }
    static void skipEmptyBuild(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > in.available()) throw new AssertionError("Invalid build fixture framing");
        var state = com.spacesim.persistence.ShipyardSmallCraftBuildQueuePersistenceCodec.decode(in.readNBytes(length));
        if (!state.orders().isEmpty()) throw new AssertionError("Historical fixture cannot discard paid build custody");
    }
    static void copyPayloads(DataInputStream in, DataOutputStream out, int count) throws IOException {
        byte[] stage21 = null;
        for (int i = 0; i < count; i++) {
            int length = in.readInt(); if (length <= 0 || length > in.available()) throw new AssertionError("Invalid fixture payload");
            byte[] payload = in.readNBytes(length); if (i == 0) stage21 = payload;
            if (i == 14) payload = historical(payload, stage21);
            out.writeInt(payload.length); out.write(payload);
        }
    }
}
