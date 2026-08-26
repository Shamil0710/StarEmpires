package com.spacesim.world;

import com.spacesim.components.WalletComponent;
import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage21HMissionAuthority.Observation;
import com.spacesim.world.Stage21HMissionAuthority.Result;
import com.spacesim.world.Stage21HNpcMissionState.KnowledgeKind;
import com.spacesim.world.Stage21HNpcMissionState.MissionContract;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.MissionStatus;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.MissionWakeup;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcKnowledgeFact;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import com.spacesim.world.Stage21HNpcMissionState.ReputationEvent;
import com.spacesim.world.Stage21HNpcMissionState.ReputationEventKind;
import com.spacesim.world.Stage21HNpcMissionState.ReputationState;
import com.spacesim.world.Stage21HNpcMissionState.StoryChainState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stage-21H lifecycle service for bounded NPC knowledge and funded mission contracts.
 *
 * <p>The service owns mission escrow wallets because escrow is explicitly contract state. Every
 * escrow unit first leaves an ordinary faction treasury through {@link WorldSimulation}; terminal
 * refund returns through the same treasury boundary, while successful payout transfers the exact
 * escrow balance to a caller-owned authoritative wallet. No source/sink method is used.</p>
 */
public final class Stage21HNpcMissionService {
    private Stage21HNpcMissionState state;
    private final Map<String, WalletComponent> escrowByMissionId = new HashMap<>();

    /**
     * Restores one service from persistent Stage-21H state.
     *
     * @param initialState validated sidecar state
     */
    public Stage21HNpcMissionService(Stage21HNpcMissionState initialState) {
        state = Objects.requireNonNull(initialState, "Initial Stage-21H state not set");
        for (MissionContract mission : state.missions()) {
            if (mission.escrowMilliCredits() > 0L) {
                escrowByMissionId.put(mission.missionId(), new WalletComponent(mission.escrowMilliCredits()));
            }
        }
    }

    /** @return current immutable persistent state */
    public Stage21HNpcMissionState snapshot() {
        return state;
    }

    /**
     * Installs a deterministic NPC roster before missions reference it.
     *
     * @param npcs persistent character rows
     * @return updated state
     */
    public Stage21HNpcMissionState installNpcRoster(List<NpcState> npcs) {
        if (!state.npcs().isEmpty() || !state.missions().isEmpty()) {
            throw new IllegalStateException("NPC roster can only be installed before Stage-21H mission activity");
        }
        state = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                state.simulationTick(),
                state.nextMissionSequence(),
                npcs,
                state.missions(),
                state.reputations(),
                state.storyChains());
        return state;
    }

    /**
     * Updates only persistent location/availability for an existing NPC.
     *
     * @param npcId stable NPC identity
     * @param locationSystemId current real posting/location system
     * @param availability current availability
     * @param nowTick authoritative tick
     * @return updated NPC row
     */
    public NpcState updateNpcPresence(
            String npcId,
            StarSystemId locationSystemId,
            NpcAvailability availability,
            long nowTick) {
        requireAdvancingOrEqualTick(nowTick);
        NpcState current = requireNpc(npcId);
        NpcState replacement = new NpcState(
                current.npcId(), current.nameKey(), current.role(), current.factionContentId(),
                Objects.requireNonNull(locationSystemId, "NPC location not set"),
                Objects.requireNonNull(availability, "NPC availability not set"),
                current.knowledge());
        replaceNpc(replacement, nowTick);
        return replacement;
    }

    /**
     * Delivers one current Stage-21A observation to an affiliated NPC.
     *
     * @param npcId receiving NPC
     * @param snapshot actor-bounded faction observation snapshot
     * @param observation exact observation already present in that snapshot
     * @param factId stable NPC-fact identity
     * @return retained NPC knowledge fact
     */
    public NpcKnowledgeFact receiveActorObservation(
            String npcId,
            FactionActorObservationSnapshot snapshot,
            ActorObservation observation,
            String factId) {
        NpcState npc = requireNpc(npcId);
        FactionActorObservationSnapshot checkedSnapshot = Objects.requireNonNull(snapshot, "Actor snapshot not set");
        ActorObservation checkedObservation = Objects.requireNonNull(observation, "Actor observation not set");
        if (!npc.factionContentId().equals(checkedSnapshot.factionContentId())) {
            throw new IllegalArgumentException("NPC may receive Stage-21A observation only from its affiliated actor");
        }
        if (!checkedSnapshot.currentObservations().contains(checkedObservation)) {
            throw new IllegalArgumentException("NPC cannot receive absent or stale Stage-21A observation");
        }
        NpcKnowledgeFact fact = new NpcKnowledgeFact(
                factId,
                checkedObservation.targetId(),
                KnowledgeKind.ACTOR_OBSERVATION,
                checkedObservation.domain().name() + "." + checkedObservation.interestKind().name(),
                checkedObservation.severityBasisPoints(),
                checkedObservation.evidence().provenanceId(),
                checkedSnapshot.observedAtTick(),
                checkedObservation.evidence().freshUntilTick());
        addKnowledge(npc, fact, checkedSnapshot.observedAtTick());
        return fact;
    }

    /**
     * Delivers one exact owner-local Stage-20 static discovery row to an affiliated NPC.
     *
     * @param npcId receiving NPC
     * @param ownerKnowledge owner-local Stage-20 knowledge; owner must equal NPC faction
     * @param object exact static object reference
     * @param receivedTick authoritative delivery tick
     * @param factId stable NPC-fact identity
     * @return retained bounded discovery fact
     */
    public NpcKnowledgeFact receiveDiscovery(
            String npcId,
            Stage20DiscoveryKnowledgeState ownerKnowledge,
            StaticObjectRef object,
            long receivedTick,
            String factId) {
        requireAdvancingOrEqualTick(receivedTick);
        NpcState npc = requireNpc(npcId);
        Stage20DiscoveryKnowledgeState checkedKnowledge = Objects.requireNonNull(
                ownerKnowledge, "Discovery knowledge not set");
        if (!npc.factionContentId().equals(checkedKnowledge.ownerId())) {
            throw new IllegalArgumentException("NPC may receive discovery only from its affiliated knowledge owner");
        }
        StaticKnowledge knowledge = checkedKnowledge.knowledge(Objects.requireNonNull(object, "Static object not set"))
                .orElseThrow(() -> new IllegalArgumentException("Cannot deliver unknown Stage-20 discovery object"));
        DiscoveryEvidence provenance = knowledge.evidence().get(knowledge.evidence().size() - 1);
        NpcKnowledgeFact fact = new NpcKnowledgeFact(
                factId,
                knowledge.object().objectId(),
                KnowledgeKind.DISCOVERY,
                "DISCOVERY." + knowledge.object().kind().name() + "." + knowledge.state().name(),
                discoveryMagnitude(knowledge.state()),
                provenance.provenanceId(),
                receivedTick,
                -1L);
        addKnowledge(npc, fact, receivedTick);
        return fact;
    }

    /**
     * Returns dialogue-safe facts actually retained by this NPC at the requested tick.
     *
     * @param npcId NPC identity
     * @param nowTick authoritative dialogue tick
     * @return current facts only; no world lookup or hidden-state enrichment occurs
     */
    public List<NpcKnowledgeFact> dialogueFacts(String npcId, long nowTick) {
        return requireNpc(npcId).currentKnowledge(nowTick);
    }

    /**
     * Creates one fully funded mission offer after validating NPC role, availability and source facts.
     *
     * @param world ordinary world/treasury authority
     * @param issuerNpcId issuing NPC
     * @param template authored template
     * @param objective ordinary-authority predicate
     * @param sourceKnowledgeFactIds issuer facts that justify the offer
     * @param deadlineTick inclusive deadline
     * @param rewardMilliCredits positive reward transferred out of the issuer faction treasury
     * @return persistent funded mission
     */
    public MissionContract offerMission(
            WorldSimulation world,
            String issuerNpcId,
            MissionTemplate template,
            MissionObjective objective,
            List<String> sourceKnowledgeFactIds,
            long deadlineTick,
            long rewardMilliCredits) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "World simulation not set");
        long tick = checkedWorld.getAuthoritativeWorldTick();
        requireAdvancingOrEqualTick(tick);
        NpcState issuer = requireNpc(issuerNpcId);
        if (issuer.availability() != NpcAvailability.AVAILABLE) {
            throw new IllegalStateException("Unavailable NPC cannot issue mission: " + issuer.npcId());
        }
        MissionTemplate checkedTemplate = Objects.requireNonNull(template, "Mission template not set");
        if (!canIssue(issuer.role(), checkedTemplate)) {
            throw new IllegalStateException("NPC role lacks authority for mission template: " + checkedTemplate);
        }
        if (deadlineTick <= tick || rewardMilliCredits <= 0L) {
            throw new IllegalArgumentException("Mission deadline/reward is invalid");
        }
        FactionEconomicState economy = checkedWorld.findFactionEconomicState(issuer.factionContentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Mission issuer faction lacks ordinary economic authority: " + issuer.factionContentId()));
        long spendable = Math.max(0L,
                economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits());
        if (rewardMilliCredits > spendable) {
            throw new IllegalStateException(
                    "Mission reward exceeds issuer faction spendable treasury: " + issuer.factionContentId());
        }
        List<String> factIds = List.copyOf(Objects.requireNonNull(sourceKnowledgeFactIds, "Source facts not set"));
        for (String factId : factIds) {
            NpcKnowledgeFact fact = issuer.knowledge().stream()
                    .filter(value -> value.factId().equals(factId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Mission source fact is absent from issuer knowledge"));
            if (!fact.currentAt(tick)) {
                throw new IllegalArgumentException("Mission cannot be generated from stale issuer fact: " + factId);
            }
        }

        String missionId = "mission.stage21h." + state.nextMissionSequence();
        WalletComponent escrow = new WalletComponent();
        boolean funded = checkedWorld.transferFromFactionTreasury(
                issuer.factionContentId(),
                escrow,
                "mission-escrow:" + missionId,
                rewardMilliCredits,
                "stage21h-mission-escrow-fund");
        if (!funded) {
            throw new IllegalStateException("Issuer faction cannot lawfully fund mission reward: " + missionId);
        }

        MissionContract mission = new MissionContract(
                missionId,
                checkedTemplate,
                1,
                issuer.npcId(),
                issuer.factionContentId(),
                factIds,
                Objects.requireNonNull(objective, "Mission objective not set"),
                tick,
                deadlineTick,
                MissionStatus.OFFERED,
                tick,
                rewardMilliCredits,
                rewardMilliCredits,
                "",
                List.of());
        try {
            ArrayList<MissionContract> missions = new ArrayList<>(state.missions());
            missions.add(mission);
            state = new Stage21HNpcMissionState(
                    Stage21HNpcMissionState.CURRENT_VERSION,
                    tick,
                    Math.addExact(state.nextMissionSequence(), 1L),
                    state.npcs(), missions, state.reputations(), state.storyChains());
            escrowByMissionId.put(missionId, escrow);
            return mission;
        } catch (RuntimeException exception) {
            if (!checkedWorld.transferToFactionTreasury(
                    issuer.factionContentId(), escrow, "mission-escrow:" + missionId,
                    rewardMilliCredits, "stage21h-mission-escrow-rollback")) {
                exception.addSuppressed(new IllegalStateException("Mission escrow rollback failed"));
            }
            throw exception;
        }
    }

    /**
     * Accepts one still-live funded offer.
     *
     * @param missionId contract identity
     * @param nowTick authoritative tick
     * @return accepted contract
     */
    public MissionContract acceptMission(String missionId, long nowTick) {
        requireAdvancingOrEqualTick(nowTick);
        MissionContract mission = requireMission(missionId);
        if (mission.status() != MissionStatus.OFFERED) {
            throw new IllegalStateException("Only OFFERED mission may be accepted");
        }
        if (nowTick > mission.deadlineTick()) {
            throw new IllegalStateException("Expired mission cannot be accepted before expiry reconciliation");
        }
        MissionContract accepted = replaceStatus(mission, MissionStatus.ACCEPTED, nowTick, "accepted", mission.pendingWakeups());
        replaceMission(accepted, nowTick);
        return accepted;
    }

    /**
     * Adds a deduplicated relevant world-event wakeup to one active mission.
     *
     * @param missionId active mission identity
     * @param wakeup authoritative event wakeup
     * @return updated mission
     */
    public MissionContract enqueueWakeup(String missionId, MissionWakeup wakeup) {
        MissionContract mission = requireMission(missionId);
        if (!mission.active()) {
            throw new IllegalStateException("Terminal mission cannot receive wakeups");
        }
        ArrayList<MissionWakeup> wakeups = new ArrayList<>(mission.pendingWakeups());
        wakeups.add(Objects.requireNonNull(wakeup, "Mission wakeup not set"));
        MissionContract replacement = copyMission(mission, mission.status(), mission.statusUpdatedTick(),
                mission.escrowMilliCredits(), mission.outcomeCode(), wakeups);
        replaceMission(replacement, Math.max(state.simulationTick(), wakeup.observedTick()));
        return replacement;
    }

    /**
     * Selects only event/deadline-relevant missions under an explicit deterministic work budget.
     *
     * @param nowTick authoritative tick
     * @param maxMissions positive reconciliation budget
     * @return stable mission IDs sorted by deadline then identity
     */
    public List<String> dueMissionIds(long nowTick, int maxMissions) {
        if (nowTick < 0L || maxMissions <= 0) {
            throw new IllegalArgumentException("Mission scheduler tick/budget is invalid");
        }
        return state.missions().stream()
                .filter(MissionContract::active)
                .filter(mission -> nowTick > mission.deadlineTick()
                        || mission.pendingWakeups().stream().anyMatch(wakeup -> wakeup.eligibleTick() <= nowTick))
                .sorted(java.util.Comparator.comparingLong(MissionContract::deadlineTick)
                        .thenComparing(MissionContract::missionId))
                .limit(maxMissions)
                .map(MissionContract::missionId)
                .toList();
    }

    /**
     * Reconciles one mission against ordinary authority and performs exact terminal escrow handling.
     *
     * @param world ordinary world/treasury authority
     * @param issuerDiscovery issuer-faction Stage-20 discovery knowledge when needed
     * @param operations Stage-21E operation registry when needed
     * @param missionId mission identity
     * @param rewardRecipient authoritative wallet receiving a successful accepted reward
     * @param subjectActorId player/player-faction identity used for observed completion reputation
     * @return resulting contract state
     */
    public MissionContract reconcileMission(
            WorldSimulation world,
            Stage20DiscoveryKnowledgeState issuerDiscovery,
            StrategicOperationState operations,
            String missionId,
            WalletComponent rewardRecipient,
            String subjectActorId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "World simulation not set");
        long tick = checkedWorld.getAuthoritativeWorldTick();
        requireAdvancingOrEqualTick(tick);
        MissionContract mission = requireMission(missionId);
        if (!mission.active()) {
            return mission;
        }
        if (tick > mission.deadlineTick()) {
            return refundAndTerminate(checkedWorld, mission, MissionStatus.EXPIRED, tick, "deadline.expired");
        }
        Observation observation = Stage21HMissionAuthority.evaluate(
                checkedWorld, issuerDiscovery, operations, mission.objective());
        if (observation.result() == Result.PENDING) {
            MissionContract retained = copyMission(
                    mission, mission.status(), mission.statusUpdatedTick(), mission.escrowMilliCredits(),
                    observation.authorityCode(), consumeDueWakeups(mission, tick));
            replaceMission(retained, tick);
            return retained;
        }
        if (observation.result() == Result.FAILED) {
            return refundAndTerminate(checkedWorld, mission, MissionStatus.FAILED, tick, observation.authorityCode());
        }
        if (mission.status() == MissionStatus.OFFERED) {
            return refundAndTerminate(
                    checkedWorld, mission, MissionStatus.FAILED, tick, "opportunity.resolved-without-player");
        }

        WalletComponent recipient = Objects.requireNonNull(rewardRecipient, "Reward recipient wallet not set");
        WalletComponent escrow = requireEscrow(mission);
        if (!escrow.transferTo(recipient, mission.rewardMilliCredits())) {
            throw new IllegalStateException("Mission reward recipient cannot receive exact escrow payout");
        }
        escrowByMissionId.remove(mission.missionId());
        MissionContract completed = copyMission(
                mission, MissionStatus.COMPLETED, tick, 0L, observation.authorityCode(), List.of());
        replaceMission(completed, tick);
        addReputationEvent(
                mission.issuerNpcId(),
                requireText(subjectActorId, "Reputation subject actor"),
                new ReputationEvent(
                        "reputation." + mission.missionId() + ".completed",
                        ReputationEventKind.CONTRACT_COMPLETED,
                        10,
                        tick,
                        mission.missionId()),
                tick);
        return requireMission(mission.missionId());
    }

    /**
     * Records one explicitly observed reputation event for an NPC who already knows its subject fact.
     *
     * @param npcId reputation-owning NPC
     * @param subjectActorId actor being remembered
     * @param sourceFactId fact proving the NPC observed/received the underlying event
     * @param event reputation event
     * @return updated directed reputation row
     */
    public ReputationState recordObservedReputation(
            String npcId,
            String subjectActorId,
            String sourceFactId,
            ReputationEvent event) {
        NpcState npc = requireNpc(npcId);
        NpcKnowledgeFact fact = npc.knowledge().stream()
                .filter(value -> value.factId().equals(sourceFactId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Reputation event lacks NPC-known source fact"));
        ReputationEvent checked = Objects.requireNonNull(event, "Reputation event not set");
        if (checked.observedTick() < fact.receivedTick()) {
            throw new IllegalArgumentException("Reputation event cannot predate its observed source fact");
        }
        return addReputationEvent(npcId, requireText(subjectActorId, "Reputation subject actor"), checked,
                Math.max(state.simulationTick(), checked.observedTick()));
    }

    /**
     * Installs persistent authored-chain state without creating missions or world outcomes.
     *
     * @param chain chain progress row
     * @return updated aggregate
     */
    public Stage21HNpcMissionState installStoryChain(StoryChainState chain) {
        ArrayList<StoryChainState> chains = new ArrayList<>(state.storyChains());
        chains.add(Objects.requireNonNull(chain, "Story chain not set"));
        state = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION, state.simulationTick(), state.nextMissionSequence(),
                state.npcs(), state.missions(), state.reputations(), chains);
        return state;
    }

    private MissionContract refundAndTerminate(
            WorldSimulation world,
            MissionContract mission,
            MissionStatus terminal,
            long tick,
            String outcomeCode) {
        WalletComponent escrow = requireEscrow(mission);
        if (!world.transferToFactionTreasury(
                mission.issuerFactionId(), escrow, "mission-escrow:" + mission.missionId(),
                mission.rewardMilliCredits(), "stage21h-mission-escrow-refund")) {
            throw new IllegalStateException("Mission escrow refund cannot return exact funds to issuer treasury");
        }
        escrowByMissionId.remove(mission.missionId());
        MissionContract replacement = copyMission(mission, terminal, tick, 0L, outcomeCode, List.of());
        replaceMission(replacement, tick);
        return requireMission(mission.missionId());
    }

    private WalletComponent requireEscrow(MissionContract mission) {
        WalletComponent wallet = escrowByMissionId.get(mission.missionId());
        if (wallet == null || wallet.getBalanceMilliCredits() != mission.escrowMilliCredits()) {
            throw new IllegalStateException("Mission escrow wallet differs from persistent escrow balance");
        }
        return wallet;
    }

    private ReputationState addReputationEvent(
            String ownerId,
            String subjectActorId,
            ReputationEvent event,
            long tick) {
        ArrayList<ReputationState> reputations = new ArrayList<>(state.reputations());
        ReputationState current = reputations.stream()
                .filter(value -> value.ownerId().equals(ownerId) && value.subjectActorId().equals(subjectActorId))
                .findFirst().orElse(new ReputationState(ownerId, subjectActorId, List.of()));
        if (current.events().stream().anyMatch(value -> value.eventId().equals(event.eventId()))) {
            return current;
        }
        ArrayList<ReputationEvent> events = new ArrayList<>(current.events());
        events.add(event);
        ReputationState replacement = new ReputationState(ownerId, subjectActorId, events);
        reputations.remove(current);
        reputations.add(replacement);
        state = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                Math.max(state.simulationTick(), tick),
                state.nextMissionSequence(), state.npcs(), state.missions(), reputations, state.storyChains());
        return replacement;
    }

    private void addKnowledge(NpcState npc, NpcKnowledgeFact fact, long tick) {
        if (npc.knows(fact.factId())) {
            throw new IllegalArgumentException("Duplicate NPC knowledge fact ID: " + fact.factId());
        }
        ArrayList<NpcKnowledgeFact> knowledge = new ArrayList<>(npc.knowledge());
        knowledge.add(fact);
        replaceNpc(new NpcState(
                npc.npcId(), npc.nameKey(), npc.role(), npc.factionContentId(),
                npc.locationSystemId(), npc.availability(), knowledge), tick);
    }

    private void replaceNpc(NpcState replacement, long tick) {
        ArrayList<NpcState> npcs = new ArrayList<>(state.npcs());
        npcs.removeIf(value -> value.npcId().equals(replacement.npcId()));
        npcs.add(replacement);
        state = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION, Math.max(state.simulationTick(), tick),
                state.nextMissionSequence(), npcs, state.missions(), state.reputations(), state.storyChains());
    }

    private void replaceMission(MissionContract replacement, long tick) {
        ArrayList<MissionContract> missions = new ArrayList<>(state.missions());
        boolean removed = missions.removeIf(value -> value.missionId().equals(replacement.missionId()));
        if (!removed) {
            throw new IllegalArgumentException("Unknown mission: " + replacement.missionId());
        }
        missions.add(replacement);
        state = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION, Math.max(state.simulationTick(), tick),
                state.nextMissionSequence(), state.npcs(), missions, state.reputations(), state.storyChains());
    }

    private MissionContract replaceStatus(
            MissionContract mission,
            MissionStatus status,
            long tick,
            String outcomeCode,
            List<MissionWakeup> wakeups) {
        return copyMission(mission, status, tick, mission.escrowMilliCredits(), outcomeCode, wakeups);
    }

    private static MissionContract copyMission(
            MissionContract mission,
            MissionStatus status,
            long statusTick,
            long escrow,
            String outcomeCode,
            List<MissionWakeup> wakeups) {
        return new MissionContract(
                mission.missionId(), mission.template(), mission.templateVersion(),
                mission.issuerNpcId(), mission.issuerFactionId(), mission.sourceKnowledgeFactIds(),
                mission.objective(), mission.createdTick(), mission.deadlineTick(), status, statusTick,
                mission.rewardMilliCredits(), escrow, outcomeCode, wakeups);
    }

    private static List<MissionWakeup> consumeDueWakeups(MissionContract mission, long tick) {
        return mission.pendingWakeups().stream().filter(value -> value.eligibleTick() > tick).toList();
    }

    private NpcState requireNpc(String npcId) {
        String checked = requireText(npcId, "NPC ID");
        return state.npcs().stream().filter(value -> value.npcId().equals(checked)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown NPC: " + checked));
    }

    private MissionContract requireMission(String missionId) {
        String checked = requireText(missionId, "Mission ID");
        return state.missions().stream().filter(value -> value.missionId().equals(checked)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown mission: " + checked));
    }

    private void requireAdvancingOrEqualTick(long tick) {
        if (tick < state.simulationTick()) {
            throw new IllegalArgumentException("Stage-21H mutation cannot move backward in time");
        }
    }

    private static boolean canIssue(NpcRole role, MissionTemplate template) {
        return switch (role) {
            case OFFICIAL -> template == MissionTemplate.ORDINARY_MARKET_PROCUREMENT
                    || template == MissionTemplate.CONSTRUCTION_REPAIR_INPUT_DELIVERY
                    || template == MissionTemplate.IMPERIAL_ACCESS_NEGOTIATION;
            case MILITARY -> template == MissionTemplate.CONVOY_ESCORT
                    || template == MissionTemplate.STRANDED_FLEET_RESCUE_REFUEL
                    || template == MissionTemplate.SYSTEM_OBJECT_RECONNAISSANCE
                    || template == MissionTemplate.INTERCEPTION_DEFENSE;
            case TRADE_LOGISTICS -> template == MissionTemplate.EMERGENCY_SUPPLY_DELIVERY
                    || template == MissionTemplate.ORDINARY_MARKET_PROCUREMENT
                    || template == MissionTemplate.CONVOY_ESCORT;
            case INDUSTRY_YARD -> template == MissionTemplate.EMERGENCY_SUPPLY_DELIVERY
                    || template == MissionTemplate.ORDINARY_MARKET_PROCUREMENT
                    || template == MissionTemplate.CONSTRUCTION_REPAIR_INPUT_DELIVERY;
            case EXPLORATION_INTELLIGENCE -> template == MissionTemplate.SYSTEM_OBJECT_RECONNAISSANCE
                    || template == MissionTemplate.DERELICT_INVESTIGATION_RECOVERY
                    || template == MissionTemplate.INTERCEPTION_DEFENSE
                    || template == MissionTemplate.IMPERIAL_ACCESS_NEGOTIATION;
            case INDEPENDENT_FRONTIER -> template == MissionTemplate.EMERGENCY_SUPPLY_DELIVERY
                    || template == MissionTemplate.ORDINARY_MARKET_PROCUREMENT
                    || template == MissionTemplate.STRANDED_FLEET_RESCUE_REFUEL
                    || template == MissionTemplate.DERELICT_INVESTIGATION_RECOVERY;
        };
    }

    private static int discoveryMagnitude(Stage20DiscoveryKnowledgeState.DiscoveryState state) {
        return switch (state) {
            case UNKNOWN -> 0;
            case DETECTED -> 2_500;
            case CLASSIFIED -> 5_000;
            case KNOWN_STATIC_LOCATION -> 10_000;
            case TRACKED -> throw new IllegalArgumentException("TRACKED does not belong to static discovery knowledge");
        };
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
