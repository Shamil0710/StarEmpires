package com.spacesim.persistence;

import com.spacesim.world.AsteroidFieldId;
import com.spacesim.world.AsteroidFieldNode;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionProductionPolicyState;
import com.spacesim.world.FactionRelationState;
import com.spacesim.world.FactionStockPolicyState;
import com.spacesim.world.FactionStrategicGoalState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.PlanetId;
import com.spacesim.world.PlanetNode;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.WorldState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Детерминированный бинарный codec {@link WorldState}.
 *
 * <p>Local economic payload кодируется существующим {@link GameStateCodec}. World schema v1
 * содержит topology + local sessions, v2 добавляет treasury, а текущая v3 — strategic faction
 * state: diplomacy, territory, fiscal rates, stock/production policies и military/expansion goals.
 * Legacy v1/v2 мигрируются нейтрально без создания отсутствующих денег или strategic policies.</p>
 */
public final class WorldStateCodec {
    private static final int MAGIC = 0x53544757;
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_SAVE_BYTES = 256 * 1024 * 1024;
    private static final int MAX_GAMESTATE_PAYLOAD_BYTES = 32 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final int MAX_SECTORS = 10_000;
    private static final int MAX_SYSTEMS = 100_000;
    private static final int MAX_PLANETS = 1_000_000;
    private static final int MAX_ASTEROID_FIELDS = 1_000_000;
    private static final int MAX_CONNECTIONS = 500_000;
    private static final int MAX_FACTIONS = 10_000;
    private static final int MAX_POLICIES_PER_FACTION = 100_000;
    private static final int MAX_GOALS_PER_FACTION = 100_000;

    private WorldStateCodec() {
        throw new AssertionError("WorldStateCodec не создаёт экземпляров");
    }

    /**
     * Кодирует полный world snapshot.
     *
     * @param state валидный WorldState текущей schema
     * @return новый deterministic byte array
     * @throws NullPointerException если state не задан
     * @throws IllegalArgumentException если schema или размер недопустимы
     */
    public static byte[] encode(WorldState state) {
        WorldState checked = Objects.requireNonNull(state, "WorldState не задан");
        if (checked.schemaVersion() != WorldState.CURRENT_VERSION) {
            throw new IllegalArgumentException("Нельзя записать WorldState schema: " + checked.schemaVersion());
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.schemaVersion());
                writeTopology(output, checked.topology());
                writeSystems(output, checked.systems());
                writeFactions(output, checked.factions());
                writeFactionStrategies(output, checked.factionStrategies());
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length > MAX_SAVE_BYTES) {
                throw new IllegalArgumentException("World save превышает допустимый размер");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Неожиданная ошибка памяти при кодировании WorldState", exception);
        }
    }

    /**
     * Декодирует и мигрирует world snapshot.
     *
     * @param bytes бинарный world save
     * @return immutable WorldState текущей schema
     * @throws NullPointerException если bytes не заданы
     * @throws IllegalArgumentException если save повреждён или schema неизвестна
     */
    public static WorldState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Байты WorldState не заданы");
        if (bytes.length == 0 || bytes.length > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Размер WorldState находится вне допустимого диапазона");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Некорректный magic WorldState");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Неподдерживаемая версия world-файла: " + fileVersion);
            }
            int schemaVersion = input.readInt();
            if (schemaVersion != WorldState.CURRENT_VERSION
                    && schemaVersion != WorldState.LEGACY_FACTION_TREASURY_VERSION
                    && schemaVersion != WorldState.LEGACY_STAGE7_VERSION) {
                throw new IllegalArgumentException("Неподдерживаемая WorldState schema: " + schemaVersion);
            }
            GalaxyTopology topology = readTopology(input);
            List<StarSystemSimulationState> systems = readSystems(input);
            WorldState state;
            if (schemaVersion == WorldState.LEGACY_STAGE7_VERSION) {
                state = WorldState.fromLegacyStage7(topology, systems);
            } else {
                List<FactionEconomicState> factions = readFactions(input);
                state = schemaVersion == WorldState.LEGACY_FACTION_TREASURY_VERSION
                        ? WorldState.fromLegacyFactionTreasury(topology, systems, factions)
                        : new WorldState(
                                WorldState.CURRENT_VERSION,
                                topology,
                                systems,
                                factions,
                                readFactionStrategies(input));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("После WorldState обнаружен лишний бинарный хвост");
            }
            return state;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("WorldState оборван", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("WorldState невозможно декодировать", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("WorldState содержит повреждённые значения", exception);
        }
    }

    /**
     * Атомарно записывает world snapshot.
     *
     * @param path целевой файл
     * @param state сохраняемый WorldState
     * @throws NullPointerException если path/state не заданы
     * @throws IOException если файл нельзя записать
     */
    public static void write(Path path, WorldState state) throws IOException {
        Path target = Objects.requireNonNull(path, "Путь WorldState не задан").toAbsolutePath();
        byte[] bytes = encode(Objects.requireNonNull(state, "WorldState не задан"));
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "world-" + prefix;
        }
        Path temp = Files.createTempFile(parent, prefix, ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Читает ограниченный world save с диска.
     *
     * @param path существующий файл
     * @return decoded WorldState
     * @throws NullPointerException если path не задан
     * @throws IOException если файл нельзя прочитать
     * @throws IllegalArgumentException если размер/формат некорректны
     */
    public static WorldState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "Путь WorldState не задан").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Размер WorldState находится вне допустимого диапазона");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writeSystems(DataOutputStream output, List<StarSystemSimulationState> systems)
            throws IOException {
        writeCount(output, systems.size(), MAX_SYSTEMS, "systemStates");
        for (StarSystemSimulationState systemState : systems) {
            output.writeLong(systemState.systemId().value());
            byte[] payload = GameStateCodec.encode(systemState.simulationState());
            if (payload.length <= 0 || payload.length > MAX_GAMESTATE_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("GameState payload системы превышает допустимый размер");
            }
            output.writeInt(payload.length);
            output.write(payload);
        }
    }

    private static List<StarSystemSimulationState> readSystems(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_SYSTEMS, "systemStates");
        List<StarSystemSimulationState> systems = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            StarSystemId systemId = new StarSystemId(input.readLong());
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAX_GAMESTATE_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Некорректная длина GameState payload системы");
            }
            byte[] payload = input.readNBytes(payloadLength);
            if (payload.length != payloadLength) {
                throw new EOFException("GameState payload системы оборван");
            }
            systems.add(new StarSystemSimulationState(systemId, GameStateCodec.decode(payload)));
        }
        return List.copyOf(systems);
    }

    private static void writeFactions(DataOutputStream output, List<FactionEconomicState> factions)
            throws IOException {
        writeCount(output, factions.size(), MAX_FACTIONS, "factions");
        for (FactionEconomicState faction : factions) {
            writeString(output, faction.factionContentId());
            output.writeLong(faction.treasuryMilliCredits());
            output.writeLong(faction.stationLiquidityReserveMilliCredits());
            output.writeLong(faction.maxLiquiditySupportPerDecisionMilliCredits());
        }
    }

    private static List<FactionEconomicState> readFactions(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_FACTIONS, "factions");
        List<FactionEconomicState> factions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            factions.add(new FactionEconomicState(
                    readString(input), input.readLong(), input.readLong(), input.readLong()));
        }
        return List.copyOf(factions);
    }

    private static void writeFactionStrategies(
            DataOutputStream output,
            List<FactionStrategicState> strategies) throws IOException {
        writeCount(output, strategies.size(), MAX_FACTIONS, "factionStrategies");
        for (FactionStrategicState value : strategies) {
            writeString(output, value.factionContentId());
            output.writeInt(value.minimumMarketAccessRelation());
            writeCount(output, value.relations().size(), MAX_FACTIONS, "factionRelations");
            for (FactionRelationState relation : value.relations()) {
                writeString(output, relation.targetFactionContentId());
                output.writeInt(relation.relation());
            }
            writeCount(output, value.controlledSystems().size(), MAX_SYSTEMS, "controlledSystems");
            for (StarSystemId systemId : value.controlledSystems()) {
                output.writeLong(systemId.value());
            }
            output.writeInt(value.stationTaxBasisPoints());
            output.writeInt(value.foreignTerritoryTariffBasisPoints());
            writeCount(output, value.stockPolicies().size(), MAX_POLICIES_PER_FACTION, "stockPolicies");
            for (FactionStockPolicyState policy : value.stockPolicies()) {
                writeString(output, policy.itemContentId());
                output.writeInt(policy.targetStockFloor());
            }
            writeCount(output, value.productionPolicies().size(), MAX_POLICIES_PER_FACTION, "productionPolicies");
            for (FactionProductionPolicyState policy : value.productionPolicies()) {
                writeString(output, policy.stationArchetypeContentId());
                writeString(output, policy.recipeContentId());
            }
            writeCount(output, value.strategicGoals().size(), MAX_GOALS_PER_FACTION, "strategicGoals");
            for (FactionStrategicGoalState goal : value.strategicGoals()) {
                writeString(output, goal.goalId());
                writeString(output, goal.type().name());
                writeCount(output, goal.demandFloors().size(), MAX_POLICIES_PER_FACTION, "goalDemandFloors");
                for (FactionStockPolicyState demand : goal.demandFloors()) {
                    writeString(output, demand.itemContentId());
                    output.writeInt(demand.targetStockFloor());
                }
            }
        }
    }

    private static List<FactionStrategicState> readFactionStrategies(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_FACTIONS, "factionStrategies");
        List<FactionStrategicState> strategies = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String factionId = readString(input);
            int threshold = input.readInt();
            int relationCount = readCount(input, MAX_FACTIONS, "factionRelations");
            List<FactionRelationState> relations = new ArrayList<>(relationCount);
            for (int relationIndex = 0; relationIndex < relationCount; relationIndex++) {
                relations.add(new FactionRelationState(readString(input), input.readInt()));
            }
            int systemCount = readCount(input, MAX_SYSTEMS, "controlledSystems");
            List<StarSystemId> controlledSystems = new ArrayList<>(systemCount);
            for (int systemIndex = 0; systemIndex < systemCount; systemIndex++) {
                controlledSystems.add(new StarSystemId(input.readLong()));
            }
            int stationTaxBasisPoints = input.readInt();
            int foreignTerritoryTariffBasisPoints = input.readInt();
            int stockCount = readCount(input, MAX_POLICIES_PER_FACTION, "stockPolicies");
            List<FactionStockPolicyState> stockPolicies = new ArrayList<>(stockCount);
            for (int policyIndex = 0; policyIndex < stockCount; policyIndex++) {
                stockPolicies.add(new FactionStockPolicyState(readString(input), input.readInt()));
            }
            int productionCount = readCount(input, MAX_POLICIES_PER_FACTION, "productionPolicies");
            List<FactionProductionPolicyState> productionPolicies = new ArrayList<>(productionCount);
            for (int policyIndex = 0; policyIndex < productionCount; policyIndex++) {
                productionPolicies.add(new FactionProductionPolicyState(readString(input), readString(input)));
            }
            int goalCount = readCount(input, MAX_GOALS_PER_FACTION, "strategicGoals");
            List<FactionStrategicGoalState> goals = new ArrayList<>(goalCount);
            for (int goalIndex = 0; goalIndex < goalCount; goalIndex++) {
                String goalId = readString(input);
                FactionStrategicGoalState.GoalType type;
                try {
                    type = FactionStrategicGoalState.GoalType.valueOf(readString(input));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Неизвестный strategic goal type", exception);
                }
                int demandCount = readCount(input, MAX_POLICIES_PER_FACTION, "goalDemandFloors");
                List<FactionStockPolicyState> demands = new ArrayList<>(demandCount);
                for (int demandIndex = 0; demandIndex < demandCount; demandIndex++) {
                    demands.add(new FactionStockPolicyState(readString(input), input.readInt()));
                }
                goals.add(new FactionStrategicGoalState(goalId, type, List.copyOf(demands)));
            }
            strategies.add(new FactionStrategicState(
                    factionId,
                    threshold,
                    List.copyOf(relations),
                    List.copyOf(controlledSystems),
                    stationTaxBasisPoints,
                    foreignTerritoryTariffBasisPoints,
                    List.copyOf(stockPolicies),
                    List.copyOf(productionPolicies),
                    List.copyOf(goals)));
        }
        return List.copyOf(strategies);
    }

    private static void writeTopology(DataOutputStream output, GalaxyTopology topology) throws IOException {
        GalaxyTopology value = Objects.requireNonNull(topology, "GalaxyTopology не задан");
        output.writeLong(value.id().value());
        writeString(output, value.name());
        writeCount(output, value.sectors().size(), MAX_SECTORS, "sectors");
        int totalSystems = 0;
        int totalPlanets = 0;
        int totalFields = 0;
        for (SectorNode sector : value.sectors()) {
            output.writeLong(sector.id().value());
            writeString(output, sector.name());
            int systemCount = sector.systems().size();
            if (systemCount > MAX_SYSTEMS - totalSystems) {
                throw new IllegalArgumentException("Topology содержит слишком много StarSystem");
            }
            totalSystems += systemCount;
            writeCount(output, systemCount, MAX_SYSTEMS, "sectorSystems");
            for (StarSystemNode system : sector.systems()) {
                output.writeLong(system.id().value());
                writeString(output, system.name());
                output.writeDouble(system.x());
                output.writeDouble(system.y());
                int planetCount = system.planets().size();
                if (planetCount > MAX_PLANETS - totalPlanets) {
                    throw new IllegalArgumentException("Topology содержит слишком много PlanetNode");
                }
                totalPlanets += planetCount;
                writeCount(output, planetCount, MAX_PLANETS, "planets");
                for (PlanetNode planet : system.planets()) {
                    output.writeLong(planet.id().value());
                    writeString(output, planet.name());
                    output.writeDouble(planet.orbitRadius());
                }
                int fieldCount = system.asteroidFields().size();
                if (fieldCount > MAX_ASTEROID_FIELDS - totalFields) {
                    throw new IllegalArgumentException("Topology содержит слишком много AsteroidFieldNode");
                }
                totalFields += fieldCount;
                writeCount(output, fieldCount, MAX_ASTEROID_FIELDS, "asteroidFields");
                for (AsteroidFieldNode field : system.asteroidFields()) {
                    output.writeLong(field.id().value());
                    writeString(output, field.name());
                    output.writeDouble(field.x());
                    output.writeDouble(field.y());
                    output.writeDouble(field.radius());
                }
            }
        }
        writeCount(output, value.connections().size(), MAX_CONNECTIONS, "connections");
        for (JumpConnection connection : value.connections()) {
            output.writeLong(connection.first().value());
            output.writeLong(connection.second().value());
        }
    }

    private static GalaxyTopology readTopology(DataInputStream input) throws IOException {
        GalaxyId galaxyId = new GalaxyId(input.readLong());
        String galaxyName = readString(input);
        int sectorCount = readCount(input, MAX_SECTORS, "sectors");
        List<SectorNode> sectors = new ArrayList<>(sectorCount);
        int totalSystems = 0;
        int totalPlanets = 0;
        int totalFields = 0;
        for (int sectorIndex = 0; sectorIndex < sectorCount; sectorIndex++) {
            SectorId sectorId = new SectorId(input.readLong());
            String sectorName = readString(input);
            int systemCount = readCount(input, MAX_SYSTEMS, "sectorSystems");
            if (systemCount > MAX_SYSTEMS - totalSystems) {
                throw new IllegalArgumentException("Topology содержит слишком много StarSystem");
            }
            totalSystems += systemCount;
            List<StarSystemNode> systems = new ArrayList<>(systemCount);
            for (int systemIndex = 0; systemIndex < systemCount; systemIndex++) {
                StarSystemId systemId = new StarSystemId(input.readLong());
                String systemName = readString(input);
                double x = input.readDouble();
                double y = input.readDouble();
                int planetCount = readCount(input, MAX_PLANETS, "planets");
                if (planetCount > MAX_PLANETS - totalPlanets) {
                    throw new IllegalArgumentException("Topology содержит слишком много PlanetNode");
                }
                totalPlanets += planetCount;
                List<PlanetNode> planets = new ArrayList<>(planetCount);
                for (int planetIndex = 0; planetIndex < planetCount; planetIndex++) {
                    planets.add(new PlanetNode(
                            new PlanetId(input.readLong()), readString(input), input.readDouble()));
                }
                int fieldCount = readCount(input, MAX_ASTEROID_FIELDS, "asteroidFields");
                if (fieldCount > MAX_ASTEROID_FIELDS - totalFields) {
                    throw new IllegalArgumentException("Topology содержит слишком много AsteroidFieldNode");
                }
                totalFields += fieldCount;
                List<AsteroidFieldNode> fields = new ArrayList<>(fieldCount);
                for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                    fields.add(new AsteroidFieldNode(
                            new AsteroidFieldId(input.readLong()),
                            readString(input),
                            input.readDouble(),
                            input.readDouble(),
                            input.readDouble()));
                }
                systems.add(new StarSystemNode(
                        systemId, systemName, x, y, List.copyOf(planets), List.copyOf(fields)));
            }
            sectors.add(new SectorNode(sectorId, sectorName, List.copyOf(systems)));
        }
        int connectionCount = readCount(input, MAX_CONNECTIONS, "connections");
        List<JumpConnection> connections = new ArrayList<>(connectionCount);
        for (int index = 0; index < connectionCount; index++) {
            connections.add(new JumpConnection(
                    new StarSystemId(input.readLong()), new StarSystemId(input.readLong())));
        }
        return new GalaxyTopology(galaxyId, galaxyName, List.copyOf(sectors), List.copyOf(connections));
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "Persistent string не задан").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Persistent string превышает допустимый размер");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Некорректная длина persistent string");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Persistent string оборван");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeCount(DataOutputStream output, int count, int maximum, String label) throws IOException {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Некорректный размер " + label + ": " + count);
        }
        output.writeInt(count);
    }

    private static int readCount(DataInputStream input, int maximum, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Некорректный размер " + label + ": " + count);
        }
        return count;
    }
}
