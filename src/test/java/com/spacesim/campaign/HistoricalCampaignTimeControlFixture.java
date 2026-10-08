package com.spacesim.campaign;

import com.spacesim.persistence.CampaignTimeSkipPersistenceCodec;
import com.spacesim.simulation.CampaignTimeSkipState;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Removes only the canonical idle v18 suffix when constructing an older native test envelope. */
final class HistoricalCampaignTimeControlFixture {
    private HistoricalCampaignTimeControlFixture() { }
    static byte[] withoutIdleTimeControl(byte[] bytes) {
        byte[] idle = CampaignTimeSkipPersistenceCodec.encode(CampaignTimeSkipState.idle());
        int boundary = bytes.length - idle.length - 4;
        if (boundary < 0 || ByteBuffer.wrap(bytes).getInt(boundary) != idle.length
                || !Arrays.equals(idle, Arrays.copyOfRange(bytes, boundary + 4, bytes.length)))
            throw new AssertionError("Historical fixture cannot discard active time control");
        return Arrays.copyOf(bytes, boundary);
    }
}
