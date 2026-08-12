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
 * <p>Topology определяет Galaxy/Sector/StarSystem hierarchy, каждый
 * {@link StarSystemSimulationState} хранит обычный {@link GameState} локального economic core, а
 * {@link FactionEconomicState} хранит authoritative strategic treasury/policy state. World-layer
 * требует ровно один local snapshot для каждой topology system и stable faction content IDs.</p>
 *
 * @param schemaVersion версия world-level persistent schema
 * @param topology immutable topology галактики
 * @param systems полный набор local simulation snapshots по системам
 * @param factions persistent strategic faction economy в canonical content-ID порядке
 */
public record WorldState(
        int schemaVersion,
        GalaxyTopology topology,
        List<StarSystemSimulationState> systems,
        List<FactionEconomicState> factions) {
    /** Текущая версия world-level persistent schema. */
    public static final int CURRENT_VERSION = 2;
    /** Stage-7 world schema без persistent faction economy. */
    public static final int LEGACY_STAGE7_VERSION = 1;

    /**
     * Source-compatible конструктор world без faction state.
     *
     * <p>Используется legacy/tests и является нейтральным: отсутствие faction state не создаёт
     * treasury money. Новые production worlds должны передавать factions явно.</p>
     *
     * @param schemaVersion текущая world schema
     * @param topology topology галактики
     * @param systems snapshots систем
     */
    public WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems) {
        this(schemaVersion, topology, systems, List.of());
    }

    /**
     * Валидирует покрытие topology и нормализует canonical system/faction order.
     *
     * @param schemaVersion версия world-level persistent schema
     * @param topology topology галактики
     * @param systems snapshots всех систем
     * @param factions strategic faction states
     * @throws NullPointerException если обязательное значение не задано
     * @throws IllegalArgumentException при неизвестной версии, duplicate/unknown system ID,
     *         неполном topology coverage или duplicate faction content ID
     */
    public WorldState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Неподдерживаемая WorldState schema: " + schemaVersion);
        }
        Objects.requireNonNull(topology, "GalaxyTopology WorldState не задан");
        Objects.requireNonNull(systems, "System states WorldState не заданы");
        Objects.requireNonNull(factions, "Faction states WorldState не заданы");
        if (topology.systems().isEmpty()) {
            throw new IllegalArgumentException("WorldState должен содержать хотя бы одну StarSystem");
        }

        List<StarSystemSimulationState> sortedSystems = new ArrayList<>(systems.size());
        Set<StarSystemId> seenSystems = new HashSet<>();
        for (StarSystemSimulationState systemState : systems) {
            StarSystemSimulationState value = Objects.requireNonNull(
                    systemState,
                    "StarSystemSimulationState не задан");
            if (topology.findSystem(value.systemId()).isEmpty()) {
                throw new IllegalArgumentException(
                        "Simulation state ссылается на неизвестную StarSystem: " + value.systemId());
            }
            if (!seenSystems.add(value.systemId())) {
                throw new IllegalArgumentException(
                        "Дублирующий simulation state StarSystem: " + value.systemId());
            }
            sortedSystems.add(value);
        }
        if (seenSystems.size() != topology.systems().size()) {
            throw new IllegalArgumentException(
                    "Каждая StarSystem topology должна иметь ровно один simulation state");
        }
        for (StarSystemNode system : topology.systems()) {
            if (!seenSystems.contains(system.id())) {
                throw new IllegalArgumentException(
                        "Отсутствует simulation state StarSystem: " + system.id());
            }
        }
        sortedSystems.sort(Comparator.comparing(StarSystemSimulationState::systemId));
        systems = List.copyOf(sortedSystems);

        List<FactionEconomicState> sortedFactions = new ArrayList<>(factions.size());
        Set<String> factionIds = new HashSet<>();
        for (FactionEconomicState faction : factions) {
            FactionEconomicState value = Objects.requireNonNull(faction, "FactionEconomicState не задан");
            if (!factionIds.add(value.factionContentId())) {
                throw new IllegalArgumentException(
                        "Дублирующий faction content ID в WorldState: " + value.factionContentId());
            }
            sortedFactions.add(value);
        }
        sortedFactions.sort(Comparator.naturalOrder());
        factions = List.copyOf(sortedFactions);
    }

    /**
     * Оборачивает legacy single-session save в минимальный world без выдуманного faction treasury.
     *
     * @param gameState текущий локальный GameState старого single-system мира
     * @return WorldState текущей schema с default topology и пустым faction-state
     */
    public static WorldState singleSystem(GameState gameState) {
        return new WorldState(
                CURRENT_VERSION,
                WorldTopologyDefaults.singleSystem(),
                List.of(new StarSystemSimulationState(
                        WorldTopologyDefaults.DEFAULT_SYSTEM_ID,
                        Objects.requireNonNull(gameState, "GameState legacy world не задан"))),
                List.of());
    }

    /**
     * Завершает декодирование Stage-7 schema v1 нейтральной миграцией.
     *
     * <p>Старое сохранение не содержало treasury, поэтому миграция создаёт пустой faction-state и
     * никогда не создаёт деньги автоматически.</p>
     *
     * @param topology decoded Stage-7 topology
     * @param systems decoded Stage-7 local sessions
     * @return WorldState текущей schema с пустым faction-state
     */
    public static WorldState fromLegacyStage7(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems) {
        return new WorldState(CURRENT_VERSION, topology, systems, List.of());
    }
}
