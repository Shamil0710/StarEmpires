package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringRuntime.TickResult;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipEngineeringDriveDamageAcceptanceTest {
    @Test
    void explicitDriveThrustCeilingReducesPhysicalThrustWithoutGenericDamageDebuff() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        ConsumableState loads = new ConsumableState(
                0d,
                0d,
                0d,
                0d,
                List.of(new ConsumableLoad(
                        "core_drive",
                        "propellant_feed",
                        InterfaceKind.REACTION_MASS,
                        100_000d,
                        100_000d,
                        0L)));
        RuntimeState healthy = runtime.initialize(fit, loads);
        double ratedThrustN = catalog.findModule("module.main_drive_escort_v1")
                .capabilityParameters().get("thrust_n");
        double exhaustVelocityMps = catalog.findModule("module.main_drive_escort_v1")
                .capabilityParameters().get("exhaust_velocity_mps");
        double damagedThrustCeilingN = ratedThrustN * 0.5d;
        RuntimeState driveDamaged = new RuntimeState(
                healthy.consumables(),
                healthy.sharedBusEnergyJ(),
                healthy.shipHeatStoredJ(),
                healthy.localHeatJByMount(),
                Map.of("core_drive", damagedThrustCeilingN),
                healthy.coolantBusCapacityW(),
                healthy.ftlCooldownSecondsByMount());

        TickResult result = runtime.advance(
                fit,
                driveDamaged,
                new OperatingCommand(Map.of("core_drive", 1d), Map.of(), Set.of()),
                1d);

        assertEquals(13_200_000d, ratedThrustN, 0d);
        assertEquals(ratedThrustN * 0.5d, result.actualThrustN(), 1e-6);
        assertEquals(
                damagedThrustCeilingN / exhaustVelocityMps,
                result.actualMassFlowKgPerS(),
                1e-9,
                "damage must reduce thrust and reaction-mass flow through the same physical drive ceiling");
        assertEquals(
                100_000d - result.actualMassFlowKgPerS(),
                result.state().consumables().reactionMassKg(),
                1e-9);
    }
}
