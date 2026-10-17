package com.spacesim.campaign;

import com.spacesim.content.ContentCatalog;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimeMigration;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState.MigrationProvenance;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.FactionInterestResolver.DecisionTrace;
import com.spacesim.world.FactionLivingActorKernel.ReviewResult;
import com.spacesim.world.FactionLivingActorRuntime;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.Stage21HNpcMissionService;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.TerritorialTransitionState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.LongConsumer;

/**
 * Ordinary production composition root for one generated campaign.
 *
 * <p>The coordinator owns no replacement simulation authority. It keeps the accepted Stage-20/20.5
 * {@link GeneratedCampaignSession}, the mutable Stage-21A actor scheduler and the immutable snapshots
 * owned by the accepted Stage-21B-H systems together as one lifecycle boundary. Due diplomatic
 * proposals are reconciled by the existing Stage-21C service at completed campaign ticks. Capture
 * delegates to
 * {@link GeneratedCampaignAuthorityCheckpoint}, so save/load uses the existing final Stage-21I
 * envelope instead of introducing another persistence format.</p>
 */
public final class GeneratedCampaignCoordinator {
    private static final long ACTOR_REVIEW_CADENCE_TICKS = 600L;
    private static final int MAX_ACTOR_REVIEWS_PER_TICK = 8;

    private final GeneratedCampaignSession session;
    private final FactionLivingActorRuntime actors;
    private final List<FactionStrategicIntentState> strategicIntents;
    private DiplomaticLifecycleState diplomacy;
    private final DiplomaticLifecycleService diplomacyLifecycle;
    private final Stage19ConflictState warfare;
    private FleetCommandState commands;
    private StrategicOperationState operations;
    private final TerritorialTransitionState transitions;
    private final SettlementRecoveryState recovery;
    private final Stage21HNpcMissionService npcMissionService;
    private final MigrationProvenance migrationProvenance;
    private final TreeMap<String, DecisionTrace> latestDecisionTraceByFaction = new TreeMap<>();
    private List<FleetCommandState.CommandGroupState> retiredGroupEvents = List.of();

    private GeneratedCampaignCoordinator(
            GeneratedCampaignAuthorityCheckpoint.RestoredAuthorities restored,
            MigrationProvenance migrationProvenance) {
        GeneratedCampaignAuthorityCheckpoint.RestoredAuthorities checked =
                Objects.requireNonNull(restored, "restored");
        this.session = checked.session();
        this.actors = checked.actors();
        this.strategicIntents = checked.strategicIntents();
        this.diplomacy = checked.diplomacy();
        this.warfare = checked.warfare();
        this.diplomacyLifecycle = new DiplomaticLifecycleService(
                session.runtime().world(), new Stage19ConflictRuntime(warfare), diplomacy);
        this.commands = checked.commands();
        this.operations = checked.operations();
        this.transitions = checked.transitions();
        this.recovery = checked.recovery();
        this.npcMissionService = new Stage21HNpcMissionService(checked.npcMissions());
        this.migrationProvenance = Objects.requireNonNull(migrationProvenance, "migrationProvenance");
    }

    /**
     * Creates a new generated campaign and immediately adopts it into the accepted final Stage-21
     * authority chain.
     *
     * <p>The existing Stage-21I migration logic is reused only to construct the deterministic
     * initial A-H sidecars from the accepted generated Stage-20 world. The resulting new campaign is
     * then marked as native Stage-21H adoption rather than as a legacy-save migration.</p>
     *
     * @param rootSeed deterministic generated-world seed
     * @return ordinary production campaign coordinator
     */
    public static GeneratedCampaignCoordinator create(long rootSeed) {
        return create(rootSeed, com.spacesim.persistence.Stage20FreightRuntimeMaterializer.ReserveLoadoutPolicy.BASELINE);
    }

    /**
     * Creates explicit new-game asset manifests; restore retains its accepted exact checkpoint.
     * @param rootSeed deterministic new-campaign seed
     * @param reservePolicy authored initial NPC reserve loadouts
     * @return ordinary coordinator with the selected initial manifest
     */
    public static GeneratedCampaignCoordinator create(long rootSeed,
            com.spacesim.persistence.Stage20FreightRuntimeMaterializer.ReserveLoadoutPolicy reservePolicy) {
        GeneratedCampaignSession initial = GeneratedCampaignSession.create(rootSeed, reservePolicy);
        Stage21IGeneratedWorldRuntimePersistentState initialized =
                Stage21IGeneratedWorldRuntimeMigration.migrate(initial.captureState());
        Stage21IGeneratedWorldRuntimePersistentState nativeCheckpoint =
                Stage21IGeneratedWorldRuntimePersistentState.compose(initialized.stage21HRuntime());
        return restore(nativeCheckpoint);
    }

    /**
     * Restores the complete accepted authority chain from one final or migrated Stage-21 checkpoint.
     *
     * @param checkpoint validated final Stage-21 checkpoint
     * @return independent restored production campaign coordinator
     */
    public static GeneratedCampaignCoordinator restore(
            Stage21IGeneratedWorldRuntimePersistentState checkpoint) {
        return restore(checkpoint, java.util.Map.of());
    }

    /**
     * @param checkpoint exact saved campaign
     * @param admissions explicit existing-station operators
     * @return restored authority retaining admitted industrial stations
     */
    public static GeneratedCampaignCoordinator restore(Stage21IGeneratedWorldRuntimePersistentState checkpoint, java.util.Map<String, String> admissions) {
        Stage21IGeneratedWorldRuntimePersistentState saved = Objects.requireNonNull(checkpoint, "checkpoint");
        return new GeneratedCampaignCoordinator(
                GeneratedCampaignAuthorityCheckpoint.restore(saved, java.util.Map.copyOf(admissions)),
                saved.migrationProvenance());
    }

    /**
     * Captures one atomic final Stage-21 checkpoint from the current accepted owners.
     *
     * <p>Migration provenance is lineage metadata, so it remains unchanged across later native
     * saves. The embedded authoritative world tick may advance, but the migration/adoption tick must
     * not be rewritten by ordinary save operations.</p>
     *
     * @return complete final Stage-21 checkpoint
     */
    public Stage21IGeneratedWorldRuntimePersistentState captureState() {
        Stage21IGeneratedWorldRuntimePersistentState current = GeneratedCampaignAuthorityCheckpoint.capture(
                session,
                actors,
                strategicIntents,
                diplomacy,
                warfare,
                commands,
                operations,
                transitions,
                recovery,
                npcMissions());
        return new Stage21IGeneratedWorldRuntimePersistentState(
                Stage21IGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21IGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                current.stage21HRuntime(),
                migrationProvenance);
    }

    /** @return ordinary generated-world session owning physical runtime progression */
    public GeneratedCampaignSession session() {
        return session;
    }

    /** @return deterministic generated-world root seed */
    public long rootSeed() {
        return session.rootSeed();
    }

    /** @return installed content catalogue used by ordinary presentation */
    public ContentCatalog content() {
        return session.content();
    }

    /** @return the single accepted mutable generated-world authority */
    public LiveRuntime runtime() {
        return session.runtime();
    }

    /** @return mutable Stage-21A autonomous-faction lifecycle owner */
    public FactionLivingActorRuntime actors() {
        return actors;
    }

    /** @return exact Stage-21B strategic-intent snapshots */
    public List<FactionStrategicIntentState> strategicIntents() {
        return strategicIntents;
    }

    /** @return exact Stage-21C diplomacy lifecycle snapshot */
    public DiplomaticLifecycleState diplomacy() {
        return diplomacy;
    }

    /** @return exact Stage-19 warfare snapshot referenced by diplomacy */
    public Stage19ConflictState warfare() {
        return warfare;
    }

    /** @return exact Stage-21D fleet command snapshot */
    public FleetCommandState commands() {
        return commands;
    }

    /**
     * Forms a metadata-only group through the existing Stage-21D owner; no fleet is created or moved.
     * @param factionId actual legal runtime faction
     * @param name player-facing group name
     * @param members already owned and legally registered ordinary fleets
     * @param home existing home system
     * @param availability actual bounded crew/service observations
     */
    void formCommandGroup(int factionId, String name, List<com.spacesim.world.FleetId> members,
            com.spacesim.world.StarSystemId home,
            java.util.Map<com.spacesim.world.FleetId, com.spacesim.world.FleetOperationalAvailability> availability) {
        var world = runtime().world();
        var forces = com.spacesim.world.FleetForceRegistry.reconstructSelected(world,
                new com.spacesim.world.FleetReadinessEvaluator(
                        com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault()),
                availability, members);
        var formed = new com.spacesim.world.FleetCommandGroupService(world.getTopology()).form(
                commands, forces, factionId, name, members, home, false, false, 2500);
        commands = formed.state();
    }

    /**
     * Retires actual command authority while retaining history and existing physical jumps.
     * @param groupId current group identity
     * @param status terminal status for its unfinished orders
     */
    void retireCommandGroup(long groupId, FleetCommandState.OrderStatus status) {
        commands = commands.retireGroup(groupId, status);
        operations = new com.spacesim.world.StrategicOperationService().failRetiredGroup(
                operations, commands, groupId, runtime().world().getAuthoritativeWorldTick());
    }

    /** @return groups retired by the last actual physics interval, never by restore or projection */
    List<FleetCommandState.CommandGroupState> retiredGroupEvents() { return retiredGroupEvents; }

    /**
     * Accepts personal movement through shared Stage-21D validation with known lawful aboard-fuel routing.
     * @param player actual actor knowledge and ownership
     * @param groupId current own group
     * @param type STAGE or RETURN
     * @param target personally known destination
     * @param availability bounded actual staffing observations
     */
    void submitPersonalGroupMovement(com.spacesim.player.PlayerState player, long groupId,
            FleetCommandState.OrderType type, com.spacesim.world.StarSystemId target,
            java.util.Map<com.spacesim.world.FleetId, com.spacesim.world.FleetOperationalAvailability> availability) {
        var world = runtime().world(); var group = commands.requireGroup(groupId);
        var forces = com.spacesim.world.FleetForceRegistry.reconstructSelected(world,
                new com.spacesim.world.FleetReadinessEvaluator(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault()),
                availability, group.memberFleetIds());
        var origin = forces.find(group.memberFleetIds().get(0)).orElseThrow().systemId();
        if (origin == null) throw new IllegalStateException("Group must be locally staged");
        long tick = world.getAuthoritativeWorldTick();
        com.spacesim.world.FleetStrategicRoutePlanner.TransitAccessPolicy legal = (faction, from, to, at, destination) ->
                player.discoveredSystemIds().contains(from) && player.discoveredSystemIds().contains(to)
                        && lawfulPersonalGroupEntry(faction, to, at);
        var planner = new com.spacesim.world.FleetStrategicRoutePlanner(world.getTopology());
        var route = planner.planConstrained(group.factionId(), origin, target, tick, legal,
                candidate -> group.memberFleetIds().stream().allMatch(id -> {
                    var fuel = world.planFleetRouteFuel(id, candidate.systems());
                    return fuel.supported() && fuel.feasible();
                })).orElseThrow(() -> new IllegalStateException("No known lawful route using aboard propellant"));
        var risk = new com.spacesim.player.PlayerFleetRoutePlanner(world, content(), player);
        var diagnostics = group.memberFleetIds().stream().map(id -> risk.evaluateKnownRoute(id, route.systems()).orElseThrow()).toList();
        int riskShare = diagnostics.stream().mapToInt(d -> d.totalCost() == 0 ? 0
                : (int)Math.ceil(10000d * d.riskCostTicks() / d.totalCost())).max().orElse(0);
        long nominalHop = Math.max(1, diagnostics.stream().mapToLong(com.spacesim.player.PlayerRouteRiskView::travelTicks)
                .max().orElse(0) / Math.max(1, route.hopCount()));
        com.spacesim.world.FleetStrategicRoutePlanner.TransitAccessPolicy selected = (faction, from, to, at, destination) -> {
            int index = route.systems().indexOf(from);
            return index >= 0 && index + 1 < route.systems().size() && route.systems().get(index + 1).equals(to)
                    && legal.canEnter(faction, from, to, at, destination);
        };
        commands = new com.spacesim.world.FleetOrderSubmissionService(planner).submit(commands, forces, groupId,
                type, FleetCommandState.OrderSource.PLAYER, target, tick, selected,
                (faction, system, at) -> new com.spacesim.world.FleetOrderSubmissionService.ServiceCapability(false, false, false, 0, nominalHop),
                (faction, orderType, selectedRoute, at) -> riskShare).state();
    }

    /**
     * Cancels only the future personal movement; existing world jumps continue unchanged.
     * @param groupId personally authorized group
     */
    void cancelPersonalGroupMovement(long groupId) {
        var order = commands.activeOrderFor(groupId).orElseThrow();
        if (order.source() != FleetCommandState.OrderSource.PLAYER) throw new IllegalStateException("Not a personal order");
        if (hasPersonalGroupGuard(groupId)) {
            var operation = operations.activeForCommandGroup(groupId).orElseThrow();
            var ended = new com.spacesim.world.StrategicOperationService().finishLocalDefense(operations, commands,
                    operation.id(), runtime().world().getAuthoritativeWorldTick());
            commands = commands.replaceOrder(order.withStatus(FleetCommandState.OrderStatus.CANCELLED));
            operations = ended;
            return;
        }
        if (operations.activeForCommandGroup(groupId).isPresent())
            throw new IllegalStateException("Existing operation requires its own lifecycle authority");
        commands = commands.replaceOrder(order.withStatus(FleetCommandState.OrderStatus.CANCELLED));
    }

    boolean hasPersonalGroupGuard(long groupId) {
        return operations.activeForCommandGroup(groupId)
                .filter(operation -> operation.status() == com.spacesim.world.StrategicOperationState.OperationStatus.ACTIVE)
                .filter(this::isPersonalGroupGuardOperation).isPresent();
    }

    boolean isPersonalGroupGuardOperation(com.spacesim.world.StrategicOperationState.OperationState operation) {
        return operation.type() == com.spacesim.world.StrategicOperationState.OperationType.DEFENSE
                && operation.rulesOfEngagement() == com.spacesim.world.StrategicOperationState.RulesOfEngagement.SELF_DEFENSE_ONLY
                && operation.stagingSystemId().equals(operation.objectiveSystemId())
                && operation.contact() == null && operation.encounter() == null
                && operation.supplyPolicy().equals(new com.spacesim.world.StrategicOperationState.SupplyPolicy(0, 0, 0))
                && operation.withdrawalPolicy().equals(new com.spacesim.world.StrategicOperationState.WithdrawalPolicy(
                        operation.objectiveSystemId(), 0, true, false))
                && commands.orders().stream().anyMatch(order -> order.id() == operation.sourceOrderId()
                        && order.commandGroupId() == operation.commandGroupId()
                        && order.type() == FleetCommandState.OrderType.GUARD
                        && order.source() == FleetCommandState.OrderSource.PLAYER
                        && (!operation.status().active() || order.status().active()));
    }

    void submitPersonalGroupGuard(long groupId,
            java.util.Map<com.spacesim.world.FleetId, com.spacesim.world.FleetOperationalAvailability> availability) {
        var world = runtime().world(); var group = commands.requireGroup(groupId);
        var forces = com.spacesim.world.FleetForceRegistry.reconstructSelected(world,
                new com.spacesim.world.FleetReadinessEvaluator(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault()),
                availability, group.memberFleetIds());
        var system = forces.find(group.memberFleetIds().get(0)).orElseThrow().systemId();
        if (system == null) throw new IllegalStateException("Local guard requires a physical system");
        long tick = world.getAuthoritativeWorldTick();
        var candidate = new com.spacesim.world.FleetOrderSubmissionService(
                new com.spacesim.world.FleetStrategicRoutePlanner(world.getTopology())).submit(commands, forces, groupId,
                FleetCommandState.OrderType.GUARD, FleetCommandState.OrderSource.PLAYER, system, tick,
                (faction, from, to, at, destination) -> from.equals(system) && to.equals(system),
                (faction, target, at) -> com.spacesim.world.FleetOrderSubmissionService.ServiceCapability.none(),
                (faction, type, route, at) -> 0).state();
        var order = candidate.activeOrderFor(groupId).orElseThrow();
        candidate = candidate.replaceOrder(order.withStatus(FleetCommandState.OrderStatus.ACTIVE));
        var admitted = new com.spacesim.world.StrategicOperationService().beginFromActiveOrder(operations, candidate,
                forces, groupId, tick, com.spacesim.world.StrategicOperationState.RulesOfEngagement.SELF_DEFENSE_ONLY,
                new com.spacesim.world.StrategicOperationState.SupplyPolicy(0, 0, 0),
                new com.spacesim.world.StrategicOperationState.WithdrawalPolicy(system, 0, true, false));
        commands = candidate; operations = admitted;
    }

    com.spacesim.world.StrategicOperationState.OperationState advancePersonalGroupGuard(long groupId,
            java.util.Map<com.spacesim.world.FleetId, com.spacesim.world.FleetOperationalAvailability> availability) {
        var world = runtime().world(); var group = commands.requireGroup(groupId);
        var order = commands.activeOrderFor(groupId).orElseThrow();
        var operation = operations.activeForCommandGroup(groupId).orElseThrow();
        long tick = world.getAuthoritativeWorldTick();
        if (operation.lastTransitionTick() >= tick) return operation;
        try {
            if (order.type() != FleetCommandState.OrderType.GUARD || order.source() != FleetCommandState.OrderSource.PLAYER
                    || operation.type() != com.spacesim.world.StrategicOperationState.OperationType.DEFENSE
                    || !operation.participantFleetIds().equals(group.memberFleetIds()))
                throw new IllegalStateException("Local guard lost its original command authority");
            var forces = com.spacesim.world.FleetForceRegistry.reconstructSelected(world,
                    new com.spacesim.world.FleetReadinessEvaluator(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault()),
                    availability, operation.participantFleetIds());
            for (var id : operation.participantFleetIds()) {
                var force = forces.find(id).orElseThrow(() -> new IllegalStateException("Guard participant missing"));
                if (force.factionId() != operation.factionId() || force.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                        || !operation.objectiveSystemId().equals(force.systemId()) || world.findFleetJump(id).isPresent()
                        || runtime().freight().findFreighter(id).filter(f ->
                        f.phase() != com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE).isPresent())
                    throw new IllegalStateException("Guard participant has another physical owner or location");
                com.spacesim.world.FleetOrderSubmissionService.validateLocalGuardReadiness(force.readiness(), id);
            }
            var reviewed = new com.spacesim.world.StrategicOperationService().reviewSupplyAndReadiness(operations,
                    operation.id(), forces, tick);
            if (reviewed.decision() != com.spacesim.world.StrategicOperationService.SupplyDecision.CONTINUE)
                throw new IllegalStateException("Local guard cannot continue without ordinary withdrawal authority");
            operations = reviewed.state();
        } catch (IllegalStateException | IllegalArgumentException rejected) {
            operations = operations.replace(operation.withLifecycle(
                    com.spacesim.world.StrategicOperationState.OperationStatus.FAILED, tick,
                    operation.unsupportedSinceTick(), null, null));
            commands = commands.replaceOrder(order.withStatus(FleetCommandState.OrderStatus.FAILED));
        }
        return operations.requireOperation(operation.id());
    }

    boolean lawfulPersonalGroupEntry(int factionId, com.spacesim.world.StarSystemId to, long tick) {
        var world = runtime().world(); var requester = world.findFactionStableId(factionId).orElseThrow();
        var controller = world.controllingFaction(to);
        if (controller.isEmpty() || controller.orElseThrow().equals(requester)) return true;
        String owner = controller.orElseThrow();
        if (diplomacy.wars().stream().anyMatch(w -> w.status() == DiplomaticLifecycleState.WarStatus.ACTIVE
                && (w.factionA().equals(owner) && w.factionB().equals(requester)
                || w.factionB().equals(owner) && w.factionA().equals(requester)))) return false;
        for (var directory : world.getFactionDiplomacyStates()) for (var treaty : directory.treaties()) {
            boolean ownerGrants = directory.factionContentId().equals(owner) && treaty.counterpartyFactionContentId().equals(requester);
            boolean counterpartyGrants = directory.factionContentId().equals(requester) && treaty.counterpartyFactionContentId().equals(owner);
            if ((!ownerGrants && !counterpartyGrants) || !treaty.activeAt(tick)) continue;
            for (var clause : treaty.clauses()) if (clause.kind() == com.spacesim.world.DiplomaticTreatyClauseState.Kind.TRANSIT_RIGHT
                    && (clause.direction() == com.spacesim.world.DiplomaticTreatyClauseState.Direction.MUTUAL
                    || ownerGrants && clause.direction() == com.spacesim.world.DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY
                    || counterpartyGrants && clause.direction() == com.spacesim.world.DiplomaticTreatyClauseState.Direction.COUNTERPARTY_TO_OWNER)) return true;
        }
        return false;
    }

    /**
     * Reconciles real arrivals and dispatches the next hop without automatic material supply.
     * @param orderId existing personal movement order
     * @param availability actual bounded crew observations
     * @return current retained order
     */
    FleetCommandState.FleetOrderState advancePersonalGroupMovement(long orderId,
            java.util.Map<com.spacesim.world.FleetId, com.spacesim.world.FleetOperationalAvailability> availability) {
        var world = runtime().world(); var order = commands.requireOrder(orderId);
        var group = commands.requireGroup(order.commandGroupId());
        var forces = com.spacesim.world.FleetForceRegistry.reconstructSelected(world,
                new com.spacesim.world.FleetReadinessEvaluator(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault()),
                availability, group.memberFleetIds());
        var service = new com.spacesim.world.FleetOrderExecutionService(world.getTopology());
        if (group.memberFleetIds().stream().allMatch(id -> world.findFleetJump(id).isEmpty()))
            commands = service.reconcilePhysicalArrival(commands, forces, orderId);
        order = commands.requireOrder(orderId);
        if (!order.status().active()) return order;
        if (order.routeCursor() + 1 >= order.route().size()) {
            var objective = order.targetSystemId();
            if (world.getAuthoritativeWorldTick() > order.submittedTick() && group.memberFleetIds().stream().allMatch(id ->
                    world.findFleetJump(id).isEmpty() && world.findFleet(id).filter(f ->
                        f.locationKind() == com.spacesim.world.FleetLocationKind.IN_SYSTEM && objective.equals(f.systemId())).isPresent()))
                commands = commands.replaceOrder(order.withStatus(FleetCommandState.OrderStatus.COMPLETE));
            return commands.requireOrder(orderId);
        }
        var next = order.route().get(order.routeCursor() + 1);
        if (!lawfulPersonalGroupEntry(group.factionId(), next, world.getAuthoritativeWorldTick())) {
            commands = commands.replaceOrder(order.withStatus(FleetCommandState.OrderStatus.FAILED));
            return commands.requireOrder(orderId);
        }
        var current = order.route().get(order.routeCursor());
        for (var id : group.memberFleetIds()) if (world.findFleetJump(id).isEmpty()
                && world.findFleet(id).filter(f -> current.equals(f.systemId())).isPresent()) {
            var ready = world.previewFittedFleetJump(id).orElseThrow();
            if (!ready.allowed()) return order;
        }
        commands = service.dispatchMovementHopOnboardOnly(world, commands, forces, orderId);
        return commands.requireOrder(orderId);
    }

    private void reconcileCommandMembershipAtTick() {
        var world = runtime().world();
        var events = new java.util.ArrayList<FleetCommandState.CommandGroupState>();
        for (var group : commands.groups()) {
            if (group.retired()) continue;
            boolean lostAuthority = group.memberFleetIds().stream().anyMatch(id -> {
                var placement = world.findFleet(id).orElse(null);
                if (placement == null) return true;
                if (placement.locationKind() == com.spacesim.world.FleetLocationKind.IN_TRANSIT) {
                    var owner = placement.transitState().entityState().faction();
                    return owner == null || owner.factionId() != group.factionId();
                }
                var owner = world.findSession(placement.systemId()).orElseThrow().getEntityRegistry()
                        .require(placement.localEntityId()).getComponent(com.spacesim.components.FactionComponent.class);
                return owner == null || owner.factionId != group.factionId();
            });
            if (lostAuthority) {
                retireCommandGroup(group.id(), FleetCommandState.OrderStatus.FAILED);
                events.add(commands.requireGroup(group.id()));
            }
        }
        retiredGroupEvents = List.copyOf(events);
    }

    /** @return exact Stage-21E strategic operation snapshot */
    public StrategicOperationState operations() {
        return operations;
    }

    /** @return exact Stage-21F territorial transition snapshot */
    public TerritorialTransitionState transitions() {
        return transitions;
    }

    /** @return exact Stage-21G settlement recovery snapshot */
    public SettlementRecoveryState recovery() {
        return recovery;
    }

    /** @return exact Stage-21H NPC/mission/reputation/story snapshot */
    public Stage21HNpcMissionState npcMissions() {
        return npcMissionService.snapshot();
    }

    /** @return existing mission lifecycle owner for adjacent composed player commands */
    Stage21HNpcMissionService npcMissionService() {
        return npcMissionService;
    }

    /** @return final-format migration/adoption lineage metadata */
    public MigrationProvenance migrationProvenance() {
        return migrationProvenance;
    }

    /**
     * Returns the latest decision trace actually produced by the production Stage-21A actor runtime.
     *
     * <p>This is ephemeral read-only diagnostics, not an additional gameplay or persistence owner.
     * A restored coordinator intentionally starts without historical diagnostics and records new
     * traces only when its accepted actor runtime performs later scheduled reviews.</p>
     *
     * @param factionContentId stable autonomous faction identity
     * @return latest completed production review trace for that faction, when one has run
     */
    public Optional<DecisionTrace> latestDecisionTrace(String factionContentId) {
        String factionId = Objects.requireNonNull(factionContentId, "factionContentId").strip();
        if (factionId.isEmpty()) {
            throw new IllegalArgumentException("factionContentId cannot be blank");
        }
        return Optional.ofNullable(latestDecisionTraceByFaction.get(factionId));
    }

    /** @return whether campaign progression is paused */
    public boolean isPaused() {
        return session.isPaused();
    }

    /** @return current simulation speed multiplier */
    public double timeScale() {
        return session.timeScale();
    }

    /** @return active fixed-clock interpolation fraction for read-only presentation */
    public double interpolationAlpha() {
        return session.interpolationAlpha();
    }

    /**
     * Applies one pause value through the ordinary campaign clock authority.
     *
     * @param paused new campaign pause state
     */
    public void setPaused(boolean paused) {
        session.setPaused(paused);
    }

    /**
     * Applies one time scale through the ordinary campaign clock authority.
     *
     * @param scale finite non-negative simulation speed multiplier
     */
    public void setTimeScale(double scale) {
        session.setTimeScale(scale);
    }

    /**
     * Advances the ordinary campaign and reviews due Stage-21A actors at exact authoritative ticks.
     *
     * <p>Freight, physical movement and industry advance first inside the accepted Stage-20 session.
     * A due actor then receives a read-only freight-ledger projection from that exact tick. The
     * projection reuses the persistent Stage-20 order ID as evidence/decision provenance and owns no
     * replacement gameplay state.</p>
     *
     * @param realDeltaSeconds finite non-negative presentation delta
     * @return deterministic ordinary-session advance diagnostics
     */
    public GeneratedCampaignSession.AdvanceReport advanceFrame(float realDeltaSeconds) {
        return advanceFrame(realDeltaSeconds, ignored -> { });
    }

    /**
     * Advances the ordinary campaign and invokes one additional observer after each accepted fixed
     * tick, after the existing Stage-21 actor review path has consumed that same tick.
     *
     * <p>This is an extension seam for later accepted authorities such as M22.8. The observer never
     * owns a clock and cannot advance the Stage-20 runtime; it receives only exact completed tick
     * numbers from the existing {@link GeneratedCampaignSession} fixed-step authority.</p>
     *
     * @param realDeltaSeconds finite non-negative presentation delta
     * @param afterFixedTick additional observer invoked once after each completed authoritative tick
     * @return deterministic ordinary-session advance diagnostics
     */
    GeneratedCampaignSession.AdvanceReport advanceFrame(
            float realDeltaSeconds,
            LongConsumer afterFixedTick) {
        LongConsumer observer = Objects.requireNonNull(afterFixedTick, "afterFixedTick");
        return session.advanceFrame(realDeltaSeconds, tick -> {
            reconcileCommandMembershipAtTick();
            expireDiplomaticProposalsAtTick(tick);
            npcMissionService.expireDueMissions(runtime().world(), 8);
            reviewActorsAtTick(tick);
            observer.accept(tick);
        });
    }

    private void expireDiplomaticProposalsAtTick(long authoritativeTick) {
        if (authoritativeTick != runtime().world().getAuthoritativeWorldTick()) {
            throw new IllegalStateException("Diplomatic lifecycle requires the exact campaign tick");
        }
        if (diplomacyLifecycle.expireDueProposals() > 0) {
            // The existing service also rejects linked ordinary treaty offers. Preserve its exact
            // result, but do not normalize a historical sidecar merely by capturing/restoring it.
            diplomacy = diplomacyLifecycle.snapshot();
        }
    }

    private void reviewActorsAtTick(long authoritativeTick) {
        // These observations depend on freight outcomes, not unrelated event or price histories.
        var batch = actors.reviewDue(
                authoritativeTick,
                MAX_ACTOR_REVIEWS_PER_TICK,
                ACTOR_REVIEW_CADENCE_TICKS,
                factionId -> GeneratedCampaignFactionObservationPublisher.publish(
                        runtime().captureFreightState(),
                        factionId,
                        authoritativeTick));
        for (ReviewResult review : batch.reviews()) {
            latestDecisionTraceByFaction.put(review.updatedState().factionContentId(), review.trace());
            GeneratedCampaignNpcPlacement.review(this, review.updatedState().factionContentId(), authoritativeTick);
        }
    }
}
