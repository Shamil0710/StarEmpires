package com.spacesim.economy;

import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18StationProductionBridgeTest {
    private static final double TOLERANCE = 1e-7d;

    private Stage18ResourceOntologyCatalog ontology;
    private Stage18ManufacturingProductRegistry products;
    private Stage18FacilityRuntime facilityRuntime;
    private Stage18StationProductionBridge bridge;

    @BeforeEach
    void setUp() {
        ontology = Stage18ResourceOntologyLoader.loadDefault();
        products = Stage18ManufacturingProductRegistry.loadDefault();
        facilityRuntime = new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        bridge = new Stage18StationProductionBridge(
                ontology,
                products,
                facilityRuntime,
                new Stage18ExtractionRuntime(ontology, Stage18ExtractionCatalogLoader.loadDefault()),
                new Stage18RefiningRuntime(ontology, Stage18RefiningCatalogLoader.loadDefault()),
                new Stage18ManufacturingRuntime(
                        ontology, Stage18ManufacturingCatalogLoader.loadDefault(), products));
    }

    @Test
    void extractionCommitsRecoveredMassIntoCanonicalStationStorage() {
        Stage18StationStorage storage = storage(
                "station.test.mine",
                Map.of("storage.dry_bulk", 1_000d),
                Map.of(),
                Map.of());
        PhysicalSourceState source = new PhysicalSourceState(
                "source.test.metallic",
                SourceKind.NATURAL_OCCURRENCE,
                "occurrence.metallic",
                ExtractionEnvironment.FREE_BODY,
                "commodity.feedstock.metallic_ore",
                1_000d,
                1_000d,
                0.5d,
                0.8d,
                Set.of());

        var result = bridge.extractToStation(
                source,
                "extraction.asteroid_excavation",
                100d,
                storage,
                facility("facility.extraction.asteroid", 4_000_000d, 1_400_000d, 12d, 0.2d, "location.free_body"),
                10d);

        assertTrue(result.committed());
        assertEquals(36.8d, storage.commodityMassKg("commodity.feedstock.metallic_ore"), TOLERANCE);
        assertEquals(900d, source.remainingAccessibleMassKg(), TOLERANCE);
    }

    @Test
    void refiningCommitsInputConsumptionAndMaterialOutputIntoSameCanonicalStorage() {
        Stage18StationStorage storage = storage(
                "station.test.refinery",
                Map.of("storage.dry_bulk", 1_000d),
                Map.of("commodity.feedstock.metallic_ore", 100d),
                Map.of());

        var result = bridge.refineAtStation(
                "refining.structural_alloy",
                50d,
                storage,
                facility("facility.processing.bulk_refinery", 100_000_000d, 75_000_000d, 80d, 5d, "location.orbital_station"),
                2d);

        assertTrue(result.accepted());
        assertEquals(50d, storage.commodityMassKg("commodity.feedstock.metallic_ore"), TOLERANCE);
        assertEquals(34d, storage.commodityMassKg("commodity.material.structural_alloy"), TOLERANCE);
    }

    @Test
    void componentManufacturingCommitsThroughCanonicalStationInventory() {
        Stage18StationStorage storage = storage(
                "station.test.fabrication",
                Map.of(
                        "storage.dry_bulk", 10_000d,
                        "storage.hazardous_controlled", 10_000d,
                        "storage.high_value_controlled", 10_000d,
                        "storage.oversized", 10_000d),
                Map.of(
                        "commodity.material.structural_alloy", 1_000d,
                        "commodity.material.light_alloy", 1_000d,
                        "commodity.material.refractory_alloy", 1_000d,
                        "commodity.material.conductor_metal", 1_000d,
                        "commodity.material.industrial_chemicals", 1_000d,
                        "commodity.material.carbon_material", 1_000d),
                Map.of());

        var result = bridge.manufactureComponentAtStation(
                "manufacturing.component.heavy",
                100d,
                storage,
                facility("facility.fabrication.heavy", 80_000_000d, 44_000_000d, 80d, 4d, "location.orbital_station"),
                10d);

        assertTrue(result.accepted());
        assertEquals(100d, storage.commodityMassKg("commodity.component.heavy_components"), TOLERANCE);
        assertEquals(945d, storage.commodityMassKg("commodity.material.structural_alloy"), TOLERANCE);
    }

    @Test
    void ordnanceManufacturingCreatesCountableProductWithoutSeparateInventoryTruth() {
        Stage18StationStorage storage = storage(
                "station.test.ordnance",
                Map.of(
                        "storage.dry_bulk", 10_000d,
                        "storage.general_container", 10_000d,
                        "storage.hazardous_controlled", 2_000d,
                        "storage.high_value_controlled", 10_000d),
                Map.of(
                        "commodity.material.light_alloy", 2_000d,
                        "commodity.material.structural_alloy", 2_000d,
                        "commodity.material.industrial_chemicals", 1_000d,
                        "commodity.component.electrical_components", 2_000d,
                        "commodity.component.precision_components", 2_000d,
                        "commodity.material.conductor_metal", 2_000d,
                        "commodity.material.carbon_material", 2_000d),
                Map.of());

        var result = bridge.manufactureProductAtStation(
                "ammo.interceptor_1t_v1",
                1,
                storage,
                facility("facility.fabrication.ordnance", 100_000_000d, 55_000_000d, 100d, 4d, "location.orbital_station"),
                300d);

        assertTrue(result.accepted());
        assertEquals(1, storage.productCount("ammo.interceptor_1t_v1"));
        assertEquals(660d, storage.commodityMassKg("commodity.material.industrial_chemicals"), TOLERANCE);
        assertEquals(1_660d, storage.usedCapacityKg("storage.hazardous_controlled"), TOLERANCE);
    }

    @Test
    void rejectedManufacturingLeavesCanonicalStationStorageUnchanged() {
        Stage18StationStorage storage = storage(
                "station.test.reject",
                Map.of(
                        "storage.dry_bulk", 10_000d,
                        "storage.general_container", 10_000d,
                        "storage.hazardous_controlled", 2_000d,
                        "storage.high_value_controlled", 10_000d,
                        "storage.oversized", 10_000d),
                Map.of(
                        "commodity.material.light_alloy", 2_000d,
                        "commodity.material.structural_alloy", 2_000d,
                        "commodity.material.industrial_chemicals", 1_000d,
                        "commodity.component.electrical_components", 2_000d,
                        "commodity.component.precision_components", 2_000d,
                        "commodity.material.conductor_metal", 2_000d,
                        "commodity.material.carbon_material", 2_000d),
                Map.of());
        var before = storage.snapshot();

        var result = bridge.manufactureProductAtStation(
                "ammo.interceptor_1t_v1",
                1,
                storage,
                facility("facility.fabrication.heavy", 80_000_000d, 44_000_000d, 80d, 4d, "location.orbital_station"),
                300d);

        assertEquals(Stage18ManufacturingRuntime.Status.MISSING_CAPABILITY, result.status());
        assertEquals(before, storage.snapshot());
    }

    private FacilityCapabilitySnapshot facility(
            String definitionId,
            double powerW,
            double heatW,
            double labor,
            double maintenanceRate,
            String location) {
        return facilityRuntime.project(new InstalledFacilityState(
                "facility.instance." + definitionId,
                definitionId,
                1d,
                powerW,
                heatW,
                labor,
                maintenanceRate,
                location,
                true));
    }

    private Stage18StationStorage storage(
            String stationId,
            Map<String, Double> capacities,
            Map<String, Double> commodities,
            Map<String, Integer> productCounts) {
        return new Stage18StationStorage(
                ontology, products, stationId, capacities, commodities, productCounts);
    }
}
