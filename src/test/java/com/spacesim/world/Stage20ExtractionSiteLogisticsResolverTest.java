package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20ExtractionSiteLogisticsResolver.ResolutionStatus;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.InitialExtractionSite;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.SystemResourceConditions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20ExtractionSiteLogisticsResolverTest {
    @Test
    void existingFreeBodyMiningOutpostResolvesWithoutInventingTransferRate() {
        var world = world(
                ExtractionEnvironment.FREE_BODY,
                "occurrence.metallic",
                "commodity.feedstock.metallic_ore",
                "location.free_body",
                "facility.extraction.asteroid",
                "extraction.asteroid_excavation");

        var report = Stage20ExtractionSiteLogisticsResolver.resolve(
                world,
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18StationInfrastructureCatalogLoader.loadDefault());
        var binding = report.bindings().get(0);

        assertEquals(ResolutionStatus.RESOLVED, binding.status());
        assertEquals("station.infrastructure.mining_outpost", binding.resolvedArchetypeId().orElseThrow());
        assertEquals(250_000d, binding.resolvedTransferKgPerSecond().orElseThrow(), 1e-9);

        var extractionCapacity = Stage20BootstrapProductionCapacityCalculator.extractionCapacities(
                world,
                Stage18ExtractionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(),
                report.asExportHandlingProvider()).get(0);
        assertTrue(extractionCapacity.sustainableExportKgPerSecond().isPresent());
        assertEquals(extractionCapacity.recoveredOutputKgPerSecond(),
                extractionCapacity.sustainableExportKgPerSecond().orElseThrow(), 1e-9);
    }

    @Test
    void surfaceExtractionRemainsUnresolvedWhenNoExistingArchetypeInstallsSurfaceMine() {
        var world = world(
                ExtractionEnvironment.SURFACE,
                "occurrence.metallic",
                "commodity.feedstock.metallic_ore",
                "location.surface",
                "facility.extraction.surface",
                "extraction.surface_mining");

        var report = Stage20ExtractionSiteLogisticsResolver.resolve(
                world,
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18StationInfrastructureCatalogLoader.loadDefault());
        var binding = report.bindings().get(0);

        assertEquals(ResolutionStatus.NO_COMPATIBLE_ARCHETYPE, binding.status());
        assertEquals(Set.of(), binding.compatibleArchetypeIds());
        assertTrue(binding.resolvedArchetypeId().isEmpty());
        assertTrue(binding.resolvedTransferKgPerSecond().isEmpty());
    }

    @Test
    void volatileSiteResolvesThroughExplicitStage18VolatileDepotBackfill() {
        var world = world(
                ExtractionEnvironment.VOLATILE_BEARING,
                "occurrence.volatiles",
                "commodity.feedstock.volatile_feedstock",
                "location.volatile_site",
                "facility.processing.volatiles",
                "extraction.thermal_volatiles");

        var report = Stage20ExtractionSiteLogisticsResolver.resolve(
                world,
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18StationInfrastructureCatalogLoader.loadDefault());
        var binding = report.bindings().get(0);

        assertEquals(ResolutionStatus.RESOLVED, binding.status());
        assertEquals(Set.of("station.infrastructure.volatile_depot"), binding.compatibleArchetypeIds());
        assertEquals("station.infrastructure.volatile_depot", binding.resolvedArchetypeId().orElseThrow());
        assertEquals(400_000d, binding.resolvedTransferKgPerSecond().orElseThrow(), 1e-9d);
    }

    private static Stage20ResourceOccurrenceWorld world(
            ExtractionEnvironment environment,
            String occurrenceTypeId,
            String commodityId,
            String locationTag,
            String facilityId,
            String extractionMethodId) {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var extraction = Stage18ExtractionCatalogLoader.loadDefault();
        var facilities = Stage18FacilityCatalogLoader.loadDefault();
        StarSystemId system = new StarSystemId(1L);
        ResourceOccurrence source = new ResourceOccurrence(
                "source.test",
                system,
                "field",
                "host.test",
                LocalPhysicalPosition.origin(),
                occurrenceTypeId,
                environment,
                commodityId,
                1d,
                1_000_000d,
                0.5d,
                0.8d,
                Set.of());
        InitialExtractionSite site = new InitialExtractionSite(
                "site.test",
                source.sourceId(),
                system,
                source.hostAnchorId(),
                locationTag,
                facilityId,
                extractionMethodId);
        return new Stage20ResourceOccurrenceWorld(
                Stage20ResourceOccurrenceWorld.CURRENT_VERSION,
                1L,
                List.of(new SystemResourceConditions(system, Map.of(occurrenceTypeId, 1d))),
                List.of(source),
                List.of(site),
                ontology.getFingerprint(),
                extraction.getFingerprint(),
                facilities.getFingerprint(),
                "test.profile");
    }
}
