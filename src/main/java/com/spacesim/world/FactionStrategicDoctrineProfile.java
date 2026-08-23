package com.spacesim.world;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable caller-owned Stage-21B strategic preference profile.
 *
 * <p>This object is policy input, not a simulation authority. It cannot mutate diplomacy, fleets,
 * territory, treasury or any other world state. A zero preference disables automatic candidate
 * generation for the corresponding goal family.</p>
 */
public final class FactionStrategicDoctrineProfile {
    private static final int FULL_PREFERENCE = 10_000;
    private final Map<StrategicGoalType, Integer> preferences;

    private FactionStrategicDoctrineProfile(Map<StrategicGoalType, Integer> preferences) {
        EnumMap<StrategicGoalType, Integer> checked = new EnumMap<>(StrategicGoalType.class);
        for (StrategicGoalType type : StrategicGoalType.values()) {
            Integer value = Objects.requireNonNull(preferences.get(type), "Missing doctrine preference for " + type);
            requireBasisPoints(value, type);
            checked.put(type, value);
        }
        this.preferences = Map.copyOf(checked);
    }

    /**
     * Creates a neutral conservative profile.
     *
     * <p>Non-escalatory roadmap goals are fully eligible. Coercion, raids, blockades and invasions
     * are disabled until an explicit caller-owned policy profile opts into them.</p>
     *
     * @return immutable neutral doctrine profile
     */
    public static FactionStrategicDoctrineProfile neutral() {
        EnumMap<StrategicGoalType, Integer> values = new EnumMap<>(StrategicGoalType.class);
        for (StrategicGoalType type : StrategicGoalType.values()) {
            values.put(type, type.escalatory() ? 0 : FULL_PREFERENCE);
        }
        return new FactionStrategicDoctrineProfile(values);
    }

    /**
     * Returns a copy with one explicit goal-family preference.
     *
     * @param type goal family to override
     * @param basisPoints preference in {@code [0,10000]}
     * @return new immutable profile
     */
    public FactionStrategicDoctrineProfile withPreference(StrategicGoalType type, int basisPoints) {
        StrategicGoalType checkedType = Objects.requireNonNull(type, "Strategic goal type not set");
        requireBasisPoints(basisPoints, checkedType);
        EnumMap<StrategicGoalType, Integer> values = new EnumMap<>(StrategicGoalType.class);
        values.putAll(preferences);
        values.put(checkedType, basisPoints);
        return new FactionStrategicDoctrineProfile(values);
    }

    /**
     * Returns the normalized preference for one goal family.
     *
     * @param type goal family
     * @return preference in {@code [0,10000]}
     */
    public int preferenceBasisPoints(StrategicGoalType type) {
        return preferences.get(Objects.requireNonNull(type, "Strategic goal type not set"));
    }

    /**
     * Reports whether the profile permits automatic candidate generation for a family.
     *
     * @param type goal family
     * @return true when preference is greater than zero
     */
    public boolean enables(StrategicGoalType type) {
        return preferenceBasisPoints(type) > 0;
    }

    private static void requireBasisPoints(int value, StrategicGoalType type) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException("Doctrine preference for " + type + " must be in [0,10000]");
        }
    }
}
