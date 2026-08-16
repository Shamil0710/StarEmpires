package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringRuntime.PowerStatus;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringRuntime.ThermalStatus;
import com.spacesim.ship.ShipEngineeringRuntime.TickResult;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipEngineeringRuntimeTest {
    private static final String FIT_ID = "fit.runtime_test";

    @Test
    void sharedBusExcludesLocalWeaponSensorBuffersAndJumpConsumesOnlyEnergyStorage() {
        Fixture fixture = fixture(ConsumableState.empty());
        RuntimeState initial = fixture.runtime.initialize(fixture.fit, fixture.loads);

        assertEquals(100_000_000d, initial.sharedBusEnergyJ(), 0d,
                "500 MJ sensor-local buffer must not become shared bus energy");

        JumpPlan plan = fixture.runtime.planJump(fixture.fit, initial);
        assertTrue(plan.allowed());
        assertEquals(JumpFailure.NONE, plan.failure());
        assertEquals(40_000_000d, plan.requiredEnergyJ(), 0d);
        assertEquals(10d, plan.spoolSeconds(), 0d);
        assertEquals(30d, plan.edgeTransitSeconds(), 0d);

        RuntimeState committed = fixture.runtime.commitJump(initial, plan);
        assertEquals(60_000_000d, committed.sharedBusEnergyJ(), 0d);
        assertEquals(10_000_000d, committed.localHeatJByMount().get("core_ftl"), 0d);
        assertEquals(60d, committed.ftlCooldownSecondsByMount().get("core_ftl"), 0d);
        assertEquals(JumpFailure.COOLDOWN_ACTIVE,
                fixture.runtime.planJump(fixture.fit, committed).failure());

        TickResult recharged = fixture.runtime.advance(
                fixture.fit, committed, OperatingCommand.idle(), 10d);
        assertEquals(80_000_000d, recharged.state().sharedBusEnergyJ(), 1e-6,
                "surplus reactor power must charge only at the fitted battery charge-power limit");
        assertEquals(50d, recharged.state().ftlCooldownSecondsByMount().get("core_ftl"), 1e-9);
    }

    @Test
    void reactionMassDepletesPhysicallyAndChangesDerivedAccelerationAndDeltaV() {
        ConsumableState loads = reactionMass(1_000d);
        Fixture fixture = fixture(loads);
        RuntimeState initial = fixture.runtime.initialize(fixture.fit, loads);
        DerivedShipState before = new DerivedShipCalculator(fixture.catalog).derive(
                fixture.catalog.findHull(fixture.fit.hullId()),
                fixture.fit,
                loads,
                DamageState.pristine());

        TickResult result = fixture.runtime.advance(
                fixture.fit,
                initial,
                new OperatingCommand(Map.of("core_drive", 1d), Map.of(), Set.of()),
                10d);

        assertEquals(100_000d, result.actualThrustN(), 1e-9);
        assertEquals(5d, result.massFlowKgPerS(), 1e-12);
        assertEquals(950d, result.state().consumables().reactionMassKg(), 1e-9);
        assertEquals(before.totalMassKg() - 50d, result.derivedState().totalMassKg(), 1e-9);
        assertTrue(result.derivedState().accelerationMps2() > before.accelerationMps2());
        assertTrue(result.derivedState().deltaVMps() < before.deltaVMps(),
                "burned reaction mass must reduce remaining delta-v");
    }

    @Test
    void explicitCoolantBusDamageBuildsLocalHeatAndThenThermallyLimitsDrive() {
        ConsumableState loads = reactionMass(1_000d);
        Fixture fixture = fixture(loads);
        RuntimeState healthy = fixture.runtime.initialize(fixture.fit, loads);
        RuntimeState coolantDamaged = new RuntimeState(
                healthy.consumables(),
                healthy.sharedBusEnergyJ(),
                healthy.shipHeatStoredJ(),
                healthy.localHeatJByMount(),
                healthy.thrustLimitNByMount(),
                0d,
                healthy.ftlCooldownSecondsByMount());
        OperatingCommand fullDrive = new OperatingCommand(
                Map.of("core_drive", 1d), Map.of(), Set.of());

        TickResult heating = fixture.runtime.advance(fixture.fit, coolantDamaged, fullDrive, 11d);
        assertEquals(ThermalStatus.SATURATED, heating.thermalStatus());
        assertTrue(heating.state().localHeatJByMount().get("core_drive") > 5_000_000d);

        TickResult throttled = fixture.runtime.advance(fixture.fit, heating.state(), fullDrive, 1d);
        assertEquals(ThermalStatus.THERMALLY_LIMITED, throttled.thermalStatus());
        assertEquals(0d, throttled.actualThrustN(), 0d,
                "drive local thermal saturation must remove thrust without a class-name debuff");
    }

    @Test
    void loadSheddingIsDeterministicAndUsesExplicitOperationalPriority() {
        ConsumableState loads = reactionMass(1_000d);
        Fixture fixture = fixture(loads);
        RuntimeState initialized = fixture.runtime.initialize(fixture.fit, loads);
        RuntimeState emptyBattery = new RuntimeState(
                initialized.consumables(),
                0d,
                initialized.shipHeatStoredJ(),
                initialized.localHeatJByMount(),
                initialized.thrustLimitNByMount(),
                initialized.coolantBusCapacityW(),
                initialized.ftlCooldownSecondsByMount());
        OperatingCommand command = new OperatingCommand(
                Map.of("core_drive", 1d),
                Map.of(
                        "utility_thermal", 500,
                        "core_ftl", 400,
                        "utility_sensor", 300,
                        "core_drive", 200),
                Set.of("core_reactor"));

        TickResult first = fixture.runtime.advance(fixture.fit, emptyBattery, command, 1d);
        RuntimeState replayStart = new RuntimeState(
                initialized.consumables(),
                0d,
                initialized.shipHeatStoredJ(),
                initialized.localHeatJByMount(),
                initialized.thrustLimitNByMount(),
                initialized.coolantBusCapacityW(),
                initialized.ftlCooldownSecondsByMount());
        TickResult second = fixture.runtime.advance(fixture.fit, replayStart, command, 1d);

        assertEquals(PowerStatus.LOAD_SHEDDING, first.powerStatus());
        assertEquals(List.of("utility_thermal", "core_ftl", "utility_sensor", "core_drive"), first.shedMounts());
        assertEquals(first, second);
        assertEquals(0d, first.actualThrustN(), 0d);
    }

    @Test
    void jumpPlanningRejectsTranslatedMassAndBadJetPowerDeterministically() {
        ConsumableState heavy = new ConsumableState(6_000d, 0d, 0d, 0d, List.of());
        Fixture fixture = fixture(heavy);
        RuntimeState state = fixture.runtime.initialize(fixture.fit, heavy);
        JumpPlan tooHeavy = fixture.runtime.planJump(fixture.fit, state);
        assertEquals(JumpFailure.TRANSLATED_MASS_EXCEEDED, tooHeavy.failure());

        ShipEngineeringCatalog badCatalog = ShipEngineeringCatalogLoader.parse(
                catalogJson().replace("\"jet_power_w\": 1000000000.0", "\"jet_power_w\": 999999999.0"));
        InstalledFit badFit = InstalledFit.fromDemonstrator(badCatalog.findDemonstratorFit(FIT_ID));
        assertThrows(IllegalArgumentException.class,
                () -> new ShipEngineeringRuntime(badCatalog).initialize(badFit, reactionMass(100d)));
    }

    private static ConsumableState reactionMass(double kg) {
        return new ConsumableState(
                0d,
                0d,
                0d,
                0d,
                List.of(new ConsumableLoad(
                        "core_drive",
                        "propellant_feed",
                        InterfaceKind.REACTION_MASS,
                        kg,
                        kg,
                        0L)));
    }

    private static Fixture fixture(ConsumableState loads) {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.parse(catalogJson());
        InstalledFit fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(FIT_ID));
        return new Fixture(catalog, new ShipEngineeringRuntime(catalog), fit, loads);
    }

    private static String catalogJson() {
        return """
                {
                  "schemaVersion": 1,
                  "migrationVersion": 1,
                  "responseSurfaces": [],
                  "materials": [{
                    "id": "material.runtime_test",
                    "densityKgPerM3": 1000.0,
                    "tags": ["test"],
                    "thermalConductivityWPerMK": 1.0,
                    "specificHeatJPerKgK": 1000.0,
                    "emissivity": 0.8,
                    "radarReflectivity": 0.5,
                    "heavyImpactResponseSurfaceId": null,
                    "constructionMaterialFamilyId": null,
                    "repairMaterialFamilyId": null
                  }],
                  "protectionStacks": [{
                    "id": "protection.runtime_test",
                    "mountMassKg": 0.0,
                    "layers": [{
                      "materialId": "material.runtime_test",
                      "thicknessM": 0.01,
                      "spacingAfterM": 0.0,
                      "orientationRad": 0.0,
                      "coverageFraction": 1.0,
                      "responseSurfaceId": null
                    }]
                  }],
                  "hulls": [{
                    "id": "hull.runtime_test",
                    "displayName": "Runtime Test Hull",
                    "architecture": "FRAME",
                    "boundingDimensionsM": {"lengthM": 30.0, "widthM": 10.0, "heightM": 6.0},
                    "bareHullMassKg": 1000.0,
                    "internalVolumeM3": 10000.0,
                    "slots": [
                      {"id": "core_reactor", "category": "CORE", "maxDimensionsM": {"lengthM": 5.0, "widthM": 5.0, "heightM": 5.0}, "maxMassKg": 1000.0},
                      {"id": "core_battery", "category": "CORE", "maxDimensionsM": {"lengthM": 5.0, "widthM": 5.0, "heightM": 5.0}, "maxMassKg": 1000.0},
                      {"id": "core_drive", "category": "CORE", "maxDimensionsM": {"lengthM": 5.0, "widthM": 5.0, "heightM": 5.0}, "maxMassKg": 1000.0},
                      {"id": "core_ftl", "category": "CORE", "maxDimensionsM": {"lengthM": 5.0, "widthM": 5.0, "heightM": 5.0}, "maxMassKg": 1000.0},
                      {"id": "utility_thermal", "category": "UTILITY", "maxDimensionsM": {"lengthM": 5.0, "widthM": 5.0, "heightM": 5.0}, "maxMassKg": 1000.0},
                      {"id": "utility_sensor", "category": "UTILITY", "maxDimensionsM": {"lengthM": 5.0, "widthM": 5.0, "heightM": 5.0}, "maxMassKg": 1000.0}
                    ],
                    "hardpoints": [],
                    "compartments": [{
                      "id": "engineering",
                      "volumeM3": 10000.0,
                      "centerM": {"xM": 0.0, "yM": 0.0, "zM": 0.0},
                      "protectionStackId": "protection.runtime_test",
                      "tags": ["engineering"]
                    }],
                    "crewBaseline": 1,
                    "lifeSupportCapacity": 20,
                    "baseSignatureGeometryAreaM2": 100.0,
                    "structuralProtectionStackId": "protection.runtime_test",
                    "maxOperationalMassKg": 50000.0,
                    "thrustMountCompatibility": ["MAIN_DRIVE"]
                  }],
                  "modules": [
                    {
                      "id": "module.runtime_reactor",
                      "displayName": "Runtime Reactor",
                      "family": "REACTOR_POWER",
                      "integrationCategories": ["CORE"],
                      "compatibleHardpointSizes": [],
                      "physicalDimensionsM": {"lengthM": 2.0, "widthM": 2.0, "heightM": 2.0},
                      "massKg": 100.0,
                      "occupiedVolumeM3": 8.0,
                      "requiredMountStrengthN": 1.0,
                      "continuousPowerSupplyW": 10000000.0,
                      "continuousPowerDemandW": 0.0,
                      "peakPowerDemandW": 0.0,
                      "storedEnergyCapacityJ": 0.0,
                      "wasteHeatW": 1000000.0,
                      "localThermalCapacityJ": 20000000.0,
                      "coolantTransferDemandW": 1000000.0,
                      "heatRejectionW": 0.0,
                      "crewRequirement": 1,
                      "automationRequirement": 1,
                      "interfaces": [],
                      "signatureContributions": {},
                      "constructionInputs": [],
                      "maintenance": {"serviceIntervalSeconds": 1000.0, "maintenanceWorkSeconds": 10.0, "repairComplexity": 0.1},
                      "capabilityParameters": {"rated_power_w": 10000000.0}
                    },
                    {
                      "id": "module.runtime_battery",
                      "displayName": "Runtime Shared Battery",
                      "family": "ENERGY_STORAGE",
                      "integrationCategories": ["CORE"],
                      "compatibleHardpointSizes": [],
                      "physicalDimensionsM": {"lengthM": 2.0, "widthM": 2.0, "heightM": 2.0},
                      "massKg": 100.0,
                      "occupiedVolumeM3": 8.0,
                      "requiredMountStrengthN": 1.0,
                      "continuousPowerSupplyW": 0.0,
                      "continuousPowerDemandW": 0.0,
                      "peakPowerDemandW": 0.0,
                      "storedEnergyCapacityJ": 100000000.0,
                      "wasteHeatW": 0.0,
                      "localThermalCapacityJ": 10000000.0,
                      "coolantTransferDemandW": 0.0,
                      "heatRejectionW": 0.0,
                      "crewRequirement": 0,
                      "automationRequirement": 1,
                      "interfaces": [],
                      "signatureContributions": {},
                      "constructionInputs": [],
                      "maintenance": {"serviceIntervalSeconds": 1000.0, "maintenanceWorkSeconds": 10.0, "repairComplexity": 0.1},
                      "capabilityParameters": {"max_charge_power_w": 2000000.0, "max_discharge_power_w": 5000000.0}
                    },
                    {
                      "id": "module.runtime_drive",
                      "displayName": "Runtime Main Drive",
                      "family": "MAIN_DRIVE",
                      "integrationCategories": ["CORE"],
                      "compatibleHardpointSizes": [],
                      "physicalDimensionsM": {"lengthM": 2.0, "widthM": 2.0, "heightM": 2.0},
                      "massKg": 100.0,
                      "occupiedVolumeM3": 8.0,
                      "requiredMountStrengthN": 1.0,
                      "continuousPowerSupplyW": 0.0,
                      "continuousPowerDemandW": 2000000.0,
                      "peakPowerDemandW": 2000000.0,
                      "storedEnergyCapacityJ": 0.0,
                      "wasteHeatW": 500000.0,
                      "localThermalCapacityJ": 5000000.0,
                      "coolantTransferDemandW": 500000.0,
                      "heatRejectionW": 0.0,
                      "crewRequirement": 1,
                      "automationRequirement": 1,
                      "interfaces": [{"kind": "REACTION_MASS", "id": "propellant_feed", "capacity": 5000.0}],
                      "signatureContributions": {},
                      "constructionInputs": [],
                      "maintenance": {"serviceIntervalSeconds": 1000.0, "maintenanceWorkSeconds": 10.0, "repairComplexity": 0.1},
                      "capabilityParameters": {"thrust_n": 100000.0, "exhaust_velocity_mps": 20000.0, "jet_power_w": 1000000000.0}
                    },
                    {
                      "id": "module.runtime_ftl",
                      "displayName": "Runtime FTL",
                      "family": "FTL_JUMP",
                      "integrationCategories": ["CORE"],
                      "compatibleHardpointSizes": [],
                      "physicalDimensionsM": {"lengthM": 2.0, "widthM": 2.0, "heightM": 2.0},
                      "massKg": 100.0,
                      "occupiedVolumeM3": 8.0,
                      "requiredMountStrengthN": 1.0,
                      "continuousPowerSupplyW": 0.0,
                      "continuousPowerDemandW": 200000.0,
                      "peakPowerDemandW": 4000000.0,
                      "storedEnergyCapacityJ": 0.0,
                      "wasteHeatW": 100000.0,
                      "localThermalCapacityJ": 20000000.0,
                      "coolantTransferDemandW": 100000.0,
                      "heatRejectionW": 0.0,
                      "crewRequirement": 1,
                      "automationRequirement": 1,
                      "interfaces": [],
                      "signatureContributions": {},
                      "constructionInputs": [],
                      "maintenance": {"serviceIntervalSeconds": 1000.0, "maintenanceWorkSeconds": 10.0, "repairComplexity": 0.1},
                      "capabilityParameters": {"translated_mass_max_kg": 5000.0, "jump_energy_j": 40000000.0, "charge_power_w": 4000000.0, "spool_time_s": 10.0, "edge_transit_time_s": 30.0, "cooldown_s": 60.0, "jump_heat_j": 10000000.0}
                    },
                    {
                      "id": "module.runtime_thermal",
                      "displayName": "Runtime Thermal Bus",
                      "family": "THERMAL_CONTROL",
                      "integrationCategories": ["UTILITY"],
                      "compatibleHardpointSizes": [],
                      "physicalDimensionsM": {"lengthM": 2.0, "widthM": 2.0, "heightM": 2.0},
                      "massKg": 100.0,
                      "occupiedVolumeM3": 8.0,
                      "requiredMountStrengthN": 1.0,
                      "continuousPowerSupplyW": 0.0,
                      "continuousPowerDemandW": 100000.0,
                      "peakPowerDemandW": 100000.0,
                      "storedEnergyCapacityJ": 0.0,
                      "wasteHeatW": 50000.0,
                      "localThermalCapacityJ": 10000000.0,
                      "coolantTransferDemandW": 0.0,
                      "heatRejectionW": 2000000.0,
                      "crewRequirement": 1,
                      "automationRequirement": 1,
                      "interfaces": [],
                      "signatureContributions": {},
                      "constructionInputs": [],
                      "maintenance": {"serviceIntervalSeconds": 1000.0, "maintenanceWorkSeconds": 10.0, "repairComplexity": 0.1},
                      "capabilityParameters": {"coolant_bus_capacity_w": 3000000.0, "ship_thermal_store_capacity_j": 100000000.0}
                    },
                    {
                      "id": "module.runtime_sensor",
                      "displayName": "Runtime Local Sensor Buffer",
                      "family": "SENSOR_EW_FIRE_CONTROL",
                      "integrationCategories": ["UTILITY"],
                      "compatibleHardpointSizes": [],
                      "physicalDimensionsM": {"lengthM": 2.0, "widthM": 2.0, "heightM": 2.0},
                      "massKg": 100.0,
                      "occupiedVolumeM3": 8.0,
                      "requiredMountStrengthN": 1.0,
                      "continuousPowerSupplyW": 0.0,
                      "continuousPowerDemandW": 500000.0,
                      "peakPowerDemandW": 500000.0,
                      "storedEnergyCapacityJ": 500000000.0,
                      "wasteHeatW": 100000.0,
                      "localThermalCapacityJ": 10000000.0,
                      "coolantTransferDemandW": 100000.0,
                      "heatRejectionW": 0.0,
                      "crewRequirement": 1,
                      "automationRequirement": 1,
                      "interfaces": [],
                      "signatureContributions": {},
                      "constructionInputs": [],
                      "maintenance": {"serviceIntervalSeconds": 1000.0, "maintenanceWorkSeconds": 10.0, "repairComplexity": 0.1},
                      "capabilityParameters": {"aperture_area_m2": 1.0}
                    }
                  ],
                  "demonstratorFits": [{
                    "id": "fit.runtime_test",
                    "hullId": "hull.runtime_test",
                    "installedModules": [
                      {"mountId": "core_reactor", "moduleId": "module.runtime_reactor"},
                      {"mountId": "core_battery", "moduleId": "module.runtime_battery"},
                      {"mountId": "core_drive", "moduleId": "module.runtime_drive"},
                      {"mountId": "core_ftl", "moduleId": "module.runtime_ftl"},
                      {"mountId": "utility_thermal", "moduleId": "module.runtime_thermal"},
                      {"mountId": "utility_sensor", "moduleId": "module.runtime_sensor"}
                    ]
                  }]
                }
                """;
    }

    private record Fixture(
            ShipEngineeringCatalog catalog,
            ShipEngineeringRuntime runtime,
            InstalledFit fit,
            ConsumableState loads) { }
}
