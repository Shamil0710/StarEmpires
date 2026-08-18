package com.spacesim.warfare;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicWarPolicyServiceTest {
    private static final StrategicWarPolicyService SERVICE = new StrategicWarPolicyService();
    private static final StrategicWarPolicyService.WarObjective CORRIDOR =
            new StrategicWarPolicyService.WarObjective("objective.open_corridor", "link.alpha-beta", true);
    private static final StrategicWarPolicyService.Policy POLICY = new StrategicWarPolicyService.Policy(
            2,
            20_000d,
            true,
            2_000_000d,
            500_000d);

    @Test
    void actorVisibleOpponentPressureCanProduceCoerciveOfferWithoutWarScore() {
        var pressured = input(
                StrategicWarPolicyService.EscalationLevel.LIMITED_WAR,
                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_UNMET,
                evidence(4, 50_000d, 10_000d, 12_000d, 2_500_000d, 0d),
                StrategicWarPolicyService.SettlementOffer.none());

        var result = SERVICE.decide(pressured);

        assertEquals(StrategicWarPolicyService.Decision.OFFER_SETTLEMENT, result.decision());
        assertTrue(result.observedOpponentMaterialPressure());
        assertTrue(result.canSustainCurrentOperations());
        assertEquals(Set.of("objective.open_corridor"), result.unresolvedMandatoryObjectiveIds());
    }

    @Test
    void actorIsolationChangesDecisionWhenOpponentLossWasNotObserved() {
        var observed = input(
                StrategicWarPolicyService.EscalationLevel.CRISIS,
                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_UNMET,
                evidence(4, 50_000d, 5_000d, 5_000d, 3_000_000d, 0d),
                StrategicWarPolicyService.SettlementOffer.none());
        var unobserved = input(
                StrategicWarPolicyService.EscalationLevel.CRISIS,
                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_UNMET,
                evidence(4, 50_000d, 5_000d, 5_000d, 0d, 0d),
                StrategicWarPolicyService.SettlementOffer.none());

        assertEquals(StrategicWarPolicyService.Decision.OFFER_SETTLEMENT, SERVICE.decide(observed).decision());
        assertEquals(StrategicWarPolicyService.Decision.ESCALATE, SERVICE.decide(unobserved).decision());
    }

    @Test
    void insufficientPhysicalReactionMassForcesSettlementSeeking() {
        var result = SERVICE.decide(input(
                StrategicWarPolicyService.EscalationLevel.LIMITED_WAR,
                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_UNMET,
                evidence(5, 19_999d, 1_000d, 1_000d, 0d, 0d),
                StrategicWarPolicyService.SettlementOffer.none()));

        assertEquals(StrategicWarPolicyService.Decision.SEEK_SETTLEMENT, result.decision());
        assertFalse(result.canSustainCurrentOperations());
    }

    @Test
    void uncoveredRepairDemandPreventsMagicalContinuedWar() {
        var result = SERVICE.decide(input(
                StrategicWarPolicyService.EscalationLevel.LIMITED_WAR,
                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_UNMET,
                evidence(5, 60_000d, 20_000d, 19_000d, 0d, 0d),
                StrategicWarPolicyService.SettlementOffer.none()));

        assertEquals(StrategicWarPolicyService.Decision.SEEK_SETTLEMENT, result.decision());
        assertFalse(result.canSustainCurrentOperations());
    }

    @Test
    void visibleOfferIsAcceptedOnlyWhenItCoversEveryUnresolvedMandatoryObjective() {
        var insufficient = SERVICE.decide(input(
                StrategicWarPolicyService.EscalationLevel.LIMITED_WAR,
                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_UNMET,
                evidence(4, 50_000d, 0d, 0d, 0d, 0d),
                new StrategicWarPolicyService.SettlementOffer(true, Set.of())));
        var sufficient = SERVICE.decide(input(
                StrategicWarPolicyService.EscalationLevel.LIMITED_WAR,
                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_UNMET,
                evidence(4, 50_000d, 0d, 0d, 0d, 0d),
                new StrategicWarPolicyService.SettlementOffer(true, Set.of("objective.open_corridor"))));

        assertEquals(StrategicWarPolicyService.Decision.ESCALATE, insufficient.decision());
        assertEquals(StrategicWarPolicyService.Decision.ACCEPT_SETTLEMENT, sufficient.decision());
    }

    @Test
    void observedCompletionOfMandatoryObjectivesDeEscalates() {
        var result = SERVICE.decide(input(
                StrategicWarPolicyService.EscalationLevel.GENERAL_WAR,
                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_MET,
                evidence(4, 50_000d, 0d, 0d, 0d, 0d),
                StrategicWarPolicyService.SettlementOffer.none()));

        assertEquals(StrategicWarPolicyService.Decision.DE_ESCALATE, result.decision());
        assertTrue(result.unresolvedMandatoryObjectiveIds().isEmpty());
    }

    @Test
    void impossibleMandatoryObjectiveSeeksSettlementInsteadOfInventingVictory() {
        var result = SERVICE.decide(input(
                StrategicWarPolicyService.EscalationLevel.GENERAL_WAR,
                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_IMPOSSIBLE,
                evidence(8, 90_000d, 0d, 0d, 5_000_000d, 1_000_000d),
                StrategicWarPolicyService.SettlementOffer.none()));

        assertEquals(StrategicWarPolicyService.Decision.SEEK_SETTLEMENT, result.decision());
    }

    @Test
    void maximumEscalationHoldsWhenNoSettlementOrObservedLeverageExists() {
        var result = SERVICE.decide(input(
                StrategicWarPolicyService.EscalationLevel.GENERAL_WAR,
                StrategicWarPolicyService.ObjectiveEvidence.UNKNOWN,
                evidence(8, 90_000d, 0d, 0d, 0d, 0d),
                StrategicWarPolicyService.SettlementOffer.none()));

        assertEquals(StrategicWarPolicyService.Decision.HOLD, result.decision());
    }

    @Test
    void identicalInputsAreDeterministicAndInputOrderingDoesNotMatter() {
        var optional = new StrategicWarPolicyService.WarObjective("objective.optional", "station.gamma", false);
        var evidence = evidence(4, 50_000d, 0d, 0d, 0d, 600_000d);
        var first = new StrategicWarPolicyService.Input(
                StrategicWarPolicyService.EscalationLevel.CRISIS,
                List.of(
                        new StrategicWarPolicyService.ObjectiveAssessment(CORRIDOR,
                                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_UNMET),
                        new StrategicWarPolicyService.ObjectiveAssessment(optional,
                                StrategicWarPolicyService.ObjectiveEvidence.UNKNOWN)),
                evidence,
                POLICY,
                StrategicWarPolicyService.SettlementOffer.none());
        var second = new StrategicWarPolicyService.Input(
                StrategicWarPolicyService.EscalationLevel.CRISIS,
                List.of(
                        new StrategicWarPolicyService.ObjectiveAssessment(optional,
                                StrategicWarPolicyService.ObjectiveEvidence.UNKNOWN),
                        new StrategicWarPolicyService.ObjectiveAssessment(CORRIDOR,
                                StrategicWarPolicyService.ObjectiveEvidence.OBSERVED_UNMET)),
                evidence,
                POLICY,
                StrategicWarPolicyService.SettlementOffer.none());

        assertEquals(SERVICE.decide(first), SERVICE.decide(second));
        assertEquals(StrategicWarPolicyService.Decision.OFFER_SETTLEMENT, SERVICE.decide(first).decision());
    }

    private static StrategicWarPolicyService.Input input(
            StrategicWarPolicyService.EscalationLevel escalation,
            StrategicWarPolicyService.ObjectiveEvidence objectiveEvidence,
            StrategicWarPolicyService.PhysicalWarEvidence evidence,
            StrategicWarPolicyService.SettlementOffer offer) {
        return new StrategicWarPolicyService.Input(
                escalation,
                List.of(new StrategicWarPolicyService.ObjectiveAssessment(CORRIDOR, objectiveEvidence)),
                evidence,
                POLICY,
                offer);
    }

    private static StrategicWarPolicyService.PhysicalWarEvidence evidence(
            int ships,
            double reactionMassKg,
            double repairDemandKg,
            double repairAvailableKg,
            double observedOpponentDestroyedMassKg,
            double observedOpponentUndeliveredCargoKg) {
        return new StrategicWarPolicyService.PhysicalWarEvidence(
                ships,
                reactionMassKg,
                repairDemandKg,
                repairAvailableKg,
                0d,
                0d,
                observedOpponentDestroyedMassKg,
                observedOpponentUndeliveredCargoKg);
    }
}
