package com.spacesim.economy;

import com.spacesim.content.Stage18ShipConsumableCatalog;
import com.spacesim.content.Stage18ShipConsumableCatalog.ShipConsumableBinding;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stage-18I physical servicing seam from canonical station commodity stock into ship interfaces.
 *
 * <p>The service never creates reaction mass/consumables on docking. It requires an authored
 * commodity-to-interface binding, the corresponding module to be installed on the requested mount,
 * remaining interface capacity and sufficient canonical Stage-18F station stock. Inventory mutation
 * occurs only after all checks succeed.</p>
 */
public final class Stage18ShipConsumableService {
    private static final double EPSILON = 1e-9d;

    private final Stage18ShipConsumableCatalog bindings;
    private final ShipEngineeringCatalog engineering;

    /**
     * Creates a physical ship-consumable servicing boundary.
     *
     * @param bindings authoritative Stage-18I interface commodity bindings
     * @param engineering authoritative Stage-17.5 ship engineering catalog
     */
    public Stage18ShipConsumableService(
            Stage18ShipConsumableCatalog bindings,
            ShipEngineeringCatalog engineering) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.engineering = Objects.requireNonNull(engineering, "engineering");
    }

    /** Stable servicing outcome. */
    public enum Status {
        /** Requested physical mass was removed from station stock and loaded into the ship. */ LOADED,
        /** Request arguments are invalid. */ INVALID_REQUEST,
        /** Binding ID is unknown. */ BINDING_NOT_FOUND,
        /** Requested mount does not contain the module authored by the binding. */ MODULE_NOT_INSTALLED,
        /** Authored module interface is unavailable or no longer matches the binding. */ INTERFACE_NOT_FOUND,
        /** Requested mass would exceed remaining interface-native capacity. */ INTERFACE_CAPACITY_EXCEEDED,
        /** Canonical station storage lacks the bound physical commodity mass. */ INSUFFICIENT_STOCK
    }

    /**
     * One immutable physical servicing result.
     *
     * @param status stable outcome
     * @param bindingId attempted binding ID
     * @param loadedMassKg physical mass committed from station stock
     * @param consumables resulting ship consumable state
     */
    public record LoadResult(
            Status status,
            String bindingId,
            double loadedMassKg,
            ConsumableState consumables) {
        /**
         * Validates one servicing result.
         *
         * @param status outcome
         * @param bindingId binding ID
         * @param loadedMassKg committed mass
         * @param consumables resulting ship state
         */
        public LoadResult {
            Objects.requireNonNull(status, "status");
            bindingId = bindingId == null ? "" : bindingId;
            if (!Double.isFinite(loadedMassKg) || loadedMassKg < 0d) {
                throw new IllegalArgumentException("loadedMassKg must be finite and non-negative");
            }
            Objects.requireNonNull(consumables, "consumables");
        }

        /** @return whether station stock and ship state were committed */
        public boolean committed() {
            return status == Status.LOADED;
        }
    }

    /**
     * Loads one bound physical commodity into a concrete fitted module interface.
     *
     * @param bindingId authored Stage-18I servicing binding ID
     * @param mountId fitted module mount receiving the load
     * @param requestedMassKg requested physical mass
     * @param fit current installed fit
     * @param current current ship consumables
     * @param station canonical Stage-18F source storage
     * @return immutable servicing result; rejected calls mutate neither station nor ship state
     */
    public LoadResult load(
            String bindingId,
            String mountId,
            double requestedMassKg,
            InstalledFit fit,
            ConsumableState current,
            Stage18StationStorage station) {
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        ConsumableState checkedCurrent = Objects.requireNonNull(current, "current");
        Stage18StationStorage checkedStation = Objects.requireNonNull(station, "station");
        if (bindingId == null || bindingId.isBlank() || mountId == null || mountId.isBlank()
                || !Double.isFinite(requestedMassKg) || requestedMassKg <= 0d) {
            return rejected(Status.INVALID_REQUEST, bindingId, checkedCurrent);
        }
        ShipConsumableBinding binding = bindings.findBinding(bindingId);
        if (binding == null) {
            return rejected(Status.BINDING_NOT_FOUND, bindingId, checkedCurrent);
        }
        InstalledModuleDefinition installed = checkedFit.installedModules().stream()
                .filter(value -> value.mountId().equals(mountId))
                .findFirst()
                .orElse(null);
        if (installed == null || !installed.moduleId().equals(binding.moduleId())) {
            return rejected(Status.MODULE_NOT_INSTALLED, bindingId, checkedCurrent);
        }
        ModuleDefinition module = engineering.findModule(binding.moduleId());
        InterfaceDefinition physicalInterface = module == null ? null : module.interfaces().stream()
                .filter(value -> value.id().equals(binding.interfaceId()) && value.kind() == binding.interfaceKind())
                .findFirst()
                .orElse(null);
        if (physicalInterface == null) {
            return rejected(Status.INTERFACE_NOT_FOUND, bindingId, checkedCurrent);
        }

        ConsumableLoad existing = checkedCurrent.interfaceLoads().stream()
                .filter(value -> value.mountId().equals(mountId)
                        && value.interfaceId().equals(binding.interfaceId())
                        && value.kind() == binding.interfaceKind())
                .findFirst()
                .orElse(null);
        double currentAmount = existing == null ? 0d : existing.amount();
        double addedAmount = requestedMassKg * binding.amountPerKg();
        if (!Double.isFinite(addedAmount)
                || currentAmount + addedAmount > physicalInterface.capacity() + EPSILON) {
            return rejected(Status.INTERFACE_CAPACITY_EXCEEDED, bindingId, checkedCurrent);
        }
        if (checkedStation.commodityMassKg(binding.commodityId()) + EPSILON < requestedMassKg) {
            return rejected(Status.INSUFFICIENT_STOCK, bindingId, checkedCurrent);
        }

        List<ConsumableLoad> loads = new ArrayList<>();
        for (ConsumableLoad load : checkedCurrent.interfaceLoads()) {
            if (existing != null && load == existing) {
                continue;
            }
            loads.add(load);
        }
        loads.add(new ConsumableLoad(
                mountId,
                binding.interfaceId(),
                binding.interfaceKind(),
                currentAmount + addedAmount,
                (existing == null ? 0d : existing.massKg()) + requestedMassKg,
                existing == null ? 0L : existing.itemCount()));

        checkedStation.removeCommodity(binding.commodityId(), requestedMassKg);
        ConsumableState updated = new ConsumableState(
                checkedCurrent.cargoMassKg(),
                checkedCurrent.storesMassKg(),
                checkedCurrent.missionPayloadMassKg(),
                checkedCurrent.missionIntegrationVolumeM3(),
                loads);
        return new LoadResult(Status.LOADED, bindingId, requestedMassKg, updated);
    }

    private static LoadResult rejected(Status status, String bindingId, ConsumableState current) {
        return new LoadResult(status, bindingId, 0d, current);
    }
}
