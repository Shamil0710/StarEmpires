package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import com.spacesim.economy.Stage18FacilityConstructionRuntime;
import com.spacesim.economy.Stage18ManufacturingRuntime;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingInventory;
import com.spacesim.economy.Stage18StationStorage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B02-B04 cross-package evidence through the accepted Stage-18 industrial authorities.
 *
 * <p>The scenarios manufacture the authored faction cargo modules with the ordinary Stage-18
 * manufacturing runtime, construct a real support facility with the ordinary Stage-18 construction
 * runtime, and prove that removing one required commodity blocks both authored products without
 * consuming inventory. No faction-local production simulator or free bootstrap path is introduced.</p>
 */
class Stage22CorePairIndustrialMachineEvidenceAcceptanceTest {
    private static final String EMPIRE_CARGO_MODULE = "module.empire_cargo_secure_v1";
    private static final String UNION_CARGO_MODULE = "module.industrial_union_cargo_section_v1";
    private static final String UNION_FREIGHT_FAMILY = "ship_family.industrial_union.freight";

    @Test
    void b02ViableColdStartManufacturesBothAuthoredModulesAndPaysUnionQualification() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B02",
                "stage18_authored_module_cold_start",
                "stage18+stage22.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> {
                    ManufacturingProbe empire = manufacture(
                            Stage22EmpireEngineeringCatalogLoader.loadDefault(),
                            Stage22EmpireManufacturingCatalogLoader.loadDefault(),
                            EMPIRE_CARGO_MODULE,
                            false);
                    ManufacturingProbe union = manufacture(
                            Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault(),
                            Stage22IndustrialUnionManufacturingCatalogLoader.loadDefault(),
                            UNION_CARGO_MODULE,
                            false);

                    var pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                            Stage22IndustrialUnionProductionState.unqualifiedYard(
                                    Stage22IndustrialUnionIndustrialProgram.YARD_ID),
                            UNION_FREIGHT_FAMILY);
                    long retoolWork = pending.retoolWorkRemainingSeconds();
                    long retoolEnergy = pending.retoolEnergyRemainingJ();
                    var paid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                            pending, retoolWork, retoolEnergy);
                    var qualified = Stage22IndustrialUnionIndustrialProgram.completeRetool(paid);
                    boolean unionQualified = !qualified.retooling()
                            && qualified.activeSeriesId().equals("assembly_series.industrial_union.logistics")
                            && retoolWork > 0L
                            && retoolEnergy > 0L;

                    ArrayList<String> breaches = new ArrayList<>();
                    if (!empire.accepted()) breaches.add("empire_authored_module_cold_start_failed");
                    if (!union.accepted()) breaches.add("union_authored_module_cold_start_failed");
                    if (!empire.massConserved()) breaches.add("empire_manufacturing_mass_not_conserved");
                    if (!union.massConserved()) breaches.add("union_manufacturing_mass_not_conserved");
                    if (!unionQualified) breaches.add("union_cold_start_qualification_not_finite");

                    return payload(
                            Map.of(
                                    "empire_output_mass_kg", empire.outputMassKg(),
                                    "union_output_mass_kg", union.outputMassKg(),
                                    "empire_consumed_input_mass_kg", empire.consumedInputMassKg(),
                                    "union_consumed_input_mass_kg", union.consumedInputMassKg(),
                                    "union_initial_retool_work_seconds", (double) retoolWork,
                                    "union_initial_retool_energy_j", (double) retoolEnergy),
                            Map.of(
                                    "empire_authored_product_manufactured", empire.accepted() ? 1d : 0d,
                                    "union_authored_product_manufactured", union.accepted() ? 1d : 0d,
                                    "empire_mass_conserved", empire.massConserved() ? 1d : 0d,
                                    "union_mass_conserved", union.massConserved() ? 1d : 0d,
                                    "union_finite_series_qualification_paid", unionQualified ? 1d : 0d),
                            breaches);
                });

        assertEquals(0, vector.hardRuleBreachCount());
        assertEquals(1d, vector.guardMetricMeans().get("empire_authored_product_manufactured"));
        assertEquals(1d, vector.guardMetricMeans().get("union_authored_product_manufactured"));
        assertEquals(1d, vector.guardMetricMeans().get("union_finite_series_qualification_paid"));
        assertTrue(vector.metricMeans().get("empire_output_mass_kg") > 0d);
        assertTrue(vector.metricMeans().get("union_output_mass_kg") > 0d);
    }

    @Test
    void b03PlannedExpansionBuildsRequiredYardSupportThroughStage18Construction() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B03",
                "stage18_required_support_facility_expansion",
                "stage18+stage22.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> {
                    String empireFacility = Stage22EmpireShipyardCatalogLoader.loadDefault().getYards().get(0)
                            .requiredSupportFacilityDefinitionIds().stream().sorted().findFirst().orElseThrow();
                    String unionFacility = Stage22IndustrialUnionShipyardCatalogLoader.loadDefault().getYards().get(0)
                            .requiredSupportFacilityDefinitionIds().stream().sorted().findFirst().orElseThrow();
                    ConstructionProbe empire = constructSupportFacility(
                            "empire", empireFacility, coordinate.seed());
                    ConstructionProbe union = constructSupportFacility(
                            "union", unionFacility, coordinate.seed());

                    ArrayList<String> breaches = new ArrayList<>();
                    if (!empire.completed()) breaches.add("empire_required_support_facility_not_constructed");
                    if (!union.completed()) breaches.add("union_required_support_facility_not_constructed");
                    if (!empire.materialsConsumed()) breaches.add("empire_expansion_materials_not_consumed");
                    if (!union.materialsConsumed()) breaches.add("union_expansion_materials_not_consumed");

                    return payload(
                            Map.of(
                                    "empire_support_facility_mass_kg", empire.installedMassKg(),
                                    "union_support_facility_mass_kg", union.installedMassKg(),
                                    "empire_support_facility_work_seconds", empire.requiredWorkSeconds(),
                                    "union_support_facility_work_seconds", union.requiredWorkSeconds()),
                            Map.of(
                                    "empire_support_facility_completed", empire.completed() ? 1d : 0d,
                                    "union_support_facility_completed", union.completed() ? 1d : 0d,
                                    "empire_expansion_materials_consumed", empire.materialsConsumed() ? 1d : 0d,
                                    "union_expansion_materials_consumed", union.materialsConsumed() ? 1d : 0d),
                            breaches);
                });

        assertEquals(0, vector.hardRuleBreachCount());
        assertEquals(1d, vector.guardMetricMeans().get("empire_support_facility_completed"));
        assertEquals(1d, vector.guardMetricMeans().get("union_support_facility_completed"));
        assertTrue(vector.metricMeans().get("empire_support_facility_mass_kg") > 0d);
        assertTrue(vector.metricMeans().get("union_support_facility_mass_kg") > 0d);
    }

    @Test
    void b04CriticalMaterialShortageBlocksBothAuthoredProductsWithoutMutation() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B04",
                "stage18_authored_module_critical_shortage",
                "stage18+stage22.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> {
                    ManufacturingProbe empire = manufacture(
                            Stage22EmpireEngineeringCatalogLoader.loadDefault(),
                            Stage22EmpireManufacturingCatalogLoader.loadDefault(),
                            EMPIRE_CARGO_MODULE,
                            true);
                    ManufacturingProbe union = manufacture(
                            Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault(),
                            Stage22IndustrialUnionManufacturingCatalogLoader.loadDefault(),
                            UNION_CARGO_MODULE,
                            true);
                    boolean empireBlocked = !empire.accepted() && empire.inventoryUnchanged();
                    boolean unionBlocked = !union.accepted() && union.inventoryUnchanged();

                    ArrayList<String> breaches = new ArrayList<>();
                    if (!empireBlocked) breaches.add("empire_shortage_did_not_fail_closed");
                    if (!unionBlocked) breaches.add("union_shortage_did_not_fail_closed");

                    return payload(
                            Map.of(
                                    "empire_output_mass_kg", empire.outputMassKg(),
                                    "union_output_mass_kg", union.outputMassKg(),
                                    "empire_consumed_input_mass_kg", empire.consumedInputMassKg(),
                                    "union_consumed_input_mass_kg", union.consumedInputMassKg()),
                            Map.of(
                                    "empire_shortage_blocked_without_mutation", empireBlocked ? 1d : 0d,
                                    "union_shortage_blocked_without_mutation", unionBlocked ? 1d : 0d),
                            breaches);
                });

        assertEquals(0, vector.hardRuleBreachCount());
        assertEquals(1d, vector.guardMetricMeans().get("empire_shortage_blocked_without_mutation"));
        assertEquals(1d, vector.guardMetricMeans().get("union_shortage_blocked_without_mutation"));
        assertEquals(0d, vector.metricMeans().get("empire_output_mass_kg"));
        assertEquals(0d, vector.metricMeans().get("union_output_mass_kg"));
    }

    private static ManufacturingProbe manufacture(
            ShipEngineeringCatalog engineering,
            Stage18ManufacturingCatalog catalog,
            String productId,
            boolean shortage) {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        var runtime = new Stage18ManufacturingRuntime(ontology, catalog, products);
        HashMap<String, Double> masses = abundantManufacturingInputs();
        if (shortage) {
            masses.remove("commodity.component.heavy_components");
        }
        ManufacturingInventory inventory = new ManufacturingInventory(
                ontology, products, storageCapacities(), masses, Map.of());
        Map<String, Double> before = inventory.snapshotCommodityMassByIdKg();
        ManufacturingCapability capability = new ManufacturingCapability(
                "facility.m22_6.cross_package_manufacturing",
                Set.of(
                        "capability.fabrication.assembly",
                        "capability.fabrication.heavy",
                        "capability.fabrication.electrical",
                        "capability.fabrication.precision",
                        "capability.fabrication.ordnance"),
                5_000_000_000_000d,
                50_000d,
                5_000d);
        var result = runtime.manufactureProduct(productId, 1, inventory, capability.openInterval(1_000d));
        double consumed = result.consumedInputMassByCommodityKg().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        boolean massConserved = result.accepted()
                && Math.abs(consumed - result.outputMassKg()) <= Math.max(1e-6d, result.outputMassKg() * 1e-9d);
        return new ManufacturingProbe(
                result.accepted(),
                result.outputMassKg(),
                consumed,
                massConserved,
                before.equals(inventory.snapshotCommodityMassByIdKg()));
    }

    private static ConstructionProbe constructSupportFacility(String key, String facilityId, long seed) {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var runtime = new Stage18FacilityConstructionRuntime(
                Stage18FacilityConstructionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(),
                ontology);
        String stationId = "station.m22_6." + key + "." + seed;
        var order = runtime.createOrder(
                "construction.order.m22_6." + key + "." + seed,
                "facility.instance.m22_6." + key + "." + seed,
                facilityId,
                stationId,
                "location.orbital_station");
        double installedMass = order.installedMassKg();
        double requiredWork = order.requiredWorkSeconds();
        Stage18StationStorage storage = new Stage18StationStorage(
                ontology,
                Stage18ManufacturingProductRegistry.loadDefault(),
                stationId,
                storageCapacities(),
                order.requiredMassByCommodityKg(),
                Map.of());
        for (Map.Entry<String, Double> required : order.requiredMassByCommodityKg().entrySet()) {
            order = runtime.deliver(order, storage, required.getKey(), required.getValue()).order();
        }
        var capability = new Stage18FacilityConstructionRuntime.ConstructionCapability(
                "construction.capability.m22_6." + key,
                Set.of(
                        "capability.fabrication.assembly",
                        "capability.fabrication.heavy",
                        "capability.fabrication.electrical",
                        "capability.fabrication.precision",
                        "capability.fabrication.ordnance"),
                1_000_000_000d);
        var result = runtime.advanceWork(order, capability.openInterval(requiredWork / 1_000_000_000d + 1d));
        boolean completed = result.status() == Stage18FacilityConstructionRuntime.WorkStatus.COMPLETED
                && result.installedFacility() != null
                && result.installedFacility().facilityDefinitionId().equals(facilityId);
        double residualRequiredMass = order.requiredMassByCommodityKg().keySet().stream()
                .mapToDouble(storage::commodityMassKg).sum();
        return new ConstructionProbe(completed, installedMass, requiredWork, residualRequiredMass <= 1e-6d);
    }

    private static Stage22CorePairMachineEvidenceBatch.ObservationPayload payload(
            Map<String, Double> metrics,
            Map<String, Double> guards,
            List<String> breaches) {
        return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(metrics, guards, breaches);
    }

    private static HashMap<String, Double> abundantManufacturingInputs() {
        HashMap<String, Double> values = new HashMap<>();
        values.put("commodity.component.heavy_components", 500_000_000d);
        values.put("commodity.component.electrical_components", 500_000_000d);
        values.put("commodity.component.precision_components", 500_000_000d);
        values.put("commodity.material.structural_alloy", 500_000_000d);
        values.put("commodity.material.light_alloy", 500_000_000d);
        values.put("commodity.material.refractory_alloy", 500_000_000d);
        values.put("commodity.material.conductor_metal", 500_000_000d);
        values.put("commodity.material.industrial_chemicals", 500_000_000d);
        values.put("commodity.material.carbon_material", 500_000_000d);
        values.put("commodity.material.electronic_grade_material", 500_000_000d);
        values.put("commodity.material.ceramic_glass", 500_000_000d);
        return values;
    }

    private static HashMap<String, Double> storageCapacities() {
        HashMap<String, Double> capacities = new HashMap<>();
        capacities.put("storage.dry_bulk", 5_000_000_000d);
        capacities.put("storage.liquid_tank", 5_000_000_000d);
        capacities.put("storage.pressurized_gas", 5_000_000_000d);
        capacities.put("storage.general_container", 5_000_000_000d);
        capacities.put("storage.hazardous_controlled", 5_000_000_000d);
        capacities.put("storage.high_value_controlled", 5_000_000_000d);
        capacities.put("storage.oversized", 5_000_000_000d);
        return capacities;
    }

    private record ManufacturingProbe(
            boolean accepted,
            double outputMassKg,
            double consumedInputMassKg,
            boolean massConserved,
            boolean inventoryUnchanged) { }

    private record ConstructionProbe(
            boolean completed,
            double installedMassKg,
            double requiredWorkSeconds,
            boolean materialsConsumed) { }
}
