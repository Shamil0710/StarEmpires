package com.spacesim.persistence;

import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.economy.FinishedProductTransferWorkQueue.Order;
import com.spacesim.economy.FinishedProductTransferWorkQueue.State;
import com.spacesim.world.FleetId;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinishedProductTransferQueuePersistenceCodecTest {
    @Test void preservesWorkAndRejectsInvalidFramingBeforeAllocatingRows() {
        var state = new State(4, List.of(new Order("order", new FleetId(7), "station",
                "freight-hold:7", Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID, 1, 2, 1)));
        byte[] bytes = FinishedProductTransferQueuePersistenceCodec.encode(state);
        assertEquals(state, FinishedProductTransferQueuePersistenceCodec.decode(bytes));
        for (int length : new int[]{0, 15, bytes.length - 1})
            assertThrows(IllegalArgumentException.class, () -> FinishedProductTransferQueuePersistenceCodec.decode(Arrays.copyOf(bytes, length)));
        assertThrows(IllegalArgumentException.class, () -> FinishedProductTransferQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        var wrongVersion = bytes.clone(); ByteBuffer.wrap(wrongVersion).putInt(2);
        assertThrows(IllegalArgumentException.class, () -> FinishedProductTransferQueuePersistenceCodec.decode(wrongVersion));
        var oversizedCount = bytes.clone(); ByteBuffer.wrap(oversizedCount).putInt(12, State.CAPACITY + 1);
        assertThrows(IllegalArgumentException.class, () -> FinishedProductTransferQueuePersistenceCodec.decode(oversizedCount));
        var futureWork = bytes.clone(); ByteBuffer.wrap(futureWork).putLong(4, 1);
        assertThrows(IllegalArgumentException.class, () -> FinishedProductTransferQueuePersistenceCodec.decode(futureWork));
    }
}
