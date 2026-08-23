package com.spacesim.world;

import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FleetCommandStateCodecTest {
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);
    private static final StarSystemId GAMMA = new StarSystemId(3L);

    @Test
    void roundTripPreservesCommandGroupsOrdersAndAllocatorWatermarks() {
        FleetCommandState state = sampleState();

        byte[] encoded = FleetCommandStateCodec.encode(state);
        FleetCommandState decoded = FleetCommandStateCodec.decode(encoded);

        assertEquals(state, decoded);
        assertArrayEquals(encoded, FleetCommandStateCodec.encode(decoded));
    }

    @Test
    void canonicalOrderingMakesEquivalentInputByteIdentical() {
        CommandGroupState first = group(1L, 101L, "First", ALPHA);
        CommandGroupState second = group(2L, 202L, "Second", BETA);
        FleetOrderState orderOne = order(1L, first.id(), OrderType.PATROL, ALPHA, BETA);
        FleetOrderState orderTwo = order(2L, second.id(), OrderType.RETURN, BETA, ALPHA);

        FleetCommandState forward = new FleetCommandState(3L, 3L,
                List.of(first, second), List.of(orderOne, orderTwo));
        FleetCommandState reversed = new FleetCommandState(3L, 3L,
                List.of(second, first), List.of(orderTwo, orderOne));

        assertEquals(forward, reversed);
        assertArrayEquals(FleetCommandStateCodec.encode(forward), FleetCommandStateCodec.encode(reversed));
    }

    @Test
    void duplicateFleetAssignmentAcrossCommandGroupsFailsClosed() {
        FleetId fleet = new FleetId(101L);
        CommandGroupState first = new CommandGroupState(1L, 1, "First", List.of(fleet), ALPHA,
                false, false, FleetReadinessState.FULL);
        CommandGroupState second = new CommandGroupState(2L, 1, "Second", List.of(fleet), ALPHA,
                false, false, FleetReadinessState.FULL);

        assertThrows(IllegalArgumentException.class,
                () -> new FleetCommandState(3L, 1L, List.of(first, second), List.of()));
    }

    @Test
    void staleAllocatorWatermarksAndUnaffiliatedGroupsFailClosed() {
        CommandGroupState group = group(2L, 101L, "Group", ALPHA);
        FleetOrderState order = order(4L, group.id(), OrderType.PATROL, ALPHA, BETA);

        assertThrows(IllegalArgumentException.class,
                () -> new FleetCommandState(2L, 5L, List.of(group), List.of(order)));
        assertThrows(IllegalArgumentException.class,
                () -> new FleetCommandState(3L, 4L, List.of(group), List.of(order)));
        assertThrows(IllegalArgumentException.class,
                () -> new CommandGroupState(1L, -1, "Unaffiliated", List.of(new FleetId(99L)), ALPHA,
                        false, false, FleetReadinessState.FULL));
    }

    @Test
    void corruptFutureTrailingAndTruncatedPayloadsFailClosed() {
        byte[] encoded = FleetCommandStateCodec.encode(sampleState());

        byte[] future = encoded.clone();
        ByteBuffer.wrap(future).putInt(4, 99);
        assertThrows(IllegalArgumentException.class, () -> FleetCommandStateCodec.decode(future));

        byte[] badMagic = encoded.clone();
        ByteBuffer.wrap(badMagic).putInt(0, 0x12345678);
        assertThrows(IllegalArgumentException.class, () -> FleetCommandStateCodec.decode(badMagic));

        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        trailing[trailing.length - 1] = 1;
        assertThrows(IllegalArgumentException.class, () -> FleetCommandStateCodec.decode(trailing));

        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        assertThrows(IllegalArgumentException.class, () -> FleetCommandStateCodec.decode(truncated));
    }

    private static FleetCommandState sampleState() {
        CommandGroupState first = group(1L, 101L, "Alpha Guard", ALPHA);
        CommandGroupState second = group(2L, 202L, "Beta Reserve", BETA);
        FleetOrderState firstOrder = new FleetOrderState(
                1L, first.id(), OrderType.REINFORCE, OrderSource.AI, GAMMA,
                List.of(ALPHA, BETA, GAMMA), 1, 100L, 150L, OrderStatus.ACTIVE);
        FleetOrderState secondOrder = new FleetOrderState(
                2L, second.id(), OrderType.REPAIR, OrderSource.PLAYER, BETA,
                List.of(BETA), 0, 120L, 160L, OrderStatus.SERVICE_PENDING);
        return new FleetCommandState(3L, 3L, List.of(second, first), List.of(secondOrder, firstOrder));
    }

    private static CommandGroupState group(long id, long fleetId, String name, StarSystemId home) {
        return new CommandGroupState(id, 1, name, List.of(new FleetId(fleetId)), home,
                false, false, 7_500);
    }

    private static FleetOrderState order(
            long id,
            long groupId,
            OrderType type,
            StarSystemId origin,
            StarSystemId destination) {
        return new FleetOrderState(id, groupId, type, OrderSource.PLAYER, destination,
                origin.equals(destination) ? List.of(origin) : List.of(origin, destination),
                0, 10L, 20L, OrderStatus.STAGING);
    }
}
