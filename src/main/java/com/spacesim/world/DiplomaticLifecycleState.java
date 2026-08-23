package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent Stage-21C political memory, negotiation, crisis, obligation and legal-war state.
 *
 * <p>This aggregate is deliberately not a second treaty, treasury, territory or warfare authority.
 * Relation events are actor-bounded remembered evidence used by Stage-21C decisions. Concrete legal
 * effects are executed through the existing {@link WorldSimulation} diplomatic/territorial command
 * boundaries, while physical conflict state remains owned by Stage 19.</p>
 *
 * @param schemaVersion Stage-21C lifecycle schema
 * @param simulationTick latest authoritative tick represented by this sidecar
 * @param nextProposalSequence next monotonically increasing proposal sequence
 * @param nextCrisisSequence next monotonically increasing crisis sequence
 * @param nextWarSequence next monotonically increasing legal-war sequence
 * @param relationMemories actor-bounded directed relationship memories
 * @param proposals persistent negotiation proposals
 * @param crises persistent diplomatic crises
 * @param wars persistent legal war/ceasefire/peace identities
 * @param obligationDecisions persistent treaty-obligation decisions
 */
public record DiplomaticLifecycleState(
        int schemaVersion,
        long simulationTick,
        long nextProposalSequence,
        long nextCrisisSequence,
        long nextWarSequence,
        List<RelationMemory> relationMemories,
        List<Proposal> proposals,
        List<Crisis> crises,
        List<War> wars,
        List<ObligationDecision> obligationDecisions) {
    /** Current Stage-21C lifecycle schema. */
    public static final int CURRENT_VERSION = 1;

    /** Validates and canonicalizes the complete diplomatic lifecycle aggregate. */
    public DiplomaticLifecycleState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-21C diplomacy schema: " + schemaVersion);
        }
        if (simulationTick < 0L || nextProposalSequence <= 0L || nextCrisisSequence <= 0L || nextWarSequence <= 0L) {
            throw new IllegalArgumentException("Stage-21C lifecycle watermarks are invalid");
        }
        relationMemories = canonical(
                relationMemories,
                Comparator.comparing(RelationMemory::ownerFactionId)
                        .thenComparing(RelationMemory::targetFactionId),
                value -> value.ownerFactionId() + "\u0000" + value.targetFactionId(),
                "relation memory");
        proposals = canonical(proposals, Comparator.comparing(Proposal::proposalId), Proposal::proposalId, "proposal");
        crises = canonical(crises, Comparator.comparing(Crisis::crisisId), Crisis::crisisId, "crisis");
        wars = canonical(wars, Comparator.comparing(War::warId), War::warId, "war");
        obligationDecisions = canonical(
                obligationDecisions,
                Comparator.comparing(ObligationDecision::decisionId),
                ObligationDecision::decisionId,
                "obligation decision");
        for (Proposal proposal : proposals) {
            if (proposal.updatedTick() > simulationTick) {
                throw new IllegalArgumentException("Proposal is newer than Stage-21C checkpoint: " + proposal.proposalId());
            }
        }
        for (Crisis crisis : crises) {
            if (crisis.updatedTick() > simulationTick) {
                throw new IllegalArgumentException("Crisis is newer than Stage-21C checkpoint: " + crisis.crisisId());
            }
        }
        for (War war : wars) {
            if (war.statusChangedTick() > simulationTick || war.startEvidence().observedTick() > simulationTick) {
                throw new IllegalArgumentException("War is newer than Stage-21C checkpoint: " + war.warId());
            }
        }
    }

    /**
     * Creates an empty current-schema lifecycle sidecar.
     *
     * @param simulationTick authoritative starting tick
     * @return empty deterministic lifecycle state
     */
    public static DiplomaticLifecycleState empty(long simulationTick) {
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("simulationTick must be non-negative");
        }
        return new DiplomaticLifecycleState(
                CURRENT_VERSION, simulationTick, 1L, 1L, 1L,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** Sources that may change an actor's remembered assessment of another faction. */
    public enum RelationFactor {
        /** Remembered helpful or hostile action. */ REMEMBERED_ACTION,
        /** Honored, breached or refused treaty performance. */ TREATY_PERFORMANCE,
        /** Actor-known territorial rivalry or accommodation. */ TERRITORIAL_CONFLICT,
        /** Actor-known trade dependency or beneficial interdependence. */ TRADE_DEPENDENCE,
        /** Actor-known military/security threat. */ THREAT,
        /** Alliance, guarantee or other diplomatic commitment. */ DIPLOMATIC_COMMITMENT
    }

    /** All proposal families required by Stage 21C. */
    public enum ProposalKind {
        /** Market-access proposal. */ ACCESS,
        /** Trade/access plus tariff proposal. */ TRADE,
        /** Territorial recognition proposal. */ RECOGNITION,
        /** Foreign construction-right proposal. */ CONSTRUCTION_RIGHTS,
        /** Non-aggression proposal. */ NON_AGGRESSION,
        /** Defensive guarantee/cooperation proposal. */ DEFENSIVE_COOPERATION,
        /** Alliance proposal. */ ALLIANCE,
        /** Embargo threat, relief or coercive proposal. */ EMBARGO,
        /** Explicit ultimatum. */ ULTIMATUM,
        /** Wartime ceasefire proposal. */ CEASEFIRE,
        /** Final peace proposal. */ PEACE
    }

    /** Negotiated term families; terms are promises/requests until an owning authority executes them. */
    public enum TermKind {
        /** Real faction-treasury amount, bounded against current treasury when offered. */ TREASURY_PAYMENT,
        /** Market-access right. */ MARKET_ACCESS,
        /** Customs/tariff exemption. */ CUSTOMS_TARIFF_EXEMPTION,
        /** Construction right in one real StarSystem. */ CONSTRUCTION_RIGHT,
        /** Recognition of a claim/control subject. */ TERRITORIAL_RECOGNITION,
        /** Non-aggression commitment. */ NON_AGGRESSION,
        /** Defensive guarantee. */ GUARANTEE,
        /** Alliance commitment. */ ALLIANCE,
        /** Embargo removal or restraint. */ EMBARGO_RELIEF,
        /** Ceasefire/peace settlement term. */ WAR_SETTLEMENT
    }

    /** Proposal lifecycle. */
    public enum ProposalStatus {
        /** Proposal awaits a response. */ OPEN,
        /** Proposal was accepted through the relevant authority/lifecycle. */ ACCEPTED,
        /** Proposal was explicitly rejected. */ REJECTED,
        /** Proposal deadline elapsed. */ EXPIRED,
        /** Proposer withdrew it before resolution. */ WITHDRAWN
    }

    /** Persistent crisis escalation. */
    public enum CrisisEscalation {
        /** Normal bargaining. */ NEGOTIATION,
        /** Coercive diplomatic pressure short of ultimatum. */ PRESSURE,
        /** Explicit final demand with a deadline. */ ULTIMATUM,
        /** Persisted political authorization to begin a war. */ WAR_AUTHORIZED,
        /** Crisis is resolved and may not escalate further. */ RESOLVED
    }

    /** Legal war lifecycle; physical operations remain separately owned. */
    public enum WarStatus {
        /** Legal war exists. */ ACTIVE,
        /** Fighting is politically suspended with a re-escalation cooldown. */ CEASEFIRE,
        /** Peace terminates the legal war and starts a post-war cooldown. */ PEACE
    }

    /** Admissible causal evidence for creating legal war identity. */
    public enum WarStartKind {
        /** A persisted crisis reached explicit WAR_AUTHORIZED state. */ CRISIS_DECISION,
        /** The declaring actor observed a concrete hostile attack. */ OBSERVED_HOSTILE_ATTACK
    }

    /** Political war-goal families used only as named objectives. */
    public enum WarGoalKind {
        /** Obtain or restore lawful access. */ ACCESS,
        /** Obtain recognition. */ RECOGNITION,
        /** Contest a real territorial subject. */ TERRITORY,
        /** Remove an actor-known security threat. */ SECURITY,
        /** Seek a bounded monetary settlement. */ REPARATION
    }

    /** Treaty/alliance obligation response. */
    public enum ObligationOutcome {
        /** Faction chose to honor the obligation. */ HONORED,
        /** Faction lawfully refused and accepts reputational consequences. */ REFUSED
    }

    /**
     * One actor-bounded remembered event.
     *
     * @param eventId stable evidence identity
     * @param factor source family
     * @param impact signed relation contribution in [-100,100]
     * @param observedTick tick when the actor learned the event
     * @param subjectId stable actor-known subject/provenance identity
     */
    public record RelationEvent(
            String eventId,
            RelationFactor factor,
            int impact,
            long observedTick,
            String subjectId) implements Comparable<RelationEvent> {
        /** Validates one remembered diplomatic event. */
        public RelationEvent {
            eventId = requireText(eventId, "Relation event ID");
            Objects.requireNonNull(factor, "Relation factor not set");
            if (impact < -100 || impact > 100 || observedTick < 0L) {
                throw new IllegalArgumentException("Relation event impact/tick is invalid");
            }
            subjectId = requireText(subjectId, "Relation event subject");
        }

        @Override
        public int compareTo(RelationEvent other) {
            return eventId.compareTo(Objects.requireNonNull(other, "RelationEvent not set").eventId);
        }
    }

    /**
     * Directed remembered relationship evidence for one actor/target pair.
     *
     * @param ownerFactionId actor whose memory this is
     * @param targetFactionId remembered counterparty
     * @param events canonical remembered evidence
     */
    public record RelationMemory(
            String ownerFactionId,
            String targetFactionId,
            List<RelationEvent> events) implements Comparable<RelationMemory> {
        /** Validates directed actor-bounded memory. */
        public RelationMemory {
            ownerFactionId = requireText(ownerFactionId, "Relation-memory owner");
            targetFactionId = requireText(targetFactionId, "Relation-memory target");
            if (ownerFactionId.equals(targetFactionId)) {
                throw new IllegalArgumentException("Faction cannot remember a self relation");
            }
            events = canonical(events, Comparator.naturalOrder(), RelationEvent::eventId, "relation event");
        }

        /** @return clamped derived assessment from persisted remembered evidence */
        public int derivedRelation() {
            int value = 0;
            for (RelationEvent event : events) {
                value = Math.max(-100, Math.min(100, value + event.impact()));
            }
            return value;
        }

        @Override
        public int compareTo(RelationMemory other) {
            RelationMemory checked = Objects.requireNonNull(other, "RelationMemory not set");
            int owner = ownerFactionId.compareTo(checked.ownerFactionId);
            return owner != 0 ? owner : targetFactionId.compareTo(checked.targetFactionId);
        }
    }

    /**
     * One bounded negotiation term.
     *
     * @param kind semantic term family
     * @param subjectId stable target identity; use a StarSystem numeric value for system-scoped terms
     * @param amountMilliCredits real treasury amount for TREASURY_PAYMENT, otherwise zero
     */
    public record Term(TermKind kind, String subjectId, long amountMilliCredits) implements Comparable<Term> {
        /** Validates one immutable negotiation term. */
        public Term {
            Objects.requireNonNull(kind, "Negotiation term kind not set");
            subjectId = requireText(subjectId, "Negotiation term subject");
            if (amountMilliCredits < 0L) {
                throw new IllegalArgumentException("Negotiation term amount cannot be negative");
            }
            if ((kind == TermKind.TREASURY_PAYMENT) != (amountMilliCredits > 0L)) {
                throw new IllegalArgumentException("Only treasury-payment terms may carry a positive money amount");
            }
        }

        @Override
        public int compareTo(Term other) {
            Term checked = Objects.requireNonNull(other, "Term not set");
            int kindOrder = kind.compareTo(checked.kind);
            if (kindOrder != 0) return kindOrder;
            int subjectOrder = subjectId.compareTo(checked.subjectId);
            return subjectOrder != 0 ? subjectOrder : Long.compare(amountMilliCredits, checked.amountMilliCredits);
        }
    }

    /**
     * Persistent diplomatic proposal.
     *
     * @param proposalId stable proposal identity
     * @param sourceGoalId Stage-21B goal identity that caused it, or a stable external command identity
     * @param proposerFactionId proposing faction
     * @param recipientFactionId receiving faction
     * @param kind proposal family
     * @param issueId stable issue/subject identity
     * @param demands terms requested from the recipient
     * @param concessions terms offered by the proposer
     * @param createdTick creation tick
     * @param deadlineTick final response tick
     * @param updatedTick latest lifecycle update tick
     * @param status current proposal status
     * @param linkedCrisisId causal crisis identity or empty
     * @param linkedTreatyId Stage-17 treaty identity or empty
     */
    public record Proposal(
            String proposalId,
            String sourceGoalId,
            String proposerFactionId,
            String recipientFactionId,
            ProposalKind kind,
            String issueId,
            List<Term> demands,
            List<Term> concessions,
            long createdTick,
            long deadlineTick,
            long updatedTick,
            ProposalStatus status,
            String linkedCrisisId,
            String linkedTreatyId) implements Comparable<Proposal> {
        /** Validates one persistent proposal. */
        public Proposal {
            proposalId = requireText(proposalId, "Proposal ID");
            sourceGoalId = requireText(sourceGoalId, "Proposal source goal ID");
            proposerFactionId = requireText(proposerFactionId, "Proposal proposer");
            recipientFactionId = requireText(recipientFactionId, "Proposal recipient");
            if (proposerFactionId.equals(recipientFactionId)) {
                throw new IllegalArgumentException("Faction cannot propose diplomacy to itself");
            }
            Objects.requireNonNull(kind, "Proposal kind not set");
            issueId = requireText(issueId, "Proposal issue ID");
            demands = canonicalTerms(demands);
            concessions = canonicalTerms(concessions);
            if (createdTick < 0L || deadlineTick <= createdTick || updatedTick < createdTick) {
                throw new IllegalArgumentException("Proposal lifecycle ticks are invalid");
            }
            Objects.requireNonNull(status, "Proposal status not set");
            linkedCrisisId = optionalText(linkedCrisisId);
            linkedTreatyId = optionalText(linkedTreatyId);
        }

        /** @return true while the proposal may still receive a response at {@code worldTick} */
        public boolean openAt(long worldTick) {
            return status == ProposalStatus.OPEN && worldTick >= createdTick && worldTick < deadlineTick;
        }

        @Override
        public int compareTo(Proposal other) {
            return proposalId.compareTo(Objects.requireNonNull(other, "Proposal not set").proposalId);
        }
    }

    /**
     * Persistent causal diplomatic crisis.
     *
     * @param crisisId stable crisis identity
     * @param initiatorFactionId initiating faction
     * @param targetFactionId target faction
     * @param issueId stable crisis issue
     * @param demands current demands
     * @param concessions current offered concessions
     * @param deadlineTick current escalation/response deadline
     * @param escalation current escalation state
     * @param causalProposalId proposal that opened the crisis, or stable external cause identity
     * @param decisionEvidenceId evidence/decision supporting the latest escalation
     * @param createdTick creation tick
     * @param updatedTick latest update tick
     */
    public record Crisis(
            String crisisId,
            String initiatorFactionId,
            String targetFactionId,
            String issueId,
            List<Term> demands,
            List<Term> concessions,
            long deadlineTick,
            CrisisEscalation escalation,
            String causalProposalId,
            String decisionEvidenceId,
            long createdTick,
            long updatedTick) implements Comparable<Crisis> {
        /** Validates one persistent crisis. */
        public Crisis {
            crisisId = requireText(crisisId, "Crisis ID");
            initiatorFactionId = requireText(initiatorFactionId, "Crisis initiator");
            targetFactionId = requireText(targetFactionId, "Crisis target");
            if (initiatorFactionId.equals(targetFactionId)) {
                throw new IllegalArgumentException("Crisis participants must differ");
            }
            issueId = requireText(issueId, "Crisis issue");
            demands = canonicalTerms(demands);
            concessions = canonicalTerms(concessions);
            Objects.requireNonNull(escalation, "Crisis escalation not set");
            causalProposalId = requireText(causalProposalId, "Crisis causal proposal/evidence");
            decisionEvidenceId = requireText(decisionEvidenceId, "Crisis decision evidence");
            if (createdTick < 0L || deadlineTick <= createdTick || updatedTick < createdTick) {
                throw new IllegalArgumentException("Crisis lifecycle ticks are invalid");
            }
        }

        /** @return whether the supplied faction is one of the two crisis participants */
        public boolean includes(String factionId) {
            return initiatorFactionId.equals(factionId) || targetFactionId.equals(factionId);
        }

        @Override
        public int compareTo(Crisis other) {
            return crisisId.compareTo(Objects.requireNonNull(other, "Crisis not set").crisisId);
        }
    }

    /**
     * Explicit named legal war goal.
     *
     * @param goalId stable objective identity
     * @param claimantFactionId faction seeking the objective
     * @param kind political goal family
     * @param subjectId real political subject identity
     * @param mandatory whether a settlement must satisfy the objective for that claimant
     */
    public record WarGoal(
            String goalId,
            String claimantFactionId,
            WarGoalKind kind,
            String subjectId,
            boolean mandatory) implements Comparable<WarGoal> {
        /** Validates one legal war objective. */
        public WarGoal {
            goalId = requireText(goalId, "War goal ID");
            claimantFactionId = requireText(claimantFactionId, "War goal claimant");
            Objects.requireNonNull(kind, "War goal kind not set");
            subjectId = requireText(subjectId, "War goal subject");
        }

        @Override
        public int compareTo(WarGoal other) {
            return goalId.compareTo(Objects.requireNonNull(other, "WarGoal not set").goalId);
        }
    }

    /**
     * Persisted causal evidence that made war legally admissible.
     *
     * @param kind causal category
     * @param evidenceId stable evidence/decision identity
     * @param observedTick tick at which the evidence existed for the declaring actor
     * @param crisisId causal crisis ID for CRISIS_DECISION, otherwise empty
     */
    public record WarStartEvidence(
            WarStartKind kind,
            String evidenceId,
            long observedTick,
            String crisisId) {
        /** Validates causal war evidence. */
        public WarStartEvidence {
            Objects.requireNonNull(kind, "War start kind not set");
            evidenceId = requireText(evidenceId, "War start evidence ID");
            if (observedTick < 0L) {
                throw new IllegalArgumentException("War start evidence tick cannot be negative");
            }
            crisisId = optionalText(crisisId);
            if (kind == WarStartKind.CRISIS_DECISION && crisisId.isEmpty()) {
                throw new IllegalArgumentException("Crisis-based war requires a causal crisis ID");
            }
            if (kind == WarStartKind.OBSERVED_HOSTILE_ATTACK && !crisisId.isEmpty()) {
                throw new IllegalArgumentException("Observed-attack war evidence must not invent a crisis ID");
            }
        }
    }

    /**
     * Persistent legal war identity and peace hysteresis.
     *
     * @param warId stable war identity
     * @param factionA first participant in canonical lexical order
     * @param factionB second participant in canonical lexical order
     * @param goals explicit political objectives
     * @param startEvidence persisted legal cause
     * @param stage19ConflictIds exact Stage-19 actor-perspective conflict identities
     * @param status legal lifecycle status
     * @param startedTick war creation tick
     * @param statusChangedTick latest ceasefire/peace transition tick
     * @param reEscalationCooldownUntilTick earliest legal re-escalation tick after ceasefire/peace
     */
    public record War(
            String warId,
            String factionA,
            String factionB,
            List<WarGoal> goals,
            WarStartEvidence startEvidence,
            List<String> stage19ConflictIds,
            WarStatus status,
            long startedTick,
            long statusChangedTick,
            long reEscalationCooldownUntilTick) implements Comparable<War> {
        /** Validates one legal war lifecycle state. */
        public War {
            warId = requireText(warId, "War ID");
            factionA = requireText(factionA, "War faction A");
            factionB = requireText(factionB, "War faction B");
            if (factionA.compareTo(factionB) >= 0) {
                throw new IllegalArgumentException("War participants must be distinct and canonically ordered");
            }
            goals = canonical(goals, Comparator.naturalOrder(), WarGoal::goalId, "war goal");
            if (goals.isEmpty()) {
                throw new IllegalArgumentException("Legal war requires at least one explicit goal");
            }
            Set<String> claimants = new HashSet<>();
            for (WarGoal goal : goals) {
                if (!goal.claimantFactionId().equals(factionA) && !goal.claimantFactionId().equals(factionB)) {
                    throw new IllegalArgumentException("War goal claimant is not a participant: " + goal.goalId());
                }
                claimants.add(goal.claimantFactionId());
            }
            if (!claimants.contains(factionA) || !claimants.contains(factionB)) {
                throw new IllegalArgumentException("Each war participant requires at least one explicit objective");
            }
            Objects.requireNonNull(startEvidence, "War start evidence not set");
            stage19ConflictIds = canonicalStrings(stage19ConflictIds, "Stage-19 conflict ID");
            if (stage19ConflictIds.size() != 2) {
                throw new IllegalArgumentException("Legal war must reference two actor-perspective Stage-19 conflicts");
            }
            Objects.requireNonNull(status, "War status not set");
            if (startedTick < 0L || statusChangedTick < startedTick || reEscalationCooldownUntilTick < 0L) {
                throw new IllegalArgumentException("War lifecycle ticks are invalid");
            }
            if (startEvidence.observedTick() > startedTick) {
                throw new IllegalArgumentException("War cannot predate its causal evidence");
            }
            if (status == WarStatus.ACTIVE && reEscalationCooldownUntilTick != 0L) {
                throw new IllegalArgumentException("Active war cannot carry a peace re-escalation cooldown");
            }
            if (status != WarStatus.ACTIVE && reEscalationCooldownUntilTick <= statusChangedTick) {
                throw new IllegalArgumentException("Ceasefire/peace must establish a future re-escalation cooldown");
            }
        }

        /** @return whether the supplied faction is a participant */
        public boolean includes(String factionId) {
            return factionA.equals(factionId) || factionB.equals(factionId);
        }

        /** @return whether this war is between exactly the supplied pair */
        public boolean matchesPair(String first, String second) {
            return includes(first) && includes(second) && !Objects.equals(first, second);
        }

        @Override
        public int compareTo(War other) {
            return warId.compareTo(Objects.requireNonNull(other, "War not set").warId);
        }
    }

    /**
     * Persisted alliance/treaty obligation response.
     *
     * @param decisionId stable decision identity
     * @param treatyId Stage-17 treaty identity
     * @param obligatedFactionId faction expected to act
     * @param beneficiaryFactionId protected/beneficiary faction
     * @param threatEvidenceId actor-known evidence that triggered the obligation
     * @param outcome chosen honor/refusal outcome
     * @param reputationImpact signed remembered reputation contribution
     * @param decisionTick authoritative decision tick
     */
    public record ObligationDecision(
            String decisionId,
            String treatyId,
            String obligatedFactionId,
            String beneficiaryFactionId,
            String threatEvidenceId,
            ObligationOutcome outcome,
            int reputationImpact,
            long decisionTick) implements Comparable<ObligationDecision> {
        /** Validates one persisted obligation decision. */
        public ObligationDecision {
            decisionId = requireText(decisionId, "Obligation decision ID");
            treatyId = requireText(treatyId, "Obligation treaty ID");
            obligatedFactionId = requireText(obligatedFactionId, "Obligated faction ID");
            beneficiaryFactionId = requireText(beneficiaryFactionId, "Beneficiary faction ID");
            if (obligatedFactionId.equals(beneficiaryFactionId)) {
                throw new IllegalArgumentException("Obligation parties must differ");
            }
            threatEvidenceId = requireText(threatEvidenceId, "Obligation threat evidence ID");
            Objects.requireNonNull(outcome, "Obligation outcome not set");
            if (reputationImpact < -100 || reputationImpact > 100 || decisionTick < 0L) {
                throw new IllegalArgumentException("Obligation reputation/tick is invalid");
            }
        }

        @Override
        public int compareTo(ObligationDecision other) {
            return decisionId.compareTo(Objects.requireNonNull(other, "ObligationDecision not set").decisionId);
        }
    }

    private static List<Term> canonicalTerms(List<Term> source) {
        Objects.requireNonNull(source, "Negotiation terms not set");
        ArrayList<Term> copy = new ArrayList<>(source.size());
        for (Term term : source) {
            copy.add(Objects.requireNonNull(term, "Negotiation term not set"));
        }
        copy.sort(Comparator.naturalOrder());
        return List.copyOf(copy);
    }

    private static List<String> canonicalStrings(List<String> source, String label) {
        Objects.requireNonNull(source, label + " list not set");
        ArrayList<String> copy = new ArrayList<>(source.size());
        for (String value : source) {
            copy.add(requireText(value, label));
        }
        copy.sort(String::compareTo);
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("Duplicate " + label);
        }
        return List.copyOf(copy);
    }

    private static <T> List<T> canonical(
            List<T> source,
            Comparator<T> comparator,
            java.util.function.Function<T, String> identity,
            String label) {
        Objects.requireNonNull(source, label + " list not set");
        ArrayList<T> copy = new ArrayList<>(source.size());
        Set<String> ids = new HashSet<>();
        for (T value : source) {
            T checked = Objects.requireNonNull(value, label + " not set");
            String id = requireText(identity.apply(checked), label + " identity");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate " + label + ": " + id);
            }
            copy.add(checked);
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.strip();
    }
}