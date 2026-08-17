package com.spacesim.economy;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionResult;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalCargoStore;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18FacilityRuntime.Status;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingInventory;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingResult;
import com.spacesim.economy.Stage18RefiningRuntime.PhysicalMaterialStore;
import com.spacesim.economy.Stage18RefiningRuntime.RefiningResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18FacilityRuntimeTest {
    private static final double TOLERANCE = 1e-7d;

    private Stage18ResourceOntologyCatalog ontology;
    private Stage18FacilityCatalog facilities;
    private Stage18FacilityRuntime runtime;

    @BeforeEach
    void setUp() {
        ontology = Stage18ResourceOntologyLoader.loadDefault();
        facilities = Stage18FacilityCatalogLoader.loadDefault();
        runtime = new Stage18FacilityRuntime(facilities);
    }

    @Test
    void pristineFullySuppliedBulkRefineryProjectsItsRatedPhysicalCapacity() {
        FacilityCapabilitySnapshot snapshot = runtime.project(state(
                "facility.processing.bulk_refinery",
                1d,
                100_000_000d,
                75_000_000d,
                80d,
                5d,
                "location.orbital_station",
                true));

        assertEquals(Status.ACTIVE, snapshot.status());
        assertEquals(100_000_000d, snapshot.effectiveProcessPowerW(), TOLERANCE);
        assertEquals(50d, snapshot.effectiveEngineeringWorkRate(), TOLERANCE);
        assertEquals(5d, snapshot.effectiveMaintenanceWorkRate(), TOLERANCE);
        assertEquals(1d, snapshot.effectiveThroughputFraction(), TOLERANCE);
        assertEquals(75_000_000d, snapshot.requiredHeatRejectionW(), TOLERANCE);
        assertTrue(snapshot.capabilityTags().contains("capability.process.bulk_refining"));
        assertTrue(snapshot.supportsStorageClass("storage.dry_bulk"));
    }

    @Test
    void heatRejectionAndLaborCreateIndependentPhysicalBottlenecks() {
        FacilityCapabilitySnapshot heatLimited = runtime.project(state(
                "facility.processing.bulk_refinery",
                1d,
                100_000_000d,
                37_500_000d,
                80d,
                5d,
                "location.orbital_station",
                true));
        FacilityCapabilitySnapshot unstaffed = runtime.project(state(
                "facility.processing.bulk_refinery",
                1d,
                100_000_000d,
                75_000_000d,
                0d,
                5d,
                "location.orbital_station",
                true));

        assertEquals(50_000_000d, heatLimited.effectiveProcessPowerW(), TOLERANCE);
        assertEquals(25d, heatLimited.effectiveEngineeringWorkRate(), TOLERANCE);
        assertEquals(0.5d, heatLimited.effectiveThroughputFraction(), TOLERANCE);

        assertEquals(100_000_000d, unstaffed.effectiveProcessPowerW(), TOLERANCE);
        assertEquals(15d, unstaffed.effectiveEngineeringWorkRate(), TOLERANCE);
        assertEquals(0.30d, unstaffed.effectiveThroughputFraction(), TOLERANCE);
    }

    @Test
    void damageScalesRatedCapacityWhileMaintenanceRemainsExplicitlyFinite() {
        FacilityCapabilitySnapshot snapshot = runtime.project(state(
                "facility.processing.bulk_refinery",
                0.5d,
                100_000_000d,
                75_000_000d,
                80d,
                1d,
                "location.orbital_station",
                true));

        assertEquals(50_000_000d, snapshot.effectiveProcessPowerW(), TOLERANCE);
        assertEquals(25d, snapshot.effectiveEngineeringWorkRate(), TOLERANCE);
        assertEquals(1d, snapshot.effectiveMaintenanceWorkRate(), TOLERANCE);
        assertEquals(0.5d, snapshot.effectiveThroughputFraction(), TOLERANCE);
    }

    @Test
    void disabledOrMislocatedFacilityCannotGrantMagicStationCapabilities() {
        FacilityCapabilitySnapshot disabled = runtime.project(state(
                "facility.processing.bulk_refinery",
                1d,
                100_000_000d,
                75_000_000d,
                80d,
                5d,
                "location.orbital_station",
                false));
        FacilityCapabilitySnapshot wrongLocation = runtime.project(state(
                "facility.processing.bulk_refinery",
                1d,
                100_000_000d,
                75_000_000d,
                80d,
                5d,
                "location.free_body",
                true));

        assertEquals(Status.DISABLED, disabled.status());
        assertEquals(Status.LOCATION_INCOMPATIBLE, wrongLocation.status());
        assertTrue(disabled.capabilityTags().isEmpty());
        assertTrue(wrongLocation.capabilityTags().isEmpty());
        assertEquals(0d, wrongLocation.effectiveEngineeringWorkRate(), TOLERANCE);
        assertFalse(wrongLocation.supportsStorageClass("storage.dry_bulk"));
    }

    @Test
    void installedAsteroidFacilityCanDriveStage18BExtractionThroughAdapter() {
        Stage18ExtractionCatalog extractionCatalog = Stage18ExtractionCatalogLoader.loadDefault();
        Stage18ExtractionRuntime extraction = new Stage18ExtractionRuntime(ontology, extractionCatalog);
        FacilityCapabilitySnapshot snapshot = runtime.project(state(
                "facility.extraction.asteroid",
                1d,
                4_000_000d,
                1_400_000d,
                12d,
                0.2d,
                "location.free_body",
                true));
        var capability = runtime.toExtractionCapability(snapshot);
        var budget = capability.openInterval(10d);
        PhysicalSourceState source = new PhysicalSourceState(
                "source.test.metallic",
                SourceKind.NATURAL_OCCURRENCE,
                "occurrence.metallic",
                ExtractionEnvironment.FREE_BODY,
                "commodity.feedstock.metallic_ore",
                1000d,
                1000d,
                0.5d,
                0.8d,
                Set.of());
        PhysicalCargoStore cargo = new PhysicalCargoStore(
                ontology,
                Map.of("storage.dry_bulk", 1000d),
                Map.of());

        ExtractionResult result = extraction.extract(
                source, "extraction.asteroid_excavation", 100d, capability, budget, cargo);

        assertTrue(result.committed());
        assertEquals(Stage18ExtractionRuntime.Status.EXTRACTED, result.status());
        assertEquals(36.8d, cargo.massKg("commodity.feedstock.metallic_ore"), TOLERANCE);
    }

    @Test
    void installedBulkRefineryCanDriveStage18CRecipeThroughAdapter() {
        Stage18RefiningCatalog refiningCatalog = Stage18RefiningCatalogLoader.loadDefault();
        Stage18RefiningRuntime refining = new Stage18RefiningRuntime(ontology, refiningCatalog);
        FacilityCapabilitySnapshot snapshot = runtime.project(state(
                "facility.processing.bulk_refinery",
                1d,
                100_000_000d,
                75_000_000d,
                80d,
                5d,
                "location.orbital_station",
                true));
        var capability = runtime.toRefiningCapability(snapshot);
        PhysicalMaterialStore store = new PhysicalMaterialStore(
                ontology,
                Map.of("storage.dry_bulk", 1000d),
                Map.of("commodity.feedstock.metallic_ore", 100d));

        RefiningResult result = refining.refine(
                "refining.structural_alloy", 50d, store, capability.openInterval(2d));

        assertTrue(result.accepted());
        assertEquals(Stage18RefiningRuntime.Status.REFINED, result.status());
        assertEquals(34d, store.massKg("commodity.material.structural_alloy"), TOLERANCE);
    }

    @Test
    void installedHeavyFabricationPlantCanDriveStage18DComponentManufacturing() {
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
        Stage18ManufacturingCatalog manufacturingCatalog = Stage18ManufacturingCatalogLoader.loadDefault();
        Stage18ManufacturingRuntime manufacturing = new Stage18ManufacturingRuntime(
                ontology, manufacturingCatalog, products);
        FacilityCapabilitySnapshot snapshot = runtime.project(state(
                "facility.fabrication.heavy",
                1d,
                80_000_000d,
                44_000_000d,
                80d,
                4d,
                "location.orbital_station",
                true));
        var capability = runtime.toManufacturingCapability(snapshot);
        ManufacturingInventory inventory = new ManufacturingInventory(
                ontology,
                products,
                manufacturingCapacities(),
                Map.of(
                        "commodity.material.structural_alloy", 1000d,
                        "commodity.material.light_alloy", 1000d,
                        "commodity.material.refractory_alloy", 1000d,
                        "commodity.material.conductor_metal", 1000d,
                        "commodity.material.industrial_chemicals", 1000d,
                        "commodity.material.carbon_material", 1000d),
                Map.of());

        ManufacturingResult result = manufacturing.manufactureComponent(
                "manufacturing.component.heavy", 100d, inventory, capability.openInterval(10d));

        assertTrue(result.accepted());
        assertEquals(Stage18ManufacturingRuntime.Status.MANUFACTURED, result.status());
        assertEquals(100d, inventory.commodityMassKg("commodity.component.heavy_components"), TOLERANCE);
        assertTrue(snapshot.canHandleUnitMass(2_000_000d));
    }

    private static InstalledFacilityState state(
            String definitionId,
            double condition,
            double powerW,
            double heatW,
            double labor,
            double maintenanceRate,
            String location,
            boolean enabled) {
        return new InstalledFacilityState(
                "facility.instance.test",
                definitionId,
                condition,
                powerW,
                heatW,
                labor,
                maintenanceRate,
                location,
                enabled);
    }

    private static Map<String, Double> manufacturingCapacities() {
        HashMap<String, Double> capacities = new HashMap<>();
        capacities.put("storage.dry_bulk", 100_000_000d);
        capacities.put("storage.general_container", 100_000_000d);
        capacities.put("storage.hazardous_controlled", 100_000_000d);
        capacities.put("storage.high_value_controlled", 100_000_000d);
        capacities.put("storage.oversized", 100_000_000d);
        return capacities;
    }
}
