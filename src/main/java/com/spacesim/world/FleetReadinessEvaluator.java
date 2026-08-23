package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityState.EngineeringConsumableLoadState;
import com.spacesim.persistence.EntityState.EngineeringState;
import com.spacesim.persistence.EntityState.InstalledModuleState;
import com.spacesim.persistence.EntityState.MountDoubleState;
import com.spacesim.persistence.EntityState.ShipInstanceState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Derives Stage-21D fleet readiness without owning or mutating any physical state. */
public final class FleetReadinessEvaluator {
    private final ShipEngineeringCatalog catalog;

    /**
     * Creates a readiness evaluator backed by the existing ship-engineering catalog.
     *
     * @param catalog authoritative hull/module/interface definitions used to interpret fitted state
     */
    public FleetReadinessEvaluator(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * Derives bounded readiness dimensions from an authoritative fitted fleet entity and explicit
     * operational observations.
     *
     * <p>The evaluator is read-only. Hull/module fit, damage, ammunition, reaction mass, sensors and
     * maintenance remain owned by their existing engineering/persistence layers. Crew and supply access
     * use the explicit availability seam and fail closed when absent.</p>
     *
     * @param entity exact authoritative persistent fleet entity payload
     * @param availability observed crew and service-access prerequisites, or {@code null} to fail closed
     * @return immutable readiness projection; unknown engineering/catalog state returns unavailable readiness
     */
    public FleetReadinessState evaluate(EntityState entity, FleetOperationalAvailability availability) {
        Objects.requireNonNull(entity, "entity");
        FleetOperationalAvailability observed = availability == null
                ? FleetOperationalAvailability.unavailable()
                : availability;
        EngineeringState engineering = entity.engineering();
        if (engineering == null) {
            return FleetReadinessState.unavailable();
        }
        HullDefinition hull = catalog.findHull(engineering.hullId());
        if (hull == null) {
            return FleetReadinessState.unavailable();
        }

        Map<String, ModuleDefinition> modules = new HashMap<>();
        int requiredCrew = hull.crewBaseline();
        boolean unknownModule = false;
        for (InstalledModuleState installed : engineering.installedModules()) {
            ModuleDefinition definition = catalog.findModule(installed.moduleId());
            if (definition == null) {
                unknownModule = true;
                continue;
            }
            modules.put(installed.mountId(), definition);
            requiredCrew = Math.addExact(requiredCrew, Math.max(0, definition.crewRequirement()));
        }
        if (unknownModule) {
            return FleetReadinessState.unavailable();
        }

        ShipInstanceState instance = engineering.instanceState();
        Map<String, Double> moduleIntegrity = valuesByMount(
                instance == null ? null : instance.moduleIntegrityByMount());
        int structural = structuralBps(instance);
        int ammunition = consumableBps(engineering, modules, InterfaceKind.AMMUNITION);
        int propellant = consumableBps(engineering, modules, InterfaceKind.REACTION_MASS);
        int crew = ratioBps(observed.availableCrew(), Math.max(1, requiredCrew));
        int sensors = sensorBps(modules, moduleIntegrity);
        int maintenance = maintenanceBps(modules,
                valuesByMount(instance == null ? null : instance.serviceAgeByMount()));
        return new FleetReadinessState(
                structural,
                ammunition,
                propellant,
                crew,
                sensors,
                maintenance,
                observed.supplyAccessBps());
    }

    private static int structuralBps(ShipInstanceState instance) {
        if (instance == null) {
            // Pre-Stage-17.5H fitted saves had no local damage extension; migration semantics mean
            // no recorded damage rather than an unknown hull.
            return FleetReadinessState.FULL;
        }
        double minimum = 1d;
        boolean observed = false;
        for (MountDoubleState value : instance.compartmentIntegrityById()) {
            minimum = Math.min(minimum, normalizedIntegrity(value.value()));
            observed = true;
        }
        for (MountDoubleState value : instance.moduleIntegrityByMount()) {
            minimum = Math.min(minimum, normalizedIntegrity(value.value()));
            observed = true;
        }
        return observed ? fractionBps(minimum) : FleetReadinessState.FULL;
    }

    private static int sensorBps(Map<String, ModuleDefinition> modules, Map<String, Double> integrity) {
        int best = -1;
        for (Map.Entry<String, ModuleDefinition> entry : modules.entrySet()) {
            if (entry.getValue().family() != ModuleFamily.SENSOR_EW_FIRE_CONTROL) {
                continue;
            }
            double value = normalizedIntegrity(integrity.getOrDefault(entry.getKey(), 1d));
            best = Math.max(best, fractionBps(value));
        }
        return Math.max(0, best);
    }

    private static int maintenanceBps(Map<String, ModuleDefinition> modules, Map<String, Double> ages) {
        int worst = FleetReadinessState.FULL;
        boolean scheduled = false;
        for (Map.Entry<String, ModuleDefinition> entry : modules.entrySet()) {
            double interval = entry.getValue().maintenance().serviceIntervalSeconds();
            if (!(interval > 0d) || !Double.isFinite(interval)) {
                continue;
            }
            scheduled = true;
            double age = Math.max(0d, ages.getOrDefault(entry.getKey(), 0d));
            double fraction = Math.max(0d, 1d - age / (2d * interval));
            worst = Math.min(worst, fractionBps(fraction));
        }
        return scheduled ? worst : FleetReadinessState.FULL;
    }

    private static int consumableBps(
            EngineeringState engineering,
            Map<String, ModuleDefinition> modules,
            InterfaceKind kind) {
        double requiredCapacity = 0d;
        double loaded = 0d;
        Map<String, EngineeringConsumableLoadState> loads = new HashMap<>();
        if (engineering.consumables() != null) {
            for (EngineeringConsumableLoadState load : engineering.consumables().interfaceLoads()) {
                loads.put(load.mountId() + "\u0000" + load.interfaceId(), load);
            }
        }
        for (Map.Entry<String, ModuleDefinition> entry : modules.entrySet()) {
            for (InterfaceDefinition iface : entry.getValue().interfaces()) {
                if (iface.kind() != kind) {
                    continue;
                }
                requiredCapacity += Math.max(0d, iface.capacity());
                EngineeringConsumableLoadState load = loads.get(entry.getKey() + "\u0000" + iface.id());
                if (load != null && kind.name().equals(load.kindName())) {
                    loaded += Math.max(0d, Math.min(iface.capacity(), load.amount()));
                }
            }
        }
        if (!(requiredCapacity > 0d)) {
            return FleetReadinessState.FULL;
        }
        return fractionBps(loaded / requiredCapacity);
    }

    private static Map<String, Double> valuesByMount(java.util.List<MountDoubleState> values) {
        Map<String, Double> result = new HashMap<>();
        if (values != null) {
            for (MountDoubleState value : values) {
                result.put(value.mountId(), value.value());
            }
        }
        return result;
    }

    private static double normalizedIntegrity(double value) {
        if (!Double.isFinite(value)) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, value));
    }

    private static int ratioBps(long actual, long required) {
        if (required <= 0L) {
            return FleetReadinessState.FULL;
        }
        return fractionBps((double) actual / (double) required);
    }

    private static int fractionBps(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        return (int) Math.round(Math.max(0d, Math.min(1d, value)) * FleetReadinessState.FULL);
    }
}
