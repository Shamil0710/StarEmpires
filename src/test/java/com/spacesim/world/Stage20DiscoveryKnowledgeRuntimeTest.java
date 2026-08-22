package com.spacesim.world;

import com.spacesim.world.Stage20DiscoveryKnowledgeRuntime.StaticObservation;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.Freshness;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceEstimate;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledgeLevel;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20DiscoveryKnowledgeRuntimeTest {
    private static final StarSystemId SYSTEM = new StarSystemId(20_701L);
    private static final StaticObjectRef STATION = new StaticObjectRef(
            SYSTEM, StaticObjectKind.INFRASTRUCTURE, "station.trade-hub.1");
    private static final StaticObjectRef DEPOSIT = new StaticObjectRef(
            SYSTEM, StaticObjectKind.RESOURCE_OCCURRENCE, "occurrence.water.1");

    @Test
    void staticKnowledgeProgressesWithoutTurningRoughDetectionIntoOmniscience() {
        Stage20DiscoveryKnowledgeRuntime runtime = new Stage20DiscoveryKnowledgeRuntime();
        Stage20DiscoveryKnowledgeState state = new Stage20DiscoveryKnowledgeState("faction.alpha", List.of());

        assertEquals(DiscoveryState.UNKNOWN, state.discoveryState(STATION));
        state = runtime.observe(state, observation(
                STATION,
                DiscoveryState.DETECTED,
                Optional.empty(),
                Optional.empty(),
                ResourceKnowledge.none(),
                evidence(DiscoverySource.PASSIVE_SENSOR, "passive-contact-1", 10d, 20d)));

        var detected = state.knowledge(STATION).orElseThrow();
        assertEquals(DiscoveryState.DETECTED, detected.state());
        assertTrue(detected.classificationId().isEmpty());
        assertTrue(detected.knownLocation().isEmpty());
        assertEquals(Freshness.CURRENT, detected.freshnessAt(20d));
        assertEquals(Freshness.STALE, detected.freshnessAt(21d));

        state = runtime.observe(state, observation(
                STATION,
                DiscoveryState.CLASSIFIED,
                Optional.of("station.trade_hub"),
                Optional.empty(),
                ResourceKnowledge.none(),
                evidence(DiscoverySource.ACTIVE_SCAN, "active-scan-2", 30d, 60d)));
        assertEquals(DiscoveryState.CLASSIFIED, state.discoveryState(STATION));
        assertTrue(state.knowledge(STATION).orElseThrow().knownLocation().isEmpty());

        LocalPhysicalPosition location = LocalPhysicalPosition.origin().translated(4.5e9d, -2.25e9d);
        state = runtime.observe(state, observation(
                STATION,
                DiscoveryState.KNOWN_STATIC_LOCATION,
                Optional.of("station.trade_hub"),
                Optional.of(location),
                ResourceKnowledge.none(),
                permanentEvidence(DiscoverySource.PHYSICAL_VISIT_OR_SURVEY, "survey-3", 90d)));
        state = runtime.observe(state, observation(
                STATION,
                DiscoveryState.DETECTED,
                Optional.empty(),
                Optional.empty(),
                ResourceKnowledge.none(),
                evidence(DiscoverySource.FACTION_INTELLIGENCE, "late-rough-report", 120d, 180d)));

        var surveyed = state.knowledge(STATION).orElseThrow();
        assertEquals(DiscoveryState.KNOWN_STATIC_LOCATION, surveyed.state());
        assertEquals(Optional.of(location), surveyed.knownLocation());
        assertEquals(Optional.of("station.trade_hub"), surveyed.classificationId());
        assertEquals(4, surveyed.evidence().size());
        assertEquals(Freshness.PERMANENT, surveyed.freshnessAt(10_000d));
        assertEquals(10d, surveyed.firstObservedSeconds());
        assertEquals(120d, surveyed.lastUpdatedSeconds());
    }

    @Test
    void resourceSurveyStoresBoundedObserverEstimateRatherThanPhysicalReserve() {
        Stage20DiscoveryKnowledgeRuntime runtime = new Stage20DiscoveryKnowledgeRuntime();
        ResourceEstimate estimate = new ResourceEstimate(0.18d, 0.31d, 7.5e8d, 1.25e9d, 0.82d);
        ResourceKnowledge survey = new ResourceKnowledge(
                ResourceKnowledgeLevel.SURVEYED_DEPOSIT,
                Optional.of("resource.volatiles.water"),
                Optional.of(estimate));

        Stage20DiscoveryKnowledgeState state = runtime.observe(
                new Stage20DiscoveryKnowledgeState("faction.survey", List.of()),
                observation(
                        DEPOSIT,
                        DiscoveryState.KNOWN_STATIC_LOCATION,
                        Optional.of("resource.volatiles.water"),
                        Optional.of(LocalPhysicalPosition.origin().translated(900_000d, 1_200_000d)),
                        survey,
                        permanentEvidence(DiscoverySource.PHYSICAL_VISIT_OR_SURVEY, "deposit-survey-44", 500d)));

        var known = state.knowledge(DEPOSIT).orElseThrow();
        assertEquals(ResourceKnowledgeLevel.SURVEYED_DEPOSIT, known.resourceKnowledge().level());
        assertEquals(estimate, known.resourceKnowledge().estimate().orElseThrow());
        assertTrue(estimate.minimumRecoverableMassKg() < estimate.maximumRecoverableMassKg());

        assertThrows(IllegalArgumentException.class,
                () -> new ResourceEstimate(0.2d, 0.2d, 1_000d, 2_000d, 1d));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceEstimate(0.1d, 0.2d, 2_000d, 2_000d, 1d));
    }

    @Test
    void staticDomainRejectsMobileTrackStateAndConflictingStableFacts() {
        Stage20DiscoveryKnowledgeRuntime runtime = new Stage20DiscoveryKnowledgeRuntime();
        Stage20DiscoveryKnowledgeState empty = new Stage20DiscoveryKnowledgeState("faction.guard", List.of());

        assertThrows(IllegalArgumentException.class, () -> observation(
                STATION,
                DiscoveryState.TRACKED,
                Optional.of("station.trade_hub"),
                Optional.empty(),
                ResourceKnowledge.none(),
                evidence(DiscoverySource.ACTIVE_SCAN, "invalid-track", 1d, 2d)));

        LocalPhysicalPosition location = LocalPhysicalPosition.origin().translated(10_000d, 20_000d);
        Stage20DiscoveryKnowledgeState known = runtime.observe(empty, observation(
                STATION,
                DiscoveryState.KNOWN_STATIC_LOCATION,
                Optional.of("station.trade_hub"),
                Optional.of(location),
                ResourceKnowledge.none(),
                permanentEvidence(DiscoverySource.PHYSICAL_VISIT_OR_SURVEY, "visit", 5d)));

        assertThrows(IllegalArgumentException.class, () -> runtime.observe(known, observation(
                STATION,
                DiscoveryState.KNOWN_STATIC_LOCATION,
                Optional.of("station.trade_hub"),
                Optional.of(location.translated(1d, 0d)),
                ResourceKnowledge.none(),
                permanentEvidence(DiscoverySource.PHYSICAL_VISIT_OR_SURVEY, "conflict", 6d))));
        assertThrows(IllegalArgumentException.class, () -> runtime.observe(known, observation(
                STATION,
                DiscoveryState.CLASSIFIED,
                Optional.of("station.naval"),
                Optional.empty(),
                ResourceKnowledge.none(),
                evidence(DiscoverySource.FACTION_INTELLIGENCE, "identity-conflict", 7d, 9d))));
    }

    @Test
    void ownerSnapshotCanonicalizesEntriesAndForbidsDuplicateStaticIdentity() {
        var stationEvidence = permanentEvidence(DiscoverySource.PHYSICAL_VISIT_OR_SURVEY, "station", 1d);
        var depositEvidence = permanentEvidence(DiscoverySource.PHYSICAL_VISIT_OR_SURVEY, "deposit", 2d);
        var station = knowledge(
                STATION,
                DiscoveryState.KNOWN_STATIC_LOCATION,
                "station.trade_hub",
                LocalPhysicalPosition.origin(),
                ResourceKnowledge.none(),
                stationEvidence);
        var deposit = knowledge(
                DEPOSIT,
                DiscoveryState.KNOWN_STATIC_LOCATION,
                "resource.volatiles.water",
                LocalPhysicalPosition.origin().translated(2_000d, 0d),
                new ResourceKnowledge(
                        ResourceKnowledgeLevel.CLASSIFIED_RESOURCE_FAMILY,
                        Optional.of("resource.volatiles.water"),
                        Optional.empty()),
                depositEvidence);

        Stage20DiscoveryKnowledgeState canonical =
                new Stage20DiscoveryKnowledgeState("faction.alpha", List.of(station, deposit));
        assertEquals(STATION, canonical.entries().get(0).object());
        assertEquals(DEPOSIT, canonical.entries().get(1).object());
        assertThrows(IllegalArgumentException.class,
                () -> new Stage20DiscoveryKnowledgeState("faction.alpha", List.of(station, station)));
    }

    private static StaticObservation observation(
            StaticObjectRef object,
            DiscoveryState state,
            Optional<String> classification,
            Optional<LocalPhysicalPosition> location,
            ResourceKnowledge resource,
            DiscoveryEvidence evidence) {
        return new StaticObservation(object, state, classification, location, resource, evidence);
    }

    private static Stage20DiscoveryKnowledgeState.StaticKnowledge knowledge(
            StaticObjectRef object,
            DiscoveryState state,
            String classification,
            LocalPhysicalPosition location,
            ResourceKnowledge resource,
            DiscoveryEvidence evidence) {
        return new Stage20DiscoveryKnowledgeState.StaticKnowledge(
                object,
                state,
                Optional.of(classification),
                Optional.of(location),
                resource,
                List.of(evidence),
                evidence.observedAtSeconds(),
                evidence.observedAtSeconds());
    }

    private static DiscoveryEvidence evidence(
            DiscoverySource source,
            String provenance,
            double observedAt,
            double freshUntil) {
        return new DiscoveryEvidence(source, provenance, observedAt, OptionalDouble.of(freshUntil));
    }

    private static DiscoveryEvidence permanentEvidence(
            DiscoverySource source,
            String provenance,
            double observedAt) {
        return new DiscoveryEvidence(source, provenance, observedAt, OptionalDouble.empty());
    }
}
