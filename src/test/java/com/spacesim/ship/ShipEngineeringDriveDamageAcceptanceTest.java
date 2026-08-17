package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringRuntime.TickResult;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
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
        ConsumableState loads = reactionMass();
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
                result.massFlowKgPerS(),
                1e-9,
                "damage must reduce thrust and reaction-mass flow through the same physical drive ceiling");
        assertEquals(
                100_000d - result.massFlowKgPerS(),
                result.state().consumables().reactionMassKg(),
                1e-9);
    }

    @Test
    void localIntegrityIsAppliedExactlyOnceByAuthoritativeLiveRuntime() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        DamageState halfDrive = new DamageState(Map.of("core_drive", 0.5d));
        RuntimeState initialized = runtime.initialize(fit, reactionMass(), halfDrive);
        double ratedThrustN = catalog.findModule("module.main_drive_escort_v1")
                .capabilityParameters().get("thrust_n");

        TickResult result = runtime.advance(
                fit,
                initialized,
                halfDrive,
                new OperatingCommand(Map.of("core_drive", 1d), Map.of(), Set.of()),
                1d);

        assertEquals(ratedThrustN, initialized.thrustLimitNByMount().get("core_drive"), 0d,
                "persistent thrust ceiling must not pre-apply local integrity");
        assertEquals(ratedThrustN * 0.5d, result.actualThrustN(), 1e-6,
                "current module integrity must scale live thrust exactly once, not integrity squared");
        assertEquals(ratedThrustN * 0.5d, result.derivedState().totalThrustN(), 1e-6,
                "live result and central derived calculator must agree on damaged thrust");
    }

    private static ConsumableState reactionMass() {
        return new ConsumableState(
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
    }
}
