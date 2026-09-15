package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.Stage18ShipConsumableCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.economy.Stage18ShipConsumableService;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage19WarfareSupplyService;
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
import com.spacesim.ship.WeaponDefinition.Launcher;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;
import com.spacesim.ship.WeaponMountRuntime;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetOrderExecutionService.ServiceOperation;
import com.spacesim.world.SettlementRecoveryState.ReplacementDemand;
import com.spacesim.world.SettlementRecoveryState.ReplacementStatus;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;

import java.util.ArrayList;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-21G adapter that executes recovery only through existing Stage-18/19/17.5 physical authority.
 *
 * <p>Reaction mass is loaded through Stage-18I commodity stock and countable ammunition through the
 * existing Stage-19F manufactured-ordnance bridge. Both results are committed to the same ordinary
 * {@link EngineeringComponent}. Repair preserves existing ordinary entity identity, consumes real
 * yard material/work and changes only settled damage. Replacement first settles an ordinary Stage-18
 * build, then materializes the completed fit as a normal system-local ECS fleet with a fresh
 * {@link FleetId}. Stage 21G stores only provenance linking a loss demand to that ordinary asset.</p>
 */
public final class Stage21GPhysicalRecoveryService {
    private final Stage18ShipConsumableCatalog consumableCatalog;
    private final Stage18ShipConsumableService consumables;
    private final Stage19WarfareSupplyService warfareSupply;
    private final ShipyardEngineeringService engineering;
    private final Stage18ShipyardRuntime shipyards;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final ShipEngineeringRuntime engineeringRuntime;
    private final ShipShieldEngineeringAdapter shieldAdapter = new ShipShieldEngineeringAdapter();
    private final ShieldFieldRuntime shieldRuntime = new ShieldFieldRuntime();

    /**
     * Creates the Stage-21G physical recovery adapter over already-authoritative services.
     *
     * @param consumableCatalog existing Stage-18 ship-consumable binding catalog
     * @param consumables existing Stage-18 finite consumable-loading authority
     * @param warfareSupply existing Stage-19 finite warfare-supply authority
     * @param engineering existing shipyard engineering planning/completion authority
     * @param shipyards existing Stage-18 shipyard material/work settlement authority
     * @param engineeringCatalog existing Stage-17.5 ship engineering content catalog
     */
    public Stage21GPhysicalRecoveryService(
            Stage18ShipConsumableCatalog consumableCatalog,
            Stage18ShipConsumableService consumables,
            Stage19WarfareSupplyService warfareSupply,
            ShipyardEngineeringService engineering,
            Stage18ShipyardRuntime shipyards,
            ShipEngineeringCatalog engineeringCatalog) {
        this.consumableCatalog = Objects.requireNonNull(consumableCatalog, "consumableCatalog");
        this.consumables = Objects.requireNonNull(consumables, "consumables");
        this.warfareSupply = Objects.requireNonNull(warfareSupply, "warfareSupply");
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.shipyards = Objects.requireNonNull(shipyards, "shipyards");
        this.engineeringCatalog = Objects.requireNonNull(engineeringCatalog, "engineeringCatalog");
        this.engineeringRuntime = new ShipEngineeringRuntime(engineeringCatalog);
    }

    /**
     * Executes one existing REFUEL service request from canonical Stage-18 commodity stock.
     *
     * @param operation existing service operation, required to be REFUEL
     * @param bindingId canonical Stage-18 consumable binding identity
     * @param mountId fitted mount receiving reaction mass
     * @param requestedMassKg requested positive reaction-mass load
     * @param fit current installed ship fit
     * @param current current ship consumable state
     * @param station ordinary station storage that supplies the commodity
     * @return existing Stage-18 load result without a parallel inventory mutation
     */
    public Stage18ShipConsumableService.LoadResult refuel(
            ServiceOperation operation,
            String bindingId,
            String mountId,
            double requestedMassKg,
            InstalledFit fit,
            ConsumableState current,
            Stage18StationStorage station) {
        requireServiceType(operation, OrderType.REFUEL);
        Stage18ShipConsumableCatalog.ShipConsumableBinding binding = consumableCatalog.findBinding(bindingId);
        if (binding != null && binding.interfaceKind() != InterfaceKind.REACTION_MASS) {
            throw new IllegalArgumentException("REFUEL cannot consume a " + binding.interfaceKind() + " binding");
        }
        return consumables.load(bindingId, mountId, requestedMassKg, fit, current, station);
    }

    /**
     * Executes and commits one REFUEL request to the same ordinary physical ship component.
     *
     * @param operation existing service operation, required to be REFUEL
     * @param bindingId canonical Stage-18 consumable binding identity
     * @param mountId fitted mount receiving reaction mass
     * @param requestedMassKg requested positive reaction-mass load
     * @param ship ordinary engineering component whose consumables are updated on success
     * @param station ordinary station storage that supplies the commodity
     * @return existing Stage-18 load result and commit outcome
     */
    public Stage18ShipConsumableService.LoadResult refuel(
            ServiceOperation operation,
            String bindingId,
            String mountId,
            double requestedMassKg,
            EngineeringComponent ship,
            Stage18StationStorage station) {
        EngineeringComponent checkedShip = Objects.requireNonNull(ship, "ship");
        RuntimeState before = Objects.requireNonNull(checkedShip.runtimeState, "ship.runtimeState");
        Stage18ShipConsumableService.LoadResult result = refuel(
                operation,
                bindingId,
                mountId,
                requestedMassKg,
                Objects.requireNonNull(checkedShip.fit, "ship.fit"),
                before.consumables(),
                station);
        if (result.committed()) {
            commitConsumables(checkedShip, before, result.consumables());
        }
        return result;
    }

    /**
     * Executes and commits one REARM request through the existing Stage-19F countable-ammunition seam.
     *
     * <p>The physical feed is resolved from the fitted Stage-17.5 module rather than accepted from the
     * caller. A feed already bound to another ammunition identity rejects before any station mutation.
     * On success, finished rounds and their identity are committed together to the ordinary ship.</p>
     *
     * @param operation existing service operation, required to be REARM
     * @param productId canonical manufactured ammunition product identity
     * @param mountId fitted launcher mount receiving ammunition
     * @param requestedRounds requested positive round count
     * @param launcher existing launcher definition governing the feed
     * @param ship ordinary engineering component whose ammunition state is updated on success
     * @param station ordinary station storage supplying manufactured ammunition
     * @return existing Stage-19 ammunition load result and commit outcome
     */
    public Stage19WarfareSupplyService.AmmunitionLoadResult rearm(
            ServiceOperation operation,
            String productId,
            String mountId,
            int requestedRounds,
            Launcher launcher,
            EngineeringComponent ship,
            Stage18StationStorage station) {
        requireServiceType(operation, OrderType.REARM);
        EngineeringComponent checkedShip = Objects.requireNonNull(ship, "ship");
        RuntimeState before = Objects.requireNonNull(checkedShip.runtimeState, "ship.runtimeState");
        ShipInstanceRuntimeState instance = Objects.requireNonNull(checkedShip.instanceState, "ship.instanceState");
        Launcher checkedLauncher = Objects.requireNonNull(launcher, "launcher");
        InterfaceDefinition feed = requireFittedAmmunitionInterface(
                Objects.requireNonNull(checkedShip.fit, "ship.fit"), mountId, checkedLauncher.ammunitionInterfaceId());
        String existingIdentity = instance.weaponLoadout()
                .ammunitionContentId(mountId, feed.id()).orElse(null);
        if (existingIdentity != null && !existingIdentity.equals(productId)) {
            throw new IllegalStateException("Physical ammunition feed already contains another ammunition identity");
        }

        Stage19WarfareSupplyService.AmmunitionLoadResult result = warfareSupply.loadAmmunition(
                productId,
                mountId,
                requestedRounds,
                checkedLauncher,
                feed,
                before.consumables(),
                Objects.requireNonNull(station, "station"));
        if (!result.committed()) {
            return result;
        }
        commitConsumables(checkedShip, before, result.consumables());
        if (existingIdentity == null) {
            ArrayList<FeedBinding> feeds = new ArrayList<>(instance.weaponLoadout().feeds());
            feeds.add(new FeedBinding(mountId, feed.id(), productId));
            checkedShip.setInstanceState(new ShipInstanceRuntimeState(
                    instance.damage(),
                    instance.shieldStatesByMount(),
                    instance.maintenance(),
                    new WeaponLoadoutState(feeds),
                    instance.weaponMountRuntime()));
        }
        return result;
    }

    /**
     * Plans, physically settles and completes one existing-identity repair request.
     *
     * @param operation existing service operation, required to be REPAIR
     * @param assetId ordinary physical asset identity being repaired
     * @param fit current installed ship fit
     * @param currentConsumables current ship consumable state used by engineering planning
     * @param damage current ordinary physical damage snapshot
     * @param station ordinary station storage supplying repair materials
     * @param yard existing Stage-18 yard capability snapshot
     * @param budget finite Stage-18 yard work budget
     * @return repair plan, physical settlement result and completion when settlement succeeds
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
     * Physically repairs and applies the settled damage state to one surviving ordinary ship.
     *
     * @param operation existing service operation, required to be REPAIR
     * @param assetId ordinary physical asset identity being repaired
     * @param ship ordinary engineering component whose damage state is updated on success
     * @param station ordinary station storage supplying repair materials
     * @param yard existing Stage-18 yard capability snapshot
     * @param budget finite Stage-18 yard work budget
     * @return repair plan, settlement and applied completion state
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
        // A missing runtime shield contract must fail before Stage-18 stock/work is committed.
        // Repairs may restore disabled emitters, so preflight the undamaged capability as well.
        repairedShields(checkedShip, DamageState.pristine());
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
     * @param recovery existing Stage-21G settlement/recovery coordinator
     * @param demandId persisted replacement-demand identity
     * @param world existing ordinary world/entity/fleet authority
     * @param identities stable/runtime faction identity authority
     * @param buildSystemId system in which the ordinary replacement entity is materialized
     * @param displayName ordinary display name for the replacement fleet entity
     * @param x local-system berth x-coordinate
     * @param y local-system berth y-coordinate
     * @param targetFit exact replacement fit matching the persisted demand fingerprint
     * @param station ordinary station storage supplying build materials
     * @param yard existing Stage-18 yard capability snapshot
     * @param budget finite Stage-18 yard work budget
     * @param currentTick authoritative current tick
     * @return physical build/commissioning result and updated recovery provenance
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
        if (currentTick < 0L) {
            throw new IllegalArgumentException("Replacement tick must be non-negative");
        }
        if (checkedWorld.findSession(checkedSystem).isEmpty()) {
            throw new IllegalArgumentException("Replacement berth system does not exist: " + checkedSystem);
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
        // Resolve every authored runtime capability while the ship is still detached. In particular,
        // an incompatible shield contract must not consume materials/work or allocate ordinary IDs.
        EngineeringComponent completedEngineering = materializeNewEngineering(targetFit);
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
            asset.add(completedEngineering);
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

    private InterfaceDefinition requireFittedAmmunitionInterface(
            InstalledFit fit,
            String mountId,
            String interfaceId) {
        String checkedMount = requireText(mountId, "mountId");
        String checkedInterface = requireText(interfaceId, "interfaceId");
        InstalledModuleDefinition installed = fit.installedModules().stream()
                .filter(value -> value.mountId().equals(checkedMount))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ammunition mount is not installed: " + checkedMount));
        ModuleDefinition module = engineeringCatalog.findModule(installed.moduleId());
        if (module == null) {
            throw new IllegalStateException("Installed ammunition module is absent from engineering catalog");
        }
        return module.interfaces().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION && value.id().equals(checkedInterface))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Installed module has no requested ammunition interface: " + checkedMount + "/" + checkedInterface));
    }

    private static void commitConsumables(
            EngineeringComponent ship,
            RuntimeState before,
            ConsumableState consumables) {
        ship.setRuntimeState(new RuntimeState(
                consumables,
                before.sharedBusEnergyJ(),
                before.shipHeatStoredJ(),
                before.localHeatJByMount(),
                before.thrustLimitNByMount(),
                before.coolantBusCapacityW(),
                before.ftlCooldownSecondsByMount()));
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
        TreeMap<String, ShieldFieldRuntime.State> shields = repairedShields(
                ship, checkedCompletion.damage().moduleDamage());
        ship.setInstanceState(new ShipInstanceRuntimeState(
                checkedCompletion.damage(),
                shields,
                before.maintenance(),
                before.weaponLoadout(),
                before.weaponMountRuntime()));
    }

    private TreeMap<String, ShieldFieldRuntime.State> repairedShields(
            EngineeringComponent ship, DamageState repairedDamage) {
        ShipInstanceRuntimeState before = Objects.requireNonNull(ship.instanceState, "ship.instanceState");
        RuntimeState operating = Objects.requireNonNull(ship.runtimeState, "ship.runtimeState");
        var derived = engineeringRuntime.derive(
                Objects.requireNonNull(ship.fit, "ship.fit"),
                operating,
                repairedDamage);
        TreeMap<String, ShieldFieldRuntime.State> shields = new TreeMap<>();
        for (ShipShieldEngineeringAdapter.FittedShield fitted : shieldAdapter.derive(derived)) {
            ShieldFieldRuntime.State existing = before.shieldStatesByMount().get(fitted.mountId());
            if (existing == null) {
                existing = new ShieldFieldRuntime.State(0d, 0d, true, 0d, fitted.emitterIntegrity());
            }
            shields.put(fitted.mountId(), shieldRuntime.withEmitterIntegrity(
                    fitted.definition(), existing, fitted.emitterIntegrity()));
        }
        return shields;
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
        RuntimeState initialized = engineeringRuntime.initialize(
                checkedFit, ConsumableState.empty(), damage.moduleDamage());
        RuntimeState operating = new RuntimeState(
                initialized.consumables(),
                0d,
                initialized.shipHeatStoredJ(),
                initialized.localHeatJByMount(),
                initialized.thrustLimitNByMount(),
                initialized.coolantBusCapacityW(),
                initialized.ftlCooldownSecondsByMount());
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

    /**
     * Result of one physically attempted repair.
     *
     * @param plan existing engineering work plan
     * @param settlement existing Stage-18 material/work settlement result
     * @param completion physical repair completion, or null when settlement did not complete
     */
    public record RepairResult(
            WorkPlan plan,
            Stage18ShipyardRuntime.SettlementResult settlement,
            RepairCompletion completion) {
        /**
         * Validates repair settlement/completion consistency.
         *
         * @param plan existing engineering work plan
         * @param settlement existing Stage-18 material/work settlement result
         * @param completion physical repair completion, or null when settlement did not complete
         */
        public RepairResult {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(settlement, "settlement");
            if (settlement.settled() != (completion != null)) {
                throw new IllegalArgumentException("Repair completion must exist exactly when physical settlement succeeds");
            }
        }
    }

    /**
     * Result of a physical replacement attempt and ordinary commissioning.
     *
     * @param plan existing engineering build work plan
     * @param settlement existing Stage-18 material/work settlement result
     * @param completion physical build completion, or null when settlement did not complete
     * @param builtSystemId exact system containing the completed ordinary asset, or null before completion
     * @param commissionedFleetId fresh ordinary FleetId, or null before completion
     * @param recoveryState updated Stage-21G recovery provenance
     */
    public record BuildResult(
            WorkPlan plan,
            Stage18ShipyardRuntime.SettlementResult settlement,
            BuildCompletion completion,
            StarSystemId builtSystemId,
            FleetId commissionedFleetId,
            SettlementRecoveryState recoveryState) {
        /**
         * Validates physical settlement, build and commissioning provenance consistency.
         *
         * @param plan existing engineering build work plan
         * @param settlement existing Stage-18 material/work settlement result
         * @param completion physical build completion, or null when settlement did not complete
         * @param builtSystemId exact system containing the completed ordinary asset, or null before completion
         * @param commissionedFleetId fresh ordinary FleetId, or null before completion
         * @param recoveryState updated Stage-21G recovery provenance
         */
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
