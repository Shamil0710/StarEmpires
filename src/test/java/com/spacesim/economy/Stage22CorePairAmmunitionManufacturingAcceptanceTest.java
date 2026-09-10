package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22EmpireManufacturingCatalogLoader;
import com.spacesim.content.Stage22IndustrialUnionManufacturingCatalogLoader;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingInventory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B12 industrial precursor: exact core ammunition is a finite Stage-18 manufactured product.
 *
 * <p>The test pays the ordinary kinetic-ammunition material, energy, engineering-work and
 * maintenance-work requirements. It does not seed finished rounds, invoke a faction production
 * multiplier or create a second ammunition recipe.</p>
 */
class Stage22CorePairAmmunitionManufacturingAcceptanceTest {
    private static final int ROUND_COUNT = 4;

    @Test
    void bothCoreRoundFamiliesConsumeMassClosedStage18InputsBeforeFinishedRoundsExist() {
        manufacture(
                "ammo.empire_axial_dart_150kg_v1",
                Stage22CorePairWeaponRuntimeCatalogLoader.loadEmpire(),
                Stage22EmpireManufacturingCatalogLoader.loadDefault());
        manufacture(
                "ammo.industrial_union_dart_140kg_v1",
                Stage22CorePairWeaponRuntimeCatalogLoader.loadIndustrialUnion(),
                Stage22IndustrialUnionManufacturingCatalogLoader.loadDefault());
    }

    private static void manufacture(
            String ammunitionId,
            Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent content,
            Stage18ManufacturingCatalog manufacturing) {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(content.engineering(), Provenance.STAGE22_AUTHORED)
                .withAmmunitionCatalog(content.ammunition(), Provenance.STAGE22_AUTHORED);
        var product = products.findProduct(ammunitionId);
        var binding = manufacturing.findProductBinding(ammunitionId);
        var profile = manufacturing.findProductProfile(binding.profileId());
        assertEquals("manufacturing.profile.kinetic_ammunition", profile.id());

        double outputMassKg = product.unitMassKg() * ROUND_COUNT;
        HashMap<String, Double> inputMasses = new HashMap<>();
        double inputMassTotalKg = 0d;
        for (var input : profile.inputs()) {
            double massKg = outputMassKg * input.fractionOfOutputMass();
            inputMasses.put(input.commodityId(), massKg);
            inputMassTotalKg += massKg;
        }
        assertEquals(outputMassKg, inputMassTotalKg, 1e-9d,
                "Stage-18 kinetic ammunition profile must remain mass closed");

        HashMap<String, Double> capacities = new HashMap<>();
        for (var storageClass : ontology.getStorageClasses()) {
            capacities.put(storageClass.id(), 10_000_000d);
        }
        ManufacturingInventory inventory = new ManufacturingInventory(
                ontology,
                products,
                capacities,
                inputMasses,
                Map.of());
        Stage18ManufacturingRuntime runtime = new Stage18ManufacturingRuntime(
                ontology,
                manufacturing,
                products);
        ManufacturingCapability capability = new ManufacturingCapability(
                "facility.m22_6.b12.kinetic_ammunition",
                profile.requiredCapabilityTags(),
                1.0e15d,
                1.0e12d,
                1.0e12d);

        var result = runtime.manufactureProduct(
                ammunitionId,
                ROUND_COUNT,
                inventory,
                capability.openInterval(1d));

        assertEquals(Stage18ManufacturingRuntime.Status.MANUFACTURED, result.status());
        assertEquals(ROUND_COUNT, result.outputUnitCount());
        assertEquals(outputMassKg, result.outputMassKg(), 1e-9d);
        assertEquals(ROUND_COUNT, inventory.productCount(ammunitionId));
        assertTrue(result.energyConsumedJ() > 0d);
        assertTrue(result.workConsumedSeconds() > 0d);
        assertTrue(result.maintenanceWorkConsumedSeconds() > 0d);
        assertEquals(outputMassKg,
                result.consumedInputMassByCommodityKg().values().stream()
                        .mapToDouble(Double::doubleValue).sum(),
                1e-9d);
        for (String commodityId : inputMasses.keySet()) {
            assertEquals(0d, inventory.commodityMassKg(commodityId), 1e-9d,
                    "manufacturing must consume the exact staged physical input mass");
        }
    }
}
