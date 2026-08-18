package com.spacesim.warfare;

import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage19ConflictState.ConflictSnapshot;
import com.spacesim.persistence.Stage19ConflictState.ConflictStatus;
import com.spacesim.persistence.Stage19ConflictState.ObjectiveSnapshot;
import com.spacesim.persistence.Stage19ConflictStateCodec;
import com.spacesim.warfare.Stage19ConflictRuntime.CurrentPhysicalReadiness;
import com.spacesim.warfare.Stage19ConflictRuntime.ObservationDelta;
import com.spacesim.warfare.StrategicWarPolicyService.Decision;
import com.spacesim.warfare.StrategicWarPolicyService.EscalationLevel;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveEvidence;
import com.spacesim.warfare.StrategicWarPolicyService.Policy;
import com.spacesim.warfare.StrategicWarPolicyService.SettlementOffer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage19ConflictRuntimeTest {
    private static final String CONFLICT_ID = "conflict.runtime.test";
    private static final String OBJECTIVE_ID = "objective.runtime.corridor";
    private static final Policy POLICY = new Policy(2, 20_000d, true, 2_000_000d, 500_000d);

    @Test
    void observationsAccumulateOnlyExplicitActorKnownConsequencesAndEvidence() {
        Stage19ConflictRuntime runtime = runtime();

        ConflictSnapshot updated = runtime.observe(
                CONFLICT_ID,
                11L,
                new ObservationDelta(
                        100_000d,
                        25_000d,
                        300_000d,
                        400_000d,
                        Map.of(OBJECTIVE_ID, ObjectiveEvidence.OBSERVED_MET)));
        updated = runtime.observe(
                CONFLICT_ID,
                12L,
                new ObservationDelta(50_000d, 0d, 0d, 100_000d, Map.of()));

        assertEquals(150_000d, updated.consequences().confirmedOwnDestroyedMassKg(), 1e-9d);
        assertEquals(25_000d, updated.consequences().confirmedOwnUndeliveredCargoKg(), 1e-9d);
        assertEquals(300_000d, updated.consequences().observedOpponentDestroyedMassKg(), 1e-9d);
        assertEquals(500_000d, updated.consequences().observedOpponentUndeliveredCargoKg(), 1e-9d);
        assertEquals(ObjectiveEvidence.OBSERVED_MET, updated.objectives().get(0).evidence());
        assertEquals(12L, runtime.simulationTick());
    }

    @Test
    void saveLoadDoesNotPersistOrManufactureCurrentPhysicalReadiness() {
        Stage19ConflictRuntime runtime = runtime();
        var first = runtime.decide(
                CONFLICT_ID,
                11L,
                new CurrentPhysicalReadiness(4, 50_000d, 0d, 0d),
                POLICY,
                SettlementOffer.none());
        assertEquals(Decision.ESCALATE, first.policyResult().decision());

        byte[] bytes = Stage19ConflictStateCodec.encode(runtime.snapshot());
        Stage19ConflictRuntime restored = new Stage19ConflictRuntime(Stage19ConflictStateCodec.decode(bytes));
        var afterLoad = restored.decide(
                CONFLICT_ID,
                12L,
                new CurrentPhysicalReadiness(4, 1_000d, 0d, 0d),
                POLICY,
                SettlementOffer.none());

        assertEquals(Decision.SEEK_SETTLEMENT, afterLoad.policyResult().decision());
        assertFalse(afterLoad.policyResult().canSustainCurrentOperations());
        assertEquals(ConflictStatus.SETTLEMENT_SEEKING, afterLoad.conflict().status());
    }

    @Test
    void observedOpponentCargoDenialCanLeadToOfferThenExplicitSettlementAcceptance() {
        Stage19ConflictRuntime runtime = runtime();
        CurrentPhysicalReadiness readiness = new CurrentPhysicalReadiness(4, 50_000d, 0d, 0d);
        runtime.observe(
                CONFLICT_ID,
                11L,
                new ObservationDelta(0d, 0d, 0d, 600_000d, Map.of()));

        var offer = runtime.decide(CONFLICT_ID, 12L, readiness, POLICY, SettlementOffer.none());
        assertEquals(Decision.OFFER_SETTLEMENT, offer.policyResult().decision());
        assertEquals(ConflictStatus.SETTLEMENT_OFFERED, offer.conflict().status());

        var accepted = runtime.decide(
                CONFLICT_ID,
                13L,
                readiness,
                POLICY,
                new SettlementOffer(true, Set.of(OBJECTIVE_ID)));
        assertEquals(Decision.ACCEPT_SETTLEMENT, accepted.policyResult().decision());
        assertEquals(ConflictStatus.RESOLVED, accepted.conflict().status());
        assertThrows(IllegalStateException.class,
                () -> runtime.observe(CONFLICT_ID, 14L, ObservationDelta.none()));
        assertThrows(IllegalStateException.class,
                () -> runtime.decide(CONFLICT_ID, 14L, readiness, POLICY, SettlementOffer.none()));
    }

    @Test
    void objectiveCompletionDeEscalatesPoliticallyWithoutChangingPhysicalReadiness() {
        Stage19ConflictRuntime runtime = runtime();
        CurrentPhysicalReadiness readiness = new CurrentPhysicalReadiness(4, 50_000d, 0d, 0d);
        runtime.decide(CONFLICT_ID, 11L, readiness, POLICY, SettlementOffer.none());
        ConflictSnapshot afterEvidence = runtime.observe(
                CONFLICT_ID,
                12L,
                new ObservationDelta(
                        0d,
                        0d,
                        0d,
                        0d,
                        Map.of(OBJECTIVE_ID, ObjectiveEvidence.OBSERVED_MET)));
        assertEquals(EscalationLevel.LIMITED_WAR, afterEvidence.escalation());

        var result = runtime.decide(CONFLICT_ID, 13L, readiness, POLICY, SettlementOffer.none());

        assertEquals(Decision.DE_ESCALATE, result.policyResult().decision());
        assertEquals(EscalationLevel.CRISIS, result.conflict().escalation());
        assertEquals(Stage19ConflictState.MobilizationPosture.NORMAL, result.conflict().mobilization());
    }

    @Test
    void unknownObjectiveAndBackwardTimeAreRejectedWithoutMutation() {
        Stage19ConflictRuntime runtime = runtime();
        Stage19ConflictState before = runtime.snapshot();

        assertThrows(IllegalArgumentException.class, () -> runtime.observe(
                CONFLICT_ID,
                11L,
                new ObservationDelta(0d, 0d, 0d, 0d,
                        Map.of("objective.unknown", ObjectiveEvidence.OBSERVED_MET))));
        assertEquals(before, runtime.snapshot());
        assertThrows(IllegalArgumentException.class,
                () -> runtime.observe(CONFLICT_ID, 9L, ObservationDelta.none()));
    }

    @Test
    void addRequiresUniqueNonResolvedConflictAndMonotonicTick() {
        Stage19ConflictRuntime empty = new Stage19ConflictRuntime(Stage19ConflictState.empty(5L));
        ConflictSnapshot conflict = initial("conflict.added");

        assertEquals(conflict, empty.add(conflict, 6L));
        assertTrue(empty.find("conflict.added").isPresent());
        assertTrue(empty.find("missing").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> empty.add(conflict, 7L));
        assertThrows(IllegalArgumentException.class,
                () -> empty.add(initial("conflict.old"), 4L));
    }

    private static Stage19ConflictRuntime runtime() {
        return new Stage19ConflictRuntime(new Stage19ConflictState(
                Stage19ConflictState.CURRENT_VERSION,
                10L,
                List.of(initial(CONFLICT_ID))));
    }

    private static ConflictSnapshot initial(String id) {
        return ConflictSnapshot.active(
                id,
                "faction.actor",
                "faction.opponent",
                EscalationLevel.CRISIS,
                List.of(new ObjectiveSnapshot(
                        OBJECTIVE_ID,
                        "link.alpha-beta",
                        true,
                        ObjectiveEvidence.OBSERVED_UNMET)));
    }
}
