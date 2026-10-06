package com.spacesim.campaign;

import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.persistence.Stage20FreightRuntimeMaterializer;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignInitialMiningReserveTest {
    @Test void miningManifestUsesOneExistingNpcReserveAndLegacyLoadNeverIssuesIt() throws Exception {
        var baseline = Stage228CampaignAuthority.create(1, List.of(), Stage20FreightRuntimeMaterializer.ReserveLoadoutPolicy.BASELINE);
        var current = Stage228CampaignAuthority.create(1);
        var oldRuntime = baseline.coordinator().runtime();
        var newRuntime = current.coordinator().runtime();
        var original = oldRuntime.freight().capture();
        var actual = newRuntime.freight().capture();
        assertEquals(Stage20FreightRuntimeMaterializer.MINING_RESERVE_VERSION, actual.materializationVersion());
        assertEquals(original.nextFleetIdValue(), actual.nextFleetIdValue());
        assertEquals(original.nextCargoLotOrdinal(), actual.nextCargoLotOrdinal());
        assertEquals(original.orders(), actual.orders());
        assertEquals(original.cargoLots(), actual.cargoLots());
        assertEquals(original.productLots(), actual.productLots());
        assertEquals(original.freighters().size(), actual.freighters().size());
        var changed = actual.freighters().stream().filter(f -> !f.equals(oldRuntime.freight().findFreighter(f.fleetId()).orElseThrow())).toList();
        assertEquals(1, changed.size());
        var miner = changed.get(0);
        var oldHull = oldRuntime.freight().findFreighter(miner.fleetId()).orElseThrow();
        assertEquals("faction.beta", miner.stableFactionId());
        assertEquals(oldHull.stableFactionId(), miner.stableFactionId());
        assertEquals(oldHull.legalFactionId(), miner.legalFactionId());
        assertEquals(oldHull.ownershipOrdinal(), miner.ownershipOrdinal());
        assertEquals(oldHull.hullId(), miner.hullId());
        assertEquals(oldHull.currentSystemId(), miner.currentSystemId());
        assertEquals(oldHull.physicalState(), miner.physicalState());
        assertTrue(miner.activeOrderId().isEmpty());
        assertEquals(Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT, miner.fitId());
        assertEquals(8_000_000d, miner.cargoCapacityKg());
        assertEquals(0, miner.cargoMassKg());
        var placement = newRuntime.world().findFleet(miner.fleetId()).orElseThrow();
        var installed = newRuntime.world().findSession(placement.systemId()).orElseThrow().getEntityRegistry()
                .require(placement.localEntityId()).getComponent(com.spacesim.components.EngineeringComponent.class);
        var capability = new com.spacesim.ship.ShipMiningEngineeringAdapter().derive(
                new com.spacesim.ship.ProductionEngineeringRuntimeResolver().derive(installed)).orElseThrow();
        assertTrue(capability.maximumSourceKgPerSecond() > 0, "A fit ID alone cannot grant extraction capability");
        assertEquals(oldRuntime.captureState().campaign().industrialState(), newRuntime.captureState().campaign().industrialState());
        for (var faction : current.coordinator().actors().capture()) {
            String id = faction.factionContentId();
            assertEquals(oldRuntime.world().findFactionEconomicState(id), newRuntime.world().findFactionEconomicState(id));
        }
        var saved = current.captureState();
        assertNull(saved.playerState(), "NPC capital must not grant personal ownership");
        assertEquals(saved, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved))).captureState());
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(
                withV15Header(Stage228GeneratedCampaignPersistenceCodec.encode(saved))));
        var historical = baseline.captureState();
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(withV15Header(Stage228GeneratedCampaignPersistenceCodec.encode(historical)));
        assertEquals(historical, migrated);
        var restored = Stage228CampaignAuthority.restore(migrated);
        assertEquals(original, restored.coordinator().runtime().freight().capture(), "Legacy load cannot issue a mining vessel or reconfigure a hull");
        assertEquals(historical, restored.captureState());
        assertNotEquals(miner.fleetId(), current.previewIndependentPilotStart().fleetId(), "Mining capital must not silently replace the cargo starter");
    }

    private static byte[] withV15Header(byte[] encoded) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var in = new DataInputStream(new ByteArrayInputStream(encoded)); var out = new DataOutputStream(bytes)) {
            out.writeInt(in.readInt()); in.readInt(); in.readInt(); in.readUTF();
            out.writeInt(15); out.writeInt(15); out.writeUTF("m22.8.generated-campaign.v15");
            out.write(in.readAllBytes());
        }
        return bytes.toByteArray();
    }
}
