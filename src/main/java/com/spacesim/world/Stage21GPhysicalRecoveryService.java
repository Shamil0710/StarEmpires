package com.spacesim.world;

import com.spacesim.content.Stage18ShipConsumableCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.economy.Stage18ShipConsumableService;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.BuildCompletion;
import com.spacesim.ship.ShipyardEngineeringService.RepairCompletion;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetOrderExecutionService.ServiceOperation;
import com.spacesim.world.SettlementRecoveryState.ReplacementDemand;

import java.util.Objects;

/**
 * Stage-21G adapter that executes recovery only through existing Stage-18/17.5 physical authority.
 *
 * <p>The adapter never changes an ordinary fleet or ECS entity directly. Consumable service returns
 * a new engineering consumable state only after canonical station stock was consumed. Repair returns
 * the existing identity-preserving Stage-17.5 completion only after Stage-18 yard inputs/work settle.
 * Replacement returns a build completion for later ordinary-world commissioning and records only the
 * Stage-21G demand lifecycle; it cannot allocate or insert a FleetId.</p>
 */
public final class Stage21GPhysicalRecoveryService {
    private final Stage18ShipConsumableCatalog consumableCatalog;
    private final Stage18ShipConsumableService consumables;
    private final ShipyardEngineeringService engineering;
    private final Stage18ShipyardRuntime shipyards;

    /**
     * Creates the physical recovery adapter.
     *
     * @param consumableCatalog authored Stage-18I commodity/interface binding authority
     * @param consumables existing Stage-18I ship-consumable service
     * @param engineering existing Stage-17.5G shipyard planning/completion authority
     * @param shipyards existing Stage-18G physical shipyard settlement authority
     */
    public Stage21GPhysicalRecoveryService(
            Stage18ShipConsumableCatalog consumableCatalog,
            Stage18ShipConsumableService consumables,
            ShipyardEngineeringService engineering,
            Stage18ShipyardRuntime shipyards) {
        this.consumableCatalog = Objects.requireNonNull(consumableCatalog, "consumableCatalog");
        this.consumables = Objects.requireNonNull(consumables, "consumables");
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.shipyards = Objects.requireNonNull(shipyards, "shipyards");
    }

    /**
     * Executes one existing REFUEL/REARM service request from canonical station stock.
     *
     * @param operation Stage-21D service request
     * @param bindingId authored Stage-18I binding
     * @param mountId fitted module mount receiving the load
     * @param requestedMassKg positive physical commodity mass
     * @param fit current installed fit
     * @param current current engineering consumables
     * @param station canonical Stage-18F storage
     * @return Stage-18I result; rejected calls mutate neither ship state nor station stock
     */
    public Stage18ShipConsumableService.LoadResult serviceConsumable(
            ServiceOperation operation,
            String bindingId,
            String mountId,
            double requestedMassKg,
            InstalledFit fit,
            ConsumableState current,
            Stage18StationStorage station) {
        ServiceOperation checked = Objects.requireNonNull(operation, "operation");
        if (checked.serviceType() != OrderType.REFUEL && checked.serviceType() != OrderType.REARM) {
            throw new IllegalArgumentException("Consumable recovery requires REFUEL or REARM service request");
        }
        Stage18ShipConsumableCatalog.ShipConsumableBinding binding = consumableCatalog.findBinding(bindingId);
        if (binding == null) {
            return consumables.load(bindingId, mountId, requestedMassKg, fit, current, station);
        }
        InterfaceKind required = checked.serviceType() == OrderType.REFUEL
                ? InterfaceKind.REACTION_MASS
                : InterfaceKind.AMMUNITION;
        if (binding.interfaceKind() != required) {
            throw new IllegalArgumentException(
                    checked.serviceType() + " cannot consume a " + binding.interfaceKind() + " binding");
        }
        return consumables.load(bindingId, mountId, requestedMassKg, fit, current, station);
    }

    /**
     * Plans, physically settles and completes one existing-identity repair request.
     *
     * @param operation Stage-21D REPAIR request
     * @param assetId existing physical ship entity identity
     * @param fit current installed fit
     * @param currentConsumables current physical loads
     * @param damage authoritative current damage snapshot
     * @param station canonical Stage-18F source storage
     * @param yard active Stage-18G yard projection
     * @param budget finite Stage-18G engineering-work budget
     * @return repair result including physical settlement and identity-preserving completion
     */
    public RepairResult repair(
            ServiceOperation operation,
            EntityId assetId,
            InstalledFit fit,
            ConsumableState currentConsumables,
            Snapshot damage,
            Stage18StationStorage station,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            Stage18ShipyardRuntime.YardWorkBudget budget) {
        requireServiceType(operation, OrderType.REPAIR);
        WorkPlan plan = engineering.planRepair(
                Objects.requireNonNull(assetId, "assetId"),
                Objects.requireNonNull(fit, "fit"),
                Objects.requireNonNull(currentConsumables, "currentConsumables"),
                Objects.requireNonNull(damage, "damage"),
                Objects.requireNonNull(yard, "yard").plannerCapability());
        Stage18ShipyardRuntime.SettlementResult settlement = shipyards.settleRepair(
                plan, damage, Objects.requireNonNull(station, "station"), yard,
                Objects.requireNonNull(budget, "budget"));
        if (!settlement.settled()) {
            return new RepairResult(plan, settlement, null);
        }
        RepairCompletion completion = engineering.completeRepair(plan, settlement.compatibilitySettlement());
        return new RepairResult(plan, settlement, completion);
    }

    /**
     * Plans and physically settles a replacement build for one persisted loss demand.
     *
     * <p>The caller supplies an already allocated new {@link EntityId}; allocation remains outside
     * Stage 21G. A successful result is only a completed physical asset payload. Ordinary fleet
     * commissioning must still create a distinct FleetId before the demand can become commissioned.</p>
     *
     * @param recovery Stage-21G metadata coordinator
     * @param demandId persisted replacement-demand identity
     * @param newAssetId newly allocated ordinary entity identity
     * @param targetFit requested replacement fit
     * @param station canonical Stage-18F source storage
     * @param yard active Stage-18G yard projection
     * @param budget finite Stage-18G engineering-work budget
     * @param currentTick authoritative tick
     * @return build result including physical settlement and build completion when successful
     */
    public BuildResult buildReplacement(
            SettlementRecoveryService recovery,
            long demandId,
            EntityId newAssetId,
            InstalledFit targetFit,
            Stage18StationStorage station,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            Stage18ShipyardRuntime.YardWorkBudget budget,
            long currentTick) {
        SettlementRecoveryService checkedRecovery = Objects.requireNonNull(recovery, "recovery");
        ReplacementDemand demand = checkedRecovery.snapshot().requireReplacementDemand(demandId);
        String fingerprint = SettlementRecoveryService.fitFingerprint(
                Objects.requireNonNull(targetFit, "targetFit"));
        if (!demand.targetFitFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("Replacement fit differs from persisted demand fingerprint");
        }
        WorkPlan plan = engineering.planBuild(targetFit, Objects.requireNonNull(yard, "yard").plannerCapability());
        Stage18ShipyardRuntime.SettlementResult settlement = shipyards.settleBuild(
                plan, Objects.requireNonNull(station, "station"), yard,
                Objects.requireNonNull(budget, "budget"));
        if (!settlement.settled()) {
            return new BuildResult(plan, settlement, null, checkedRecovery.snapshot());
        }
        BuildCompletion completion = engineering.completeBuild(
                Objects.requireNonNull(newAssetId, "newAssetId"),
                plan,
                settlement.compatibilitySettlement());
        checkedRecovery.markYardSettled(demandId, completion.assetId().value(), currentTick);
        return new BuildResult(plan, settlement, completion, checkedRecovery.snapshot());
    }

    private static void requireServiceType(ServiceOperation operation, OrderType expected) {
        ServiceOperation checked = Objects.requireNonNull(operation, "operation");
        if (checked.serviceType() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " service request, got " + checked.serviceType());
        }
    }

    /**
     * Physical repair attempt result.
     *
     * @param plan Stage-17.5G repair plan
     * @param settlement Stage-18G physical settlement result
     * @param completion identity-preserving repair completion, or null when settlement failed
     */
    public record RepairResult(
            WorkPlan plan,
            Stage18ShipyardRuntime.SettlementResult settlement,
            RepairCompletion completion) {
        /** Validates one repair result. */
        public RepairResult {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(settlement, "settlement");
            if (settlement.settled() != (completion != null)) {
                throw new IllegalArgumentException("Repair completion must exist exactly when physical settlement succeeds");
            }
        }
    }

    /**
     * Physical replacement build attempt result.
     *
     * @param plan Stage-17.5G build plan
     * @param settlement Stage-18G physical settlement result
     * @param completion physical build completion, or null when settlement failed
     * @param recoveryState current Stage-21G metadata after the attempt
     */
    public record BuildResult(
            WorkPlan plan,
            Stage18ShipyardRuntime.SettlementResult settlement,
            BuildCompletion completion,
            SettlementRecoveryState recoveryState) {
        /** Validates one replacement build result. */
        public BuildResult {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(settlement, "settlement");
            Objects.requireNonNull(recoveryState, "recoveryState");
            if (settlement.settled() != (completion != null)) {
                throw new IllegalArgumentException("Build completion must exist exactly when physical settlement succeeds");
            }
        }
    }
}
