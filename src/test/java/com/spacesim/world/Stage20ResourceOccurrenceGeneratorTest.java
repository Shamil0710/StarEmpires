package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.world.Stage20LocalInfrastructureLayoutGenerator.PlacementRequest;
import com.spacesim.world.Stage20ResourceOccurrenceGenerator.ResourceHostProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20ResourceOccurrenceGeneratorTest {
    private static final String HUB = "station.infrastructure.trade_logistics_hub";

    @Test
    void generationIsDeterministicAndIndependentOfHostOrdering() {
        long seed = 0x20E5EEDL;
        GalaxyTopology topology = topology(3);
        List<Stage20LocalInfrastructureLayout> layouts = List.of(
                layout(seed, 1L, "field-a"),
                layout(seed, 2L, "field-b"),
                layout(seed, 3L, "field-c"));
        List<ResourceHostProfile> hosts = List.of(
                freeBodyHost(1L, "field-a", Map.of(
                        "occurrence.metallic", 1.7d,
                        "occurrence.strategic_metals", 1.3d,
                        "occurrence.silicates", 0.8d)),
                freeBodyHost(2L, "field-b", Map.of(
                        "occurrence.carbonaceous", 1.8d,
                        "occurrence.water_ice", 1.2d,
                        "occurrence.metallic", 0.4d)),
                volatileHost(3L, "field-c", Map.of(
                        "occurrence.water_ice", 1.5d,
                        "occurrence.volatiles", 1.8d,
                        "occurrence.carbonaceous", 1.0d)));
        List<ResourceHostProfile> reversed = new ArrayList<>(hosts);
        Collections.reverse(reversed);

        Stage20ResourceOccurrenceWorld first = generate(seed, topology, layouts, hosts);
        Stage20ResourceOccurrenceWorld second = generate(seed, topology, layouts, reversed);

        assertEquals(first, second);
        assertEquals(Stage20ResourceOccurrenceWorld.CURRENT_VERSION, first.version());
        assertEquals(topology.systems().size(), first.systemConditions().size());
        assertEquals(Stage18ResourceOntologyLoader.loadDefault().getFingerprint(), first.ontologyFingerprint());
    }

    @Test
    void incompatibleOccurrenceEnvironmentIsNeverCreatedEvenWithMaximumHostAffinity() {
        long seed = 7L;
        GalaxyTopology topology = topology(1);
        Stage20LocalInfrastructureLayout layout = layout(seed, 1L, "field");
        ResourceHostProfile host = freeBodyHost(
                1L, "field", Map.of("occurrence.volatiles", 2d));

        Stage20ResourceOccurrenceWorld result = generate(
                seed, topology, List.of(layout), List.of(host));

        assertTrue(result.occurrences().stream()
                .noneMatch(value -> value.occurrenceTypeId().equals("occurrence.volatiles")));
        assertTrue(result.initialExtractionSites().isEmpty());
    }

    @Test
    void generatedOccurrenceProjectsDirectlyIntoFiniteStage18SourceState() {
        GeneratedWithOccurrence generated = findOccurrence();
        Stage20ResourceOccurrenceWorld.ResourceOccurrence occurrence = generated.world().occurrences().get(0);

        PhysicalSourceState runtimeState = occurrence.toPhysicalSourceState();

        assertEquals(occurrence.sourceId(), runtimeState.sourceId());
        assertEquals(occurrence.occurrenceTypeId(), runtimeState.sourceTypeId());
        assertEquals(occurrence.environment(), runtimeState.environment());
        assertEquals(occurrence.outputCommodityId(), runtimeState.outputCommodityId());
        assertEquals(occurrence.initialAccessibleMassKg(), runtimeState.initialAccessibleMassKg());
        assertEquals(occurrence.initialAccessibleMassKg(), runtimeState.remainingAccessibleMassKg());
        assertEquals(occurrence.gradeFraction(), runtimeState.gradeFraction());
        assertEquals(occurrence.sourceRecoveryFraction(), runtimeState.sourceRecoveryFraction());
        assertTrue(runtimeState.initialAccessibleMassKg() > 0d);
    }

    @Test
    void neighboringSystemsAreMoreCorrelatedThanRemoteEndsAcrossSeedPopulation() {
        GalaxyTopology topology = topology(5);
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        double neighborDifference = 0d;
        double remoteDifference = 0d;
        int samples = 0;
        for (long seed = 1L; seed <= 128L; seed++) {
            Stage20ResourceOccurrenceWorld world = Stage20ResourceOccurrenceGenerator.generate(
                    seed,
                    topology,
                    List.of(),
                    List.of(),
                    ontology,
                    Stage18ExtractionCatalogLoader.loadDefault(),
                    Stage18FacilityCatalogLoader.loadDefault());
            for (var type : ontology.getOccurrenceTypes()) {
                neighborDifference += Math.abs(
                        world.conditions(new StarSystemId(1L)).potential(type.id())
                                - world.conditions(new StarSystemId(2L)).potential(type.id()));
                remoteDifference += Math.abs(
                        world.conditions(new StarSystemId(1L)).potential(type.id())
                                - world.conditions(new StarSystemId(5L)).potential(type.id()));
                samples++;
            }
        }

        assertTrue(samples > 0);
        assertTrue(neighborDifference / samples < remoteDifference / samples);
    }

    @Test
    void initialSitesUseRealCompatibleStage18FacilitiesInsteadOfStationRoleBonuses() {
        GeneratedWithOccurrence generated = findOccurrenceWithSite();
        Stage20ResourceOccurrenceWorld world = generated.world();
        var facilities = Stage18FacilityCatalogLoader.loadDefault();
        var extraction = Stage18ExtractionCatalogLoader.loadDefault();

        assertFalse(world.initialExtractionSites().isEmpty());
        for (var site : world.initialExtractionSites()) {
            var source = world.occurrence(site.sourceId());
            var facility = facilities.findFacility(site.facilityDefinitionId());
            var method = extraction.findMethod(site.extractionMethodId());
            assertTrue(facility.allowedLocationTags().contains(site.locationTag()));
            assertTrue(facility.capabilityTags().containsAll(method.requiredCapabilityTags()));
            assertTrue(method.compatibleOccurrenceTypeIds().contains(source.occurrenceTypeId()));
            assertEquals(method.environment(), source.environment());
        }
    }

    private static Stage20ResourceOccurrenceWorld generate(
            long seed,
            GalaxyTopology topology,
            List<Stage20LocalInfrastructureLayout> layouts,
            List<ResourceHostProfile> hosts) {
        return Stage20ResourceOccurrenceGenerator.generate(
                seed,
                topology,
                layouts,
                hosts,
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ExtractionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault());
    }

    private static GeneratedWithOccurrence findOccurrence() {
        for (long seed = 1L; seed <= 10_000L; seed++) {
            GalaxyTopology topology = topology(1);
            Stage20LocalInfrastructureLayout layout = layout(seed, 1L, "field");
            Stage20ResourceOccurrenceWorld world = generate(
                    seed,
                    topology,
                    List.of(layout),
                    List.of(freeBodyHost(1L, "field", Map.of(
                            "occurrence.metallic", 2d,
                            "occurrence.silicates", 2d))));
            if (!world.occurrences().isEmpty()) {
                return new GeneratedWithOccurrence(seed, world);
            }
        }
        throw new AssertionError("No occurrence found in deterministic seed search");
    }

    private static GeneratedWithOccurrence findOccurrenceWithSite() {
        for (long seed = 1L; seed <= 20_000L; seed++) {
            GalaxyTopology topology = topology(1);
            Stage20LocalInfrastructureLayout layout = layout(seed, 1L, "field");
            Stage20ResourceOccurrenceWorld world = generate(
                    seed,
                    topology,
                    List.of(layout),
                    List.of(freeBodyHost(1L, "field", Map.of("occurrence.metallic", 2d))));
            if (!world.initialExtractionSites().isEmpty()) {
                return new GeneratedWithOccurrence(seed, world);
            }
        }
        throw new AssertionError("No initial extraction site found in deterministic seed search");
    }

    private static ResourceHostProfile freeBodyHost(
            long systemId,
            String anchor,
            Map<String, Double> affinities) {
        return new ResourceHostProfile(
                new StarSystemId(systemId),
                anchor,
                "host.asteroid.free_body",
                ExtractionEnvironment.FREE_BODY,
                "location.free_body",
                affinities,
                Set.of());
    }

    private static ResourceHostProfile volatileHost(
            long systemId,
            String anchor,
            Map<String, Double> affinities) {
        return new ResourceHostProfile(
                new StarSystemId(systemId),
                anchor,
                "host.volatile_bearing",
                ExtractionEnvironment.VOLATILE_BEARING,
                "location.volatile_site",
                affinities,
                Set.of());
    }

    private static Stage20LocalInfrastructureLayout layout(long seed, long systemId, String resourceAnchor) {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(seed, new StarSystemId(systemId));
        return Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry,
                geometry.centralReference().translated(10_000_000d, 20_000_000d),
                "hub-" + systemId,
                HUB,
                List.of(PlacementRequest.resourceFieldAnchor(resourceAnchor)));
    }

    private static GalaxyTopology topology(int systems) {
        ArrayList<StarSystemNode> nodes = new ArrayList<>();
        ArrayList<JumpConnection> edges = new ArrayList<>();
        for (int value = 1; value <= systems; value++) {
            nodes.add(new StarSystemNode(new StarSystemId(value), "System " + value, value * 10d, 0d));
            if (value > 1) {
                edges.add(new JumpConnection(new StarSystemId(value - 1L), new StarSystemId(value)));
            }
        }
        return new GalaxyTopology(
                new GalaxyId(1L),
                "Stage20E test galaxy",
                List.of(new SectorNode(new SectorId(1L), "Test sector", nodes)),
                edges);
    }

    private record GeneratedWithOccurrence(long seed, Stage20ResourceOccurrenceWorld world) {
    }
}
