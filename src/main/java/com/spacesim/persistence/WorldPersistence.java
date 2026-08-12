package com.spacesim.persistence;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import com.spacesim.world.WorldTopologyDefaults;

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
import java.util.Objects;

/**
 * Content-aware файловая граница Stage-7 {@link WorldSimulation}.
 *
 * <p>Новый world-save хранит semantic fingerprint общего content catalog, active StarSystem,
 * strategic cadence/budget и payload {@link WorldStateCodec}. Reader также принимает исторические
 * {@code STEC}/{@code STEM} single-session saves через {@link ContentBoundSaveCodec} и без изменения
 * экономического snapshot оборачивает их в {@link WorldState#singleSystem(GameState)}.</p>
 */
public final class WorldPersistence {
    private static final int MAGIC = 0x53545743; // STWC — Star Empires World + Content.
    private static final int FORMAT_VERSION = 1;
    private static final int FINGERPRINT_BYTES = 64;
    private static final int MAX_WORLD_PAYLOAD_BYTES = 256 * 1024 * 1024;

    private WorldPersistence() {
        throw new AssertionError("WorldPersistence не создаёт экземпляров");
    }

    /**
     * Атомарно сохраняет world runtime вместе с content fingerprint и scheduler конфигурацией.
     *
     * @param path целевой save-файл
     * @param world сохраняемый world runtime
     * @throws NullPointerException если обязательное значение не задано
     * @throws IOException если файл нельзя записать или заменить
     */
    public static void save(Path path, WorldSimulation world) throws IOException {
        WorldSimulation checked = Objects.requireNonNull(world, "WorldSimulation не задан");
        SimulationSession active = checked.findSession(checked.getActiveSystemId()).orElseThrow();
        byte[] bytes = encode(
                checked.snapshot(),
                checked.getActiveSystemId(),
                checked.getStrategicStepTicks(),
                checked.getRemoteUpdateBudgetPerFrame(),
                active.getContentCatalog().getFingerprint());
        atomicWrite(Objects.requireNonNull(path, "Путь world save не задан"), bytes);
    }

    /**
     * Загружает world save или legacy single-session save на встроенном production catalog.
     *
     * @param path существующий save-файл
     * @return новый независимый WorldSimulation
     * @throws IOException если файл нельзя прочитать
     * @throws IllegalArgumentException если fingerprint или формат несовместимы
     */
    public static WorldSimulation load(Path path) throws IOException {
        return load(path, ContentCatalogLoader.loadDefault());
    }

    /**
     * Загружает world/legacy save после semantic content compatibility check.
     *
     * @param path существующий save-файл
     * @param contentCatalog catalog, которым продолжатся все local sessions
     * @return новый независимый WorldSimulation
     * @throws IOException если файл нельзя прочитать
     * @throws IllegalArgumentException если fingerprint или бинарный формат несовместимы
     */
    public static WorldSimulation load(Path path, ContentCatalog contentCatalog) throws IOException {
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        Path source = Objects.requireNonNull(path, "Путь world save не задан").toAbsolutePath();
        long size = Files.size(source);
        long maximum = MAX_WORLD_PAYLOAD_BYTES + 256L;
        if (size <= 0L || size > maximum) {
            throw new IllegalArgumentException("Размер world save находится вне допустимого диапазона");
        }
        byte[] bytes = Files.readAllBytes(source);
        if (readLeadingInt(bytes) == MAGIC) {
            DecodedWorld decoded = decodeWorld(bytes);
            requireCompatibleFingerprint(decoded.contentFingerprint(), content);
            return WorldSimulation.restore(
                    decoded.state(),
                    content,
                    decoded.activeSystemId(),
                    decoded.strategicStepTicks(),
                    decoded.remoteUpdateBudgetPerFrame());
        }

        ContentBoundSaveCodec.DecodedSave legacy = ContentBoundSaveCodec.decode(bytes);
        requireCompatibleFingerprint(legacy.contentFingerprint(), content);
        return WorldSimulation.restore(
                WorldState.singleSystem(legacy.state()),
                content,
                WorldTopologyDefaults.DEFAULT_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    private static byte[] encode(
            WorldState state,
            StarSystemId activeSystemId,
            int strategicStepTicks,
            int remoteUpdateBudgetPerFrame,
            String contentFingerprint) {
        WorldState checked = Objects.requireNonNull(state, "WorldState не задан");
        StarSystemId active = Objects.requireNonNull(activeSystemId, "Active StarSystemId не задан");
        if (checked.topology().findSystem(active).isEmpty()) {
            throw new IllegalArgumentException("Active StarSystem отсутствует в WorldState topology");
        }
        if (strategicStepTicks <= 1 || remoteUpdateBudgetPerFrame <= 0) {
            throw new IllegalArgumentException("Scheduler конфигурация world save некорректна");
        }
        String fingerprint = requireFingerprint(contentFingerprint);
        byte[] payload = WorldStateCodec.encode(checked);
        if (payload.length <= 0 || payload.length > MAX_WORLD_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("WorldState payload превышает допустимый размер");
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(payload.length + 96);
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.write(fingerprint.getBytes(StandardCharsets.US_ASCII));
                output.writeLong(active.value());
                output.writeInt(strategicStepTicks);
                output.writeInt(remoteUpdateBudgetPerFrame);
                output.writeInt(payload.length);
                output.write(payload);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Неожиданная ошибка памяти при кодировании world save", exception);
        }
    }

    private static DecodedWorld decodeWorld(byte[] bytes) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Некорректный magic world save");
            }
            int version = input.readInt();
            if (version != FORMAT_VERSION) {
                throw new IllegalArgumentException("Неподдерживаемая версия world save: " + version);
            }
            byte[] fingerprintBytes = input.readNBytes(FINGERPRINT_BYTES);
            if (fingerprintBytes.length != FINGERPRINT_BYTES) {
                throw new EOFException("Content fingerprint world save оборван");
            }
            String fingerprint = requireFingerprint(
                    new String(fingerprintBytes, StandardCharsets.US_ASCII));
            StarSystemId activeSystemId = new StarSystemId(input.readLong());
            int strategicStepTicks = input.readInt();
            int remoteBudget = input.readInt();
            if (strategicStepTicks <= 1 || remoteBudget <= 0) {
                throw new IllegalArgumentException("Scheduler конфигурация world save повреждена");
            }
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAX_WORLD_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Некорректная длина WorldState payload");
            }
            byte[] payload = input.readNBytes(payloadLength);
            if (payload.length != payloadLength) {
                throw new EOFException("WorldState payload оборван");
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("После world save обнаружен лишний бинарный хвост");
            }
            WorldState state = WorldStateCodec.decode(payload);
            if (state.topology().findSystem(activeSystemId).isEmpty()) {
                throw new IllegalArgumentException("Active StarSystem world save отсутствует в topology");
            }
            return new DecodedWorld(
                    state,
                    activeSystemId,
                    strategicStepTicks,
                    remoteBudget,
                    fingerprint);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("World save оборван", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("World save невозможно декодировать", exception);
        }
    }

    private static void atomicWrite(Path path, byte[] bytes) throws IOException {
        Path target = path.toAbsolutePath();
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

    private static void requireCompatibleFingerprint(String expected, ContentCatalog actual) {
        if (!expected.equals(actual.getFingerprint())) {
            throw new IllegalArgumentException(
                    "Content catalog несовместим с world save: expected="
                            + expected + ", actual=" + actual.getFingerprint());
        }
    }

    private static String requireFingerprint(String value) {
        Objects.requireNonNull(value, "Content fingerprint не задан");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Content fingerprint должен быть lowercase SHA-256 hex");
        }
        return value;
    }

    private static int readLeadingInt(byte[] bytes) {
        if (bytes.length < Integer.BYTES) {
            throw new IllegalArgumentException("Save-файл слишком короткий");
        }
        return ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
    }

    private record DecodedWorld(
            WorldState state,
            StarSystemId activeSystemId,
            int strategicStepTicks,
            int remoteUpdateBudgetPerFrame,
            String contentFingerprint) {
    }
}
