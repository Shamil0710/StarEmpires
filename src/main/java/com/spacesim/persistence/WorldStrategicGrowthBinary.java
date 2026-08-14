package com.spacesim.persistence;

import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.FactionStrategicGoalState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicGrowthState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stage-11 file-format trailer for persistent strategic growth plans. */
final class WorldStrategicGrowthBinary {
    private static final int MAX_PLANS = 100_000;
    private static final int MAX_SUPPORT_FLEETS = 10_000;
    private static final int MAX_STOCK_TARGETS = 100_000;

    private WorldStrategicGrowthBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<FactionStrategicState> strategies) throws IOException {
        int count = 0;
        for (FactionStrategicState strategy : strategies) {
            for (FactionStrategicGoalState goal : strategy.strategicGoals()) {
                if (goal.growthPlan() != null) {
                    count++;
                }
            }
        }
        WorldIoSupport.writeCount(out, count, MAX_PLANS, "strategicGrowthPlans");
        for (FactionStrategicState strategy : strategies) {
            for (FactionStrategicGoalState goal : strategy.strategicGoals()) {
                StrategicGrowthState.Plan plan = goal.growthPlan();
                if (plan == null) {
                    continue;
                }
                if (!strategy.factionContentId().equals(plan.id().ownerContentId())) {
                    throw new IllegalArgumentException("Growth plan owner differs from faction strategy");
                }
                WorldIoSupport.writeString(out, strategy.factionContentId());
                WorldIoSupport.writeString(out, goal.goalId());
                out.writeLong(plan.id().sequence());
                out.writeLong(plan.sourceSystemId().value());
                out.writeLong(plan.targetSystemId().value());
                WorldIoSupport.writeString(out, plan.reason().name());
                WorldIoSupport.writeString(out, plan.anchorArchetypeContentId());
                out.writeBoolean(plan.anchorProjectId() != null);
                if (plan.anchorProjectId() != null) {
                    out.writeLong(plan.anchorProjectId().value());
                }
                out.writeInt(plan.requiredSupportFleetCount());
                WorldIoSupport.writeCount(
                        out, plan.assignedSupportFleetIds().size(), MAX_SUPPORT_FLEETS, "growthSupportFleets");
                for (FleetId fleetId : plan.assignedSupportFleetIds()) {
                    out.writeLong(fleetId.value());
                }
                WorldIoSupport.writeCount(
                        out, plan.initialStockTargets().size(), MAX_STOCK_TARGETS, "growthStockTargets");
                for (StrategicGrowthState.StockTarget target : plan.initialStockTargets()) {
                    WorldIoSupport.writeString(out, target.itemContentId());
                    out.writeInt(target.targetAmount());
                }
                out.writeLong(plan.approvedBudgetMilliCredits());
                WorldIoSupport.writeString(out, plan.status().name());
                out.writeLong(plan.createdTick());
                out.writeLong(plan.stateChangedTick());
                out.writeLong(plan.terminalTick());
            }
        }
    }

    static List<FactionStrategicState> readAndAttach(
            DataInputStream in,
            List<FactionStrategicState> strategies) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_PLANS, "strategicGrowthPlans");
        if (count == 0) {
            return strategies;
        }
        Map<String, StrategicGrowthState.Plan> plansByGoal = new HashMap<>();
        for (int index = 0; index < count; index++) {
            String factionId = WorldIoSupport.readString(in);
            String goalId = WorldIoSupport.readString(in);
            long sequence = in.readLong();
            StarSystemId source = new StarSystemId(in.readLong());
            StarSystemId target = new StarSystemId(in.readLong());
            StrategicGrowthState.Reason reason = readReason(in);
            String anchor = WorldIoSupport.readString(in);
            ConstructionProjectId projectId = in.readBoolean() ? new ConstructionProjectId(in.readLong()) : null;
            int requiredSupport = in.readInt();
            int fleetCount = WorldIoSupport.readCount(in, MAX_SUPPORT_FLEETS, "growthSupportFleets");
            List<FleetId> fleets = new ArrayList<>(fleetCount);
            for (int fleetIndex = 0; fleetIndex < fleetCount; fleetIndex++) {
                fleets.add(new FleetId(in.readLong()));
            }
            int stockCount = WorldIoSupport.readCount(in, MAX_STOCK_TARGETS, "growthStockTargets");
            List<StrategicGrowthState.StockTarget> stockTargets = new ArrayList<>(stockCount);
            for (int stockIndex = 0; stockIndex < stockCount; stockIndex++) {
                stockTargets.add(new StrategicGrowthState.StockTarget(
                        WorldIoSupport.readString(in), in.readInt()));
            }
            StrategicGrowthState.Plan plan = new StrategicGrowthState.Plan(
                    new StrategicGrowthState.PlanId(factionId, sequence),
                    source,
                    target,
                    reason,
                    anchor,
                    projectId,
                    requiredSupport,
                    fleets,
                    stockTargets,
                    in.readLong(),
                    readStatus(in),
                    in.readLong(),
                    in.readLong(),
                    in.readLong());
            String key = key(factionId, goalId);
            if (plansByGoal.putIfAbsent(key, plan) != null) {
                throw new IllegalArgumentException("Duplicate strategic growth trailer key: " + key);
            }
        }

        List<FactionStrategicState> result = new ArrayList<>(strategies.size());
        int attached = 0;
        for (FactionStrategicState strategy : strategies) {
            List<FactionStrategicGoalState> goals = new ArrayList<>(strategy.strategicGoals().size());
            for (FactionStrategicGoalState goal : strategy.strategicGoals()) {
                StrategicGrowthState.Plan plan = plansByGoal.get(key(strategy.factionContentId(), goal.goalId()));
                if (plan == null) {
                    goals.add(goal);
                    continue;
                }
                if (goal.type() != FactionStrategicGoalState.GoalType.EXPANSION) {
                    throw new IllegalArgumentException("Growth trailer references non-EXPANSION goal");
                }
                goals.add(new FactionStrategicGoalState(goal.goalId(), goal.type(), goal.demandFloors(), plan));
                attached++;
            }
            result.add(copyWithGoals(strategy, goals));
        }
        if (attached != plansByGoal.size()) {
            throw new IllegalArgumentException("Growth trailer references unknown faction/goal");
        }
        return List.copyOf(result);
    }

    private static StrategicGrowthState.Reason readReason(DataInputStream in) throws IOException {
        try {
            return StrategicGrowthState.Reason.valueOf(WorldIoSupport.readString(in));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown strategic growth reason", exception);
        }
    }

    private static StrategicGrowthState.Status readStatus(DataInputStream in) throws IOException {
        try {
            return StrategicGrowthState.Status.valueOf(WorldIoSupport.readString(in));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown strategic growth status", exception);
        }
    }

    private static String key(String factionId, String goalId) {
        return factionId + '\u0000' + goalId;
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
