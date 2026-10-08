package com.spacesim.persistence;

import com.spacesim.simulation.CampaignTimeSkipState;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CampaignTimeSkipPersistenceCodecTest {
    @Test void deterministicRequestRoundTripAndMalformedDataRejection() {
        var request = new CampaignTimeSkipState(CampaignTimeSkipState.Mode.MINUTES, 8, 608, 1, 2, 7,
                "IN_TRANSIT", 8, 1, Map.of("mount:sensor", .9), 42);
        byte[] bytes = CampaignTimeSkipPersistenceCodec.encode(request);
        assertEquals(request, CampaignTimeSkipPersistenceCodec.decode(bytes));
        assertArrayEquals(bytes, CampaignTimeSkipPersistenceCodec.encode(CampaignTimeSkipPersistenceCodec.decode(bytes)));
        assertThrows(IllegalArgumentException.class, () -> CampaignTimeSkipPersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> CampaignTimeSkipPersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        byte[] badMode = bytes.clone(); badMode[4] = 127;
        assertThrows(IllegalArgumentException.class, () -> CampaignTimeSkipPersistenceCodec.decode(badMode));
        assertThrows(IllegalArgumentException.class, () -> new CampaignTimeSkipState(CampaignTimeSkipState.Mode.EVENT,
                8, 100, 0, 2, 7, "IN_TRANSIT", 1, 1, Map.of(), 0));
    }
}
