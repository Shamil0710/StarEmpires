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
 * Stage-11D deterministic coordinator for multiple concurrent faction growth plans.
 *
 * <p>The coordinator lets all incomplete plans continue using the ordinary Stage-11C physical
 * executor. When one or more anchor projects for the same target are already completed, it first
 * resolves the winner from completion time and stable plan identity, lets that plan claim, then
 * advances all remaining plans. Losers therefore observe foreign territory and fail through the
 * same non-combat rule used by the single-plan runtime.</p>
 */
public final class FactionExpansionCompetitionCoordinator {
    private FactionExpansionCompetitionCoordinator() {
        throw new AssertionError("Utility class");
    }

    /**
     * Advances every active growth plan once in deterministic competition order.
     *
     * @param runtime authoritative Stage-11 physical growth runtime
     * @return canonical current plan snapshots after the decision
     */
    public static List<StrategicGrowthState.Plan> advanceAll(FactionExpansionRuntime runtime) {
        FactionExpansionRuntime growth = Objects.requireNonNull(runtime, "FactionExpansionRuntime not set");
        List<StrategicGrowthState.Plan> initial = activePlans(growth.snapshot());
        Map<StarSystemId, StrategicGrowthState.PlanId> winners = completedWinners(growth, initial);
        Set<StrategicGrowthState.PlanId> advanced = new HashSet<>();

        List<StrategicGrowthState.PlanId> winningIds = new ArrayList<>(winners.values());
        winningIds.sort(Comparator.naturalOrder());
        for (StrategicGrowthState.PlanId winner : winningIds) {
            growth.advancePlan(winner);
            advanced.add(winner);
        }

        for (StrategicGrowthState.Plan plan : activePlans(growth.snapshot())) {
            if (!advanced.contains(plan.id())) {
                growth.advancePlan(plan.id());
            }
        }
        return allPlans(growth.snapshot());
    }

    private static Map<StarSystemId, StrategicGrowthState.PlanId> completedWinners(
            FactionExpansionRuntime runtime,
            List<StrategicGrowthState.Plan> plans) {
        Map<StarSystemId, List<StrategicGrowthState.Plan>> plansByTarget = new HashMap<>();
        for (StrategicGrowthState.Plan plan : plans) {
            if (completed(runtime, plan)) {
                plansByTarget.computeIfAbsent(plan.targetSystemId(), ignored -> new ArrayList<>()).add(plan);
            }
        }
        Map<StarSystemId, StrategicGrowthState.PlanId> winners = new HashMap<>();
        for (Map.Entry<StarSystemId, List<StrategicGrowthState.Plan>> entry : plansByTarget.entrySet()) {
            String controller = runtime.world().controllingFaction(entry.getKey()).orElse("");
            StrategicGrowthCompetitionResolver.chooseWinner(
                            entry.getKey(),
                            controller,
                            entry.getValue(),
                            runtime.world().getConstructionProjects())
                    .ifPresent(winner -> winners.put(entry.getKey(), winner));
        }
        return winners;
    }

    private static boolean completed(
            FactionExpansionRuntime runtime,
            StrategicGrowthState.Plan plan) {
        return plan.anchorProjectId() != null
                && runtime.world().findConstructionProject(plan.anchorProjectId())
                .map(project -> project.status() == ConstructionProjectStatus.COMPLETED)
                .orElse(false);
    }

    private static List<StrategicGrowthState.Plan> activePlans(WorldState state) {
        List<StrategicGrowthState.Plan> result = new ArrayList<>();
        for (FactionStrategicState strategy : state.factionStrategies()) {
            for (StrategicGrowthState.Plan plan : StrategicGrowthPlanService.plans(strategy)) {
                if (!plan.status().terminal()) {
                    result.add(plan);
                }
            }
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static List<StrategicGrowthState.Plan> allPlans(WorldState state) {
        List<StrategicGrowthState.Plan> result = new ArrayList<>();
        for (FactionStrategicState strategy : state.factionStrategies()) {
            result.addAll(StrategicGrowthPlanService.plans(strategy));
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }
}
