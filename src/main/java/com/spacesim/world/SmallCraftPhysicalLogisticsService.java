package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18ShipyardRuntime.SettlementResult;
import com.spacesim.economy.Stage18ShipyardRuntime.YardCapabilitySnapshot;
import com.spacesim.economy.Stage18ShipyardRuntime.YardWorkBudget;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponMountRuntime;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftTurnaroundService.ConsumableTransfer;
import com.spacesim.world.SmallCraftTurnaroundService.TurnaroundPlan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * M22.8G physical production, replacement-delivery and supply-demand boundary for small craft.
 *
 * <p>This service does not own a parallel economy. New craft are admitted only after an ordinary
 * {@link Stage18ShipyardRuntime#settleBuild} has atomically consumed Stage-18 hull materials,
 * finished modules and finite yard work. The completed craft then receives a fresh
 * {@link SmallCraftId}, but remains outside every carrier/station bay with empty physical
 * consumables until ordinary logistics deliver and service it.</p>
 *
 * <p>Physical delivery is delegated to an injected {@link PhysicalDeliveryAuthority}; no public
 * method can place a newly produced craft in a bay without a matching delivery receipt. Arrival
 * enters {@link OccupancyState#SERVICING}, so M22.8C/G finite propellant, ammunition, repair and
 * maintenance settlement still gates {@link OccupancyState#READY}.</p>
 */
public final class SmallCraftPhysicalLogisticsService {
    private final SmallCraftRegistry craftRegistry;
    private final SmallCraftHangarRegistry hangars;
    private final ShipEngineeringCatalog engineering;
    private final ShipProtectionCatalog protection;
    private final ShipyardEngineeringService shipyardEngineering;
    private final Stage18ShipyardRuntime shipyardRuntime;
    private final PhysicalDeliveryAuthority deliveryAuthority;
    private final SmallCraftFitAuthority fitAuthority;
    private final ShipEngineeringRuntime engineeringRuntime;

    /**
     * Creates the M22.8G production/logistics boundary over existing physical authorities.
     *
     * @param craftRegistry persistent individual-craft authority
     * @param hangars physical bay occupancy authority
     * @param engineering accepted production engineering catalog
     * @param protection accepted physical damage/protection catalog
     * @param shipyardEngineering common Stage-17.5G planning/completion authority
     * @param shipyardRuntime physical Stage-18G settlement authority
     * @param deliveryAuthority ordinary physical logistics arrival authority
     */
    public SmallCraftPhysicalLogisticsService(
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars,
            ShipEngineeringCatalog engineering,
            ShipProtectionCatalog protection,
            ShipyardEngineeringService shipyardEngineering,
            Stage18ShipyardRuntime shipyardRuntime,
            PhysicalDeliveryAuthority deliveryAuthority) {
        this.craftRegistry = Objects.requireNonNull(craftRegistry, "craftRegistry");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.shipyardEngineering =
                Objects.requireNonNull(shipyardEngineering, "shipyardEngineering");
        this.shipyardRuntime = Objects.requireNonNull(shipyardRuntime, "shipyardRuntime");
        this.deliveryAuthority = Objects.requireNonNull(deliveryAuthority, "deliveryAuthority");
        this.fitAuthority = new SmallCraftFitAuthority(engineering);
        this.engineeringRuntime = new ShipEngineeringRuntime(engineering);
        if (!craftRegistry.engineeringCatalogFingerprint().equals(fitAuthority.catalogFingerprint())) {
            throw new IllegalArgumentException(
                    "small-craft registry and M22.8G production catalog must match exactly");
        }
    }

    /**
     * Plans one physically manufactured small craft through the ordinary shipyard planner.
     *
     * @param stableFactionId owning faction identity
     * @param designId authored fit/design identity retained by the small-craft authority
     * @param sourceStationId physical Stage-18 production station
     * @param targetFit exact installed fit to manufacture
     * @param yard active physical Stage-18 yard projection
     * @return immutable production plan; no identity or material is consumed
     */
    public ProductionPlan planBuild(
            String stableFactionId,
            String designId,
            String sourceStationId,
            InstalledFit targetFit,
            YardCapabilitySnapshot yard) {
        YardCapabilitySnapshot checkedYard = Objects.requireNonNull(yard, "yard");
        if (!checkedYard.active() || checkedYard.plannerCapability() == null) {
            throw new IllegalArgumentException("small-craft production requires an active physical yard");
        }
        InstalledFit checkedFit = Objects.requireNonNull(targetFit, "targetFit");
        WorkPlan workPlan = shipyardEngineering.planBuild(
                checkedFit,
                checkedYard.plannerCapability());
        // Validate the eventual no-grant physical state before any Stage-18 inventory can be consumed.
        pristineUnservicedState(
                new SmallCraftId(Math.max(1L, craftRegistry.nextIdValue())),
                requireText(stableFactionId, "stableFactionId"),
                requireText(designId, "designId"),
                checkedFit);
        return new ProductionPlan(
                stableFactionId,
                designId,
                sourceStationId,
                checkedFit,
                checkedYard,
                workPlan);
    }

    /**
     * Settles one planned build and creates a pending-delivery physical craft only on success.
     *
     * <p>Rejected Stage-18 settlements leave the allocator, registry and logistics sidecar unchanged.
     * Successful settlement consumes real station stock/work before the new identity is reserved.</p>
     *
     * @param logistics current G logistics sidecar
     * @param plan immutable production plan
     * @param storage canonical Stage-18 source-station storage
     * @param budget finite shared yard work budget
     * @return settlement plus optional newly produced craft and updated pending-delivery state
     */
    public BuildResult settleBuild(
            LogisticsState logistics,
            ProductionPlan plan,
            Stage18StationStorage storage,
            YardWorkBudget budget) {
        LogisticsState current = Objects.requireNonNull(logistics, "logistics");
        ProductionPlan checkedPlan = Objects.requireNonNull(plan, "plan");
        Stage18StationStorage checkedStorage = Objects.requireNonNull(storage, "storage");
        YardWorkBudget checkedBudget = Objects.requireNonNull(budget, "budget");
        if (!checkedStorage.stationId().equals(checkedPlan.sourceStationId())) {
            throw new IllegalArgumentException("build storage does not match production station");
        }

        SettlementResult settlement = shipyardRuntime.settleBuild(
                checkedPlan.workPlan(),
                checkedStorage,
                checkedPlan.yard(),
                checkedBudget);
        if (!settlement.settled()) {
            return new BuildResult(current, settlement, null);
        }

        long compatibilityIdentity = craftRegistry.nextIdValue();
        var completion = shipyardEngineering.completeBuild(
                new EntityId(compatibilityIdentity),
                checkedPlan.workPlan(),
                settlement.compatibilitySettlement());
        if (!completion.fit().equals(checkedPlan.targetFit())) {
            throw new IllegalStateException("settled shipyard completion changed the planned fit");
        }

        SmallCraftId id = craftRegistry.reserveIdentityForCompletedProduction();
        SmallCraftState produced = pristineUnservicedState(
                id,
                checkedPlan.stableFactionId(),
                checkedPlan.designId(),
                completion.fit());
        craftRegistry.registerProducedCraft(produced);
        LogisticsState next = current.add(new PendingDelivery(
                id,
                checkedPlan.sourceStationId(),
                checkedPlan.designId()));
        return new BuildResult(next, settlement, produced);
    }

    /**
     * Confirms ordinary physical transport and assigns the arrived craft to a compatible bay.
     *
     * <p>Arrival never makes a craft combat-ready. The craft enters {@code SERVICING}; ordinary
     * M22.8C turnaround plus Stage-18/19 supply must load finite propellant/ammunition and complete
     * any required service before readiness.</p>
     *
     * @param logistics current pending-delivery state
     * @param craftId produced craft being delivered
     * @param bay physical destination bay
     * @param authoritativeTick current authoritative world tick
     * @return updated logistics state and validated physical delivery receipt
     */
    public DeliveryResult confirmDelivery(
            LogisticsState logistics,
            SmallCraftId craftId,
            BayDefinition bay,
            long authoritativeTick) {
        LogisticsState current = Objects.requireNonNull(logistics, "logistics");
        SmallCraftId checkedCraft = Objects.requireNonNull(craftId, "craftId");
        BayDefinition checkedBay = Objects.requireNonNull(bay, "bay");
        if (authoritativeTick < 0L) {
            throw new IllegalArgumentException("authoritativeTick must be non-negative");
        }
        PendingDelivery pending = current.require(checkedCraft);
        if (craftRegistry.find(checkedCraft).isEmpty()) {
            throw new IllegalStateException("pending delivery references missing physical craft");
        }
        if (hangars.find(checkedCraft).isPresent()) {
            throw new IllegalStateException("pending delivery craft is already assigned to a bay");
        }
        if (!hangars.canAccept(checkedCraft, checkedBay)) {
            throw new IllegalStateException(
                    "pending delivery craft does not fit current destination bay capacity");
        }

        DeliveryReceipt receipt = Objects.requireNonNull(
                deliveryAuthority.confirmArrival(pending, checkedBay, authoritativeTick),
                "delivery receipt");
        receipt.requireMatches(pending, checkedBay, authoritativeTick);
        if (!receipt.arrived()) {
            return new DeliveryResult(current, receipt, false);
        }

        hangars.assign(checkedCraft, checkedBay, OccupancyState.SERVICING);
        return new DeliveryResult(current.remove(checkedCraft), receipt, true);
    }

    /**
     * Projects exact observable supply/service demand from an already-authoritative C turnaround plan.
     *
     * <p>The projection adds no resources and performs no settlement. Consumable transfers preserve
     * their exact physical amount/mass/count, while Stage-17.5 repair/maintenance work plans retain
     * their ordinary industrial input/work requirements for Stage-18 settlement.</p>
     *
     * @param plan finite M22.8C turnaround plan
     * @return immutable observable demand projection
     */
    public TurnaroundDemand projectTurnaroundDemand(TurnaroundPlan plan) {
        TurnaroundPlan checked = Objects.requireNonNull(plan, "plan");
        return new TurnaroundDemand(
                checked.craftId(),
                checked.transfers(),
                checked.requiredHandlingWorkSeconds(),
                checked.repairPlan(),
                checked.maintenancePlan());
    }

    private SmallCraftState pristineUnservicedState(
            SmallCraftId id,
            String stableFactionId,
            String designId,
            InstalledFit fit) {
        var hull = engineering.findHull(fit.hullId());
        if (hull == null) {
            throw new IllegalArgumentException("unknown production hull: " + fit.hullId());
        }
        var layout = protection.findHullDamageLayout(hull.id());
        if (layout == null) {
            throw new IllegalArgumentException(
                    "missing physical damage layout for production hull: " + hull.id());
        }
        ShipDamageRuntime.Snapshot damage = ShipDamageRuntime.Snapshot.pristine(hull, layout);
        var runtime = engineeringRuntime.initialize(
                fit,
                ConsumableState.empty(),
                damage.moduleDamage());
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                damage,
                Map.of(),
                MaintenanceState.initial(),
                WeaponLoadoutState.empty(),
                WeaponMountRuntime.RuntimeState.empty());
        SmallCraftState state = new SmallCraftState(
                id,
                stableFactionId,
                designId,
                fit,
                runtime,
                instance);
        fitAuthority.requireValid(state);
        return state;
    }

    /**
     * One immutable planned Stage-18 manufacture.
     *
     * @param stableFactionId owning faction identity
     * @param designId authored design/fit identity retained by the craft
     * @param sourceStationId physical production station
     * @param targetFit exact installed fit
     * @param yard physical yard projection used by the plan
     * @param workPlan ordinary Stage-17.5G build plan
     */
    public record ProductionPlan(
            String stableFactionId,
            String designId,
            String sourceStationId,
            InstalledFit targetFit,
            YardCapabilitySnapshot yard,
            WorkPlan workPlan) {
        /** Validates one immutable production plan.
         * @param stableFactionId owning faction identity
         * @param designId authored design identity
         * @param sourceStationId physical production station
         * @param targetFit target installed fit
         * @param yard physical yard projection
         * @param workPlan common shipyard plan
         */
        public ProductionPlan {
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            designId = requireText(designId, "designId");
            sourceStationId = requireText(sourceStationId, "sourceStationId");
            Objects.requireNonNull(targetFit, "targetFit");
            Objects.requireNonNull(yard, "yard");
            Objects.requireNonNull(workPlan, "workPlan");
            if (!targetFit.equals(workPlan.targetFit())) {
                throw new IllegalArgumentException("production plan target fit mismatch");
            }
        }
    }

    /**
     * One produced craft waiting for ordinary physical transport to a bay.
     *
     * @param craftId fresh persistent craft identity
     * @param sourceStationId physical production origin
     * @param designId authored design identity
     */
    public record PendingDelivery(
            SmallCraftId craftId,
            String sourceStationId,
            String designId) implements Comparable<PendingDelivery> {
        /** Validates one pending physical delivery.
         * @param craftId fresh craft identity
         * @param sourceStationId production origin
         * @param designId authored design identity
         */
        public PendingDelivery {
            Objects.requireNonNull(craftId, "craftId");
            sourceStationId = requireText(sourceStationId, "sourceStationId");
            designId = requireText(designId, "designId");
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(PendingDelivery other) {
            return craftId.compareTo(Objects.requireNonNull(other, "other").craftId());
        }
    }

    /**
     * Persistent-shaped G logistics sidecar; M22.8M later binds it into campaign save envelopes.
     *
     * @param pendingDeliveries produced physical craft not yet assigned after transport
     */
    public record LogisticsState(List<PendingDelivery> pendingDeliveries) {
        /** Validates, sorts and freezes pending physical deliveries.
         * @param pendingDeliveries produced but undelivered craft
         */
        public LogisticsState {
            Objects.requireNonNull(pendingDeliveries, "pendingDeliveries");
            ArrayList<PendingDelivery> copy = new ArrayList<>(pendingDeliveries);
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("pending deliveries cannot contain null");
            }
            copy.sort(Comparator.naturalOrder());
            for (int index = 1; index < copy.size(); index++) {
                if (copy.get(index - 1).craftId().equals(copy.get(index).craftId())) {
                    throw new IllegalArgumentException(
                            "duplicate pending delivery: " + copy.get(index).craftId());
                }
            }
            pendingDeliveries = List.copyOf(copy);
        }

        /** @return empty logistics state */
        public static LogisticsState empty() {
            return new LogisticsState(List.of());
        }

        /**
         * Resolves one pending delivery.
         *
         * @param craftId pending craft identity
         * @return matching pending delivery
         */
        public PendingDelivery require(SmallCraftId craftId) {
            SmallCraftId checked = Objects.requireNonNull(craftId, "craftId");
            return pendingDeliveries.stream()
                    .filter(value -> value.craftId().equals(checked))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "craft is not pending physical delivery: " + checked));
        }

        private LogisticsState add(PendingDelivery pending) {
            PendingDelivery checked = Objects.requireNonNull(pending, "pending");
            if (pendingDeliveries.stream()
                    .anyMatch(value -> value.craftId().equals(checked.craftId()))) {
                throw new IllegalArgumentException(
                        "craft already pending delivery: " + checked.craftId());
            }
            ArrayList<PendingDelivery> copy = new ArrayList<>(pendingDeliveries);
            copy.add(checked);
            return new LogisticsState(copy);
        }

        private LogisticsState remove(SmallCraftId craftId) {
            SmallCraftId checked = Objects.requireNonNull(craftId, "craftId");
            ArrayList<PendingDelivery> copy = new ArrayList<>(pendingDeliveries);
            boolean removed = copy.removeIf(value -> value.craftId().equals(checked));
            if (!removed) {
                throw new IllegalArgumentException(
                        "craft is not pending delivery: " + checked);
            }
            return new LogisticsState(copy);
        }
    }

    /**
     * Stage-18 build result.
     *
     * @param logisticsState updated pending-delivery state
     * @param settlement exact physical Stage-18 build settlement
     * @param producedCraft new physical craft, or null when settlement was rejected
     */
    public record BuildResult(
            LogisticsState logisticsState,
            SettlementResult settlement,
            SmallCraftState producedCraft) {
        /** Validates one build result.
         * @param logisticsState updated logistics state
         * @param settlement Stage-18 build settlement
         * @param producedCraft produced physical craft or null
         */
        public BuildResult {
            Objects.requireNonNull(logisticsState, "logisticsState");
            Objects.requireNonNull(settlement, "settlement");
            if (settlement.settled() != (producedCraft != null)) {
                throw new IllegalArgumentException(
                        "produced craft must exist exactly when build settlement succeeds");
            }
        }

        /** @return optional produced craft */
        public Optional<SmallCraftState> producedCraftOptional() {
            return Optional.ofNullable(producedCraft);
        }
    }

    /**
     * Physical delivery proof supplied by ordinary logistics.
     *
     * @param craftId delivered craft identity
     * @param sourceStationId physical production origin
     * @param destinationHostStableId destination carrier/station host identity
     * @param transportReferenceId ordinary transport/order evidence identity
     * @param authoritativeTick arrival tick
     * @param arrived whether physical arrival actually completed
     */
    public record DeliveryReceipt(
            SmallCraftId craftId,
            String sourceStationId,
            String destinationHostStableId,
            String transportReferenceId,
            long authoritativeTick,
            boolean arrived) {
        /** Validates one physical delivery receipt.
         * @param craftId delivered craft identity
         * @param sourceStationId production origin
         * @param destinationHostStableId destination host identity
         * @param transportReferenceId transport/order evidence
         * @param authoritativeTick arrival tick
         * @param arrived whether arrival completed
         */
        public DeliveryReceipt {
            Objects.requireNonNull(craftId, "craftId");
            sourceStationId = requireText(sourceStationId, "sourceStationId");
            destinationHostStableId =
                    requireText(destinationHostStableId, "destinationHostStableId");
            transportReferenceId = requireText(transportReferenceId, "transportReferenceId");
            if (authoritativeTick < 0L) {
                throw new IllegalArgumentException("authoritativeTick must be non-negative");
            }
        }

        private void requireMatches(
                PendingDelivery pending,
                BayDefinition bay,
                long requestedTick) {
            if (!craftId.equals(pending.craftId())
                    || !sourceStationId.equals(pending.sourceStationId())
                    || !destinationHostStableId.equals(bay.id().hostStableId())
                    || authoritativeTick != requestedTick) {
                throw new IllegalArgumentException(
                        "physical delivery receipt does not match pending craft/destination/tick");
            }
        }
    }

    /**
     * Result of one physical delivery confirmation.
     *
     * @param logisticsState updated pending-delivery state
     * @param receipt ordinary logistics receipt
     * @param assigned whether physical bay assignment occurred
     */
    public record DeliveryResult(
            LogisticsState logisticsState,
            DeliveryReceipt receipt,
            boolean assigned) {
        /** Validates one delivery result.
         * @param logisticsState updated logistics state
         * @param receipt physical delivery receipt
         * @param assigned whether bay assignment occurred
         */
        public DeliveryResult {
            Objects.requireNonNull(logisticsState, "logisticsState");
            Objects.requireNonNull(receipt, "receipt");
            if (assigned != receipt.arrived()) {
                throw new IllegalArgumentException(
                        "bay assignment must agree with physical arrival");
            }
        }
    }

    /**
     * Exact observable carrier-operation supply demand.
     *
     * @param craftId persistent craft identity
     * @param consumableTransfers finite propellant/ammunition/other transfer demand
     * @param handlingWorkSeconds finite inspection/transfer work demand
     * @param repairPlan optional ordinary repair input/work demand
     * @param maintenancePlan optional ordinary maintenance/spares/work demand
     */
    public record TurnaroundDemand(
            SmallCraftId craftId,
            List<ConsumableTransfer> consumableTransfers,
            double handlingWorkSeconds,
            WorkPlan repairPlan,
            WorkPlan maintenancePlan) {
        /** Validates one immutable demand projection.
         * @param craftId persistent craft identity
         * @param consumableTransfers exact finite consumable demand
         * @param handlingWorkSeconds finite handling work
         * @param repairPlan optional repair demand
         * @param maintenancePlan optional maintenance demand
         */
        public TurnaroundDemand {
            Objects.requireNonNull(craftId, "craftId");
            consumableTransfers =
                    List.copyOf(Objects.requireNonNull(consumableTransfers, "consumableTransfers"));
            if (!Double.isFinite(handlingWorkSeconds) || handlingWorkSeconds <= 0d) {
                throw new IllegalArgumentException(
                        "handlingWorkSeconds must be finite and positive");
            }
        }

        /** @return whether ordinary shipyard repair capacity/material is demanded */
        public boolean repairRequired() {
            return repairPlan != null;
        }

        /** @return whether ordinary shipyard maintenance/spares capacity is demanded */
        public boolean maintenanceRequired() {
            return maintenancePlan != null;
        }
    }

    /**
     * External ordinary-logistics authority used to prove that a produced craft physically arrived.
     */
    @FunctionalInterface
    public interface PhysicalDeliveryAuthority {
        /**
         * Confirms or rejects physical arrival for one pending craft.
         *
         * @param pending produced craft waiting at its source station
         * @param destination physical destination bay
         * @param authoritativeTick current authoritative world tick
         * @return immutable transport receipt
         */
        DeliveryReceipt confirmArrival(
                PendingDelivery pending,
                BayDefinition destination,
                long authoritativeTick);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
