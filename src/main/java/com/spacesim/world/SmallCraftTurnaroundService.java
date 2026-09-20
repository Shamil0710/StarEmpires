package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.ShipyardCapability;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import com.spacesim.ship.ShipyardEngineeringService.WorkSettlement;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * M22.8C finite turnaround boundary for recovered individual small craft.
 *
 * <p>The service never creates fuel, ammunition, repair material or work. Consumable loading uses
 * explicit physical transfers against authored Stage-17.5 interfaces. Repair and scheduled
 * maintenance reuse {@link ShipyardEngineeringService} work plans and settlements. A craft remains
 * {@link OccupancyState#SERVICING} until every requested physical transfer and work requirement is
 * fully settled; only then may it become {@link OccupancyState#READY}.</p>
 *
 * <p>The service owns no clock. {@code completedHandlingWorkSeconds} is supplied by the accepted
 * fixed-step/industry work authority, while Stage-18/G later binds delivered material to ordinary
 * inventory and logistics. This C-layer only proves that finite requirements cannot be bypassed.</p>
 */
public final class SmallCraftTurnaroundService {
    private static final double EPSILON = 1e-9d;

    private final SmallCraftRegistry craftRegistry;
    private final SmallCraftHangarRegistry hangars;
    private final ShipEngineeringCatalog engineering;
    private final ShipyardEngineeringService shipyard;

    /**
     * Creates one finite turnaround boundary.
     *
     * @param craftRegistry individual persistent craft authority
     * @param hangars physical hangar occupancy authority
     * @param engineering accepted production engineering catalog
     * @param shipyard accepted Stage-17.5G repair/maintenance authority
     */
    public SmallCraftTurnaroundService(
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars,
            ShipEngineeringCatalog engineering,
            ShipyardEngineeringService shipyard) {
        this.craftRegistry = Objects.requireNonNull(craftRegistry, "craftRegistry");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.shipyard = Objects.requireNonNull(shipyard, "shipyard");
    }

    /**
     * Physical service throughput attached to one bay.
     *
     * @param bayId stable physical bay identity
     * @param baseInspectionWorkSeconds mandatory non-zero inspection/service work
     * @param transferRateByKind authored native interface units transferred per simulation second
     */
    public record ServiceProfile(
            BayId bayId,
            double baseInspectionWorkSeconds,
            Map<InterfaceKind, Double> transferRateByKind) {
        /** Validates one finite physical service profile.
         * @param bayId stable bay identity
         * @param baseInspectionWorkSeconds mandatory inspection work
         * @param transferRateByKind positive transfer rate by supported physical interface kind
         */
        public ServiceProfile {
            Objects.requireNonNull(bayId, "bayId");
            requirePositive(baseInspectionWorkSeconds, "baseInspectionWorkSeconds");
            Objects.requireNonNull(transferRateByKind, "transferRateByKind");
            EnumMap<InterfaceKind, Double> copy = new EnumMap<>(InterfaceKind.class);
            for (Map.Entry<InterfaceKind, Double> entry : transferRateByKind.entrySet()) {
                InterfaceKind kind = Objects.requireNonNull(entry.getKey(), "transfer kind");
                Double rate = Objects.requireNonNull(entry.getValue(), "transfer rate");
                requirePositive(rate, "transfer rate");
                copy.put(kind, rate);
            }
            transferRateByKind = Collections.unmodifiableMap(copy);
        }

        double rateFor(InterfaceKind kind) {
            Double rate = transferRateByKind.get(Objects.requireNonNull(kind, "kind"));
            if (rate == null) {
                throw new IllegalArgumentException(
                        "Service profile has no physical transfer path for " + kind);
            }
            return rate;
        }
    }

    /**
     * One finite physical consumable transfer into an authored craft interface.
     *
     * @param mountId installed module mount
     * @param interfaceId authored module-local interface
     * @param kind physical interface kind
     * @param amount positive authored native interface amount
     * @param massKg positive delivered physical mass
     * @param itemCount delivered count where meaningful; zero is valid for bulk fluid/reaction mass
     */
    public record ConsumableTransfer(
            String mountId,
            String interfaceId,
            InterfaceKind kind,
            double amount,
            double massKg,
            long itemCount) implements Comparable<ConsumableTransfer> {
        /** Validates one finite physical transfer.
         * @param mountId installed module mount
         * @param interfaceId authored module interface
         * @param kind physical interface kind
         * @param amount positive native amount
         * @param massKg positive physical mass
         * @param itemCount non-negative physical item count
         */
        public ConsumableTransfer {
            mountId = requireText(mountId, "mountId");
            interfaceId = requireText(interfaceId, "interfaceId");
            Objects.requireNonNull(kind, "kind");
            requirePositive(amount, "amount");
            requirePositive(massKg, "massKg");
            if (itemCount < 0L) {
                throw new IllegalArgumentException("itemCount cannot be negative");
            }
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(ConsumableTransfer other) {
            ConsumableTransfer checked = Objects.requireNonNull(other, "other");
            int mount = mountId.compareTo(checked.mountId);
            if (mount != 0) {
                return mount;
            }
            int iface = interfaceId.compareTo(checked.interfaceId);
            if (iface != 0) {
                return iface;
            }
            return kind.name().compareTo(checked.kind.name());
        }
    }

    /**
     * Requested finite turnaround work.
     *
     * @param transfers exact physical consumables to load
     * @param repairRequested whether current physical damage must be repaired through shipyard authority
     * @param maintenanceRequested whether due scheduled service must be settled through shipyard authority
     */
    public record TurnaroundRequest(
            List<ConsumableTransfer> transfers,
            boolean repairRequested,
            boolean maintenanceRequested) {
        /** Freezes deterministic transfers.
         * @param transfers exact physical consumables to load
         * @param repairRequested repair intent
         * @param maintenanceRequested scheduled maintenance intent
         */
        public TurnaroundRequest {
            Objects.requireNonNull(transfers, "transfers");
            ArrayList<ConsumableTransfer> copy = new ArrayList<>(transfers);
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("transfers cannot contain null");
            }
            copy.sort(Comparator.naturalOrder());
            for (int index = 1; index < copy.size(); index++) {
                ConsumableTransfer previous = copy.get(index - 1);
                ConsumableTransfer current = copy.get(index);
                if (previous.compareTo(current) == 0) {
                    throw new IllegalArgumentException(
                            "Duplicate transfer target: "
                                    + current.mountId() + "/" + current.interfaceId());
                }
            }
            transfers = List.copyOf(copy);
        }
    }

    /**
     * Immutable stale-safe turnaround plan.
     *
     * @param craftId individual craft identity
     * @param bayId servicing bay
     * @param sourceState exact state from which the plan was produced
     * @param preparedState state after the requested consumable transfers, before repair/maintenance
     * @param transfers exact finite consumable requirements
     * @param requiredHandlingWorkSeconds mandatory inspection plus transfer handling work
     * @param repairPlan optional Stage-17.5 repair plan, null when not requested
     * @param maintenancePlan optional Stage-17.5 maintenance plan, null when not requested
     */
    public record TurnaroundPlan(
            SmallCraftId craftId,
            BayId bayId,
            SmallCraftState sourceState,
            SmallCraftState preparedState,
            List<ConsumableTransfer> transfers,
            double requiredHandlingWorkSeconds,
            WorkPlan repairPlan,
            WorkPlan maintenancePlan) {
        /** Validates one immutable plan.
         * @param craftId craft identity
         * @param bayId servicing bay
         * @param sourceState exact source state
         * @param preparedState post-transfer preview
         * @param transfers exact transfers
         * @param requiredHandlingWorkSeconds mandatory physical handling work
         * @param repairPlan optional repair plan
         * @param maintenancePlan optional maintenance plan
         */
        public TurnaroundPlan {
            Objects.requireNonNull(craftId, "craftId");
            Objects.requireNonNull(bayId, "bayId");
            Objects.requireNonNull(sourceState, "sourceState");
            Objects.requireNonNull(preparedState, "preparedState");
            transfers = List.copyOf(Objects.requireNonNull(transfers, "transfers"));
            requirePositive(requiredHandlingWorkSeconds, "requiredHandlingWorkSeconds");
            if (!sourceState.id().equals(craftId) || !preparedState.id().equals(craftId)) {
                throw new IllegalArgumentException("Turnaround states must preserve craft identity");
            }
        }

        /** @return whether all underlying Stage-17.5 repair/service plans are physically feasible */
        public boolean feasible() {
            return (repairPlan == null || repairPlan.feasibility().feasible())
                    && (maintenancePlan == null || maintenancePlan.feasibility().feasible());
        }
    }

    /**
     * Physical settlement proving that planned turnaround resources/work actually arrived.
     *
     * @param deliveredTransfers exact delivered consumables
     * @param completedHandlingWorkSeconds completed inspection/transfer work
     * @param repairSettlement Stage-17.5 repair settlement or empty when repair was not requested
     * @param maintenanceSettlement Stage-17.5 maintenance settlement or empty when service was not requested
     */
    public record TurnaroundSettlement(
            List<ConsumableTransfer> deliveredTransfers,
            double completedHandlingWorkSeconds,
            WorkSettlement repairSettlement,
            WorkSettlement maintenanceSettlement) {
        /** Validates deterministic settlement inputs.
         * @param deliveredTransfers exact delivered consumables
         * @param completedHandlingWorkSeconds completed handling work
         * @param repairSettlement repair input/work settlement
         * @param maintenanceSettlement maintenance work settlement
         */
        public TurnaroundSettlement {
            Objects.requireNonNull(deliveredTransfers, "deliveredTransfers");
            ArrayList<ConsumableTransfer> copy = new ArrayList<>(deliveredTransfers);
            copy.sort(Comparator.naturalOrder());
            deliveredTransfers = List.copyOf(copy);
            requireNonNegative(completedHandlingWorkSeconds, "completedHandlingWorkSeconds");
            Objects.requireNonNull(repairSettlement, "repairSettlement");
            Objects.requireNonNull(maintenanceSettlement, "maintenanceSettlement");
        }
    }

    /**
     * Plans finite turnaround for one already recovered SERVICING craft.
     *
     * @param craftId individual craft
     * @param bay current physical bay projection
     * @param profile physical service throughput for the same bay
     * @param request requested finite work
     * @param yard current physical repair/maintenance capability
     * @return immutable stale-safe turnaround plan
     */
    public TurnaroundPlan plan(
            SmallCraftId craftId,
            BayDefinition bay,
            ServiceProfile profile,
            TurnaroundRequest request,
            ShipyardCapability yard) {
        SmallCraftId checkedCraft = Objects.requireNonNull(craftId, "craftId");
        BayDefinition checkedBay = Objects.requireNonNull(bay, "bay");
        ServiceProfile checkedProfile = Objects.requireNonNull(profile, "profile");
        TurnaroundRequest checkedRequest = Objects.requireNonNull(request, "request");
        ShipyardCapability checkedYard = Objects.requireNonNull(yard, "yard");
        if (!checkedProfile.bayId().equals(checkedBay.id())) {
            throw new IllegalArgumentException("Service profile does not match physical bay");
        }
        var assignment = requireServicingAssignment(checkedCraft, checkedBay);
        SmallCraftState source = craftRegistry.find(checkedCraft).orElseThrow(
                () -> new IllegalArgumentException("Unknown small craft: " + checkedCraft));

        ConsumableState preparedConsumables =
                applyTransfers(source.fit(), source.runtimeState().consumables(), checkedRequest.transfers());
        RuntimeState preparedRuntime = withConsumables(source.runtimeState(), preparedConsumables);
        SmallCraftState prepared = new SmallCraftState(
                source.id(),
                source.stableFactionId(),
                source.designId(),
                source.fit(),
                preparedRuntime,
                source.instanceState());
        craftRegistry.candidatePhysicalFootprint(prepared);

        double handlingWork = checkedProfile.baseInspectionWorkSeconds();
        for (ConsumableTransfer transfer : checkedRequest.transfers()) {
            handlingWork += transfer.amount() / checkedProfile.rateFor(transfer.kind());
        }

        EntityId planningAsset = planningEntityId(checkedCraft);
        WorkPlan repairPlan = checkedRequest.repairRequested()
                ? shipyard.planRepair(
                        planningAsset,
                        source.fit(),
                        preparedConsumables,
                        source.instanceState().damage(),
                        checkedYard)
                : null;
        WorkPlan maintenancePlan = checkedRequest.maintenanceRequested()
                ? shipyard.planMaintenance(
                        planningAsset,
                        source.fit(),
                        preparedConsumables,
                        source.instanceState().maintenance(),
                        checkedYard)
                : null;

        if (assignment.hostKind() != checkedBay.hostKind()) {
            throw new IllegalArgumentException("Servicing bay host family changed during planning");
        }
        return new TurnaroundPlan(
                checkedCraft,
                checkedBay.id(),
                source,
                prepared,
                checkedRequest.transfers(),
                handlingWork,
                repairPlan,
                maintenancePlan);
    }

    /**
     * Completes fully settled turnaround and makes the same physical craft READY.
     *
     * @param plan stale-safe finite plan
     * @param settlement delivered physical resources/work
     * @param currentBay current physical bay projection after all mass/resource changes
     * @return updated individual craft state
     */
    public SmallCraftState complete(
            TurnaroundPlan plan,
            TurnaroundSettlement settlement,
            BayDefinition currentBay) {
        TurnaroundPlan checkedPlan = Objects.requireNonNull(plan, "plan");
        TurnaroundSettlement checkedSettlement = Objects.requireNonNull(settlement, "settlement");
        BayDefinition checkedBay = Objects.requireNonNull(currentBay, "currentBay");
        if (!checkedPlan.bayId().equals(checkedBay.id())) {
            throw new IllegalArgumentException("Turnaround completion bay identity mismatch");
        }
        var assignment = requireServicingAssignment(checkedPlan.craftId(), checkedBay);
        SmallCraftState current = craftRegistry.find(checkedPlan.craftId()).orElseThrow(
                () -> new IllegalArgumentException("Unknown small craft: " + checkedPlan.craftId()));
        if (!current.equals(checkedPlan.sourceState())) {
            throw new IllegalStateException("Turnaround plan is stale; physical craft state changed");
        }
        if (!checkedSettlement.deliveredTransfers().equals(checkedPlan.transfers())) {
            throw new IllegalStateException("Turnaround consumables are not fully delivered");
        }
        if (checkedSettlement.completedHandlingWorkSeconds() + EPSILON
                < checkedPlan.requiredHandlingWorkSeconds()) {
            throw new IllegalStateException("Turnaround handling work is incomplete");
        }
        if (!checkedPlan.feasible()) {
            throw new IllegalStateException("Turnaround contains infeasible repair/service work");
        }

        ShipInstanceRuntimeState instance = checkedPlan.preparedState().instanceState();
        if (checkedPlan.repairPlan() != null) {
            var repaired = shipyard.completeRepair(
                    checkedPlan.repairPlan(), checkedSettlement.repairSettlement());
            if (!repaired.assetId().equals(planningEntityId(checkedPlan.craftId()))) {
                throw new IllegalStateException("Repair planning identity changed");
            }
            instance = new ShipInstanceRuntimeState(
                    repaired.damage(),
                    instance.shieldStatesByMount(),
                    instance.maintenance(),
                    instance.weaponLoadout(),
                    instance.weaponMountRuntime());
        }
        if (checkedPlan.maintenancePlan() != null) {
            var serviced = shipyard.completeMaintenance(
                    checkedPlan.maintenancePlan(),
                    checkedSettlement.maintenanceSettlement(),
                    instance.maintenance());
            if (!serviced.assetId().equals(planningEntityId(checkedPlan.craftId()))) {
                throw new IllegalStateException("Maintenance planning identity changed");
            }
            instance = new ShipInstanceRuntimeState(
                    instance.damage(),
                    instance.shieldStatesByMount(),
                    serviced.maintenance(),
                    instance.weaponLoadout(),
                    instance.weaponMountRuntime());
        }

        SmallCraftState completed = new SmallCraftState(
                checkedPlan.craftId(),
                checkedPlan.preparedState().stableFactionId(),
                checkedPlan.preparedState().designId(),
                checkedPlan.preparedState().fit(),
                checkedPlan.preparedState().runtimeState(),
                instance);
        var footprint = craftRegistry.candidatePhysicalFootprint(completed);
        if (assignment.hostKind() != checkedBay.hostKind()
                || !SmallCraftHangarCapacity.canAccept(
                        checkedBay,
                        hangars.usageExcluding(checkedBay.id(), checkedPlan.craftId()),
                        footprint)) {
            throw new IllegalStateException(
                    "Serviced craft no longer fits current physical bay capacity");
        }

        craftRegistry.replacePhysicalState(completed);
        hangars.transition(checkedPlan.craftId(), OccupancyState.READY);
        return completed;
    }

    private SmallCraftHangarRegistry.Assignment requireServicingAssignment(
            SmallCraftId craftId,
            BayDefinition bay) {
        var assignment = hangars.find(Objects.requireNonNull(craftId, "craftId")).orElseThrow(
                () -> new IllegalArgumentException("Turnaround craft is not embarked: " + craftId));
        if (!assignment.bayId().equals(Objects.requireNonNull(bay, "bay").id())
                || assignment.state() != OccupancyState.SERVICING) {
            throw new IllegalArgumentException(
                    "Turnaround requires SERVICING craft in requested bay: " + craftId);
        }
        return assignment;
    }

    private ConsumableState applyTransfers(
            com.spacesim.ship.ShipEngineeringState.InstalledFit fit,
            ConsumableState current,
            Collection<ConsumableTransfer> transfers) {
        Objects.requireNonNull(fit, "fit");
        ConsumableState checkedCurrent = Objects.requireNonNull(current, "current");
        Objects.requireNonNull(transfers, "transfers");

        TreeMap<InterfaceKey, ConsumableLoad> loads = new TreeMap<>();
        for (ConsumableLoad load : checkedCurrent.interfaceLoads()) {
            InterfaceKey key = new InterfaceKey(load.mountId(), load.interfaceId(), load.kind());
            if (loads.putIfAbsent(key, load) != null) {
                throw new IllegalArgumentException(
                        "Ambiguous duplicate current consumable interface: " + key);
            }
        }

        for (ConsumableTransfer transfer : transfers) {
            ConsumableTransfer checkedTransfer = Objects.requireNonNull(transfer, "transfer");
            InterfaceDefinition authored = requireInterface(fit, checkedTransfer);
            InterfaceKey key = new InterfaceKey(
                    checkedTransfer.mountId(),
                    checkedTransfer.interfaceId(),
                    checkedTransfer.kind());
            ConsumableLoad existing = loads.get(key);
            double currentAmount = existing == null ? 0d : existing.amount();
            double nextAmount = currentAmount + checkedTransfer.amount();
            if (nextAmount > authored.capacity() + EPSILON) {
                throw new IllegalArgumentException(
                        "Consumable transfer exceeds authored interface capacity: " + key);
            }
            double currentMass = existing == null ? 0d : existing.massKg();
            long currentCount = existing == null ? 0L : existing.itemCount();
            loads.put(key, new ConsumableLoad(
                    key.mountId(),
                    key.interfaceId(),
                    key.kind(),
                    nextAmount,
                    currentMass + checkedTransfer.massKg(),
                    Math.addExact(currentCount, checkedTransfer.itemCount())));
        }

        return new ConsumableState(
                checkedCurrent.cargoMassKg(),
                checkedCurrent.storesMassKg(),
                checkedCurrent.missionPayloadMassKg(),
                checkedCurrent.missionIntegrationVolumeM3(),
                List.copyOf(loads.values()));
    }

    private InterfaceDefinition requireInterface(
            com.spacesim.ship.ShipEngineeringState.InstalledFit fit,
            ConsumableTransfer transfer) {
        var installed = fit.installedModules().stream()
                .filter(value -> value.mountId().equals(transfer.mountId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transfer references uninstalled mount: " + transfer.mountId()));
        var module = engineering.findModule(installed.moduleId());
        if (module == null) {
            throw new IllegalStateException(
                    "Installed module missing from engineering catalog: " + installed.moduleId());
        }
        return module.interfaces().stream()
                .filter(value -> value.id().equals(transfer.interfaceId())
                        && value.kind() == transfer.kind())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transfer references missing authored interface: "
                                + transfer.mountId() + "/" + transfer.interfaceId()));
    }

    private static RuntimeState withConsumables(RuntimeState source, ConsumableState consumables) {
        RuntimeState checked = Objects.requireNonNull(source, "source");
        return new RuntimeState(
                Objects.requireNonNull(consumables, "consumables"),
                checked.sharedBusEnergyJ(),
                checked.shipHeatStoredJ(),
                checked.localHeatJByMount(),
                checked.thrustLimitNByMount(),
                checked.coolantBusCapacityW(),
                checked.ftlCooldownSecondsByMount());
    }

    private static EntityId planningEntityId(SmallCraftId craftId) {
        /*
         * ShipyardEngineeringService uses EntityId only as an identity-preserving work-order token.
         * This value never enters the world entity allocator or persistence; the authoritative asset
         * identity remains SmallCraftId throughout M22.8.
         */
        return new EntityId(Objects.requireNonNull(craftId, "craftId").value());
    }

    private record InterfaceKey(
            String mountId,
            String interfaceId,
            InterfaceKind kind) implements Comparable<InterfaceKey> {
        private InterfaceKey {
            mountId = requireText(mountId, "mountId");
            interfaceId = requireText(interfaceId, "interfaceId");
            Objects.requireNonNull(kind, "kind");
        }

        @Override
        public int compareTo(InterfaceKey other) {
            InterfaceKey checked = Objects.requireNonNull(other, "other");
            int mount = mountId.compareTo(checked.mountId);
            if (mount != 0) {
                return mount;
            }
            int iface = interfaceId.compareTo(checked.interfaceId);
            if (iface != 0) {
                return iface;
            }
            return kind.name().compareTo(checked.kind.name());
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
