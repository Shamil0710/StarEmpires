package com.spacesim.world;

import com.spacesim.persistence.GameState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned persistent snapshot world-layer поверх нескольких локальных simulation sessions.
 *
 * <p>Topology определяет Galaxy/Sector/StarSystem hierarchy, а каждый {@link StarSystemSimulationState}
 * хранит обычный {@link GameState} существующего deterministic economic core. World-layer не
 * дублирует экономические компоненты и требует ровно один local snapshot для каждой системы
 * topology, поэтому удалённая система не может молча исчезнуть из authoritative состояния.</p>
 *
 * @param schemaVersion версия world-level persistent schema
 * @param topology immutable topology галактики
 * @param systems полный набор local simulation snapshots по системам
 */
public record WorldState(
        int schemaVersion,
        GalaxyTopology topology,
        List<StarSystemSimulationState> systems) {
    /** Текущая версия world-level persistent schema. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Валидирует покрытие topology и нормализует порядок system snapshots.
     *
     * @param schemaVersion версия world-level persistent schema
     * @param topology topology галактики
     * @param systems snapshots всех систем
     * @throws NullPointerException если обязательное значение не задано
     * @throws IllegalArgumentException при неизвестной версии, duplicate/unknown system ID или
     *         неполном покрытии topology
     */
    public WorldState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Неподдерживаемая WorldState schema: " + schemaVersion);
        }
        Objects.requireNonNull(topology, "GalaxyTopology WorldState не задан");
        Objects.requireNonNull(systems, "System states WorldState не заданы");
        if (topology.systems().isEmpty()) {
            throw new IllegalArgumentException("WorldState должен содержать хотя бы одну StarSystem");
        }

        List<StarSystemSimulationState> sorted = new ArrayList<>(systems.size());
        Set<StarSystemId> seen = new HashSet<>();
        for (StarSystemSimulationState systemState : systems) {
            StarSystemSimulationState value = Objects.requireNonNull(
                    systemState,
                    "StarSystemSimulationState не задан");
            if (topology.findSystem(value.systemId()).isEmpty()) {
                throw new IllegalArgumentException(
                        "Simulation state ссылается на неизвестную StarSystem: " + value.systemId());
            }
            if (!seen.add(value.systemId())) {
                throw new IllegalArgumentException(
                        "Дублирующий simulation state StarSystem: " + value.systemId());
            }
            sorted.add(value);
        }
        if (seen.size() != topology.systems().size()) {
            throw new IllegalArgumentException(
                    "Каждая StarSystem topology должна иметь ровно один simulation state");
        }
        for (StarSystemNode system : topology.systems()) {
            if (!seen.contains(system.id())) {
                throw new IllegalArgumentException(
                        "Отсутствует simulation state StarSystem: " + system.id());
            }
        }
        sorted.sort(Comparator.comparing(StarSystemSimulationState::systemId));
        systems = List.copyOf(sorted);
    }

    /**
     * Оборачивает существующий single-session save в минимальный Stage-7 world без изменения
     * экономического snapshot.
     *
     * @param gameState текущий локальный GameState старого single-system мира
     * @return WorldState с default topology и тем же GameState
     */
    public static WorldState singleSystem(GameState gameState) {
        return new WorldState(
                CURRENT_VERSION,
                WorldTopologyDefaults.singleSystem(),
                List.of(new StarSystemSimulationState(
                        WorldTopologyDefaults.DEFAULT_SYSTEM_ID,
                        Objects.requireNonNull(gameState, "GameState legacy world не задан"))));
    }
}
