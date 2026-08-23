package com.spacesim.world;

import java.util.Objects;

/**
 * Declarative read-only Stage-21B success/failure condition.
 *
 * <p>The strategic planner never evaluates this condition against hidden world truth. Execution or
 * domain authorities may use the stable condition code when producing an explicit
 * {@link StrategicGoalOutcomeSignal}; the condition itself exists for persistence, explanation and
 * adapter contracts only.</p>
 *
 * @param code stable lowercase-hyphenated condition identity
 * @param targetId stable actor-known condition target
 */
public record StrategicGoalCondition(String code, String targetId) implements Comparable<StrategicGoalCondition> {

    /**
     * Validates a persistence-safe condition descriptor.
     *
     * @param code stable lowercase-hyphenated condition identity
     * @param targetId stable actor-known condition target
     */
    public StrategicGoalCondition {
        code = requireText(code, "Strategic condition code");
        targetId = requireText(targetId, "Strategic condition target ID");
        if (!code.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Strategic condition code must be lowercase hyphenated text");
        }
    }

    @Override
    public int compareTo(StrategicGoalCondition other) {
        Objects.requireNonNull(other, "other");
        int codeOrder = code.compareTo(other.code);
        return codeOrder != 0 ? codeOrder : targetId.compareTo(other.targetId);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
