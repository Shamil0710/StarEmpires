package com.spacesim.world;

import com.spacesim.world.DiplomaticLifecycleService.ProposalRequest;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.RelationMemory;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Stage-21B to Stage-21C planning seam for autonomous diplomatic proposals.
 *
 * <p>The planner consumes only a persisted accepted strategic-intent aggregate plus actor-bounded
 * Stage-21C relation memory. It has no {@link WorldSimulation} reference and therefore cannot read
 * hidden treasury, military, territorial or market truth. The resulting request is still validated
 * by {@link DiplomaticLifecycleService}, which delegates executable legal effects to the existing
 * Stage-17 authorities.</p>
 */
public final class StrategicDiplomaticProposalPlanner {
    private StrategicDiplomaticProposalPlanner() {
        throw new AssertionError("No instances");
    }

    /**
     * Converts one currently active persisted strategic goal into a deterministic diplomatic request.
     *
     * <p>Goals that are intrinsically non-diplomatic at this stage (stockpile/explore) return empty.
     * A stalled or terminal goal cannot issue a new autonomous proposal. Relation memory is used only
     * as actor-known evidence to choose between lawful diplomatic variants; no random value may choose
     * an escalatory result.</p>
     *
     * @param intent persisted Stage-21B intent aggregate that owns the goal
     * @param goalId exact persisted goal identity to consume
     * @param recipientFactionId actor-known diplomatic counterparty
     * @param relationMemory directed actor-bounded memory for owner to recipient
     * @param deadlineTick future Stage-21C response deadline
     * @return deterministic request, or empty when the active goal has no Stage-21C diplomatic action
     */
    public static Optional<ProposalRequest> plan(
            FactionStrategicIntentState intent,
            String goalId,
            String recipientFactionId,
            RelationMemory relationMemory,
            long deadlineTick) {
        FactionStrategicIntentState checkedIntent = Objects.requireNonNull(intent, "Strategic intent not set");
        String checkedGoalId = requireText(goalId, "Strategic goal ID");
        String recipient = requireText(recipientFactionId, "Diplomatic recipient");
        RelationMemory memory = Objects.requireNonNull(relationMemory, "Relation memory not set");
        if (!memory.ownerFactionId().equals(checkedIntent.factionContentId())
                || !memory.targetFactionId().equals(recipient)) {
            throw new IllegalArgumentException("Relation memory does not match strategic-goal diplomatic pair");
        }
        if (deadlineTick < 0L) {
            throw new IllegalArgumentException("Proposal deadline cannot be negative");
        }

        StrategicGoalState goal = checkedIntent.goals().stream()
                .filter(candidate -> candidate.goalId().equals(checkedGoalId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Strategic goal is not owned by supplied Stage-21B intent: " + checkedGoalId));
        if (goal.lifecycle() != StrategicGoalState.Lifecycle.ACTIVE) {
            return Optional.empty();
        }

        int relation = memory.derivedRelation();
        ProposalKind kind = proposalKind(goal.type(), relation);
        if (kind == null) {
            return Optional.empty();
        }
        List<Term> demands = defaultDemands(goal, kind);
        return Optional.of(new ProposalRequest(
                goal.goalId(),
                checkedIntent.factionContentId(),
                recipient,
                kind,
                goal.targetId(),
                demands,
                List.of(),
                deadlineTick));
    }

    private static ProposalKind proposalKind(StrategicGoalType type, int relation) {
        return switch (Objects.requireNonNull(type, "Strategic goal type not set")) {
            case OBTAIN_ACCESS -> relation >= 20 ? ProposalKind.TRADE : ProposalKind.ACCESS;
            case SECURE_ROUTE, ESCORT, DEFEND ->
                    relation >= 0 ? ProposalKind.DEFENSIVE_COOPERATION : ProposalKind.NON_AGGRESSION;
            case DETER -> relation >= 25 ? ProposalKind.DEFENSIVE_COOPERATION : ProposalKind.NON_AGGRESSION;
            case COERCE -> relation <= -40 ? ProposalKind.ULTIMATUM : ProposalKind.EMBARGO;
            case CLAIM, RECOVER -> ProposalKind.RECOGNITION;
            case BLOCKADE -> ProposalKind.EMBARGO;
            case RAID, INVADE -> ProposalKind.ULTIMATUM;
            case STOCKPILE, EXPLORE -> null;
        };
    }

    private static List<Term> defaultDemands(StrategicGoalState goal, ProposalKind kind) {
        if (kind == ProposalKind.ACCESS || kind == ProposalKind.TRADE) {
            return List.of(new Term(TermKind.MARKET_ACCESS, goal.targetId(), 0L));
        }
        if (kind == ProposalKind.RECOGNITION && numericSystemId(goal.targetId())) {
            return List.of(new Term(TermKind.TERRITORIAL_RECOGNITION, goal.targetId(), 0L));
        }
        return List.of();
    }

    private static boolean numericSystemId(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0L;
        } catch (NumberFormatException exception) {
            return false;
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
