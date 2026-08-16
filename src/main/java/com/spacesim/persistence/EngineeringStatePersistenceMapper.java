package com.spacesim.persistence;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

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
        List<EntityState.InstalledModuleState> modules = fit.installedModules().stream()
                .map(value -> new EntityState.InstalledModuleState(value.mountId(), value.moduleId()))
                .toList();
        ConsumableState consumables = runtime.consumables();
        List<EntityState.EngineeringConsumableLoadState> loads = consumables.interfaceLoads().stream()
                .map(value -> new EntityState.EngineeringConsumableLoadState(
                        value.mountId(), value.interfaceId(), value.kind().name(),
                        value.amount(), value.massKg(), value.itemCount()))
                .toList();
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
                captureMap(runtime.ftlCooldownSecondsByMount()));
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
        return new EngineeringComponent(fit, runtime);
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
            String mountId = requireNonBlank(row.mountId(), label + " mountId");
            if (result.putIfAbsent(mountId, row.value()) != null) {
                throw new IllegalArgumentException("Duplicate " + label + " mount: " + mountId);
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
