package com.spacesim.world;

import com.spacesim.persistence.EntityState;
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
 * faction layers хранят treasury/strategy/pressure, construction layer — реальные проекты,
 * Stage-10 fleet layer отделяет устойчивый {@link FleetId} от system-local EntityId, а
 * Stage-10B jump layer сохраняет активную фазу межсистемного перехода. Stage 17 schema v9
 * дополнительно сохраняет world-defined faction identities отдельно от immutable authored
 * content catalog.</p>
 *
 * @param schemaVersion версия world-level persistent schema
 * @param topology immutable topology галактики
 * @param systems полный набор local simulation snapshots по системам
 * @param factions persistent strategic faction economy в canonical content-ID порядке
 * @param factionStrategies persistent diplomacy/territory state в canonical content-ID порядке
 * @param nextConstructionProjectIdValue следующий неиспользованный world-level construction project ID
 * @param constructionProjects persistent construction projects в canonical ID порядке
 * @param factionEconomicPressures persistent faction pressure/hysteresis states
 * @param nextFleetIdValue следующий неиспользованный world-level FleetId
 * @param fleets persistent fleet placement states в canonical FleetId порядке
 * @param fleetJumps persistent active jump states в canonical FleetId порядке
 * @param factionIdentities persistent world-defined faction identities в canonical stable-ID порядке
 * @param factionDiplomacyStates persistent institutional diplomacy в canonical faction-ID порядке
 */
public record WorldState(
        int schemaVersion,
        GalaxyTopology topology,
        List<StarSystemSimulationState> systems,
        List<FactionEconomicState> factions,
        List<FactionStrategicState> factionStrategies,
        long nextConstructionProjectIdValue,
        List<ConstructionProjectState> constructionProjects,
        List<FactionEconomicPressureState> factionEconomicPressures,
        long nextFleetIdValue,
        List<FleetPlacementState> fleets,
        List<FleetJumpState> fleetJumps,
        List<WorldFactionIdentityState> factionIdentities,
        List<FactionDiplomacyState> factionDiplomacyStates) {
    /**
     * Source-compatible pre-Stage-17E constructor with neutral explicit diplomacy.
     *
     * @param schemaVersion world schema version
     * @param topology galaxy topology
     * @param systems local simulation snapshots
     * @param factions faction economic states
     * @param factionStrategies faction strategic states
     * @param nextConstructionProjectIdValue construction allocator watermark
     * @param constructionProjects persistent construction projects
     * @param factionEconomicPressures persistent economic-pressure states
     * @param nextFleetIdValue fleet allocator watermark
     * @param fleets fleet placements
     * @param fleetJumps active jump states
     * @param factionIdentities world-defined faction identities
     */
    public WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> factionStrategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> constructionProjects,
            List<FactionEconomicPressureState> factionEconomicPressures,
            long nextFleetIdValue,
            List<FleetPlacementState> fleets,
            List<FleetJumpState> fleetJumps,
            List<WorldFactionIdentityState> factionIdentities) {
        this(
                schemaVersion,
                topology,
                systems,
                factions,
                factionStrategies,
                nextConstructionProjectIdValue,
                constructionProjects,
                factionEconomicPressures,
                nextFleetIdValue,
                fleets,
                fleetJumps,
                factionIdentities,
                neutralDiplomacy(factionStrategies));
    }

    /** Текущая Stage-17 версия world-level persistent schema. */
    public static final int CURRENT_VERSION = 9;
    /** Stage-16 schema с external-owner construction settlement, без dynamic faction directory. */
    public static final int LEGACY_STAGE16_VERSION = 8;
    /** Stage-10B/Stage-15 schema с active jump FSM и faction-only construction settlement. */
    public static final int LEGACY_STAGE10_JUMP_VERSION = 7;
    /** Stage-10A schema с FleetId/placement layer, но без active jump FSM. */
    public static final int LEGACY_STAGE10A_VERSION = 6;
    /** Stage-9D/9E schema с persistent economic pressure, но без world FleetId layer. */
    public static final int LEGACY_STAGE9_PRESSURE_VERSION = 5;
    /** Stage-9B/9C schema с construction projects, но без persistent economic pressure. */
    public static final int LEGACY_STAGE9_CONSTRUCTION_VERSION = 4;
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
        this(schemaVersion, topology, systems, List.of(), List.of(), 1L, List.of(), List.of(),
                FleetBootstrap.create(systems));
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
        this(schemaVersion, topology, systems, factions, List.of(), 1L, List.of(), List.of(),
                FleetBootstrap.create(systems));
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
        this(schemaVersion, topology, systems, factions, factionStrategies, 1L, List.of(), List.of(),
                FleetBootstrap.create(systems));
    }

    /**
     * Source-compatible конструктор Stage-9 world без pressure state.
     *
     * @param schemaVersion текущая world schema
     * @param topology topology галактики
     * @param systems snapshots систем
     * @param factions faction economic states
     * @param factionStrategies diplomacy/territory states
     * @param nextConstructionProjectIdValue construction allocator watermark
     * @param constructionProjects persistent construction projects
     */
    public WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> factionStrategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> constructionProjects) {
        this(schemaVersion, topology, systems, factions, factionStrategies,
                nextConstructionProjectIdValue, constructionProjects, List.of(),
                FleetBootstrap.create(systems));
    }

    /**
     * Source-compatible Stage-9 pressure constructor, автоматически создающий world FleetIds.
     *
     * @param schemaVersion текущая world schema
     * @param topology topology галактики
     * @param systems snapshots систем
     * @param factions faction economic states
     * @param factionStrategies diplomacy/territory states
     * @param nextConstructionProjectIdValue construction allocator watermark
     * @param constructionProjects persistent construction projects
     * @param factionEconomicPressures persistent faction pressure states
     */
    public WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> factionStrategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> constructionProjects,
            List<FactionEconomicPressureState> factionEconomicPressures) {
        this(schemaVersion, topology, systems, factions, factionStrategies,
                nextConstructionProjectIdValue, constructionProjects, factionEconomicPressures,
                FleetBootstrap.create(systems));
    }

    /**
     * Source-compatible Stage-10A constructor без active jump state.
     *
     * @param schemaVersion текущая world schema
     * @param topology topology галактики
     * @param systems snapshots систем
     * @param factions faction economic states
     * @param factionStrategies diplomacy/territory states
     * @param nextConstructionProjectIdValue construction allocator watermark
     * @param constructionProjects persistent construction projects
     * @param factionEconomicPressures persistent faction pressure states
     * @param nextFleetIdValue world FleetId allocator watermark
     * @param fleets persistent fleet placements
     */
    public WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> factionStrategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> constructionProjects,
            List<FactionEconomicPressureState> factionEconomicPressures,
            long nextFleetIdValue,
            List<FleetPlacementState> fleets) {
        this(schemaVersion, topology, systems, factions, factionStrategies,
                nextConstructionProjectIdValue, constructionProjects, factionEconomicPressures,
                nextFleetIdValue, fleets, List.of());
    }

    /**
     * Source-compatible Stage-16 constructor без world-defined faction identities.
     *
     * @param schemaVersion текущая world schema
     * @param topology topology галактики
     * @param systems snapshots систем
     * @param factions faction economic states
     * @param factionStrategies diplomacy/territory states
     * @param nextConstructionProjectIdValue construction allocator watermark
     * @param constructionProjects persistent construction projects
     * @param factionEconomicPressures persistent faction pressure states
     * @param nextFleetIdValue world FleetId allocator watermark
     * @param fleets persistent fleet placements
     * @param fleetJumps persistent active jump states
     */
    public WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> factionStrategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> constructionProjects,
            List<FactionEconomicPressureState> factionEconomicPressures,
            long nextFleetIdValue,
            List<FleetPlacementState> fleets,
            List<FleetJumpState> fleetJumps) {
        this(schemaVersion, topology, systems, factions, factionStrategies,
                nextConstructionProjectIdValue, constructionProjects, factionEconomicPressures,
                nextFleetIdValue, fleets, fleetJumps, List.of());
    }

    private WorldState(
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> factionStrategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> constructionProjects,
            List<FactionEconomicPressureState> factionEconomicPressures,
            FleetBootstrap.Result fleetBootstrap) {
        this(schemaVersion, topology, systems, factions, factionStrategies,
                nextConstructionProjectIdValue, constructionProjects, factionEconomicPressures,
                fleetBootstrap.nextId(), fleetBootstrap.placements(), List.of());
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
     * @param factionEconomicPressures persistent faction pressure states
     * @param nextFleetIdValue следующий world FleetId allocator watermark
     * @param fleets world-level fleet placement states
     * @param fleetJumps active persistent jump states
     * @param factionIdentities world-defined faction identity states
     * @param factionDiplomacyStates persistent institutional diplomacy states
     * @throws NullPointerException если обязательное значение не задано
     * @throws IllegalArgumentException при неизвестной версии, duplicate/unknown IDs,
     *         неполном topology/fleet coverage или несовместимом fleet location state
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
        Objects.requireNonNull(factionEconomicPressures, "Economic pressure states WorldState не заданы");
        Objects.requireNonNull(fleets, "Fleet placement states WorldState не заданы");
        Objects.requireNonNull(fleetJumps, "Fleet jump states WorldState не заданы");
        Objects.requireNonNull(factionIdentities, "World faction identities WorldState не заданы");
        Objects.requireNonNull(factionDiplomacyStates, "Faction diplomacy states WorldState not set");
        if (nextConstructionProjectIdValue <= 0L) {
            throw new IllegalArgumentException("Следующий ConstructionProjectId должен быть положительным");
        }
        if (nextFleetIdValue <= 0L) {
            throw new IllegalArgumentException("Следующий FleetId должен быть положительным");
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

        List<FactionDiplomacyState> sortedDiplomacy = new ArrayList<>(factionDiplomacyStates.size());
        Set<String> diplomacyFactionIds = new HashSet<>();
        Set<String> treatyIds = new HashSet<>();
        for (FactionDiplomacyState diplomacy : factionDiplomacyStates) {
            FactionDiplomacyState value = Objects.requireNonNull(diplomacy, "FactionDiplomacyState not set");
            if (!strategicFactionIds.contains(value.factionContentId())) {
                throw new IllegalArgumentException("Diplomacy state references unknown strategic faction: "
                        + value.factionContentId());
            }
            if (!diplomacyFactionIds.add(value.factionContentId())) {
                throw new IllegalArgumentException("Duplicate faction diplomacy state: " + value.factionContentId());
            }
            for (DiplomaticStandingState standing : value.standings()) {
                requireDiplomaticTarget(strategicFactionIds, standing.targetFactionContentId());
            }
            for (DiplomaticGrievanceState grievance : value.grievances()) {
                requireDiplomaticTarget(strategicFactionIds, grievance.targetFactionContentId());
            }
            for (DiplomaticTreatyState treaty : value.treaties()) {
                requireDiplomaticTarget(strategicFactionIds, treaty.counterpartyFactionContentId());
                if (!treatyIds.add(treaty.treatyId())) {
                    throw new IllegalArgumentException("Duplicate world treaty ID: " + treaty.treatyId());
                }
                for (DiplomaticTreatyClauseState clause : treaty.clauses()) {
                    if (clause.systemId() != null && topology.findSystem(clause.systemId()).isEmpty()) {
                        throw new IllegalArgumentException("Treaty clause references unknown StarSystem: "
                                + clause.systemId());
                    }
                }
            }
            for (DiplomaticEmbargoState embargo : value.embargoes()) {
                requireDiplomaticTarget(strategicFactionIds, embargo.targetFactionContentId());
            }
            sortedDiplomacy.add(value);
        }
        if (!diplomacyFactionIds.equals(strategicFactionIds)) {
            throw new IllegalArgumentException("Faction diplomacy states must exactly cover strategic factions");
        }
        sortedDiplomacy.sort(Comparator.naturalOrder());
        factionDiplomacyStates = List.copyOf(sortedDiplomacy);

        List<WorldFactionIdentityState> sortedIdentities = new ArrayList<>(factionIdentities.size());
        Set<String> identityStableIds = new HashSet<>();
        Set<Integer> identityRuntimeIds = new HashSet<>();
        for (WorldFactionIdentityState identity : factionIdentities) {
            WorldFactionIdentityState value = Objects.requireNonNull(identity, "WorldFactionIdentityState не задан");
            if (!identityStableIds.add(value.stableFactionId())) {
                throw new IllegalArgumentException(
                        "Дублирующий world faction stable ID: " + value.stableFactionId());
            }
            if (!identityRuntimeIds.add(value.runtimeFactionId())) {
                throw new IllegalArgumentException(
                        "Дублирующий world faction runtime ID: " + value.runtimeFactionId());
            }
            sortedIdentities.add(value);
        }
        sortedIdentities.sort(Comparator.naturalOrder());
        factionIdentities = List.copyOf(sortedIdentities);

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
            if (value.settlementKind() == ConstructionSettlementKind.FACTION_TREASURY
                    && !economicFactionIds.contains(value.ownerFactionContentId())) {
                throw new IllegalArgumentException("Construction project ссылается на неизвестную faction account: "
                        + value.ownerFactionContentId());
            }
            StarSystemSimulationState systemState = findSystemState(systems, value.systemId());
            if (value.constructionSiteEntityId() != null
                    && systemState.simulationState().entities().stream()
                    .noneMatch(entity -> entity.id().equals(value.constructionSiteEntityId()))) {
                throw new IllegalArgumentException("Construction project потерял site entity: " + value.id());
            }
            maxProjectId = Math.max(maxProjectId, value.id().value());
            sortedProjects.add(value);
        }
        if (nextConstructionProjectIdValue <= maxProjectId) {
            throw new IllegalArgumentException("Construction project allocator watermark повторно использует существующий ID");
        }
        sortedProjects.sort(Comparator.naturalOrder());
        constructionProjects = List.copyOf(sortedProjects);

        List<FactionEconomicPressureState> sortedPressures = new ArrayList<>(factionEconomicPressures.size());
        Set<String> pressureKeys = new HashSet<>();
        for (FactionEconomicPressureState pressure : factionEconomicPressures) {
            FactionEconomicPressureState value = Objects.requireNonNull(pressure, "FactionEconomicPressureState не задан");
            if (!economicFactionIds.contains(value.factionContentId())) {
                throw new IllegalArgumentException("Economic pressure ссылается на неизвестную faction: "
                        + value.factionContentId());
            }
            if (topology.findSystem(value.systemId()).isEmpty()) {
                throw new IllegalArgumentException("Economic pressure ссылается на неизвестную StarSystem: "
                        + value.systemId());
            }
            String key = value.factionContentId() + "\u0000" + value.systemId().value() + "\u0000" + value.itemContentId();
            if (!pressureKeys.add(key)) {
                throw new IllegalArgumentException("Дублирующий economic pressure key: " + key);
            }
            sortedPressures.add(value);
        }
        sortedPressures.sort(Comparator.naturalOrder());
        factionEconomicPressures = List.copyOf(sortedPressures);

        Set<String> localFleetKeys = new HashSet<>();
        for (StarSystemSimulationState system : systems) {
            for (EntityState entity : system.simulationState().entities()) {
                if (isFleet(entity)) {
                    localFleetKeys.add(localFleetKey(system.systemId(), entity.id().value()));
                }
            }
        }

        List<FleetPlacementState> sortedFleets = new ArrayList<>(fleets.size());
        Set<FleetId> fleetIds = new HashSet<>();
        Set<String> coveredLocalFleets = new HashSet<>();
        long maxFleetId = 0L;
        for (FleetPlacementState placement : fleets) {
            FleetPlacementState value = Objects.requireNonNull(placement, "FleetPlacementState не задан");
            Objects.requireNonNull(value.id(), "FleetPlacementState FleetId не задан");
            Objects.requireNonNull(value.locationKind(), "FleetPlacementState locationKind не задан");
            if (!fleetIds.add(value.id())) {
                throw new IllegalArgumentException("Дублирующий FleetId: " + value.id());
            }
            maxFleetId = Math.max(maxFleetId, value.id().value());
            if (value.locationKind() == FleetLocationKind.IN_SYSTEM) {
                if (value.systemId() == null || value.localEntityId() == null || value.transitState() != null) {
                    throw new IllegalArgumentException("IN_SYSTEM fleet имеет несовместимое location state: " + value.id());
                }
                if (topology.findSystem(value.systemId()).isEmpty()) {
                    throw new IllegalArgumentException("Fleet ссылается на неизвестную StarSystem: " + value.systemId());
                }
                EntityState entity = findEntity(systems, value.systemId(), value.localEntityId().value());
                if (!isFleet(entity)) {
                    throw new IllegalArgumentException("Fleet placement не указывает на local fleet entity: " + value.id());
                }
                String key = localFleetKey(value.systemId(), value.localEntityId().value());
                if (!coveredLocalFleets.add(key)) {
                    throw new IllegalArgumentException("Local fleet имеет несколько world placements: " + key);
                }
            } else if (value.locationKind() == FleetLocationKind.IN_TRANSIT) {
                if (value.systemId() != null || value.localEntityId() != null || value.transitState() == null) {
                    throw new IllegalArgumentException("IN_TRANSIT fleet имеет несовместимое local location: " + value.id());
                }
                FleetTransitState transit = value.transitState();
                if (topology.findSystem(transit.originSystemId()).isEmpty()
                        || topology.findSystem(transit.destinationSystemId()).isEmpty()) {
                    throw new IllegalArgumentException("Transit fleet ссылается на неизвестную StarSystem: " + value.id());
                }
                if (!isFleet(transit.entityState())) {
                    throw new IllegalArgumentException("Transit payload не является fleet: " + value.id());
                }
                if (findEntityOrNull(systems, transit.originSystemId(), transit.entityState().id().value()) != null) {
                    throw new IllegalArgumentException("Transit fleet всё ещё существует в origin session: " + value.id());
                }
            } else {
                throw new IllegalArgumentException("Неизвестный FleetLocationKind: " + value.locationKind());
            }
            sortedFleets.add(value);
        }
        if (!coveredLocalFleets.equals(localFleetKeys)) {
            Set<String> missing = new HashSet<>(localFleetKeys);
            missing.removeAll(coveredLocalFleets);
            throw new IllegalArgumentException("Не все local fleets имеют world FleetId: " + missing);
        }
        if (nextFleetIdValue <= maxFleetId) {
            throw new IllegalArgumentException("Fleet allocator watermark повторно использует существующий ID");
        }
        sortedFleets.sort(Comparator.naturalOrder());
        fleets = List.copyOf(sortedFleets);

        List<FleetJumpState> sortedJumps = new ArrayList<>(fleetJumps.size());
        Set<FleetId> jumpFleetIds = new HashSet<>();
        for (FleetJumpState jump : fleetJumps) {
            FleetJumpState value = Objects.requireNonNull(jump, "FleetJumpState не задан");
            if (!jumpFleetIds.add(value.fleetId())) {
                throw new IllegalArgumentException("Fleet имеет несколько active jump states: " + value.fleetId());
            }
            if (topology.findSystem(value.originSystemId()).isEmpty()
                    || topology.findSystem(value.destinationSystemId()).isEmpty()
                    || !topology.neighbors(value.originSystemId()).contains(value.destinationSystemId())) {
                throw new IllegalArgumentException(
                        "Fleet jump не имеет direct topology connection: " + value.fleetId());
            }
            FleetPlacementState placement = findFleetPlacement(fleets, value.fleetId());
            if (placement == null) {
                throw new IllegalArgumentException(
                        "Fleet jump ссылается на неизвестный FleetId: " + value.fleetId());
            }
            switch (value.phase()) {
                case MOVING_TO_JUMP, JUMP_PENDING -> {
                    if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                            || !value.originSystemId().equals(placement.systemId())) {
                        throw new IllegalArgumentException(
                                "Pre-jump phase не совпадает с origin placement: " + value.fleetId());
                    }
                }
                case IN_TRANSIT -> {
                    FleetTransitState transit = placement.transitState();
                    if (placement.locationKind() != FleetLocationKind.IN_TRANSIT
                            || transit == null
                            || !value.originSystemId().equals(transit.originSystemId())
                            || !value.destinationSystemId().equals(transit.destinationSystemId())) {
                        throw new IllegalArgumentException(
                                "Jump transit phase не совпадает с physical transit: " + value.fleetId());
                    }
                }
                case ARRIVING -> {
                    if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                            || !value.destinationSystemId().equals(placement.systemId())) {
                        throw new IllegalArgumentException(
                                "Arrival phase не совпадает с destination placement: " + value.fleetId());
                    }
                }
            }
            sortedJumps.add(value);
        }
        sortedJumps.sort(Comparator.naturalOrder());
        fleetJumps = List.copyOf(sortedJumps);
    }

    /**
     * Оборачивает legacy single-session save в минимальный world без выдуманного faction state.
     *
     * @param gameState текущий локальный GameState старого single-system мира
     * @return WorldState текущей schema с deterministic FleetIds
     */
    public static WorldState singleSystem(GameState gameState) {
        List<StarSystemSimulationState> systems = List.of(new StarSystemSimulationState(
                WorldTopologyDefaults.DEFAULT_SYSTEM_ID,
                Objects.requireNonNull(gameState, "GameState legacy world не задан")));
        return new WorldState(CURRENT_VERSION, WorldTopologyDefaults.singleSystem(), systems,
                List.of(), List.of(), 1L, List.of(), List.of());
    }

    /**
     * Завершает декодирование Stage-7 schema v1 нейтральной миграцией.
     *
     * @param topology decoded Stage-7 topology
     * @param systems decoded Stage-7 local sessions
     * @return WorldState текущей schema
     */
    public static WorldState fromLegacyStage7(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems) {
        return new WorldState(CURRENT_VERSION, topology, systems, List.of(), List.of(), 1L, List.of(), List.of());
    }

    /**
     * Завершает декодирование treasury-only schema v2.
     *
     * @param topology decoded topology
     * @param systems decoded local sessions
     * @param factions decoded treasury states
     * @return WorldState текущей schema
     */
    public static WorldState fromLegacyFactionTreasury(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions) {
        return new WorldState(CURRENT_VERSION, topology, systems, factions, List.of(), 1L, List.of(), List.of());
    }

    /**
     * Мигрирует Stage-8 schema v3.
     *
     * @param topology decoded topology
     * @param systems decoded local sessions
     * @param factions decoded faction economy
     * @param strategies decoded strategic faction state
     * @return current WorldState
     */
    public static WorldState fromLegacyStage8(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> strategies) {
        return new WorldState(CURRENT_VERSION, topology, systems, factions, strategies, 1L, List.of(), List.of());
    }

    /**
     * Мигрирует Stage-9 schema v4, сохраняя construction layer.
     *
     * @param topology decoded topology
     * @param systems decoded local sessions
     * @param factions decoded faction economy
     * @param strategies decoded strategic faction state
     * @param nextConstructionProjectIdValue decoded construction allocator watermark
     * @param projects decoded construction projects
     * @return current WorldState
     */
    public static WorldState fromLegacyStage9Construction(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> strategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> projects) {
        return new WorldState(CURRENT_VERSION, topology, systems, factions, strategies,
                nextConstructionProjectIdValue, projects, List.of());
    }

    /**
     * Мигрирует Stage-9 schema v5, сохраняя pressure layer и создавая deterministic FleetIds.
     *
     * @param topology decoded topology
     * @param systems decoded local sessions
     * @param factions decoded faction economy
     * @param strategies decoded strategic faction state
     * @param nextConstructionProjectIdValue decoded construction allocator watermark
     * @param projects decoded construction projects
     * @param pressures decoded economic pressure states
     * @return current WorldState with deterministic fleet placements
     */
    public static WorldState fromLegacyStage9Pressure(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> strategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> projects,
            List<FactionEconomicPressureState> pressures) {
        return new WorldState(CURRENT_VERSION, topology, systems, factions, strategies,
                nextConstructionProjectIdValue, projects, pressures);
    }

    /**
     * Мигрирует Stage-10A schema v6 без выдуманных active jump states.
     *
     * @param topology decoded topology
     * @param systems decoded local sessions
     * @param factions decoded faction economy
     * @param strategies decoded strategic faction state
     * @param nextConstructionProjectIdValue decoded construction allocator watermark
     * @param projects decoded construction projects
     * @param pressures decoded economic pressure states
     * @param nextFleetIdValue decoded FleetId allocator watermark
     * @param fleets decoded fleet placements
     * @return current WorldState with empty active jump layer
     */
    public static WorldState fromLegacyStage10A(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> strategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> projects,
            List<FactionEconomicPressureState> pressures,
            long nextFleetIdValue,
            List<FleetPlacementState> fleets) {
        return new WorldState(CURRENT_VERSION, topology, systems, factions, strategies,
                nextConstructionProjectIdValue, projects, pressures,
                nextFleetIdValue, fleets, List.of());
    }

    /**
     * Мигрирует Stage-10B/Stage-15 schema v7 и сохраняет active jump state.
     *
     * @param topology decoded topology
     * @param systems decoded local sessions
     * @param factions decoded faction economy
     * @param strategies decoded strategic faction state
     * @param nextConstructionProjectIdValue decoded construction allocator watermark
     * @param projects decoded faction-only construction projects
     * @param pressures decoded economic pressure states
     * @param nextFleetIdValue decoded FleetId allocator watermark
     * @param fleets decoded fleet placements
     * @param jumps decoded active jump states
     * @return current Stage-17 WorldState
     */
    public static WorldState fromLegacyStage10Jump(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> strategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> projects,
            List<FactionEconomicPressureState> pressures,
            long nextFleetIdValue,
            List<FleetPlacementState> fleets,
            List<FleetJumpState> jumps) {
        return new WorldState(CURRENT_VERSION, topology, systems, factions, strategies,
                nextConstructionProjectIdValue, projects, pressures,
                nextFleetIdValue, fleets, jumps);
    }

    /**
     * Мигрирует Stage-16 schema v8, сохраняя весь физический мир и создавая пустой dynamic directory.
     *
     * @param topology decoded topology
     * @param systems decoded local sessions
     * @param factions decoded faction economy
     * @param strategies decoded strategic faction state
     * @param nextConstructionProjectIdValue decoded construction allocator watermark
     * @param projects decoded construction projects
     * @param pressures decoded economic pressure states
     * @param nextFleetIdValue decoded FleetId allocator watermark
     * @param fleets decoded fleet placements
     * @param jumps decoded active jump states
     * @return current Stage-17 WorldState with no invented dynamic factions
     */
    public static WorldState fromLegacyStage16(
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems,
            List<FactionEconomicState> factions,
            List<FactionStrategicState> strategies,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> projects,
            List<FactionEconomicPressureState> pressures,
            long nextFleetIdValue,
            List<FleetPlacementState> fleets,
            List<FleetJumpState> jumps) {
        return new WorldState(CURRENT_VERSION, topology, systems, factions, strategies,
                nextConstructionProjectIdValue, projects, pressures,
                nextFleetIdValue, fleets, jumps, List.of());
    }

    private static FleetPlacementState findFleetPlacement(
            List<FleetPlacementState> fleets, FleetId fleetId) {
        for (FleetPlacementState placement : fleets) {
            if (placement.id().equals(fleetId)) {
                return placement;
            }
        }
        return null;
    }

    private static StarSystemSimulationState findSystemState(
            List<StarSystemSimulationState> systems,
            StarSystemId id) {
        for (StarSystemSimulationState system : systems) {
            if (system.systemId().equals(id)) {
                return system;
            }
        }
        throw new IllegalArgumentException("Отсутствует simulation state StarSystem: " + id);
    }

    private static EntityState findEntity(
            List<StarSystemSimulationState> systems,
            StarSystemId systemId,
            long entityId) {
        EntityState entity = findEntityOrNull(systems, systemId, entityId);
        if (entity == null) {
            throw new IllegalArgumentException("Local entity отсутствует: " + systemId + "/entity:" + entityId);
        }
        return entity;
    }

    private static EntityState findEntityOrNull(
            List<StarSystemSimulationState> systems,
            StarSystemId systemId,
            long entityId) {
        StarSystemSimulationState system = findSystemState(systems, systemId);
        for (EntityState entity : system.simulationState().entities()) {
            if (entity.id().value() == entityId) {
                return entity;
            }
        }
        return null;
    }

    private static boolean isFleet(EntityState entity) {
        return entity != null && entity.identity() != null && "FLEET".equals(entity.identity().kindName());
    }

    private static String localFleetKey(StarSystemId systemId, long entityId) {
        return systemId.value() + ":" + entityId;
    }
    private static List<FactionDiplomacyState> neutralDiplomacy(List<FactionStrategicState> strategies) {
        Objects.requireNonNull(strategies, "Faction strategic states not set");
        List<FactionDiplomacyState> result = new ArrayList<>(strategies.size());
        for (FactionStrategicState strategy : strategies) {
            result.add(FactionDiplomacyState.neutral(
                    Objects.requireNonNull(strategy, "FactionStrategicState not set").factionContentId()));
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static void requireDiplomaticTarget(Set<String> knownFactionIds, String targetFactionContentId) {
        if (!knownFactionIds.contains(targetFactionContentId)) {
            throw new IllegalArgumentException("Diplomacy state references unknown target faction: "
                    + targetFactionContentId);
        }
    }

}
