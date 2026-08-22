package com.spacesim.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactionStrategicGoalPlannerTest {

    @Test
    void impossibleGoalsAreDeferredBeforeSelection() {
        FactionStrategicGoal possible = goal("secure", FactionStrategicGoal.GoalFamily.SECURE_ROUTE, 70);
        FactionStrategicGoal impossible = goal("invade", FactionStrategicGoal.GoalFamily.INVADE, 100);

        List<FactionStrategicGoal> result = FactionStrategicGoalPlanner.select(
                List.of(
                        new FactionStrategicGoalPlanner.GoalCandidate(impossible, false, "no logistics"),
                        new FactionStrategicGoalPlanner.GoalCandidate(possible, true, "route available")),
                5);

        assertEquals(List.of(possible), result);
    }

    @Test
    void orderingIsStableAndDoesNotChurnForSameEvidence() {
        FactionStrategicGoal high = goal("a", FactionStrategicGoal.GoalFamily.DEFEND, 90);
        FactionStrategicGoal low = goal("b", FactionStrategicGoal.GoalFamily.EXPLORE, 20);

        assertEquals(
                List.of(high, low),
                FactionStrategicGoalPlanner.select(
                        List.of(
                                new FactionStrategicGoalPlanner.GoalCandidate(low, true, "ok"),
                                new FactionStrategicGoalPlanner.GoalCandidate(high, true, "ok")),
                        2));
    }

    private static FactionStrategicGoal goal(String id, FactionStrategicGoal.GoalFamily family, int urgency) {
        return new FactionStrategicGoal(
                id,
                family,
                "target",
                List.of("evidence"),
                urgency,
                1000,
                "achieved",
                "failed",
                100,
                50);
    }
}
