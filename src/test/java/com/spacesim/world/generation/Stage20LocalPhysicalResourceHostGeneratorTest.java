package com.spacesim.world.generation;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.Stage20LocalInfrastructureLayout;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20LocalInfrastructureLayoutGenerator;
import com.spacesim.world.Stage20LocalInfrastructureLayoutGenerator.PlacementRequest;
import com.spacesim.world.Stage20ResourceOccurrenceGenerator;
import com.spacesim.world.Stage20ResourceOccurrenceWorld;
import com.spacesim.world.Stage20SystemGeometry;
import com.spacesim.world.Stage20SystemGeometryGenerator;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20LocalPhysicalResourceHostGeneratorTest {
    private static final String HUB = "station.infrastructure.trade_logistics_hub";

    @Test
    void generationIsDeterministicAndIndependentOfLayoutOrdering() {
        long seed = 0x20B20E5L;
        GalaxyTopology topology = topology(3);
        List<Stage20LocalInfrastructureLayout> layouts = List.of(
                layout(seed, 1L, "field-a", "field-b"),
                layout(seed, 2L, "field-c"),
                layout(seed, 3L, "field-d"));
        ArrayList<Stage20LocalInfrastructureLayout> reversed = new ArrayList<>(layouts);
        Collections.reverse(reversed);

        var first = generate(seed, topology, layouts);
        var second = generate(seed, topology, reversed);

        assertEquals(first, second);
        assertEquals(Stage20LocalPhysicalResourceHostGenerator.CURRENT_VERSION, first.version());
        assertEquals(Stage18ExtractionCatalogLoader.loadDefault().getFingerprint(), first.extractionCatalogFingerprint());
        assertEquals(4, first.hosts().size());
    }

    @Test
    void everyResourceAnchorGetsOnePhysicalHostAtTheExactAuthoritativePosition() {
        long seed = 91L;
        GalaxyTopology topology = topology(2);
        List<Stage20LocalInfrastructureLayout> layouts = List.of(
                layout(seed, 1L, "ore-a", "ore-b"),
                layout(seed, 2L, "ore-c"));

        var generated = generate(seed, topology, layouts);

        long expectedAnchors = layouts.stream()
                .flatMap(value -> value.placements().stream())
                .filter(value -> value.kind() == PlacementKind.RESOURCE_FIELD_ANCHOR)
                .count();
        assertEquals(expectedAnchors, generated.hosts().size());
        for (var host : generated.hosts()) {
            Stage20LocalInfrastructureLayout layout = layouts.stream()
                    .filter(value -> value.systemId().equals(host.systemId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(host.position(), layout.placement(host.anchorId()).position());
            assertEquals(PlacementKind.RESOURCE_FIELD_ANCHOR, layout.placement(host.anchorId()).kind());
        }
    }

    @Test
    void generatedProfilesUseOnlyRealStage18NaturalExtractionCompatibility() {
        long seed = 17L;
        GalaxyTopology topology = topology(4);
        List<Stage20LocalInfrastructureLayout> layouts = List.of(
                layout(seed, 1L, "field-a"),
                layout(seed, 2L, "field-b"),
                layout(seed, 3L, "field-c"),
                layout(seed, 4L, "field-d"));
        Stage18ExtractionCatalog extraction = Stage18ExtractionCatalogLoader.loadDefault();

        var generated = Stage20LocalPhysicalResourceHostGenerator.generate(seed, topology, layouts, extraction);

        assertFalse(generated.hosts().isEmpty());
        assertEquals(generated.hosts().size(), generated.resourceHostProfiles().size());
        for (var host : generated.hosts()) {
            Set<String> compatible = extraction.getMethods().stream()
                    .filter(value -> value.sourceKind() == SourceKind.NATURAL_OCCURRENCE)
                    .filter(value -> value.environment() == host.hostClass().environment())
                    .flatMap(value -> value.compatibleOccurrenceTypeIds().stream())
                    .collect(java.util.stream.Collectors.toSet());
            assertFalse(compatible.isEmpty());
            assertEquals(compatible, host.occurrenceAffinityByTypeId().keySet());
            assertTrue(host.occurrenceAffinityByTypeId().values().stream().allMatch(value -> value == 1d));

            var profile = host.toResourceHostProfile();
            assertEquals(host.systemId(), profile.systemId());
            assertEquals(host.anchorId(), profile.anchorId());
            assertEquals(host.hostClass().hostClassId(), profile.hostClassId());
            assertEquals(host.hostClass().environment(), profile.environment());
            assertEquals(host.hostClass().locationTag(), profile.locationTag());
            assertEquals(host.occurrenceAffinityByTypeId(), profile.occurrenceAffinityByTypeId());
        }
    }

    @Test
    void generatedHostsFeedStage20EOccurrenceGenerationWithoutHandAuthoredProfiles() {
        GeneratedWorld generatedWorld = firstWorldWithOccurrence();
        var physicalHosts = generatedWorld.hosts();
        Stage20ResourceOccurrenceWorld resources = generatedWorld.resources();

        assertFalse(resources.occurrences().isEmpty());
        for (var occurrence : resources.occurrences()) {
            var host = physicalHosts.host(occurrence.systemId(), occurrence.anchorId());
            assertEquals(host.position(), occurrence.position());
            assertEquals(host.hostClass().hostClassId(), occurrence.hostClassId());
            assertEquals(host.hostClass().environment(), occurrence.environment());
            assertTrue(host.occurrenceAffinityByTypeId().containsKey(occurrence.occurrenceTypeId()));
        }
    }

    private static GeneratedWorld firstWorldWithOccurrence() {
        for (long seed = 1L; seed <= 512L; seed++) {
            GalaxyTopology topology = topology(3);
            List<Stage20LocalInfrastructureLayout> layouts = List.of(
                    layout(seed, 1L, "field-a"),
                    layout(seed, 2L, "field-b"),
                    layout(seed, 3L, "field-c"));
            var hosts = generate(seed, topology, layouts);
            Stage20ResourceOccurrenceWorld resources = Stage20ResourceOccurrenceGenerator.generate(
                    seed,
                    topology,
                    layouts,
                    hosts.resourceHostProfiles(),
                    Stage18ResourceOntologyLoader.loadDefault(),
                    Stage18ExtractionCatalogLoader.loadDefault(),
                    Stage18FacilityCatalogLoader.loadDefault());
            if (!resources.occurrences().isEmpty()) {
                return new GeneratedWorld(hosts, resources);
            }
        }
        throw new AssertionError("No Stage-20E occurrence generated from physical hosts in deterministic seed corpus");
    }

    private static Stage20LocalPhysicalResourceHostGenerator.GenerationResult generate(
            long seed,
            GalaxyTopology topology,
            List<Stage20LocalInfrastructureLayout> layouts) {
        return Stage20LocalPhysicalResourceHostGenerator.generate(
                seed, topology, layouts, Stage18ExtractionCatalogLoader.loadDefault());
    }

    private static Stage20LocalInfrastructureLayout layout(long seed, long systemId, String... anchors) {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(seed, new StarSystemId(systemId));
        List<PlacementRequest> requests = java.util.Arrays.stream(anchors)
                .map(PlacementRequest::resourceFieldAnchor)
                .toList();
        return Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry,
                geometry.centralReference().translated(10_000_000d, 20_000_000d),
                "hub-" + systemId,
                HUB,
                requests);
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
                "Stage20 local physical host test galaxy",
                List.of(new SectorNode(new SectorId(1L), "Test sector", nodes)),
                edges);
    }

    private record GeneratedWorld(
            Stage20LocalPhysicalResourceHostGenerator.GenerationResult hosts,
            Stage20ResourceOccurrenceWorld resources) {
    }
}
