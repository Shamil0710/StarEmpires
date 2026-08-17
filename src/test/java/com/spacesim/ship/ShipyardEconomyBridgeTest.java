package com.spacesim.ship;

import com.spacesim.components.InventoryComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipyardIndustrialCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEconomyBridge.PhysicalInputBinding;
import com.spacesim.ship.ShipyardEngineeringService.ShipyardCapability;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import com.spacesim.ship.ShipyardEngineeringService.WorkSettlement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipyardEconomyBridgeTest {
    @Test
    void ordinaryInventoryStockIsConsumedBeforeBuildCanComplete() {
        Fixture fixture = fixture();
        WorkPlan plan = fixture.service.planBuild(fixture.fit, capableYard());
        InventoryComponent inventory = stockedInventory(plan, 10);
        int heavyBefore = inventory.stock[0];

        WorkSettlement settlement = ShipyardEconomyBridge.consumeRequiredInputs(
                plan, inventory, binding(), plan.requirements().totalWorkSeconds());

        assertTrue(inventory.stock[0] < heavyBefore);
        ShipyardEngineeringService.BuildCompletion completion = fixture.service.completeBuild(
                new EntityId(600L), plan, settlement);
        assertEquals(fixture.fit, completion.fit());
    }

    @Test
    void insufficientInventoryRejectsAtomicallyWithoutPartialConsumption() {
        Fixture fixture = fixture();
        WorkPlan plan = fixture.service.planBuild(fixture.fit, capableYard());
        InventoryComponent inventory = stockedInventory(plan, 0);
        inventory.stock[1] = 0;
        int[] before = inventory.stock.clone();

        assertThrows(IllegalStateException.class,
                () -> ShipyardEconomyBridge.consumeRequiredInputs(
                        plan, inventory, binding(), plan.requirements().totalWorkSeconds()));

        for (int index = 0; index < inventory.stock.length; index++) {
            assertEquals(before[index], inventory.stock[index]);
        }
    }

    private static Fixture fixture() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipyardEngineeringService service = new ShipyardEngineeringService(
                engineering, ShipyardIndustrialCatalogLoader.loadDefault(engineering));
        InstalledFit fit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        return new Fixture(service, fit);
    }

    private static PhysicalInputBinding binding() {
        return new PhysicalInputBinding(Map.of(
                "component.heavy", 0,
                "component.electrical", 1,
                "component.precision", 2));
    }

    private static InventoryComponent stockedInventory(WorkPlan plan, int extra) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = Integer.MAX_VALUE;
        for (ShipyardEngineeringService.IndustrialInputRequirement input : plan.requirements().inputs()) {
            int runtimeId = binding().runtimeItemIdByContentId().get(input.contentId());
            inventory.stock[runtimeId] = Math.toIntExact((long) Math.ceil(input.amount()) + extra);
        }
        return inventory;
    }

    private static ShipyardCapability capableYard() {
        return new ShipyardCapability(
                "yard.escort_demonstrator",
                new Dimensions3d(300d, 120d, 70d),
                30_000_000d,
                Set.of(
                        "heavy_structure", "pressure_hull", "armor_integration",
                        "heavy_machinery", "power_system_integration", "propulsion_integration",
                        "electronics_integration", "precision_alignment", "thermal_system_integration",
                        "light_structure", "weapon_integration"),
                Set.of("component.heavy", "component.electrical", "component.precision"),
                Set.of(
                        "escort_frame_fixture", "heavy_lift", "reactor_service_fixture",
                        "drive_alignment_fixture", "sensor_calibration_rig", "coolant_pressure_rig",
                        "weapon_bore_alignment_rig"),
                1d, 8d, 500, 500, 2_000_000_000d);
    }

    private record Fixture(ShipyardEngineeringService service, InstalledFit fit) { }
}
