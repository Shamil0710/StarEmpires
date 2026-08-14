package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.controllers.TradeController;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stage-11C authoritative runtime for autonomous physical faction growth.
 *
 * <p>The runtime owns the current {@link WorldSimulation}. Persistent strategic transitions are
 * applied by snapshotting and restoring the same world with an updated immutable
 * {@link FactionStrategicState}; physical trade, jump transit and construction remain owned by the
 * existing Stage-9/10 services. The world is rebuilt only when persistent strategic state changes,
 * not on simulation frames.</p>
 */
public final class FactionExpansionRuntime {
    private static final float RESERVED_AI_COOLDOWN_SECONDS = 2f;
    private static final float SITE_SPACING = 120f;

    private WorldSimulation world;
    private final ContentCatalog content;

    /**
     * Creates a growth runtime around one authoritative world.
     *
     * @param world current multi-system runtime
     * @param content canonical content catalog
     */
    public FactionExpansionRuntime(WorldSimulation world, ContentCatalog content) {
        this.world = Objects.requireNonNull(world, "WorldSimulation not set");
        this.content = Objects.requireNonNull(content, "ContentCatalog not set");
    }

    /** @return current authoritative world instance */
    public WorldSimulation world() {
        return world;
    }

    /** @return current persistent world snapshot including Stage-11 strategic state */
    public WorldState snapshot() {
        return world.snapshot();
    }

    /**
     * Advances ordinary world time. Callers should use this method because a strategic transition
     * may replace the internally owned WorldSimulation instance.
     *
     * @param realDeltaSeconds render/frame delta forwarded to WorldSimulation
     * @return world advance report
     */
    public WorldSimulation.AdvanceReport advanceFrame(float realDeltaSeconds) {
        return world.advanceFrame(realDeltaSeconds);
    }

    /**
     * Creates the best currently viable unclaimed growth plan when the faction has no active one.
     *
     * @param factionContentId stable faction content ID
     * @return created plan, existing active plan, or empty when no unclaimed opportunity exists
     */
    public Optional<StrategicGrowthState.Plan> planBestUnclaimed(String factionContentId) {
        String factionId = normalizedId(factionContentId, "Faction ID");
        FactionStrategicState strategy = world.findFactionStrategicState(factionId).orElse(null);
        if (strategy == null) {
            return Optional.empty();
        }
        for (StrategicGrowthState.Plan existing : StrategicGrowthPlanService.plans(strategy)) {
            if (!existing.status().terminal()) {
                return Optional.of(existing);
            }
        }
        ExpansionOpportunity selected = FactionExpansionOpportunityAnalyzer
                .analyze(world, content, factionId)
                .stream()
                .filter(candidate -> candidate.controllingFactionContentId().isEmpty())
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return Optional.empty();
        }
        long tick = authoritativeTick();
        FactionStrategicState updated = StrategicGrowthPlanService.createPlan(
                strategy, selected, content, tick);
        replaceStrategy(updated);
        return StrategicGrowthPlanService.plans(updated).stream()
                .filter(plan -> plan.targetSystemId().equals(selected.targetSystemId()))
                .findFirst();
    }

    /**
     * Performs one deterministic strategic decision for a persistent growth plan.
     *
     * <p>World time is not advanced by this method. Repeated calls are idempotent while a fleet is
     * in an authoritative jump or while construction is building.</p>
     *
     * @param planId stable persistent plan identity
     * @return current plan after this decision
     */
    public StrategicGrowthState.Plan advancePlan(StrategicGrowthState.PlanId planId) {
        StrategicGrowthState.PlanId id = Objects.requireNonNull(planId, "PlanId not set");
        FactionStrategicState strategy = requireStrategy(id.ownerContentId());
        StrategicGrowthState.Plan plan = StrategicGrowthPlanService.findPlan(strategy, id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown growth plan: " + id));
        if (plan.status().terminal()) {
            releaseAssignedFleet(plan);
            return plan;
        }
        try {
            return switch (plan.status()) {
                case PLANNED -> approve(strategy, plan);
                case APPROVED -> beginExecution(strategy, plan);
                case EXECUTING -> execute(strategy, plan);
                case ESTABLISHED, CANCELLED, FAILED -> plan;
            };
        } catch (RuntimeException exception) {
            return failPlan(strategy, plan);
        }
    }

    private StrategicGrowthState.Plan approve(
            FactionStrategicState strategy,
            StrategicGrowthState.Plan plan) {
        if (foreignController(plan.targetSystemId(), strategy.factionContentId())) {
            return failPlan(strategy, plan);
        }
        FactionEconomicState economy = world.findFactionEconomicState(strategy.factionContentId()).orElse(null);
        if (economy == null || economy.treasuryMilliCredits() < plan.approvedBudgetMilliCredits()) {
            return failPlan(strategy, plan);
        }
        FleetId support = selectSupportFleet(strategy.factionContentId(), plan.sourceSystemId()).orElse(null);
        if (support == null) {
            return plan;
        }
        StrategicGrowthState.Plan approved = new StrategicGrowthState.Plan(
                plan.id(),
                plan.sourceSystemId(),
                plan.targetSystemId(),
                plan.reason(),
                plan.anchorArchetypeContentId(),
                null,
                1,
                List.of(support),
                plan.initialStockTargets(),
                plan.approvedBudgetMilliCredits(),
                StrategicGrowthState.Status.APPROVED,
                plan.createdTick(),
                authoritativeTick(),
                -1L);
        reserveFleet(approved);
        replacePlan(strategy, approved);
        return approved;
    }

    private StrategicGrowthState.Plan beginExecution(
            FactionStrategicState strategy,
            StrategicGrowthState.Plan plan) {
        if (!plan.supportRequirementSatisfied()
                || foreignController(plan.targetSystemId(), strategy.factionContentId())) {
            return failPlan(strategy, plan);
        }
        reserveFleet(plan);
        float x = constructionCoordinate(plan.id().sequence(), true);
        float y = constructionCoordinate(plan.id().sequence(), false);
        ConstructionProjectId projectId = world.createConstructionProject(
                strategy.factionContentId(),
                plan.anchorArchetypeContentId(),
                plan.targetSystemId(),
                x,
                y);
        ConstructionProjectState project = world.findConstructionProject(projectId).orElseThrow();
        if (plan.approvedBudgetMilliCredits() < project.minimumFundingMilliCredits()) {
            throw new IllegalStateException("Approved growth budget cannot fund anchor project");
        }
        long funded = world.fundConstructionProject(projectId, project.minimumFundingMilliCredits());
        if (funded != project.minimumFundingMilliCredits()) {
            throw new IllegalStateException("Anchor project funding was not atomic");
        }
        StrategicGrowthState.Plan executing = StrategicGrowthPlanService.transition(
                plan,
                StrategicGrowthState.Status.EXECUTING,
                projectId,
                authoritativeTick());
        replacePlan(strategy, executing);
        return executing;
    }

    private StrategicGrowthState.Plan execute(
            FactionStrategicState strategy,
            StrategicGrowthState.Plan plan) {
        if (foreignController(plan.targetSystemId(), strategy.factionContentId())) {
            return failPlan(strategy, plan);
        }
        if (!plan.supportRequirementSatisfied()) {
            return failPlan(strategy, plan);
        }
        reserveFleet(plan);
        ConstructionProjectState project = world.findConstructionProject(plan.anchorProjectId()).orElse(null);
        if (project == null
                || project.status() == ConstructionProjectStatus.CANCELLED
                || project.status() == ConstructionProjectStatus.FAILED) {
            return failPlan(strategy, plan);
        }
        if (project.status() == ConstructionProjectStatus.COMPLETED) {
            FactionStrategicState claimed = addControlledSystem(strategy, plan.targetSystemId());
            StrategicGrowthState.Plan established = StrategicGrowthPlanService.transition(
                    plan,
                    StrategicGrowthState.Status.ESTABLISHED,
                    plan.anchorProjectId(),
                    authoritativeTick());
            claimed = StrategicGrowthPlanService.replacePlan(claimed, established);
            releaseAssignedFleet(established);
            replaceStrategy(claimed);
            return established;
        }
        driveMaterials(plan, project);
        return plan;
    }

    private void driveMaterials(
            StrategicGrowthState.Plan plan,
            ConstructionProjectState project) {
        FleetId fleetId = plan.assignedSupportFleetIds().get(0);
        if (world.findFleetJump(fleetId).isPresent()) {
            return;
        }
        FleetPlacementState placement = world.findFleet(fleetId).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("Assigned growth fleet is unavailable");
        }
        SimulationSession session = world.findSession(placement.systemId()).orElseThrow();
        Entity fleet = session.getEntityRegistry().find(placement.localEntityId());
        if (fleet == null) {
            throw new IllegalStateException("Assigned growth fleet entity is missing");
        }
        reserveLocalTradeAi(fleet);

        if (placement.systemId().equals(plan.targetSystemId())) {
            deliverAvailableCargo(fleet, placement.localEntityId(), project);
            ConstructionProjectState refreshed = world.findConstructionProject(project.id()).orElseThrow();
            if (!refreshed.materialsFulfilled()) {
                requestToward(fleetId, plan.sourceSystemId(), refreshed, false);
            }
            return;
        }

        if (placement.systemId().equals(plan.sourceSystemId())) {
            ConstructionProjectState refreshed = world.findConstructionProject(project.id()).orElseThrow();
            if (!hasRequiredCargo(fleet, refreshed)) {
                buyNextMaterial(fleet, session, refreshed);
            }
            refreshed = world.findConstructionProject(project.id()).orElseThrow();
            if (hasRequiredCargo(fleet, refreshed)) {
                requestToward(fleetId, plan.targetSystemId(), refreshed, true);
            }
            return;
        }

        StarSystemId destination = hasRequiredCargo(fleet, project)
                ? plan.targetSystemId() : plan.sourceSystemId();
        requestToward(fleetId, destination, project, destination.equals(plan.targetSystemId()));
    }

    private void deliverAvailableCargo(
            Entity fleet,
            EntityId localFleetId,
            ConstructionProjectState project) {
        InventoryComponent inventory = fleet.getComponent(InventoryComponent.class);
        if (inventory == null) {
            throw new IllegalStateException("Growth fleet has no inventory");
        }
        for (ConstructionMaterialState material : project.materials()) {
            ContentCatalog.ItemDefinition item = content.findItem(material.itemContentId());
            if (item == null) {
                throw new IllegalStateException("Unknown construction material: " + material.itemContentId());
            }
            int available = inventory.stock[item.runtimeId()];
            int amount = Math.min(available, material.remainingAmount());
            if (amount > 0) {
                world.deliverConstructionMaterial(project.id(), localFleetId, material.itemContentId(), amount);
            }
        }
    }

    private void buyNextMaterial(
            Entity fleet,
            SimulationSession session,
            ConstructionProjectState project) {
        InventoryComponent fleetInventory = fleet.getComponent(InventoryComponent.class);
        WalletComponent fleetWallet = fleet.getComponent(WalletComponent.class);
        if (fleetInventory == null || fleetWallet == null || fleetInventory.getFreeCapacity() <= 0) {
            return;
        }
        TradeController controller = new TradeController(session.getLedger());
        ReputationComponent reputation = fleet.getComponent(ReputationComponent.class);
        for (ConstructionMaterialState material : project.materials()) {
            if (material.fulfilled()) {
                continue;
            }
            ContentCatalog.ItemDefinition item = content.findItem(material.itemContentId());
            if (item == null) {
                continue;
            }
            Entity supplier = bestSupplier(session, fleet, item.runtimeId(), controller, reputation);
            if (supplier == null) {
                continue;
            }
            InventoryComponent supplierInventory = supplier.getComponent(InventoryComponent.class);
            int maximum = Math.min(
                    material.remainingAmount(),
                    Math.min(fleetInventory.getFreeCapacity(), supplierInventory.stock[item.runtimeId()]));
            int affordable = maximumAffordableAmount(
                    controller.getEffectiveSellPrice(supplier, item.runtimeId(), reputation),
                    maximum,
                    fleetWallet);
            if (affordable > 0
                    && controller.buyFromStation(supplier, fleet, item.runtimeId(), affordable, reputation)) {
                return;
            }
        }
    }

    private Entity bestSupplier(
            SimulationSession session,
            Entity fleet,
            int itemId,
            TradeController controller,
            ReputationComponent reputation) {
        List<Entity> candidates = new ArrayList<>();
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity == fleet) {
                continue;
            }
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            EntityIdComponent identity = entity.getComponent(EntityIdComponent.class);
            if (market == null || inventory == null || wallet == null || identity == null
                    || !market.isTradable(itemId)
                    || inventory.stock[itemId] <= 0
                    || !controller.canTradeWithStation(fleet, entity)) {
                continue;
            }
            float price = controller.getEffectiveSellPrice(entity, itemId, reputation);
            if (Float.isFinite(price) && price > 0f) {
                candidates.add(entity);
            }
        }
        candidates.sort(Comparator
                .comparingDouble((Entity entity) -> controller.getEffectiveSellPrice(entity, itemId, reputation))
                .thenComparingLong(entity -> entity.getComponent(EntityIdComponent.class).id.value()));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static int maximumAffordableAmount(float unitPrice, int maximum, WalletComponent wallet) {
        if (!Float.isFinite(unitPrice) || unitPrice <= 0f || maximum <= 0 || wallet == null) {
            return 0;
        }
        int low = 0;
        int high = maximum;
        while (low < high) {
            int candidate = low + (high - low + 1) / 2;
            long cost;
            try {
                cost = Money.tradeValue(unitPrice, candidate);
            } catch (IllegalArgumentException exception) {
                high = candidate - 1;
                continue;
            }
            if (cost > 0L && wallet.canDebit(cost)) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return low;
    }

    private void requestToward(
            FleetId fleetId,
            StarSystemId destination,
            ConstructionProjectState project,
            boolean targetIsProject) {
        FleetPlacementState placement = world.findFleet(fleetId).orElseThrow();
        GalacticPath path = world.createGalacticPathPlanner()
                .findPath(placement.systemId(), destination)
                .orElseThrow(() -> new IllegalStateException("Growth route became disconnected"));
        if (path.jumpCount() <= 0) {
            return;
        }
        StarSystemId next = path.systems().get(1);
        boolean finalHop = next.equals(destination);
        float arrivalX = targetIsProject && finalHop ? project.x() : 0f;
        float arrivalY = targetIsProject && finalHop ? project.y() : 0f;
        world.requestFleetJump(fleetId, next, arrivalX, arrivalY);
    }

    private boolean hasRequiredCargo(Entity fleet, ConstructionProjectState project) {
        InventoryComponent inventory = fleet.getComponent(InventoryComponent.class);
        if (inventory == null) {
            return false;
        }
        for (ConstructionMaterialState material : project.materials()) {
            ContentCatalog.ItemDefinition item = content.findItem(material.itemContentId());
            if (item != null && inventory.stock[item.runtimeId()] > 0 && material.remainingAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private Optional<FleetId> selectSupportFleet(String factionContentId, StarSystemId sourceSystemId) {
        ContentCatalog.FactionDefinition faction = content.findFaction(factionContentId);
        if (faction == null) {
            return Optional.empty();
        }
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !sourceSystemId.equals(placement.systemId())) {
                continue;
            }
            SimulationSession session = world.findSession(sourceSystemId).orElseThrow();
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            if (entity == null) {
                continue;
            }
            FactionComponent owner = entity.getComponent(FactionComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            TradeAIComponent tradeAi = entity.getComponent(TradeAIComponent.class);
            if (owner != null
                    && owner.factionId == faction.runtimeId()
                    && inventory != null
                    && inventory.capacity > 0
                    && inventory.getTotalStock() == 0
                    && wallet != null
                    && tradeAi != null
                    && tradeAi.state == TradeAIComponent.State.IDLE) {
                return Optional.of(placement.id());
            }
        }
        return Optional.empty();
    }

    private void reserveFleet(StrategicGrowthState.Plan plan) {
        for (FleetId fleetId : plan.assignedSupportFleetIds()) {
            FleetPlacementState placement = world.findFleet(fleetId).orElse(null);
            if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            SimulationSession session = world.findSession(placement.systemId()).orElse(null);
            Entity fleet = session == null ? null : session.getEntityRegistry().find(placement.localEntityId());
            if (fleet != null) {
                reserveLocalTradeAi(fleet);
            }
        }
    }

    private static void reserveLocalTradeAi(Entity fleet) {
        TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
        if (ai == null) {
            return;
        }
        ai.state = TradeAIComponent.State.IDLE;
        ai.resetRoute();
        ai.routeSearchCooldown = Math.max(ai.routeSearchCooldown, RESERVED_AI_COOLDOWN_SECONDS);
    }

    private void releaseAssignedFleet(StrategicGrowthState.Plan plan) {
        for (FleetId fleetId : plan.assignedSupportFleetIds()) {
            FleetPlacementState placement = world.findFleet(fleetId).orElse(null);
            if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            SimulationSession session = world.findSession(placement.systemId()).orElse(null);
            Entity fleet = session == null ? null : session.getEntityRegistry().find(placement.localEntityId());
            if (fleet != null) {
                TradeAIComponent ai = fleet.getComponent(TradeAIComponent.class);
                if (ai != null) {
                    ai.routeSearchCooldown = 0f;
                }
            }
        }
    }

    private StrategicGrowthState.Plan failPlan(
            FactionStrategicState strategy,
            StrategicGrowthState.Plan plan) {
        StrategicGrowthState.Plan failed = StrategicGrowthPlanService.transition(
                plan,
                StrategicGrowthState.Status.FAILED,
                plan.anchorProjectId(),
                Math.max(authoritativeTick(), plan.stateChangedTick()));
        releaseAssignedFleet(failed);
        replacePlan(strategy, failed);
        return failed;
    }

    private void replacePlan(FactionStrategicState strategy, StrategicGrowthState.Plan plan) {
        replaceStrategy(StrategicGrowthPlanService.replacePlan(strategy, plan));
    }

    private void replaceStrategy(FactionStrategicState replacement) {
        WorldState state = world.snapshot();
        List<FactionStrategicState> strategies = new ArrayList<>(state.factionStrategies().size());
        boolean found = false;
        for (FactionStrategicState strategy : state.factionStrategies()) {
            if (strategy.factionContentId().equals(replacement.factionContentId())) {
                strategies.add(replacement);
                found = true;
            } else {
                strategies.add(strategy);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown faction strategy: " + replacement.factionContentId());
        }
        WorldState updated = new WorldState(
                state.schemaVersion(),
                state.topology(),
                state.systems(),
                state.factions(),
                strategies,
                state.nextConstructionProjectIdValue(),
                state.constructionProjects(),
                state.factionEconomicPressures(),
                state.nextFleetIdValue(),
                state.fleets(),
                state.fleetJumps());
        StarSystemId active = world.getActiveSystemId();
        int step = world.getStrategicStepTicks();
        int budget = world.getRemoteUpdateBudgetPerFrame();
        world = WorldSimulation.restore(updated, content, active, step, budget);
    }

    private FactionStrategicState addControlledSystem(
            FactionStrategicState strategy,
            StarSystemId systemId) {
        if (strategy.controls(systemId)) {
            return strategy;
        }
        if (foreignController(systemId, strategy.factionContentId())) {
            throw new IllegalStateException("Cannot claim foreign-controlled system without combat");
        }
        List<StarSystemId> systems = new ArrayList<>(strategy.controlledSystems());
        systems.add(systemId);
        systems.sort(Comparator.naturalOrder());
        return new FactionStrategicState(
                strategy.factionContentId(),
                strategy.minimumMarketAccessRelation(),
                strategy.relations(),
                systems,
                strategy.stationTaxBasisPoints(),
                strategy.foreignTerritoryTariffBasisPoints(),
                strategy.stockPolicies(),
                strategy.productionPolicies(),
                strategy.strategicGoals());
    }

    private boolean foreignController(StarSystemId systemId, String factionContentId) {
        return world.controllingFaction(systemId)
                .filter(controller -> !controller.equals(factionContentId))
                .isPresent();
    }

    private FactionStrategicState requireStrategy(String factionContentId) {
        return world.findFactionStrategicState(factionContentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown faction strategy: " + factionContentId));
    }

    private long authoritativeTick() {
        return world.findSession(world.getActiveSystemId()).orElseThrow().getClock().getTick();
    }

    private static float constructionCoordinate(long sequence, boolean xAxis) {
        long index = Math.max(0L, sequence - 1L);
        long lane = xAxis ? index % 16L : (index / 16L) % 16L;
        float sign = xAxis ? 1f : -1f;
        return sign * (250f + lane * SITE_SPACING);
    }

    private static String normalizedId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return normalized;
    }
}
