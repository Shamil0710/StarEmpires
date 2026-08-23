package com.spacesim.world.generation;

import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.DiplomaticLifecycleService.DiplomaticSituation;
import com.spacesim.world.DiplomaticLifecycleService.StrategicOutcome;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.DiplomaticLifecycleState.CrisisEscalation;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.RelationEvent;
import com.spacesim.world.DiplomaticLifecycleState.RelationFactor;
import com.spacesim.world.DiplomaticLifecycleState.RelationMemory;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Stage21CRepresentativeOutcomeAcceptanceTest {
    private enum RepresentativeHistory {
        TRADE(StrategicOutcome.TRADE),
        DETERRENCE(StrategicOutcome.DETERRENCE),
        NEGOTIATED_RESOLUTION(StrategicOutcome.NEGOTIATED_RESOLUTION),
        WAR(StrategicOutcome.WAR);

        private final StrategicOutcome expectedOutcome;

        RepresentativeHistory(StrategicOutcome expectedOutcome) {
            this.expectedOutcome = expectedOutcome;
        }
    }

    @Test
    void fixedGeneratedSeedCorpusExercisesDifferentPersistedPoliticalHistories() {
        EnumSet<StrategicOutcome> outcomes = EnumSet.noneOf(StrategicOutcome.class);
        List<Long> seeds = Stage20RepresentativeSeedCorpus.seeds();
        RepresentativeHistory[] histories = RepresentativeHistory.values();

        for (int index = 0; index < seeds.size(); index++) {
            long seed = seeds.get(index);
            RepresentativeHistory history = histories[index % histories.length];
            var generated = Stage20PlayableGeneratedWorldFactory.create(seed).runtime();
            var saved = generated.captureState();
            String first = saved.worldState().factions().get(0).factionContentId();
            String second = saved.worldState().factions().get(1).factionContentId();
            long now = generated.world().getAuthoritativeWorldTick();
            DiplomaticLifecycleService lifecycle = new DiplomaticLifecycleService(
                    generated.world(),
                    new Stage19ConflictRuntime(Stage19ConflictState.empty(now)),
                    DiplomaticLifecycleState.empty(now));

            DiplomaticSituation situation = materializeHistory(
                    lifecycle,
                    first,
                    second,
                    history,
                    now + 120L);
            StrategicOutcome outcome = DiplomaticLifecycleService.selectOutcome(situation, seed);

            assertEquals(history.expectedOutcome, outcome, "unexpected political outcome for generated seed " + seed);
            outcomes.add(outcome);
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

        DiplomaticSituation causalWar = new DiplomaticSituation(
                -70,
                1_000,
                9_000,
                1_000,
                CrisisEscalation.WAR_AUTHORIZED,
                false);
        for (long seed : Stage20RepresentativeSeedCorpus.seeds()) {
            assertEquals(StrategicOutcome.WAR, DiplomaticLifecycleService.selectOutcome(causalWar, seed));
        }
    }

    private static DiplomaticSituation materializeHistory(
            DiplomaticLifecycleService lifecycle,
            String first,
            String second,
            RepresentativeHistory history,
            long deadlineTick) {
        long now = lifecycle.snapshot().simulationTick();
        switch (history) {
            case TRADE -> remember(lifecycle, first, second, RelationFactor.TRADE_DEPENDENCE, 80, now, "trade");
            case DETERRENCE -> {
                remember(lifecycle, first, second, RelationFactor.THREAT, -60, now, "threat");
                remember(lifecycle, first, second, RelationFactor.DIPLOMATIC_COMMITMENT, 80, now, "commitment");
            }
            case NEGOTIATED_RESOLUTION -> {
                remember(lifecycle, first, second, RelationFactor.TRADE_DEPENDENCE, 50, now, "trade");
                remember(lifecycle, first, second, RelationFactor.THREAT, -50, now, "threat");
                remember(lifecycle, first, second, RelationFactor.DIPLOMATIC_COMMITMENT, 45, now, "commitment");
            }
            case WAR -> {
                remember(lifecycle, first, second, RelationFactor.THREAT, -90, now, "threat");
                remember(lifecycle, first, second, RelationFactor.REMEMBERED_ACTION, -30, now, "hostile-action");
            }
        }

        CrisisEscalation escalation = switch (history) {
            case TRADE -> CrisisEscalation.NEGOTIATION;
            case DETERRENCE -> persistedCrisisEscalation(
                    lifecycle, first, second, ProposalKind.NON_AGGRESSION, history, deadlineTick, 1);
            case NEGOTIATED_RESOLUTION -> persistedCrisisEscalation(
                    lifecycle, first, second, ProposalKind.ACCESS, history, deadlineTick, 2);
            case WAR -> persistedCrisisEscalation(
                    lifecycle, first, second, ProposalKind.ULTIMATUM, history, deadlineTick, 3);
        };

        RelationMemory memory = lifecycle.snapshot().relationMemories().stream()
                .filter(candidate -> candidate.ownerFactionId().equals(first)
                        && candidate.targetFactionId().equals(second))
                .findFirst()
                .orElseThrow();
        int tradeDependence = positiveFactorBasisPoints(memory, RelationFactor.TRADE_DEPENDENCE);
        int threat = negativeFactorBasisPoints(memory, RelationFactor.THREAT);
        int commitment = positiveFactorBasisPoints(memory, RelationFactor.DIPLOMATIC_COMMITMENT);
        boolean credibleSettlement = history == RepresentativeHistory.NEGOTIATED_RESOLUTION;
        return new DiplomaticSituation(
                memory.derivedRelation(),
                tradeDependence,
                threat,
                commitment,
                escalation,
                credibleSettlement);
    }

    private static CrisisEscalation persistedCrisisEscalation(
            DiplomaticLifecycleService lifecycle,
            String first,
            String second,
            ProposalKind kind,
            RepresentativeHistory history,
            long deadlineTick,
            int steps) {
        String suffix = history.name().toLowerCase();
        var proposal = lifecycle.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.representative." + suffix,
                first,
                second,
                kind,
                "issue.representative." + suffix,
                List.of(),
                List.of(),
                deadlineTick));
        var crisis = lifecycle.openCrisis(proposal.proposalId(), "decision.open." + suffix, deadlineTick);
        for (int step = 1; step <= steps; step++) {
            crisis = lifecycle.escalateCrisis(
                    crisis.crisisId(),
                    "decision." + suffix + "." + step,
                    deadlineTick);
        }
        return crisis.escalation();
    }

    private static void remember(
            DiplomaticLifecycleService lifecycle,
            String owner,
            String target,
            RelationFactor factor,
            int impact,
            long observedTick,
            String subject) {
        lifecycle.remember(owner, target, new RelationEvent(
                "memory.representative." + subject,
                factor,
                impact,
                observedTick,
                "subject.representative." + subject));
    }

    private static int positiveFactorBasisPoints(RelationMemory memory, RelationFactor factor) {
        int impact = memory.events().stream()
                .filter(event -> event.factor() == factor)
                .mapToInt(RelationEvent::impact)
                .sum();
        return Math.max(0, Math.min(10_000, impact * 100));
    }

    private static int negativeFactorBasisPoints(RelationMemory memory, RelationFactor factor) {
        int impact = memory.events().stream()
                .filter(event -> event.factor() == factor)
                .mapToInt(RelationEvent::impact)
                .sum();
        return Math.max(0, Math.min(10_000, -impact * 100));
    }
}
