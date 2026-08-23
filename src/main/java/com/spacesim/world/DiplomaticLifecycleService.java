package com.spacesim.world;

import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage19ConflictState.ConflictSnapshot;
import com.spacesim.persistence.Stage19ConflictState.ObjectiveSnapshot;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.warfare.StrategicWarPolicyService.EscalationLevel;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveEvidence;
import com.spacesim.world.DiplomaticLifecycleState.Crisis;
import com.spacesim.world.DiplomaticLifecycleState.CrisisEscalation;
import com.spacesim.world.DiplomaticLifecycleState.ObligationDecision;
import com.spacesim.world.DiplomaticLifecycleState.ObligationOutcome;
import com.spacesim.world.DiplomaticLifecycleState.Proposal;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.ProposalStatus;
import com.spacesim.world.DiplomaticLifecycleState.RelationEvent;
import com.spacesim.world.DiplomaticLifecycleState.RelationFactor;
import com.spacesim.world.DiplomaticLifecycleState.RelationMemory;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import com.spacesim.world.DiplomaticLifecycleState.War;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarStartEvidence;
import com.spacesim.world.DiplomaticLifecycleState.WarStartKind;
import com.spacesim.world.DiplomaticLifecycleState.WarStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stage-21C deterministic lifecycle coordinator over existing Stage-17 and Stage-19 authorities.
 *
 * <p>The service stores actor-bounded political memory and legal causality, but treaty/embargo,
 * market access, territorial concessions and warfare consequences remain owned by their existing
 * systems. Monetary negotiation terms are validated against the real faction treasury at offer and
 * acceptance time; Stage 21C records the promise but does not invent an escrow wallet or duplicate
 * money. Actual reparations/payment execution belongs to the settlement/recovery stage.</p>
 */
public final class DiplomaticLifecycleService {
    /** Minimum default post-ceasefire/peace cooldown used by callers that do not define a larger one. */
    public static final long MINIMUM_REESCALATION_COOLDOWN_TICKS = 600L;

    private final WorldSimulation world;
    private final Stage19ConflictRuntime warfare;
    private long nextProposalSequence;
    private long nextCrisisSequence;
    private long nextWarSequence;
    private final List<RelationMemory> relationMemories;
    private final List<Proposal> proposals;
    private final List<Crisis> crises;
    private final List<War> wars;
    private final List<ObligationDecision> obligationDecisions;
    private long lastLifecycleTick;

    /**
     * Restores a Stage-21C coordinator from persistent state.
     *
     * @param world existing authoritative ordinary world
     * @param warfare existing Stage-19 conflict authority
     * @param state persistent Stage-21C lifecycle sidecar
     */
    public DiplomaticLifecycleService(
            WorldSimulation world,
            Stage19ConflictRuntime warfare,
            DiplomaticLifecycleState state) {
        this.world = Objects.requireNonNull(world, "WorldSimulation not set");
        this.warfare = Objects.requireNonNull(warfare, "Stage19ConflictRuntime not set");
        DiplomaticLifecycleState checked = Objects.requireNonNull(state, "DiplomaticLifecycleState not set");
        nextProposalSequence = checked.nextProposalSequence();
        nextCrisisSequence = checked.nextCrisisSequence();
        nextWarSequence = checked.nextWarSequence();
        relationMemories = new ArrayList<>(checked.relationMemories());
        proposals = new ArrayList<>(checked.proposals());
        crises = new ArrayList<>(checked.crises());
        wars = new ArrayList<>(checked.wars());
        obligationDecisions = new ArrayList<>(checked.obligationDecisions());
        lastLifecycleTick = checked.simulationTick();
        validateRestoredAuthorityReferences();
    }

    /**
     * Captures the current deterministic lifecycle sidecar.
     *
     * @return immutable current-schema state
     */
    public DiplomaticLifecycleState snapshot() {
        long tick = Math.max(lastLifecycleTick, world.getAuthoritativeWorldTick());
        return new DiplomaticLifecycleState(
                DiplomaticLifecycleState.CURRENT_VERSION,
                tick,
                nextProposalSequence,
                nextCrisisSequence,
                nextWarSequence,
                relationMemories,
                proposals,
                crises,
                wars,
                obligationDecisions);
    }

    /**
     * Records one actor-known relationship event without reading hidden world truth.
     *
     * @param ownerFactionId remembering actor
     * @param targetFactionId remembered counterparty
     * @param event explicit actor-known evidence
     * @return updated directed relation memory
     */
    public RelationMemory remember(
            String ownerFactionId,
            String targetFactionId,
            RelationEvent event) {
        String owner = requireFaction(ownerFactionId);
        String target = requireFaction(targetFactionId);
        if (owner.equals(target)) {
            throw new IllegalArgumentException("Faction cannot remember itself as a diplomatic counterparty");
        }
        RelationEvent checked = Objects.requireNonNull(event, "RelationEvent not set");
        long now = world.getAuthoritativeWorldTick();
        if (checked.observedTick() > now) {
            throw new IllegalArgumentException("Diplomatic memory cannot come from the future");
        }
        int index = relationMemoryIndex(owner, target);
        List<RelationEvent> events = index < 0
                ? new ArrayList<>()
                : new ArrayList<>(relationMemories.get(index).events());
        if (events.stream().anyMatch(existing -> existing.eventId().equals(checked.eventId()))) {
            throw new IllegalArgumentException("Diplomatic memory event already exists: " + checked.eventId());
        }
        events.add(checked);
        RelationMemory updated = new RelationMemory(owner, target, events);
        if (index < 0) relationMemories.add(updated); else relationMemories.set(index, updated);
        relationMemories.sort(Comparator.naturalOrder());
        touch(now);
        return updated;
    }

    /**
     * Reads the current actor-bounded relationship assessment derived from remembered factors.
     *
     * @param ownerFactionId remembering actor
     * @param targetFactionId counterparty
     * @return clamped assessment in [-100,100], or zero when no evidence exists
     */
    public int derivedRelation(String ownerFactionId, String targetFactionId) {
        String owner = requireFaction(ownerFactionId);
        String target = requireFaction(targetFactionId);
        int index = relationMemoryIndex(owner, target);
        return index < 0 ? 0 : relationMemories.get(index).derivedRelation();
    }

    /**
     * Opens a bounded proposal after validating real treasury and territorial term feasibility.
     *
     * @param request immutable proposal request derived from Stage-21B intent or player command
     * @return persistent open proposal
     */
    public Proposal propose(ProposalRequest request) {
        ProposalRequest checked = Objects.requireNonNull(request, "ProposalRequest not set");
        String proposer = requireFaction(checked.proposerFactionId());
        String recipient = requireFaction(checked.recipientFactionId());
        if (proposer.equals(recipient)) {
            throw new IllegalArgumentException("Faction cannot negotiate with itself");
        }
        long now = world.getAuthoritativeWorldTick();
        if (checked.deadlineTick() <= now) {
            throw new IllegalArgumentException("Proposal deadline must be in the future");
        }
        validateTermsForGrantor(proposer, checked.concessions());
        validateTermsForGrantor(recipient, checked.demands());
        Proposal proposal = new Proposal(
                "proposal." + nextProposalSequence++,
                checked.sourceGoalId(),
                proposer,
                recipient,
                checked.kind(),
                checked.issueId(),
                checked.demands(),
                checked.concessions(),
                now,
                checked.deadlineTick(),
                now,
                ProposalStatus.OPEN,
                "",
                "");
        proposals.add(proposal);
        proposals.sort(Comparator.naturalOrder());
        touch(now);
        return proposal;
    }

    /**
     * Materializes the treaty-bearing portion of an open proposal through the Stage-17 command boundary.
     *
     * <p>Proposal families without a Stage-17 treaty representation remain pure Stage-21C legal offers.
     * Recognition, construction concessions, embargoes and war status have their own existing commands.</p>
     *
     * @param proposalId persistent proposal identity
     * @return updated proposal, possibly linked to a Stage-17 treaty
     */
    public Proposal materializeTreatyOffer(String proposalId) {
        int index = requireOpenProposalIndex(proposalId);
        Proposal current = proposals.get(index);
        if (!current.linkedTreatyId().isEmpty()) {
            return current;
        }
        List<DiplomaticTreatyClauseState> clauses = treatyClauses(current);
        if (clauses.isEmpty()) {
            return current;
        }
        DiplomaticTreatyCommandResult result = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Offer(
                        current.proposerFactionId(),
                        current.recipientFactionId(),
                        clauses,
                        current.deadlineTick()));
        long now = world.getAuthoritativeWorldTick();
        Proposal updated = copyProposal(
                current,
                current.status(),
                now,
                current.linkedCrisisId(),
                result.treaty().treatyId());
        proposals.set(index, updated);
        touch(now);
        return updated;
    }

    /**
     * Accepts a proposal and delegates every currently executable legal effect to its existing authority.
     *
     * <p>Real treasury-payment terms are revalidated here but intentionally remain settlement promises;
     * Stage 21G owns actual reparations transfers. No money is created, reserved or destroyed.</p>
     *
     * @param proposalId persistent proposal identity
     * @return accepted persistent proposal
     */
    public Proposal accept(String proposalId) {
        int index = requireOpenProposalIndex(proposalId);
        Proposal current = proposals.get(index);
        long now = world.getAuthoritativeWorldTick();
        validateTermsForGrantor(current.proposerFactionId(), current.concessions());
        validateTermsForGrantor(current.recipientFactionId(), current.demands());

        if (current.linkedTreatyId().isEmpty() && !treatyClauses(current).isEmpty()) {
            current = materializeTreatyOffer(current.proposalId());
            index = proposalIndex(current.proposalId());
        }
        if (!current.linkedTreatyId().isEmpty()) {
            world.applyDiplomaticTreatyCommand(
                    new DiplomaticTreatyCommand.Accept(
                            current.recipientFactionId(), current.linkedTreatyId()));
        }
        executeNonMonetaryTerms(current.proposerFactionId(), current.recipientFactionId(), current.concessions());
        executeNonMonetaryTerms(current.recipientFactionId(), current.proposerFactionId(), current.demands());
        if (current.kind() == ProposalKind.CEASEFIRE) {
            setWarStatus(current.issueId(), WarStatus.CEASEFIRE, MINIMUM_REESCALATION_COOLDOWN_TICKS);
        } else if (current.kind() == ProposalKind.PEACE) {
            setWarStatus(current.issueId(), WarStatus.PEACE, MINIMUM_REESCALATION_COOLDOWN_TICKS);
        }

        Proposal accepted = copyProposal(
                current, ProposalStatus.ACCEPTED, now, current.linkedCrisisId(), current.linkedTreatyId());
        proposals.set(index, accepted);
        resolveLinkedCrisis(current.linkedCrisisId(), "proposal-accepted:" + current.proposalId(), now);
        touch(now);
        return accepted;
    }

    /**
     * Rejects an open proposal without applying its promised terms.
     *
     * @param proposalId proposal identity
     * @return rejected proposal
     */
    public Proposal reject(String proposalId) {
        int index = requireOpenProposalIndex(proposalId);
        Proposal current = proposals.get(index);
        long now = world.getAuthoritativeWorldTick();
        if (!current.linkedTreatyId().isEmpty()) {
            world.applyDiplomaticTreatyCommand(
                    new DiplomaticTreatyCommand.Reject(
                            current.recipientFactionId(), current.linkedTreatyId()));
        }
        Proposal rejected = copyProposal(
                current, ProposalStatus.REJECTED, now, current.linkedCrisisId(), current.linkedTreatyId());
        proposals.set(index, rejected);
        touch(now);
        return rejected;
    }

    /**
     * Expires every open proposal whose deadline has elapsed.
     *
     * @return number of proposals transitioned to EXPIRED
     */
    public int expireDueProposals() {
        long now = world.getAuthoritativeWorldTick();
        int changed = 0;
        for (int index = 0; index < proposals.size(); index++) {
            Proposal current = proposals.get(index);
            if (current.status() == ProposalStatus.OPEN && current.deadlineTick() <= now) {
                proposals.set(index, copyProposal(
                        current, ProposalStatus.EXPIRED, now, current.linkedCrisisId(), current.linkedTreatyId()));
                changed++;
            }
        }
        if (changed > 0) touch(now);
        return changed;
    }

    /**
     * Opens a persistent crisis from an existing proposal or explicit actor-known cause.
     *
     * @param proposalId causal proposal identity
     * @param decisionEvidenceId actor-known decision/evidence identity
     * @param deadlineTick next crisis deadline
     * @return newly persistent crisis
     */
    public Crisis openCrisis(String proposalId, String decisionEvidenceId, long deadlineTick) {
        int proposalIndex = requireProposalIndex(proposalId);
        Proposal proposal = proposals.get(proposalIndex);
        if (!proposal.linkedCrisisId().isEmpty()) {
            return requireCrisis(proposal.linkedCrisisId());
        }
        long now = world.getAuthoritativeWorldTick();
        if (deadlineTick <= now) {
            throw new IllegalArgumentException("Crisis deadline must be in the future");
        }
        Crisis crisis = new Crisis(
                "crisis." + nextCrisisSequence++,
                proposal.proposerFactionId(),
                proposal.recipientFactionId(),
                proposal.issueId(),
                proposal.demands(),
                proposal.concessions(),
                deadlineTick,
                CrisisEscalation.NEGOTIATION,
                proposal.proposalId(),
                requireText(decisionEvidenceId, "Crisis decision evidence"),
                now,
                now);
        crises.add(crisis);
        crises.sort(Comparator.naturalOrder());
        proposals.set(proposalIndex, copyProposal(
                proposal, proposal.status(), now, crisis.crisisId(), proposal.linkedTreatyId()));
        touch(now);
        return crisis;
    }

    /**
     * Advances one crisis by exactly one legal escalation step from explicit evidence/decision.
     *
     * @param crisisId crisis identity
     * @param decisionEvidenceId non-random causal decision/evidence identity
     * @param nextDeadlineTick future deadline for the new step
     * @return updated crisis
     */
    public Crisis escalateCrisis(
            String crisisId,
            String decisionEvidenceId,
            long nextDeadlineTick) {
        int index = requireCrisisIndex(crisisId);
        Crisis current = crises.get(index);
        if (current.escalation() == CrisisEscalation.RESOLVED
                || current.escalation() == CrisisEscalation.WAR_AUTHORIZED) {
            throw new IllegalStateException("Terminal crisis escalation cannot advance: " + crisisId);
        }
        long now = world.getAuthoritativeWorldTick();
        if (nextDeadlineTick <= now) {
            throw new IllegalArgumentException("Escalated crisis requires a future deadline");
        }
        CrisisEscalation next = switch (current.escalation()) {
            case NEGOTIATION -> CrisisEscalation.PRESSURE;
            case PRESSURE -> CrisisEscalation.ULTIMATUM;
            case ULTIMATUM -> CrisisEscalation.WAR_AUTHORIZED;
            case WAR_AUTHORIZED, RESOLVED -> throw new IllegalStateException("Crisis cannot escalate further");
        };
        Crisis updated = new Crisis(
                current.crisisId(),
                current.initiatorFactionId(),
                current.targetFactionId(),
                current.issueId(),
                current.demands(),
                current.concessions(),
                nextDeadlineTick,
                next,
                current.causalProposalId(),
                requireText(decisionEvidenceId, "Crisis escalation evidence"),
                current.createdTick(),
                now);
        crises.set(index, updated);
        touch(now);
        return updated;
    }

    /**
     * Applies a market-access embargo through Stage 17 as a coercive crisis action.
     *
     * @param crisisId causal crisis
     * @param actorFactionId embargoing crisis participant
     * @param expiresTick embargo expiry or -1 for indefinite
     * @return authoritative Stage-17 embargo transition
     */
    public DiplomaticEmbargoCommandResult imposeEmbargo(
            String crisisId,
            String actorFactionId,
            long expiresTick) {
        Crisis crisis = requireCrisis(crisisId);
        String actor = requireFaction(actorFactionId);
        if (!crisis.includes(actor) || crisis.escalation() == CrisisEscalation.NEGOTIATION
                || crisis.escalation() == CrisisEscalation.RESOLVED) {
            throw new IllegalStateException("Embargo requires an unresolved coercive crisis participant");
        }
        String target = actor.equals(crisis.initiatorFactionId())
                ? crisis.targetFactionId() : crisis.initiatorFactionId();
        DiplomaticEmbargoCommandResult result = world.applyDiplomaticEmbargoCommand(
                new DiplomaticEmbargoCommand.Impose(actor, target, expiresTick, "stage21c:" + crisis.crisisId()));
        remember(
                target,
                actor,
                new RelationEvent(
                        "memory.embargo." + crisis.crisisId() + "." + actor,
                        RelationFactor.REMEMBERED_ACTION,
                        -25,
                        world.getAuthoritativeWorldTick(),
                        crisis.issueId()));
        return result;
    }

    /**
     * Declares a war only from a persisted WAR_AUTHORIZED crisis.
     *
     * @param crisisId causal persisted crisis
     * @param goals explicit objectives for both participants
     * @return legal war identity linked to two Stage-19 actor-perspective conflicts
     */
    public War declareWarFromCrisis(String crisisId, List<WarGoal> goals) {
        Crisis crisis = requireCrisis(crisisId);
        if (crisis.escalation() != CrisisEscalation.WAR_AUTHORIZED) {
            throw new IllegalStateException("War requires a persisted WAR_AUTHORIZED crisis");
        }
        return declareWar(
                crisis.initiatorFactionId(),
                crisis.targetFactionId(),
                goals,
                new WarStartEvidence(
                        WarStartKind.CRISIS_DECISION,
                        crisis.decisionEvidenceId(),
                        crisis.updatedTick(),
                        crisis.crisisId()));
    }

    /**
     * Declares a war from explicit actor-observed hostile-attack evidence without inventing a crisis.
     *
     * @param firstFactionId first participant
     * @param secondFactionId second participant
     * @param hostileAttackEvidenceId stable observed attack evidence identity
     * @param evidenceTick actor observation tick
     * @param goals explicit objectives for both participants
     * @return legal war identity linked to Stage-19 conflict authority
     */
    public War declareWarFromObservedAttack(
            String firstFactionId,
            String secondFactionId,
            String hostileAttackEvidenceId,
            long evidenceTick,
            List<WarGoal> goals) {
        long now = world.getAuthoritativeWorldTick();
        if (evidenceTick < 0L || evidenceTick > now) {
            throw new IllegalArgumentException("Observed hostile attack evidence tick is invalid");
        }
        return declareWar(
                requireFaction(firstFactionId),
                requireFaction(secondFactionId),
                goals,
                new WarStartEvidence(
                        WarStartKind.OBSERVED_HOSTILE_ATTACK,
                        requireText(hostileAttackEvidenceId, "Hostile attack evidence ID"),
                        evidenceTick,
                        ""));
    }

    /**
     * Transitions an active war to ceasefire with mandatory hysteresis.
     *
     * @param warId legal war identity
     * @param cooldownTicks requested re-escalation cooldown
     * @return ceasefire state
     */
    public War ceasefire(String warId, long cooldownTicks) {
        return setWarStatus(warId, WarStatus.CEASEFIRE, cooldownTicks);
    }

    /**
     * Transitions an active/ceasefire war to peace with mandatory hysteresis.
     *
     * @param warId legal war identity
     * @param cooldownTicks requested post-war cooldown
     * @return peace state
     */
    public War peace(String warId, long cooldownTicks) {
        return setWarStatus(warId, WarStatus.PEACE, cooldownTicks);
    }

    /**
     * Evaluates an active guarantee obligation. Refusal remains legal but breaches the Stage-17 treaty
     * and therefore receives the existing grievance/trust/credibility consequences plus remembered
     * Stage-21C reputation evidence.
     *
     * @param treatyId active Stage-17 treaty
     * @param obligatedFactionId faction expected to honor the guarantee
     * @param beneficiaryFactionId protected faction
     * @param threatEvidenceId actor-known trigger evidence
     * @param canHonor whether caller-owned physical/political feasibility permits honoring now
     * @return persistent obligation decision
     */
    public ObligationDecision evaluateObligation(
            String treatyId,
            String obligatedFactionId,
            String beneficiaryFactionId,
            String threatEvidenceId,
            boolean canHonor) {
        String obligated = requireFaction(obligatedFactionId);
        String beneficiary = requireFaction(beneficiaryFactionId);
        TreatyOwner treatyOwner = requireTreatyOwner(treatyId);
        DiplomaticTreatyState treaty = treatyOwner.treaty();
        long now = world.getAuthoritativeWorldTick();
        if (!treaty.activeAt(now)) {
            throw new IllegalStateException("Treaty obligation is not active: " + treatyId);
        }
        if (!isGuaranteeObligation(treatyOwner.ownerFactionId(), treaty, obligated, beneficiary)) {
            throw new IllegalStateException("Treaty does not obligate this faction toward the beneficiary");
        }
        ObligationOutcome outcome = canHonor ? ObligationOutcome.HONORED : ObligationOutcome.REFUSED;
        int reputation = canHonor ? 12 : -35;
        String decisionId = "obligation." + (obligationDecisions.size() + 1L);
        ObligationDecision decision = new ObligationDecision(
                decisionId,
                treaty.treatyId(),
                obligated,
                beneficiary,
                requireText(threatEvidenceId, "Obligation threat evidence"),
                outcome,
                reputation,
                now);
        if (!canHonor) {
            world.applyDiplomaticTreatyCommand(
                    new DiplomaticTreatyCommand.Breach(
                            obligated, treaty.treatyId(), "stage21c-obligation-refusal"));
        }
        remember(
                beneficiary,
                obligated,
                new RelationEvent(
                        "memory." + decisionId,
                        RelationFactor.DIPLOMATIC_COMMITMENT,
                        reputation,
                        now,
                        treaty.treatyId()));
        obligationDecisions.add(decision);
        obligationDecisions.sort(Comparator.naturalOrder());
        touch(now);
        return decision;
    }

    /**
     * Selects a broad diplomatic outcome from substantive actor-bounded factors.
     *
     * <p>The tie-breaker is consulted only when peaceful alternatives have exactly equal scores.
     * It can never produce WAR; war requires explicit hostile/crisis predicates.</p>
     *
     * @param situation actor-bounded strategic situation
     * @param boundedTieBreaker arbitrary deterministic tie-break input
     * @return explainable broad outcome family
     */
    public static StrategicOutcome selectOutcome(DiplomaticSituation situation, long boundedTieBreaker) {
        DiplomaticSituation input = Objects.requireNonNull(situation, "DiplomaticSituation not set");
        if (input.crisisEscalation() == CrisisEscalation.WAR_AUTHORIZED
                && input.threatBasisPoints() >= 7_000
                && input.relation() <= -40
                && !input.credibleSettlementOffer()) {
            return StrategicOutcome.WAR;
        }
        if (input.credibleSettlementOffer()
                && (input.tradeDependenceBasisPoints() >= 4_000
                || input.commitmentBasisPoints() >= 4_000)) {
            return StrategicOutcome.NEGOTIATED_RESOLUTION;
        }
        if (input.threatBasisPoints() >= 4_000 && input.commitmentBasisPoints() >= 5_000) {
            return StrategicOutcome.DETERRENCE;
        }
        if (input.tradeDependenceBasisPoints() >= 5_000 && input.relation() >= 0) {
            return StrategicOutcome.TRADE;
        }
        int tradeScore = input.tradeDependenceBasisPoints() + Math.max(0, input.relation()) * 50;
        int deterrenceScore = input.threatBasisPoints() + input.commitmentBasisPoints();
        if (tradeScore == deterrenceScore) {
            return (boundedTieBreaker & 1L) == 0L ? StrategicOutcome.TRADE : StrategicOutcome.DETERRENCE;
        }
        return tradeScore > deterrenceScore ? StrategicOutcome.TRADE : StrategicOutcome.DETERRENCE;
    }

    /** Broad Stage-21C outcomes used by representative-corpus acceptance. */
    public enum StrategicOutcome {
        /** Cooperative trade/access path. */ TRADE,
        /** Credible security posture avoids war. */ DETERRENCE,
        /** Crisis ends through accepted terms. */ NEGOTIATED_RESOLUTION,
        /** Persisted causal escalation reaches legal war. */ WAR
    }

    /**
     * Actor-bounded inputs for broad outcome selection.
     *
     * @param relation remembered assessment in [-100,100]
     * @param tradeDependenceBasisPoints observed trade dependence in [0,10000]
     * @param threatBasisPoints observed threat in [0,10000]
     * @param commitmentBasisPoints observed alliance/commitment strength in [0,10000]
     * @param crisisEscalation current persisted crisis posture
     * @param credibleSettlementOffer whether an actual bounded offer is visible
     */
    public record DiplomaticSituation(
            int relation,
            int tradeDependenceBasisPoints,
            int threatBasisPoints,
            int commitmentBasisPoints,
            CrisisEscalation crisisEscalation,
            boolean credibleSettlementOffer) {
        /**
         * Validates bounded actor-visible diplomatic inputs.
         *
         * @param relation remembered assessment in [-100,100]
         * @param tradeDependenceBasisPoints observed trade dependence in [0,10000]
         * @param threatBasisPoints observed threat in [0,10000]
         * @param commitmentBasisPoints observed alliance/commitment strength in [0,10000]
         * @param crisisEscalation current persisted crisis posture
         * @param credibleSettlementOffer whether an actual bounded offer is visible
         */
        public DiplomaticSituation {
            if (relation < -100 || relation > 100) {
                throw new IllegalArgumentException("Diplomatic relation must be in [-100,100]");
            }
            requireBasisPoints(tradeDependenceBasisPoints, "trade dependence");
            requireBasisPoints(threatBasisPoints, "threat");
            requireBasisPoints(commitmentBasisPoints, "commitment");
            Objects.requireNonNull(crisisEscalation, "Crisis escalation not set");
        }
    }

    /**
     * Immutable request for a new proposal.
     *
     * @param sourceGoalId persistent Stage-21B goal or stable player-command identity
     * @param proposerFactionId proposer
     * @param recipientFactionId recipient
     * @param kind proposal family
     * @param issueId stable issue/target identity
     * @param demands terms requested from recipient
     * @param concessions terms offered by proposer
     * @param deadlineTick future response deadline
     */
    public record ProposalRequest(
            String sourceGoalId,
            String proposerFactionId,
            String recipientFactionId,
            ProposalKind kind,
            String issueId,
            List<Term> demands,
            List<Term> concessions,
            long deadlineTick) {
        /**
         * Validates immutable proposal request syntax before authority checks.
         *
         * @param sourceGoalId persistent Stage-21B goal or stable player-command identity
         * @param proposerFactionId proposer
         * @param recipientFactionId recipient
         * @param kind proposal family
         * @param issueId stable issue/target identity
         * @param demands terms requested from recipient
         * @param concessions terms offered by proposer
         * @param deadlineTick future response deadline
         */
        public ProposalRequest {
            sourceGoalId = requireText(sourceGoalId, "Proposal source goal ID");
            proposerFactionId = requireText(proposerFactionId, "Proposal proposer");
            recipientFactionId = requireText(recipientFactionId, "Proposal recipient");
            Objects.requireNonNull(kind, "Proposal kind not set");
            issueId = requireText(issueId, "Proposal issue");
            demands = List.copyOf(Objects.requireNonNull(demands, "Proposal demands not set"));
            concessions = List.copyOf(Objects.requireNonNull(concessions, "Proposal concessions not set"));
            if (demands.stream().anyMatch(Objects::isNull) || concessions.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Proposal terms cannot contain null");
            }
            if (deadlineTick < 0L) {
                throw new IllegalArgumentException("Proposal deadline cannot be negative");
            }
        }
    }

    private War declareWar(
            String firstFactionId,
            String secondFactionId,
            List<WarGoal> goals,
            WarStartEvidence evidence) {
        String first = requireFaction(firstFactionId);
        String second = requireFaction(secondFactionId);
        if (first.equals(second)) {
            throw new IllegalArgumentException("War participants must differ");
        }
        String factionA = first.compareTo(second) < 0 ? first : second;
        String factionB = first.compareTo(second) < 0 ? second : first;
        List<WarGoal> checkedGoals = List.copyOf(Objects.requireNonNull(goals, "War goals not set"));
        long now = world.getAuthoritativeWorldTick();
        if (evidence.observedTick() > now) {
            throw new IllegalArgumentException("War cause cannot come from the future");
        }
        for (War existing : wars) {
            if (!existing.matchesPair(factionA, factionB)) continue;
            if (existing.status() == WarStatus.ACTIVE) {
                throw new IllegalStateException("An active legal war already exists for this faction pair");
            }
            if (now < existing.reEscalationCooldownUntilTick()) {
                throw new IllegalStateException("War re-escalation cooldown remains active for this faction pair");
            }
        }
        if (evidence.kind() == WarStartKind.CRISIS_DECISION) {
            Crisis crisis = requireCrisis(evidence.crisisId());
            if (crisis.escalation() != CrisisEscalation.WAR_AUTHORIZED
                    || !crisis.includes(factionA) || !crisis.includes(factionB)) {
                throw new IllegalStateException("Causal crisis does not authorize this war");
            }
        }
        Map<String, List<WarGoal>> byClaimant = new HashMap<>();
        for (WarGoal goal : checkedGoals) {
            byClaimant.computeIfAbsent(goal.claimantFactionId(), ignored -> new ArrayList<>()).add(goal);
        }
        if (!byClaimant.containsKey(factionA) || !byClaimant.containsKey(factionB)) {
            throw new IllegalArgumentException("Each war participant requires at least one explicit goal");
        }

        String warId = "war." + nextWarSequence++;
        String conflictA = warId + ":" + factionA;
        String conflictB = warId + ":" + factionB;
        warfare.add(ConflictSnapshot.active(
                conflictA,
                factionA,
                factionB,
                EscalationLevel.LIMITED_WAR,
                stage19Objectives(byClaimant.get(factionA))), now);
        warfare.add(ConflictSnapshot.active(
                conflictB,
                factionB,
                factionA,
                EscalationLevel.LIMITED_WAR,
                stage19Objectives(byClaimant.get(factionB))), now);
        War war = new War(
                warId,
                factionA,
                factionB,
                checkedGoals,
                evidence,
                List.of(conflictA, conflictB),
                WarStatus.ACTIVE,
                now,
                now,
                0L);
        wars.add(war);
        wars.sort(Comparator.naturalOrder());
        touch(now);
        return war;
    }

    private War setWarStatus(String warId, WarStatus status, long cooldownTicks) {
        int index = requireWarIndex(warId);
        War current = wars.get(index);
        WarStatus next = Objects.requireNonNull(status, "War status not set");
        if (current.status() == WarStatus.PEACE) {
            throw new IllegalStateException("Peace is terminal for an existing legal war identity");
        }
        if (next == WarStatus.ACTIVE) {
            throw new IllegalArgumentException("Use a new causal declaration to re-escalate war");
        }
        if (cooldownTicks < MINIMUM_REESCALATION_COOLDOWN_TICKS) {
            throw new IllegalArgumentException("Re-escalation cooldown is below the Stage-21C minimum");
        }
        long now = world.getAuthoritativeWorldTick();
        War updated = new War(
                current.warId(),
                current.factionA(),
                current.factionB(),
                current.goals(),
                current.startEvidence(),
                current.stage19ConflictIds(),
                next,
                current.startedTick(),
                now,
                Math.addExact(now, cooldownTicks));
        wars.set(index, updated);
        touch(now);
        return updated;
    }

    private void executeNonMonetaryTerms(String grantor, String grantee, List<Term> terms) {
        for (Term term : terms) {
            switch (term.kind()) {
                case CONSTRUCTION_RIGHT -> world.grantTerritorialConstructionRight(
                        grantor, grantee, system(term.subjectId()), -1L);
                case TERRITORIAL_RECOGNITION -> executeRecognition(grantor, grantee, system(term.subjectId()));
                case EMBARGO_RELIEF -> {
                    FactionDiplomacyState state = world.findFactionDiplomacyState(grantor).orElseThrow();
                    if (state.hasActiveMarketEmbargoAgainst(grantee, world.getAuthoritativeWorldTick())) {
                        world.applyDiplomaticEmbargoCommand(new DiplomaticEmbargoCommand.Revoke(grantor, grantee));
                    }
                }
                case TREASURY_PAYMENT, MARKET_ACCESS, CUSTOMS_TARIFF_EXEMPTION,
                        NON_AGGRESSION, GUARANTEE, ALLIANCE, WAR_SETTLEMENT -> {
                    // Money is a bounded promise for Stage 21G; treaty/legal terms are handled elsewhere.
                }
            }
        }
    }

    private void executeRecognition(String recognizer, String target, StarSystemId systemId) {
        if (world.controllingFaction(systemId).filter(target::equals).isPresent()) {
            world.recognizeTerritorialControl(recognizer, target, systemId);
            return;
        }
        world.recognizeTerritorialClaim(recognizer, target, systemId);
    }

    private List<DiplomaticTreatyClauseState> treatyClauses(Proposal proposal) {
        List<DiplomaticTreatyClauseState> clauses = new ArrayList<>();
        addTermClauses(clauses, proposal.concessions(), DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY);
        addTermClauses(clauses, proposal.demands(), DiplomaticTreatyClauseState.Direction.COUNTERPARTY_TO_OWNER);
        if (clauses.isEmpty()) {
            switch (proposal.kind()) {
                case ACCESS -> clauses.add(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                        DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY,
                        null));
                case TRADE -> {
                    clauses.add(new DiplomaticTreatyClauseState(
                            DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                            DiplomaticTreatyClauseState.Direction.MUTUAL,
                            null));
                    clauses.add(new DiplomaticTreatyClauseState(
                            DiplomaticTreatyClauseState.Kind.CUSTOMS_TARIFF_EXEMPTION,
                            DiplomaticTreatyClauseState.Direction.MUTUAL,
                            null));
                }
                case DEFENSIVE_COOPERATION, ALLIANCE -> clauses.add(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.GUARANTEE,
                        DiplomaticTreatyClauseState.Direction.MUTUAL,
                        null));
                case RECOGNITION, CONSTRUCTION_RIGHTS, NON_AGGRESSION, EMBARGO, ULTIMATUM, CEASEFIRE, PEACE -> {
                    // These families use dedicated Stage-17 territory/embargo or Stage-21C legal state.
                }
            }
        }
        return List.copyOf(clauses);
    }

    private static void addTermClauses(
            List<DiplomaticTreatyClauseState> clauses,
            List<Term> terms,
            DiplomaticTreatyClauseState.Direction direction) {
        for (Term term : terms) {
            switch (term.kind()) {
                case MARKET_ACCESS -> clauses.add(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.MARKET_ACCESS, direction, null));
                case CUSTOMS_TARIFF_EXEMPTION -> clauses.add(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.CUSTOMS_TARIFF_EXEMPTION, direction, null));
                case CONSTRUCTION_RIGHT -> clauses.add(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.CONSTRUCTION_RIGHT, direction, system(term.subjectId())));
                case GUARANTEE, ALLIANCE -> clauses.add(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.GUARANTEE, direction, null));
                case TREASURY_PAYMENT, TERRITORIAL_RECOGNITION, NON_AGGRESSION, EMBARGO_RELIEF, WAR_SETTLEMENT -> {
                    // Not represented by an existing Stage-17 treaty clause.
                }
            }
        }
    }

    private void validateTermsForGrantor(String grantor, List<Term> terms) {
        requireFaction(grantor);
        long requestedMoney = 0L;
        for (Term term : Objects.requireNonNull(terms, "Negotiation terms not set")) {
            Term checked = Objects.requireNonNull(term, "Negotiation term not set");
            switch (checked.kind()) {
                case TREASURY_PAYMENT -> requestedMoney = Math.addExact(requestedMoney, checked.amountMilliCredits());
                case CONSTRUCTION_RIGHT -> {
                    StarSystemId system = system(checked.subjectId());
                    String controller = world.controllingFaction(system).orElse(null);
                    if (!grantor.equals(controller)) {
                        throw new IllegalStateException(
                                "Construction-right offer exceeds grantor territorial authority: " + system);
                    }
                }
                case TERRITORIAL_RECOGNITION -> {
                    StarSystemId system = system(checked.subjectId());
                    if (world.getTopology().findSystem(system).isEmpty()) {
                        throw new IllegalArgumentException("Recognition term references unknown system: " + system);
                    }
                }
                case MARKET_ACCESS, CUSTOMS_TARIFF_EXEMPTION, NON_AGGRESSION,
                        GUARANTEE, ALLIANCE, EMBARGO_RELIEF, WAR_SETTLEMENT -> {
                    // No physical resource is consumed at proposal time.
                }
            }
        }
        if (requestedMoney > 0L) {
            FactionEconomicState economy = world.findFactionEconomicState(grantor).orElseThrow(
                    () -> new IllegalArgumentException("Faction lacks treasury authority: " + grantor));
            long spendable = Math.max(0L,
                    economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits());
            if (requestedMoney > spendable) {
                throw new IllegalStateException(
                        "Negotiation treasury concession exceeds real spendable treasury: " + grantor);
            }
        }
    }

    private boolean isGuaranteeObligation(
            String owner,
            DiplomaticTreatyState treaty,
            String obligated,
            String beneficiary) {
        String counterparty = treaty.counterpartyFactionContentId();
        if (!(owner.equals(obligated) && counterparty.equals(beneficiary)
                || owner.equals(beneficiary) && counterparty.equals(obligated))) {
            return false;
        }
        for (DiplomaticTreatyClauseState clause : treaty.clauses()) {
            if (clause.kind() != DiplomaticTreatyClauseState.Kind.GUARANTEE) continue;
            if (clause.direction() == DiplomaticTreatyClauseState.Direction.MUTUAL) return true;
            if (owner.equals(obligated)
                    && clause.direction() == DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY) return true;
            if (counterparty.equals(obligated)
                    && clause.direction() == DiplomaticTreatyClauseState.Direction.COUNTERPARTY_TO_OWNER) return true;
        }
        return false;
    }

    private TreatyOwner requireTreatyOwner(String treatyId) {
        String id = requireText(treatyId, "Treaty ID");
        for (FactionDiplomacyState state : world.getFactionDiplomacyStates()) {
            for (DiplomaticTreatyState treaty : state.treaties()) {
                if (treaty.treatyId().equals(id)) {
                    return new TreatyOwner(state.factionContentId(), treaty);
                }
            }
        }
        throw new IllegalArgumentException("Unknown treaty: " + id);
    }

    private void validateRestoredAuthorityReferences() {
        for (RelationMemory memory : relationMemories) {
            requireFaction(memory.ownerFactionId());
            requireFaction(memory.targetFactionId());
        }
        for (Proposal proposal : proposals) {
            requireFaction(proposal.proposerFactionId());
            requireFaction(proposal.recipientFactionId());
        }
        for (Crisis crisis : crises) {
            requireFaction(crisis.initiatorFactionId());
            requireFaction(crisis.targetFactionId());
        }
        for (War war : wars) {
            requireFaction(war.factionA());
            requireFaction(war.factionB());
            for (String conflictId : war.stage19ConflictIds()) {
                if (warfare.find(conflictId).isEmpty()) {
                    throw new IllegalArgumentException("Legal war references missing Stage-19 conflict: " + conflictId);
                }
            }
        }
    }

    private void resolveLinkedCrisis(String crisisId, String evidenceId, long now) {
        if (crisisId == null || crisisId.isBlank()) return;
        int index = requireCrisisIndex(crisisId);
        Crisis current = crises.get(index);
        if (current.escalation() == CrisisEscalation.RESOLVED) return;
        crises.set(index, new Crisis(
                current.crisisId(),
                current.initiatorFactionId(),
                current.targetFactionId(),
                current.issueId(),
                current.demands(),
                current.concessions(),
                current.deadlineTick(),
                CrisisEscalation.RESOLVED,
                current.causalProposalId(),
                requireText(evidenceId, "Crisis resolution evidence"),
                current.createdTick(),
                now));
    }

    private static List<ObjectiveSnapshot> stage19Objectives(List<WarGoal> goals) {
        return goals.stream()
                .map(goal -> new ObjectiveSnapshot(
                        goal.goalId(), goal.subjectId(), goal.mandatory(), ObjectiveEvidence.OBSERVED_UNMET))
                .toList();
    }

    private int relationMemoryIndex(String owner, String target) {
        for (int index = 0; index < relationMemories.size(); index++) {
            RelationMemory memory = relationMemories.get(index);
            if (memory.ownerFactionId().equals(owner) && memory.targetFactionId().equals(target)) return index;
        }
        return -1;
    }

    private int proposalIndex(String proposalId) {
        String id = requireText(proposalId, "Proposal ID");
        for (int index = 0; index < proposals.size(); index++) {
            if (proposals.get(index).proposalId().equals(id)) return index;
        }
        return -1;
    }

    private int requireProposalIndex(String proposalId) {
        int index = proposalIndex(proposalId);
        if (index < 0) throw new IllegalArgumentException("Unknown proposal: " + proposalId);
        return index;
    }

    private int requireOpenProposalIndex(String proposalId) {
        int index = requireProposalIndex(proposalId);
        Proposal proposal = proposals.get(index);
        long now = world.getAuthoritativeWorldTick();
        if (!proposal.openAt(now)) {
            throw new IllegalStateException("Proposal is not open at current world tick: " + proposalId);
        }
        return index;
    }

    private int requireCrisisIndex(String crisisId) {
        String id = requireText(crisisId, "Crisis ID");
        for (int index = 0; index < crises.size(); index++) {
            if (crises.get(index).crisisId().equals(id)) return index;
        }
        throw new IllegalArgumentException("Unknown crisis: " + id);
    }

    private Crisis requireCrisis(String crisisId) {
        return crises.get(requireCrisisIndex(crisisId));
    }

    private int requireWarIndex(String warId) {
        String id = requireText(warId, "War ID");
        for (int index = 0; index < wars.size(); index++) {
            if (wars.get(index).warId().equals(id)) return index;
        }
        throw new IllegalArgumentException("Unknown war: " + id);
    }

    private static Proposal copyProposal(
            Proposal current,
            ProposalStatus status,
            long updatedTick,
            String linkedCrisisId,
            String linkedTreatyId) {
        return new Proposal(
                current.proposalId(),
                current.sourceGoalId(),
                current.proposerFactionId(),
                current.recipientFactionId(),
                current.kind(),
                current.issueId(),
                current.demands(),
                current.concessions(),
                current.createdTick(),
                current.deadlineTick(),
                updatedTick,
                status,
                linkedCrisisId,
                linkedTreatyId);
    }

    private static StarSystemId system(String subjectId) {
        String value = requireText(subjectId, "StarSystem term subject");
        try {
            return new StarSystemId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("System-scoped negotiation term must use numeric StarSystemId: " + value,
                    exception);
        }
    }

    private String requireFaction(String factionId) {
        String id = requireText(factionId, "Faction ID");
        if (world.findFactionRuntimeId(id).isEmpty()) {
            throw new IllegalArgumentException("Unknown faction identity: " + id);
        }
        return id;
    }

    private void touch(long worldTick) {
        if (worldTick < lastLifecycleTick) {
            throw new IllegalStateException("Stage-21C lifecycle tick cannot move backwards");
        }
        lastLifecycleTick = worldTick;
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in [0,10000]");
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private record TreatyOwner(String ownerFactionId, DiplomaticTreatyState treaty) {
    }
}