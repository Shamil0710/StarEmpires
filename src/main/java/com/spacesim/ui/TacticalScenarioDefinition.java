package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalBattleDeceptionRuntime;

import java.util.Objects;
import java.util.function.Supplier;

/** Immutable Stage-19J presentation-side scenario descriptor and fresh-runtime factory. */
public record TacticalScenarioDefinition(
        TacticalScenarioId id,
        String displayName,
        String description,
        int alphaShips,
        int betaShips,
        Supplier<LiveTacticalBattleDeceptionRuntime> runtimeFactory) {

    /**
     * Validates immutable scenario metadata without adding combat authority.
     *
     * @param id canonical scenario identity
     * @param displayName human-readable tactical viewer name
     * @param description short validation-purpose description
     * @param alphaShips authored ALPHA combatant count
     * @param betaShips authored BETA combatant count
     * @param runtimeFactory supplier of fresh authoritative production runtimes
     */
    public TacticalScenarioDefinition {
        Objects.requireNonNull(id, "id");
        displayName = requireText(displayName, "displayName");
        description = requireText(description, "description");
        if (alphaShips <= 0 || betaShips <= 0) {
            throw new IllegalArgumentException("scenario ship counts must be positive");
        }
        Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    /** @return total authored combatant count */
    public int totalShips() {
        return alphaShips + betaShips;
    }

    /** @return one fresh authoritative production runtime for this scenario */
    public LiveTacticalBattleDeceptionRuntime createRuntime() {
        return Objects.requireNonNull(runtimeFactory.get(), "runtimeFactory returned null");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
