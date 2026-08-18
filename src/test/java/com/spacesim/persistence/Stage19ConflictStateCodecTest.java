package com.spacesim.persistence;

import com.spacesim.persistence.Stage19ConflictState.ConflictSnapshot;
import com.spacesim.persistence.Stage19ConflictState.ObjectiveSnapshot;
import com.spacesim.warfare.StrategicWarPolicyService.EscalationLevel;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveEvidence;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage19ConflictStateCodecTest {
    @Test
    void roundTripIsDeterministicAndCanonicalizesConflictAndObjectiveOrdering() {
        ConflictSnapshot beta = conflict(
                "conflict.beta",
                List.of(
                        objective("objective.zeta"),
                        objective("objective.alpha")));
        ConflictSnapshot alpha = conflict(
                "conflict.alpha",
                List.of(objective("objective.gamma")));
        Stage19ConflictState state = new Stage19ConflictState(
                Stage19ConflictState.CURRENT_VERSION,
                120L,
                List.of(beta, alpha));

        byte[] first = Stage19ConflictStateCodec.encode(state);
        Stage19ConflictState decoded = Stage19ConflictStateCodec.decode(first);
        byte[] second = Stage19ConflictStateCodec.encode(decoded);

        assertEquals(List.of("conflict.alpha", "conflict.beta"),
                decoded.conflicts().stream().map(ConflictSnapshot::conflictId).toList());
        assertEquals(List.of("objective.alpha", "objective.zeta"),
                decoded.conflicts().get(1).objectives().stream().map(ObjectiveSnapshot::id).toList());
        assertEquals(state, decoded);
        assertArrayEquals(first, second);
    }

    @Test
    void trailingBytesAreRejected() {
        byte[] encoded = Stage19ConflictStateCodec.encode(new Stage19ConflictState(
                Stage19ConflictState.CURRENT_VERSION,
                5L,
                List.of(conflict("conflict.one", List.of(objective("objective.one"))))));
        byte[] withTrailingByte = Arrays.copyOf(encoded, encoded.length + 1);
        withTrailingByte[withTrailingByte.length - 1] = 1;

        assertThrows(IllegalArgumentException.class,
                () -> Stage19ConflictStateCodec.decode(withTrailingByte));
    }

    @Test
    void malformedOrTruncatedPayloadsAreRejected() {
        byte[] encoded = Stage19ConflictStateCodec.encode(new Stage19ConflictState(
                Stage19ConflictState.CURRENT_VERSION,
                5L,
                List.of(conflict("conflict.one", List.of(objective("objective.one"))))));

        assertThrows(IllegalArgumentException.class,
                () -> Stage19ConflictStateCodec.decode(Arrays.copyOf(encoded, encoded.length - 3)));
        byte[] wrongMagic = encoded.clone();
        wrongMagic[0] = 0;
        assertThrows(IllegalArgumentException.class,
                () -> Stage19ConflictStateCodec.decode(wrongMagic));
    }

    @Test
    void duplicateConflictIdentityIsRejectedBeforeEncoding() {
        ConflictSnapshot conflict = conflict("conflict.duplicate", List.of(objective("objective.one")));

        assertThrows(IllegalArgumentException.class, () -> new Stage19ConflictState(
                Stage19ConflictState.CURRENT_VERSION,
                10L,
                List.of(conflict, conflict)));
    }

    private static ConflictSnapshot conflict(String id, List<ObjectiveSnapshot> objectives) {
        return ConflictSnapshot.active(
                id,
                "faction.actor." + id,
                "faction.opponent." + id,
                EscalationLevel.CRISIS,
                objectives);
    }

    private static ObjectiveSnapshot objective(String id) {
        return new ObjectiveSnapshot(
                id,
                "subject." + id,
                true,
                ObjectiveEvidence.OBSERVED_UNMET);
    }
}
