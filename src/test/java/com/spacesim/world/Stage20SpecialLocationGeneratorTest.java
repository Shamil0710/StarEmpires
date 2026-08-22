package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.persistence.Stage20DiscoveryPersistenceCodec;
import com.spacesim.persistence.Stage20DiscoveryPersistentState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledgeLevel;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20SpecialLocationDiscovery.ObservationMethod;
import com.spacesim.world.Stage20SpecialLocationWorld.CoordinateDomain;
import com.spacesim.world.Stage20SpecialLocationWorld.LocationKind;
import com.spacesim.world.Stage20SpecialLocationWorld.SecurityAssessment;
import com.spacesim.world.Stage20SpecialLocationWorld.SpecialLocation;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20SpecialLocationGeneratorTest {
    private static final ResolvedProbeResult ACCEPTED =
            Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(1L);

    @Test
    void acceptedWorldProducesDeterministicPhysicalLocationsAndFiniteStage18Value() {
        Stage20SpecialLocationWorld first = Stage20SpecialLocationGenerator.generateCurrent(ACCEPTED);
        Stage20SpecialLocationWorld repeated = Stage20SpecialLocationGenerator.generateCurrent(ACCEPTED);

        assertEquals(first, repeated);
        assertEquals(Stage20SpecialLocationWorld.CURRENT_VERSION, first.version());
        assertEquals(ACCEPTED.rootSeed(), first.rootSeed());
        assertEquals(ACCEPTED.version(), first.resolvedProbeVersion());
        assertEquals(
                EnumSet.allOf(LocationKind.class),
                first.locations().stream().map(SpecialLocation::kind).collect(Collectors.toSet()));
        assertEquals(first.locations().size(), first.locations().stream()
                .map(SpecialLocation::locationId).distinct().count());

        var occurrenceById = ACCEPTED.generation().resourceWorld().orElseThrow().occurrences().stream()
                .collect(Collectors.toMap(
                        Stage20ResourceOccurrenceWorld.ResourceOccurrence::sourceId,
                        value -> value));
        for (SpecialLocation location : first.locations()) {
            assertEquals(CoordinateDomain.LOCAL_SYSTEM_SI, location.coordinateDomain());
            assertEquals(SecurityAssessment.UNASSESSED, location.securityAssessment());
            assertTrue(location.nearestTrafficDistanceM() > 0d);
            assertTrue(location.miningShipApproachTimeS() > 0d);
            assertFalse(location.hazardTags().isEmpty());
            assertTrue(ACCEPTED.generation().topology().requireAcceptedTopology()
                    .findSystem(location.systemId()).isPresent());

            if (location.kind() == LocationKind.DERELICT) {
                assertFalse(location.salvageStreams().isEmpty());
                assertEquals(
                        location.finiteRecoverableValueKg(),
                        location.salvageStreams().stream()
                                .mapToDouble(value -> value.accessibleMassKg()).sum(),
                        1e-6d);
                assertTrue(location.salvageStreams().stream()
                        .allMatch(value -> value.accessibleMassKg() < value.constructedMassKg()));
                assertTrue(location.salvageSources().stream().allMatch(source ->
                        source.sourceKind() == SourceKind.SALVAGE_STREAM
                                && source.environment() == ExtractionEnvironment.SALVAGE_SITE
                                && source.remainingAccessibleMassKg() > 0d
                                && source.requiredCapabilityTags().contains("capability.process.recycling")));
            } else if (location.kind() == LocationKind.RESOURCE_PHENOMENON) {
                var occurrence = occurrenceById.get(location.linkedResourceSourceId().orElseThrow());
                assertTrue(occurrence != null);
                assertEquals(
                        occurrence.initialAccessibleMassKg()
                                * occurrence.gradeFraction()
                                * occurrence.sourceRecoveryFraction(),
                        location.finiteRecoverableValueKg(),
                        1e-6d);
                assertTrue(location.salvageStreams().isEmpty());
            } else {
                assertEquals(0d, location.finiteRecoverableValueKg(), 0d);
                assertTrue(location.linkedResourceSourceId().isEmpty());
                assertTrue(location.salvageStreams().isEmpty());
            }
        }
    }

    @Test
    void observationQualityDoesNotBootstrapOmniscienceAndSpecialKnowledgePersists() {
        Stage20SpecialLocationWorld world = Stage20SpecialLocationGenerator.generateCurrent(ACCEPTED);
        SpecialLocation resource = world.locations().stream()
                .filter(value -> value.kind() == LocationKind.RESOURCE_PHENOMENON)
                .findFirst().orElseThrow();
        Stage20DiscoveryKnowledgeRuntime runtime = new Stage20DiscoveryKnowledgeRuntime();
        Stage20DiscoveryKnowledgeState knowledge = new Stage20DiscoveryKnowledgeState("faction.test", List.of());

        var active = Stage20SpecialLocationDiscovery.observe(
                resource,
                ObservationMethod.ACTIVE_SCAN,
                new DiscoveryEvidence(
                        DiscoverySource.ACTIVE_SCAN,
                        "scan.resource-contact",
                        10d,
                        OptionalDouble.of(70d)));
        knowledge = runtime.observe(knowledge, active);
        assertEquals(DiscoveryState.DETECTED, knowledge.discoveryState(active.object()));
        assertTrue(knowledge.knowledge(active.object()).orElseThrow().classificationId().isEmpty());

        var survey = Stage20SpecialLocationDiscovery.observe(
                resource,
                ObservationMethod.PHYSICAL_SURVEY,
                new DiscoveryEvidence(
                        DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                        "survey.resource-contact",
                        100d,
                        OptionalDouble.empty()));
        knowledge = runtime.observe(knowledge, survey);
        var surveyed = knowledge.knowledge(survey.object()).orElseThrow();
        assertEquals(StaticObjectKind.SPECIAL_LOCATION, surveyed.object().kind());
        assertEquals(DiscoveryState.KNOWN_STATIC_LOCATION, surveyed.state());
        assertEquals(resource.position(), surveyed.knownLocation().orElseThrow());
        assertEquals(ResourceKnowledgeLevel.NONE, surveyed.resourceKnowledge().level());

        Stage20DiscoveryPersistentState persistent = new Stage20DiscoveryPersistentState(
                Stage20DiscoveryPersistentState.CURRENT_VERSION,
                world.rootSeed(),
                world.resolvedProbeVersion(),
                "stage20h-test-world-fingerprint",
                List.of(knowledge));
        byte[] bytes = Stage20DiscoveryPersistenceCodec.encode(persistent);
        assertTrue(Arrays.equals(bytes, Stage20DiscoveryPersistenceCodec.encode(persistent)));
        assertEquals(persistent, Stage20DiscoveryPersistenceCodec.decode(bytes));

        assertThrows(
                IllegalArgumentException.class,
                () -> Stage20SpecialLocationDiscovery.observe(
                        resource,
                        ObservationMethod.PASSIVE_SENSOR,
                        new DiscoveryEvidence(
                                DiscoverySource.ACTIVE_SCAN,
                                "mismatched-source",
                                1d,
                                OptionalDouble.of(2d))));
    }
}
