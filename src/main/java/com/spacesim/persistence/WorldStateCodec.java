package com.spacesim.persistence;

import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
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
 * Детерминированный бинарный codec Stage-7 {@link WorldState}.
 *
 * <p>World codec не сериализует экономические компоненты повторно. Каждый system payload кодируется
 * существующим {@link GameStateCodec}, поэтому local save schema, migration и invariant checks
 * остаются единым источником истины. World-layer добавляет только canonical Galaxy topology и
 * отображение StarSystem ID -> local GameState.</p>
 */
public final class WorldStateCodec {
    private static final int MAGIC = 0x53544757; // STGW — Star Empires Galaxy World.
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_SAVE_BYTES = 256 * 1024 * 1024;
    private static final int MAX_GAMESTATE_PAYLOAD_BYTES = 32 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final int MAX_SECTORS = 10_000;
    private static final int MAX_SYSTEMS = 100_000;
    private static final int MAX_CONNECTIONS = 500_000;

    private WorldStateCodec() {
        throw new AssertionError("WorldStateCodec не создаёт экземпляров");
    }

    /**
     * Кодирует полный world snapshot в deterministic binary representation.
     *
     * @param state валидный WorldState текущей schema
     * @return новый массив байтов
     * @throws NullPointerException если state не задан
     * @throws IllegalArgumentException если версия неизвестна или общий размер превышает лимит
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
                writeTopology(output, checked.topology());
                writeCount(output, checked.systems().size(), MAX_SYSTEMS, "systemStates");
                for (StarSystemSimulationState systemState : checked.systems()) {
                    output.writeLong(systemState.systemId().value());
                    byte[] payload = GameStateCodec.encode(systemState.simulationState());
                    if (payload.length <= 0 || payload.length > MAX_GAMESTATE_PAYLOAD_BYTES) {
                        throw new IllegalArgumentException(
                                "GameState payload системы превышает допустимый размер");
                    }
                    output.writeInt(payload.length);
                    output.write(payload);
                }
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length > MAX_SAVE_BYTES) {
                throw new IllegalArgumentException("World save превышает допустимый размер");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Неожиданная ошибка памяти при кодировании WorldState",
                    exception);
        }
    }

    /**
     * Декодирует и полностью валидирует world snapshot.
     *
     * @param bytes бинарный world save
     * @return новый immutable WorldState
     * @throws NullPointerException если bytes не заданы
     * @throws IllegalArgumentException если формат повреждён, неизвестен или превышает лимиты
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
                throw new IllegalArgumentException(
                        "Неподдерживаемая версия world-файла: " + fileVersion);
            }
            int schemaVersion = input.readInt();
            if (schemaVersion != WorldState.CURRENT_VERSION) {
                throw new IllegalArgumentException(
                        "Неподдерживаемая WorldState schema: " + schemaVersion);
            }

            GalaxyTopology topology = readTopology(input);
            int systemCount = readCount(input, MAX_SYSTEMS, "systemStates");
            List<StarSystemSimulationState> systemStates = new ArrayList<>(systemCount);
            for (int index = 0; index < systemCount; index++) {
                StarSystemId systemId = new StarSystemId(input.readLong());
                int payloadLength = input.readInt();
                if (payloadLength <= 0 || payloadLength > MAX_GAMESTATE_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException(
                            "Некорректная длина GameState payload системы");
                }
                byte[] payload = input.readNBytes(payloadLength);
                if (payload.length != payloadLength) {
                    throw new EOFException("GameState payload системы оборван");
                }
                systemStates.add(new StarSystemSimulationState(
                        systemId,
                        GameStateCodec.decode(payload)));
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException(
                        "После WorldState обнаружен лишний бинарный хвост");
            }
            return new WorldState(
                    schemaVersion,
                    topology,
                    List.copyOf(systemStates));
        } catch (EOFException exception) {
            throw new IllegalArgumentException("WorldState оборван", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("WorldState невозможно декодировать", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException(
                    "WorldState содержит повреждённые значения",
                    exception);
        }
    }

    /**
     * Атомарно записывает world snapshot рядом с целевым файлом.
     *
     * @param path целевой файл
     * @param state сохраняемый WorldState
     * @throws NullPointerException если path или state не заданы
     * @throws IOException если файл нельзя записать или заменить
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
     * @return decoded immutable WorldState
     * @throws NullPointerException если path не задан
     * @throws IOException если файл нельзя прочитать
     * @throws IllegalArgumentException если размер или бинарный формат некорректны
     */
    public static WorldState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "Путь WorldState не задан").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Размер WorldState находится вне допустимого диапазона");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writeTopology(
            DataOutputStream output,
            GalaxyTopology topology) throws IOException {
        GalaxyTopology value = Objects.requireNonNull(topology, "GalaxyTopology не задан");
        output.writeLong(value.id().value());
        writeString(output, value.name());
        writeCount(output, value.sectors().size(), MAX_SECTORS, "sectors");
        int totalSystems = 0;
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
                systems.add(new StarSystemNode(
                        new StarSystemId(input.readLong()),
                        readString(input),
                        input.readDouble(),
                        input.readDouble()));
            }
            sectors.add(new SectorNode(sectorId, sectorName, List.copyOf(systems)));
        }
        int connectionCount = readCount(input, MAX_CONNECTIONS, "connections");
        List<JumpConnection> connections = new ArrayList<>(connectionCount);
        for (int index = 0; index < connectionCount; index++) {
            connections.add(new JumpConnection(
                    new StarSystemId(input.readLong()),
                    new StarSystemId(input.readLong())));
        }
        return new GalaxyTopology(
                galaxyId,
                galaxyName,
                List.copyOf(sectors),
                List.copyOf(connections));
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "Persistent string не задан")
                .getBytes(StandardCharsets.UTF_8);
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

    private static void writeCount(
            DataOutputStream output,
            int count,
            int maximum,
            String label) throws IOException {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Некорректный размер " + label + ": " + count);
        }
        output.writeInt(count);
    }

    private static int readCount(
            DataInputStream input,
            int maximum,
            String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Некорректный размер " + label + ": " + count);
        }
        return count;
    }
}
