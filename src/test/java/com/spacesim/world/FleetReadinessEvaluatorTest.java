package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityState.EngineeringConsumableLoadState;
import com.spacesim.persistence.EntityState.EngineeringConsumableState;
import com.spacesim.persistence.EntityState.EngineeringState;
import com.spacesim.persistence.EntityState.InstalledModuleState;
import com.spacesim.persistence.EntityState.MountDoubleState;
import com.spacesim.persistence.EntityState.ShipInstanceState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FleetReadinessEvaluatorTest {
    private final FleetReadinessEvaluator evaluator =
            new FleetReadinessEvaluator(ShipEngineeringCatalogLoader.loadDefault());

    @Test
    void readinessIsDerivedFromDamageAmmunitionPropellantCrewSensorsMaintenanceAndSupply() {
        EntityState entity = entity(engineering(
                fittedModules(),
                new EngineeringConsumableState(0d, 0d, 0d, 0d, List.of(
                        new EngineeringConsumableLoadState(
                                "core_drive", "propellant_feed", "REACTION_MASS", 900_000d, 0d, 0L),
                        new EngineeringConsumableLoadState(
                                "weapon_spinal", "kinetic_magazine_feed", "AMMUNITION", 60_000d, 0d, 0L))),
                new ShipInstanceState(
                        List.of(new MountDoubleState("engineering", 0.60d)),
                        List.of(
                                new MountDoubleState("core_reactor", 0.95d),
                                new MountDoubleState("core_drive", 0.90d),
                                new MountDoubleState("utility_sensor", 0.80d),
                                new MountDoubleState("utility_thermal", 1.00d),
                                new MountDoubleState("weapon_spinal", 0.90d)),
                        List.of(),
                        List.of(new MountDoubleState("core_reactor", 432_000d)),
                        List.of(),
                        List.of())));

        FleetReadinessState readiness = evaluator.evaluate(
                entity,
                new FleetOperationalAvailability(134, 7_500));

        assertEquals(6_000, readiness.structuralBps());
        assertEquals(5_000, readiness.ammunitionBps());
        assertEquals(5_000, readiness.propellantBps());
        assertEquals(5_000, readiness.crewBps(), "134 available of 268 required crew must be 50% readiness");
        assertEquals(8_000, readiness.sensorsBps());
        assertEquals(5_000, readiness.maintenanceBps());
        assertEquals(7_500, readiness.supplyAccessBps());
        assertEquals(5_000, readiness.overallBps());
    }

    @Test
    void missingAvailabilityFailsClosedWithoutInventingCrewOrSupplyAccess() {
        FleetReadinessState readiness = evaluator.evaluate(
                entity(engineering(fittedModules(), fullConsumables(), null)),
                null);

        assertEquals(0, readiness.crewBps());
        assertEquals(0, readiness.supplyAccessBps());
        assertEquals(0, readiness.overallBps());
    }

    @Test
    void missingEngineeringUnknownHullAndUnknownModuleFailClosed() {
        assertEquals(FleetReadinessState.unavailable(), evaluator.evaluate(entity(null),
                new FleetOperationalAvailability(1_000, 10_000)));

        EngineeringState unknownHull = new EngineeringState(
                "hull.missing",
                List.of(),
                fullConsumables(),
                0d, 0d, List.of(), List.of(), 0d, List.of(), null);
        assertEquals(FleetReadinessState.unavailable(), evaluator.evaluate(entity(unknownHull),
                new FleetOperationalAvailability(1_000, 10_000)));

        EngineeringState unknownModule = engineering(
                List.of(new InstalledModuleState("core_reactor", "module.missing")),
                fullConsumables(),
                null);
        assertEquals(FleetReadinessState.unavailable(), evaluator.evaluate(entity(unknownModule),
                new FleetOperationalAvailability(1_000, 10_000)));
    }

    private static EngineeringState engineering(
            List<InstalledModuleState> modules,
            EngineeringConsumableState consumables,
            ShipInstanceState instanceState) {
        return new EngineeringState(
                "hull.escort_destroyer_v1",
                modules,
                consumables,
                0d,
                0d,
                List.of(),
                List.of(),
                0d,
                List.of(),
                instanceState);
    }

    private static List<InstalledModuleState> fittedModules() {
        return List.of(
                new InstalledModuleState("core_reactor", "module.reactor_5gw_v1"),
                new InstalledModuleState("core_drive", "module.main_drive_escort_v1"),
                new InstalledModuleState("utility_sensor", "module.sensor_array_escort_v1"),
                new InstalledModuleState("utility_thermal", "module.radiator_escort_v1"),
                new InstalledModuleState("weapon_spinal", "module.railgun_large_v1"));
    }

    private static EngineeringConsumableState fullConsumables() {
        return new EngineeringConsumableState(0d, 0d, 0d, 0d, List.of(
                new EngineeringConsumableLoadState(
                        "core_drive", "propellant_feed", "REACTION_MASS", 1_800_000d, 0d, 0L),
                new EngineeringConsumableLoadState(
                        "weapon_spinal", "kinetic_magazine_feed", "AMMUNITION", 120_000d, 0d, 0L)));
    }

    private static EntityState entity(EngineeringState engineering) {
        return new EntityState(new EntityId(1L), null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, engineering, null);
    }
}
