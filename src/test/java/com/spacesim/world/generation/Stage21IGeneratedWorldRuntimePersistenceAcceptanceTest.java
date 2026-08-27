package com.spacesim.world.generation;

import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21IGeneratedWorldRuntimePersistenceAcceptanceTest {

    @Test
    void nativeFinalCheckpointRoundTripsByteStablyWithoutInventingMigration() {
        var stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime().captureState();
        var migrated = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(stage20));
        var nativeState = Stage21IGeneratedWorldRuntimePersistentState.compose(migrated.stage21HRuntime());

        byte[] encoded = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(nativeState);
        var decoded = Stage21IGeneratedWorldRuntimePersistenceCodec.decode(encoded);
        var decodedViaCompatibilityEntryPoint =
                Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(encoded);

        assertEquals(nativeState, decoded);
        assertEquals(nativeState, decodedViaCompatibilityEntryPoint);
        assertArrayEquals(encoded, Stage21IGeneratedWorldRuntimePersistenceCodec.encode(decoded));
        assertFalse(decoded.migrationProvenance().migrated());
        assertEquals("stage21h.native", decoded.migrationProvenance().sourceFormat());
        assertEquals(
                Stage21IGeneratedWorldRuntimePersistentState.authoritativeWorldTick(decoded.stage21HRuntime()),
                decoded.migrationProvenance().migrationTick());
    }

    @Test
    void supportedStage20PointFiveSaveMigratesWithoutRewritingSavedWorldAuthority() {
        var stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 21L).runtime().captureState();
        byte[] legacyBytes = Stage20GeneratedWorldRuntimePersistenceCodec.encode(stage20);

        var migrated = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(legacyBytes);
        var embeddedStage20 = migrated.stage21HRuntime().stage21GRuntime().stage21FRuntime()
                .stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime()
                .stage21ARuntime().stage20Runtime();

        assertTrue(migrated.migrationProvenance().migrated());
        assertEquals("stage20.5.v2", migrated.migrationProvenance().sourceFormat());
        assertArrayEquals(
                legacyBytes,
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(embeddedStage20));
        assertEquals(stage20.worldState().factions().stream()
                        .map(faction -> faction.factionContentId())
                        .distinct()
                        .sorted()
                        .toList(),
                migrated.stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()
                        .stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime()
                        .livingActors().stream()
                        .map(actor -> actor.factionContentId())
                        .sorted()
                        .toList());

        byte[] finalBytes = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(migrated);
        var restored = Stage21IGeneratedWorldRuntimePersistenceCodec.decode(finalBytes);
        assertEquals(migrated, restored);
        assertArrayEquals(finalBytes, Stage21IGeneratedWorldRuntimePersistenceCodec.encode(restored));
    }

    @Test
    void nativeDecoderFailsClosedOnTruncationTrailingBytesAndCorruptMagic() {
        var stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 22L).runtime().captureState();
        var migrated = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(stage20));
        byte[] nativeBytes = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(
                Stage21IGeneratedWorldRuntimePersistentState.compose(migrated.stage21HRuntime()));

        byte[] truncated = Arrays.copyOf(nativeBytes, nativeBytes.length - 1);
        byte[] trailing = Arrays.copyOf(nativeBytes, nativeBytes.length + 1);
        trailing[trailing.length - 1] = 1;
        byte[] corruptMagic = nativeBytes.clone();
        corruptMagic[0] ^= 0x01;

        assertThrows(IllegalArgumentException.class,
                () -> Stage21IGeneratedWorldRuntimePersistenceCodec.decode(truncated));
        assertThrows(IllegalArgumentException.class,
                () -> Stage21IGeneratedWorldRuntimePersistenceCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> Stage21IGeneratedWorldRuntimePersistenceCodec.decode(corruptMagic));
        assertThrows(IllegalArgumentException.class,
                () -> Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(corruptMagic));
        assertThrows(IllegalArgumentException.class,
                () -> Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(new byte[0]));
    }
}
