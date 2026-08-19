package com.spacesim.persistence;

import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.simulation.Stage20MaterializationService.PhysicalStateSnapshot;
import com.spacesim.persistence.Stage20MaterializationPersistentState.PhysicalEntityState;

import java.util.List;
import java.util.Objects;

/** Capture/restore boundary for Stage-20 materialization state over the unchanged core GameState. */
public final class Stage20MaterializationPersistence {
    private Stage20MaterializationPersistence() {
        throw new AssertionError("No instances");
    }

    /**
     * Captures the full current session including dematerialized persistent entities and Stage-20 physical state.
     *
     * @param session authoritative local simulation session
     * @param materialization Stage-20 materialization boundary associated with the session
     * @return immutable Stage-20 persistence envelope
     */
    public static Stage20MaterializationPersistentState capture(
            SimulationSession session,
            Stage20MaterializationService materialization) {
        SimulationSession checkedSession = Objects.requireNonNull(session, "session");
        Stage20MaterializationService checkedMaterialization = Objects.requireNonNull(
                materialization, "materialization");
        GameState base = checkedSession.snapshot();
        List<EntityState> allPersistentEntities = checkedMaterialization.snapshotAllPersistentEntities();
        GameState complete = new GameState(
                base.schemaVersion(),
                base.rootSeed(),
                base.clock(),
                base.nextEntityIdValue(),
                base.eventRandomState(),
                base.asteroidRandomState(),
                base.events(),
                base.asteroidSpawner(),
                base.priceRecorder(),
                base.ledger(),
                allPersistentEntities);
        List<PhysicalEntityState> physical = checkedMaterialization.snapshotPhysicalStates().stream()
                .map(Stage20MaterializationPersistence::toPersistentPhysicalState)
                .toList();
        return new Stage20MaterializationPersistentState(
                Stage20MaterializationPersistentState.CURRENT_VERSION,
                complete,
                physical);
    }

    /**
     * Restores causal persistent state and Stage-20 physical authority.
     *
     * <p>All entities are initially restored as live Ashley representations because materialization
     * level is recomputable relevance. The subsequent production relevance scheduler may immediately
     * dematerialize entities again without changing the restored causal state.</p>
     *
     * @param state validated Stage-20 persistence envelope
     * @return restored runtime session plus registered materialization service
     */
    public static RestoredRuntime restore(Stage20MaterializationPersistentState state) {
        Stage20MaterializationPersistentState checked = Objects.requireNonNull(state, "state");
        SimulationSession session = SimulationSession.restore(checked.gameState());
        Stage20MaterializationService materialization = Stage20MaterializationService.forSession(session);
        for (PhysicalEntityState physical : checked.physicalEntities()) {
            materialization.registerPhysicalState(physical.id(), physical.physicalState());
        }
        return new RestoredRuntime(session, materialization);
    }

    private static PhysicalEntityState toPersistentPhysicalState(PhysicalStateSnapshot snapshot) {
        return new PhysicalEntityState(snapshot.id(), snapshot.physicalState());
    }

    /**
     * Restored Stage-20 local runtime pair.
     *
     * @param session restored authoritative simulation session
     * @param materialization restored Stage-20 materialization service with physical sidecar registered
     */
    public record RestoredRuntime(
            SimulationSession session,
            Stage20MaterializationService materialization) {
        /**
         * Validates one restored runtime pair.
         *
         * @param session restored simulation session
         * @param materialization restored materialization service
         */
        public RestoredRuntime {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(materialization, "materialization");
        }
    }
}
