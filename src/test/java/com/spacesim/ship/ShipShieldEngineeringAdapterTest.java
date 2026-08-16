package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShipEngineeringState.InstalledCapability;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipShieldEngineeringAdapterTest {
    @Test
    void operationalShieldUsesAuthoredPhysicsAndCurrentIntegrity() {
        ShipShieldEngineeringAdapter adapter = new ShipShieldEngineeringAdapter();
        InstalledCapability capability = new InstalledCapability(
                "shield_fore",
                "module.shield_test",
                ModuleFamily.SHIELD_FIELD,
                Map.of(
                        "field_reserve_j", 1_000_000d,
                        "interaction_power_w", 500_000d,
                        "recharge_power_w", 200_000d,
                        "recharge_efficiency", 0.8d,
                        "heat_per_absorbed_j", 0.1d,
                        "restart_delay_s", 2d,
                        "coverage_center_rad", 0d,
                        "coverage_half_arc_rad", Math.PI / 2d,
                        DerivedShipCalculator.RUNTIME_INTEGRITY, 0.5d));

        ShipShieldEngineeringAdapter.FittedShield fitted = adapter.fromCapability(capability);
        assertEquals(0.5d, fitted.emitterIntegrity(), 0d);
        assertEquals(1_000_000d, fitted.definition().reserveCapacityJ(), 0d);
        assertEquals(500_000d, fitted.definition().interactionPowerW(), 0d);
        ShieldFieldRuntime.State charged = fitted.chargedState(new ShieldFieldRuntime());
        assertEquals(500_000d, charged.reserveJ(), 1e-9d);
    }

    @Test
    void destroyedOrNonShieldCapabilityDoesNotCreateField() {
        ShipShieldEngineeringAdapter adapter = new ShipShieldEngineeringAdapter();
        Map<String, Double> parameters = Map.of(
                "field_reserve_j", 1d,
                "interaction_power_w", 1d,
                "recharge_power_w", 0d,
                "recharge_efficiency", 1d,
                "heat_per_absorbed_j", 0d,
                "restart_delay_s", 0d,
                "coverage_center_rad", 0d,
                "coverage_half_arc_rad", 1d,
                DerivedShipCalculator.RUNTIME_INTEGRITY, 0d);
        assertNull(adapter.fromCapability(new InstalledCapability(
                "shield", "module.shield", ModuleFamily.SHIELD_FIELD, parameters)));
        assertNull(adapter.fromCapability(new InstalledCapability(
                "drive", "module.drive", ModuleFamily.MAIN_DRIVE, Map.of())));
    }

    @Test
    void missingPhysicalParameterRejects() {
        ShipShieldEngineeringAdapter adapter = new ShipShieldEngineeringAdapter();
        assertThrows(IllegalArgumentException.class, () -> adapter.fromCapability(new InstalledCapability(
                "shield", "module.shield", ModuleFamily.SHIELD_FIELD,
                Map.of(DerivedShipCalculator.RUNTIME_INTEGRITY, 1d))));
    }
}
