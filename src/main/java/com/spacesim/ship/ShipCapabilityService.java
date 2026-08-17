package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShieldFieldRuntime.State;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipShieldEngineeringAdapter.FittedShield;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Shared read-only Stage-17.5H capability API consumed by UI, player commands and AI planning.
 *
 * <p>The service never mutates ECS and never caches derived statistics. Every query is rebuilt from
 * the fitted catalog definitions plus the exact current engineering, consumable, local damage,
 * shield, maintenance and weapon-continuity state. This is the public seam that prevents UI/AI code
 * from reading implementation arrays and accidentally creating a second combat model.</p>
 */
public final class ShipCapabilityService {
    private static final double EPSILON = 1e-9d;

    private final ShipEngineeringCatalog catalog;
    private final ShipEngineeringRuntime engineeringRuntime;
    private final ShipSensorEngineeringAdapter sensorAdapter = new ShipSensorEngineeringAdapter();
    private final ShipShieldEngineeringAdapter shieldAdapter = new ShipShieldEngineeringAdapter();

    /**
     * Creates one read-only capability service over the production engineering catalog.
     *
     * @param catalog production ship engineering definitions
     */
    public ShipCapabilityService(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.engineeringRuntime = new ShipEngineeringRuntime(catalog);
    }

    /**
     * Projects one complete read-only capability snapshot.
     *
     * @param component authoritative physical engineering component
     * @return immutable current capability projection
     */
    public Snapshot snapshot(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        DamageState damage = checked.instanceState.damage().moduleDamage();
        DerivedShipState derived = engineeringRuntime.derive(checked.fit, checked.runtimeState, damage);
        return new Snapshot(
                derived,
                accelerationEnvelope(checked, derived, damage),
                derived.deltaVMps(),
                new ThermalEndurance(
                        derived.continuousHeatMarginW(),
                        checked.runtimeState.shipHeatStoredJ(),
                        remainingLocalThermalCapacityJ(checked, damage)),
                ammunitionEndurance(checked),
                damageCapabilityState(checked),
                shieldCoverage(checked, derived),
                maintenanceState(checked),
                sensorAdapter.derive(derived));
    }

    /**
     * Queries the damage-aware acceleration envelope without changing reaction mass.
     *
     * @param component authoritative physical engineering component
     * @return current max physical thrust/acceleration envelope
     */
    public AccelerationEnvelope getAccelerationEnvelope(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        DamageState damage = checked.instanceState.damage().moduleDamage();
        DerivedShipState derived = engineeringRuntime.derive(checked.fit, checked.runtimeState, damage);
        return accelerationEnvelope(checked, derived, damage);
    }

    /**
     * Returns remaining physical rocket-equation delta-v from current carried reaction mass.
     *
     * @param component authoritative physical engineering component
     * @return remaining delta-v in meters per second
     */
    public double getRemainingDeltaV(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        return engineeringRuntime.derive(
                checked.fit, checked.runtimeState, checked.instanceState.damage().moduleDamage()).deltaVMps();
    }

    /**
     * Plans FTL through the same damage-aware engineering runtime used by execution.
     *
     * @param component authoritative physical engineering component
     * @return accepted or rejected physical jump plan
     */
    public JumpPlan planJump(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        return engineeringRuntime.planJump(
                checked.fit, checked.runtimeState, checked.instanceState.damage().moduleDamage());
    }

    /**
     * Returns persisted shield reserve/collapse state aligned to currently fitted emitters.
     *
     * @param component authoritative physical engineering component
     * @return mount-sorted shield coverage summaries
     */
    public List<ShieldCoverage> getShieldCoverage(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        DamageState damage = checked.instanceState.damage().moduleDamage();
        DerivedShipState derived = engineeringRuntime.derive(checked.fit, checked.runtimeState, damage);
        return shieldCoverage(checked, derived);
    }

    /**
     * Returns current thermal margin and remaining local buffers.
     *
     * @param component authoritative physical engineering component
     * @return read-only thermal endurance projection
     */
    public ThermalEndurance getThermalEndurance(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        DamageState damage = checked.instanceState.damage().moduleDamage();
        DerivedShipState derived = engineeringRuntime.derive(checked.fit, checked.runtimeState, damage);
        return new ThermalEndurance(
                derived.continuousHeatMarginW(),
                checked.runtimeState.shipHeatStoredJ(),
                remainingLocalThermalCapacityJ(checked, damage));
    }

    /**
     * Returns physical ammunition inventory plus feed identity bindings.
     *
     * @param component authoritative physical engineering component
     * @return read-only ammunition endurance projection
     */
    public AmmunitionEndurance getAmmunitionEndurance(EngineeringComponent component) {
        return ammunitionEndurance(requireComponent(component));
    }

    /**
     * Returns local compartment/module integrity without collapsing survivability into global HP.
     *
     * @param component authoritative physical engineering component
     * @return damage capability projection
     */
    public DamageCapabilityState getDamageCapabilityState(EngineeringComponent component) {
        return damageCapabilityState(requireComponent(component));
    }

    /**
     * Returns whether ordinary repair work is physically required and which locations need it.
     *
     * @param component authoritative physical engineering component
     * @return repair-need projection
     */
    public RepairNeed getRepairNeed(EngineeringComponent component) {
        DamageCapabilityState damage = getDamageCapabilityState(component);
        List<String> damagedCompartments = damage.compartmentIntegrityById().entrySet().stream()
                .filter(entry -> entry.getValue() < 1d - EPSILON)
                .map(Map.Entry::getKey)
                .toList();
        List<String> damagedMounts = damage.moduleIntegrityByMount().entrySet().stream()
                .filter(entry -> entry.getValue() < 1d - EPSILON)
                .map(Map.Entry::getKey)
                .toList();
        return new RepairNeed(!damagedCompartments.isEmpty() || !damagedMounts.isEmpty(),
                damagedCompartments, damagedMounts);
    }

    private AccelerationEnvelope accelerationEnvelope(
            EngineeringComponent component,
            DerivedShipState derived,
            DamageState damage) {
        double thrustN = 0d;
        for (InstalledModuleDefinition assignment : component.fit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            if (module == null || (module.family() != ModuleFamily.MAIN_DRIVE
                    && module.family() != ModuleFamily.MANEUVER_THRUSTERS)) {
                continue;
            }
            double rated = module.capabilityParameters().getOrDefault(ShipEngineeringRuntime.THRUST_N, 0d);
            double persistedLimit = component.runtimeState.thrustLimitNByMount()
                    .getOrDefault(assignment.mountId(), rated);
            double integrity = damage.moduleIntegrityByMount().getOrDefault(assignment.mountId(), 1d);
            thrustN += Math.min(rated, persistedLimit) * integrity;
        }
        double acceleration = derived.totalMassKg() <= 0d ? 0d : thrustN / derived.totalMassKg();
        return new AccelerationEnvelope(thrustN, acceleration, derived.totalMassKg());
    }

    private double remainingLocalThermalCapacityJ(EngineeringComponent component, DamageState damage) {
        double remaining = 0d;
        for (InstalledModuleDefinition assignment : component.fit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            if (module == null) {
                continue;
            }
            double integrity = damage.moduleIntegrityByMount().getOrDefault(assignment.mountId(), 1d);
            double capacity = module.localThermalCapacityJ() * integrity;
            double stored = component.runtimeState.localHeatJByMount().getOrDefault(assignment.mountId(), 0d);
            remaining += Math.max(0d, capacity - stored);
        }
        return remaining;
    }

    private static AmmunitionEndurance ammunitionEndurance(EngineeringComponent component) {
        List<AmmunitionFeed> feeds = component.instanceState.weaponLoadout().feeds().stream()
                .map(binding -> new AmmunitionFeed(
                        binding.mountId(), binding.interfaceId(), binding.ammunitionContentId(),
                        component.runtimeState.consumables().interfaceLoads().stream()
                                .filter(load -> load.mountId().equals(binding.mountId())
                                        && load.interfaceId().equals(binding.interfaceId()))
                                .mapToDouble(load -> load.amount()).sum(),
                        component.runtimeState.consumables().interfaceLoads().stream()
                                .filter(load -> load.mountId().equals(binding.mountId())
                                        && load.interfaceId().equals(binding.interfaceId()))
                                .mapToLong(load -> load.itemCount()).sum()))
                .toList();
        return new AmmunitionEndurance(
                component.runtimeState.consumables().ammunitionMassKg(),
                component.runtimeState.consumables().ammunitionCount(),
                feeds);
    }

    private static DamageCapabilityState damageCapabilityState(EngineeringComponent component) {
        return new DamageCapabilityState(
                component.instanceState.damage().compartmentIntegrityById(),
                component.instanceState.damage().moduleDamage().moduleIntegrityByMount());
    }

    private List<ShieldCoverage> shieldCoverage(EngineeringComponent component, DerivedShipState derived) {
        List<ShieldCoverage> result = new ArrayList<>();
        for (FittedShield fitted : shieldAdapter.derive(derived)) {
            State persisted = component.instanceState.shieldStatesByMount().get(fitted.mountId());
            if (persisted == null) {
                persisted = new State(0d, 0d, true, 0d, fitted.emitterIntegrity());
            } else {
                persisted = new ShieldFieldRuntime().withEmitterIntegrity(
                        fitted.definition(), persisted, fitted.emitterIntegrity());
            }
            result.add(new ShieldCoverage(
                    fitted.mountId(), fitted.moduleId(),
                    fitted.definition().coverageCenterRad(), fitted.definition().coverageHalfArcRad(),
                    persisted.reserveJ(), fitted.definition().reserveCapacityJ() * fitted.emitterIntegrity(),
                    persisted.collapsed(), persisted.restartRemainingSeconds(), fitted.emitterIntegrity()));
        }
        return List.copyOf(result);
    }

    private MaintenanceProjection maintenanceState(EngineeringComponent component) {
        MaintenanceState maintenance = component.instanceState.maintenance();
        List<String> overdue = new ArrayList<>();
        TreeMap<String, Double> ages = new TreeMap<>();
        for (InstalledModuleDefinition assignment : component.fit.installedModules()) {
            double age = maintenance.secondsSinceServiceByMount().getOrDefault(assignment.mountId(), 0d);
            ages.put(assignment.mountId(), age);
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            if (module != null && module.maintenance().serviceIntervalSeconds() > 0d
                    && age + EPSILON >= module.maintenance().serviceIntervalSeconds()) {
                overdue.add(assignment.mountId());
            }
        }
        return new MaintenanceProjection(ages, overdue);
    }

    private static EngineeringComponent requireComponent(EngineeringComponent component) {
        EngineeringComponent checked = Objects.requireNonNull(component, "component");
        Objects.requireNonNull(checked.fit, "component.fit");
        Objects.requireNonNull(checked.runtimeState, "component.runtimeState");
        Objects.requireNonNull(checked.instanceState, "component.instanceState");
        return checked;
    }

    /**
     * Immutable all-in-one capability projection.
     *
     * @param derived central damage-aware engineering state
     * @param acceleration current thrust/acceleration envelope
     * @param remainingDeltaVMps remaining reaction-mass delta-v
     * @param thermal thermal endurance projection
     * @param ammunition physical ammunition endurance
     * @param damage local damage projection
     * @param shields fitted/persisted shield coverage
     * @param maintenance scheduled-service projection
     * @param sensors fitted damage-aware sensor/signature projection
     */
    public record Snapshot(
            DerivedShipState derived,
            AccelerationEnvelope acceleration,
            double remainingDeltaVMps,
            ThermalEndurance thermal,
            AmmunitionEndurance ammunition,
            DamageCapabilityState damage,
            List<ShieldCoverage> shields,
            MaintenanceProjection maintenance,
            ShipSensorEngineeringAdapter.FittedSensorSuite sensors) {
        public Snapshot {
            Objects.requireNonNull(derived, "derived");
            Objects.requireNonNull(acceleration, "acceleration");
            if (!Double.isFinite(remainingDeltaVMps) || remainingDeltaVMps < 0d) {
                throw new IllegalArgumentException("remainingDeltaVMps must be finite and non-negative");
            }
            Objects.requireNonNull(thermal, "thermal");
            Objects.requireNonNull(ammunition, "ammunition");
            Objects.requireNonNull(damage, "damage");
            shields = List.copyOf(Objects.requireNonNull(shields, "shields"));
            Objects.requireNonNull(maintenance, "maintenance");
            Objects.requireNonNull(sensors, "sensors");
        }
    }

    /** @param maxThrustN current damage/limit-aware thrust envelope
     * @param maxAccelerationMps2 max current acceleration
     * @param totalMassKg current physical mass */
    public record AccelerationEnvelope(double maxThrustN, double maxAccelerationMps2, double totalMassKg) { }

    /** @param continuousHeatMarginW continuous heat-rejection minus waste-heat margin
     * @param shipHeatStoredJ heat stored on the common ship bus
     * @param remainingLocalCapacityJ remaining damage-aware module-local thermal capacity */
    public record ThermalEndurance(
            double continuousHeatMarginW, double shipHeatStoredJ, double remainingLocalCapacityJ) { }

    /** @param ammunitionMassKg physical ammunition mass
     * @param ammunitionCount authored ammunition item count
     * @param feeds ammunition identity/quantity by physical feed */
    public record AmmunitionEndurance(double ammunitionMassKg, long ammunitionCount, List<AmmunitionFeed> feeds) {
        public AmmunitionEndurance {
            feeds = List.copyOf(Objects.requireNonNull(feeds, "feeds"));
        }
    }

    /** @param mountId fitted weapon mount
     * @param interfaceId module-local feed
     * @param ammunitionContentId loaded ammunition identity
     * @param amount physical interface amount
     * @param itemCount physical item count */
    public record AmmunitionFeed(
            String mountId, String interfaceId, String ammunitionContentId, double amount, long itemCount) { }

    /** @param compartmentIntegrityById local compartment structural integrity
     * @param moduleIntegrityByMount local installed subsystem integrity */
    public record DamageCapabilityState(
            Map<String, Double> compartmentIntegrityById, Map<String, Double> moduleIntegrityByMount) {
        public DamageCapabilityState {
            compartmentIntegrityById = Map.copyOf(Objects.requireNonNull(compartmentIntegrityById, "compartments"));
            moduleIntegrityByMount = Map.copyOf(Objects.requireNonNull(moduleIntegrityByMount, "modules"));
        }
    }

    /** @param repairRequired whether local damage exists
     * @param damagedCompartmentIds damaged compartment IDs
     * @param damagedMountIds damaged module mount IDs */
    public record RepairNeed(
            boolean repairRequired, List<String> damagedCompartmentIds, List<String> damagedMountIds) {
        public RepairNeed {
            damagedCompartmentIds = List.copyOf(Objects.requireNonNull(damagedCompartmentIds, "compartments"));
            damagedMountIds = List.copyOf(Objects.requireNonNull(damagedMountIds, "mounts"));
        }
    }

    /** @param mountId physical emitter mount
     * @param moduleId emitter module content ID
     * @param coverageCenterRad hull-local coverage center
     * @param coverageHalfArcRad coverage half arc
     * @param reserveJ persisted current reserve
     * @param reserveCapacityJ current damage-aware capacity
     * @param collapsed whether field is collapsed
     * @param restartRemainingSeconds remaining lockout
     * @param emitterIntegrity current physical emitter integrity */
    public record ShieldCoverage(
            String mountId,
            String moduleId,
            double coverageCenterRad,
            double coverageHalfArcRad,
            double reserveJ,
            double reserveCapacityJ,
            boolean collapsed,
            double restartRemainingSeconds,
            double emitterIntegrity) { }

    /** @param secondsSinceServiceByMount current service age
     * @param overdueMounts mounts at or past authored service interval */
    public record MaintenanceProjection(Map<String, Double> secondsSinceServiceByMount, List<String> overdueMounts) {
        public MaintenanceProjection {
            secondsSinceServiceByMount = Map.copyOf(Objects.requireNonNull(secondsSinceServiceByMount, "ages"));
            overdueMounts = List.copyOf(Objects.requireNonNull(overdueMounts, "overdueMounts"));
        }
    }
}
