package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20EconomicBootstrapValidator.BootstrapRequirementProfile;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.FailureReason;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20LocalInfrastructureLayoutGenerator.PlacementRequest;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.InitialExtractionSite;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.SystemResourceConditions;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20EconomicBootstrapValidatorTest {
    private static final String HUB = "station.infrastructure.trade_logistics_hub";

    @Test
    void refineryChainCanImportPhysicalFeedstockAndSupplyEssentialStart() {
        GalaxyTopology topology = chainTopology();
        Stage20ResourceOccurrenceWorld world = metallicWorld();
        List<Stage20LocalInfrastructureLayout> layouts = List.of(
                stationLayout(17L, 1L, "station.infrastructure.trade_logistics_hub"),
                stationLayout(17L, 2L, "station.infrastructure.refinery_complex"),
                stationLayout(17L, 3L, "station.infrastructure.trade_logistics_hub"));
        BootstrapRequirementProfile requirements = new BootstrapRequirementProfile(
                "test.physical-route.v1",
                10_000d,
                10d,
                List.of(new CommodityRequirement(
                        "commodity.material.structural_alloy", 10_000d, 10d)));

        var report = Stage20EconomicBootstrapValidator.validate(
                topology,
                world,
                layouts,
                List.of(new StarSystemId(3L)),
                requirements,
                bfsRouteEvaluator(topology),
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ExtractionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(),
                Stage18StationInfrastructureCatalogLoader.loadDefault(),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault());

        assertTrue(report.accepted());
        assertTrue(report.failures().isEmpty());
        assertEquals(Set.of(new StarSystemId(2L)),
                report.producerSystemsByCommodity().get("commodity.material.structural_alloy"));
        assertEquals(new StarSystemId(2L), report.requirementEvidence().get(0).producerSystemId());
        assertEquals(List.of(new StarSystemId(2L), new StarSystemId(3L)),
                report.requirementEvidence().get(0).route().orderedSystems());
    }

    @Test
    void missingEssentialChainIsRejectedWithoutInjectingFallbackResource() {
        GalaxyTopology topology = chainTopology();
        Stage20ResourceOccurrenceWorld world = metallicWorld();
        BootstrapRequirementProfile requirements = new BootstrapRequirementProfile(
                "test.no-fallback.v1",
                20_000d,
                1d,
                List.of(new CommodityRequirement(
                        "commodity.consumable.reactor_fuel", 20_000d, 1d)));

        var report = Stage20EconomicBootstrapValidator.validate(
                topology,
                world,
                List.of(stationLayout(17L, 2L, "station.infrastructure.refinery_complex")),
                List.of(new StarSystemId(3L)),
                requirements,
                bfsRouteEvaluator(topology),
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ExtractionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(),
                Stage18StationInfrastructureCatalogLoader.loadDefault(),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault());

        assertFalse(report.accepted());
        assertEquals(1, report.failures().size());
        assertEquals(FailureReason.NO_PRODUCER, report.failures().get(0).reason());
        assertFalse(report.producerSystemsByCommodity().containsKey("commodity.feedstock.fissile_minerals"));
        assertFalse(report.producerSystemsByCommodity().containsKey("commodity.consumable.reactor_fuel"));
    }

    @Test
    void routeEvaluatorCannotSmuggleNonNeighborShortcutIntoBootstrapAcceptance() {
        GalaxyTopology topology = chainTopology();
        Stage20ResourceOccurrenceWorld world = metallicWorld();
        BootstrapRequirementProfile requirements = new BootstrapRequirementProfile(
                "test.reject-shortcut.v1",
                20_000d,
                1d,
                List.of(new CommodityRequirement(
                        "commodity.feedstock.metallic_ore", 20_000d, 1d)));

        assertThrows(IllegalArgumentException.class, () -> Stage20EconomicBootstrapValidator.validate(
                topology,
                world,
                List.of(),
                List.of(new StarSystemId(3L)),
                requirements,
                (origin, destination) -> Optional.of(new RouteAssessment(
                        List.of(origin, destination), 1d, 1000d)),
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ExtractionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(),
                Stage18StationInfrastructureCatalogLoader.loadDefault(),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault()));
    }

    @Test
    void calibratedRouteLimitsCanRejectReachableButEconomicallyUnusableSupplier() {
        GalaxyTopology topology = chainTopology();
        Stage20ResourceOccurrenceWorld world = metallicWorld();
        BootstrapRequirementProfile requirements = new BootstrapRequirementProfile(
                "test.route-envelope.v1",
                20_000d,
                1d,
                List.of(new CommodityRequirement(
                        "commodity.feedstock.metallic_ore", 1_000d, 1d)));

        var report = Stage20EconomicBootstrapValidator.validate(
                topology,
                world,
                List.of(),
                List.of(new StarSystemId(3L)),
                requirements,
                bfsRouteEvaluator(topology),
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ExtractionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(),
                Stage18StationInfrastructureCatalogLoader.loadDefault(),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault());

        assertFalse(report.accepted());
        assertEquals(FailureReason.NO_FEASIBLE_ROUTE, report.failures().get(0).reason());
    }

    private static Stage20ResourceOccurrenceWorld metallicWorld() {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var extraction = Stage18ExtractionCatalogLoader.loadDefault();
        var facilities = Stage18FacilityCatalogLoader.loadDefault();
        ResourceOccurrence occurrence = new ResourceOccurrence(
                "source.stage20e.test.metallic",
                new StarSystemId(1L),
                "field-metal",
                "host.asteroid.free_body",
                LocalPhysicalPosition.origin(),
                "occurrence.metallic",
                ExtractionEnvironment.FREE_BODY,
                "commodity.feedstock.metallic_ore",
                1d,
                1.0e10d,
                0.5d,
                0.9d,
                Set.of());
        InitialExtractionSite site = new InitialExtractionSite(
                "site.stage20e.test.metallic",
                occurrence.sourceId(),
                occurrence.systemId(),
                occurrence.hostAnchorId(),
                "location.free_body",
                "facility.extraction.asteroid",
                "extraction.asteroid_excavation");
        return new Stage20ResourceOccurrenceWorld(
                Stage20ResourceOccurrenceWorld.CURRENT_VERSION,
                17L,
                List.of(new SystemResourceConditions(
                        new StarSystemId(1L), Map.of("occurrence.metallic", 1d))),
                List.of(occurrence),
                List.of(site),
                ontology.getFingerprint(),
                extraction.getFingerprint(),
                facilities.getFingerprint(),
                "stage20e.resource-geography.v1");
    }

    private static Stage20LocalInfrastructureLayout stationLayout(
            long seed,
            long systemId,
            String stationArchetype) {
        Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(seed, new StarSystemId(systemId));
        List<PlacementRequest> requests = stationArchetype.equals(HUB)
                ? List.of()
                : List.of(PlacementRequest.independentStation("industry-" + systemId, stationArchetype));
        return Stage20LocalInfrastructureLayoutGenerator.generate(
                geometry,
                geometry.centralReference().translated(10_000_000d, 20_000_000d),
                "hub-" + systemId,
                HUB,
                requests);
    }

    private static GalaxyTopology chainTopology() {
        List<StarSystemNode> systems = List.of(
                new StarSystemNode(new StarSystemId(1L), "A", 0d, 0d),
                new StarSystemNode(new StarSystemId(2L), "B", 10d, 0d),
                new StarSystemNode(new StarSystemId(3L), "C", 20d, 0d));
        return new GalaxyTopology(
                new GalaxyId(1L),
                "Bootstrap test",
                List.of(new SectorNode(new SectorId(1L), "Sector", systems)),
                List.of(
                        new JumpConnection(new StarSystemId(1L), new StarSystemId(2L)),
                        new JumpConnection(new StarSystemId(2L), new StarSystemId(3L))));
    }

    private static Stage20EconomicBootstrapValidator.RouteEvaluator bfsRouteEvaluator(GalaxyTopology topology) {
        return (origin, destination) -> {
            if (origin.equals(destination)) {
                return Optional.of(new RouteAssessment(List.of(origin), 0d, 1000d));
            }
            ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
            Map<StarSystemId, StarSystemId> parent = new HashMap<>();
            queue.add(origin);
            parent.put(origin, null);
            while (!queue.isEmpty()) {
                StarSystemId current = queue.removeFirst();
                if (current.equals(destination)) {
                    break;
                }
                for (StarSystemId neighbor : topology.neighbors(current)) {
                    if (!parent.containsKey(neighbor)) {
                        parent.put(neighbor, current);
                        queue.addLast(neighbor);
                    }
                }
            }
            if (!parent.containsKey(destination)) {
                return Optional.empty();
            }
            ArrayList<StarSystemId> reverse = new ArrayList<>();
            for (StarSystemId cursor = destination; cursor != null; cursor = parent.get(cursor)) {
                reverse.add(cursor);
            }
            java.util.Collections.reverse(reverse);
            int hops = reverse.size() - 1;
            return Optional.of(new RouteAssessment(reverse, hops * 3600d, 1000d));
        };
    }
}
