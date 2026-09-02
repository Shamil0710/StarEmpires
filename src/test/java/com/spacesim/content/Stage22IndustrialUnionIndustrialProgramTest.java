package com.spacesim.content;

import com.spacesim.content.Stage22IndustrialUnionProductionState.YardSeriesState;
import com.spacesim.persistence.Stage22IndustrialUnionProductionStateCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22IndustrialUnionIndustrialProgramTest {
    private static final String FINGERPRINT = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void commonalityIsEarnedByRepeatedSeriesCompletionsAndFeedsStage18ProfileGrammar() {
        YardSeriesState yard = Stage22IndustrialUnionProductionState.unqualifiedYard(
                Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        yard = payAndCompleteRetool(yard, "ship_family.industrial_union.corvette");

        var base = Stage22CommonManufacturingProfiles.definitions().get(0);
        var cold = Stage22IndustrialUnionIndustrialProgram.modifierFor(
                yard, "ship_family.industrial_union.corvette");
        assertFalse(cold.steadySeries());
        var coldProfile = Stage22IndustrialUnionIndustrialProgram.deriveProfile(
                base, "manufacturing.profile.industrial_union_cold_series", cold);
        assertTrue(coldProfile.workSecondsPerOutputKg() < base.workSecondsPerOutputKg());
        assertEquals(base.inputs(), coldProfile.inputs());

        for (int index = 0; index < 3; index++) {
            yard = Stage22IndustrialUnionIndustrialProgram.recordCompletedUnit(
                    yard, "ship_family.industrial_union.corvette");
        }
        var steady = Stage22IndustrialUnionIndustrialProgram.modifierFor(
                yard, "ship_family.industrial_union.frigate");
        assertTrue(steady.steadySeries());
        assertTrue(steady.workMultiplier() < cold.workMultiplier());
        assertTrue(steady.energyMultiplier() < cold.energyMultiplier());
    }

    @Test
    void abruptSeriesChangeFailsClosedUntilPositiveRetoolWorkAndEnergyArePaid() {
        YardSeriesState yard = Stage22IndustrialUnionProductionState.unqualifiedYard(
                Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        yard = payAndCompleteRetool(yard, "ship_family.industrial_union.corvette");
        YardSeriesState pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                yard, "ship_family.industrial_union.freight");

        assertTrue(pending.retooling());
        assertTrue(pending.retoolWorkRemainingSeconds() > 0L);
        assertTrue(pending.retoolEnergyRemainingJ() > 0L);
        assertThrows(IllegalStateException.class, () -> Stage22IndustrialUnionIndustrialProgram.modifierFor(
                pending, "ship_family.industrial_union.freight"));
        assertThrows(IllegalStateException.class, () -> Stage22IndustrialUnionIndustrialProgram.completeRetool(pending));

        YardSeriesState almost = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                pending, pending.retoolWorkRemainingSeconds(), pending.retoolEnergyRemainingJ() - 1L);
        assertThrows(IllegalStateException.class, () -> Stage22IndustrialUnionIndustrialProgram.completeRetool(almost));
        YardSeriesState paid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(almost, 0L, 1L);
        YardSeriesState changed = Stage22IndustrialUnionIndustrialProgram.completeRetool(paid);
        assertEquals("assembly_series.industrial_union.logistics", changed.activeSeriesId());
        assertEquals(0, changed.commonalityStreak());
    }

    @Test
    void productionStateRoundTripPreservesCommonalityAndMidRetoolDebtByteStably() {
        YardSeriesState first = Stage22IndustrialUnionProductionState.unqualifiedYard(
                Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        first = payAndCompleteRetool(first, "ship_family.industrial_union.corvette");
        first = Stage22IndustrialUnionIndustrialProgram.recordCompletedUnit(
                Stage22IndustrialUnionIndustrialProgram.recordCompletedUnit(
                        first, "ship_family.industrial_union.corvette"),
                "ship_family.industrial_union.frigate");
        first = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                first, "ship_family.industrial_union.freight");
        first = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(first, 12_345L, 67_890L);

        var state = new Stage22IndustrialUnionProductionState(
                1,
                Stage22IndustrialUnionProductionState.STABLE_FACTION_ID,
                FINGERPRINT,
                7L,
                List.of(first));
        byte[] encoded = Stage22IndustrialUnionProductionStateCodec.encode(state);
        var decoded = Stage22IndustrialUnionProductionStateCodec.decode(encoded);
        assertEquals(state.stableFactionId(), decoded.stableFactionId());
        assertEquals(state.packageFingerprint(), decoded.packageFingerprint());
        assertEquals(state.sequence(), decoded.sequence());
        assertEquals(state.yards(), decoded.yards());
        assertArrayEquals(encoded, Stage22IndustrialUnionProductionStateCodec.encode(decoded));
    }

    @Test
    void filesystemRoundTripPreservesState(@TempDir Path directory) throws Exception {
        var state = new Stage22IndustrialUnionProductionState(
                1,
                Stage22IndustrialUnionProductionState.STABLE_FACTION_ID,
                FINGERPRINT,
                3L,
                List.of(Stage22IndustrialUnionProductionState.unqualifiedYard(
                        Stage22IndustrialUnionIndustrialProgram.YARD_ID)));
        Path file = directory.resolve("union-production.bin");
        Stage22IndustrialUnionProductionStateCodec.write(file, state);
        var loaded = Stage22IndustrialUnionProductionStateCodec.read(file);
        assertEquals(state.stableFactionId(), loaded.stableFactionId());
        assertEquals(state.packageFingerprint(), loaded.packageFingerprint());
        assertEquals(state.sequence(), loaded.sequence());
        assertEquals(state.yards(), loaded.yards());
    }

    @Test
    void codecRejectsCorruptTruncatedFutureAndTrailingState() {
        var state = new Stage22IndustrialUnionProductionState(
                1,
                Stage22IndustrialUnionProductionState.STABLE_FACTION_ID,
                FINGERPRINT,
                0L,
                List.of(Stage22IndustrialUnionProductionState.unqualifiedYard(
                        Stage22IndustrialUnionIndustrialProgram.YARD_ID)));
        byte[] valid = Stage22IndustrialUnionProductionStateCodec.encode(state);

        byte[] corruptMagic = valid.clone();
        corruptMagic[0] ^= 0x01;
        assertThrows(IllegalArgumentException.class,
                () -> Stage22IndustrialUnionProductionStateCodec.decode(corruptMagic));
        assertThrows(IllegalArgumentException.class,
                () -> Stage22IndustrialUnionProductionStateCodec.decode(Arrays.copyOf(valid, valid.length - 1)));
        byte[] future = valid.clone();
        future[7] = 2;
        assertThrows(IllegalArgumentException.class,
                () -> Stage22IndustrialUnionProductionStateCodec.decode(future));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class,
                () -> Stage22IndustrialUnionProductionStateCodec.decode(trailing));
    }

    @Test
    void defaultProgramCoversNineFamiliesAndExistingProfileAuthority() {
        var report = Stage22IndustrialUnionIndustrialProgram.validateDefault();
        assertEquals(3, report.seriesCount());
        assertEquals(9, report.coveredFamilyCount());
        assertEquals(12L, report.sharedCoreAssemblyReferences());
        assertTrue(report.retoolWorkSeconds() > 0L);
        assertTrue(report.retoolEnergyJ() > 0L);
    }

    private static YardSeriesState payAndCompleteRetool(YardSeriesState state, String familyId) {
        YardSeriesState pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(state, familyId);
        YardSeriesState paid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                pending, pending.retoolWorkRemainingSeconds(), pending.retoolEnergyRemainingJ());
        return Stage22IndustrialUnionIndustrialProgram.completeRetool(paid);
    }
}
