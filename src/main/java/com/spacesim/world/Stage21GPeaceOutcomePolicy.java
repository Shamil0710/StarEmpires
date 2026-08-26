package com.spacesim.world;

import com.spacesim.warfare.StrategicWarPolicyService;
import com.spacesim.warfare.StrategicWarPolicyService.DecisionResult;
import com.spacesim.warfare.StrategicWarPolicyService.EscalationLevel;
import com.spacesim.warfare.StrategicWarPolicyService.Input;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveAssessment;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveEvidence;
import com.spacesim.warfare.StrategicWarPolicyService.PhysicalWarEvidence;
import com.spacesim.warfare.StrategicWarPolicyService.Policy;
import com.spacesim.warfare.StrategicWarPolicyService.SettlementOffer;
import com.spacesim.warfare.StrategicWarPolicyService.WarObjective;
import com.spacesim.world.DiplomaticLifecycleState.War;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stage-21G bridge from persistent Stage-21C legal war goals to the existing Stage-19 settlement policy.
 *
 * <p>This class deliberately owns no war score, exhaustion currency or diplomatic mutation. The
 * caller supplies only actor-known objective evidence, physical sustainment/loss evidence and the
 * objective IDs covered by an actor-visible offer. Stage 19 remains the sole decision authority;
 * Stage 21C remains the sole legal ceasefire/peace acceptance authority.</p>
 */
public final class Stage21GPeaceOutcomePolicy {
    private final StrategicWarPolicyService policyService;

    /** Creates the bridge over the existing Stage-19 policy authority. */
    public Stage21GPeaceOutcomePolicy(StrategicWarPolicyService policyService) {
        this.policyService = Objects.requireNonNull(policyService, "policyService");
    }

    /**
     * Evaluates one participant's settlement posture from legal goals and physical evidence.
     *
     * @param war persistent Stage-21C legal war
     * @param actorFactionId participant whose bounded knowledge is evaluated
     * @param escalation current Stage-19 escalation level
     * @param objectiveEvidenceByGoalId actor-known evidence keyed by its legal war-goal ID; missing is UNKNOWN
     * @param physicalEvidence actor-bounded physical losses/sustainment/leverage evidence
     * @param policy existing Stage-19 thresholds
     * @param offer actor-visible offer coverage, or {@link SettlementOffer#none()}
     * @return unmodified Stage-19 decision result
     */
    public DecisionResult evaluate(
            War war,
            String actorFactionId,
            EscalationLevel escalation,
            Map<String, ObjectiveEvidence> objectiveEvidenceByGoalId,
            PhysicalWarEvidence physicalEvidence,
            Policy policy,
            SettlementOffer offer) {
        War checkedWar = Objects.requireNonNull(war, "war");
        String actor = requireText(actorFactionId, "actorFactionId");
        if (!actor.equals(checkedWar.factionA()) && !actor.equals(checkedWar.factionB())) {
            throw new IllegalArgumentException("Settlement-policy actor is not a legal war participant");
        }
        Map<String, ObjectiveEvidence> evidence = Map.copyOf(
                Objects.requireNonNull(objectiveEvidenceByGoalId, "objectiveEvidenceByGoalId"));
        SettlementOffer checkedOffer = Objects.requireNonNull(offer, "offer");

        ArrayList<WarGoal> actorGoals = new ArrayList<>();
        for (WarGoal goal : checkedWar.goals()) {
            if (actor.equals(goal.sponsorFactionId())) {
                actorGoals.add(goal);
            }
        }
        actorGoals.sort(Comparator.comparing(WarGoal::goalId));
        if (actorGoals.isEmpty()) {
            throw new IllegalStateException("Legal war participant has no actor-owned war goals");
        }

        Set<String> legalGoalIds = new HashSet<>();
        ArrayList<ObjectiveAssessment> assessments = new ArrayList<>();
        for (WarGoal goal : actorGoals) {
            legalGoalIds.add(goal.goalId());
            assessments.add(new ObjectiveAssessment(
                    new WarObjective(goal.goalId(), goal.subjectId(), goal.mandatory()),
                    evidence.getOrDefault(goal.goalId(), ObjectiveEvidence.UNKNOWN)));
        }
        if (!legalGoalIds.containsAll(checkedOffer.grantedObjectiveIds())) {
            throw new IllegalArgumentException("Visible settlement offer claims unknown/non-actor legal war goals");
        }
        for (String goalId : evidence.keySet()) {
            if (!legalGoalIds.contains(goalId)) {
                throw new IllegalArgumentException("Objective evidence references unknown/non-actor legal war goal: " + goalId);
            }
        }

        return policyService.decide(new Input(
                Objects.requireNonNull(escalation, "escalation"),
                assessments,
                Objects.requireNonNull(physicalEvidence, "physicalEvidence"),
                Objects.requireNonNull(policy, "policy"),
                checkedOffer));
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
