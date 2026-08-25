package com.spacesim.world.generation;

import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.Stage21ECommandLossReconciliationService;
import com.spacesim.world.Stage21ETacticalMaterializationService;
import com.spacesim.world.StrategicOperationService;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.TacticalEncounterState;

import java.util.Objects;

/**
 * Atomic generated-world Stage-21E orchestration for one synchronous exact Stage-19 exchange.
 *
 * <p>The production authority commits every Stage-19 physical effect before returning. This service
 * immediately closes the transient ENGAGED reference and reconciles any actually destroyed FleetIds
 * out of current Stage-21D command membership in the same call. Consequently no save boundary can
 * retain hidden tactical runtime or an active command reference to a destroyed ordinary fleet.</p>
 */
public final class Stage21EGeneratedWorldTacticalExecutionService {
    private final Stage21ETacticalMaterializationService tactical;
    private final StrategicOperationService operations;
    private final Stage21ECommandLossReconciliationService losses;
    private final Stage21EGeneratedWorldStage19Authority authority;

    /**
     * Creates the generated-world exact tactical executor.
     *
     * @param authority synchronous production Stage-19/generated-world adapter
     */
    public Stage21EGeneratedWorldTacticalExecutionService(Stage21EGeneratedWorldStage19Authority authority) {
        this(new Stage21ETacticalMaterializationService(), new StrategicOperationService(),
                new Stage21ECommandLossReconciliationService(), authority);
    }

    /**
     * Creates an executor with explicit Stage-21E lifecycle services.
     *
     * @param tactical actor-contact/physical-co-location tactical gate
     * @param operations persistent operation lifecycle authority
     * @param losses command-loss reconciliation boundary
     * @param authority synchronous production Stage-19/generated-world adapter
     */
    public Stage21EGeneratedWorldTacticalExecutionService(
            Stage21ETacticalMaterializationService tactical,
            StrategicOperationService operations,
            Stage21ECommandLossReconciliationService losses,
            Stage21EGeneratedWorldStage19Authority authority) {
        this.tactical = Objects.requireNonNull(tactical, "tactical");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.losses = Objects.requireNonNull(losses, "losses");
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    /**
     * Validates contact, commits exact Stage-19 physics, closes engagement and cleans command losses.
     *
     * @param commandState current Stage-21D command metadata owning the operation
     * @param operationState persistent operation state containing CONTACT_CONFIRMED operation
     * @param operationId operation to execute
     * @param forces pre-exchange ordinary physical force reconstruction used by the tactical gate
     * @param currentTick authoritative non-negative world tick
     * @return updated command/operation state and resolved encounter reference
     */
    public ExecutionResult execute(
            FleetCommandState commandState,
            StrategicOperationState operationState,
            long operationId,
            FleetForceRegistry forces,
            long currentTick) {
        Objects.requireNonNull(commandState, "commandState");
        Objects.requireNonNull(operationState, "operationState");
        Objects.requireNonNull(forces, "forces");
        TacticalEncounterState encounter = tactical.materialize(
                operationState.requireOperation(operationId), forces, currentTick, authority);
        StrategicOperationState engaged = operations.markEngaged(
                operationState, operationId, encounter, currentTick);
        StrategicOperationState resolved = operations.resolveEngagement(
                engaged, operationId, currentTick);
        TacticalEncounterState persisted = resolved.requireOperation(operationId).encounter();
        if (persisted == null || persisted.active()) {
            throw new IllegalStateException("synchronous tactical execution left an active in-memory encounter");
        }

        Stage21ECommandLossReconciliationService.ReconciliationResult reconciled = losses.reconcile(
                commandState,
                resolved,
                operationId,
                authority::fleetExists,
                currentTick);
        return new ExecutionResult(
                reconciled.commandState(),
                reconciled.operationState(),
                persisted,
                reconciled.owningGroupDestroyed());
    }

    /**
     * Result of one atomically closed exact tactical exchange.
     *
     * @param commandState updated Stage-21D command metadata without destroyed fleet members
     * @param operationState updated Stage-21E operation metadata
     * @param encounter resolved exact tactical encounter reference
     * @param owningGroupDestroyed whether the operation-owning command group lost every member
     */
    public record ExecutionResult(
            FleetCommandState commandState,
            StrategicOperationState operationState,
            TacticalEncounterState encounter,
            boolean owningGroupDestroyed) {
        /**
         * Validates a closed execution result.
         *
         * @param commandState updated Stage-21D command metadata
         * @param operationState updated Stage-21E operation metadata
         * @param encounter resolved exact tactical encounter reference
         * @param owningGroupDestroyed whether the operation-owning group was removed
         */
        public ExecutionResult {
            Objects.requireNonNull(commandState, "commandState");
            Objects.requireNonNull(operationState, "operationState");
            Objects.requireNonNull(encounter, "encounter");
            if (encounter.active()) {
                throw new IllegalArgumentException("execution result encounter must be resolved");
            }
        }
    }
}
