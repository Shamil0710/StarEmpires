package com.spacesim.world;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic Stage-21B goal selection foundation.
 *
 * <p>The planner consumes already resolved interests and feasibility reports. It does not inspect
 * hidden world state and does not issue economy, diplomacy or fleet commands.</p>
 */
public final class FactionStrategicGoalPlanner {
    private FactionStrategicGoalPlanner() {
        throw new AssertionError("Utility class");
    }

    public record GoalCandidate(
            FactionStrategicGoal goal,
            boolean physicallyFeasible,
            String feasibilityReason) {

        public GoalCandidate {
            Objects.requireNonNull(goal, "Goal not set");
            feasibilityReason = require(feasibilityReason, "Feasibility reason");
        }
    }

    public static List<FactionStrategicGoal> select(
            Collection<GoalCandidate> candidates,
            int budget) {
        Objects.requireNonNull(candidates, "Candidates not set");
        if (budget <= 0) {
            throw new IllegalArgumentException("Goal budget must be positive");
        }
        return candidates.stream()
                .filter(GoalCandidate::physicallyFeasible)
                .map(GoalCandidate::goal)
                .sorted()
                .limit(budget)
                .toList();
    }

    private static String require(String value, String label) {
        String result = Objects.requireNonNull(value, label + " not set").strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return result;
    }
}
