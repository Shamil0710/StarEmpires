package com.spacesim.persistence;

import com.spacesim.warfare.StrategicWarPolicyService.Decision;
import com.spacesim.warfare.StrategicWarPolicyService.EscalationLevel;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveAssessment;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveEvidence;
import com.spacesim.warfare.StrategicWarPolicyService.WarObjective;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable persistent Stage-19H conflict extension state.
 *
 * <p>This state deliberately persists only information owned by the warfare layer: named conflict
 * identity, actor-bounded objectives/evidence, political escalation/mobilization posture, cumulative
 * observed wartime consequences and the last strategic decision. Physical ships, cargo, ammunition,
 * reaction mass, repair stock and industrial queues remain in their existing Stage-17.5/18 owners.
 * Treaties, embargoes, trust and grievances remain in Stage-17 diplomacy persistence.</p>
 *
 * @param schemaVersion Stage-19 conflict persistence schema version
 * @param simulationTick authoritative checkpoint tick associated with the conflict snapshot
 * @param conflicts deterministic actor-perspective conflict snapshots
 */
public record Stage19ConflictState(
        int schemaVersion,
        long simulationTick,
        List<ConflictSnapshot> conflicts) {
    /** Current Stage-19 conflict persistence schema. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Validates, canonicalizes and freezes one Stage-19 conflict snapshot.
     *
     * @param schemaVersion Stage-19 conflict persistence schema version
     * @param simulationTick authoritative non-negative checkpoint tick
     * @param conflicts actor-perspective conflict snapshots
     */
    public Stage19ConflictState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-19 conflict schema: " + schemaVersion);
        }
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("simulationTick must be non-negative");
        }
        Objects.requireNonNull(conflicts, "conflicts");
        ArrayList<ConflictSnapshot> copy = new ArrayList<>(conflicts.size());
        Set<String> seenIds = new HashSet<>();
        for (ConflictSnapshot conflict : conflicts) {
            ConflictSnapshot checked = Objects.requireNonNull(conflict, "conflict");
            if (!seenIds.add(checked.conflictId())) {
                throw new IllegalArgumentException("Duplicate Stage-19 conflict ID: " + checked.conflictId());
            }
            if (checked.lastDecisionTick() > simulationTick) {
                throw new IllegalArgumentException("Conflict decision tick cannot exceed snapshot tick: "
                        + checked.conflictId());
            }
            copy.add(checked);
        }
        copy.sort(Comparator.comparing(ConflictSnapshot::conflictId));
        conflicts = List.copyOf(copy);
    }

    /**
     * Creates an empty current-schema warfare extension.
     *
     * @param simulationTick authoritative checkpoint tick
     * @return empty deterministic Stage-19 conflict state
     */
    public static Stage19ConflictState empty(long simulationTick) {
        return new Stage19ConflictState(CURRENT_VERSION, simulationTick, List.of());
    }

    /** Lifecycle state of one actor-perspective strategic conflict. */
    public enum ConflictStatus {
        /** Active conflict with no currently persisted settlement posture. */ ACTIVE,
        /** Actor is seeking terms because aims/readiness cannot sustain the current course. */ SETTLEMENT_SEEKING,
        /** Actor has chosen to offer terms; legal treaty proposal remains Stage-17 owned. */ SETTLEMENT_OFFERED,
        /** Conflict is politically resolved in this actor-perspective Stage-19 state. */ RESOLVED
    }

    /** Political mobilization authorization; values grant no physical statistics or free resources. */
    public enum MobilizationPosture {
        /** No war-specific mobilization authorization beyond ordinary readiness. */ NORMAL,
        /** Partial mobilization authorization for bounded war. */ PARTIAL,
        /** Full represented mobilization authorization for general war. */ FULL
    }

    /**
     * Cumulative actor-known physical consequences that cannot be reconstructed from current stocks alone.
     *
     * <p>These values are historical information, not a second physical ledger. They may only be increased
     * from already confirmed/observed Stage-17.5/18/19E-F outcomes.</p>
     *
     * @param confirmedOwnDestroyedMassKg confirmed own constructed ship mass destroyed
     * @param confirmedOwnUndeliveredCargoKg confirmed own cargo mass denied or left undelivered
     * @param observedOpponentDestroyedMassKg opponent constructed mass actor observed/confirmed destroyed
     * @param observedOpponentUndeliveredCargoKg opponent cargo mass actor observed/confirmed denied
     */
    public record ObservedConsequences(
            double confirmedOwnDestroyedMassKg,
            double confirmedOwnUndeliveredCargoKg,
            double observedOpponentDestroyedMassKg,
            double observedOpponentUndeliveredCargoKg) {
        /**
         * Validates one actor-known cumulative consequence snapshot.
         *
         * @param confirmedOwnDestroyedMassKg confirmed own destroyed constructed mass
         * @param confirmedOwnUndeliveredCargoKg confirmed own undelivered cargo mass
         * @param observedOpponentDestroyedMassKg actor-observed opponent destroyed constructed mass
         * @param observedOpponentUndeliveredCargoKg actor-observed opponent undelivered cargo mass
         */
        public ObservedConsequences {
            requireNonNegativeFinite(confirmedOwnDestroyedMassKg, "confirmedOwnDestroyedMassKg");
            requireNonNegativeFinite(confirmedOwnUndeliveredCargoKg, "confirmedOwnUndeliveredCargoKg");
            requireNonNegativeFinite(observedOpponentDestroyedMassKg, "observedOpponentDestroyedMassKg");
            requireNonNegativeFinite(observedOpponentUndeliveredCargoKg, "observedOpponentUndeliveredCargoKg");
        }

        /** @return zeroed actor-known consequence history */
        public static ObservedConsequences none() {
            return new ObservedConsequences(0d, 0d, 0d, 0d);
        }
    }

    /**
     * Persistent actor-visible version of one Stage-19G named objective.
     *
     * @param id stable objective identity
     * @param subjectId stable real political subject identity
     * @param mandatory whether settlement must satisfy the objective
     * @param evidence actor-bounded current evidence state
     */
    public record ObjectiveSnapshot(
            String id,
            String subjectId,
            boolean mandatory,
            ObjectiveEvidence evidence) {
        /**
         * Validates one objective snapshot.
         *
         * @param id stable objective identity
         * @param subjectId stable real political subject identity
         * @param mandatory whether the objective is mandatory
         * @param evidence actor-bounded evidence state
         */
        public ObjectiveSnapshot {
            id = requireText(id, "objective id");
            subjectId = requireText(subjectId, "objective subjectId");
            Objects.requireNonNull(evidence, "evidence");
        }

        /** @return Stage-19G assessment without adding any hidden information */
        public ObjectiveAssessment assessment() {
            return new ObjectiveAssessment(new WarObjective(id, subjectId, mandatory), evidence);
        }
    }

    /**
     * One directed actor-perspective conflict snapshot.
     *
     * @param conflictId stable conflict identity
     * @param actorFactionId faction whose information/policy perspective this snapshot represents
     * @param opponentFactionId opposing faction identity
     * @param escalation current political escalation authorization
     * @param mobilization current political mobilization authorization
     * @param status conflict lifecycle status
     * @param objectives named actor-visible objectives sorted canonically by ID
     * @param consequences cumulative actor-known physical consequences
     * @param lastDecision last Stage-19G policy result applied to this snapshot
     * @param lastDecisionTick tick of last applied decision, or {@code -1} before any decision
     */
    public record ConflictSnapshot(
            String conflictId,
            String actorFactionId,
            String opponentFactionId,
            EscalationLevel escalation,
            MobilizationPosture mobilization,
            ConflictStatus status,
            List<ObjectiveSnapshot> objectives,
            ObservedConsequences consequences,
            Decision lastDecision,
            long lastDecisionTick) {
        /**
         * Validates, sorts and freezes one directed conflict snapshot.
         *
         * @param conflictId stable conflict identity
         * @param actorFactionId deciding faction identity
         * @param opponentFactionId opposing faction identity
         * @param escalation current escalation authorization
         * @param mobilization current mobilization authorization
         * @param status lifecycle status
         * @param objectives named actor-visible objective snapshots
         * @param consequences cumulative actor-known physical consequences
         * @param lastDecision last policy decision
         * @param lastDecisionTick last decision tick or {@code -1}
         */
        public ConflictSnapshot {
            conflictId = requireText(conflictId, "conflictId");
            actorFactionId = requireText(actorFactionId, "actorFactionId");
            opponentFactionId = requireText(opponentFactionId, "opponentFactionId");
            if (actorFactionId.equals(opponentFactionId)) {
                throw new IllegalArgumentException("Conflict actor and opponent must differ");
            }
            Objects.requireNonNull(escalation, "escalation");
            Objects.requireNonNull(mobilization, "mobilization");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(objectives, "objectives");
            Objects.requireNonNull(consequences, "consequences");
            Objects.requireNonNull(lastDecision, "lastDecision");
            if (lastDecisionTick < -1L) {
                throw new IllegalArgumentException("lastDecisionTick must be -1 or non-negative");
            }
            if (objectives.isEmpty()) {
                throw new IllegalArgumentException("Conflict requires at least one named objective");
            }
            ArrayList<ObjectiveSnapshot> copy = new ArrayList<>(objectives.size());
            Set<String> seen = new HashSet<>();
            for (ObjectiveSnapshot objective : objectives) {
                ObjectiveSnapshot checked = Objects.requireNonNull(objective, "objective");
                if (!seen.add(checked.id())) {
                    throw new IllegalArgumentException("Duplicate conflict objective ID: " + checked.id());
                }
                copy.add(checked);
            }
            copy.sort(Comparator.comparing(ObjectiveSnapshot::id));
            objectives = List.copyOf(copy);
        }

        /**
         * Creates the initial active actor-perspective conflict state.
         *
         * @param conflictId stable conflict identity
         * @param actorFactionId deciding faction identity
         * @param opponentFactionId opposing faction identity
         * @param escalation initial political escalation
         * @param objectives initial named actor-visible objectives
         * @return active conflict with zero observed consequence history
         */
        public static ConflictSnapshot active(
                String conflictId,
                String actorFactionId,
                String opponentFactionId,
                EscalationLevel escalation,
                List<ObjectiveSnapshot> objectives) {
            EscalationLevel checked = Objects.requireNonNull(escalation, "escalation");
            return new ConflictSnapshot(
                    conflictId,
                    actorFactionId,
                    opponentFactionId,
                    checked,
                    mobilizationFor(checked),
                    ConflictStatus.ACTIVE,
                    objectives,
                    ObservedConsequences.none(),
                    Decision.HOLD,
                    -1L);
        }
    }

    /**
     * Derives the minimum political mobilization label represented by one escalation state.
     *
     * <p>This is categorization only; it grants no physical ship, stock, throughput or combat bonus.</p>
     *
     * @param escalation current escalation authorization
     * @return corresponding political mobilization posture
     */
    public static MobilizationPosture mobilizationFor(EscalationLevel escalation) {
        return switch (Objects.requireNonNull(escalation, "escalation")) {
            case CRISIS -> MobilizationPosture.NORMAL;
            case LIMITED_WAR -> MobilizationPosture.PARTIAL;
            case GENERAL_WAR -> MobilizationPosture.FULL;
        };
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return value;
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
