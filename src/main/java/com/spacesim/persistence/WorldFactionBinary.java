package com.spacesim.persistence;

import com.spacesim.world.EconomicBottleneckType;
import com.spacesim.world.FactionEconomicPressureState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionProductionPolicyState;
import com.spacesim.world.FactionRelationState;
import com.spacesim.world.FactionStockPolicyState;
import com.spacesim.world.FactionStrategicGoalState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.StarSystemId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class WorldFactionBinary {
    private static final int MAX_FACTIONS = 10_000;
    private static final int MAX_SYSTEMS = 100_000;
    private static final int MAX_POLICIES_PER_FACTION = 100_000;
    private static final int MAX_GOALS_PER_FACTION = 100_000;
    private static final int MAX_ECONOMIC_PRESSURE_STATES = 1_000_000;

    private WorldFactionBinary() {
        throw new AssertionError("Utility class");
    }

    static void writeEconomic(DataOutputStream out, List<FactionEconomicState> factions)
            throws IOException {
        WorldIoSupport.writeCount(out, factions.size(), MAX_FACTIONS, "factions");
        for (FactionEconomicState faction : factions) {
            WorldIoSupport.writeString(out, faction.factionContentId());
            out.writeLong(faction.treasuryMilliCredits());
            out.writeLong(faction.stationLiquidityReserveMilliCredits());
            out.writeLong(faction.maxLiquiditySupportPerDecisionMilliCredits());
        }
    }

    static List<FactionEconomicState> readEconomic(DataInputStream in) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_FACTIONS, "factions");
        List<FactionEconomicState> factions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            factions.add(new FactionEconomicState(
                    WorldIoSupport.readString(in),
                    in.readLong(),
                    in.readLong(),
                    in.readLong()));
        }
        return List.copyOf(factions);
    }

    static void writeStrategies(DataOutputStream out, List<FactionStrategicState> strategies)
            throws IOException {
        WorldIoSupport.writeCount(out, strategies.size(), MAX_FACTIONS, "factionStrategies");
        for (FactionStrategicState value : strategies) {
            WorldIoSupport.writeString(out, value.factionContentId());
            out.writeInt(value.minimumMarketAccessRelation());

            WorldIoSupport.writeCount(out, value.relations().size(), MAX_FACTIONS, "factionRelations");
            for (FactionRelationState relation : value.relations()) {
                WorldIoSupport.writeString(out, relation.targetFactionContentId());
                out.writeInt(relation.relation());
            }

            WorldIoSupport.writeCount(out, value.controlledSystems().size(), MAX_SYSTEMS, "controlledSystems");
            for (StarSystemId systemId : value.controlledSystems()) {
                out.writeLong(systemId.value());
            }

            out.writeInt(value.stationTaxBasisPoints());
            out.writeInt(value.foreignTerritoryTariffBasisPoints());

            WorldIoSupport.writeCount(
                    out, value.stockPolicies().size(), MAX_POLICIES_PER_FACTION, "stockPolicies");
            for (FactionStockPolicyState policy : value.stockPolicies()) {
                WorldIoSupport.writeString(out, policy.itemContentId());
                out.writeInt(policy.targetStockFloor());
            }

            WorldIoSupport.writeCount(
                    out, value.productionPolicies().size(), MAX_POLICIES_PER_FACTION, "productionPolicies");
            for (FactionProductionPolicyState policy : value.productionPolicies()) {
                WorldIoSupport.writeString(out, policy.stationArchetypeContentId());
                WorldIoSupport.writeString(out, policy.recipeContentId());
            }

            WorldIoSupport.writeCount(
                    out, value.strategicGoals().size(), MAX_GOALS_PER_FACTION, "strategicGoals");
            for (FactionStrategicGoalState goal : value.strategicGoals()) {
                WorldIoSupport.writeString(out, goal.goalId());
                WorldIoSupport.writeString(out, goal.type().name());
                WorldIoSupport.writeCount(
                        out, goal.demandFloors().size(), MAX_POLICIES_PER_FACTION, "goalDemandFloors");
                for (FactionStockPolicyState demand : goal.demandFloors()) {
                    WorldIoSupport.writeString(out, demand.itemContentId());
                    out.writeInt(demand.targetStockFloor());
                }
            }
        }
    }

    static List<FactionStrategicState> readStrategies(DataInputStream in) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_FACTIONS, "factionStrategies");
        List<FactionStrategicState> strategies = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String factionId = WorldIoSupport.readString(in);
            int threshold = in.readInt();

            int relationCount = WorldIoSupport.readCount(in, MAX_FACTIONS, "factionRelations");
            List<FactionRelationState> relations = new ArrayList<>(relationCount);
            for (int relationIndex = 0; relationIndex < relationCount; relationIndex++) {
                relations.add(new FactionRelationState(
                        WorldIoSupport.readString(in), in.readInt()));
            }

            int systemCount = WorldIoSupport.readCount(in, MAX_SYSTEMS, "controlledSystems");
            List<StarSystemId> controlledSystems = new ArrayList<>(systemCount);
            for (int systemIndex = 0; systemIndex < systemCount; systemIndex++) {
                controlledSystems.add(new StarSystemId(in.readLong()));
            }

            int stationTaxBasisPoints = in.readInt();
            int foreignTerritoryTariffBasisPoints = in.readInt();

            int stockCount = WorldIoSupport.readCount(
                    in, MAX_POLICIES_PER_FACTION, "stockPolicies");
            List<FactionStockPolicyState> stockPolicies = new ArrayList<>(stockCount);
            for (int policyIndex = 0; policyIndex < stockCount; policyIndex++) {
                stockPolicies.add(new FactionStockPolicyState(
                        WorldIoSupport.readString(in), in.readInt()));
            }

            int productionCount = WorldIoSupport.readCount(
                    in, MAX_POLICIES_PER_FACTION, "productionPolicies");
            List<FactionProductionPolicyState> productionPolicies =
                    new ArrayList<>(productionCount);
            for (int policyIndex = 0; policyIndex < productionCount; policyIndex++) {
                productionPolicies.add(new FactionProductionPolicyState(
                        WorldIoSupport.readString(in),
                        WorldIoSupport.readString(in)));
            }

            int goalCount = WorldIoSupport.readCount(in, MAX_GOALS_PER_FACTION, "strategicGoals");
            List<FactionStrategicGoalState> goals = new ArrayList<>(goalCount);
            for (int goalIndex = 0; goalIndex < goalCount; goalIndex++) {
                String goalId = WorldIoSupport.readString(in);
                FactionStrategicGoalState.GoalType type;
                try {
                    type = FactionStrategicGoalState.GoalType.valueOf(WorldIoSupport.readString(in));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Unknown strategic goal type", exception);
                }

                int demandCount = WorldIoSupport.readCount(
                        in, MAX_POLICIES_PER_FACTION, "goalDemandFloors");
                List<FactionStockPolicyState> demands = new ArrayList<>(demandCount);
                for (int demandIndex = 0; demandIndex < demandCount; demandIndex++) {
                    demands.add(new FactionStockPolicyState(
                            WorldIoSupport.readString(in), in.readInt()));
                }
                goals.add(new FactionStrategicGoalState(goalId, type, List.copyOf(demands)));
            }

            strategies.add(new FactionStrategicState(
                    factionId,
                    threshold,
                    List.copyOf(relations),
                    List.copyOf(controlledSystems),
                    stationTaxBasisPoints,
                    foreignTerritoryTariffBasisPoints,
                    List.copyOf(stockPolicies),
                    List.copyOf(productionPolicies),
                    List.copyOf(goals)));
        }
        return List.copyOf(strategies);
    }

    static void writePressures(DataOutputStream out, List<FactionEconomicPressureState> states)
            throws IOException {
        WorldIoSupport.writeCount(
                out, states.size(), MAX_ECONOMIC_PRESSURE_STATES, "economicPressureStates");
        for (FactionEconomicPressureState state : states) {
            WorldIoSupport.writeString(out, state.factionContentId());
            out.writeLong(state.systemId().value());
            WorldIoSupport.writeString(out, state.itemContentId());
            WorldIoSupport.writeString(out, state.bottleneckType().name());
            out.writeLong(state.firstObservedTick());
            out.writeLong(state.lastObservedTick());
            out.writeInt(state.consecutiveObservations());
            out.writeLong(state.peakUnmetDemandUnits());
            out.writeLong(state.lastUnmetDemandUnits());
            out.writeLong(state.cooldownUntilTick());
        }
    }

    static List<FactionEconomicPressureState> readPressures(DataInputStream in)
            throws IOException {
        int count = WorldIoSupport.readCount(
                in, MAX_ECONOMIC_PRESSURE_STATES, "economicPressureStates");
        List<FactionEconomicPressureState> states = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String factionId = WorldIoSupport.readString(in);
            StarSystemId systemId = new StarSystemId(in.readLong());
            String itemId = WorldIoSupport.readString(in);
            EconomicBottleneckType type;
            try {
                type = EconomicBottleneckType.valueOf(WorldIoSupport.readString(in));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown EconomicBottleneckType", exception);
            }
            states.add(new FactionEconomicPressureState(
                    factionId,
                    systemId,
                    itemId,
                    type,
                    in.readLong(),
                    in.readLong(),
                    in.readInt(),
                    in.readLong(),
                    in.readLong(),
                    in.readLong()));
        }
        return List.copyOf(states);
    }
}
