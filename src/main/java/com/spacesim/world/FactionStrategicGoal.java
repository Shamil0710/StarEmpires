package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * Durable explainable intent selected by an autonomous faction.
 *
 * <p>This is not an execution order. Later Stage-21 slices translate accepted intent into
 * diplomacy, logistics and fleet commands through existing authorities.</p>
 */
public record FactionStrategicGoal(
        String goalId,
        GoalFamily family,
        String targetId,
        List<String> evidenceIds,
        int urgency,
        long costCeiling,
        String successCondition,
        String failureCondition,
        long expiresAtTick,
        long committedUntilTick) implements Comparable<FactionStrategicGoal> {

    public FactionStrategicGoal {
        goalId = require(goalId, "Goal id");
        Objects.requireNonNull(family, "Goal family not set");
        targetId = require(targetId, "Target id");
        evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "Evidence ids not set"));
        if (urgency < 0 || urgency > 100) {
            throw new IllegalArgumentException("Urgency outside range");
        }
        if (costCeiling < 0 || expiresAtTick < 0 || committedUntilTick < 0) {
            throw new IllegalArgumentException("Goal values cannot be negative");
        }
        successCondition = require(successCondition, "Success condition");
        failureCondition = require(failureCondition, "Failure condition");
        if (committedUntilTick > expiresAtTick) {
            throw new IllegalArgumentException("Commitment cannot outlive expiry");
        }
    }

    @Override
    public int compareTo(FactionStrategicGoal other) {
        int urgencyCompare = Integer.compare(other.urgency, urgency);
        return urgencyCompare != 0 ? urgencyCompare : goalId.compareTo(other.goalId);
    }

    public enum GoalFamily {
        SECURE_ROUTE,
        OBTAIN_ACCESS,
        STOCKPILE,
        DEFEND,
        ESCORT,
        EXPLORE,
        CLAIM,
        DETER,
        COERCE,
        RAID,
        BLOCKADE,
        INVADE,
        RECOVER
    }

    private static String require(String value, String label) {
        String result = Objects.requireNonNull(value, label + " not set").strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return result;
    }
}
