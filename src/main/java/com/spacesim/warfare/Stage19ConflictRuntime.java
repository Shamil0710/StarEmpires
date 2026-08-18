package com.spacesim.warfare;

import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage19ConflictState.ConflictSnapshot;
import com.spacesim.persistence.Stage19ConflictState.ConflictStatus;
import com.spacesim.persistence.Stage19ConflictState.MobilizationPosture;
import com.spacesim.persistence.Stage19ConflictState.ObjectiveSnapshot;
import com.spacesim.persistence.Stage19ConflictState.ObservedConsequences;
import com.spacesim.warfare.StrategicWarPolicyService.Decision;
import com.spacesim.warfare.StrategicWarPolicyService.EscalationLevel;
import com.spacesim.warfare.StrategicWarPolicyService.Input;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveAssessment;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveEvidence;
import com.spacesim.warfare.StrategicWarPolicyService.PhysicalWarEvidence;
import com.spacesim.warfare.StrategicWarPolicyService.Policy;
import com.spacesim.warfare.StrategicWarPolicyService.Result;
import com.spacesim.warfare.StrategicWarPolicyService.SettlementOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Stage-19H mutable runtime for warfare-owned persistent conflict information.
 *
 * <p>The runtime records actor-known historical consequences and political state only. Current own
 * readiness is supplied on every decision from its physical owner and is never persisted here as an
 * alternative inventory. Opponent consequences enter only through explicit observation deltas supplied
 * by callers that already possess that information. The runtime never reads ECS/world state, never
 * changes Stage-17 treaties and never grants physical capability.</p>
 */
public final class Stage19ConflictRuntime {
    private final StrategicWarPolicyService policyService;
    private final TreeMap<String, ConflictSnapshot> conflicts = new TreeMap<>();
    private long simulationTick;

    /**
     * Restores one runtime from the separate Stage-19 conflict extension.
     *
     * @param state validated persistent conflict state
     */
    public Stage19ConflictRuntime(Stage19ConflictState state) {
        this(state, new StrategicWarPolicyService());
    }

    Stage19ConflictRuntime(Stage19ConflictState state, StrategicWarPolicyService policyService) {
        Stage19ConflictState checked = Objects.requireNonNull(state, "state");
        this.policyService = Objects.requireNonNull(policyService, "policyService");
        simulationTick = checked.simulationTick();
        for (ConflictSnapshot conflict : checked.conflicts()) {
            conflicts.put(conflict.conflictId(), conflict);
        }
    }

    /** @return authoritative checkpoint tick owned by this conflict extension */
    public long simulationTick() {
        return simulationTick;
    }

    /**
     * Captures the current deterministic Stage-19 conflict extension.
     *
     * @return immutable current-schema conflict snapshot
     */
    public Stage19ConflictState snapshot() {
        return new Stage19ConflictState(
                Stage19ConflictState.CURRENT_VERSION,
                simulationTick,
                new ArrayList<>(conflicts.values()));
    }

    /**
     * Finds one conflict without exposing mutable runtime internals.
     *
     * @param conflictId stable conflict identity
     * @return immutable conflict snapshot or empty
     */
    public Optional<ConflictSnapshot> find(String conflictId) {
        if (conflictId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(conflicts.get(conflictId));
    }

    /**
     * Adds one newly created actor-perspective conflict at a monotonic tick.
     *
     * @param conflict initial active conflict snapshot
     * @param tick authoritative creation tick
     * @return installed immutable conflict snapshot
     */
    public ConflictSnapshot add(ConflictSnapshot conflict, long tick) {
        ConflictSnapshot checked = Objects.requireNonNull(conflict, "conflict");
        requireTick(tick);
        if (checked.status() == ConflictStatus.RESOLVED) {
            throw new IllegalArgumentException("Cannot add a conflict already resolved");
        }
        if (conflicts.containsKey(checked.conflictId())) {
            throw new IllegalArgumentException("Conflict already exists: " + checked.conflictId());
        }
        conflicts.put(checked.conflictId(), checked);
        simulationTick = tick;
        return checked;
    }

    /**
     * Applies newly confirmed actor-visible outcomes and objective evidence.
     *
     * <p>All consequence values are non-negative deltas and are added to cumulative historical
     * knowledge. This operation never removes physical cargo/ships itself; physical owners must have
     * already produced the measured outcome.</p>
     *
     * @param conflictId target actor-perspective conflict
     * @param tick authoritative observation tick
     * @param delta newly confirmed actor-visible outcome delta
     * @return updated immutable conflict snapshot
     */
    public ConflictSnapshot observe(String conflictId, long tick, ObservationDelta delta) {
        ConflictSnapshot current = requireMutableConflict(conflictId);
        requireTick(tick);
        ObservationDelta checked = Objects.requireNonNull(delta, "delta");
        ObservedConsequences prior = current.consequences();
        ObservedConsequences consequences = new ObservedConsequences(
                addFinite(prior.confirmedOwnDestroyedMassKg(), checked.confirmedOwnDestroyedMassKg()),
                addFinite(prior.confirmedOwnUndeliveredCargoKg(), checked.confirmedOwnUndeliveredCargoKg()),
                addFinite(prior.observedOpponentDestroyedMassKg(), checked.observedOpponentDestroyedMassKg()),
                addFinite(prior.observedOpponentUndeliveredCargoKg(), checked.observedOpponentUndeliveredCargoKg()));

        List<ObjectiveSnapshot> objectives = new ArrayList<>(current.objectives().size());
        TreeMap<String, ObjectiveEvidence> remainingUpdates = new TreeMap<>(checked.objectiveEvidenceById());
        for (ObjectiveSnapshot objective : current.objectives()) {
            ObjectiveEvidence update = remainingUpdates.remove(objective.id());
            objectives.add(update == null
                    ? objective
                    : new ObjectiveSnapshot(
                            objective.id(), objective.subjectId(), objective.mandatory(), update));
        }
        if (!remainingUpdates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Observation references unknown objective IDs: " + remainingUpdates.keySet());
        }

        ConflictSnapshot updated = copy(
                current,
                current.escalation(),
                current.mobilization(),
                current.status(),
                objectives,
                consequences,
                current.lastDecision(),
                current.lastDecisionTick());
        conflicts.put(conflictId, updated);
        simulationTick = tick;
        return updated;
    }

    /**
     * Evaluates Stage-19G policy from persisted actor-known history plus current physical own readiness.
     *
     * <p>Current readiness is intentionally not cached or persisted by this runtime. A save/load therefore
     * cannot manufacture ammunition, propellant, repair stock or operational ships: callers must supply
     * the current values again from their authoritative physical owners.</p>
     *
     * @param conflictId target actor-perspective conflict
     * @param tick authoritative decision tick
     * @param readiness current own physical readiness
     * @param policy explicit Stage-19G strategic policy thresholds
     * @param settlementOffer currently visible settlement terms
     * @return policy result together with the updated political conflict snapshot
     */
    public DecisionApplication decide(
            String conflictId,
            long tick,
            CurrentPhysicalReadiness readiness,
            Policy policy,
            SettlementOffer settlementOffer) {
        ConflictSnapshot current = requireMutableConflict(conflictId);
        requireTick(tick);
        CurrentPhysicalReadiness physical = Objects.requireNonNull(readiness, "readiness");
        Policy checkedPolicy = Objects.requireNonNull(policy, "policy");
        SettlementOffer offer = Objects.requireNonNull(settlementOffer, "settlementOffer");

        List<ObjectiveAssessment> assessments = current.objectives().stream()
                .map(ObjectiveSnapshot::assessment)
                .toList();
        ObservedConsequences history = current.consequences();
        PhysicalWarEvidence evidence = new PhysicalWarEvidence(
                physical.operationalCombatShips(),
                physical.reactionMassKg(),
                physical.repairDemandKg(),
                physical.repairMaterialAvailableKg(),
                history.confirmedOwnDestroyedMassKg(),
                history.confirmedOwnUndeliveredCargoKg(),
                history.observedOpponentDestroyedMassKg(),
                history.observedOpponentUndeliveredCargoKg());
        Result result = policyService.decide(new Input(
                current.escalation(), assessments, evidence, checkedPolicy, offer));
        ConflictSnapshot updated = applyDecision(current, result.decision(), tick);
        conflicts.put(conflictId, updated);
        simulationTick = tick;
        return new DecisionApplication(result, updated);
    }

    private ConflictSnapshot applyDecision(ConflictSnapshot current, Decision decision, long tick) {
        EscalationLevel escalation = current.escalation();
        ConflictStatus status = current.status();
        switch (decision) {
            case ESCALATE -> {
                escalation = escalate(escalation);
                status = ConflictStatus.ACTIVE;
            }
            case OFFER_SETTLEMENT -> status = ConflictStatus.SETTLEMENT_OFFERED;
            case SEEK_SETTLEMENT -> status = ConflictStatus.SETTLEMENT_SEEKING;
            case ACCEPT_SETTLEMENT -> status = ConflictStatus.RESOLVED;
            case DE_ESCALATE -> {
                EscalationLevel next = deEscalate(escalation);
                if (next == escalation && escalation == EscalationLevel.CRISIS) {
                    status = ConflictStatus.RESOLVED;
                } else {
                    escalation = next;
                    status = ConflictStatus.ACTIVE;
                }
            }
            case HOLD -> {
                // Preserve the current political posture exactly.
            }
        }
        MobilizationPosture mobilization = Stage19ConflictState.mobilizationFor(escalation);
        return copy(
                current,
                escalation,
                mobilization,
                status,
                current.objectives(),
                current.consequences(),
                decision,
                tick);
    }

    private ConflictSnapshot requireMutableConflict(String conflictId) {
        if (conflictId == null || conflictId.isBlank()) {
            throw new IllegalArgumentException("conflictId must be non-blank");
        }
        ConflictSnapshot current = conflicts.get(conflictId);
        if (current == null) {
            throw new IllegalArgumentException("Unknown conflict: " + conflictId);
        }
        if (current.status() == ConflictStatus.RESOLVED) {
            throw new IllegalStateException("Resolved conflict cannot be mutated: " + conflictId);
        }
        return current;
    }

    private void requireTick(long tick) {
        if (tick < simulationTick) {
            throw new IllegalArgumentException(
                    "Stage-19 conflict tick cannot move backwards: " + tick + " < " + simulationTick);
        }
    }

    private static EscalationLevel escalate(EscalationLevel value) {
        return switch (value) {
            case CRISIS -> EscalationLevel.LIMITED_WAR;
            case LIMITED_WAR, GENERAL_WAR -> EscalationLevel.GENERAL_WAR;
        };
    }

    private static EscalationLevel deEscalate(EscalationLevel value) {
        return switch (value) {
            case GENERAL_WAR -> EscalationLevel.LIMITED_WAR;
            case LIMITED_WAR -> EscalationLevel.CRISIS;
            case CRISIS -> EscalationLevel.CRISIS;
        };
    }

    private static ConflictSnapshot copy(
            ConflictSnapshot current,
            EscalationLevel escalation,
            MobilizationPosture mobilization,
            ConflictStatus status,
            List<ObjectiveSnapshot> objectives,
            ObservedConsequences consequences,
            Decision lastDecision,
            long lastDecisionTick) {
        return new ConflictSnapshot(
                current.conflictId(),
                current.actorFactionId(),
                current.opponentFactionId(),
                escalation,
                mobilization,
                status,
                objectives,
                consequences,
                lastDecision,
                lastDecisionTick);
    }

    private static double addFinite(double left, double right) {
        double value = left + right;
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Cumulative Stage-19 consequence overflow");
        }
        return value;
    }

    /**
     * Newly confirmed actor-visible physical outcome delta.
     *
     * @param confirmedOwnDestroyedMassKg newly confirmed own destroyed constructed mass
     * @param confirmedOwnUndeliveredCargoKg newly confirmed own denied/undelivered cargo mass
     * @param observedOpponentDestroyedMassKg newly observed opponent destroyed constructed mass
     * @param observedOpponentUndeliveredCargoKg newly observed opponent denied/undelivered cargo mass
     * @param objectiveEvidenceById explicit actor-visible objective-evidence replacements by objective ID
     */
    public record ObservationDelta(
            double confirmedOwnDestroyedMassKg,
            double confirmedOwnUndeliveredCargoKg,
            double observedOpponentDestroyedMassKg,
            double observedOpponentUndeliveredCargoKg,
            Map<String, ObjectiveEvidence> objectiveEvidenceById) {
        /**
         * Validates one non-negative observation delta.
         *
         * @param confirmedOwnDestroyedMassKg newly confirmed own destroyed constructed mass
         * @param confirmedOwnUndeliveredCargoKg newly confirmed own denied cargo mass
         * @param observedOpponentDestroyedMassKg newly observed opponent destroyed constructed mass
         * @param observedOpponentUndeliveredCargoKg newly observed opponent denied cargo mass
         * @param objectiveEvidenceById explicit objective evidence updates
         */
        public ObservationDelta {
            requireNonNegativeFinite(confirmedOwnDestroyedMassKg, "confirmedOwnDestroyedMassKg");
            requireNonNegativeFinite(confirmedOwnUndeliveredCargoKg, "confirmedOwnUndeliveredCargoKg");
            requireNonNegativeFinite(observedOpponentDestroyedMassKg, "observedOpponentDestroyedMassKg");
            requireNonNegativeFinite(observedOpponentUndeliveredCargoKg, "observedOpponentUndeliveredCargoKg");
            Objects.requireNonNull(objectiveEvidenceById, "objectiveEvidenceById");
            TreeMap<String, ObjectiveEvidence> copy = new TreeMap<>();
            for (Map.Entry<String, ObjectiveEvidence> entry : objectiveEvidenceById.entrySet()) {
                String id = entry.getKey();
                if (id == null || id.isBlank()) {
                    throw new IllegalArgumentException("objective evidence ID must be non-blank");
                }
                copy.put(id, Objects.requireNonNull(entry.getValue(), "objective evidence"));
            }
            objectiveEvidenceById = Map.copyOf(copy);
        }

        /** @return a delta with no newly observed physical or objective information */
        public static ObservationDelta none() {
            return new ObservationDelta(0d, 0d, 0d, 0d, Map.of());
        }
    }

    /**
     * Current own physical readiness supplied by authoritative physical owners at decision time.
     *
     * @param operationalCombatShips currently physical and operational own combat ships
     * @param reactionMassKg current physical reaction mass available to committed forces
     * @param repairDemandKg current physical repair-material demand
     * @param repairMaterialAvailableKg compatible physical repair material currently available
     */
    public record CurrentPhysicalReadiness(
            int operationalCombatShips,
            double reactionMassKg,
            double repairDemandKg,
            double repairMaterialAvailableKg) {
        /**
         * Validates one current physical readiness snapshot.
         *
         * @param operationalCombatShips current operational own combat ships
         * @param reactionMassKg current physical reaction mass in kilograms
         * @param repairDemandKg current physical repair-material demand in kilograms
         * @param repairMaterialAvailableKg compatible physical repair material available in kilograms
         */
        public CurrentPhysicalReadiness {
            if (operationalCombatShips < 0) {
                throw new IllegalArgumentException("operationalCombatShips must be non-negative");
            }
            requireNonNegativeFinite(reactionMassKg, "reactionMassKg");
            requireNonNegativeFinite(repairDemandKg, "repairDemandKg");
            requireNonNegativeFinite(repairMaterialAvailableKg, "repairMaterialAvailableKg");
        }
    }

    /**
     * One strategic policy decision and the political conflict state produced by applying it.
     *
     * @param policyResult inspectable Stage-19G decision predicates
     * @param conflict updated warfare-owned conflict state
     */
    public record DecisionApplication(Result policyResult, ConflictSnapshot conflict) {
        /**
         * Validates one decision application.
         *
         * @param policyResult inspectable Stage-19G policy result
         * @param conflict updated conflict state
         */
        public DecisionApplication {
            Objects.requireNonNull(policyResult, "policyResult");
            Objects.requireNonNull(conflict, "conflict");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
