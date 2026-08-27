package com.spacesim.world.generation;

import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21FGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21GGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistenceCodec;
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
        var embeddedStage20 = stage20From(migrated);

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
    void everySupportedStage21SourceMigratesAndPreservesItsCompleteSourceEnvelope() {
        var stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 23L).runtime().captureState();
        var complete = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(stage20));
        var h = complete.stage21HRuntime();
        var g = h.stage21GRuntime();
        var f = g.stage21FRuntime();
        var e = f.stage21ERuntime();
        var d = e.stage21DRuntime();
        var c = d.stage21CRuntime();
        var b = c.stage21BRuntime();
        var a = b.stage21ARuntime();

        byte[] aBytes = Stage21AGeneratedWorldRuntimePersistenceCodec.encode(a);
        var fromA = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(aBytes);
        assertMigration(fromA, "stage21a.v1");
        assertArrayEquals(aBytes, Stage21AGeneratedWorldRuntimePersistenceCodec.encode(
                stage20Container(fromA).stage21ARuntime()));

        byte[] bBytes = Stage21BGeneratedWorldRuntimePersistenceCodec.encode(b);
        var fromB = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(bBytes);
        assertMigration(fromB, "stage21b.v5");
        assertArrayEquals(bBytes, Stage21BGeneratedWorldRuntimePersistenceCodec.encode(
                stage20Container(fromB)));

        byte[] cBytes = Stage21CGeneratedWorldRuntimePersistenceCodec.encode(c);
        var fromC = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(cBytes);
        assertMigration(fromC, "stage21c.v6");
        assertArrayEquals(cBytes, Stage21CGeneratedWorldRuntimePersistenceCodec.encode(
                fromC.stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()
                        .stage21DRuntime().stage21CRuntime()));

        byte[] dBytes = Stage21DGeneratedWorldRuntimePersistenceCodec.encode(d);
        var fromD = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(dBytes);
        assertMigration(fromD, "stage21d.v7");
        assertArrayEquals(dBytes, Stage21DGeneratedWorldRuntimePersistenceCodec.encode(
                fromD.stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()
                        .stage21DRuntime()));

        byte[] eBytes = Stage21EGeneratedWorldRuntimePersistenceCodec.encode(e);
        var fromE = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(eBytes);
        assertMigration(fromE, "stage21e.v8");
        assertArrayEquals(eBytes, Stage21EGeneratedWorldRuntimePersistenceCodec.encode(
                fromE.stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()));

        byte[] fBytes = Stage21FGeneratedWorldRuntimePersistenceCodec.encode(f);
        var fromF = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(fBytes);
        assertMigration(fromF, "stage21f.v9");
        assertArrayEquals(fBytes, Stage21FGeneratedWorldRuntimePersistenceCodec.encode(
                fromF.stage21HRuntime().stage21GRuntime().stage21FRuntime()));

        byte[] gBytes = Stage21GGeneratedWorldRuntimePersistenceCodec.encode(g);
        var fromG = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(gBytes);
        assertMigration(fromG, "stage21g.v10");
        assertArrayEquals(gBytes, Stage21GGeneratedWorldRuntimePersistenceCodec.encode(
                fromG.stage21HRuntime().stage21GRuntime()));

        byte[] hBytes = Stage21HGeneratedWorldRuntimePersistenceCodec.encode(h);
        var fromH = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(hBytes);
        assertMigration(fromH, "stage21h.v11");
        assertArrayEquals(hBytes, Stage21HGeneratedWorldRuntimePersistenceCodec.encode(fromH.stage21HRuntime()));
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

    private static void assertMigration(Stage21IGeneratedWorldRuntimePersistentState migrated, String sourceFormat) {
        assertTrue(migrated.migrationProvenance().migrated());
        assertEquals(sourceFormat, migrated.migrationProvenance().sourceFormat());
    }

    private static com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState stage20Container(
            Stage21IGeneratedWorldRuntimePersistentState state) {
        return state.stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()
                .stage21DRuntime().stage21CRuntime().stage21BRuntime();
    }

    private static com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState stage20From(
            Stage21IGeneratedWorldRuntimePersistentState state) {
        return stage20Container(state).stage21ARuntime().stage20Runtime();
    }
}
