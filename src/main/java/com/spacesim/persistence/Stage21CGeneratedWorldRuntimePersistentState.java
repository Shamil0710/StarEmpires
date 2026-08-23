package com.spacesim.persistence;

import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.DiplomaticLifecycleState.Crisis;
import com.spacesim.world.DiplomaticLifecycleState.CrisisEscalation;
import com.spacesim.world.DiplomaticLifecycleState.ObligationDecision;
import com.spacesim.world.DiplomaticLifecycleState.Proposal;
import com.spacesim.world.DiplomaticLifecycleState.RelationMemory;
import com.spacesim.world.DiplomaticLifecycleState.War;
import com.spacesim.world.DiplomaticLifecycleState.WarStartKind;
import com.spacesim.world.WorldState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Atomic Stage-21C generated-world checkpoint composition.
 *
 * <p>The complete accepted Stage-21B runtime remains embedded unchanged. Stage 21C adds only the
 * political lifecycle sidecar and the already-established Stage-19 warfare extension. Treaty,
 * embargo, access, tariff, treasury, territory, fleet and physical-world authorities therefore
 * remain in their original persistence owners.</p>
 *
 * @param schemaVersion Stage-21C composition schema
 * @param runtimeVersion exact Stage-21C runtime composition contract
 * @param stage21BRuntime exact underlying accepted Stage-21B checkpoint
 * @param diplomacyLifecycle persistent Stage-21C political lifecycle
 * @param warfareState exact Stage-19 actor-perspective conflict extension referenced by legal wars
 */
public record Stage21CGeneratedWorldRuntimePersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21BGeneratedWorldRuntimePersistentState stage21BRuntime,
        DiplomaticLifecycleState diplomacyLifecycle,
        Stage19ConflictState warfareState) {
    /** Current Stage-21C checkpoint composition schema. */
    public static final int CURRENT_VERSION = 6;
    /** Stable Stage-21C runtime composition contract. */
    public static final String CURRENT_RUNTIME_VERSION = "stage21c.generated-world-diplomacy-lifecycle.v6";

    /**
     * Cross-validates faction, treaty, crisis and war/conflict references without rewriting embedded authorities.
     *
     * @param schemaVersion Stage-21C composition schema
     * @param runtimeVersion exact Stage-21C runtime composition contract
     * @param stage21BRuntime exact underlying accepted Stage-21B checkpoint
     * @param diplomacyLifecycle persistent Stage-21C political lifecycle
     * @param warfareState exact Stage-19 conflict extension
     */
    public Stage21CGeneratedWorldRuntimePersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported Stage-21C runtime checkpoint schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "Stage-21C runtime version");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException("Unsupported Stage-21C runtime version: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21BRuntime, "Stage-21B runtime checkpoint not set");
        Objects.requireNonNull(diplomacyLifecycle, "Stage-21C diplomacy lifecycle not set");
        Objects.requireNonNull(warfareState, "Stage-19 warfare state not set");

        WorldState world = stage21BRuntime.stage21ARuntime().stage20Runtime().worldState();
        Set<String> knownFactions = world.factions().stream()
                .map(faction -> faction.factionContentId())
                .collect(Collectors.toUnmodifiableSet());
        Map<String, com.spacesim.world.DiplomaticTreatyState> treatiesById = new HashMap<>();
        for (var directory : world.factionDiplomacyStates()) {
            for (var treaty : directory.treaties()) {
                if (treatiesById.putIfAbsent(treaty.treatyId(), treaty) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate embedded Stage-17 treaty identity: " + treaty.treatyId());
                }
            }
        }

        for (RelationMemory memory : diplomacyLifecycle.relationMemories()) {
            requireKnown(knownFactions, memory.ownerFactionId(), "relation-memory owner");
            requireKnown(knownFactions, memory.targetFactionId(), "relation-memory target");
            for (var event : memory.events()) {
                if (event.observedTick() > diplomacyLifecycle.simulationTick()) {
                    throw new IllegalArgumentException(
                            "Stage-21C relation memory is newer than its checkpoint: " + event.eventId());
                }
            }
        }
        Map<String, Proposal> proposalsById = new HashMap<>();
        for (Proposal proposal : diplomacyLifecycle.proposals()) {
            requireKnown(knownFactions, proposal.proposerFactionId(), "proposal proposer");
            requireKnown(knownFactions, proposal.recipientFactionId(), "proposal recipient");
            proposalsById.put(proposal.proposalId(), proposal);
            if (!proposal.linkedTreatyId().isEmpty() && !treatiesById.containsKey(proposal.linkedTreatyId())) {
                throw new IllegalArgumentException(
                        "Stage-21C proposal references missing Stage-17 treaty: " + proposal.linkedTreatyId());
            }
        }
        Map<String, Crisis> crisesById = new HashMap<>();
        for (Crisis crisis : diplomacyLifecycle.crises()) {
            requireKnown(knownFactions, crisis.initiatorFactionId(), "crisis initiator");
            requireKnown(knownFactions, crisis.targetFactionId(), "crisis target");
            crisesById.put(crisis.crisisId(), crisis);
            Proposal causalProposal = proposalsById.get(crisis.causalProposalId());
            if (causalProposal != null
                    && (!causalProposal.proposerFactionId().equals(crisis.initiatorFactionId())
                    || !causalProposal.recipientFactionId().equals(crisis.targetFactionId()))) {
                throw new IllegalArgumentException(
                        "Crisis participant pair disagrees with causal proposal: " + crisis.crisisId());
            }
        }
        for (Proposal proposal : diplomacyLifecycle.proposals()) {
            if (!proposal.linkedCrisisId().isEmpty()) {
                Crisis crisis = crisesById.get(proposal.linkedCrisisId());
                if (crisis == null || !crisis.causalProposalId().equals(proposal.proposalId())) {
                    throw new IllegalArgumentException(
                            "Proposal/crisis causal link is missing or asymmetric: " + proposal.proposalId());
                }
            }
        }
        for (ObligationDecision decision : diplomacyLifecycle.obligationDecisions()) {
            requireKnown(knownFactions, decision.obligatedFactionId(), "obligated faction");
            requireKnown(knownFactions, decision.beneficiaryFactionId(), "obligation beneficiary");
            if (decision.decisionTick() > diplomacyLifecycle.simulationTick()) {
                throw new IllegalArgumentException(
                        "Stage-21C obligation decision is newer than its checkpoint: " + decision.decisionId());
            }
            if (!treatiesById.containsKey(decision.treatyId())) {
                throw new IllegalArgumentException(
                        "Stage-21C obligation references missing Stage-17 treaty: " + decision.treatyId());
            }
        }

        Map<String, Stage19ConflictState.ConflictSnapshot> conflictsById = new HashMap<>();
        for (Stage19ConflictState.ConflictSnapshot conflict : warfareState.conflicts()) {
            requireKnown(knownFactions, conflict.actorFactionId(), "Stage-19 conflict actor");
            requireKnown(knownFactions, conflict.opponentFactionId(), "Stage-19 conflict opponent");
            conflictsById.put(conflict.conflictId(), conflict);
        }
        for (War war : diplomacyLifecycle.wars()) {
            requireKnown(knownFactions, war.factionA(), "war participant");
            requireKnown(knownFactions, war.factionB(), "war participant");
            if (war.startEvidence().kind() == WarStartKind.CRISIS_DECISION) {
                Crisis cause = crisesById.get(war.startEvidence().crisisId());
                if (cause == null
                        || cause.escalation() != CrisisEscalation.WAR_AUTHORIZED
                        || !cause.includes(war.factionA())
                        || !cause.includes(war.factionB())
                        || !cause.decisionEvidenceId().equals(war.startEvidence().evidenceId())) {
                    throw new IllegalArgumentException(
                            "Legal war lacks its exact persisted WAR_AUTHORIZED crisis evidence: " + war.warId());
                }
            }
            for (String conflictId : war.stage19ConflictIds()) {
                Stage19ConflictState.ConflictSnapshot conflict = conflictsById.get(conflictId);
                if (conflict == null) {
                    throw new IllegalArgumentException(
                            "Stage-21C legal war references missing Stage-19 conflict: " + conflictId);
                }
                if (!(conflict.actorFactionId().equals(war.factionA())
                        && conflict.opponentFactionId().equals(war.factionB())
                        || conflict.actorFactionId().equals(war.factionB())
                        && conflict.opponentFactionId().equals(war.factionA()))) {
                    throw new IllegalArgumentException(
                            "Stage-19 conflict participant pair disagrees with legal war: " + conflictId);
                }
            }
        }
    }

    private static void requireKnown(Set<String> knownFactions, String factionId, String label) {
        if (!knownFactions.contains(factionId)) {
            throw new IllegalArgumentException("Unknown " + label + " in Stage-21C checkpoint: " + factionId);
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
