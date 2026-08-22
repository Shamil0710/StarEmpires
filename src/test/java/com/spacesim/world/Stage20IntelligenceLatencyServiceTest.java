package com.spacesim.world;

import com.spacesim.world.Stage20DiscoveryKnowledgeRuntime.StaticObservation;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.Freshness;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage20IntelligenceLatencyService.CommunicationNode;
import com.spacesim.world.Stage20IntelligenceLatencyService.DeliveryChannel;
import com.spacesim.world.Stage20IntelligenceLatencyService.TransmissionMode;
import com.spacesim.world.generation.Stage20RepresentativeGeneratedWorldProbeProfileV3;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20IntelligenceLatencyServiceTest {
    private static final ResolvedProbeResult ACCEPTED =
            Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);

    @Test
    void localSignalUsesExactPhysicalDistanceAndPreservesObservationAge() {
        StarSystemId system = new StarSystemId(20_801L);
        CommunicationNode source = new CommunicationNode(
                "relay.source", "faction.source", system, LocalPhysicalPosition.origin());
        CommunicationNode destination = new CommunicationNode(
                "relay.destination",
                "faction.destination",
                system,
                LocalPhysicalPosition.origin().translated(
                        Stage20IntelligenceLatencyService.VACUUM_LIGHT_SPEED_MPS, 0d));
        var plan = Stage20IntelligenceLatencyService.planLocal("local-light-second", source, destination);

        assertEquals(TransmissionMode.LOCAL_ELECTROMAGNETIC, plan.mode());
        assertEquals(Stage20IntelligenceLatencyService.VACUUM_LIGHT_SPEED_MPS,
                plan.localPropagationDistanceM(), 0d);
        assertEquals(1d, plan.transmissionSeconds(), 1e-12d);
        StaticObservation observation = detectedObservation(
                system,
                "special.local-contact",
                new DiscoveryEvidence(
                        DiscoverySource.PASSIVE_SENSOR,
                        "passive-local-contact",
                        5d,
                        OptionalDouble.of(15d)));
        var receipt = Stage20IntelligenceLatencyService.deliver(
                "receipt.local",
                plan,
                observation,
                6d,
                DeliveryChannel.INFRASTRUCTURE_BROADCAST);

        assertEquals(7d, receipt.receiptTimeSeconds(), 1e-12d);
        assertEquals(2d, receipt.ageAtReceiptSeconds(), 1e-12d);
        assertEquals(Freshness.CURRENT, receipt.freshnessAtReceipt());
        assertEquals(5d, receipt.recipientObservation().evidence().observedAtSeconds(), 0d);
        assertEquals(
                DiscoverySource.PERSISTENT_INFRASTRUCTURE_BROADCAST,
                receipt.recipientObservation().evidence().source());
    }

    @Test
    void generatedInterSystemCourierUsesNeighborEdgesAndCanArriveAlreadyStale() {
        var assignments = ACCEPTED.generation().placement().orElseThrow().assignments();
        var sourceStart = assignments.get(0);
        var destinationStart = assignments.stream()
                .filter(value -> !value.systemId().equals(sourceStart.systemId()))
                .findFirst().orElseThrow();
        var jumpPlan = Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent()
                .inputs().transport().returnPlan();
        var plan = Stage20IntelligenceLatencyService.planGeneratedHubCourier(
                "courier.start-hubs",
                ACCEPTED,
                sourceStart.stableFactionId(),
                sourceStart.systemId(),
                destinationStart.stableFactionId(),
                destinationStart.systemId(),
                jumpPlan,
                "stage20i.test.current-fitted-courier");

        assertEquals(TransmissionMode.PHYSICAL_COURIER, plan.mode());
        var route = plan.courierRoute().orElseThrow();
        assertTrue(route.jumpCount() > 0);
        assertTrue(route.perHopRevalidationRequired());
        assertTrue(plan.sourceCourierAccessSeconds() > 0d);
        assertTrue(plan.destinationCourierAccessSeconds() > 0d);
        assertTrue(plan.transmissionSeconds() > route.estimatedArrivalSeconds());
        assertTrue(route.edges().stream().allMatch(value ->
                ACCEPTED.generation().topology().requireAcceptedTopology()
                        .connections().contains(value.connection())));

        StaticObservation observation = new StaticObservation(
                new StaticObjectRef(
                        sourceStart.systemId(),
                        StaticObjectKind.SPECIAL_LOCATION,
                        "special.shared-report"),
                DiscoveryState.CLASSIFIED,
                Optional.of("anomaly.energetic-field.v1"),
                Optional.empty(),
                ResourceKnowledge.none(),
                new DiscoveryEvidence(
                        DiscoverySource.ACTIVE_SCAN,
                        "active-source-report",
                        10d,
                        OptionalDouble.of(70d)));
        var receipt = Stage20IntelligenceLatencyService.deliver(
                "receipt.inter-system",
                plan,
                observation,
                20d,
                DeliveryChannel.SHARED_INTELLIGENCE);

        assertEquals(20d + plan.transmissionSeconds(), receipt.receiptTimeSeconds(), 1e-9d);
        assertEquals(Freshness.STALE, receipt.freshnessAtReceipt());
        assertEquals(DiscoverySource.PURCHASED_OR_SHARED_MAP_DATA,
                receipt.recipientObservation().evidence().source());
        assertEquals(10d, receipt.recipientObservation().evidence().observedAtSeconds(), 0d);

        Stage20DiscoveryKnowledgeState received = new Stage20DiscoveryKnowledgeRuntime().observe(
                new Stage20DiscoveryKnowledgeState(destinationStart.stableFactionId(), List.of()),
                receipt.recipientObservation());
        assertEquals(
                Freshness.STALE,
                received.knowledge(observation.object()).orElseThrow()
                        .freshnessAt(receipt.receiptTimeSeconds()));

        var permanent = Stage20IntelligenceLatencyService.deliver(
                "receipt.permanent",
                plan,
                detectedObservation(
                        sourceStart.systemId(),
                        "special.permanent-map",
                        new DiscoveryEvidence(
                                DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                                "permanent-survey",
                                10d,
                                OptionalDouble.empty())),
                20d,
                DeliveryChannel.SHARED_INTELLIGENCE);
        assertEquals(Freshness.PERMANENT, permanent.freshnessAtReceipt());
    }

    @Test
    void invalidTimeAndCrossSystemLocalShortcutFailClosed() {
        StarSystemId sourceSystem = new StarSystemId(20_802L);
        StarSystemId destinationSystem = new StarSystemId(20_803L);
        CommunicationNode source = new CommunicationNode(
                "source", "source-owner", sourceSystem, LocalPhysicalPosition.origin());
        CommunicationNode destination = new CommunicationNode(
                "destination", "destination-owner", destinationSystem,
                LocalPhysicalPosition.origin().translated(1_000d, 0d));

        assertThrows(
                IllegalArgumentException.class,
                () -> Stage20IntelligenceLatencyService.planLocal("invalid-local", source, destination));
        var sameSystemDestination = new CommunicationNode(
                "destination-local", "destination-owner", sourceSystem,
                LocalPhysicalPosition.origin().translated(1_000d, 0d));
        var local = Stage20IntelligenceLatencyService.planLocal("valid-local", source, sameSystemDestination);
        assertThrows(
                IllegalArgumentException.class,
                () -> Stage20IntelligenceLatencyService.deliver(
                        "invalid-time",
                        local,
                        detectedObservation(
                                sourceSystem,
                                "special.future",
                                new DiscoveryEvidence(
                                        DiscoverySource.PASSIVE_SENSOR,
                                        "future-observation",
                                        100d,
                                        OptionalDouble.empty())),
                        99d,
                        DeliveryChannel.SHARED_INTELLIGENCE));
    }

    private static StaticObservation detectedObservation(
            StarSystemId systemId,
            String objectId,
            DiscoveryEvidence evidence) {
        return new StaticObservation(
                new StaticObjectRef(systemId, StaticObjectKind.SPECIAL_LOCATION, objectId),
                DiscoveryState.DETECTED,
                Optional.empty(),
                Optional.empty(),
                ResourceKnowledge.none(),
                evidence);
    }
}
