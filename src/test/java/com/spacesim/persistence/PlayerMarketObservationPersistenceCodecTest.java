package com.spacesim.persistence;

import com.spacesim.player.PlayerMarketObservationState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerMarketObservationPersistenceCodecTest {
    @Test
    void roundTripIsExactAndCanonical() {
        var state = PlayerMarketObservationState.empty()
                .record("hub.9", "commodity.feedstock.metallic_ore", 10, 48_001, 43_200)
                .record("hub.9", "commodity.feedstock.metallic_ore", 600, 47_123, 42_411)
                .record("hub.2", "commodity.material.structural_alloy", 3, 92_000, 82_800);
        byte[] bytes = PlayerMarketObservationPersistenceCodec.encode(state);
        assertEquals(state, PlayerMarketObservationPersistenceCodec.decode(bytes));
        assertArrayEquals(bytes, PlayerMarketObservationPersistenceCodec.encode(PlayerMarketObservationPersistenceCodec.decode(bytes)));
        assertEquals(PlayerMarketObservationState.empty(),
                PlayerMarketObservationPersistenceCodec.decode(PlayerMarketObservationPersistenceCodec.encode(PlayerMarketObservationState.empty())));
    }

    @Test
    void malformedPayloadsAreRejected() throws IOException {
        byte[] valid = PlayerMarketObservationPersistenceCodec.encode(
                PlayerMarketObservationState.empty().record("hub.9", "ore", 1, 10, 9));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerMarketObservationPersistenceCodec.decode(Arrays.copyOf(valid, valid.length + 1)), "trailing byte");
        assertThrows(IllegalArgumentException.class,
                () -> PlayerMarketObservationPersistenceCodec.decode(Arrays.copyOf(valid, valid.length - 3)), "truncated");
        assertThrows(IllegalArgumentException.class, () -> PlayerMarketObservationPersistenceCodec.decode(new byte[3]));
        // A non-positive price cannot be smuggled in through the payload.
        assertThrows(IllegalArgumentException.class, () -> PlayerMarketObservationPersistenceCodec.decode(payload(1, 0, 9)));
        // Ticks must increase inside a series.
        assertThrows(IllegalArgumentException.class, () -> PlayerMarketObservationPersistenceCodec.decode(payload(5, 10, 9, 5, 11, 10)));
    }

    private static byte[] payload(long... quoteFields) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(1);
            out.writeInt(1);
            out.writeUTF("hub.9");
            out.writeUTF("ore");
            out.writeInt(quoteFields.length / 3);
            for (long field : quoteFields) out.writeLong(field);
        }
        return bytes.toByteArray();
    }
}
