package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingCatalog.ProductBindingDefinition;
import com.spacesim.content.Stage18ShipyardCatalog.CompartmentRepairProfile;
import com.spacesim.content.Stage18ShipyardCatalog.HullPhysicalProfile;
import com.spacesim.content.Stage18ShipyardCatalog.PhysicalInputDefinition;
import com.spacesim.content.Stage18ShipyardCatalog.ModuleServiceProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage22AuthoredProductionBridgeTest {
    @Test
    void authoredManufacturingBindingUsesExistingStage18Profile() {
        Stage18ManufacturingCatalog base = Stage18ManufacturingCatalogLoader.loadDefault();
        ProductBindingDefinition authored = new ProductBindingDefinition(
                "module.stage22_bridge_probe_v1", "manufacturing.profile.reactor");

        Stage18ManufacturingCatalog combined = Stage22AuthoredProductionBridge.withProductBindings(
                base, List.of(authored));

        assertNotNull(combined.findProductBinding("module.stage22_bridge_probe_v1"));
        assertEquals(base.getProductBindings().size() + 1, combined.getProductBindings().size());
        assertEquals(base.getComponentRecipes(), combined.getComponentRecipes());
        assertEquals(base.getProductProfiles(), combined.getProductProfiles());
    }

    @Test
    void authoredManufacturingBindingRejectsUnknownProfileAndDuplicateProduct() {
        Stage18ManufacturingCatalog base = Stage18ManufacturingCatalogLoader.loadDefault();
        assertThrows(IllegalArgumentException.class, () -> Stage22AuthoredProductionBridge.withProductBindings(
                base,
                List.of(new ProductBindingDefinition(
                        "module.stage22_unknown_profile_v1", "manufacturing.profile.not_real"))));

        ProductBindingDefinition existing = base.getProductBindings().get(0);
        assertThrows(IllegalArgumentException.class, () -> Stage22AuthoredProductionBridge.withProductBindings(
                base, List.of(existing)));
    }

    @Test
    void authoredPhysicalShipyardProfilesComposeWithoutReplacingStage18Authority() {
        Stage18ShipyardCatalog base = Stage18ShipyardCatalogLoader.loadDefault();
        PhysicalInputDefinition heavy = new PhysicalInputDefinition(
                "commodity.component.heavy_components", 1_000d);
        HullPhysicalProfile hull = new HullPhysicalProfile(
                "hull.stage22_bridge_probe_v1",
                List.of(heavy),
                List.of(new CompartmentRepairProfile("mission_core", List.of(heavy))));
        ModuleServiceProfile module = new ModuleServiceProfile(
                "module.stage22_bridge_probe_v1",
                List.of(new PhysicalInputDefinition("commodity.component.heavy_components", 500d)),
                List.of(new PhysicalInputDefinition("commodity.component.heavy_components", 25d)));

        Stage18ShipyardCatalog combined = Stage22AuthoredProductionBridge.withShipyardProfiles(
                base, List.of(), List.of(hull), List.of(module));

        assertNotNull(combined.findHullProfile(hull.hullId()));
        assertNotNull(combined.findModuleProfile(module.moduleId()));
        assertEquals(base.getYards(), combined.getYards());
        assertEquals(base.getHullProfiles().size() + 1, combined.getHullProfiles().size());
        assertEquals(base.getModuleProfiles().size() + 1, combined.getModuleProfiles().size());
    }

    @Test
    void authoredPhysicalShipyardProfilesRejectDuplicateIdentities() {
        Stage18ShipyardCatalog base = Stage18ShipyardCatalogLoader.loadDefault();
        HullPhysicalProfile existingHull = base.getHullProfiles().get(0);
        ModuleServiceProfile existingModule = base.getModuleProfiles().get(0);

        assertThrows(IllegalArgumentException.class, () -> Stage22AuthoredProductionBridge.withShipyardProfiles(
                base, List.of(), List.of(existingHull), List.of()));
        assertThrows(IllegalArgumentException.class, () -> Stage22AuthoredProductionBridge.withShipyardProfiles(
                base, List.of(), List.of(), List.of(existingModule)));
    }
}
