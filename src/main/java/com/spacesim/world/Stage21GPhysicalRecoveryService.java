package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
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
import com.spacesim.world.SettlementRecoveryState.ReplacementStatus;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;

import java.util.Objects;

/**
 * Stage-21G adapter that executes recovery only through existing Stage-18/17.5 physical authority.
 *
 * <p>Consumables are loaded only after canonical stock consumption. Repair preserves the existing
 * ordinary entity identity and requires real yard inputs/work. Replacement first settles an ordinary
 * Stage-18 build, then materializes the completed engineering state as a normal system-local ECS
 * entity and finally obtains a fresh FleetId from {@link FleetWorldService}. Stage 21G stores only
 * provenance linking the loss demand to that ordinary asset and fleet.</p>
 */
public final class Stage21GPhysicalRecoveryService {
    private final Stage18ShipConsumableCatalog consumableCatalog;
    private final Stage18ShipConsumableService consumables;
    private final ShipyardEngineeringService engineering;
    private final Stage18ShipyardRuntime shipyards;

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

    /** Executes one existing REFUEL/REARM service request from canonical station stock. */
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

    /** Plans, physically settles and completes one existing-identity repair request. */
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
     * Physically builds and ordinarily commissions one replacement for a persisted loss demand.
     *
     * <p>There is no caller-supplied EntityId or FleetId. After Stage-18 atomically consumes physical
     * inputs and finite yard work, the ordinary local-system lifecycle allocates the EntityId. The
     * completed Stage-17.5 engineering state is attached to that entity; only then is the entity given
     * fleet identity and registered through the existing {@link FleetWorldService}, which allocates a
     * fresh FleetId. Failure to settle the yard creates neither an entity nor a FleetId.</p>
     *
     * @param recovery Stage-21G metadata coordinator
     * @param demandId persisted replacement-demand identity
     * @param world ordinary world/entity/fleet authority
     * @param identities stable/runtime faction identity resolver
     * @param buildSystemId system containing the physical yard/output berth
     * @param displayName ordinary display name for the commissioned replacement
     * @param x output-berth x position
     * @param y output-berth y position
     * @param targetFit requested replacement fit
     * @param station canonical Stage-18F source storage
     * @param yard active Stage-18G yard projection
     * @param budget finite Stage-18G engineering-work budget
     * @param currentTick authoritative tick
     * @return build result; unsuccessful physical settlement never creates ordinary assets
     */
    public BuildResult buildReplacement(
            SettlementRecoveryService recovery,
            long demandId,
            WorldSimulation world,
            FactionIdentityResolver identities,
            StarSystemId buildSystemId,
            String displayName,
            float x,
            float y,
            InstalledFit targetFit,
            Stage18StationStorage station,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            Stage18ShipyardRuntime.YardWorkBudget budget,
            long currentTick) {
        SettlementRecoveryService checkedRecovery = Objects.requireNonNull(recovery, "recovery");
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "world");
        FactionIdentityResolver checkedIdentities = Objects.requireNonNull(identities, "identities");
        StarSystemId checkedSystem = Objects.requireNonNull(buildSystemId, "buildSystemId");
        String checkedName = requireText(displayName, "displayName");
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Replacement berth coordinates must be finite");
        }
        ReplacementDemand demand = checkedRecovery.snapshot().requireReplacementDemand(demandId);
        if (demand.status() != ReplacementStatus.DEMANDED) {
            throw new IllegalStateException("Replacement demand has already left the planning queue");
        }
        if (checkedRecovery.snapshot().requireSettlement(demand.settlementId()).status() == SettlementStatus.PENDING) {
            throw new IllegalStateException("Replacement build requires a finalized Stage-21G recovery plan");
        }
        String fingerprint = SettlementRecoveryService.fitFingerprint(
                Objects.requireNonNull(targetFit, "targetFit"));
        if (!demand.targetFitFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("Replacement fit differs from persisted demand fingerprint");
        }
        int runtimeFactionId = checkedIdentities.runtimeId(demand.factionContentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Replacement owner lacks runtime faction identity: " + demand.factionContentId()));

        WorkPlan plan = engineering.planBuild(targetFit, Objects.requireNonNull(yard, "yard").plannerCapability());
        Stage18ShipyardRuntime.SettlementResult settlement = shipyards.settleBuild(
                plan, Objects.requireNonNull(station, "station"), yard,
                Objects.requireNonNull(budget, "budget"));
        if (!settlement.settled()) {
            return new BuildResult(plan, settlement, null, null, null, checkedRecovery.snapshot());
        }

        Entity asset = new Entity().add(new FactionComponent(runtimeFactionId));
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        asset.add(transform);
        EntityId assetId = checkedWorld.createEntity(checkedSystem, asset);
        try {
            BuildCompletion completion = engineering.completeBuild(
                    assetId, plan, settlement.compatibilitySettlement());
            asset.add(new EngineeringComponent(completion.initialEngineeringState()));
            asset.add(new IdentityComponent(checkedName, IdentityComponent.Kind.FLEET));
            FleetId fleetId = checkedWorld.fleetWorldService().registerFleetAtSystem(checkedSystem, assetId);
            checkedRecovery.markYardSettled(demandId, checkedSystem, completion.assetId().value(), currentTick);
            checkedRecovery.markCommissioned(demandId, fleetId, currentTick);
            return new BuildResult(
                    plan, settlement, completion, checkedSystem, fleetId, checkedRecovery.snapshot());
        } catch (RuntimeException exception) {
            checkedWorld.destroyEntity(checkedSystem, assetId);
            throw exception;
        }
    }

    private static void requireServiceType(ServiceOperation operation, OrderType expected) {
        ServiceOperation checked = Objects.requireNonNull(operation, "operation");
        if (checked.serviceType() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " service request, got " + checked.serviceType());
        }
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }

    public record RepairResult(
            WorkPlan plan,
            Stage18ShipyardRuntime.SettlementResult settlement,
            RepairCompletion completion) {
        public RepairResult {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(settlement, "settlement");
            if (settlement.settled() != (completion != null)) {
                throw new IllegalArgumentException("Repair completion must exist exactly when physical settlement succeeds");
            }
        }
    }

    /** Result of a physical replacement attempt and ordinary commissioning. */
    public record BuildResult(
            WorkPlan plan,
            Stage18ShipyardRuntime.SettlementResult settlement,
            BuildCompletion completion,
            StarSystemId builtSystemId,
            FleetId commissionedFleetId,
            SettlementRecoveryState recoveryState) {
        public BuildResult {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(settlement, "settlement");
            Objects.requireNonNull(recoveryState, "recoveryState");
            boolean completed = completion != null;
            if (settlement.settled() != completed) {
                throw new IllegalArgumentException("Build completion must exist exactly when physical settlement succeeds");
            }
            if (completed != (builtSystemId != null) || completed != (commissionedFleetId != null)) {
                throw new IllegalArgumentException(
                        "Settled replacement must carry exact system and commissioned FleetId provenance");
            }
        }
    }
}
