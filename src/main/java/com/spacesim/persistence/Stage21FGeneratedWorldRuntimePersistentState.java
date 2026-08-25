package com.spacesim.persistence;

import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.TerritorialTransitionState;
import com.spacesim.world.TerritorialTransitionState.OccupationState;
import com.spacesim.world.WorldState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic Stage-21F generated-world checkpoint composition.
 *
 * <p>The complete accepted Stage-21E runtime is embedded unchanged. Stage 21F adds only
 * occupation-transition metadata. Claims, stabilization, established control, economy, fleet
 * placement, operation history and faction identities remain persisted by their existing owners.</p>
 *
 * @param schemaVersion exact Stage-21F checkpoint schema version
 * @param runtimeVersion exact Stage-21F runtime contract identifier
 * @param stage21ERuntime complete embedded Stage-21E checkpoint
 * @param territorialTransitions Stage-21F persistent occupation-transition metadata
 */
public record Stage21FGeneratedWorldRuntimePersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21EGeneratedWorldRuntimePersistentState stage21ERuntime,
        TerritorialTransitionState territorialTransitions) {

    /** Current Stage-21F checkpoint schema. */
    public static final int CURRENT_VERSION = 9;
    /** Current Stage-21F runtime contract identifier. */
    public static final String CURRENT_RUNTIME_VERSION = "stage21f.generated-world-territorial-transition.v9";

    /**
     * Validates every Stage-21F transition against embedded Stage-21E operation and Stage-17 world law.
     *
     * <p>Occupation metadata may reference only a persisted Stage-21E {@code INVASION} operation with
     * the same objective system and exact stable/runtime owner identity. Claim-provenance metadata is
     * accepted only while the corresponding Stage-17 claim actually exists. Occupation evaluation
     * ticks may not run ahead of the embedded active-system authoritative clock. The current
     * generated-world checkpoint is bound to the default authored content catalog plus persisted
     * world-defined identities, matching the production resolver used by the generated runtime. No
     * mapping is duplicated into Stage-21F state.</p>
     */
    public Stage21FGeneratedWorldRuntimePersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-21F checkpoint schema: " + schemaVersion);
        }
        runtimeVersion = Objects.requireNonNull(runtimeVersion, "runtimeVersion").strip();
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException("Unsupported Stage-21F runtime version: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21ERuntime, "stage21ERuntime");
        Objects.requireNonNull(territorialTransitions, "territorialTransitions");

        Stage20GeneratedWorldRuntimePersistentState stage20 = stage21ERuntime.stage21DRuntime()
                .stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        WorldState world = stage20.worldState();
        long authoritativeWorldTick = world.systems().stream()
                .filter(system -> system.systemId().equals(stage20.activeSystemId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Stage-21F checkpoint active system is absent from saved world state"))
                .simulationState()
                .clock()
                .tick();
        Set<com.spacesim.world.StarSystemId> systems = new HashSet<>();
        world.topology().systems().forEach(system -> systems.add(system.id()));
        Map<String, FactionStrategicState> strategyByFaction = new HashMap<>();
        for (FactionStrategicState strategy : world.factionStrategies()) {
            strategyByFaction.put(strategy.factionContentId(), strategy);
        }
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), world.factionIdentities());

        for (OccupationState occupation : territorialTransitions.occupations()) {
            if (occupation.lastEvaluatedTick() > authoritativeWorldTick) {
                throw new IllegalArgumentException(
                        "Stage-21F occupation evaluation is ahead of authoritative world time: "
                                + occupation.operationId());
            }
            if (!systems.contains(occupation.systemId())) {
                throw new IllegalArgumentException(
                        "Stage-21F occupation references unknown objective system: " + occupation.systemId());
            }
            FactionStrategicState strategy = strategyByFaction.get(occupation.factionContentId());
            if (strategy == null) {
                throw new IllegalArgumentException(
                        "Stage-21F occupation references unknown strategic faction: "
                                + occupation.factionContentId());
            }
            OperationState operation;
            try {
                operation = stage21ERuntime.operationState().requireOperation(occupation.operationId());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Stage-21F occupation references unknown Stage-21E operation: "
                                + occupation.operationId(), exception);
            }
            if (operation.type() != OperationType.INVASION) {
                throw new IllegalArgumentException(
                        "Stage-21F occupation must reference an INVASION operation: " + operation.id());
            }
            if (!operation.objectiveSystemId().equals(occupation.systemId())) {
                throw new IllegalArgumentException(
                        "Stage-21F occupation objective differs from Stage-21E invasion: " + operation.id());
            }
            String operationFaction = identities.stableId(operation.factionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Stage-21F invasion references unknown runtime faction: " + operation.factionId()));
            if (!operationFaction.equals(occupation.factionContentId())) {
                throw new IllegalArgumentException(
                        "Stage-21F occupation faction differs from Stage-21E invasion owner: " + operation.id());
            }
            if (occupation.claimCreatedByOccupation() && strategy.claimFor(occupation.systemId()) == null) {
                throw new IllegalArgumentException(
                        "Stage-21F occupation claims provenance for a missing Stage-17 claim: " + operation.id());
            }
        }
    }

    /**
     * Composes a current Stage-21F checkpoint over the accepted Stage-21E checkpoint.
     *
     * @param stage21E complete accepted Stage-21E generated-world checkpoint
     * @param transitions persistent Stage-21F occupation-transition metadata
     * @return validated current-version Stage-21F checkpoint wrapper
     */
    public static Stage21FGeneratedWorldRuntimePersistentState compose(
            Stage21EGeneratedWorldRuntimePersistentState stage21E,
            TerritorialTransitionState transitions) {
        return new Stage21FGeneratedWorldRuntimePersistentState(
                CURRENT_VERSION, CURRENT_RUNTIME_VERSION, stage21E, transitions);
    }
}
