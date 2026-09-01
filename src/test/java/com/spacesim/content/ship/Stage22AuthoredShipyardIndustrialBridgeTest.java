package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ConstructionInputDefinition;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.CompartmentRepairProfile;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.HullIndustrialProfile;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage22AuthoredShipyardIndustrialBridgeTest {
    @Test
    void authoredIndustrialProfilesComposeThroughCommonCatalog() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog base = ShipyardIndustrialCatalogLoader.loadDefault(engineering);
        ConstructionInputDefinition input = new ConstructionInputDefinition("component.heavy", 100d);
        HullIndustrialProfile hull = new HullIndustrialProfile(
                "hull.stage22_bridge_probe_v1",
                List.of(input),
                Set.of("heavy_structure"),
                Set.of("heavy_lift"),
                0.25d,
                100_000d,
                4,
                2,
                1_000d,
                List.of(new CompartmentRepairProfile("mission_core", List.of(input), 100d)));
        ModuleIndustrialProfile module = new ModuleIndustrialProfile(
                "module.stage22_bridge_probe_v1",
                Set.of("heavy_machinery"),
                Set.of("heavy_lift"),
                0.25d,
                100_000d,
                4,
                2,
                500d,
                100d,
                80d);

        ShipyardIndustrialCatalog combined = Stage22AuthoredShipyardIndustrialBridge.withProfiles(
                base, List.of(hull), List.of(module));

        assertNotNull(combined.findHullProfile(hull.hullId()));
        assertNotNull(combined.findModuleProfile(module.moduleId()));
        assertEquals(base.getHullProfiles().size() + 1, combined.getHullProfiles().size());
        assertEquals(base.getModuleProfiles().size() + 1, combined.getModuleProfiles().size());
    }

    @Test
    void authoredIndustrialProfilesRejectBaseAndBatchDuplicates() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog base = ShipyardIndustrialCatalogLoader.loadDefault(engineering);
        HullIndustrialProfile existingHull = base.getHullProfiles().get(0);
        ModuleIndustrialProfile existingModule = base.getModuleProfiles().get(0);

        assertThrows(IllegalArgumentException.class, () -> Stage22AuthoredShipyardIndustrialBridge.withProfiles(
                base, List.of(existingHull), List.of()));
        assertThrows(IllegalArgumentException.class, () -> Stage22AuthoredShipyardIndustrialBridge.withProfiles(
                base, List.of(), List.of(existingModule)));

        ConstructionInputDefinition input = new ConstructionInputDefinition("component.heavy", 1d);
        HullIndustrialProfile duplicate = new HullIndustrialProfile(
                "hull.stage22_duplicate_probe_v1",
                List.of(input), Set.of("x"), Set.of("y"), 0d, 0d, 0, 0, 1d, List.of());
        assertThrows(IllegalArgumentException.class, () -> Stage22AuthoredShipyardIndustrialBridge.withProfiles(
                base, List.of(duplicate, duplicate), List.of()));
    }
}
