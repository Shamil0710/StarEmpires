package com.spacesim.persistence;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.ShieldFieldRuntime;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.WeaponLoadoutState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Value-only mapper for the Stage-17.5 engineering component nested inside {@link EntityState}. */
final class EngineeringStatePersistenceMapper {
    private EngineeringStatePersistenceMapper() {
        throw new AssertionError("Utility class");
    }

    static EntityState.EngineeringState capture(EngineeringComponent component) {
        if (component == null) {
            return null;
        }
        InstalledFit fit = Objects.requireNonNull(component.fit, "EngineeringComponent.fit");
        RuntimeState runtime = Objects.requireNonNull(component.runtimeState, "EngineeringComponent.runtimeState");
        ShipInstanceRuntimeState instance = Objects.requireNonNull(
                component.instanceState, "EngineeringComponent.instanceState");
        List<EntityState.InstalledModuleState> modules = fit.installedModules().stream()
                .map(value -> new EntityState.InstalledModuleState(value.mountId(), value.moduleId()))
                .toList();
        ConsumableState consumables = runtime.consumables();
        List<EntityState.EngineeringConsumableLoadState> loads = consumables.interfaceLoads().stream()
                .map(value -> new EntityState.EngineeringConsumableLoadState(
                        value.mountId(), value.interfaceId(), value.kind().name(),
                        value.amount(), value.massKg(), value.itemCount()))
                .toList();
        List<EntityState.ShieldRuntimeState> shields = instance.shieldStatesByMount().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new EntityState.ShieldRuntimeState(
                        entry.getKey(), entry.getValue().reserveJ(), entry.getValue().accumulatedHeatJ(),
                        entry.getValue().collapsed(), entry.getValue().restartRemainingSeconds(),
                        entry.getValue().emitterIntegrity()))
                .toList();
        List<EntityState.WeaponFeedState> feeds = instance.weaponLoadout().feeds().stream()
                .map(value -> new EntityState.WeaponFeedState(
                        value.mountId(), value.interfaceId(), value.ammunitionContentId()))
                .toList();
        EntityState.ShipInstanceState instanceState = new EntityState.ShipInstanceState(
                captureMap(instance.damage().compartmentIntegrityById()),
                captureMap(instance.damage().moduleDamage().moduleIntegrityByMount()),
                shields,
                captureMap(instance.maintenance().secondsSinceServiceByMount()),
                feeds,
                captureMap(instance.weaponMountRuntime().cooldownSecondsByMount()));
        return new EntityState.EngineeringState(
                fit.hullId(),
                List.copyOf(modules),
                new EntityState.EngineeringConsumableState(
                        consumables.cargoMassKg(),
                        consumables.storesMassKg(),
                        consumables.missionPayloadMassKg(),
                        consumables.missionIntegrationVolumeM3(),
                        List.copyOf(loads)),
                runtime.sharedBusEnergyJ(),
                runtime.shipHeatStoredJ(),
                captureMap(runtime.localHeatJByMount()),
                captureMap(runtime.thrustLimitNByMount()),
                runtime.coolantBusCapacityW(),
                captureMap(runtime.ftlCooldownSecondsByMount()),
                instanceState);
    }

    static EngineeringComponent restore(EntityState.EngineeringState state) {
        EntityState.EngineeringState checked = Objects.requireNonNull(state, "EngineeringState");
        List<InstalledModuleDefinition> modules = new ArrayList<>();
        for (EntityState.InstalledModuleState value : requireList(checked.installedModules(), "installedModules")) {
            modules.add(new InstalledModuleDefinition(
                    requireNonBlank(value.mountId(), "installed module mountId"),
                    requireNonBlank(value.moduleId(), "installed module moduleId")));
        }
        InstalledFit fit = new InstalledFit(requireNonBlank(checked.hullId(), "hullId"), modules);

        EntityState.EngineeringConsumableState persistedLoads = Objects.requireNonNull(
                checked.consumables(), "engineering consumables");
        List<ConsumableLoad> loads = new ArrayList<>();
        for (EntityState.EngineeringConsumableLoadState value
                : requireList(persistedLoads.interfaceLoads(), "interfaceLoads")) {
            InterfaceKind kind;
            try {
                kind = InterfaceKind.valueOf(requireNonBlank(value.kindName(), "interface kind"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown engineering interface kind: " + value.kindName(), exception);
            }
            loads.add(new ConsumableLoad(
                    requireNonBlank(value.mountId(), "consumable mountId"),
                    requireNonBlank(value.interfaceId(), "consumable interfaceId"),
                    kind,
                    value.amount(),
                    value.massKg(),
                    value.itemCount()));
        }
        ConsumableState consumables = new ConsumableState(
                persistedLoads.cargoMassKg(),
                persistedLoads.storesMassKg(),
                persistedLoads.missionPayloadMassKg(),
                persistedLoads.missionIntegrationVolumeM3(),
                loads);
        RuntimeState runtime = new RuntimeState(
                consumables,
                checked.sharedBusEnergyJ(),
                checked.shipHeatStoredJ(),
                restoreMap(checked.localHeatJByMount(), "localHeatJByMount"),
                restoreMap(checked.thrustLimitNByMount(), "thrustLimitNByMount"),
                checked.coolantBusCapacityW(),
                restoreMap(checked.ftlCooldownSecondsByMount(), "ftlCooldownSecondsByMount"));
        ShipInstanceRuntimeState instance = restoreInstance(checked.instanceState());
        return new EngineeringComponent(fit, runtime, instance);
    }

    private static ShipInstanceRuntimeState restoreInstance(EntityState.ShipInstanceState state) {
        if (state == null) {
            return ShipInstanceRuntimeState.legacyNeutral();
        }
        Map<String, Double> compartments = restoreMap(state.compartmentIntegrityById(), "compartmentIntegrityById");
        Map<String, Double> modules = restoreMap(state.moduleIntegrityByMount(), "moduleIntegrityByMount");
        TreeMap<String, ShieldFieldRuntime.State> shields = new TreeMap<>();
        for (EntityState.ShieldRuntimeState row : requireList(state.shieldsByMount(), "shieldsByMount")) {
            String mountId = requireNonBlank(row.mountId(), "shield mountId");
            if (shields.putIfAbsent(mountId, new ShieldFieldRuntime.State(
                    row.reserveJ(), row.accumulatedHeatJ(), row.collapsed(),
                    row.restartRemainingSeconds(), row.emitterIntegrity())) != null) {
                throw new IllegalArgumentException("Duplicate shield mount: " + mountId);
            }
        }
        List<WeaponLoadoutState.FeedBinding> feeds = requireList(state.weaponFeeds(), "weaponFeeds").stream()
                .map(row -> new WeaponLoadoutState.FeedBinding(
                        requireNonBlank(row.mountId(), "weapon feed mountId"),
                        requireNonBlank(row.interfaceId(), "weapon feed interfaceId"),
                        requireNonBlank(row.ammunitionContentId(), "weapon feed ammunitionContentId")))
                .toList();
        return new ShipInstanceRuntimeState(
                new ShipDamageRuntime.Snapshot(compartments, new DamageState(modules)),
                shields,
                new MaintenanceState(restoreMap(state.serviceAgeByMount(), "serviceAgeByMount")),
                new WeaponLoadoutState(feeds),
                new com.spacesim.ship.WeaponMountRuntime.RuntimeState(
                        restoreMap(state.weaponCooldownByMount(), "weaponCooldownByMount")));
    }

    private static List<EntityState.MountDoubleState> captureMap(Map<String, Double> source) {
        return source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new EntityState.MountDoubleState(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Map<String, Double> restoreMap(List<EntityState.MountDoubleState> rows, String label) {
        TreeMap<String, Double> result = new TreeMap<>();
        for (EntityState.MountDoubleState row : requireList(rows, label)) {
            String mountId = requireNonBlank(row.mountId(), label + " key");
            if (result.putIfAbsent(mountId, row.value()) != null) {
                throw new IllegalArgumentException("Duplicate " + label + " key: " + mountId);
            }
        }
        return result;
    }

    private static <T> List<T> requireList(List<T> values, String label) {
        Objects.requireNonNull(values, label);
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " contains null");
        }
        return values;
    }

    private static String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return value;
    }
}
