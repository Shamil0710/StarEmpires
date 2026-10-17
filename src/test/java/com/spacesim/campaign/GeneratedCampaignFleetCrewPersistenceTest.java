package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.Stage23FleetCrewManifest;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignFleetCrewPersistenceTest {
    @Test void explicitNewGameCommissioningSurvivesPurchaseAndExactRestoreWithoutRepeatedStaffing() {
        var c = Stage228CampaignAuthority.create(1);
        var before = c.captureState();
        assertEquals(Stage23FleetCrewManifest.COMMISSIONED_PROFILE, before.crewManifest().profileVersion());
        assertFalse(before.crewManifest().entries().isEmpty());
        c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        var purchased = c.captureState();
        assertEquals(before.crewManifest(), purchased.crewManifest(), "Purchase transfers the same staffed hull, not a new crew allocation");
        assertTrue(purchased.crewManifest().availableCrew(purchased.playerState().activeFleetId()) > 0);
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(purchased)));
        assertEquals(purchased, restored.captureState());
        assertEquals(purchased.crewManifest(), restored.captureState().crewManifest());
        var runtime = purchased.stage21Runtime().stage21HRuntime().stage21GRuntime().stage21FRuntime()
                .stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime()
                .stage21ARuntime().stage20Runtime();
        assertEquals(purchased, GeneratedCampaignPlayerWorldTransition.compose(purchased,
                new com.spacesim.player.PlayableWorldState(com.spacesim.player.PlayableWorldState.CURRENT_VERSION,
                        runtime.worldState(), purchased.playerState())),
                "A composed personal transition must retain all adjacent owners including crew observations");
    }

    @Test void genuineV22LayoutRetainsUnknownStaffingAndForgedOlderHeaderCannotClaimTheNewPayload() throws Exception {
        var c = FoundedCampaignFixture.restore();
        var baseline = c.captureState();
        byte[] current = Stage228GeneratedCampaignPersistenceCodec.encode(baseline);
        var buffer = new ByteArrayOutputStream();
        try (var in = new DataInputStream(new ByteArrayInputStream(current)); var out = new DataOutputStream(buffer)) {
            out.writeInt(in.readInt()); in.readInt(); in.readInt(); in.readUTF();
            out.writeInt(22); out.writeInt(22); out.writeUTF("m22.8.generated-campaign.v22");
            // Genuine previous framing retains all fifteen original payloads and has no crew sidecar.
            HistoricalCampaignContentFixture.copyPayloads(in, out, 15);
            int crewLength = in.readInt(); assertEquals(crewLength, in.readNBytes(crewLength).length);
            int flightLength = in.readInt(); assertEquals(flightLength, in.readNBytes(flightLength).length);
            int ownershipLength = in.readInt();
            assertTrue(com.spacesim.persistence.PersonalCraftOwnershipCodec.decode(in.readNBytes(ownershipLength)).isEmpty());
            int admissionLength = in.readInt();
            assertTrue(com.spacesim.persistence.IndustrialStationAdmissionCodec.decode(in.readNBytes(admissionLength)).isEmpty());
            HistoricalCampaignContentFixture.skipEmptyBuild(in); assertEquals(-1, in.read());
        }
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(buffer.toByteArray());
        assertEquals(Stage23FleetCrewManifest.unobserved(), migrated.crewManifest());
        assertEquals(baseline.playerState(), migrated.playerState());
        assertEquals(baseline.stage21Runtime(), migrated.stage21Runtime());
        assertEquals(migrated, Stage228CampaignAuthority.restore(migrated).captureState());
        // Simply relabelling the newer payload without removing it is not a historical checkpoint.
        byte[] forged = current.clone(); java.nio.ByteBuffer.wrap(forged).putInt(4, 22).putInt(8, 22);
        byte[] oldIdentity = "m22.8.generated-campaign.v22".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(oldIdentity, 0, forged, 14, oldIdentity.length);
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(forged));
    }
}
