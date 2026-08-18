package com.spacesim.ui;

import com.spacesim.ship.Stage19ScaledLiveTacticalFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Canonical presentation-side catalog for the six mandatory Stage-19J validation scenarios. */
public final class TacticalScenarioCatalog {
    private static final List<TacticalScenarioDefinition> DEFINITIONS = List.of(
            new TacticalScenarioDefinition(
                    TacticalScenarioId.LEGACY_DUEL,
                    "1v1 Legacy Duel",
                    "Reference duel using the shared production tactical runtime.",
                    1,
                    1,
                    Stage19ScaledLiveTacticalFactory::createLegacyDuel),
            new TacticalScenarioDefinition(
                    TacticalScenarioId.BALANCED_4V4,
                    "4v4 Balanced",
                    "Balanced squadron scale-up with shared actor-bounded control.",
                    4,
                    4,
                    Stage19ScaledLiveTacticalFactory::createBalanced4v4),
            new TacticalScenarioDefinition(
                    TacticalScenarioId.MIXED_8V8,
                    "8v8 Mixed",
                    "Mixed tactical roles with finite strike, decoy and interceptor stores.",
                    8,
                    8,
                    Stage19ScaledLiveTacticalFactory::createMixed8v8),
            new TacticalScenarioDefinition(
                    TacticalScenarioId.DAMAGED_DEPLETED_8V8,
                    "8v8 Damaged / Depleted",
                    "Accepted mixed 8v8 pre-damage and reaction-mass depletion case.",
                    8,
                    8,
                    Stage19ScaledLiveTacticalFactory::createDamagedDepleted8v8),
            new TacticalScenarioDefinition(
                    TacticalScenarioId.MIXED_16V16,
                    "16v16 Mixed",
                    "Accepted 32-ship exact-local mixed engagement without maximum saturation loadout.",
                    16,
                    16,
                    Stage19ScaledLiveTacticalFactory::createMixed16v16),
            new TacticalScenarioDefinition(
                    TacticalScenarioId.SATURATION_16V16,
                    "16v16 Saturation",
                    "Dense kinetic, strike, interceptor and decoy validation scenario.",
                    16,
                    16,
                    Stage19ScaledLiveTacticalFactory::createSaturation32));

    private static final Map<TacticalScenarioId, TacticalScenarioDefinition> BY_ID = buildById();

    private TacticalScenarioCatalog() {
    }

    /** @return immutable canonical scenario order used by launch tooling and tests */
    public static List<TacticalScenarioDefinition> definitions() {
        return DEFINITIONS;
    }

    /**
     * Resolves the canonical definition for an enum identity.
     *
     * @param id canonical Stage-19J scenario identity
     * @return canonical immutable scenario definition
     */
    public static TacticalScenarioDefinition require(TacticalScenarioId id) {
        TacticalScenarioDefinition definition = BY_ID.get(Objects.requireNonNull(id, "id"));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown tactical scenario id: " + id);
        }
        return definition;
    }

    /**
     * Resolves a stable CLI key case-insensitively after trimming surrounding whitespace.
     *
     * @param cliKey candidate stable scenario command-line key
     * @return matching canonical definition, or empty when no key matches
     */
    public static Optional<TacticalScenarioDefinition> findByCliKey(String cliKey) {
        if (cliKey == null) {
            return Optional.empty();
        }
        String normalized = cliKey.trim().toLowerCase(Locale.ROOT);
        return DEFINITIONS.stream()
                .filter(value -> value.id().cliKey().equals(normalized))
                .findFirst();
    }

    /**
     * Resolves a CLI key or throws a message that enumerates every accepted value.
     *
     * @param cliKey candidate stable scenario command-line key
     * @return matching canonical immutable scenario definition
     */
    public static TacticalScenarioDefinition requireByCliKey(String cliKey) {
        return findByCliKey(cliKey).orElseThrow(() -> new IllegalArgumentException(
                "Unknown tactical scenario '" + cliKey + "'. Valid values: " + validCliKeys()));
    }

    /** @return comma-separated stable keys suitable for launcher diagnostics */
    public static String validCliKeys() {
        return DEFINITIONS.stream()
                .map(value -> value.id().cliKey())
                .collect(Collectors.joining(", "));
    }

    private static Map<TacticalScenarioId, TacticalScenarioDefinition> buildById() {
        EnumMap<TacticalScenarioId, TacticalScenarioDefinition> values =
                new EnumMap<>(TacticalScenarioId.class);
        for (TacticalScenarioDefinition definition : DEFINITIONS) {
            TacticalScenarioDefinition previous = values.put(definition.id(), definition);
            if (previous != null) {
                throw new IllegalStateException("Duplicate tactical scenario id: " + definition.id());
            }
        }
        if (values.size() != TacticalScenarioId.values().length) {
            throw new IllegalStateException("Tactical scenario catalog must define every TacticalScenarioId");
        }
        return Map.copyOf(values);
    }
}
