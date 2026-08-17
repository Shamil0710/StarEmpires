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
 * <p>Every query is rebuilt from the fitted catalog plus exact current engineering, consumable,
 * local damage, shield, maintenance and weapon-continuity state. Derived values are not cached as a
 * second source of truth and this service never mutates ECS.</p>
 */
public final class ShipCapabilityService {
    private static final double EPSILON = 1e-9d;
    private final ShipEngineeringCatalog catalog;
    private final ShipEngineeringRuntime engineeringRuntime;
    private final ShipSensorEngineeringAdapter sensorAdapter = new ShipSensorEngineeringAdapter();
    private final ShipShieldEngineeringAdapter shieldAdapter = new ShipShieldEngineeringAdapter();

    /**
     * Creates the shared capability projection service.
     *
     * @param catalog production ship engineering definitions
     */
    public ShipCapabilityService(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.engineeringRuntime = new ShipEngineeringRuntime(catalog);
    }

    /**
     * Builds the complete immutable physical capability projection.
     *
     * @param component authoritative physical engineering component
     * @return immutable projection
     */
    public Snapshot snapshot(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        DamageState damage = checked.instanceState.damage().moduleDamage();
        DerivedShipState derived = engineeringRuntime.derive(checked.fit, checked.runtimeState, damage);
        return new Snapshot(derived, accelerationEnvelope(checked, derived, damage), derived.deltaVMps(),
                new ThermalEndurance(derived.continuousHeatMarginW(), checked.runtimeState.shipHeatStoredJ(),
                        remainingLocalThermalCapacityJ(checked, damage)),
                ammunitionEndurance(checked), damageCapabilityState(checked), shieldCoverage(checked, derived),
                maintenanceState(checked), sensorAdapter.derive(derived));
    }

    /**
     * Returns the damage-aware thrust and acceleration envelope.
     *
     * @param component authoritative physical engineering component
     * @return acceleration envelope
     */
    public AccelerationEnvelope getAccelerationEnvelope(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        DamageState damage = checked.instanceState.damage().moduleDamage();
        DerivedShipState derived = engineeringRuntime.derive(checked.fit, checked.runtimeState, damage);
        return accelerationEnvelope(checked, derived, damage);
    }

    /**
     * Returns current remaining reaction-mass delta-v.
     *
     * @param component authoritative component
     * @return remaining delta-v in meters per second
     */
    public double getRemainingDeltaV(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        return engineeringRuntime.derive(checked.fit, checked.runtimeState,
                checked.instanceState.damage().moduleDamage()).deltaVMps();
    }

    /**
     * Plans an FTL jump using the current damage-aware fit and operating state.
     *
     * @param component authoritative component
     * @return damage-aware fitted jump plan
     */
    public JumpPlan planJump(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        return engineeringRuntime.planJump(checked.fit, checked.runtimeState,
                checked.instanceState.damage().moduleDamage());
    }

    /**
     * Returns current fitted shield coverage and reserve state.
     *
     * @param component authoritative component
     * @return fitted shield coverage
     */
    public List<ShieldCoverage> getShieldCoverage(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        DamageState damage = checked.instanceState.damage().moduleDamage();
        return shieldCoverage(checked, engineeringRuntime.derive(checked.fit, checked.runtimeState, damage));
    }

    /**
     * Returns current thermal margins and stored heat.
     *
     * @param component authoritative component
     * @return thermal endurance
     */
    public ThermalEndurance getThermalEndurance(EngineeringComponent component) {
        EngineeringComponent checked = requireComponent(component);
        DamageState damage = checked.instanceState.damage().moduleDamage();
        DerivedShipState derived = engineeringRuntime.derive(checked.fit, checked.runtimeState, damage);
        return new ThermalEndurance(derived.continuousHeatMarginW(), checked.runtimeState.shipHeatStoredJ(),
                remainingLocalThermalCapacityJ(checked, damage));
    }

    /**
     * Returns physical ammunition mass/count and feed bindings.
     *
     * @param component authoritative component
     * @return ammunition endurance
     */
    public AmmunitionEndurance getAmmunitionEndurance(EngineeringComponent component) {
        return ammunitionEndurance(requireComponent(component));
    }

    /**
     * Returns local compartment and module integrity.
     *
     * @param component authoritative component
     * @return local damage capability state
     */
    public DamageCapabilityState getDamageCapabilityState(EngineeringComponent component) {
        return damageCapabilityState(requireComponent(component));
    }

    /**
     * Returns ordinary repair need without mutating engineering state.
     *
     * @param component authoritative component
     * @return ordinary repair need
     */
    public RepairNeed getRepairNeed(EngineeringComponent component) {
        DamageCapabilityState damage = getDamageCapabilityState(component);
        List<String> compartments = damage.compartmentIntegrityById().entrySet().stream()
                .filter(entry -> entry.getValue() < 1d - EPSILON).map(Map.Entry::getKey).toList();
        List<String> mounts = damage.moduleIntegrityByMount().entrySet().stream()
                .filter(entry -> entry.getValue() < 1d - EPSILON).map(Map.Entry::getKey).toList();
        return new RepairNeed(!compartments.isEmpty() || !mounts.isEmpty(), compartments, mounts);
    }

    private AccelerationEnvelope accelerationEnvelope(
            EngineeringComponent component, DerivedShipState derived, DamageState damage) {
        double thrustN = 0d;
        for (InstalledModuleDefinition assignment : component.fit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            if (module == null || (module.family() != ModuleFamily.MAIN_DRIVE
                    && module.family() != ModuleFamily.MANEUVER_THRUSTERS)) {
                continue;
            }
            double rated = module.capabilityParameters().getOrDefault(ShipEngineeringRuntime.THRUST_N, 0d);
            double limit = component.runtimeState.thrustLimitNByMount().getOrDefault(assignment.mountId(), rated);
            double integrity = damage.moduleIntegrityByMount().getOrDefault(assignment.mountId(), 1d);
            thrustN += Math.min(rated, limit) * integrity;
        }
        return new AccelerationEnvelope(thrustN,
                derived.totalMassKg() <= 0d ? 0d : thrustN / derived.totalMassKg(), derived.totalMassKg());
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
        List<AmmunitionFeed> feeds = component.instanceState.weaponLoadout().feeds().stream().map(binding ->
                new AmmunitionFeed(binding.mountId(), binding.interfaceId(), binding.ammunitionContentId(),
                        component.runtimeState.consumables().interfaceLoads().stream()
                                .filter(load -> load.mountId().equals(binding.mountId())
                                        && load.interfaceId().equals(binding.interfaceId()))
                                .mapToDouble(load -> load.amount()).sum(),
                        component.runtimeState.consumables().interfaceLoads().stream()
                                .filter(load -> load.mountId().equals(binding.mountId())
                                        && load.interfaceId().equals(binding.interfaceId()))
                                .mapToLong(load -> load.itemCount()).sum())).toList();
        return new AmmunitionEndurance(component.runtimeState.consumables().ammunitionMassKg(),
                component.runtimeState.consumables().ammunitionCount(), feeds);
    }

    private static DamageCapabilityState damageCapabilityState(EngineeringComponent component) {
        return new DamageCapabilityState(component.instanceState.damage().compartmentIntegrityById(),
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
            result.add(new ShieldCoverage(fitted.mountId(), fitted.moduleId(),
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
     * All-in-one read-only capability projection.
     *
     * @param derived central damage-aware engineering state
     * @param acceleration current acceleration envelope
     * @param remainingDeltaVMps remaining reaction-mass delta-v
     * @param thermal thermal endurance
     * @param ammunition ammunition endurance
     * @param damage local damage
     * @param shields shield coverage
     * @param maintenance maintenance projection
     * @param sensors fitted sensor suite
     */
    public record Snapshot(DerivedShipState derived, AccelerationEnvelope acceleration, double remainingDeltaVMps,
            ThermalEndurance thermal, AmmunitionEndurance ammunition, DamageCapabilityState damage,
            List<ShieldCoverage> shields, MaintenanceProjection maintenance,
            ShipSensorEngineeringAdapter.FittedSensorSuite sensors) {
        /**
         * Validates and freezes one snapshot.
         *
         * @param derived central damage-aware engineering state
         * @param acceleration current acceleration envelope
         * @param remainingDeltaVMps remaining reaction-mass delta-v
         * @param thermal thermal endurance
         * @param ammunition ammunition endurance
         * @param damage local damage
         * @param shields shield coverage
         * @param maintenance maintenance projection
         * @param sensors fitted sensor suite
         */
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

    /**
     * Current thrust-derived acceleration envelope.
     *
     * @param maxThrustN maximum available thrust
     * @param maxAccelerationMps2 maximum acceleration
     * @param totalMassKg current total mass
     */
    public record AccelerationEnvelope(double maxThrustN, double maxAccelerationMps2, double totalMassKg) { }

    /**
     * Current thermal headroom projection.
     *
     * @param continuousHeatMarginW continuous heat margin
     * @param shipHeatStoredJ stored ship-bus heat
     * @param remainingLocalCapacityJ remaining module-local thermal capacity
     */
    public record ThermalEndurance(double continuousHeatMarginW, double shipHeatStoredJ,
            double remainingLocalCapacityJ) { }

    /**
     * Current ammunition endurance projection.
     *
     * @param ammunitionMassKg physical ammunition mass
     * @param ammunitionCount physical ammunition item count
     * @param feeds loaded weapon feed rows
     */
    public record AmmunitionEndurance(double ammunitionMassKg, long ammunitionCount, List<AmmunitionFeed> feeds) {
        /**
         * Validates and freezes feeds.
         *
         * @param ammunitionMassKg physical ammunition mass
         * @param ammunitionCount physical ammunition item count
         * @param feeds loaded weapon feed rows
         */
        public AmmunitionEndurance {
            feeds = List.copyOf(Objects.requireNonNull(feeds, "feeds"));
        }
    }

    /**
     * One loaded ammunition feed projection.
     *
     * @param mountId weapon mount
     * @param interfaceId feed interface
     * @param ammunitionContentId ammunition content ID
     * @param amount interface-native physical amount
     * @param itemCount physical item count
     */
    public record AmmunitionFeed(String mountId, String interfaceId, String ammunitionContentId,
            double amount, long itemCount) { }

    /**
     * Current local damage capability state.
     *
     * @param compartmentIntegrityById compartment integrity by compartment ID
     * @param moduleIntegrityByMount module integrity by fitted mount
     */
    public record DamageCapabilityState(Map<String, Double> compartmentIntegrityById,
            Map<String, Double> moduleIntegrityByMount) {
        /**
         * Validates and freezes integrity maps.
         *
         * @param compartmentIntegrityById compartment integrity by compartment ID
         * @param moduleIntegrityByMount module integrity by fitted mount
         */
        public DamageCapabilityState {
            compartmentIntegrityById = Map.copyOf(Objects.requireNonNull(compartmentIntegrityById, "compartments"));
            moduleIntegrityByMount = Map.copyOf(Objects.requireNonNull(moduleIntegrityByMount, "modules"));
        }
    }

    /**
     * Ordinary repair requirement projection.
     *
     * @param repairRequired whether any physical repair is required
     * @param damagedCompartmentIds damaged compartment IDs
     * @param damagedMountIds damaged fitted mount IDs
     */
    public record RepairNeed(boolean repairRequired, List<String> damagedCompartmentIds, List<String> damagedMountIds) {
        /**
         * Validates and freezes repair targets.
         *
         * @param repairRequired whether any physical repair is required
         * @param damagedCompartmentIds damaged compartment IDs
         * @param damagedMountIds damaged fitted mount IDs
         */
        public RepairNeed {
            damagedCompartmentIds = List.copyOf(Objects.requireNonNull(damagedCompartmentIds, "compartments"));
            damagedMountIds = List.copyOf(Objects.requireNonNull(damagedMountIds, "mounts"));
        }
    }

    /**
     * Current shield emitter coverage projection.
     *
     * @param mountId emitter mount
     * @param moduleId emitter module content ID
     * @param coverageCenterRad coverage center angle
     * @param coverageHalfArcRad coverage half arc
     * @param reserveJ current field reserve
     * @param reserveCapacityJ damage-aware reserve capacity
     * @param collapsed whether the field is collapsed
     * @param restartRemainingSeconds remaining restart lockout
     * @param emitterIntegrity current emitter integrity
     */
    public record ShieldCoverage(String mountId, String moduleId, double coverageCenterRad, double coverageHalfArcRad,
            double reserveJ, double reserveCapacityJ, boolean collapsed, double restartRemainingSeconds,
            double emitterIntegrity) { }

    /**
     * Current maintenance projection.
     *
     * @param secondsSinceServiceByMount service age by fitted mount
     * @param overdueMounts mounts at or beyond authored service interval
     */
    public record MaintenanceProjection(Map<String, Double> secondsSinceServiceByMount, List<String> overdueMounts) {
        /**
         * Validates and freezes maintenance collections.
         *
         * @param secondsSinceServiceByMount service age by fitted mount
         * @param overdueMounts mounts at or beyond authored service interval
         */
        public MaintenanceProjection {
            secondsSinceServiceByMount = Map.copyOf(Objects.requireNonNull(secondsSinceServiceByMount, "ages"));
            overdueMounts = List.copyOf(Objects.requireNonNull(overdueMounts, "overdueMounts"));
        }
    }
}
