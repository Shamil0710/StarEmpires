package com.spacesim.world.generation;

import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.DiplomaticLifecycleService.DiplomaticSituation;
import com.spacesim.world.DiplomaticLifecycleService.StrategicOutcome;
import com.spacesim.world.DiplomaticLifecycleState.CrisisEscalation;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Stage21CRepresentativeOutcomeAcceptanceTest {
    private static final List<DiplomaticSituation> FIXED_REPRESENTATIVE_PROFILES = List.of(
            new DiplomaticSituation(35, 8_000, 1_000, 1_000, CrisisEscalation.NEGOTIATION, false),
            new DiplomaticSituation(-10, 1_000, 6_000, 8_000, CrisisEscalation.PRESSURE, false),
            new DiplomaticSituation(-25, 5_000, 5_000, 4_500, CrisisEscalation.ULTIMATUM, true),
            new DiplomaticSituation(-70, 1_000, 9_000, 1_000, CrisisEscalation.WAR_AUTHORIZED, false));

    @Test
    void fixedRepresentativeSeedCorpusExercisesTradeDeterrenceSettlementAndWar() {
        EnumSet<StrategicOutcome> outcomes = EnumSet.noneOf(StrategicOutcome.class);
        List<Long> seeds = Stage20RepresentativeSeedCorpus.seeds();

        for (int index = 0; index < seeds.size(); index++) {
            DiplomaticSituation profile = FIXED_REPRESENTATIVE_PROFILES.get(
                    index % FIXED_REPRESENTATIVE_PROFILES.size());
            outcomes.add(DiplomaticLifecycleService.selectOutcome(profile, seeds.get(index)));
        }

        assertEquals(EnumSet.allOf(StrategicOutcome.class), outcomes);
    }

    @Test
    void boundedTieBreakCanChooseOnlyBetweenNonWarAlternatives() {
        DiplomaticSituation exactNonWarTie = new DiplomaticSituation(
                0,
                4_000,
                4_000,
                0,
                CrisisEscalation.NEGOTIATION,
                false);
        for (long seed : Stage20RepresentativeSeedCorpus.seeds()) {
            assertFalse(DiplomaticLifecycleService.selectOutcome(exactNonWarTie, seed) == StrategicOutcome.WAR);
        }

        DiplomaticSituation causalWar = FIXED_REPRESENTATIVE_PROFILES.get(3);
        for (long seed : Stage20RepresentativeSeedCorpus.seeds()) {
            assertEquals(StrategicOutcome.WAR, DiplomaticLifecycleService.selectOutcome(causalWar, seed));
        }
    }
}