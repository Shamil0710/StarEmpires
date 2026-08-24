package com.spacesim.world.generation;

import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.Stage21ETacticalMaterializationService;
import com.spacesim.world.StrategicOperationService;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.TacticalEncounterState;

import java.util.Objects;

/**
 * Atomic generated-world Stage-21E orchestration for one synchronous exact Stage-19 exchange.
 *
 * <p>The production authority commits every Stage-19 physical effect before returning. This service
 * immediately closes the transient ENGAGED reference in the same call, so no save boundary can retain
 * an encounter id whose tactical runtime exists only in memory. The returned persistent operation
 * therefore contains a resolved encounter reference plus the already-mutated ordinary world.</p>
 */
public final class Stage21EGeneratedWorldTacticalExecutionService {
    private final Stage21ETacticalMaterializationService tactical;
    private final StrategicOperationService operations;
    private final Stage21EGeneratedWorldStage19Authority authority;

    /**
     * Creates the generated-world exact tactical executor.
     *
     * @param authority synchronous production Stage-19/generated-world adapter
     */
    public Stage21EGeneratedWorldTacticalExecutionService(Stage21EGeneratedWorldStage19Authority authority) {
        this(new Stage21ETacticalMaterializationService(), new StrategicOperationService(), authority);
    }

    /**
     * Creates an executor with explicit Stage-21E lifecycle services.
     *
     * @param tactical actor-contact/physical-co-location tactical gate
     * @param operations persistent operation lifecycle authority
     * @param authority synchronous production Stage-19/generated-world adapter
     */
    public Stage21EGeneratedWorldTacticalExecutionService(
            Stage21ETacticalMaterializationService tactical,
            StrategicOperationService operations,
            Stage21EGeneratedWorldStage19Authority authority) {
        this.tactical = Objects.requireNonNull(tactical, "tactical");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    /**
     * Validates contact, executes/commits exact Stage-19 physics and closes the transient engagement.
     *
     * @param state persistent operation state containing CONTACT_CONFIRMED operation
     * @param operationId operation to execute
     * @param forces pre-exchange ordinary physical force reconstruction used by the tactical gate
     * @param currentTick authoritative non-negative world tick
     * @return updated persistent operation state and resolved encounter reference
     */
    public ExecutionResult execute(
            StrategicOperationState state,
            long operationId,
            FleetForceRegistry forces,
            long currentTick) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(forces, "forces");
        TacticalEncounterState encounter = tactical.materialize(
                state.requireOperation(operationId), forces, currentTick, authority);
        StrategicOperationState engaged = operations.markEngaged(
                state, operationId, encounter, currentTick);
        StrategicOperationState resolved = operations.resolveEngagement(
                engaged, operationId, currentTick);
        TacticalEncounterState persisted = resolved.requireOperation(operationId).encounter();
        if (persisted == null || persisted.active()) {
            throw new IllegalStateException("synchronous tactical execution left an active in-memory encounter");
        }
        return new ExecutionResult(resolved, persisted);
    }

    /**
     * Result of one atomically closed exact tactical exchange.
     *
     * @param state updated persistent operation state
     * @param encounter resolved exact tactical encounter reference
     */
    public record ExecutionResult(
            StrategicOperationState state,
            TacticalEncounterState encounter) {
        /**
         * Validates a closed execution result.
         *
         * @param state updated persistent operation state
         * @param encounter resolved exact tactical encounter reference
         */
        public ExecutionResult {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(encounter, "encounter");
            if (encounter.active()) {
                throw new IllegalArgumentException("execution result encounter must be resolved");
            }
        }
    }
}
