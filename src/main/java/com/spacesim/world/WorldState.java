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
 * {@link StarSystemSimulationState} хранит обычный {@link GameState} локального economic core,
 * {@link FactionEconomicState} хранит authoritative treasury/policy state, а
 * {@link FactionStrategicState} — directed relations и strategic territory. World-layer требует
 * ровно один local snapshot для каждой topology system и canonical stable faction content IDs.</p>
 *
 * @param schemaVersion версия world-level persistent schema
 * @param topology immutable topology галактики
 * @param systems полный набор local simulation snapshots по системам
 * @param factions persistent strategic faction economy в canonical content-ID порядке
 * @param factionStrategies persistent diplomacy/territory state в canonical content-ID порядке
 * @param nextConstructionProjectIdValue следующий неиспользованный world-level construction project ID
 * @param constructionProjects persistent construction projects в canonical ID порядке
 */
public record WorldState(
        int schemaVersion,
        GalaxyTopology topology,
        List<StarSystemSimulationState> systems,
        List<FactionEconomicState> factions,
        List<FactionStrategicState> factionStrategies,
        long nextConstructionProjectIdValue,
        List<ConstructionProjectState> constructionProjects) {
    /** Текущая версия world-level persistent schema. */
    public static final int CURRENT_VERSION = 4;
    /** Stage-8 full strategic schema без construction projects. */
    public static final int LEGACY_STAGE8_VERSION = 3;
    /** Stage-8 treasury-only schema без diplomacy/territory. */
    public static final int LEGACY_FACTION_TREASURY_VERSION = 2;
    /** Stage-7 world schema без persistent faction economy. */
    public static final int LEGACY_STAGE7_VERSION = 1;

    /**
     * Source-compatible конструктор world без faction state.
     *
     * @param schemaVersion текущая world schema
     * @param topology topology галактики
     * @param systems snapshots систем
     */
    public WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems) {
        this(schemaVersion, topology, systems, List.of(), List.of(), 1L, List.of());
    }

    /**
     * Source-compatible конструктор treasury-only world.
     *
     * @param schemaVersion текущая world schema
     * @param topology topology галактики
     * @param systems snapshots систем
     * @param factions strategic treasury states
     */
    public WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions) {
        this(schemaVersion, topology, systems, factions, List.of(), 1L, List.of());
    }

    /**
     * Source-compatible конструктор Stage-8 world без construction state.
     *
     * @param schemaVersion текущая world schema
     * @param topology topology галактики
     * @param systems snapshots систем
     * @param factions faction economic states
     * @param factionStrategies diplomacy/territory states
     */
    public WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> factionStrategies) {
        this(schemaVersion, topology, systems, factions, factionStrategies, 1L, List.of());
    }

    /**
     * Валидирует coverage/uniqueness и нормализует canonical ordering.
     *
     * @param schemaVersion версия world-level persistent schema
     * @param topology topology галактики
     * @param systems snapshots всех систем
     * @param factions economic faction states
     * @param factionStrategies diplomacy/territory states
     * @param nextConstructionProjectIdValue следующий construction-project allocator watermark
     * @param constructionProjects construction project snapshots
     * @throws NullPointerException если обязательное значение не задано
     * @throws IllegalArgumentException при неизвестной версии, duplicate/unknown system ID,
     *         неполном topology coverage, duplicate faction ID или двойном владении территорией
     */
    public WorldState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Неподдерживаемая WorldState schema: " + schemaVersion);
        }
        Objects.requireNonNull(topology, "GalaxyTopology WorldState не задан");
        Objects.requireNonNull(systems, "System states WorldState не заданы");
        Objects.requireNonNull(factions, "Faction states WorldState не заданы");
        Objects.requireNonNull(factionStrategies, "Faction strategic states WorldState не заданы");
        Objects.requireNonNull(constructionProjects, "Construction projects WorldState не заданы");
        if (nextConstructionProjectIdValue <= 0L) {
            throw new IllegalArgumentException("Следующий ConstructionProjectId должен быть положительным");
        }
        if (topology.systems().isEmpty()) {
            throw new IllegalArgumentException("WorldState должен содержать хотя бы одну StarSystem");
        }

        List<StarSystemSimulationState> sortedSystems = new ArrayList<>(systems.size());
        Set<StarSystemId> seenSystems = new HashSet<>();
        for (StarSystemSimulationState systemState : systems) {
            StarSystemSimulationState value = Objects.requireNonNull(systemState, "StarSystemSimulationState не задан");
            if (topology.findSystem(value.systemId()).isEmpty()) {
                throw new IllegalArgumentException(
                        "Simulation state ссылается на неизвестную StarSystem: " + value.systemId());
            }
            if (!seenSystems.add(value.systemId())) {
                throw new IllegalArgumentException("Дублирующий simulation state StarSystem: " + value.systemId());
            }
            sortedSystems.add(value);
        }
        if (seenSystems.size() != topology.systems().size()) {
            throw new IllegalArgumentException("Каждая StarSystem topology должна иметь ровно один simulation state");
        }
        for (StarSystemNode system : topology.systems()) {
            if (!seenSystems.contains(system.id())) {
                throw new IllegalArgumentException("Отсутствует simulation state StarSystem: " + system.id());
            }
        }
        sortedSystems.sort(Comparator.comparing(StarSystemSimulationState::systemId));
        systems = List.copyOf(sortedSystems);

        List<FactionEconomicState> sortedFactions = new ArrayList<>(factions.size());
        Set<String> economicFactionIds = new HashSet<>();
        for (FactionEconomicState faction : factions) {
            FactionEconomicState value = Objects.requireNonNull(faction, "FactionEconomicState не задан");
            if (!economicFactionIds.add(value.factionContentId())) {
                throw new IllegalArgumentException(
                        "Дублирующий faction content ID в WorldState: " + value.factionContentId());
            }
            sortedFactions.add(value);
        }
        sortedFactions.sort(Comparator.naturalOrder());
        factions = List.copyOf(sortedFactions);

        List<FactionStrategicState> sortedStrategies = new ArrayList<>(factionStrategies.size());
        Set<String> strategicFactionIds = new HashSet<>();
        Set<StarSystemId> controlledSystems = new HashSet<>();
        for (FactionStrategicState strategy : factionStrategies) {
            FactionStrategicState value = Objects.requireNonNull(strategy, "FactionStrategicState не задан");
            if (!strategicFactionIds.add(value.factionContentId())) {
                throw new IllegalArgumentException(
                        "Дублирующий strategic faction content ID: " + value.factionContentId());
            }
            for (StarSystemId controlled : value.controlledSystems()) {
                if (topology.findSystem(controlled).isEmpty()) {
                    throw new IllegalArgumentException(
                            "Faction territory ссылается на неизвестную StarSystem: " + controlled);
                }
                if (!controlledSystems.add(controlled)) {
                    throw new IllegalArgumentException(
                            "StarSystem не может одновременно контролироваться двумя factions: " + controlled);
                }
            }
            sortedStrategies.add(value);
        }
        sortedStrategies.sort(Comparator.naturalOrder());
        factionStrategies = List.copyOf(sortedStrategies);

        List<ConstructionProjectState> sortedProjects = new ArrayList<>(constructionProjects.size());
        Set<ConstructionProjectId> projectIds = new HashSet<>();
        long maxProjectId = 0L;
        for (ConstructionProjectState project : constructionProjects) {
            ConstructionProjectState value = Objects.requireNonNull(project, "ConstructionProjectState не задан");
            if (!projectIds.add(value.id())) {
                throw new IllegalArgumentException("Дублирующий ConstructionProjectId: " + value.id());
            }
            if (topology.findSystem(value.systemId()).isEmpty()) {
                throw new IllegalArgumentException("Construction project ссылается на неизвестную StarSystem: " + value.systemId());
            }
            if (!economicFactionIds.contains(value.ownerFactionContentId())) {
                throw new IllegalArgumentException("Construction project ссылается на неизвестную faction account: "
                        + value.ownerFactionContentId());
            }
            StarSystemSimulationState systemState = null;
            for (StarSystemSimulationState candidate : systems) {
                if (candidate.systemId().equals(value.systemId())) {
                    systemState = candidate;
                    break;
                }
            }
            if (systemState == null) {
                throw new IllegalArgumentException("Construction project target session отсутствует");
            }
            if (value.constructionSiteEntityId() != null
                    && systemState.simulationState().entities().stream()
                    .noneMatch(entity -> entity.id().equals(value.constructionSiteEntityId()))) {
                throw new IllegalArgumentException("Construction project потерял site entity: " + value.id());
            }
            if (value.completedStationEntityId() != null
                    && systemState.simulationState().entities().stream()
                    .noneMatch(entity -> entity.id().equals(value.completedStationEntityId()))) {
                throw new IllegalArgumentException("Completed construction project потерял station entity: " + value.id());
            }
            maxProjectId = Math.max(maxProjectId, value.id().value());
            sortedProjects.add(value);
        }
        if (nextConstructionProjectIdValue <= maxProjectId) {
            throw new IllegalArgumentException("Construction project allocator watermark повторно использует существующий ID");
        }
        sortedProjects.sort(Comparator.naturalOrder());
        constructionProjects = List.copyOf(sortedProjects);
    }

    /**
     * Оборачивает legacy single-session save в минимальный world без выдуманного faction state.
     *
     * @param gameState текущий локальный GameState старого single-system мира
     * @return WorldState текущей schema с default topology и пустыми faction layers
     */
    public static WorldState singleSystem(GameState gameState) {
        return new WorldState(
                CURRENT_VERSION,
                WorldTopologyDefaults.singleSystem(),
                List.of(new StarSystemSimulationState(
                        WorldTopologyDefaults.DEFAULT_SYSTEM_ID,
                        Objects.requireNonNull(gameState, "GameState legacy world не задан"))),
                List.of(),
                List.of(),
                1L,
                List.of());
    }

    /**
     * Завершает декодирование Stage-7 schema v1 нейтральной миграцией.
     *
     * @param topology decoded Stage-7 topology
     * @param systems decoded Stage-7 local sessions
     * @return WorldState текущей schema без автоматически созданных faction state
     */
    public static WorldState fromLegacyStage7(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems) {
        return new WorldState(CURRENT_VERSION, topology, systems, List.of(), List.of(), 1L, List.of());
    }

    /**
     * Завершает декодирование treasury-only schema v2 без выдуманной diplomacy/territory.
     *
     * @param topology decoded topology
     * @param systems decoded local sessions
     * @param factions decoded treasury states
     * @return WorldState текущей schema с сохранёнными treasury и пустым strategic layer
     */
    public static WorldState fromLegacyFactionTreasury(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions) {
        return new WorldState(CURRENT_VERSION, topology, systems, factions, List.of(), 1L, List.of());
    }

    /**
     * Мигрирует Stage-8 schema v3, сохраняя diplomacy/territory и создавая пустой construction layer.
     *
     * @param topology decoded topology
     * @param systems decoded local sessions
     * @param factions decoded faction economy
     * @param strategies decoded strategic faction state
     * @return current WorldState with empty construction projects and allocator starting at one
     */
    public static WorldState fromLegacyStage8(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> strategies) {
        return new WorldState(CURRENT_VERSION, topology, systems, factions, strategies, 1L, List.of());
    }
}
