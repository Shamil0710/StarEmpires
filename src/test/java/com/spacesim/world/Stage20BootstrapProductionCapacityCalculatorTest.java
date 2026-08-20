package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ExportHandlingStatus;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.HeadroomStatus;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.InitialExtractionSite;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.SystemResourceConditions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20BootstrapProductionCapacityCalculatorTest {
    @Test
    void extractionCapacityMirrorsStage18MethodFacilityAndRecoveryCeilings() {
        Stage20ResourceOccurrenceWorld world = metallicExtractionWorld();

        var capacity = Stage20BootstrapProductionCapacityCalculator.extractionCapacities(
                        world,
                        Stage18ExtractionCatalogLoader.loadDefault(),
                        Stage18FacilityCatalogLoader.loadDefault())
                .get(0);

        assertEquals(25d, capacity.grossSourceKgPerSecond(), 1e-9);
        assertEquals(9.2d, capacity.recoveredOutputKgPerSecond(), 1e-9);
        assertEquals(40d, capacity.reserveLifetimeSeconds(), 1e-9);
        assertEquals(ExportHandlingStatus.UNRESOLVED, capacity.exportHandlingStatus());
        assertFalse(capacity.sustainableExportKgPerSecond().isPresent());
    }

    @Test
    void explicitSourceHandlingCapsExportWithoutChangingProcessCapacity() {
        Stage20ResourceOccurrenceWorld world = metallicExtractionWorld();

        var capacity = Stage20BootstrapProductionCapacityCalculator.extractionCapacities(
                        world,
                        Stage18ExtractionCatalogLoader.loadDefault(),
                        Stage18FacilityCatalogLoader.loadDefault(),
                        (site, source) -> OptionalDouble.of(5d))
                .get(0);

        assertEquals(9.2d, capacity.recoveredOutputKgPerSecond(), 1e-9);
        assertEquals(ExportHandlingStatus.RESOLVED, capacity.exportHandlingStatus());
        assertEquals(5d, capacity.sustainableExportKgPerSecond().orElseThrow(), 1e-9);
    }

    @Test
    void unresolvedAndInsufficientThroughputRemainExplicitDiagnostics() {
        var unresolved = Stage20BootstrapProductionCapacityCalculator.assessHeadroom(
                10d, OptionalDouble.empty(), "test");
        var insufficient = Stage20BootstrapProductionCapacityCalculator.assessHeadroom(
                10d, OptionalDouble.of(7d), "test");
        var sufficient = Stage20BootstrapProductionCapacityCalculator.assessHeadroom(
                10d, OptionalDouble.of(12d), "test");

        assertEquals(HeadroomStatus.UNRESOLVED, unresolved.status());
        assertFalse(unresolved.availableKgPerSecond().isPresent());
        assertEquals(HeadroomStatus.INSUFFICIENT, insufficient.status());
        assertEquals(-3d, insufficient.headroomKgPerSecond().orElseThrow(), 1e-9);
        assertEquals(HeadroomStatus.SUFFICIENT, sufficient.status());
        assertEquals(2d, sufficient.headroomKgPerSecond().orElseThrow(), 1e-9);
    }

    @Test
    void refineryAndComponentRowsUseOneRealInstalledFacilityEach() {
        var rows = Stage20BootstrapProductionCapacityCalculator.stationProcessCapacities(
                List.of(refineryLayout(), industrialLayout()),
                Stage18StationInfrastructureCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(),
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault());

        var structural = rows.stream()
                .filter(row -> row.processId().equals("refining.structural_alloy"))
                .filter(row -> row.stationPlacementId().equals("refinery"))
                .findFirst()
                .orElseThrow();
        var heavyRows = rows.stream()
                .filter(row -> row.processId().equals("manufacturing.component.heavy"))
                .filter(row -> row.stationPlacementId().equals("industry"))
                .toList();

        assertEquals("facility.processing.bulk_refinery", structural.facilityDefinitionId());
        assertEquals(17d, structural.theoreticalOutputKgPerSecond(), 1e-9);
        assertEquals(2, heavyRows.size());
        assertEquals(
                Set.of("facility.fabrication.assembly", "facility.fabrication.heavy"),
                heavyRows.stream().map(row -> row.facilityDefinitionId()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(heavyRows.stream().allMatch(row -> row.theoreticalOutputKgPerSecond() > 0d));
        assertTrue(rows.stream().allMatch(row -> row.theoreticalOutputKgPerSecond() > 0d));
    }

    private static Stage20ResourceOccurrenceWorld metallicExtractionWorld() {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var extraction = Stage18ExtractionCatalogLoader.loadDefault();
        var facilities = Stage18FacilityCatalogLoader.loadDefault();
        StarSystemId system = new StarSystemId(1L);
        ResourceOccurrence source = new ResourceOccurrence(
                "source.metallic",
                system,
                "field",
                "host.asteroid.free_body",
                LocalPhysicalPosition.origin(),
                "occurrence.metallic",
                ExtractionEnvironment.FREE_BODY,
                "commodity.feedstock.metallic_ore",
                1d,
                1_000d,
                0.5d,
                0.8d,
                Set.of());
        InitialExtractionSite site = new InitialExtractionSite(
                "site.metallic",
                source.sourceId(),
                system,
                source.hostAnchorId(),
                "location.free_body",
                "facility.extraction.asteroid",
                "extraction.asteroid_excavation");
        return new Stage20ResourceOccurrenceWorld(
                Stage20ResourceOccurrenceWorld.CURRENT_VERSION,
                1L,
                List.of(new SystemResourceConditions(system, Map.of("occurrence.metallic", 1d))),
                List.of(source),
                List.of(site),
                ontology.getFingerprint(),
                extraction.getFingerprint(),
                facilities.getFingerprint(),
                "test.profile");
    }

    private static Stage20LocalInfrastructureLayout refineryLayout() {
        return layout(
                new StarSystemId(2L),
                "refinery",
                "station.infrastructure.refinery_complex");
    }

    private static Stage20LocalInfrastructureLayout industrialLayout() {
        return layout(
                new StarSystemId(3L),
                "industry",
                "station.infrastructure.industrial_station");
    }

    private static Stage20LocalInfrastructureLayout layout(
            StarSystemId systemId,
            String placementId,
            String archetypeId) {
        InfrastructurePlacement station = new InfrastructurePlacement(
                placementId,
                PlacementKind.MAJOR_HUB_STATION,
                Optional.of(archetypeId),
                LocalPhysicalPosition.origin(),
                1d,
                1d);
        return new Stage20LocalInfrastructureLayout(
                Stage20LocalInfrastructureLayout.CURRENT_VERSION,
                systemId,
                1L,
                placementId,
                List.of(station),
                List.of(),
                "test.system-geometry",
                "test.route-calibration",
                "test.station-geometry",
                "test.station-defense");
    }
}
