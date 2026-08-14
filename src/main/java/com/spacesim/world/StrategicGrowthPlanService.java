package com.spacesim.world;

import com.spacesim.content.ContentCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure immutable operations for Stage-11 persistent spatial growth plans.
 *
 * <p>The service never mutates WorldSimulation, wallets, fleets or territory. It transforms one
 * {@link FactionStrategicState} into another so the result remains part of normal world
 * persistence. Physical construction and logistics belong to Stage 11C.</p>
 */
public final class StrategicGrowthPlanService {
    private static final String GOAL_PREFIX = "growth-plan:";

    private StrategicGrowthPlanService() {
        throw new AssertionError("Utility class");
    }

    /**
     * Creates one deterministic PLANNED expansion goal from a Stage-11A opportunity.
     *
     * @param strategy current persistent faction strategic state
     * @param opportunity selected Stage-11A opportunity
     * @param content canonical content catalog
     * @param createdTick authoritative creation tick
     * @return new faction strategic state containing the plan
     */
    public static FactionStrategicState createPlan(
            FactionStrategicState strategy,
            ExpansionOpportunity opportunity,
            ContentCatalog content,
            long createdTick) {
        FactionStrategicState current = Objects.requireNonNull(strategy, "Faction strategy not set");
        ExpansionOpportunity selected = Objects.requireNonNull(opportunity, "Expansion opportunity not set");
        ContentCatalog catalog = Objects.requireNonNull(content, "ContentCatalog not set");
        if (createdTick < 0L) {
            throw new IllegalArgumentException("Growth plan creation tick cannot be negative");
        }
        if (!current.factionContentId().equals(selected.factionContentId())) {
            throw new IllegalArgumentException("Opportunity belongs to another faction");
        }
        if (current.controls(selected.targetSystemId())) {
            throw new IllegalArgumentException("Faction already controls expansion target");
        }
        for (StrategicGrowthState.Plan plan : plans(current)) {
            if (!plan.status().terminal() && plan.targetSystemId().equals(selected.targetSystemId())) {
                throw new IllegalStateException("Faction already has an active growth plan for target");
            }
        }

        ContentCatalog.StationArchetypeDefinition anchor =
                catalog.findStationArchetype(selected.anchorStationArchetypeContentId());
        if (anchor == null || anchor.construction() == null || anchor.construction().materials().isEmpty()) {
            throw new IllegalArgumentException("Expansion anchor is not constructible");
        }
        if (selected.constructionFundingMilliCredits() <= 0L) {
            throw new IllegalArgumentException("Expansion opportunity has invalid funding requirement");
        }

        long sequence = nextSequence(current);
        StrategicGrowthState.PlanId planId = new StrategicGrowthState.PlanId(current.factionContentId(), sequence);
        List<StrategicGrowthState.StockTarget> stockTargets = stockTargets(anchor.construction().materials());
        List<FactionStockPolicyState> demandFloors = new ArrayList<>(stockTargets.size());
        for (StrategicGrowthState.StockTarget target : stockTargets) {
            demandFloors.add(new FactionStockPolicyState(target.itemContentId(), target.targetAmount()));
        }

        StrategicGrowthState.Plan plan = new StrategicGrowthState.Plan(
                planId,
                selected.sourceSystemId(),
                selected.targetSystemId(),
                reasonFor(selected),
                selected.anchorStationArchetypeContentId(),
                null,
                0,
                List.of(),
                stockTargets,
                selected.constructionFundingMilliCredits(),
                StrategicGrowthState.Status.PLANNED,
                createdTick,
                createdTick,
                -1L);
        FactionStrategicGoalState goal = new FactionStrategicGoalState(
                GOAL_PREFIX + sequence,
                FactionStrategicGoalState.GoalType.EXPANSION,
                demandFloors,
                plan);

        List<FactionStrategicGoalState> goals = new ArrayList<>(current.strategicGoals());
        goals.add(goal);
        goals.sort(Comparator.naturalOrder());
        return copyWithGoals(current, goals);
    }

    /**
     * Replaces an existing persistent plan without changing its goal identity or demand floors.
     *
     * @param strategy current faction strategy
     * @param updated replacement plan with the same PlanId
     * @return new faction strategy
     */
    public static FactionStrategicState replacePlan(
            FactionStrategicState strategy,
            StrategicGrowthState.Plan updated) {
        FactionStrategicState current = Objects.requireNonNull(strategy, "Faction strategy not set");
        StrategicGrowthState.Plan replacement = Objects.requireNonNull(updated, "Updated growth plan not set");
        if (!current.factionContentId().equals(replacement.id().ownerContentId())) {
            throw new IllegalArgumentException("Growth plan belongs to another faction");
        }
        List<FactionStrategicGoalState> goals = new ArrayList<>(current.strategicGoals().size());
        boolean found = false;
        for (FactionStrategicGoalState goal : current.strategicGoals()) {
            StrategicGrowthState.Plan existing = goal.growthPlan();
            if (existing != null && existing.id().equals(replacement.id())) {
                goals.add(new FactionStrategicGoalState(
                        goal.goalId(), goal.type(), goal.demandFloors(), replacement));
                found = true;
            } else {
                goals.add(goal);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Growth plan is not part of faction strategy: " + replacement.id());
        }
        return copyWithGoals(current, goals);
    }

    /**
     * Lists physical growth plans in deterministic PlanId order.
     *
     * @param strategy faction strategic state
     * @return immutable plan list
     */
    public static List<StrategicGrowthState.Plan> plans(FactionStrategicState strategy) {
        List<StrategicGrowthState.Plan> result = new ArrayList<>();
        for (FactionStrategicGoalState goal : Objects.requireNonNull(strategy, "Faction strategy not set").strategicGoals()) {
            if (goal.growthPlan() != null) {
                result.add(goal.growthPlan());
            }
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    /**
     * Finds one plan by stable identity.
     *
     * @param strategy faction strategic state
     * @param id plan identity
     * @return matching plan
     */
    public static Optional<StrategicGrowthState.Plan> findPlan(
            FactionStrategicState strategy,
            StrategicGrowthState.PlanId id) {
        if (id == null) {
            return Optional.empty();
        }
        for (StrategicGrowthState.Plan plan : plans(strategy)) {
            if (plan.id().equals(id)) {
                return Optional.of(plan);
            }
        }
        return Optional.empty();
    }

    /**
     * Creates a copy with a new lifecycle status and optional construction-project link.
     *
     * @param plan current plan
     * @param status target status
     * @param projectId linked construction project or null when allowed by target status
     * @param tick authoritative transition tick
     * @return validated replacement plan
     */
    public static StrategicGrowthState.Plan transition(
            StrategicGrowthState.Plan plan,
            StrategicGrowthState.Status status,
            ConstructionProjectId projectId,
            long tick) {
        StrategicGrowthState.Plan current = Objects.requireNonNull(plan, "Growth plan not set");
        StrategicGrowthState.Status target = Objects.requireNonNull(status, "Growth status not set");
        if (tick < current.stateChangedTick()) {
            throw new IllegalArgumentException("Growth transition cannot move backwards in time");
        }
        if (!transitionAllowed(current.status(), target)) {
            throw new IllegalStateException("Illegal growth transition: " + current.status() + " -> " + target);
        }
        long terminalTick = target.terminal() ? tick : -1L;
        return new StrategicGrowthState.Plan(
                current.id(),
                current.sourceSystemId(),
                current.targetSystemId(),
                current.reason(),
                current.anchorArchetypeContentId(),
                projectId,
                current.requiredSupportFleetCount(),
                current.assignedSupportFleetIds(),
                current.initialStockTargets(),
                current.approvedBudgetMilliCredits(),
                target,
                current.createdTick(),
                tick,
                terminalTick);
    }

    private static boolean transitionAllowed(StrategicGrowthState.Status from, StrategicGrowthState.Status to) {
        if (from.terminal()) {
            return false;
        }
        return switch (from) {
            case PLANNED -> to == StrategicGrowthState.Status.APPROVED
                    || to == StrategicGrowthState.Status.CANCELLED
                    || to == StrategicGrowthState.Status.FAILED;
            case APPROVED -> to == StrategicGrowthState.Status.EXECUTING
                    || to == StrategicGrowthState.Status.CANCELLED
                    || to == StrategicGrowthState.Status.FAILED;
            case EXECUTING -> to == StrategicGrowthState.Status.ESTABLISHED
                    || to == StrategicGrowthState.Status.FAILED;
            case ESTABLISHED, CANCELLED, FAILED -> false;
        };
    }

    private static long nextSequence(FactionStrategicState strategy) {
        long maximum = 0L;
        for (StrategicGrowthState.Plan plan : plans(strategy)) {
            maximum = Math.max(maximum, plan.id().sequence());
        }
        if (maximum == Long.MAX_VALUE) {
            throw new IllegalStateException("Growth plan sequence exhausted");
        }
        return maximum + 1L;
    }

    private static List<StrategicGrowthState.StockTarget> stockTargets(Map<String, Integer> materials) {
        List<StrategicGrowthState.StockTarget> targets = new ArrayList<>(materials.size());
        for (Map.Entry<String, Integer> material : materials.entrySet()) {
            targets.add(new StrategicGrowthState.StockTarget(material.getKey(), material.getValue()));
        }
        targets.sort(Comparator.naturalOrder());
        return List.copyOf(targets);
    }

    private static StrategicGrowthState.Reason reasonFor(ExpansionOpportunity opportunity) {
        boolean resources = opportunity.remainingMineableUnits() > 0L;
        boolean demand = opportunity.unmetDemandUnits() > 0L;
        if (resources && !demand) {
            return StrategicGrowthState.Reason.RESOURCE_ACCESS;
        }
        if (demand && !resources) {
            return StrategicGrowthState.Reason.MARKET_DEMAND;
        }
        if (!resources && !demand && opportunity.marketCount() > 0) {
            return StrategicGrowthState.Reason.TRADE_NETWORK;
        }
        if (!resources && !demand && opportunity.path().jumpCount() > 1) {
            return StrategicGrowthState.Reason.STRATEGIC_REACH;
        }
        return StrategicGrowthState.Reason.BALANCED;
    }

    private static FactionStrategicState copyWithGoals(
            FactionStrategicState state,
            List<FactionStrategicGoalState> goals) {
        return new FactionStrategicState(
                state.factionContentId(),
                state.minimumMarketAccessRelation(),
                state.relations(),
                state.controlledSystems(),
                state.stationTaxBasisPoints(),
                state.foreignTerritoryTariffBasisPoints(),
                state.stockPolicies(),
                state.productionPolicies(),
                goals);
    }
}
