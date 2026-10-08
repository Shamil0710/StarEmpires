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
        var wrongVersion = bytes.clone(); ByteBuffer.wrap(wrongVersion).putInt(3);
        assertThrows(IllegalArgumentException.class, () -> FinishedProductTransferQueuePersistenceCodec.decode(wrongVersion));
        var oversizedCount = bytes.clone(); ByteBuffer.wrap(oversizedCount).putInt(12, State.CAPACITY + 1);
        assertThrows(IllegalArgumentException.class, () -> FinishedProductTransferQueuePersistenceCodec.decode(oversizedCount));
        var futureWork = bytes.clone(); ByteBuffer.wrap(futureWork).putLong(4, 1);
        assertThrows(IllegalArgumentException.class, () -> FinishedProductTransferQueuePersistenceCodec.decode(futureWork));
    }

    @Test void preservesPurchaseAndReadsLegacyWorkWithoutInventingMoney() throws Exception {
        String product = Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
        long fee = com.spacesim.economy.FinishedProductPurchasePayment.quote(product, 1);
        var payment = new com.spacesim.economy.FinishedProductPurchasePayment("faction.stellar_empire", fee);
        var order = new Order("paid", new FleetId(7), "station", "freight-hold:7", product, 1, 2, 1, payment);
        var state = new State(4, List.of(order));
        assertEquals(state, FinishedProductTransferQueuePersistenceCodec.decode(FinishedProductTransferQueuePersistenceCodec.encode(state)));
        assertThrows(IllegalArgumentException.class, () -> new Order("bad", new FleetId(7), "station", "freight-hold:7",
                product, 1, 2, 1, new com.spacesim.economy.FinishedProductPurchasePayment(payment.sellerFactionId(), fee - 1)));
        assertThrows(IllegalArgumentException.class, () -> new Order("bad", new FleetId(7), "freight-hold:7", "station", product, 1, 2, 1, payment));
        var buffer = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(buffer)) {
            out.writeInt(1); out.writeLong(4); out.writeInt(1);
            out.writeUTF(order.orderId()); out.writeLong(7); out.writeUTF("station"); out.writeUTF("freight-hold:7");
            out.writeUTF(product); out.writeInt(1); out.writeLong(2); out.writeDouble(1);
        }
        var legacy = FinishedProductTransferQueuePersistenceCodec.decode(buffer.toByteArray());
        assertNull(legacy.orders().get(0).purchasePayment());
        assertEquals(new Order("paid", new FleetId(7), "station", "freight-hold:7", product, 1, 2, 1), legacy.orders().get(0));
    }
}
