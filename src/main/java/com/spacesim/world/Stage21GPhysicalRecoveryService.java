package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.Stage18ShipConsumableCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.economy.Stage18ShipConsumableService;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShieldFieldRuntime;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipShieldEngineeringAdapter;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.BuildCompletion;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.ShipyardEngineeringService.RepairCompletion;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponMountRuntime;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetOrderExecutionService.ServiceOperation;
import com.spacesim.world.SettlementRecoveryState.ReplacementDemand;
import com.spacesim.world.SettlementRecoveryState.ReplacementStatus;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-21G adapter that executes recovery only through existing Stage-18/17.5 physical authority.
 *
 * <p>Consumables are loaded only after canonical stock consumption and are committed to the same
 * ordinary {@link EngineeringComponent}. Repair preserves the existing ordinary entity identity,
 * requires real yard inputs/work and changes only the settled damage state; it does not refill
 * propellant, ammunition, electrical storage or shield reserve. Replacement first settles an
 * ordinary Stage-18 build, then materializes the completed fit as a normal system-local ECS fleet
 * with a fresh {@link FleetId}. Stage 21G stores only provenance linking the loss demand to that
 * ordinary asset and fleet.</p>
 */
public final class Stage21GPhysicalRecoveryService {
    private final Stage18ShipConsumableCatalog consumableCatalog;
    private final Stage18ShipConsumableService consumables;
    private final ShipyardEngineeringService engineering;
    private final Stage18ShipyardRuntime shipyards;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final ShipEngineeringRuntime engineeringRuntime;
    private final ShipShieldEngineeringAdapter shieldAdapter = new ShipShieldEngineeringAdapter();
    private final ShieldFieldRuntime shieldRuntime = new ShieldFieldRuntime();

    /**
     * Creates the Stage-21G physical recovery adapter over the already-authoritative Stage-18 and
     * Stage-17.5 services.
     *
     * @param consumableCatalog Stage-18I commodity/interface bindings
     * @param consumables Stage-18I stock-bound loading authority
     * @param engineering Stage-17.5G shipyard planning/completion authority
     * @param shipyards Stage-18G finite material/work settlement authority
     * @param engineeringCatalog immutable Stage-17.5 engineering definitions used by materialization
     */
    public Stage21GPhysicalRecoveryService(
            Stage18ShipConsumableCatalog consumableCatalog,
            Stage18ShipConsumableService consumables,
            ShipyardEngineeringService engineering,
            Stage18ShipyardRuntime shipyards,
            ShipEngineeringCatalog engineeringCatalog) {
        this.consumableCatalog = Objects.requireNonNull(consumableCatalog, "consumableCatalog");
        this.consumables = Objects.requireNonNull(consumables, "consumables");
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.shipyards = Objects.requireNonNull(shipyards, "shipyards");
        this.engineeringCatalog = Objects.requireNonNull(engineeringCatalog, "engineeringCatalog");
        this.engineeringRuntime = new ShipEngineeringRuntime(engineeringCatalog);
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

    /**
     * Executes and commits one REFUEL/REARM request to the same ordinary physical ship component.
     *
     * <p>A rejected Stage-18 load leaves both station stock and ship state unchanged. A committed
     * load replaces only the authoritative consumable payload; power, heat, damage, shields,
     * maintenance and weapon-cycle continuity are preserved.</p>
     */
    public Stage18ShipConsumableService.LoadResult serviceConsumable(
            ServiceOperation operation,
            String bindingId,
            String mountId,
            double requestedMassKg,
            EngineeringComponent ship,
            Stage18StationStorage station) {
        EngineeringComponent checkedShip = Objects.requireNonNull(ship, "ship");
        RuntimeState before = Objects.requireNonNull(checkedShip.runtimeState, "ship.runtimeState");
        Stage18ShipConsumableService.LoadResult result = serviceConsumable(
                operation,
                bindingId,
                mountId,
                requestedMassKg,
                Objects.requireNonNull(checkedShip.fit, "ship.fit"),
                before.consumables(),
                station);
        if (result.committed()) {
            checkedShip.setRuntimeState(new RuntimeState(
                    result.consumables(),
                    before.sharedBusEnergyJ(),
                    before.shipHeatStoredJ(),
                    before.localHeatJByMount(),
                    before.thrustLimitNByMount(),
                    before.coolantBusCapacityW(),
                    before.ftlCooldownSecondsByMount()));
        }
        return result;
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
     * Physically repairs and applies the settled damage state to one surviving ordinary ship.
     *
     * <p>The existing EntityId and fitted modules remain unchanged. Repair may restore structural and
     * subsystem integrity only after Stage-18 material/work settlement. It never refills consumables,
     * shared electrical energy or shield reserve and never resets maintenance or launcher cooldowns.</p>
     */
    public RepairResult repair(
            ServiceOperation operation,
            EntityId assetId,
            EngineeringComponent ship,
            Stage18StationStorage station,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            Stage18ShipyardRuntime.YardWorkBudget budget) {
        EngineeringComponent checkedShip = Objects.requireNonNull(ship, "ship");
        ShipInstanceRuntimeState before = Objects.requireNonNull(checkedShip.instanceState, "ship.instanceState");
        RepairResult result = repair(
                operation,
                assetId,
                Objects.requireNonNull(checkedShip.fit, "ship.fit"),
                Objects.requireNonNull(checkedShip.runtimeState, "ship.runtimeState").consumables(),
                before.damage(),
                station,
                yard,
                budget);
        if (result.completion() != null) {
            applyRepair(assetId, checkedShip, result.completion());
        }
        return result;
    }

    /**
     * Physically builds and ordinarily commissions one replacement for a persisted loss demand.
     *
     * <p>There is no caller-supplied EntityId or FleetId. After Stage-18 atomically consumes physical
     * inputs and finite yard work, the ordinary local-system lifecycle allocates the EntityId and
     * registers the fleet through the existing world fleet authority. The new ship starts pristine but
     * with empty propellant/ammunition and empty shield reserve; later servicing must use the ordinary
     * Stage-18 loading/recharge paths. Failure to settle the yard creates neither an entity nor a FleetId.</p>
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

        Entity asset = new Entity()
                .add(new FactionComponent(runtimeFactionId))
                .add(new IdentityComponent(checkedName, IdentityComponent.Kind.FLEET));
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        asset.add(transform);
        EntityId assetId = checkedWorld.createEntity(checkedSystem, asset);
        try {
            BuildCompletion completion = engineering.completeBuild(
                    assetId, plan, settlement.compatibilitySettlement());
            asset.add(materializeNewEngineering(completion.fit()));
            FleetId fleetId = checkedWorld.findFleetByLocal(checkedSystem, assetId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Ordinary replacement fleet registration did not produce a FleetId"));
            checkedRecovery.markYardSettled(demandId, checkedSystem, completion.assetId().value(), currentTick);
            checkedRecovery.markCommissioned(demandId, fleetId, currentTick);
            return new BuildResult(
                    plan, settlement, completion, checkedSystem, fleetId, checkedRecovery.snapshot());
        } catch (RuntimeException exception) {
            if (!checkedWorld.removeEntity(checkedSystem, assetId)) {
                exception.addSuppressed(new IllegalStateException(
                        "Replacement rollback could not remove ordinary built asset: " + assetId));
            }
            throw exception;
        }
    }

    private void applyRepair(
            EntityId assetId,
            EngineeringComponent ship,
            RepairCompletion completion) {
        EntityId checkedId = Objects.requireNonNull(assetId, "assetId");
        RepairCompletion checkedCompletion = Objects.requireNonNull(completion, "completion");
        if (!checkedId.equals(checkedCompletion.assetId())) {
            throw new IllegalArgumentException("repair completion belongs to another physical asset");
        }
        ShipInstanceRuntimeState before = Objects.requireNonNull(ship.instanceState, "ship.instanceState");
        RuntimeState operating = Objects.requireNonNull(ship.runtimeState, "ship.runtimeState");
        var derived = engineeringRuntime.derive(
                Objects.requireNonNull(ship.fit, "ship.fit"),
                operating,
                checkedCompletion.damage().moduleDamage());
        TreeMap<String, ShieldFieldRuntime.State> shields = new TreeMap<>();
        for (ShipShieldEngineeringAdapter.FittedShield fitted : shieldAdapter.derive(derived)) {
            ShieldFieldRuntime.State existing = before.shieldStatesByMount().get(fitted.mountId());
            if (existing == null) {
                existing = new ShieldFieldRuntime.State(0d, 0d, true, 0d, fitted.emitterIntegrity());
            }
            shields.put(fitted.mountId(), shieldRuntime.withEmitterIntegrity(
                    fitted.definition(), existing, fitted.emitterIntegrity()));
        }
        ship.setInstanceState(new ShipInstanceRuntimeState(
                checkedCompletion.damage(),
                shields,
                before.maintenance(),
                before.weaponLoadout(),
                before.weaponMountRuntime()));
    }

    private EngineeringComponent materializeNewEngineering(InstalledFit fit) {
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        HullDefinition hull = engineeringCatalog.findHull(checkedFit.hullId());
        if (hull == null) {
            throw new IllegalStateException("Built replacement hull is absent from engineering catalog: "
                    + checkedFit.hullId());
        }
        TreeMap<String, Double> compartments = new TreeMap<>();
        hull.compartments().forEach(compartment -> compartments.put(compartment.id(), 1d));
        Snapshot damage = new Snapshot(compartments, DamageState.pristine());
        RuntimeState operating = engineeringRuntime.initialize(
                checkedFit, ConsumableState.empty(), damage.moduleDamage());
        var derived = engineeringRuntime.derive(checkedFit, operating, damage.moduleDamage());
        TreeMap<String, ShieldFieldRuntime.State> shields = new TreeMap<>();
        for (ShipShieldEngineeringAdapter.FittedShield fitted : shieldAdapter.derive(derived)) {
            ShieldFieldRuntime.State empty = new ShieldFieldRuntime.State(
                    0d, 0d, true, 0d, fitted.emitterIntegrity());
            shields.put(fitted.mountId(), shieldRuntime.withEmitterIntegrity(
                    fitted.definition(), empty, fitted.emitterIntegrity()));
        }
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                damage,
                shields,
                MaintenanceState.initial(),
                WeaponLoadoutState.empty(),
                WeaponMountRuntime.RuntimeState.empty());
        return new EngineeringComponent(checkedFit, operating, instance);
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

    /** Result of one physically attempted repair. */
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
