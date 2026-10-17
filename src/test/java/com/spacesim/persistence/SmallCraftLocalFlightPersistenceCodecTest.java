package com.spacesim.persistence;

import com.spacesim.world.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmallCraftLocalFlightPersistenceCodecTest {
    @Test void exactFarCoordinatesVelocityIdentitiesAndWatermarkRoundTripWithoutMaterialization() {
        var state = state(); var payload = SmallCraftLocalFlightPersistenceCodec.encode(state);
        var restored = SmallCraftLocalFlightPersistenceCodec.decode(payload);
        assertEquals(state, restored); assertArrayEquals(payload, SmallCraftLocalFlightPersistenceCodec.encode(restored));
        assertEquals(SmallCraftLocalFlightState.empty(), SmallCraftLocalFlightPersistenceCodec.decode(
                SmallCraftLocalFlightPersistenceCodec.encode(SmallCraftLocalFlightState.empty())));
    }

    @Test void corruptCountsVersionsTruncationTrailingDataAndNonfiniteVelocityFailClosed() {
        byte[] payload = SmallCraftLocalFlightPersistenceCodec.encode(state());
        for (int length : new int[]{0, 1, 7, 15, payload.length - 1}) {
            byte[] truncated = Arrays.copyOf(payload, length);
            assertThrows(IllegalArgumentException.class, () -> SmallCraftLocalFlightPersistenceCodec.decode(truncated));
        }
        assertThrows(IllegalArgumentException.class, () -> SmallCraftLocalFlightPersistenceCodec.decode(Arrays.copyOf(payload, payload.length + 1)));
        byte[] version = payload.clone(); ByteBuffer.wrap(version).putInt(4, 2);
        assertThrows(IllegalArgumentException.class, () -> SmallCraftLocalFlightPersistenceCodec.decode(version));
        for (int count : new int[]{-1, SmallCraftLocalFlightState.MAX_FLIGHTS + 1, SmallCraftLocalFlightState.MAX_FLIGHTS}) {
            byte[] bad = payload.clone(); ByteBuffer.wrap(bad).putInt(16, count);
            assertThrows(IllegalArgumentException.class, () -> SmallCraftLocalFlightPersistenceCodec.decode(bad));
        }
        byte[] velocity = payload.clone(); ByteBuffer.wrap(velocity).putDouble(payload.length - 24, Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> SmallCraftLocalFlightPersistenceCodec.decode(velocity));
        var flight = state().flights().get(0);
        assertThrows(IllegalArgumentException.class, () -> new SmallCraftLocalFlightState(10, List.of(flight, flight)));
        assertThrows(IllegalArgumentException.class, () -> new SmallCraftLocalFlightState(1, List.of(flight)));
    }

    private static SmallCraftLocalFlightState state() {
        return new SmallCraftLocalFlightState(10, List.of(new SmallCraftLocalFlightState.Flight(new SmallCraftId(3),
                new StarSystemId(16), "carrier.original", 4, new LocalPhysicalKinematics(
                        new LocalPhysicalPosition(4_000_000_000L, -9_000_000_000L, 25, -72), 123, -8), 2)));
    }
}
