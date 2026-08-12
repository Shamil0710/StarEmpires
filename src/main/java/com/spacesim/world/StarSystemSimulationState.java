package com.spacesim.world;

import com.spacesim.persistence.GameState;

import java.util.Objects;

/**
 * Persistent связь одной звёздной системы с snapshot её локальной deterministic simulation.
 *
 * <p>System ID принадлежит world-layer, а {@link GameState} остаётся независимым snapshot одной
 * существующей {@code SimulationSession}. Поэтому economic core не знает о Galaxy/Sector hierarchy.
 *
 * @param systemId система, которой принадлежит локальная simulation session
 * @param simulationState authoritative snapshot этой локальной simulation session
 */
public record StarSystemSimulationState(
        StarSystemId systemId,
        GameState simulationState) {
    /**
     * Проверяет обязательные persistent значения.
     *
     * @param systemId система, которой принадлежит snapshot
     * @param simulationState локальный authoritative GameState
     * @throws NullPointerException если ID или snapshot не заданы
     * @throws IllegalArgumentException если snapshot не текущей schema
     */
    public StarSystemSimulationState {
        Objects.requireNonNull(systemId, "StarSystemId simulation state не задан");
        Objects.requireNonNull(simulationState, "GameState системы не задан");
        if (simulationState.schemaVersion() != GameState.CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "WorldState принимает только текущую GameState schema: "
                            + simulationState.schemaVersion());
        }
    }
}
