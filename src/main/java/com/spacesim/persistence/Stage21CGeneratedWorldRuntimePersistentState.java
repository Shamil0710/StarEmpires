package com.spacesim.persistence;

import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.DiplomaticLifecycleState.Crisis;
import com.spacesim.world.DiplomaticLifecycleState.ObligationDecision;
import com.spacesim.world.DiplomaticLifecycleState.Proposal;
import com.spacesim.world.DiplomaticLifecycleState.RelationMemory;
import com.spacesim.world.DiplomaticLifecycleState.War;

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
     * Cross-validates faction and war/conflict identity references without rewriting embedded authorities.
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

        Set<String> knownFactions = stage21BRuntime.stage21ARuntime().stage20Runtime().worldState().factions().stream()
                .map(faction -> faction.factionContentId())
                .collect(Collectors.toUnmodifiableSet());
        for (RelationMemory memory : diplomacyLifecycle.relationMemories()) {
            requireKnown(knownFactions, memory.ownerFactionId(), "relation-memory owner");
            requireKnown(knownFactions, memory.targetFactionId(), "relation-memory target");
        }
        for (Proposal proposal : diplomacyLifecycle.proposals()) {
            requireKnown(knownFactions, proposal.proposerFactionId(), "proposal proposer");
            requireKnown(knownFactions, proposal.recipientFactionId(), "proposal recipient");
        }
        for (Crisis crisis : diplomacyLifecycle.crises()) {
            requireKnown(knownFactions, crisis.initiatorFactionId(), "crisis initiator");
            requireKnown(knownFactions, crisis.targetFactionId(), "crisis target");
        }
        for (ObligationDecision decision : diplomacyLifecycle.obligationDecisions()) {
            requireKnown(knownFactions, decision.obligatedFactionId(), "obligated faction");
            requireKnown(knownFactions, decision.beneficiaryFactionId(), "obligation beneficiary");
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
        if (diplomacyLifecycle.simulationTick() > warfareState.simulationTick()
                && !warfareState.conflicts().isEmpty()) {
            throw new IllegalArgumentException(
                    "Stage-21C diplomacy checkpoint cannot advance beyond referenced warfare checkpoint");
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