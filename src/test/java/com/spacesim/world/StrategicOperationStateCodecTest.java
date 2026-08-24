package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.TacticalEncounterState;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class StrategicOperationStateCodecTest {
    @Test
    void roundTripPreservesMidOperationContactAndEncounterExactly() {
        ContactState contact = new ContactState(
                new FleetId(900L), new StarSystemId(7L), ObservationChannel.LOCAL_SENSOR_REPORT,
                "track:900:42", 42L, 52L);
        TacticalEncounterState encounter = new TacticalEncounterState(
                77L, new FleetId(900L), new StarSystemId(7L), 45L, -1L);
        OperationState operation = new OperationState(
                3L,
                OperationType.INTERCEPTION,
                11L,
                12L,
                2,
                List.of(new FleetId(100L), new FleetId(101L)),
                new StarSystemId(5L),
                new StarSystemId(7L),
                "system:7",
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(4_000, 2_000, 300L),
                new WithdrawalPolicy(new StarSystemId(5L), 2_500, true, true),
                OperationStatus.ENGAGED,
                40L,
                45L,
                -1L,
                contact,
                encounter);
        StrategicOperationState expected = new StrategicOperationState(4L, List.of(operation));

        byte[] first = StrategicOperationStateCodec.encode(expected);
        StrategicOperationState decoded = StrategicOperationStateCodec.decode(first);
        byte[] second = StrategicOperationStateCodec.encode(decoded);

        assertEquals(expected, decoded);
        assertEquals(Arrays.toString(first), Arrays.toString(second));
    }

    @Test
    void corruptFutureAndTrailingPayloadsFailClosed() {
        StrategicOperationState state = StrategicOperationState.empty();
        byte[] encoded = StrategicOperationStateCodec.encode(state);

        byte[] badMagic = encoded.clone();
        badMagic[0] ^= 0x01;
        assertThrows(IllegalArgumentException.class, () -> StrategicOperationStateCodec.decode(badMagic));

        byte[] futureVersion = encoded.clone();
        futureVersion[7] = 2;
        assertThrows(IllegalArgumentException.class, () -> StrategicOperationStateCodec.decode(futureVersion));

        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IllegalArgumentException.class, () -> StrategicOperationStateCodec.decode(trailing));
    }
}
