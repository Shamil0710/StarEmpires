package com.spacesim.world;

import com.spacesim.world.TerritorialTransitionState.OccupationState;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerritorialTransitionStateCodecTest {
    @Test
    void roundTripPreservesExactProgressDeadlineClaimProvenanceAndEstablishedControlDeterministically() {
        TerritorialTransitionState state = new TerritorialTransitionState(List.of(
                new OccupationState("faction.b", new StarSystemId(9L), 12L, 100L, 180L, 80L, 170L,
                        false, false, OccupationStatus.OCCUPYING),
                new OccupationState("faction.a", new StarSystemId(3L), 11L, 90L, 190L, 300L, -1L,
                        true, true, OccupationStatus.SECURED)));

        byte[] first = TerritorialTransitionStateCodec.encode(state);
        TerritorialTransitionState restored = TerritorialTransitionStateCodec.decode(first);
        byte[] second = TerritorialTransitionStateCodec.encode(restored);

        assertEquals(state, restored);
        assertArrayEquals(first, second);
        assertEquals(80L, restored.occupationFor("faction.b", new StarSystemId(9L)).orElseThrow().securedTicks());
        assertEquals(170L,
                restored.occupationFor("faction.b", new StarSystemId(9L)).orElseThrow().unsupportedSinceTick());
        assertTrue(restored.occupationFor("faction.a", new StarSystemId(3L)).orElseThrow().claimCreatedByOccupation());
        assertTrue(restored.occupationFor("faction.a", new StarSystemId(3L)).orElseThrow().controlEverEstablished());
    }

    @Test
    void futureCorruptTruncatedAndTrailingPayloadsFailClosed() {
        byte[] valid = TerritorialTransitionStateCodec.encode(TerritorialTransitionState.empty());

        byte[] future = valid.clone();
        ByteBuffer.wrap(future).putInt(4, 2);
        assertThrows(IllegalArgumentException.class, () -> TerritorialTransitionStateCodec.decode(future));

        byte[] corrupt = valid.clone();
        corrupt[0] ^= 0x7f;
        assertThrows(IllegalArgumentException.class, () -> TerritorialTransitionStateCodec.decode(corrupt));

        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        assertThrows(IllegalArgumentException.class, () -> TerritorialTransitionStateCodec.decode(truncated));

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> TerritorialTransitionStateCodec.decode(trailing));
    }

    @Test
    void duplicateFactionSystemTransitionIsRejected() {
        OccupationState first = new OccupationState(
                "faction.same", new StarSystemId(4L), 1L, 0L, 0L, 0L, -1L,
                false, false, OccupationStatus.OCCUPYING);
        OccupationState second = new OccupationState(
                "faction.same", new StarSystemId(4L), 2L, 10L, 10L, 0L, -1L,
                false, false, OccupationStatus.OCCUPYING);
        assertThrows(IllegalArgumentException.class, () -> new TerritorialTransitionState(List.of(first, second)));
    }

    @Test
    void liberationCannotBeInventedWithoutPriorEstablishedControl() {
        assertThrows(IllegalArgumentException.class, () -> new OccupationState(
                "faction.same", new StarSystemId(4L), 1L, 0L, 10L, 300L, -1L,
                false, false, OccupationStatus.LIBERATED));
    }
}
