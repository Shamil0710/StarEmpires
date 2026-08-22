package com.spacesim.world;

import com.spacesim.world.FactionLivingActorState.EventWakeup;
import com.spacesim.world.FactionLivingActorState.WakeupReason;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactionLivingActorStateCodecTest {

    @Test
    void checkpointBytesAreStableAcrossFactionAndWakeupInputOrdering() {
        EventWakeup late = new EventWakeup(WakeupReason.TREATY_CHANGED, "event.z", 30L, 40L);
        EventWakeup early = new EventWakeup(WakeupReason.LOSS_REPORTED, "event.a", 20L, 25L);
        FactionLivingActorState alpha = new FactionLivingActorState(
                "faction.alpha", 100L, 120L, 50L, 2L, List.of(late, early));
        FactionLivingActorState bravo = FactionLivingActorState.initial("faction.bravo", 80L);

        byte[] left = FactionLivingActorStateCodec.encode(List.of(bravo, alpha));
        byte[] right = FactionLivingActorStateCodec.encode(List.of(alpha, bravo));

        assertArrayEquals(left, right);
        assertEquals(List.of(alpha, bravo), FactionLivingActorStateCodec.decode(left));
    }

    @Test
    void malformedOrDuplicateCheckpointStateFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> FactionLivingActorStateCodec.decode(
                "unknown-version\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> FactionLivingActorStateCodec.encode(List.of(
                FactionLivingActorState.initial("faction.alpha", 10L),
                FactionLivingActorState.initial("faction.alpha", 20L))));
    }

    @Test
    void wakeupSourceIdentityCannotBeDuplicatedInsideOneActor() {
        EventWakeup first = new EventWakeup(WakeupReason.ARRIVAL, "event.same", 10L, 10L);
        EventWakeup duplicate = new EventWakeup(WakeupReason.PROJECT_COMPLETED, "event.same", 12L, 12L);

        assertThrows(IllegalArgumentException.class, () -> new FactionLivingActorState(
                "faction.alpha", 20L, 0L, -1L, 0L, List.of(first, duplicate)));
    }
}
