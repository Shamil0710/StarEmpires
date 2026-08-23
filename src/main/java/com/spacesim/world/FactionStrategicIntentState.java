package com.spacesim.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Persistent Stage-21B strategic intent aggregate for one autonomous faction actor.
 *
 * @param factionContentId stable owning faction identity
 * @param nextGoalSequence next monotonically increasing persistent goal sequence
 * @param goals open and terminal goal history in canonical goal-ID order
 */
public record FactionStrategicIntentState(
        String factionContentId,
        long nextGoalSequence,
        List<StrategicGoalState> goals) implements Comparable<FactionStrategicIntentState> {

    /**
     * Validates ownership, persistent IDs and canonicalizes goal order.
     *
     * @param factionContentId stable owning faction identity
     * @param nextGoalSequence next monotonically increasing persistent goal sequence
     * @param goals open and terminal goal history in canonical goal-ID order
     */
    public FactionStrategicIntentState {
        factionContentId = requireText(factionContentId, "Strategic intent faction ID");
        if (nextGoalSequence <= 0L) {
            throw new IllegalArgumentException("Next strategic goal sequence must be positive");
        }
        ArrayList<StrategicGoalState> sorted = new ArrayList<>(
                Objects.requireNonNull(goals, "Strategic goals not set"));
        if (sorted.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Strategic goals cannot contain null");
        }
        sorted.sort(null);
        HashSet<String> goalIds = new HashSet<>();
        HashSet<String> openIntentKeys = new HashSet<>();
        for (StrategicGoalState goal : sorted) {
            if (!factionContentId.equals(goal.factionContentId())) {
                throw new IllegalArgumentException("Strategic goal belongs to a different faction: " + goal.goalId());
            }
            if (!goalIds.add(goal.goalId())) {
                throw new IllegalArgumentException("Duplicate strategic goal ID: " + goal.goalId());
            }
            if (goal.isOpen() && !openIntentKeys.add(goal.intentKey())) {
                throw new IllegalArgumentException("Duplicate open strategic intent: " + goal.intentKey());
            }
        }
        goals = List.copyOf(sorted);
    }

    /**
     * Creates an empty persistent intent aggregate.
     *
     * @param factionContentId stable owning faction identity
     * @return initial strategic intent state with sequence one and no goals
     */
    public static FactionStrategicIntentState initial(String factionContentId) {
        return new FactionStrategicIntentState(factionContentId, 1L, List.of());
    }

    /**
     * Returns currently active goals in canonical persistent-ID order.
     *
     * @return immutable active goal list
     */
    public List<StrategicGoalState> activeGoals() {
        return goals.stream()
                .filter(goal -> goal.lifecycle() == StrategicGoalState.Lifecycle.ACTIVE)
                .toList();
    }

    /**
     * Returns all accepted goals that remain active or stalled.
     *
     * @return immutable open goal list
     */
    public List<StrategicGoalState> openGoals() {
        return goals.stream().filter(StrategicGoalState::isOpen).toList();
    }

    /**
     * Allocates one deterministic persistent goal ID without mutating the state.
     *
     * @return goal ID derived only from stable faction identity and persisted sequence
     */
    public String nextGoalId() {
        return factionContentId + ":strategic-goal:" + nextGoalSequence;
    }

    @Override
    public int compareTo(FactionStrategicIntentState other) {
        Objects.requireNonNull(other, "other");
        return factionContentId.compareTo(other.factionContentId);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
