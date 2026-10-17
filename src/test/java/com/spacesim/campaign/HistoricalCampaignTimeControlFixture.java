package com.spacesim.campaign;

import com.spacesim.persistence.CampaignTimeSkipPersistenceCodec;
import com.spacesim.simulation.CampaignTimeSkipState;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Builds older framing by removing validated current sidecars and idle time control. */
final class HistoricalCampaignTimeControlFixture {
    private HistoricalCampaignTimeControlFixture() { }
    static byte[] withoutIdleTimeControl(byte[] bytes) {
        try {
            // Some callers supply the complete file, others the body after its header.
            var input = new java.io.ByteArrayInputStream(bytes);
            var in = new java.io.DataInputStream(input);
            if (ByteBuffer.wrap(bytes).getInt() == 0x53323843) {
                in.readInt(); in.readInt(); in.readInt(); in.readUTF();
            }
            var prefix = new java.io.ByteArrayOutputStream();
            prefix.write(bytes, 0, bytes.length - input.available());
            var prefixOut = new java.io.DataOutputStream(prefix);
            byte[] stage21 = null;
            for (int index = 0; index < 14; index++) {
                int length = in.readInt();
                if (length <= 0 || length > input.available()) throw new AssertionError("Invalid current fixture framing");
                byte[] payload = in.readNBytes(length); if (index == 0) stage21 = payload;
                prefixOut.writeInt(length); prefixOut.write(payload);
            }
            int declarationLength = in.readInt();
            if (declarationLength <= 0 || declarationLength > input.available()) throw new AssertionError("Invalid fixture content framing");
            HistoricalCampaignContentFixture.historical(in.readNBytes(declarationLength), stage21);
            int crewLength = in.readInt();
            if (crewLength <= 0 || crewLength > input.available()) throw new AssertionError("Invalid fixture crew framing");
            com.spacesim.persistence.Stage23FleetCrewManifestCodec.decode(in.readNBytes(crewLength));
            int flightLength = in.readInt();
            if (flightLength <= 0 || flightLength > input.available()) throw new AssertionError("Invalid fixture local-flight framing");
            var flights = com.spacesim.persistence.SmallCraftLocalFlightPersistenceCodec.decode(in.readNBytes(flightLength));
            if (!flights.flights().isEmpty()) throw new AssertionError("Historical fixture cannot discard physical craft deployments");
            int ownershipLength = in.readInt();
            if (ownershipLength <= 0 || ownershipLength > input.available()) throw new AssertionError("Invalid fixture personal-property framing");
            var ownership = com.spacesim.persistence.PersonalCraftOwnershipCodec.decode(in.readNBytes(ownershipLength));
            if (!ownership.isEmpty()) throw new AssertionError("Historical fixture cannot discard personal craft property rights");
            int admissionLength = in.readInt();
            if (admissionLength <= 0 || admissionLength > input.available()) throw new AssertionError("Invalid fixture admission framing");
            if (!com.spacesim.persistence.IndustrialStationAdmissionCodec.decode(in.readNBytes(admissionLength)).isEmpty())
                throw new AssertionError("Historical fixture cannot discard industrial admissions");
            HistoricalCampaignContentFixture.skipEmptyBuild(in);
            if (in.read() != -1) throw new AssertionError("Trailing current fixture bytes");
            bytes = prefix.toByteArray();
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        byte[] idle = CampaignTimeSkipPersistenceCodec.encode(CampaignTimeSkipState.idle());
        int boundary = bytes.length - idle.length - 4;
        if (boundary < 0 || ByteBuffer.wrap(bytes).getInt(boundary) != idle.length
                || !Arrays.equals(idle, Arrays.copyOfRange(bytes, boundary + 4, bytes.length)))
            throw new AssertionError("Historical fixture cannot discard active time control");
        return Arrays.copyOf(bytes, boundary);
    }
}
