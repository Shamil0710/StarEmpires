package com.spacesim.warfare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stage-19G deterministic strategic-war policy over actor-bounded evidence and physical pressure.
 *
 * <p>The service does not inspect ECS/world state, mutate diplomacy, apply combat modifiers or create
 * an abstract war-score currency. Callers provide only evidence already available to the deciding
 * actor plus physical consequences measured by their owning Stage-17.5/18/19E-F systems. Explicit
 * policy thresholds translate those measurements into a political decision.</p>
 *
 * <p>Persistent diplomacy remains owned by the existing Stage-17 world layer. In particular, this
 * service does not replace {@code FactionDiplomacyRuntime}, treaty evaluation or treaty/embargo
 * commands. A caller that turns a strategic decision into a legal diplomatic transition must do so
 * through the existing {@code WorldSimulation} diplomatic-command boundary.</p>
 */
public final class StrategicWarPolicyService {
    /** Escalation state; the enum itself grants no physical capability. */
    public enum EscalationLevel {
        /** Armed crisis or coercive deployment short of sustained war. */ CRISIS,
        /** Bounded military operations with limited political aims. */ LIMITED_WAR,
        /** Maximum political authorization represented by this Stage-19G slice. */ GENERAL_WAR
    }

    /** Actor-bounded evidence state for one named war objective. */
    public enum ObjectiveEvidence {
        /** The actor lacks enough information to determine whether the objective is met. */ UNKNOWN,
        /** The actor has observed that the objective remains unmet. */ OBSERVED_UNMET,
        /** The actor has observed that the objective is met. */ OBSERVED_MET,
        /** The actor has evidence that the objective can no longer be achieved under its current definition. */ OBSERVED_IMPOSSIBLE
    }

    /** Strategic policy decision. None of these values changes physical state by itself. */
    public enum Decision {
        /** Maintain the current political/military authorization. */ HOLD,
        /** Raise political authorization by one represented escalation level. */ ESCALATE,
        /** Propose terms while retaining the current physical posture. */ OFFER_SETTLEMENT,
        /** Seek relief because mandatory aims are impossible or current operations cannot be sustained physically. */ SEEK_SETTLEMENT,
        /** Accept an offered settlement that explicitly satisfies all mandatory aims. */ ACCEPT_SETTLEMENT,
        /** Reduce escalation because all mandatory aims are already observed as met. */ DE_ESCALATE
    }

    /**
     * One explicit political objective.
     *
     * @param id stable objective identity used by settlement terms
     * @param subjectId stable real route, system, facility, asset or other political subject identity
     * @param mandatory whether settlement must satisfy this objective for automatic acceptance
     */
    public record WarObjective(String id, String subjectId, boolean mandatory) {
        /**
         * Validates one immutable named objective.
         *
         * @param id stable objective identity used by settlement terms
         * @param subjectId stable real political subject identity
         * @param mandatory whether the objective is mandatory for settlement acceptance
         */
        public WarObjective {
            id = requireText(id, "id");
            subjectId = requireText(subjectId, "subjectId");
        }
    }

    /**
     * Actor-visible status of one objective.
     *
     * @param objective named political objective
     * @param evidence actor-bounded evidence state
     */
    public record ObjectiveAssessment(WarObjective objective, ObjectiveEvidence evidence) {
        /**
         * Validates one immutable objective assessment.
         *
         * @param objective named political objective
         * @param evidence actor-bounded evidence state
         */
        public ObjectiveAssessment {
            Objects.requireNonNull(objective, "objective");
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    /**
     * Physical war consequences available to this actor's strategic decision.
     *
     * <p>Own quantities may come from authoritative own state. Opponent quantities must be limited to
     * physically observed/confirmed consequences; this record intentionally contains no hidden enemy
     * readiness or omniscient production capacity.</p>
     *
     * @param ownOperationalCombatShips currently physical and operational own combat ships
     * @param ownReactionMassKg current physical reaction mass available to committed own forces
     * @param ownRepairDemandKg current physical repair-material demand represented by the owning repair planner
     * @param ownRepairMaterialAvailableKg compatible physical repair material currently available to satisfy that demand
     * @param confirmedOwnDestroyedMassKg confirmed physical own constructed ship mass destroyed in the conflict
     * @param confirmedOwnUndeliveredCargoKg confirmed own cargo mass whose physical delivery failed or remains denied
     * @param observedOpponentDestroyedMassKg opponent constructed ship mass the actor has actually observed/confirmed destroyed
     * @param observedOpponentUndeliveredCargoKg opponent cargo mass the actor has actually observed/confirmed denied or undelivered
     */
    public record PhysicalWarEvidence(
            int ownOperationalCombatShips,
            double ownReactionMassKg,
            double ownRepairDemandKg,
            double ownRepairMaterialAvailableKg,
            double confirmedOwnDestroyedMassKg,
            double confirmedOwnUndeliveredCargoKg,
            double observedOpponentDestroyedMassKg,
            double observedOpponentUndeliveredCargoKg) {
        /**
         * Validates non-negative physical evidence.
         *
         * @param ownOperationalCombatShips physical operational own combat ships
         * @param ownReactionMassKg own physical reaction mass in kilograms
         * @param ownRepairDemandKg current physical repair-material demand in kilograms
         * @param ownRepairMaterialAvailableKg compatible physical repair material available in kilograms
         * @param confirmedOwnDestroyedMassKg confirmed destroyed own constructed mass in kilograms
         * @param confirmedOwnUndeliveredCargoKg confirmed own undelivered cargo mass in kilograms
         * @param observedOpponentDestroyedMassKg observed opponent destroyed constructed mass in kilograms
         * @param observedOpponentUndeliveredCargoKg observed opponent denied or undelivered cargo mass in kilograms
         */
        public PhysicalWarEvidence {
            if (ownOperationalCombatShips < 0) {
                throw new IllegalArgumentException("ownOperationalCombatShips must be non-negative");
            }
            requireNonNegativeFinite(ownReactionMassKg, "ownReactionMassKg");
            requireNonNegativeFinite(ownRepairDemandKg, "ownRepairDemandKg");
            requireNonNegativeFinite(ownRepairMaterialAvailableKg, "ownRepairMaterialAvailableKg");
            requireNonNegativeFinite(confirmedOwnDestroyedMassKg, "confirmedOwnDestroyedMassKg");
            requireNonNegativeFinite(confirmedOwnUndeliveredCargoKg, "confirmedOwnUndeliveredCargoKg");
            requireNonNegativeFinite(observedOpponentDestroyedMassKg, "observedOpponentDestroyedMassKg");
            requireNonNegativeFinite(observedOpponentUndeliveredCargoKg, "observedOpponentUndeliveredCargoKg");
        }
    }

    /**
     * Explicit strategic policy thresholds applied to physical quantities.
     *
     * @param minimumOperationalCombatShipsToSustain minimum physical operational ships required to continue current operations
     * @param minimumReactionMassKgToSustain minimum physical reaction mass required to continue current operations
     * @param requireRepairMaterialCoverage whether current compatible repair material must cover current repair demand
     * @param opponentDestroyedMassKgForCoerciveOffer observed opponent destroyed mass sufficient to justify proposing terms
     * @param opponentUndeliveredCargoKgForCoerciveOffer observed opponent denied cargo mass sufficient to justify proposing terms
     */
    public record Policy(
            int minimumOperationalCombatShipsToSustain,
            double minimumReactionMassKgToSustain,
            boolean requireRepairMaterialCoverage,
            double opponentDestroyedMassKgForCoerciveOffer,
            double opponentUndeliveredCargoKgForCoerciveOffer) {
        /**
         * Validates one explicit strategic policy.
         *
         * @param minimumOperationalCombatShipsToSustain minimum physical operational combat ships
         * @param minimumReactionMassKgToSustain minimum physical reaction mass in kilograms
         * @param requireRepairMaterialCoverage whether compatible repair stock must cover current repair demand
         * @param opponentDestroyedMassKgForCoerciveOffer observed destroyed opponent mass threshold in kilograms
         * @param opponentUndeliveredCargoKgForCoerciveOffer observed denied opponent cargo threshold in kilograms
         */
        public Policy {
            if (minimumOperationalCombatShipsToSustain < 0) {
                throw new IllegalArgumentException("minimumOperationalCombatShipsToSustain must be non-negative");
            }
            requireNonNegativeFinite(minimumReactionMassKgToSustain, "minimumReactionMassKgToSustain");
            requireNonNegativeFinite(opponentDestroyedMassKgForCoerciveOffer,
                    "opponentDestroyedMassKgForCoerciveOffer");
            requireNonNegativeFinite(opponentUndeliveredCargoKgForCoerciveOffer,
                    "opponentUndeliveredCargoKgForCoerciveOffer");
        }
    }

    /**
     * Settlement terms visible to the deciding actor.
     *
     * @param present whether an actual offer exists
     * @param objectiveIdsGrantedToActor exact objective IDs the offer explicitly grants to the actor
     */
    public record SettlementOffer(boolean present, Set<String> objectiveIdsGrantedToActor) {
        /**
         * Freezes deterministic settlement terms.
         *
         * @param present whether an actual settlement offer exists
         * @param objectiveIdsGrantedToActor exact objective IDs explicitly granted to the actor
         */
        public SettlementOffer {
            Objects.requireNonNull(objectiveIdsGrantedToActor, "objectiveIdsGrantedToActor");
            TreeSet<String> copy = new TreeSet<>();
            for (String value : objectiveIdsGrantedToActor) {
                copy.add(requireText(value, "objective ID"));
            }
            objectiveIdsGrantedToActor = Collections.unmodifiableSet(copy);
            if (!present && !objectiveIdsGrantedToActor.isEmpty()) {
                throw new IllegalArgumentException("absent settlement offer cannot grant objectives");
            }
        }

        /** @return an explicit absence of settlement terms */
        public static SettlementOffer none() {
            return new SettlementOffer(false, Set.of());
        }
    }

    /**
     * Complete immutable decision input.
     *
     * @param escalation current political escalation authorization
     * @param objectives actor-visible assessments of named political objectives
     * @param physicalEvidence actor-bounded physical consequences and own sustainment state
     * @param policy explicit political thresholds
     * @param settlementOffer currently visible settlement offer, if any
     */
    public record Input(
            EscalationLevel escalation,
            List<ObjectiveAssessment> objectives,
            PhysicalWarEvidence physicalEvidence,
            Policy policy,
            SettlementOffer settlementOffer) {
        /**
         * Validates and deterministically orders one decision input.
         *
         * @param escalation current political escalation authorization
         * @param objectives actor-visible named objective assessments
         * @param physicalEvidence actor-bounded physical consequences and own sustainment
         * @param policy explicit political thresholds
         * @param settlementOffer currently visible settlement offer
         */
        public Input {
            Objects.requireNonNull(escalation, "escalation");
            Objects.requireNonNull(objectives, "objectives");
            Objects.requireNonNull(physicalEvidence, "physicalEvidence");
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(settlementOffer, "settlementOffer");
            if (objectives.isEmpty()) {
                throw new IllegalArgumentException("at least one war objective is required");
            }
            ArrayList<ObjectiveAssessment> copy = new ArrayList<>(objectives);
            copy.sort(Comparator.comparing(value -> value.objective().id()));
            HashSet<String> ids = new HashSet<>();
            for (ObjectiveAssessment assessment : copy) {
                Objects.requireNonNull(assessment, "objective assessment");
                if (!ids.add(assessment.objective().id())) {
                    throw new IllegalArgumentException("duplicate objective ID: " + assessment.objective().id());
                }
            }
            objectives = List.copyOf(copy);
        }
    }

    /**
     * Inspectable result of one policy evaluation.
     *
     * @param decision selected political posture
     * @param canSustainCurrentOperations whether explicit physical sustainment requirements are currently met
     * @param observedOpponentMaterialPressure whether actor-visible physical pressure crosses either coercive threshold
     * @param unresolvedMandatoryObjectiveIds mandatory objectives not yet observed as met
     */
    public record Result(
            Decision decision,
            boolean canSustainCurrentOperations,
            boolean observedOpponentMaterialPressure,
            Set<String> unresolvedMandatoryObjectiveIds) {
        /**
         * Freezes one deterministic result.
         *
         * @param decision selected political posture
         * @param canSustainCurrentOperations whether physical sustainment requirements are met
         * @param observedOpponentMaterialPressure whether observed opponent pressure crosses a policy threshold
         * @param unresolvedMandatoryObjectiveIds mandatory objective IDs not yet observed as met
         */
        public Result {
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(unresolvedMandatoryObjectiveIds, "unresolvedMandatoryObjectiveIds");
            unresolvedMandatoryObjectiveIds = Collections.unmodifiableSet(new TreeSet<>(unresolvedMandatoryObjectiveIds));
        }
    }

    /**
     * Evaluates one strategic decision without reading or mutating world state.
     *
     * @param input actor-bounded objectives, physical evidence, policy and visible terms
     * @return deterministic political decision and inspectable derived predicates
     */
    public Result decide(Input input) {
        Input checked = Objects.requireNonNull(input, "input");
        Set<String> unresolvedMandatory = unresolvedMandatory(checked.objectives());
        boolean mandatoryImpossible = checked.objectives().stream()
                .anyMatch(value -> value.objective().mandatory()
                        && value.evidence() == ObjectiveEvidence.OBSERVED_IMPOSSIBLE);
        boolean allMandatoryObservedMet = unresolvedMandatory.isEmpty();
        boolean offerSatisfiesMandatory = checked.settlementOffer().present()
                && unresolvedMandatory.stream()
                .allMatch(checked.settlementOffer().objectiveIdsGrantedToActor()::contains);
        boolean sustainment = canSustain(checked.physicalEvidence(), checked.policy());
        boolean opponentPressure = coercivePressureObserved(checked.physicalEvidence(), checked.policy());

        Decision decision;
        if (offerSatisfiesMandatory) {
            decision = Decision.ACCEPT_SETTLEMENT;
        } else if (allMandatoryObservedMet) {
            decision = Decision.DE_ESCALATE;
        } else if (mandatoryImpossible || !sustainment) {
            decision = Decision.SEEK_SETTLEMENT;
        } else if (opponentPressure) {
            decision = Decision.OFFER_SETTLEMENT;
        } else if (checked.escalation() != EscalationLevel.GENERAL_WAR) {
            decision = Decision.ESCALATE;
        } else {
            decision = Decision.HOLD;
        }
        return new Result(decision, sustainment, opponentPressure, unresolvedMandatory);
    }

    private static Set<String> unresolvedMandatory(List<ObjectiveAssessment> objectives) {
        TreeSet<String> result = new TreeSet<>();
        for (ObjectiveAssessment assessment : objectives) {
            if (assessment.objective().mandatory() && assessment.evidence() != ObjectiveEvidence.OBSERVED_MET) {
                result.add(assessment.objective().id());
            }
        }
        return result;
    }

    private static boolean canSustain(PhysicalWarEvidence evidence, Policy policy) {
        if (evidence.ownOperationalCombatShips() < policy.minimumOperationalCombatShipsToSustain()) {
            return false;
        }
        if (evidence.ownReactionMassKg() + 1.0e-9d < policy.minimumReactionMassKgToSustain()) {
            return false;
        }
        return !policy.requireRepairMaterialCoverage()
                || evidence.ownRepairMaterialAvailableKg() + 1.0e-9d >= evidence.ownRepairDemandKg();
    }

    private static boolean coercivePressureObserved(PhysicalWarEvidence evidence, Policy policy) {
        boolean destroyedPressure = policy.opponentDestroyedMassKgForCoerciveOffer() > 0d
                && evidence.observedOpponentDestroyedMassKg() + 1.0e-9d
                >= policy.opponentDestroyedMassKgForCoerciveOffer();
        boolean cargoPressure = policy.opponentUndeliveredCargoKgForCoerciveOffer() > 0d
                && evidence.observedOpponentUndeliveredCargoKg() + 1.0e-9d
                >= policy.opponentUndeliveredCargoKgForCoerciveOffer();
        return destroyedPressure || cargoPressure;
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
