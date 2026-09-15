package com.spacesim.campaign;

import com.spacesim.campaign.GeneratedCampaignAuthorityCheckpoint.RestoredAuthorities;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimeMigration;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FleetCommandGroupService;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetOrderSubmissionService;
import com.spacesim.world.FleetOrderSubmissionService.ServiceCapabilityPolicy;
import com.spacesim.world.FleetOrderSubmissionService.StrategicRiskPolicy;
import com.spacesim.world.FleetStrategicRoutePlanner;
import com.spacesim.world.FleetStrategicRoutePlanner.TransitAccessPolicy;
import com.spacesim.world.SettlementRecoveryService;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.Stage21HNpcMissionService;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationService;
import com.spacesim.world.StrategicOperationService.SupplyReview;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.TerritorialTransitionService;
import com.spacesim.world.TerritorialTransitionState;

import java.util.List;
import java.util.Objects;

/**
 * Production orchestration boundary for one complete generated campaign.
 *
 * <p>The coordinator owns no competing gameplay authority. The mutable physical world, economy,
 * freight and clocks remain inside {@link GeneratedCampaignSession}; diplomacy, warfare and RPG
 * missions remain inside their accepted mutable owners. Stage-21D command, Stage-21E operation and
 * Stage-21F territorial-transition metadata are retained only as immutable canonical replacements
 * returned by their accepted services. Stage-21G is retained by its existing mutable
 * {@link SettlementRecoveryService}. This class only composes those owners around one campaign.</p>
 */
public final class GeneratedCampaignCoordinator {
    /** Accepted Stage-21I workload evidence caps expensive actor reviews at seven per batch. */
    static final int LIVING_ACTOR_REVIEW_BUDGET = 7;

    /** Deterministic medium strategic cadence expressed in authoritative simulation ticks. */
    static final long LIVING_ACTOR_REVIEW_CADENCE_TICKS = 1_200L;

    private final RestoredAuthorities authorities;
    private final FleetOrderSubmissionService fleetOrderSubmissionService;
    private final StrategicOperationService strategicOperationService;
    private final TerritorialTransitionService territorialTransitionService;
    private final SettlementRecoveryService settlementRecoveryService;
    private FleetCommandState fleetCommands;
    private StrategicOperationState operations;
    private TerritorialTransitionState transitions;

    private GeneratedCampaignCoordinator(RestoredAuthorities authorities) {
        this.authorities = Objects.requireNonNull(authorities, "authorities");
        this.fleetOrderSubmissionService = new FleetOrderSubmissionService(new FleetStrategicRoutePlanner(
                authorities.session().runtime().world().getTopology()));
        this.strategicOperationService = new StrategicOperationService();
        this.territorialTransitionService = new TerritorialTransitionService();
        this.settlementRecoveryService = new SettlementRecoveryService(authorities.recovery());
        this.fleetCommands = authorities.commands();
        this.operations = authorities.operations();
        this.transitions = authorities.transitions();
    }

    /**
     * Creates a new generated campaign and composes the accepted Stage-21 authority chain around it.
     *
     * @param rootSeed deterministic generated-world seed
     * @return one production campaign coordinator
     */
    public static GeneratedCampaignCoordinator create(long rootSeed) {
        GeneratedCampaignSession session = GeneratedCampaignSession.create(rootSeed);
        Stage21IGeneratedWorldRuntimePersistentState initial =
                Stage21IGeneratedWorldRuntimeMigration.migrate(session.captureState());
        return restore(initial);
    }

    /**
     * Restores one native or already migrated final Stage-21 checkpoint.
     *
     * @param checkpoint complete accepted campaign checkpoint
     * @return restored coordinator
     */
    public static GeneratedCampaignCoordinator restore(
            Stage21IGeneratedWorldRuntimePersistentState checkpoint) {
        return new GeneratedCampaignCoordinator(
                GeneratedCampaignAuthorityCheckpoint.restore(
                        Objects.requireNonNull(checkpoint, "checkpoint")));
    }

    /**
     * Restores a native Stage-21I payload or migrates a supported Stage-20.5/21A-H payload.
     *
     * @param bytes persisted generated-campaign bytes
     * @return restored coordinator
     */
    public static GeneratedCampaignCoordinator decodeOrMigrate(byte[] bytes) {
        return restore(Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(
                Objects.requireNonNull(bytes, "bytes")));
    }

    /** @return the single mutable ordinary generated-world session used by simulation and UI */
    public GeneratedCampaignSession session() {
        return authorities.session();
    }

    /** @return accepted mutable Stage-21C diplomacy owner over this campaign's ordinary world */
    public DiplomaticLifecycleService diplomacy() {
        return authorities.diplomacyService();
    }

    /** @return accepted mutable Stage-19 actor-known conflict owner shared by diplomacy */
    public Stage19ConflictRuntime warfare() {
        return authorities.warfareRuntime();
    }

    /** @return current immutable Stage-21D fleet-command metadata */
    public FleetCommandState fleetCommands() {
        return fleetCommands;
    }

    /**
     * Forms one Stage-21D command group over a read-only reconstruction of ordinary fleets.
     *
     * @param forces read-only physical fleet reconstruction used for formation validation
     * @param factionId numeric ordinary-world faction owner
     * @param name player/AI-visible command-group name
     * @param memberFleetIds physical fleets assigned to the group
     * @param homeSystemId canonical home system for the group
     * @param reserve whether the group is retained as strategic reserve
     * @param homeDefense whether the group is constrained to home-defense duty
     * @param maxStrategicRiskBps maximum accepted strategic-route risk in basis points
     * @return newly allocated canonical command-group metadata
     */
    public CommandGroupState formFleetCommandGroup(
            FleetForceRegistry forces,
            int factionId,
            String name,
            List<FleetId> memberFleetIds,
            StarSystemId homeSystemId,
            boolean reserve,
            boolean homeDefense,
            int maxStrategicRiskBps) {
        FleetCommandGroupService.FormationResult formed = new FleetCommandGroupService(
                session().runtime().world().getTopology()).form(
                        fleetCommands,
                        Objects.requireNonNull(forces, "forces"),
                        factionId,
                        name,
                        memberFleetIds,
                        homeSystemId,
                        reserve,
                        homeDefense,
                        maxStrategicRiskBps);
        fleetCommands = formed.state();
        return formed.group();
    }

    /**
     * Submits one player or AI Stage-21D order through the shared production validation boundary.
     *
     * <p>The supplied policies are read-only adapters to existing legal-access, Stage-18 service and
     * strategic-risk authorities. The coordinator stores only the canonical replacement returned by
     * {@link FleetOrderSubmissionService}; physical fleet placement is never mutated here.</p>
     *
     * @param forces read-only physical fleet reconstruction used for order validation
     * @param commandGroupId accepted Stage-21D command group receiving the order
     * @param type requested canonical fleet-order type
     * @param source player or AI provenance for the order
     * @param targetSystemId canonical physical destination system
     * @param accessPolicy existing authority for legal transit through candidate systems
     * @param servicePolicy existing authority for required destination service capability
     * @param riskPolicy existing authority for strategic-route risk acceptance
     * @return accepted canonical fleet-order metadata
     */
    public FleetOrderState submitFleetOrder(
            FleetForceRegistry forces,
            long commandGroupId,
            OrderType type,
            OrderSource source,
            StarSystemId targetSystemId,
            TransitAccessPolicy accessPolicy,
            ServiceCapabilityPolicy servicePolicy,
            StrategicRiskPolicy riskPolicy) {
        FleetOrderSubmissionService.SubmissionResult accepted = fleetOrderSubmissionService.submit(
                fleetCommands,
                Objects.requireNonNull(forces, "forces"),
                commandGroupId,
                Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(targetSystemId, "targetSystemId"),
                session().runtime().world().getAuthoritativeWorldTick(),
                Objects.requireNonNull(accessPolicy, "accessPolicy"),
                Objects.requireNonNull(servicePolicy, "servicePolicy"),
                Objects.requireNonNull(riskPolicy, "riskPolicy"));
        fleetCommands = accepted.state();
        return accepted.order();
    }

    /** @return current immutable Stage-21E strategic-operation metadata */
    public StrategicOperationState operations() {
        return operations;
    }

    /**
     * Admits one Stage-21E operation from an already accepted active Stage-21D order.
     *
     * @param forces read-only physical fleet reconstruction backing the command group
     * @param commandGroupId accepted Stage-21D command group with an active order
     * @param rulesOfEngagement canonical combat escalation constraints
     * @param supplyPolicy canonical supply/readiness continuation constraints
     * @param withdrawalPolicy canonical withdrawal conditions
     * @return newly allocated canonical operation metadata
     */
    public OperationState beginStrategicOperation(
            FleetForceRegistry forces,
            long commandGroupId,
            RulesOfEngagement rulesOfEngagement,
            SupplyPolicy supplyPolicy,
            WithdrawalPolicy withdrawalPolicy) {
        long operationId = operations.nextOperationId();
        operations = strategicOperationService.beginFromActiveOrder(
                operations,
                fleetCommands,
                Objects.requireNonNull(forces, "forces"),
                commandGroupId,
                session().runtime().world().getAuthoritativeWorldTick(),
                Objects.requireNonNull(rulesOfEngagement, "rulesOfEngagement"),
                Objects.requireNonNull(supplyPolicy, "supplyPolicy"),
                Objects.requireNonNull(withdrawalPolicy, "withdrawalPolicy"));
        return operations.requireOperation(operationId);
    }

    /**
     * Reconciles an existing Stage-21E operation against current ordinary physical readiness/supply.
     *
     * @param operationId accepted Stage-21E operation to review
     * @param forces current read-only physical fleet reconstruction
     * @return canonical review and continuation decision
     */
    public SupplyReview reviewStrategicOperationSupply(long operationId, FleetForceRegistry forces) {
        SupplyReview review = strategicOperationService.reviewSupplyAndReadiness(
                operations,
                operationId,
                Objects.requireNonNull(forces, "forces"),
                session().runtime().world().getAuthoritativeWorldTick());
        operations = review.state();
        return review;
    }

    /** @return current immutable Stage-21F territorial-transition metadata */
    public TerritorialTransitionState territorialTransitions() {
        return transitions;
    }

    /**
     * Reconciles one Stage-21F invasion occupation through the existing Stage-17 territory authority.
     *
     * <p>Any claim/control mutation is performed only by {@link TerritorialTransitionService} through
     * the ordinary world. The coordinator retains the returned Stage-21F metadata and any canonical
     * Stage-21E lifecycle replacement produced by that service.</p>
     *
     * @param forces current read-only physical fleet reconstruction
     * @param identities accepted content-to-world faction identity resolver
     * @param operationId accepted Stage-21E invasion operation being reconciled
     * @return canonical territorial reconciliation result
     */
    public TerritorialTransitionService.AdvanceResult advanceTerritorialTransition(
            FleetForceRegistry forces,
            FactionIdentityResolver identities,
            long operationId) {
        TerritorialTransitionService.AdvanceResult result = territorialTransitionService.advance(
                transitions,
                session().runtime().world(),
                operations,
                Objects.requireNonNull(forces, "forces"),
                Objects.requireNonNull(identities, "identities"),
                operationId,
                session().runtime().world().getAuthoritativeWorldTick());
        transitions = result.transitions();
        operations = result.operations();
        return result;
    }

    /**
     * Returns the accepted mutable Stage-21G settlement/recovery owner.
     *
     * <p>The service composes diplomacy, treasury, fleet orders and shipyard/loss evidence only when
     * callers provide those existing authorities to its methods; it never becomes a duplicate owner.</p>
     *
     * @return shared campaign Stage-21G recovery authority
     */
    public SettlementRecoveryService settlementRecovery() {
        return settlementRecoveryService;
    }

    /** @return current immutable Stage-21G settlement/recovery snapshot */
    public SettlementRecoveryState settlementRecoveryState() {
        return settlementRecoveryService.snapshot();
    }

    /** @return accepted mutable Stage-21H NPC/mission/reputation/story owner */
    public Stage21HNpcMissionService npcMissions() {
        return authorities.npcMissionService();
    }

    /**
     * Advances the ordinary physical campaign and then runs due Stage-21A actor reviews at the
     * resulting authoritative simulation tick.
     *
     * <p>Stage-21A receives only facts already persisted as actor-bounded Stage-21C relation memory.
     * The observation adapter has no world/truth reference, so zero-tick frames and hidden generated
     * state cannot mutate or leak into autonomous reasoning.</p>
     *
     * @param realDeltaSeconds finite non-negative presentation delta
     * @return ordinary physical campaign advance diagnostics
     */
    public GeneratedCampaignSession.AdvanceReport advanceFrame(float realDeltaSeconds) {
        GeneratedCampaignSession.AdvanceReport report = authorities.session().advanceFrame(realDeltaSeconds);
        if (report.fixedTicks() == 0L) {
            return report;
        }

        long nowTick = authorities.session().runtime().world().getAuthoritativeWorldTick();
        authorities.actors().reviewDue(
                nowTick,
                LIVING_ACTOR_REVIEW_BUDGET,
                LIVING_ACTOR_REVIEW_CADENCE_TICKS,
                factionId -> GeneratedCampaignActorObservationPublisher.publish(
                        factionId,
                        nowTick,
                        authorities.diplomacy()));
        return report;
    }

    /** @return complete native Stage-21I checkpoint from all currently composed authorities */
    public Stage21IGeneratedWorldRuntimePersistentState captureState() {
        return GeneratedCampaignAuthorityCheckpoint.capture(
                authorities.session(),
                authorities.actors(),
                authorities.strategicIntents(),
                authorities.diplomacy(),
                authorities.warfare(),
                fleetCommands,
                operations,
                transitions,
                settlementRecoveryService.snapshot(),
                authorities.npcMissions());
    }

    /** @return deterministic native Stage-21I bytes for the complete current campaign */
    public byte[] encode() {
        return Stage21IGeneratedWorldRuntimePersistenceCodec.encode(captureState());
    }

    /**
     * Compatibility helper proving that a Stage-20.5 checkpoint enters the same production path.
     *
     * @param session ordinary campaign whose accepted Stage-20.5 state should be migrated
     * @return coordinator restored through the public migration codec boundary
     */
    static GeneratedCampaignCoordinator migrateStage20(GeneratedCampaignSession session) {
        byte[] legacy = Stage20GeneratedWorldRuntimePersistenceCodec.encode(
                Objects.requireNonNull(session, "session").captureState());
        return decodeOrMigrate(legacy);
    }

    /**
     * Exposes the accepted handoff only to package integration code and tests.
     *
     * @return currently composed authority references and snapshots
     */
    RestoredAuthorities authorities() {
        return authorities;
    }
}
