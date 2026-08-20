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
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.InitialExtractionSite;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.SystemResourceConditions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Stage20EconomicBootstrapSingleFacilityCapabilityTest {
    @Test
    void recipeCapabilitiesCannotBeAssembledByUnioningDifferentInstalledFacilities() {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var extraction = Stage18ExtractionCatalogLoader.loadDefault();
        var facilities = Stage18FacilityCatalogLoader.parse(FACILITIES_JSON, ontology);
        var stationCatalog = Stage18StationInfrastructureCatalogLoader.parse(
                STATIONS_JSON, ontology, facilities);
        var refining = Stage18RefiningCatalogLoader.parse(REFINING_JSON, ontology);
        var manufacturing = Stage18ManufacturingCatalogLoader.loadDefault();
        StarSystemId system = new StarSystemId(1L);
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "single-facility-test",
                List.of(new SectorNode(
                        new SectorId(1L),
                        "sector",
                        List.of(new StarSystemNode(system, "system", 0d, 0d)))),
                List.of());

        ResourceOccurrence source = new ResourceOccurrence(
                "source.metal",
                system,
                "field",
                "host.free_body",
                LocalPhysicalPosition.origin(),
                "occurrence.metallic",
                ExtractionEnvironment.FREE_BODY,
                "commodity.feedstock.metallic_ore",
                1d,
                1_000_000d,
                1d,
                1d,
                Set.of());
        InitialExtractionSite site = new InitialExtractionSite(
                "site.metal",
                source.sourceId(),
                system,
                source.hostAnchorId(),
                "location.free_body",
                "facility.test.extraction",
                "extraction.asteroid_excavation");
        Stage20ResourceOccurrenceWorld world = new Stage20ResourceOccurrenceWorld(
                Stage20ResourceOccurrenceWorld.CURRENT_VERSION,
                1L,
                List.of(new SystemResourceConditions(system, Map.of("occurrence.metallic", 1d))),
                List.of(source),
                List.of(site),
                ontology.getFingerprint(),
                extraction.getFingerprint(),
                facilities.getFingerprint(),
                "test.profile");

        InfrastructurePlacement station = new InfrastructurePlacement(
                "station",
                PlacementKind.MAJOR_HUB_STATION,
                Optional.of("station.test.split"),
                LocalPhysicalPosition.origin(),
                1d,
                1d);
        Stage20LocalInfrastructureLayout layout = new Stage20LocalInfrastructureLayout(
                Stage20LocalInfrastructureLayout.CURRENT_VERSION,
                system,
                1L,
                "station",
                List.of(station),
                List.of(),
                "test.system",
                "test.route",
                "test.station",
                "test.defense");

        var report = Stage20EconomicBootstrapValidator.validate(
                topology,
                world,
                List.of(layout),
                List.of(system),
                new BootstrapRequirementProfile(
                        "test.requirements",
                        10d,
                        1d,
                        List.of(new CommodityRequirement(
                                "commodity.material.structural_alloy", 10d, 1d))),
                (origin, destination) -> Optional.of(new RouteAssessment(List.of(system), 1d, 1_000d)),
                ontology,
                extraction,
                facilities,
                stationCatalog,
                refining,
                manufacturing);

        assertFalse(report.accepted());
        assertEquals(1, report.failures().size());
        assertEquals(FailureReason.NO_PRODUCER, report.failures().get(0).reason());
    }

    private static final String FACILITIES_JSON = """
            {"schemaVersion":1,"facilities":[
              {"id":"facility.test.extraction","displayName":"Test extraction","family":"EXTRACTION",
               "capabilityTags":["capability.extraction.asteroid_excavation"],
               "ratedProcessPowerW":1000000,"engineeringWorkRate":10,"maintenanceWorkRate":1,
               "heatRejectionWPerProcessW":0.5,"requiredLaborUnitsAtFullRate":1,"automationFloorFraction":1,
               "storageClassInterfaces":["storage.dry_bulk"],"maxHandledUnitMassKg":1000000,
               "allowedLocationTags":["location.free_body"]},
              {"id":"facility.test.bulk","displayName":"Test bulk","family":"PROCESSING",
               "capabilityTags":["capability.process.bulk_refining"],
               "ratedProcessPowerW":1000000,"engineeringWorkRate":10,"maintenanceWorkRate":1,
               "heatRejectionWPerProcessW":0.5,"requiredLaborUnitsAtFullRate":1,"automationFloorFraction":1,
               "storageClassInterfaces":["storage.dry_bulk"],"maxHandledUnitMassKg":1000000,
               "allowedLocationTags":["location.orbital_station"]},
              {"id":"facility.test.chemical","displayName":"Test chemical","family":"PROCESSING",
               "capabilityTags":["capability.process.chemical_processing"],
               "ratedProcessPowerW":1000000,"engineeringWorkRate":10,"maintenanceWorkRate":1,
               "heatRejectionWPerProcessW":0.5,"requiredLaborUnitsAtFullRate":1,"automationFloorFraction":1,
               "storageClassInterfaces":["storage.dry_bulk"],"maxHandledUnitMassKg":1000000,
               "allowedLocationTags":["location.orbital_station"]}
            ]}
            """;

    private static final String STATIONS_JSON = """
            {"schemaVersion":1,"archetypes":[
              {"id":"station.test.split","displayName":"Split capability station",
               "installedFacilityDefinitionIds":["facility.test.bulk","facility.test.chemical"],
               "storageCapacityByClassKg":{"storage.dry_bulk":10000000},
               "transferStorageClassIds":["storage.dry_bulk"],
               "transferMassRateKgPerSecond":1000,"maxTransferUnitMassKg":1000000,
               "allowedLocationTags":["location.orbital_station"]}
            ]}
            """;

    private static final String REFINING_JSON = """
            {"schemaVersion":1,"recipes":[
              {"id":"refining.test.split","displayName":"Split capability recipe",
               "inputs":[{"commodityId":"commodity.feedstock.metallic_ore","fractionOfInputMass":1.0}],
               "outputCommodityId":"commodity.material.structural_alloy",
               "outputMassFraction":0.5,"discardedMassFraction":0.5,
               "requiredCapabilityTags":["capability.process.bulk_refining","capability.process.chemical_processing"],
               "energyJPerInputKg":1000,"workSecondsPerInputKg":0.1,"maintenanceWorkSecondsPerInputKg":0.01}
            ]}
            """;
}
