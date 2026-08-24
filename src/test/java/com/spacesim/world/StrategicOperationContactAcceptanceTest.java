package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class StrategicOperationContactAcceptanceTest {
    @Test
    void interceptionContactMustComeFromCurrentActorBoundedSecurityEvidence() {
        StrategicOperationState initial = activeInterception();
        FleetId target = new FleetId(900L);
        ActorObservation fresh = new ActorObservation(
                Domain.SECURITY,
                InterestKind.BORDER_SECURITY,
                target.toString(),
                8_000,
                new ObservationEvidence(ObservationChannel.LOCAL_SENSOR_REPORT, "scan:900", 40L, 50L));
        FactionActorObservationSnapshot observed = new FactionActorObservationSnapshot(
                "faction.alpha", 45L, List.of(), List.of(), List.of(fresh), List.of());

        StrategicOperationState contacted = new StrategicOperationService().acquireContact(
                initial, 1L, observed, target, new StarSystemId(2L), 45L);

        OperationState operation = contacted.requireOperation(1L);
        assertEquals(OperationStatus.CONTACT_CONFIRMED, operation.status());
        assertEquals(target, operation.contact().targetFleetId());
        assertEquals("scan:900", operation.contact().provenanceId());
    }

    @Test
    void staleOrNonSecurityEvidenceCannotRevealTarget() {
        StrategicOperationState initial = activeInterception();
        FleetId target = new FleetId(900L);
        ActorObservation stale = new ActorObservation(
                Domain.SECURITY,
                InterestKind.BORDER_SECURITY,
                target.toString(),
                8_000,
                new ObservationEvidence(ObservationChannel.INTELLIGENCE_REPORT, "intel:900", 20L, 30L));
        FactionActorObservationSnapshot observed = new FactionActorObservationSnapshot(
                "faction.alpha", 45L, List.of(), List.of(), List.of(stale), List.of());

        assertThrows(IllegalStateException.class, () -> new StrategicOperationService().acquireContact(
                initial, 1L, observed, target, new StarSystemId(2L), 45L));
    }

    @Test
    void onlyTheSixRoadmapOperationOrderFamiliesAreAdmitted() {
        assertEquals(OperationType.ESCORT, StrategicOperationService.operationType(FleetCommandState.OrderType.ESCORT));
        assertEquals(OperationType.INTERCEPTION, StrategicOperationService.operationType(FleetCommandState.OrderType.INTERCEPT));
        assertEquals(OperationType.RAID, StrategicOperationService.operationType(FleetCommandState.OrderType.RAID));
        assertEquals(OperationType.BLOCKADE, StrategicOperationService.operationType(FleetCommandState.OrderType.BLOCKADE));
        assertEquals(OperationType.DEFENSE, StrategicOperationService.operationType(FleetCommandState.OrderType.GUARD));
        assertEquals(OperationType.INVASION, StrategicOperationService.operationType(FleetCommandState.OrderType.INVADE));
        assertThrows(IllegalArgumentException.class,
                () -> StrategicOperationService.operationType(FleetCommandState.OrderType.REPAIR));
    }

    private static StrategicOperationState activeInterception() {
        OperationState operation = new OperationState(
                1L,
                OperationType.INTERCEPTION,
                10L,
                20L,
                1,
                List.of(new FleetId(100L)),
                new StarSystemId(1L),
                new StarSystemId(2L),
                "system:2",
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(4_000, 2_000, 100L),
                new WithdrawalPolicy(new StarSystemId(1L), 2_500, true, true),
                OperationStatus.ACTIVE,
                35L,
                35L,
                -1L,
                null,
                null);
        return new StrategicOperationState(2L, List.of(operation));
    }
}
