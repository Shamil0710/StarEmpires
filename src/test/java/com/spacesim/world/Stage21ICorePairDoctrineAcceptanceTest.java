package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Final Stage-21I core-pair proof over the ordinary Stage-21B planner. */
class Stage21ICorePairDoctrineAcceptanceTest {
    private static final String IMPERIAL_ID = "faction.empire";
    private static final String INDUSTRIAL_UNION_ID = "faction.industrial-union";

    private static final CoreDoctrineProfile IMPERIAL = new CoreDoctrineProfile(
            9_500,
            5_000,
            9_000);
    private static final CoreDoctrineProfile INDUSTRIAL_UNION = new CoreDoctrineProfile(
            5_000,
            9_500,
            9_000);

    @Test
    void equivalentLawfulOpportunitySetDivergesOnlyThroughExplicitDoctrinePreference() {
        List<StrategicGoalCandidate> imperialOptions = sharedSupplyDependencyOptions(IMPERIAL);
        List<StrategicGoalCandidate> industrialOptions = sharedSupplyDependencyOptions(INDUSTRIAL_UNION);

        assertSameNonDoctrineInputs(imperialOptions, industrialOptions);

        var imperial = review(IMPERIAL_ID, imperialOptions);
        var industrial = review(INDUSTRIAL_UNION_ID, industrialOptions);

        assertEquals(1, imperial.state().activeGoals().size());
        assertEquals(1, industrial.state().activeGoals().size());
        assertEquals(StrategicGoalType.DEFEND, imperial.state().activeGoals().get(0).type());
        assertEquals(StrategicGoalType.STOCKPILE, industrial.state().activeGoals().get(0).type());
        assertNotEquals(
                imperial.state().activeGoals().get(0).type(),
                industrial.state().activeGoals().get(0).type());

        assertTrue(imperial.projections().stream()
                .anyMatch(row -> row.type() == StrategicGoalType.DEFEND
                        && row.doctrinePreferenceBasisPoints() == IMPERIAL.defendPreferenceBps()));
        assertTrue(industrial.projections().stream()
                .anyMatch(row -> row.type() == StrategicGoalType.STOCKPILE
                        && row.doctrinePreferenceBasisPoints() == INDUSTRIAL_UNION.stockpilePreferenceBps()));
    }

    @Test
    void sharedPhysicalShortageEvidenceConvergesOnSameRationalGoal() {
        StrategicGoalCandidate imperialShortage = shortageCandidate(IMPERIAL);
        StrategicGoalCandidate industrialShortage = shortageCandidate(INDUSTRIAL_UNION);

        assertSameNonDoctrineInputs(List.of(imperialShortage), List.of(industrialShortage));

        var imperial = review(IMPERIAL_ID, List.of(imperialShortage));
        var industrial = review(INDUSTRIAL_UNION_ID, List.of(industrialShortage));

        assertEquals(StrategicGoalType.STOCKPILE, imperial.state().activeGoals().get(0).type());
        assertEquals(StrategicGoalType.STOCKPILE, industrial.state().activeGoals().get(0).type());
        assertEquals(
                imperial.state().activeGoals().get(0).sourceEvidence(),
                industrial.state().activeGoals().get(0).sourceEvidence());
    }

    @Test
    void factionIdentifierDoesNotChangeOutcomeWhenDoctrineInputsAreEqual() {
        List<StrategicGoalCandidate> sameOptions = sharedSupplyDependencyOptions(IMPERIAL);

        var namedCoreFaction = review(IMPERIAL_ID, sameOptions);
        var arbitraryFaction = review("faction.control-with-no-name-bonus", sameOptions);

        assertEquals(
                namedCoreFaction.state().activeGoals().get(0).type(),
                arbitraryFaction.state().activeGoals().get(0).type());
        assertEquals(
                namedCoreFaction.projections().stream().map(row -> row.scoreBasisPoints()).toList(),
                arbitraryFaction.projections().stream().map(row -> row.scoreBasisPoints()).toList());
    }

    private static FactionStrategicGoalPlanner.PlanningResult review(
            String factionId,
            List<StrategicGoalCandidate> candidates) {
        return FactionStrategicGoalPlanner.review(
                FactionLivingActorState.initial(factionId, 24L),
                FactionStrategicIntentState.initial(factionId),
                candidates,
                StrategicPlanningEnvelope.balanced(5L),
                24L);
    }

    private static List<StrategicGoalCandidate> sharedSupplyDependencyOptions(CoreDoctrineProfile doctrine) {
        String target = "route:core-pair-supply";
        StrategicGoalEvidence evidence = evidence(
                InterestKind.SUPPLY_DEPENDENCY,
                target,
                8_000,
                "economic-ledger:core-pair-route");
        return List.of(
                candidate(StrategicGoalType.DEFEND, target, evidence, doctrine.defendPreferenceBps()),
                candidate(StrategicGoalType.STOCKPILE, target, evidence, doctrine.stockpilePreferenceBps()));
    }

    private static StrategicGoalCandidate shortageCandidate(CoreDoctrineProfile doctrine) {
        String target = "resource:core-pair-propellant";
        StrategicGoalEvidence evidence = evidence(
                InterestKind.RESOURCE_DEFICIT,
                target,
                9_000,
                "economic-ledger:core-pair-propellant-shortage");
        return candidate(StrategicGoalType.STOCKPILE, target, evidence, doctrine.shortagePreferenceBps());
    }

    private static StrategicGoalCandidate candidate(
            StrategicGoalType type,
            String target,
            StrategicGoalEvidence evidence,
            int doctrinePreferenceBps) {
        return new StrategicGoalCandidate(
                type,
                target,
                evidence,
                evidence.severityBasisPoints(),
                8_500,
                8_500,
                doctrinePreferenceBps,
                StrategicPlanningEnvelope.balanced(5L),
                List.of(),
                -1L,
                24L,
                StrategicGoalOutcomeSignal.NONE);
    }

    private static StrategicGoalEvidence evidence(
            InterestKind kind,
            String target,
            int severity,
            String provenanceId) {
        return new StrategicGoalEvidence(
                kind,
                target,
                severity,
                List.of(new ObservationEvidence(
                        ObservationChannel.ECONOMIC_LEDGER,
                        provenanceId,
                        24L,
                        -1L)));
    }

    private static void assertSameNonDoctrineInputs(
            List<StrategicGoalCandidate> left,
            List<StrategicGoalCandidate> right) {
        assertEquals(left.size(), right.size());
        for (int index = 0; index < left.size(); index++) {
            StrategicGoalCandidate a = left.get(index);
            StrategicGoalCandidate b = right.get(index);
            assertEquals(a.type(), b.type());
            assertEquals(a.targetId(), b.targetId());
            assertEquals(a.sourceEvidence(), b.sourceEvidence());
            assertEquals(a.urgencyBasisPoints(), b.urgencyBasisPoints());
            assertEquals(a.strategicValueBasisPoints(), b.strategicValueBasisPoints());
            assertEquals(a.feasibilityBasisPoints(), b.feasibilityBasisPoints());
            assertEquals(a.requestedBudget(), b.requestedBudget());
            assertEquals(a.costCeiling(), b.costCeiling());
            assertEquals(a.successConditions(), b.successConditions());
            assertEquals(a.failureConditions(), b.failureConditions());
            assertEquals(a.blockers(), b.blockers());
            assertEquals(a.expiresAtTick(), b.expiresAtTick());
            assertEquals(a.reviewCadenceTicks(), b.reviewCadenceTicks());
            assertEquals(a.outcomeSignal(), b.outcomeSignal());
        }
    }

    /** Minimal Stage-21I doctrine fixture; values are explicit inputs, never inferred from faction names. */
    private record CoreDoctrineProfile(
            int defendPreferenceBps,
            int stockpilePreferenceBps,
            int shortagePreferenceBps) {
    }
}
