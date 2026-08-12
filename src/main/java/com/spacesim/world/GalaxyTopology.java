package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable persistent topology галактики с deterministic lookup и jump/system indexes.
 *
 * <p>Topology хранит только strategic structure. Экономические ECS-сущности остаются внутри
 * локальных SimulationSession. Планеты и asteroid fields индексируются глобально по stable ID и
 * могут быть разрешены обратно в родительскую StarSystem без обхода всей Galaxy.</p>
 */
public final class GalaxyTopology {
    private final GalaxyId id;
    private final String name;
    private final List<SectorNode> sectors;
    private final List<StarSystemNode> systems;
    private final List<JumpConnection> connections;
    private final Map<SectorId, SectorNode> sectorsById;
    private final Map<StarSystemId, StarSystemNode> systemsById;
    private final Map<StarSystemId, SectorNode> sectorsBySystemId;
    private final Map<StarSystemId, List<StarSystemId>> neighborsBySystemId;
    private final Map<PlanetId, PlanetNode> planetsById;
    private final Map<PlanetId, StarSystemNode> systemsByPlanetId;
    private final Map<AsteroidFieldId, AsteroidFieldNode> asteroidFieldsById;
    private final Map<AsteroidFieldId, StarSystemNode> systemsByAsteroidFieldId;

    /**
     * Создаёт и полностью валидирует topology.
     *
     * @param id устойчивый ID галактики
     * @param name отображаемое имя
     * @param sectors сектора в произвольном входном порядке
     * @param connections jump connections в произвольном входном порядке
     * @throws NullPointerException если обязательное значение не задано
     * @throws IllegalArgumentException при пустом имени, дублирующихся ID/connection или ссылке jump
     *         на отсутствующую систему
     */
    public GalaxyTopology(
            GalaxyId id,
            String name,
            List<SectorNode> sectors,
            List<JumpConnection> connections) {
        this.id = Objects.requireNonNull(id, "GalaxyId не задан");
        this.name = normalizedName(name);
        Objects.requireNonNull(sectors, "Список секторов не задан");
        Objects.requireNonNull(connections, "Список jump connections не задан");

        List<SectorNode> sortedSectors = new ArrayList<>(sectors.size());
        Map<SectorId, SectorNode> sectorIndex = new HashMap<>();
        Map<StarSystemId, StarSystemNode> systemIndex = new HashMap<>();
        Map<StarSystemId, SectorNode> systemSectorIndex = new HashMap<>();
        Map<PlanetId, PlanetNode> planetIndex = new HashMap<>();
        Map<PlanetId, StarSystemNode> planetSystemIndex = new HashMap<>();
        Map<AsteroidFieldId, AsteroidFieldNode> fieldIndex = new HashMap<>();
        Map<AsteroidFieldId, StarSystemNode> fieldSystemIndex = new HashMap<>();
        List<StarSystemNode> flattenedSystems = new ArrayList<>();

        for (SectorNode sector : sectors) {
            SectorNode value = Objects.requireNonNull(sector, "Сектор topology не задан");
            if (sectorIndex.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("Дублирующий SectorId: " + value.id());
            }
            sortedSectors.add(value);
            for (StarSystemNode system : value.systems()) {
                if (systemIndex.putIfAbsent(system.id(), system) != null) {
                    throw new IllegalArgumentException(
                            "Дублирующий StarSystemId между секторами: " + system.id());
                }
                systemSectorIndex.put(system.id(), value);
                flattenedSystems.add(system);
                for (PlanetNode planet : system.planets()) {
                    if (planetIndex.putIfAbsent(planet.id(), planet) != null) {
                        throw new IllegalArgumentException(
                                "Дублирующий PlanetId между системами: " + planet.id());
                    }
                    planetSystemIndex.put(planet.id(), system);
                }
                for (AsteroidFieldNode field : system.asteroidFields()) {
                    if (fieldIndex.putIfAbsent(field.id(), field) != null) {
                        throw new IllegalArgumentException(
                                "Дублирующий AsteroidFieldId между системами: " + field.id());
                    }
                    fieldSystemIndex.put(field.id(), system);
                }
            }
        }
        sortedSectors.sort(Comparator.comparing(SectorNode::id));
        flattenedSystems.sort(Comparator.comparing(StarSystemNode::id));

        List<JumpConnection> sortedConnections = new ArrayList<>(connections.size());
        Set<JumpConnection> uniqueConnections = new HashSet<>();
        Map<StarSystemId, TreeSet<StarSystemId>> mutableNeighbors = new HashMap<>();
        for (StarSystemNode system : flattenedSystems) {
            mutableNeighbors.put(system.id(), new TreeSet<>());
        }
        for (JumpConnection connection : connections) {
            JumpConnection value = Objects.requireNonNull(connection, "Jump connection не задан");
            requireKnownSystem(systemIndex, value.first());
            requireKnownSystem(systemIndex, value.second());
            if (!uniqueConnections.add(value)) {
                throw new IllegalArgumentException("Дублирующий jump connection: " + value);
            }
            sortedConnections.add(value);
            mutableNeighbors.get(value.first()).add(value.second());
            mutableNeighbors.get(value.second()).add(value.first());
        }
        sortedConnections.sort(Comparator.naturalOrder());

        Map<StarSystemId, List<StarSystemId>> neighborIndex = new HashMap<>();
        for (Map.Entry<StarSystemId, TreeSet<StarSystemId>> entry : mutableNeighbors.entrySet()) {
            neighborIndex.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        this.sectors = List.copyOf(sortedSectors);
        this.systems = List.copyOf(flattenedSystems);
        this.connections = List.copyOf(sortedConnections);
        this.sectorsById = Map.copyOf(sectorIndex);
        this.systemsById = Map.copyOf(systemIndex);
        this.sectorsBySystemId = Map.copyOf(systemSectorIndex);
        this.neighborsBySystemId = Map.copyOf(neighborIndex);
        this.planetsById = Map.copyOf(planetIndex);
        this.systemsByPlanetId = Map.copyOf(planetSystemIndex);
        this.asteroidFieldsById = Map.copyOf(fieldIndex);
        this.systemsByAsteroidFieldId = Map.copyOf(fieldSystemIndex);
    }

    /** @return устойчивый ID галактики */
    public GalaxyId id() {
        return id;
    }

    /** @return нормализованное отображаемое имя */
    public String name() {
        return name;
    }

    /** @return сектора в deterministic SectorId-порядке */
    public List<SectorNode> sectors() {
        return sectors;
    }

    /** @return все системы галактики в deterministic StarSystemId-порядке */
    public List<StarSystemNode> systems() {
        return systems;
    }

    /** @return канонические jump connections в deterministic порядке */
    public List<JumpConnection> connections() {
        return connections;
    }

    /**
     * Ищет сектор по ID.
     *
     * @param sectorId устойчивый ID сектора
     * @return найденный сектор либо empty
     */
    public Optional<SectorNode> findSector(SectorId sectorId) {
        return Optional.ofNullable(sectorId == null ? null : sectorsById.get(sectorId));
    }

    /**
     * Ищет систему по ID.
     *
     * @param systemId устойчивый ID системы
     * @return найденная система либо empty
     */
    public Optional<StarSystemNode> findSystem(StarSystemId systemId) {
        return Optional.ofNullable(systemId == null ? null : systemsById.get(systemId));
    }

    /**
     * Ищет родительский сектор системы.
     *
     * @param systemId устойчивый ID системы
     * @return сектор либо empty для неизвестной системы
     */
    public Optional<SectorNode> sectorOf(StarSystemId systemId) {
        return Optional.ofNullable(systemId == null ? null : sectorsBySystemId.get(systemId));
    }

    /**
     * Возвращает соседние по jump connections системы в deterministic ID-порядке.
     *
     * @param systemId устойчивый ID системы
     * @return immutable список соседей или пустой список для неизвестной системы
     */
    public List<StarSystemId> neighbors(StarSystemId systemId) {
        if (systemId == null) {
            return List.of();
        }
        return neighborsBySystemId.getOrDefault(systemId, List.of());
    }

    /**
     * Ищет стратегическую планету по глобальному stable ID.
     *
     * @param planetId ID планеты
     * @return планета либо empty
     */
    public Optional<PlanetNode> findPlanet(PlanetId planetId) {
        return Optional.ofNullable(planetId == null ? null : planetsById.get(planetId));
    }

    /**
     * Разрешает родительскую систему планеты.
     *
     * @param planetId ID планеты
     * @return StarSystem либо empty
     */
    public Optional<StarSystemNode> systemOf(PlanetId planetId) {
        return Optional.ofNullable(planetId == null ? null : systemsByPlanetId.get(planetId));
    }

    /**
     * Ищет strategic asteroid field по глобальному stable ID.
     *
     * @param fieldId ID поля
     * @return asteroid field либо empty
     */
    public Optional<AsteroidFieldNode> findAsteroidField(AsteroidFieldId fieldId) {
        return Optional.ofNullable(fieldId == null ? null : asteroidFieldsById.get(fieldId));
    }

    /**
     * Разрешает родительскую систему asteroid field.
     *
     * @param fieldId ID поля
     * @return StarSystem либо empty
     */
    public Optional<StarSystemNode> systemOf(AsteroidFieldId fieldId) {
        return Optional.ofNullable(fieldId == null ? null : systemsByAsteroidFieldId.get(fieldId));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GalaxyTopology topology)) {
            return false;
        }
        return id.equals(topology.id)
                && name.equals(topology.name)
                && sectors.equals(topology.sectors)
                && connections.equals(topology.connections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, sectors, connections);
    }

    @Override
    public String toString() {
        return "GalaxyTopology[id=" + id
                + ", name=" + name
                + ", sectors=" + sectors
                + ", connections=" + connections + ']';
    }

    private static String normalizedName(String name) {
        String value = Objects.requireNonNull(name, "Имя галактики не задано").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Имя галактики не может быть пустым");
        }
        return value;
    }

    private static void requireKnownSystem(
            Map<StarSystemId, StarSystemNode> systemIndex,
            StarSystemId systemId) {
        if (!systemIndex.containsKey(systemId)) {
            throw new IllegalArgumentException(
                    "Jump connection ссылается на неизвестную систему: " + systemId);
        }
    }
}
