package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipEngineeringGrantService.IntervalBudget;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ISharedEngineeringIntervalAcceptanceTest {
    private static final double INTERVAL_SECONDS = 1d;

    @Test
    void intervalBudgetCannotSpendContinuousOrStorageDischargePowerTwice() {
        Fixture fixture = fixture();
        ShipEngineeringGrantService grants = new ShipEngineeringGrantService(fixture.catalog);
        IntervalBudget budget = grants.beginInterval(fixture.component, INTERVAL_SECONDS);
        double initialEnergy = fixture.component.runtimeState.sharedBusEnergyJ();
        double firstPowerW = budget.initialContinuousPowerW() * 0.75d;
        double secondPowerW = budget.initialContinuousPowerW() * 0.75d;

        var first = grants.grantAndCommit(
                fixture.component, "utility_sensor", firstPowerW, 0d, INTERVAL_SECONDS, budget);
        var second = grants.grantAndCommit(
                fixture.component, "weapon_primary", secondPowerW, 0d, INTERVAL_SECONDS, budget);

        assertTrue(first.committed());
        assertTrue(second.committed());
        assertEquals(0d, budget.remainingContinuousPowerW(), 1e-6d);
        assertTrue(budget.committedStorageDrawJ() > 0d,
                "the second overlapping load must draw only the residual demand from physical storage");
        assertTrue(budget.committedStorageDrawJ()
                <= budget.initialStorageDischargePowerW() * INTERVAL_SECONDS + 1e-6d);
        assertEquals(
                initialEnergy - budget.committedStorageDrawJ(),
                fixture.component.runtimeState.sharedBusEnergyJ(),
                1e-6d);
    }

    @Test
    void deniedOverlappingOperationLeavesBudgetAndShipStateUnchanged() {
        Fixture fixture = fixture();
        ShipEngineeringGrantService grants = new ShipEngineeringGrantService(fixture.catalog);
        IntervalBudget budget = grants.beginInterval(fixture.component, INTERVAL_SECONDS);

        var admitted = grants.grantAndCommit(
                fixture.component,
                "utility_sensor",
                budget.initialContinuousPowerW(),
                1_000d,
                INTERVAL_SECONDS,
                budget);
        assertTrue(admitted.committed());
        var stateBeforeDenied = fixture.component.runtimeState;
        double continuousBefore = budget.remainingContinuousPowerW();
        double storagePowerBefore = budget.remainingStorageDischargePowerW();
        double storageDrawBefore = budget.committedStorageDrawJ();
        double heatBefore = budget.committedHeatJ();
        int operationsBefore = budget.committedOperations();

        var denied = grants.grantAndCommit(
                fixture.component,
                "weapon_primary",
                budget.initialStorageDischargePowerW() + 1d,
                10_000d,
                INTERVAL_SECONDS,
                budget);

        assertFalse(denied.committed());
        assertEquals(stateBeforeDenied, fixture.component.runtimeState);
        assertEquals(continuousBefore, budget.remainingContinuousPowerW(), 0d);
        assertEquals(storagePowerBefore, budget.remainingStorageDischargePowerW(), 0d);
        assertEquals(storageDrawBefore, budget.committedStorageDrawJ(), 0d);
        assertEquals(heatBefore, budget.committedHeatJ(), 0d);
        assertEquals(operationsBefore, budget.committedOperations());
    }

    @Test
    void realSensorBeamAndShieldFacadesShareOnePhysicalIntervalBudgetDeterministically() {
        FacadeRun first = runFacadeChain();
        FacadeRun second = runFacadeChain();

        assertEquals(first, second);
        assertTrue(first.sensorExecuted());
        assertTrue(first.trackState().ordinal() >= InformationState.TRACKED.ordinal());
        assertNotNull(first.beamSolution(), "beam must use the track produced through the admitted sensor operation");
        assertTrue(first.beamSolution().allowed());
        assertTrue(first.committedOperations() >= 2,
                "sensor and beam must both be admitted through the shared interval budget");
        assertTrue(first.storageDrawJ() > 0d,
                "the real overlapping sensor/beam/shield chain must exceed continuous margin and use physical storage");
        assertTrue(first.storageDrawJ() <= first.storageDischargeCeilingJ() + 1e-6d);
        assertTrue(first.remainingContinuousPowerW() >= 0d);
        assertTrue(first.remainingStorageDischargePowerW() >= 0d);
        assertTrue(first.sharedEnergyAfterJ() < first.sharedEnergyBeforeJ());
        assertTrue(first.shieldReserveAfterJ() >= 0d);
    }

    @Test
    void intervalBudgetIsBoundToOnePhysicalShip() {
        Fixture first = fixture();
        Fixture second = fixture();
        ShipEngineeringGrantService grants = new ShipEngineeringGrantService(first.catalog);
        IntervalBudget budget = grants.beginInterval(first.component, INTERVAL_SECONDS);

        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> grants.grantAndCommit(
                        second.component,
                        "utility_sensor",
                        1d,
                        0d,
                        INTERVAL_SECONDS,
                        budget));
        assertTrue(exception.getMessage().contains("different engineering component"));
    }

    private static FacadeRun runFacadeChain() {
        Fixture fixture = fixture();
        ShipEngineeringGrantService grantService = new ShipEngineeringGrantService(fixture.catalog);
        IntervalBudget budget = grantService.beginInterval(fixture.component, INTERVAL_SECONDS);
        double energyBefore = fixture.component.runtimeState.sharedBusEnergyJ();

        var derived = new ShipCapabilityService(fixture.catalog).snapshot(fixture.component).derived();
        var sensors = new ShipSensorEngineeringAdapter().derive(derived);
        FittedSensor radar = sensors.sensors().stream()
                .filter(value -> value.definition().mode() == Mode.ACTIVE_RADAR)
                .findFirst()
                .orElseThrow();

        ShipObservationEngineeringService observationService = new ShipObservationEngineeringService(fixture.catalog);
        var observation = observationService.observe(
                fixture.component,
                radar,
                SensorRuntimeState.nominal(),
                INTERVAL_SECONDS,
                1001L,
                2001L,
                new Position2d(0d, 0d),
                new Position2d(100_000d, 0d),
                sensors.staticSignature(),
                ElectronicWarfareState.empty(),
                10d,
                budget);
        assertTrue(observation.measurement().isPresent(), "nominal Stage-17.5I radar must produce a measurement");

        TrackState track = new ShipSensorRuntime().fuse(
                2001L,
                java.util.List.of(observation.measurement().orElseThrow()),
                DatalinkState.local(),
                TrackQualityPolicy.defaultPolicy(),
                10d);

        BeamWeapon beam = beamWeapon(fixture.catalog);
        ShipBeamEngineeringService beamService = new ShipBeamEngineeringService(fixture.catalog);
        var beamSolution = beamService.planAndCommit(
                fixture.component,
                "weapon_primary",
                beam,
                track,
                0d,
                0d,
                INTERVAL_SECONDS,
                budget);

        ShipShieldEngineeringService shieldService = new ShipShieldEngineeringService(fixture.catalog);
        var shieldState = shieldService.stepRecharge(
                fixture.component,
                "utility_shield",
                INTERVAL_SECONDS,
                budget);

        return new FacadeRun(
                observation.executed(),
                track.informationState(),
                beamSolution,
                shieldState.reserveJ(),
                energyBefore,
                fixture.component.runtimeState.sharedBusEnergyJ(),
                budget.committedStorageDrawJ(),
                budget.initialStorageDischargePowerW() * INTERVAL_SECONDS,
                budget.remainingContinuousPowerW(),
                budget.remainingStorageDischargePowerW(),
                budget.committedOperations());
    }

    private static BeamWeapon beamWeapon(ShipEngineeringCatalog catalog) {
        var module = catalog.findModule("module.test_weapon_beam_v1");
        return new BeamWeapon(
                module.id(),
                parameter(module.capabilityParameters(), "wavelength_m"),
                parameter(module.capabilityParameters(), "aperture_diameter_m"),
                parameter(module.capabilityParameters(), "pointing_jitter_rad"),
                parameter(module.capabilityParameters(), "beam_power_w"),
                module.peakPowerDemandW(),
                module.wasteHeatW(),
                parameter(module.capabilityParameters(), "max_continuous_dwell_s"));
    }

    private static double parameter(Map<String, Double> values, String key) {
        Double value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("missing beam capability: " + key);
        }
        return value;
    }

    private static Fixture fixture() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadDoctrines();
        var doctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.C_HIGH_MOBILITY_BEAM);
        InstalledFit fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(doctrine.fitId()));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        var operating = runtime.initialize(fit, doctrine.initialConsumables(), DamageState.pristine());
        EngineeringComponent component = new EngineeringComponent(
                fit,
                operating,
                ShipInstanceRuntimeState.legacyNeutral());
        return new Fixture(catalog, component);
    }

    private record Fixture(ShipEngineeringCatalog catalog, EngineeringComponent component) {
    }

    private record FacadeRun(
            boolean sensorExecuted,
            InformationState trackState,
            BeamWeaponRuntime.BeamSolution beamSolution,
            double shieldReserveAfterJ,
            double sharedEnergyBeforeJ,
            double sharedEnergyAfterJ,
            double storageDrawJ,
            double storageDischargeCeilingJ,
            double remainingContinuousPowerW,
            double remainingStorageDischargePowerW,
            int committedOperations) {
    }
}
