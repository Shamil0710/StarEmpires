package com.spacesim.persistence;

import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.FactionEconomicPressureState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.WorldState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * Детерминированный бинарный codec {@link WorldState}.
 *
 * <p>World schema v1 хранит topology + local sessions, v2 добавляет faction treasury, v3 strategic
 * state, v4 construction projects, v5 economic pressure/hysteresis, v6 world-level fleet
 * placement/transit state, v7 active jump FSM, Stage-16 schema v8 разделяет construction
 * settlement и legal/faction identity, а Stage-17 schema v9 добавляет persistent world-defined
 * faction identity directory. Save v4-v7 продолжает читать старый faction-only construction
 * layout, v8 читает Stage-16 external-owner layout и мигрирует с пустым dynamic directory.
 * File format v2 добавляет bounded Stage-11 strategic-growth trailer, file format v3 —
 * Stage-17D territorial claims/control maintenance/recognition/concession trailer, v4 —
 * Stage-17E institutional diplomacy, v5 — отдельную transaction/customs tariff policy, v6 —
 * Stage-17F persistent institutional doctrine profiles, v7 — treasury reserve floor и
 * construction-investment authorization cap, а v8 — общий Stage-17F.6 policy-review watermark.
 * v1-v3 детерминированно мигрируют в neutral explicit diplomacy без выдуманных treaties, grievances
 * или embargoes; v1-v4 получают нулевой customs tariff, v1-v5 — neutral doctrine с midpoint 50 по
 * каждой оси, v1-v6 сохраняют прежнее fiscal поведение: treasury reserve 0 и отсутствие дополнительного
 * construction cap, а v1-v7 получают never-reviewed policy lifecycle. Local entity payload
 * кодируется {@link GameStateCodec}.</p>
 */
public final class WorldStateCodec {
    private static final int MAGIC = 0x53544757;
    private static final int LEGACY_FILE_FORMAT_VERSION = 1;
    private static final int GROWTH_FILE_FORMAT_VERSION = 2;
    private static final int TERRITORY_FILE_FORMAT_VERSION = 3;
    private static final int DIPLOMACY_FILE_FORMAT_VERSION = 4;
    private static final int CUSTOMS_FILE_FORMAT_VERSION = 5;
    private static final int DOCTRINE_FILE_FORMAT_VERSION = 6;
    private static final int FISCAL_FILE_FORMAT_VERSION = 7;
    private static final int FILE_FORMAT_VERSION = 8;
    private static final int MAX_SAVE_BYTES = 256 * 1024 * 1024;

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
            throw new IllegalArgumentException(
                    "Нельзя записать WorldState schema: " + checked.schemaVersion());
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                output.writeInt(checked.schemaVersion());

                WorldTopologyBinary.write(output, checked.topology());
                WorldSystemBinary.write(output, checked.systems());
                WorldFactionBinary.writeEconomic(output, checked.factions());
                WorldFactionBinary.writeStrategies(output, checked.factionStrategies());
                output.writeLong(checked.nextConstructionProjectIdValue());
                WorldConstructionBinary.write(output, checked.constructionProjects());
                WorldFactionBinary.writePressures(output, checked.factionEconomicPressures());
                output.writeLong(checked.nextFleetIdValue());
                WorldFleetBinary.write(output, checked.fleets());
                WorldFleetBinary.writeJumps(output, checked.fleetJumps());
                WorldFactionIdentityBinary.write(output, checked.factionIdentities());
                WorldStrategicGrowthBinary.write(output, checked.factionStrategies());
                WorldTerritoryBinary.write(output, checked.factionStrategies());
                WorldDiplomacyBinary.write(output, checked.factionDiplomacyStates());
                WorldCustomsBinary.write(output, checked.factionDiplomacyStates());
                WorldDoctrineBinary.write(output, checked.factionStrategies());
                WorldFiscalPolicyBinary.write(output, checked.factions());
                WorldPolicyReviewBinary.write(output, checked.factions());
            }

            byte[] bytes = buffer.toByteArray();
            if (bytes.length > MAX_SAVE_BYTES) {
                throw new IllegalArgumentException("World save превышает допустимый размер");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Неожиданная ошибка памяти при кодировании WorldState", exception);
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
            throw new IllegalArgumentException(
                    "Размер WorldState находится вне допустимого диапазона");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Некорректный magic WorldState");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION
                    && fileVersion != FISCAL_FILE_FORMAT_VERSION
                    && fileVersion != DOCTRINE_FILE_FORMAT_VERSION
                    && fileVersion != CUSTOMS_FILE_FORMAT_VERSION
                    && fileVersion != DIPLOMACY_FILE_FORMAT_VERSION
                    && fileVersion != TERRITORY_FILE_FORMAT_VERSION
                    && fileVersion != GROWTH_FILE_FORMAT_VERSION
                    && fileVersion != LEGACY_FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        "Неподдерживаемая версия world-файла: " + fileVersion);
            }

            int schemaVersion = input.readInt();
            requireSupportedSchema(schemaVersion);

            GalaxyTopology topology = WorldTopologyBinary.read(input);
            List<StarSystemSimulationState> systems = WorldSystemBinary.read(input);
            WorldState state = readSchema(input, schemaVersion, topology, systems);
            if (fileVersion >= GROWTH_FILE_FORMAT_VERSION) {
                List<FactionStrategicState> strategies =
                        WorldStrategicGrowthBinary.readAndAttach(input, state.factionStrategies());
                state = withStrategies(state, strategies);
            }
            if (fileVersion >= TERRITORY_FILE_FORMAT_VERSION) {
                List<FactionStrategicState> strategies =
                        WorldTerritoryBinary.readAndAttach(input, state.factionStrategies());
                state = withStrategies(state, strategies);
            }
            if (fileVersion >= DIPLOMACY_FILE_FORMAT_VERSION) {
                state = withDiplomacy(state, WorldDiplomacyBinary.read(input));
            }
            if (fileVersion >= CUSTOMS_FILE_FORMAT_VERSION) {
                state = withDiplomacy(
                        state,
                        WorldCustomsBinary.readAndAttach(input, state.factionDiplomacyStates()));
            }
            if (fileVersion >= DOCTRINE_FILE_FORMAT_VERSION) {
                state = withStrategies(
                        state,
                        WorldDoctrineBinary.readAndAttach(input, state.factionStrategies()));
            }
            if (fileVersion >= FISCAL_FILE_FORMAT_VERSION) {
                state = withFactions(
                        state,
                        WorldFiscalPolicyBinary.readAndAttach(input, state.factions()));
            }
            if (fileVersion >= FILE_FORMAT_VERSION) {
                state = withFactions(
                        state,
                        WorldPolicyReviewBinary.readAndAttach(input, state.factions()));
            }

            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "После WorldState обнаружен лишний бинарный хвост");
            }
            return state;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("WorldState оборван", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "WorldState невозможно декодировать", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException(
                    "WorldState содержит повреждённые значения", exception);
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
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
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
            throw new IllegalArgumentException(
                    "Размер WorldState находится вне допустимого диапазона");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void requireSupportedSchema(int schemaVersion) {
        if (schemaVersion != WorldState.CURRENT_VERSION
                && schemaVersion != WorldState.LEGACY_STAGE16_VERSION
                && schemaVersion != WorldState.LEGACY_STAGE10_JUMP_VERSION
                && schemaVersion != WorldState.LEGACY_STAGE10A_VERSION
                && schemaVersion != WorldState.LEGACY_STAGE9_PRESSURE_VERSION
                && schemaVersion != WorldState.LEGACY_STAGE9_CONSTRUCTION_VERSION
                && schemaVersion != WorldState.LEGACY_STAGE8_VERSION
                && schemaVersion != WorldState.LEGACY_FACTION_TREASURY_VERSION
                && schemaVersion != WorldState.LEGACY_STAGE7_VERSION) {
            throw new IllegalArgumentException(
                    "Неподдерживаемая WorldState schema: " + schemaVersion);
        }
    }

    private static WorldState readSchema(
            DataInputStream input,
            int schemaVersion,
            GalaxyTopology topology,
            List<StarSystemSimulationState> systems) throws IOException {
        if (schemaVersion == WorldState.LEGACY_STAGE7_VERSION) {
            return WorldState.fromLegacyStage7(topology, systems);
        }

        List<FactionEconomicState> factions = WorldFactionBinary.readEconomic(input);
        if (schemaVersion == WorldState.LEGACY_FACTION_TREASURY_VERSION) {
            return WorldState.fromLegacyFactionTreasury(topology, systems, factions);
        }

        List<FactionStrategicState> strategies = WorldFactionBinary.readStrategies(input);
        if (schemaVersion == WorldState.LEGACY_STAGE8_VERSION) {
            return WorldState.fromLegacyStage8(topology, systems, factions, strategies);
        }

        long nextProjectId = input.readLong();
        List<ConstructionProjectState> projects = schemaVersion >= WorldState.LEGACY_STAGE16_VERSION
                ? WorldConstructionBinary.read(input)
                : WorldConstructionBinary.readLegacy(input);
        if (schemaVersion == WorldState.LEGACY_STAGE9_CONSTRUCTION_VERSION) {
            return WorldState.fromLegacyStage9Construction(
                    topology, systems, factions, strategies, nextProjectId, projects);
        }

        List<FactionEconomicPressureState> pressures = WorldFactionBinary.readPressures(input);
        if (schemaVersion == WorldState.LEGACY_STAGE9_PRESSURE_VERSION) {
            return WorldState.fromLegacyStage9Pressure(
                    topology,
                    systems,
                    factions,
                    strategies,
                    nextProjectId,
                    projects,
                    pressures);
        }

        long nextFleetId = input.readLong();
        var fleets = WorldFleetBinary.read(input);
        if (schemaVersion == WorldState.LEGACY_STAGE10A_VERSION) {
            return WorldState.fromLegacyStage10A(
                    topology,
                    systems,
                    factions,
                    strategies,
                    nextProjectId,
                    projects,
                    pressures,
                    nextFleetId,
                    fleets);
        }

        var jumps = WorldFleetBinary.readJumps(input);
        if (schemaVersion == WorldState.LEGACY_STAGE10_JUMP_VERSION) {
            return WorldState.fromLegacyStage10Jump(
                    topology,
                    systems,
                    factions,
                    strategies,
                    nextProjectId,
                    projects,
                    pressures,
                    nextFleetId,
                    fleets,
                    jumps);
        }
        if (schemaVersion == WorldState.LEGACY_STAGE16_VERSION) {
            return WorldState.fromLegacyStage16(
                    topology,
                    systems,
                    factions,
                    strategies,
                    nextProjectId,
                    projects,
                    pressures,
                    nextFleetId,
                    fleets,
                    jumps);
        }

        List<WorldFactionIdentityState> identities = WorldFactionIdentityBinary.read(input);
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                systems,
                factions,
                strategies,
                nextProjectId,
                projects,
                pressures,
                nextFleetId,
                fleets,
                jumps,
                identities);
    }

    private static WorldState withFactions(
            WorldState state,
            List<FactionEconomicState> factions) {
        return new WorldState(
                state.schemaVersion(),
                state.topology(),
                state.systems(),
                factions,
                state.factionStrategies(),
                state.nextConstructionProjectIdValue(),
                state.constructionProjects(),
                state.factionEconomicPressures(),
                state.nextFleetIdValue(),
                state.fleets(),
                state.fleetJumps(),
                state.factionIdentities(),
                state.factionDiplomacyStates());
    }

    private static WorldState withStrategies(
            WorldState state,
            List<FactionStrategicState> strategies) {
        return new WorldState(
                state.schemaVersion(),
                state.topology(),
                state.systems(),
                state.factions(),
                strategies,
                state.nextConstructionProjectIdValue(),
                state.constructionProjects(),
                state.factionEconomicPressures(),
                state.nextFleetIdValue(),
                state.fleets(),
                state.fleetJumps(),
                state.factionIdentities(),
                state.factionDiplomacyStates());
    }

    private static WorldState withDiplomacy(
            WorldState state,
            List<FactionDiplomacyState> diplomacyStates) {
        return new WorldState(
                state.schemaVersion(),
                state.topology(),
                state.systems(),
                state.factions(),
                state.factionStrategies(),
                state.nextConstructionProjectIdValue(),
                state.constructionProjects(),
                state.factionEconomicPressures(),
                state.nextFleetIdValue(),
                state.fleets(),
                state.fleetJumps(),
                state.factionIdentities(),
                diplomacyStates);
    }
}
