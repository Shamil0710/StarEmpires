package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent Stage-21H RPG-sidecar state grounded in already authoritative living-world state.
 *
 * <p>The aggregate owns only NPC identity/availability/received knowledge, mission lifecycle and
 * escrow bookkeeping, bounded RPG reputation evidence, and authored chain progress. It never owns
 * treasury, cargo, fleets, construction, diplomacy, operations, territory, industrial inventory or
 * discovery truth.</p>
 *
 * @param schemaVersion exact Stage-21H sidecar schema
 * @param simulationTick latest authoritative world tick represented by this sidecar
 * @param nextMissionSequence next deterministic mission sequence
 * @param npcs persistent NPC rows
 * @param missions persistent contract rows
 * @param reputations bounded observed RPG reputation rows
 * @param storyChains persistent authored-chain progress rows
 */
public record Stage21HNpcMissionState(
        int schemaVersion,
        long simulationTick,
        long nextMissionSequence,
        List<NpcState> npcs,
        List<MissionContract> missions,
        List<ReputationState> reputations,
        List<StoryChainState> storyChains) {

    /** Current Stage-21H sidecar schema. */
    public static final int CURRENT_VERSION = 1;
    private static final String MISSION_SEQUENCE_PREFIX = "mission.stage21h.";

    /** Six canonical Stage-21H role archetypes. */
    public enum NpcRole {
        /** Political, administrative and diplomatic official. */ OFFICIAL,
        /** Fleet, defense or security commander. */ MILITARY,
        /** Trade, procurement and freight operator. */ TRADE_LOGISTICS,
        /** Industrial, repair or shipyard authority. */ INDUSTRY_YARD,
        /** Exploration, reconnaissance or intelligence contact. */ EXPLORATION_INTELLIGENCE,
        /** Civilian, frontier, labor or independent voice. */ INDEPENDENT_FRONTIER
    }

    /** Persistent NPC availability. */
    public enum NpcAvailability {
        /** NPC may currently issue or discuss work. */ AVAILABLE,
        /** NPC exists but is temporarily unavailable. */ UNAVAILABLE,
        /** NPC was displaced from the normal posting. */ DISPLACED,
        /** NPC is permanently unavailable because the character died. */ DEAD
    }

    /** Provenance family retained by an NPC knowledge fact. */
    public enum KnowledgeKind {
        /** Fact delivered through the Stage-21A actor-observation boundary. */ ACTOR_OBSERVATION,
        /** Fact delivered from owner-local Stage-20 discovery knowledge. */ DISCOVERY
    }

    /** Stage-21H mission templates. The first eight entries are the canonical minimum contract set. */
    public enum MissionTemplate {
        /** Emergency physical supply delivery. */ EMERGENCY_SUPPLY_DELIVERY,
        /** Ordinary market procurement. */ ORDINARY_MARKET_PROCUREMENT,
        /** Convoy escort. */ CONVOY_ESCORT,
        /** Stranded-fleet rescue or refuel. */ STRANDED_FLEET_RESCUE_REFUEL,
        /** System/object reconnaissance. */ SYSTEM_OBJECT_RECONNAISSANCE,
        /** Derelict investigation and finite recovery. */ DERELICT_INVESTIGATION_RECOVERY,
        /** Interception/defense against a real threat. */ INTERCEPTION_DEFENSE,
        /** Construction or repair input delivery. */ CONSTRUCTION_REPAIR_INPUT_DELIVERY,
        /** Gold-slice access negotiation step grounded in ordinary diplomacy. */ IMPERIAL_ACCESS_NEGOTIATION
    }

    /** Mission lifecycle. */
    public enum MissionStatus {
        /** Funded opportunity exists but has not been accepted. */ OFFERED,
        /** Offer was explicitly rejected before acceptance. */ REJECTED,
        /** Contract was accepted and remains active. */ ACCEPTED,
        /** Authoritative world state satisfied the objective. */ COMPLETED,
        /** Authoritative world state made the objective impossible or failed. */ FAILED,
        /** Simulation-time deadline elapsed. */ EXPIRED,
        /** Contract was cancelled through a validated lifecycle command. */ CANCELLED
    }

    /** Ordinary authority domain consulted for objective evaluation. */
    public enum ObjectiveAuthority {
        /** Ordinary Stage-20 physical freight/order authority. */ FREIGHT,
        /** Ordinary fleet placement/identity/engineering authority. */ FLEET,
        /** Stage-20 owner-local discovery authority. */ DISCOVERY,
        /** Ordinary construction-project authority. */ CONSTRUCTION,
        /** Stage-18 finite salvage-source authority plus Stage-20 discovery. */ INDUSTRY,
        /** Stage-17 treaty/access authority. */ DIPLOMACY,
        /** Stage-21E operation authority. */ OPERATION,
        /** Ordinary economic state. */ ECONOMY
    }

    /** Predicate vocabulary whose truth must be read from ordinary authorities. */
    public enum ObjectiveKind {
        /** One ordinary Stage-20 transport order must deliver at least the requested whole kilograms. */
        FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
        /** A stable FleetId must be present in the specified StarSystem. */ FLEET_PRESENT_IN_SYSTEM,
        /** A stable FleetId must no longer exist in ordinary world placement. */ FLEET_ABSENT,
        /** Issuer-owned convoy and contracted escort FleetId must be co-present at the destination. */
        ESCORT_FLEETS_PRESENT_IN_SYSTEM,
        /** A stable FleetId must carry at least the requested reaction-mass kilograms. */
        FLEET_REACTION_MASS_KG_AT_LEAST,
        /** Owner-local discovery must reach at least the requested static state. */ DISCOVERY_AT_LEAST,
        /** A finite Stage-18 salvage source must be discovered and actually depleted by the requested kilograms. */
        DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST,
        /** An ordinary construction project must have at least the requested delivered units. */
        CONSTRUCTION_DELIVERED_UNITS_AT_LEAST,
        /** An ordinary construction project must be terminal COMPLETED. */ CONSTRUCTION_COMPLETED,
        /** Existing diplomacy must currently grant legal market access. */ MARKET_ACCESS_ALLOWED,
        /** A Stage-21E operation must reach the requested terminal status. */ OPERATION_STATUS,
        /** A faction treasury must retain at least the requested amount. */ FACTION_TREASURY_AT_LEAST
    }

    /** Observed RPG reputation event families. */
    public enum ReputationEventKind {
        /** Mission was completed and the observer learned it. */ CONTRACT_COMPLETED,
        /** Accepted mission failed and the observer learned it. */ CONTRACT_FAILED,
        /** Explicit betrayal was observed by the reputation owner. */ BETRAYAL,
        /** A player action was observed through a lawful channel. */ OBSERVED_PLAYER_ACTION,
        /** A faction-level outcome was observed through a lawful channel. */ OBSERVED_FACTION_OUTCOME
    }

    /** Persistent authored story-chain lifecycle. */
    public enum StoryChainStatus {
        /** Chain is available but has not started. */ AVAILABLE,
        /** Chain has at least one active/resolved step and more may follow. */ ACTIVE,
        /** Final authored step was resolved. */ COMPLETED,
        /** The live world invalidated the remaining chain honestly. */ CLOSED_BY_WORLD
    }

    /**
     * One fact actually received by an NPC.
     *
     * @param factId stable deduplication identity
     * @param subjectId stable actor-known subject identity
     * @param kind provenance family
     * @param claimCode bounded semantic claim code; never hidden world payload
     * @param magnitudeBasisPoints optional bounded magnitude in [0,10000]
     * @param provenanceId stable originating report/scan/ledger identity
     * @param receivedTick simulation tick when the NPC received the fact
     * @param freshUntilTick inclusive freshness horizon, or -1 for durable knowledge
     */
    public record NpcKnowledgeFact(
            String factId,
            String subjectId,
            KnowledgeKind kind,
            String claimCode,
            int magnitudeBasisPoints,
            String provenanceId,
            long receivedTick,
            long freshUntilTick) implements Comparable<NpcKnowledgeFact> {

        /**
         * Validates one bounded NPC-known fact.
         *
         * @param factId stable fact identity
         * @param subjectId actor-known subject
         * @param kind provenance family
         * @param claimCode bounded semantic claim
         * @param magnitudeBasisPoints bounded magnitude
         * @param provenanceId originating evidence identity
         * @param receivedTick receipt tick
         * @param freshUntilTick inclusive freshness or -1
         */
        public NpcKnowledgeFact {
            factId = requireText(factId, "Knowledge fact ID");
            subjectId = requireText(subjectId, "Knowledge subject");
            Objects.requireNonNull(kind, "Knowledge kind not set");
            claimCode = requireText(claimCode, "Knowledge claim code");
            if (magnitudeBasisPoints < 0 || magnitudeBasisPoints > 10_000) {
                throw new IllegalArgumentException("Knowledge magnitude must be in [0,10000]");
            }
            provenanceId = requireText(provenanceId, "Knowledge provenance ID");
            requireNonNegative(receivedTick, "Knowledge received tick");
            if (freshUntilTick < -1L || freshUntilTick >= 0L && freshUntilTick < receivedTick) {
                throw new IllegalArgumentException("Knowledge freshness horizon is invalid");
            }
        }

        /**
         * @param nowTick authoritative tick
         * @return whether the retained fact is currently usable
         */
        public boolean currentAt(long nowTick) {
            requireNonNegative(nowTick, "Current tick");
            return nowTick >= receivedTick && (freshUntilTick < 0L || nowTick <= freshUntilTick);
        }

        @Override
        public int compareTo(NpcKnowledgeFact other) {
            return factId.compareTo(Objects.requireNonNull(other, "other").factId);
        }
    }

    /**
     * One persistent character identity.
     *
     * @param npcId stable character identity
     * @param nameKey stable localization/name key
     * @param role canonical role archetype
     * @param factionContentId institutional affiliation used for lawful issuer authority
     * @param locationSystemId current persistent system location
     * @param availability current availability
     * @param knowledge facts actually delivered to this character
     */
    public record NpcState(
            String npcId,
            String nameKey,
            NpcRole role,
            String factionContentId,
            StarSystemId locationSystemId,
            NpcAvailability availability,
            List<NpcKnowledgeFact> knowledge) implements Comparable<NpcState> {

        /**
         * Validates and canonicalizes one NPC row.
         *
         * @param npcId stable NPC identity
         * @param nameKey localization/name key
         * @param role role archetype
         * @param factionContentId affiliation
         * @param locationSystemId current system
         * @param availability availability state
         * @param knowledge retained facts
         */
        public NpcState {
            npcId = requireText(npcId, "NPC ID");
            nameKey = requireText(nameKey, "NPC name key");
            Objects.requireNonNull(role, "NPC role not set");
            factionContentId = requireText(factionContentId, "NPC faction");
            Objects.requireNonNull(locationSystemId, "NPC location not set");
            Objects.requireNonNull(availability, "NPC availability not set");
            knowledge = canonical(knowledge, Comparator.naturalOrder(), NpcKnowledgeFact::factId, "NPC knowledge");
        }

        /**
         * @param factId knowledge ID
         * @return whether this NPC owns that retained fact
         */
        public boolean knows(String factId) {
            String checked = requireText(factId, "Knowledge fact ID");
            return knowledge.stream().anyMatch(value -> value.factId().equals(checked));
        }

        /**
         * @param nowTick authoritative tick
         * @return current dialogue-safe facts only
         */
        public List<NpcKnowledgeFact> currentKnowledge(long nowTick) {
            return knowledge.stream().filter(value -> value.currentAt(nowTick)).toList();
        }

        @Override
        public int compareTo(NpcState other) {
            return npcId.compareTo(Objects.requireNonNull(other, "other").npcId);
        }
    }

    /**
     * One objective predicate. The mission stores references and thresholds, never cached truth.
     *
     * <p>For {@link ObjectiveKind#ESCORT_FLEETS_PRESENT_IN_SYSTEM}, {@code subjectId} is the convoy
     * FleetId and {@code requiredState} is the contracted escort FleetId. For
     * {@link ObjectiveKind#DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST}, {@code subjectId} is
     * {@code staticObjectId|salvageSourceId} and {@code requiredState} is {@code KIND:STATE}.</p>
     *
     * @param authority ordinary authority domain
     * @param kind predicate kind
     * @param subjectId stable order/FleetId/project/operation/faction/object identity
     * @param systemId target StarSystem value, or 0 when the predicate is not system-scoped
     * @param threshold non-negative numeric threshold used by the predicate
     * @param requiredState optional enum/state token interpreted only by the owning production adapter
     */
    public record MissionObjective(
            ObjectiveAuthority authority,
            ObjectiveKind kind,
            String subjectId,
            long systemId,
            long threshold,
            String requiredState) {

        /**
         * Validates one declarative ordinary-authority predicate.
         *
         * @param authority authority domain
         * @param kind predicate kind
         * @param subjectId stable subject
         * @param systemId system value or zero
         * @param threshold numeric threshold
         * @param requiredState bounded state token
         */
        public MissionObjective {
            Objects.requireNonNull(authority, "Objective authority not set");
            Objects.requireNonNull(kind, "Objective kind not set");
            subjectId = requireText(subjectId, "Objective subject");
            if (systemId < 0L || threshold < 0L) {
                throw new IllegalArgumentException("Objective numeric values cannot be negative");
            }
            requiredState = Objects.requireNonNull(requiredState, "Objective required state not set").strip();
            validateObjectiveShape(authority, kind, subjectId, systemId, threshold, requiredState);
        }
    }

    /**
     * Persistent bounded mission wakeup.
     *
     * @param eventId stable world-event identity
     * @param observedTick tick when relevant authority reported it
     * @param eligibleTick earliest reconciliation tick
     */
    public record MissionWakeup(String eventId, long observedTick, long eligibleTick)
            implements Comparable<MissionWakeup> {
        /**
         * Validates one wakeup.
         *
         * @param eventId world-event identity
         * @param observedTick observation tick
         * @param eligibleTick reconciliation eligibility tick
         */
        public MissionWakeup {
            eventId = requireText(eventId, "Mission wakeup event ID");
            requireNonNegative(observedTick, "Mission wakeup observation tick");
            requireNonNegative(eligibleTick, "Mission wakeup eligibility tick");
            if (eligibleTick < observedTick) {
                throw new IllegalArgumentException("Mission wakeup eligibility cannot precede observation");
            }
        }

        @Override
        public int compareTo(MissionWakeup other) {
            MissionWakeup checked = Objects.requireNonNull(other, "other");
            int eligible = Long.compare(eligibleTick, checked.eligibleTick);
            return eligible != 0 ? eligible : eventId.compareTo(checked.eventId);
        }
    }

    /**
     * One persistent mission contract.
     *
     * @param missionId stable contract identity
     * @param template canonical template
     * @param templateVersion authored template version
     * @param issuerNpcId issuing NPC
     * @param issuerFactionId lawful funding/authority faction
     * @param sourceKnowledgeFactIds issuer facts that justify the opportunity
     * @param objective declarative ordinary-authority predicate
     * @param createdTick offer creation tick
     * @param deadlineTick inclusive simulation-time deadline
     * @param status lifecycle state
     * @param statusUpdatedTick latest lifecycle transition tick
     * @param rewardMilliCredits promised funded monetary reward
     * @param escrowMilliCredits real balance currently held by the mission escrow wallet
     * @param outcomeCode bounded diagnostic/outcome code
     * @param pendingWakeups relevant world-event wakeups waiting for reconciliation
     */
    public record MissionContract(
            String missionId,
            MissionTemplate template,
            int templateVersion,
            String issuerNpcId,
            String issuerFactionId,
            List<String> sourceKnowledgeFactIds,
            MissionObjective objective,
            long createdTick,
            long deadlineTick,
            MissionStatus status,
            long statusUpdatedTick,
            long rewardMilliCredits,
            long escrowMilliCredits,
            String outcomeCode,
            List<MissionWakeup> pendingWakeups) implements Comparable<MissionContract> {

        /**
         * Validates and canonicalizes one funded contract.
         *
         * @param missionId contract identity
         * @param template template family
         * @param templateVersion template version
         * @param issuerNpcId issuer NPC
         * @param issuerFactionId issuer faction
         * @param sourceKnowledgeFactIds source knowledge IDs
         * @param objective ordinary-authority predicate
         * @param createdTick creation tick
         * @param deadlineTick deadline
         * @param status lifecycle status
         * @param statusUpdatedTick latest status tick
         * @param rewardMilliCredits promised reward
         * @param escrowMilliCredits currently retained escrow
         * @param outcomeCode outcome/diagnostic code
         * @param pendingWakeups bounded pending event wakeups
         */
        public MissionContract {
            missionId = requireText(missionId, "Mission ID");
            Objects.requireNonNull(template, "Mission template not set");
            if (templateVersion <= 0) {
                throw new IllegalArgumentException("Mission template version must be positive");
            }
            issuerNpcId = requireText(issuerNpcId, "Mission issuer NPC");
            issuerFactionId = requireText(issuerFactionId, "Mission issuer faction");
            sourceKnowledgeFactIds = canonicalStrings(sourceKnowledgeFactIds, "Mission source knowledge");
            if (sourceKnowledgeFactIds.isEmpty()) {
                throw new IllegalArgumentException("Mission must retain at least one issuer-known source fact");
            }
            Objects.requireNonNull(objective, "Mission objective not set");
            validateTemplateObjective(template, objective.kind());
            requireNonNegative(createdTick, "Mission creation tick");
            if (deadlineTick <= createdTick) {
                throw new IllegalArgumentException("Mission deadline must follow creation");
            }
            Objects.requireNonNull(status, "Mission status not set");
            if (statusUpdatedTick < createdTick) {
                throw new IllegalArgumentException("Mission status update cannot precede creation");
            }
            if (rewardMilliCredits <= 0L || escrowMilliCredits < 0L || escrowMilliCredits > rewardMilliCredits) {
                throw new IllegalArgumentException("Mission reward/escrow values are invalid");
            }
            boolean active = status == MissionStatus.OFFERED || status == MissionStatus.ACCEPTED;
            if (active != (escrowMilliCredits == rewardMilliCredits)) {
                throw new IllegalArgumentException("Active mission must retain the complete funded reward in escrow");
            }
            if (!active && escrowMilliCredits != 0L) {
                throw new IllegalArgumentException("Terminal mission cannot retain escrow funds");
            }
            outcomeCode = Objects.requireNonNull(outcomeCode, "Mission outcome code not set").strip();
            pendingWakeups = canonical(pendingWakeups, Comparator.naturalOrder(), MissionWakeup::eventId, "Mission wakeups");
            if (!active && !pendingWakeups.isEmpty()) {
                throw new IllegalArgumentException("Terminal mission cannot retain pending wakeups");
            }
            if (!active && outcomeCode.isEmpty()) {
                throw new IllegalArgumentException("Terminal mission requires an explicit outcome code");
            }
        }

        /** @return whether the contract still requires world-state reconciliation */
        public boolean active() {
            return status == MissionStatus.OFFERED || status == MissionStatus.ACCEPTED;
        }

        @Override
        public int compareTo(MissionContract other) {
            return missionId.compareTo(Objects.requireNonNull(other, "other").missionId);
        }
    }

    /**
     * One observed RPG reputation event.
     *
     * @param eventId stable deduplication identity
     * @param kind observed event family
     * @param delta signed bounded change in [-25,25]
     * @param observedTick tick when the reputation owner learned the event
     * @param subjectId stable mission/action/outcome provenance identity
     */
    public record ReputationEvent(
            String eventId,
            ReputationEventKind kind,
            int delta,
            long observedTick,
            String subjectId) implements Comparable<ReputationEvent> {
        /**
         * Validates one observed reputation event.
         *
         * @param eventId event identity
         * @param kind event family
         * @param delta bounded signed delta
         * @param observedTick observation tick
         * @param subjectId event subject/provenance
         */
        public ReputationEvent {
            eventId = requireText(eventId, "Reputation event ID");
            Objects.requireNonNull(kind, "Reputation event kind not set");
            if (delta < -25 || delta > 25 || delta == 0) {
                throw new IllegalArgumentException("Reputation delta must be non-zero in [-25,25]");
            }
            requireNonNegative(observedTick, "Reputation observation tick");
            subjectId = requireText(subjectId, "Reputation event subject");
        }

        @Override
        public int compareTo(ReputationEvent other) {
            return eventId.compareTo(Objects.requireNonNull(other, "other").eventId);
        }
    }

    /**
     * Directed RPG reputation. This is social memory, not Stage-17/21C legal faction diplomacy.
     *
     * @param ownerId NPC or faction-facing reputation owner
     * @param subjectActorId player/player-faction actor being remembered
     * @param events observed events only
     */
    public record ReputationState(
            String ownerId,
            String subjectActorId,
            List<ReputationEvent> events) implements Comparable<ReputationState> {
        /**
         * Validates one directed reputation row.
         *
         * @param ownerId reputation owner
         * @param subjectActorId remembered actor
         * @param events observed evidence events
         */
        public ReputationState {
            ownerId = requireText(ownerId, "Reputation owner");
            subjectActorId = requireText(subjectActorId, "Reputation subject actor");
            if (ownerId.equals(subjectActorId)) {
                throw new IllegalArgumentException("Reputation owner cannot target itself");
            }
            events = canonical(events, Comparator.naturalOrder(), ReputationEvent::eventId, "Reputation events");
        }

        /** @return clamped derived RPG reputation in [-100,100] */
        public int derivedValue() {
            int value = 0;
            for (ReputationEvent event : events) {
                value = Math.max(-100, Math.min(100, value + event.delta()));
            }
            return value;
        }

        @Override
        public int compareTo(ReputationState other) {
            ReputationState checked = Objects.requireNonNull(other, "other");
            int owner = ownerId.compareTo(checked.ownerId);
            return owner != 0 ? owner : subjectActorId.compareTo(checked.subjectActorId);
        }
    }

    /**
     * Persistent progress of one compact authored chain.
     *
     * @param chainId stable chain identity
     * @param currentStep zero-based number of authored steps already issued
     * @param totalSteps fixed authored step count
     * @param status current chain status
     * @param missionIds mission identities already issued by this chain in authored order
     */
    public record StoryChainState(
            String chainId,
            int currentStep,
            int totalSteps,
            StoryChainStatus status,
            List<String> missionIds) implements Comparable<StoryChainState> {
        /**
         * Validates one authored-chain progress row.
         *
         * @param chainId chain identity
         * @param currentStep issued-step count
         * @param totalSteps authored step count
         * @param status lifecycle state
         * @param missionIds linked missions in authored order
         */
        public StoryChainState {
            chainId = requireText(chainId, "Story chain ID");
            if (totalSteps < 3 || totalSteps > 5 || currentStep < 0 || currentStep > totalSteps) {
                throw new IllegalArgumentException("Story chain must contain 3-5 steps with valid progress");
            }
            Objects.requireNonNull(status, "Story chain status not set");
            missionIds = orderedUniqueStrings(missionIds, "Story chain mission IDs");
            if (missionIds.size() != currentStep) {
                throw new IllegalArgumentException("Story chain issued-step count must equal linked mission count");
            }
            if (status == StoryChainStatus.AVAILABLE && currentStep != 0) {
                throw new IllegalArgumentException("Available story chain cannot already contain progress");
            }
            if (status == StoryChainStatus.COMPLETED && currentStep != totalSteps) {
                throw new IllegalArgumentException("Completed story chain must reach its final step");
            }
        }

        @Override
        public int compareTo(StoryChainState other) {
            return chainId.compareTo(Objects.requireNonNull(other, "other").chainId);
        }
    }

    /**
     * Validates and canonicalizes the complete Stage-21H sidecar.
     *
     * @param schemaVersion sidecar schema
     * @param simulationTick sidecar authoritative tick
     * @param nextMissionSequence next mission allocator sequence
     * @param npcs persistent NPC rows
     * @param missions persistent mission rows
     * @param reputations persistent reputation rows
     * @param storyChains persistent story-chain rows
     */
    public Stage21HNpcMissionState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-21H sidecar schema: " + schemaVersion);
        }
        requireNonNegative(simulationTick, "Stage-21H simulation tick");
        if (nextMissionSequence <= 0L) {
            throw new IllegalArgumentException("Next mission sequence must be positive");
        }
        npcs = canonical(npcs, Comparator.naturalOrder(), NpcState::npcId, "NPCs");
        missions = canonical(missions, Comparator.naturalOrder(), MissionContract::missionId, "Missions");
        reputations = canonical(
                reputations,
                Comparator.naturalOrder(),
                value -> value.ownerId() + "\u0000" + value.subjectActorId(),
                "Reputations");
        storyChains = canonical(storyChains, Comparator.naturalOrder(), StoryChainState::chainId, "Story chains");

        Map<String, NpcState> npcById = new HashMap<>();
        for (NpcState npc : npcs) {
            npcById.put(npc.npcId(), npc);
            for (NpcKnowledgeFact fact : npc.knowledge()) {
                if (fact.receivedTick() > simulationTick) {
                    throw new IllegalArgumentException("NPC knowledge is newer than Stage-21H sidecar: " + fact.factId());
                }
            }
        }
        Set<String> missionIds = new HashSet<>();
        long maximumAllocatedMissionSequence = 0L;
        for (MissionContract mission : missions) {
            missionIds.add(mission.missionId());
            maximumAllocatedMissionSequence = Math.max(
                    maximumAllocatedMissionSequence, allocatedMissionSequence(mission.missionId()));
            NpcState issuer = npcById.get(mission.issuerNpcId());
            if (issuer == null) {
                throw new IllegalArgumentException("Mission references missing issuer NPC: " + mission.missionId());
            }
            if (!issuer.factionContentId().equals(mission.issuerFactionId())) {
                throw new IllegalArgumentException("Mission issuer faction differs from persistent NPC affiliation");
            }
            if (mission.createdTick() > simulationTick || mission.statusUpdatedTick() > simulationTick) {
                throw new IllegalArgumentException("Mission is newer than Stage-21H sidecar time");
            }
            for (MissionWakeup wakeup : mission.pendingWakeups()) {
                if (wakeup.observedTick() > simulationTick) {
                    throw new IllegalArgumentException("Mission wakeup is newer than Stage-21H sidecar: " + wakeup.eventId());
                }
            }
            boolean opportunityGrounded = false;
            for (String factId : mission.sourceKnowledgeFactIds()) {
                NpcKnowledgeFact fact = issuer.knowledge().stream()
                        .filter(value -> value.factId().equals(factId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Mission cites fact absent from issuer NPC knowledge: " + factId));
                if (!fact.currentAt(mission.createdTick())) {
                    throw new IllegalArgumentException(
                            "Mission source fact was unavailable or stale at mission creation: " + factId);
                }
                opportunityGrounded |= supportsOpportunity(mission.template(), fact.claimCode());
            }
            if (!opportunityGrounded) {
                throw new IllegalArgumentException(
                        "Mission has no causally compatible issuer-known opportunity fact: " + mission.missionId());
            }
        }
        if (nextMissionSequence <= maximumAllocatedMissionSequence) {
            throw new IllegalArgumentException("Next mission sequence must be above every allocated Stage-21H mission ID");
        }
        for (ReputationState reputation : reputations) {
            for (ReputationEvent event : reputation.events()) {
                if (event.observedTick() > simulationTick) {
                    throw new IllegalArgumentException(
                            "Reputation evidence is newer than Stage-21H sidecar: " + event.eventId());
                }
            }
        }
        for (StoryChainState chain : storyChains) {
            for (String missionId : chain.missionIds()) {
                if (!missionIds.contains(missionId)) {
                    throw new IllegalArgumentException("Story chain references missing mission: " + missionId);
                }
            }
        }
    }

    /**
     * @param simulationTick authoritative starting tick
     * @return empty current-schema Stage-21H state
     */
    public static Stage21HNpcMissionState empty(long simulationTick) {
        requireNonNegative(simulationTick, "Stage-21H simulation tick");
        return new Stage21HNpcMissionState(
                CURRENT_VERSION, simulationTick, 1L, List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Returns the ordinary authority that owns truth for one objective predicate.
     *
     * <p>This read-only vocabulary seam lets downstream authored catalogs bind to the accepted
     * Stage-21H lifecycle without copying its authority matrix.</p>
     *
     * @param kind objective predicate family
     * @return ordinary authority responsible for that predicate
     */
    public static ObjectiveAuthority expectedAuthority(ObjectiveKind kind) {
        return switch (Objects.requireNonNull(kind, "Objective kind not set")) {
            case FREIGHT_ORDER_DELIVERED_KG_AT_LEAST -> ObjectiveAuthority.FREIGHT;
            case FLEET_PRESENT_IN_SYSTEM,
                    FLEET_ABSENT,
                    ESCORT_FLEETS_PRESENT_IN_SYSTEM,
                    FLEET_REACTION_MASS_KG_AT_LEAST -> ObjectiveAuthority.FLEET;
            case DISCOVERY_AT_LEAST -> ObjectiveAuthority.DISCOVERY;
            case DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST -> ObjectiveAuthority.INDUSTRY;
            case CONSTRUCTION_DELIVERED_UNITS_AT_LEAST,
                    CONSTRUCTION_COMPLETED -> ObjectiveAuthority.CONSTRUCTION;
            case MARKET_ACCESS_ALLOWED -> ObjectiveAuthority.DIPLOMACY;
            case OPERATION_STATUS -> ObjectiveAuthority.OPERATION;
            case FACTION_TREASURY_AT_LEAST -> ObjectiveAuthority.ECONOMY;
        };
    }

    private static void validateObjectiveShape(
            ObjectiveAuthority authority,
            ObjectiveKind kind,
            String subjectId,
            long systemId,
            long threshold,
            String requiredState) {
        boolean validParameters = switch (kind) {
            case FREIGHT_ORDER_DELIVERED_KG_AT_LEAST ->
                    systemId == 0L && threshold > 0L && requiredState.isEmpty();
            case FLEET_PRESENT_IN_SYSTEM -> systemId > 0L && threshold == 0L && requiredState.isEmpty();
            case FLEET_ABSENT -> systemId == 0L && threshold == 0L && requiredState.isEmpty();
            case ESCORT_FLEETS_PRESENT_IN_SYSTEM -> systemId > 0L && threshold == 0L && isPositiveLong(subjectId)
                    && isPositiveLong(requiredState) && !subjectId.equals(requiredState);
            case FLEET_REACTION_MASS_KG_AT_LEAST ->
                    systemId == 0L && threshold > 0L && requiredState.isEmpty();
            case DISCOVERY_AT_LEAST -> systemId > 0L && threshold == 0L && !requiredState.isEmpty();
            case DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST ->
                    systemId > 0L && threshold > 0L && requiredState.split(":", -1).length == 2
                    && subjectId.split("\\|", -1).length == 2;
            case CONSTRUCTION_DELIVERED_UNITS_AT_LEAST ->
                    systemId == 0L && threshold > 0L && requiredState.isEmpty();
            case CONSTRUCTION_COMPLETED -> systemId == 0L && threshold == 0L && requiredState.isEmpty();
            case MARKET_ACCESS_ALLOWED, OPERATION_STATUS ->
                    systemId == 0L && threshold == 0L && !requiredState.isEmpty();
            case FACTION_TREASURY_AT_LEAST -> systemId == 0L && requiredState.isEmpty();
        };
        if (authority != expectedAuthority(kind) || !validParameters) {
            throw new IllegalArgumentException("Objective kind is incompatible with its authority/parameters: " + kind);
        }
    }

    /**
     * Requires one authored template/predicate pairing to be instantiable by the Stage-21H lifecycle.
     *
     * @param template accepted runtime mission family
     * @param kind ordinary-authority objective predicate
     */
    public static void validateTemplateObjective(MissionTemplate template, ObjectiveKind kind) {
        Objects.requireNonNull(template, "Mission template not set");
        Objects.requireNonNull(kind, "Objective kind not set");
        boolean valid = switch (template) {
            case EMERGENCY_SUPPLY_DELIVERY, ORDINARY_MARKET_PROCUREMENT ->
                    kind == ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST;
            case CONVOY_ESCORT -> kind == ObjectiveKind.ESCORT_FLEETS_PRESENT_IN_SYSTEM;
            case STRANDED_FLEET_RESCUE_REFUEL -> kind == ObjectiveKind.FLEET_REACTION_MASS_KG_AT_LEAST;
            case SYSTEM_OBJECT_RECONNAISSANCE -> kind == ObjectiveKind.DISCOVERY_AT_LEAST;
            case DERELICT_INVESTIGATION_RECOVERY ->
                    kind == ObjectiveKind.DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST;
            case INTERCEPTION_DEFENSE -> kind == ObjectiveKind.OPERATION_STATUS;
            case CONSTRUCTION_REPAIR_INPUT_DELIVERY ->
                    kind == ObjectiveKind.CONSTRUCTION_DELIVERED_UNITS_AT_LEAST
                            || kind == ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST;
            case IMPERIAL_ACCESS_NEGOTIATION -> kind == ObjectiveKind.MARKET_ACCESS_ALLOWED;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Mission template cannot use the supplied ordinary-authority predicate: " + template + "/" + kind);
        }
    }

    /**
     * Reports whether an NPC role may issue one accepted runtime mission family.
     *
     * <p>This read-only vocabulary seam is the same rule used by the live mission service, so
     * downstream authored catalogs cannot drift into a second issuer-authority matrix.</p>
     *
     * @param role persistent NPC role
     * @param template accepted runtime mission family
     * @return {@code true} when the Stage-21H lifecycle permits the pairing
     */
    public static boolean canIssue(NpcRole role, MissionTemplate template) {
        NpcRole checkedRole = Objects.requireNonNull(role, "NPC role not set");
        MissionTemplate checkedTemplate = Objects.requireNonNull(template, "Mission template not set");
        return switch (checkedRole) {
            case OFFICIAL -> checkedTemplate == MissionTemplate.ORDINARY_MARKET_PROCUREMENT
                    || checkedTemplate == MissionTemplate.CONSTRUCTION_REPAIR_INPUT_DELIVERY
                    || checkedTemplate == MissionTemplate.IMPERIAL_ACCESS_NEGOTIATION;
            case MILITARY -> checkedTemplate == MissionTemplate.CONVOY_ESCORT
                    || checkedTemplate == MissionTemplate.STRANDED_FLEET_RESCUE_REFUEL
                    || checkedTemplate == MissionTemplate.SYSTEM_OBJECT_RECONNAISSANCE
                    || checkedTemplate == MissionTemplate.INTERCEPTION_DEFENSE;
            case TRADE_LOGISTICS -> checkedTemplate == MissionTemplate.EMERGENCY_SUPPLY_DELIVERY
                    || checkedTemplate == MissionTemplate.ORDINARY_MARKET_PROCUREMENT
                    || checkedTemplate == MissionTemplate.CONVOY_ESCORT;
            case INDUSTRY_YARD -> checkedTemplate == MissionTemplate.EMERGENCY_SUPPLY_DELIVERY
                    || checkedTemplate == MissionTemplate.ORDINARY_MARKET_PROCUREMENT
                    || checkedTemplate == MissionTemplate.CONSTRUCTION_REPAIR_INPUT_DELIVERY;
            case EXPLORATION_INTELLIGENCE -> checkedTemplate == MissionTemplate.SYSTEM_OBJECT_RECONNAISSANCE
                    || checkedTemplate == MissionTemplate.DERELICT_INVESTIGATION_RECOVERY
                    || checkedTemplate == MissionTemplate.INTERCEPTION_DEFENSE
                    || checkedTemplate == MissionTemplate.IMPERIAL_ACCESS_NEGOTIATION;
            case INDEPENDENT_FRONTIER -> checkedTemplate == MissionTemplate.EMERGENCY_SUPPLY_DELIVERY
                    || checkedTemplate == MissionTemplate.ORDINARY_MARKET_PROCUREMENT
                    || checkedTemplate == MissionTemplate.STRANDED_FLEET_RESCUE_REFUEL
                    || checkedTemplate == MissionTemplate.DERELICT_INVESTIGATION_RECOVERY;
        };
    }

    private static boolean supportsOpportunity(MissionTemplate template, String claimCode) {
        String claim = requireText(claimCode, "Mission opportunity claim code");
        return switch (template) {
            case EMERGENCY_SUPPLY_DELIVERY -> claim.equals("ECONOMIC.RESOURCE_DEFICIT");
            case ORDINARY_MARKET_PROCUREMENT -> claim.equals("ECONOMIC.SUPPLY_DEPENDENCY")
                    || claim.equals("ECONOMIC.RESOURCE_DEFICIT");
            case CONVOY_ESCORT, STRANDED_FLEET_RESCUE_REFUEL -> claim.equals("SECURITY.ROUTE_EXPOSURE");
            case SYSTEM_OBJECT_RECONNAISSANCE -> claim.equals("DISCOVERY.STATIC_OBJECT")
                    || claim.startsWith("DISCOVERY.");
            case DERELICT_INVESTIGATION_RECOVERY -> claim.equals("DISCOVERY.SPECIAL_LOCATION")
                    || claim.startsWith("DISCOVERY.SPECIAL_LOCATION.");
            case INTERCEPTION_DEFENSE -> claim.equals("SECURITY.BORDER_SECURITY")
                    || claim.equals("SECURITY.ROUTE_EXPOSURE");
            case CONSTRUCTION_REPAIR_INPUT_DELIVERY -> claim.equals("ECONOMIC.RESOURCE_DEFICIT")
                    || claim.equals("ECONOMIC.SUPPLY_DEPENDENCY");
            case IMPERIAL_ACCESS_NEGOTIATION -> claim.equals("DIPLOMATIC.MARKET_ACCESS");
        };
    }

    private static long allocatedMissionSequence(String missionId) {
        if (!missionId.startsWith(MISSION_SEQUENCE_PREFIX)) {
            return 0L;
        }
        String suffix = missionId.substring(MISSION_SEQUENCE_PREFIX.length());
        try {
            long value = Long.parseLong(suffix);
            if (value <= 0L) {
                throw new IllegalArgumentException("Allocated Stage-21H mission sequence must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Malformed allocated Stage-21H mission ID: " + missionId, exception);
        }
    }

    private static boolean isPositiveLong(String value) {
        try {
            return Long.parseLong(value) > 0L;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static List<String> canonicalStrings(List<String> values, String label) {
        ArrayList<String> copy = new ArrayList<>(Objects.requireNonNull(values, label + " not set"));
        for (int i = 0; i < copy.size(); i++) {
            copy.set(i, requireText(copy.get(i), label + " entry"));
        }
        copy.sort(String::compareTo);
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("Duplicate " + label);
        }
        return List.copyOf(copy);
    }

    private static List<String> orderedUniqueStrings(List<String> values, String label) {
        ArrayList<String> copy = new ArrayList<>(Objects.requireNonNull(values, label + " not set"));
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < copy.size(); i++) {
            String value = requireText(copy.get(i), label + " entry");
            copy.set(i, value);
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Duplicate " + label);
            }
        }
        return List.copyOf(copy);
    }

    private static <T> List<T> canonical(
            List<T> values,
            Comparator<T> comparator,
            java.util.function.Function<T, String> identity,
            String label) {
        ArrayList<T> copy = new ArrayList<>(Objects.requireNonNull(values, label + " not set"));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " cannot contain null");
        }
        copy.sort(comparator);
        Set<String> ids = new HashSet<>();
        for (T value : copy) {
            if (!ids.add(identity.apply(value))) {
                throw new IllegalArgumentException("Duplicate " + label + " identity");
            }
        }
        return List.copyOf(copy);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }
}
