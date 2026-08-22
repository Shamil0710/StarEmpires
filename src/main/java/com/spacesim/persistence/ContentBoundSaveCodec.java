package com.spacesim.persistence;

import com.spacesim.content.ContentCatalogLoader;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Файловый envelope поверх {@link GameStateCodec}, связывающий save с semantic content catalog.
 *
 * <p>{@link GameStateCodec} остаётся backward-compatible core binary payload. Envelope v2 добавляет
 * deterministic Stage-17.5H extension для local damage/shield/maintenance/weapon continuity and
 * system-local sensor knowledge. Это не второй источник истины: extension сериализует поля тех же
 * {@link EntityState}, которые ECS mapper захватывает в authoritative snapshot, а при decode они
 * присоединяются обратно до materialization. V1/raw saves получают только neutral missing-H state —
 * без бесплатного shield reserve, ammunition identity, repair или sensor knowledge.</p>
 */
public final class ContentBoundSaveCodec {
    private static final int ENVELOPE_MAGIC = 0x53544543; // STEC
    private static final int LEGACY_GAMESTATE_MAGIC = 0x5354454D; // STEM
    private static final int LEGACY_ENVELOPE_VERSION = 1;
    private static final int ENVELOPE_VERSION = 2;
    private static final int MAX_PAYLOAD_BYTES = 32 * 1024 * 1024;
    private static final int FINGERPRINT_BYTES = 64;
    private static final int MAX_EXTENSION_ENTITIES = 100_000;
    private static final int MAX_EXTENSION_ROWS = 100_000;
    private static final int MAX_STRING_BYTES = 1024 * 1024;

    private ContentBoundSaveCodec() {
        throw new AssertionError("ContentBoundSaveCodec не создаёт экземпляров");
    }

    /**
     * Кодирует current GameState, H continuity extension и semantic fingerprint.
     *
     * @param state authoritative snapshot текущей core schema
     * @param contentFingerprint lowercase SHA-256 hex fingerprint
     * @return новый бинарный envelope v2
     */
    public static byte[] encode(GameState state, String contentFingerprint) {
        GameState checked = Objects.requireNonNull(state, "GameState не задан");
        String fingerprint = requireFingerprint(contentFingerprint);
        byte[] payload = GameStateCodec.encode(checked);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("GameState payload превышает допустимый размер");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(payload.length + 256);
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(ENVELOPE_MAGIC);
                output.writeInt(ENVELOPE_VERSION);
                output.write(fingerprint.getBytes(StandardCharsets.US_ASCII));
                output.writeInt(payload.length);
                output.write(payload);
                writeStage175HExtension(output, checked);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Неожиданная ошибка памяти при кодировании save-envelope", exception);
        }
    }

    /**
     * Декодирует content-bound envelope либо исторический raw save.
     *
     * @param bytes бинарное содержимое файла
     * @return decoded state и ожидаемый fingerprint каталога
     */
    public static DecodedSave decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Байты сохранения не заданы");
        if (bytes.length < Integer.BYTES) {
            throw new IllegalArgumentException("Файл сохранения слишком короткий");
        }
        int magic = readLeadingInt(bytes);
        if (magic == LEGACY_GAMESTATE_MAGIC) {
            return new DecodedSave(
                    GameStateCodec.decode(bytes),
                    ContentCatalogLoader.loadDefault().getFingerprint(),
                    true);
        }
        if (magic != ENVELOPE_MAGIC) {
            throw new IllegalArgumentException("Неизвестный magic save-envelope");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            input.readInt();
            int version = input.readInt();
            if (version != LEGACY_ENVELOPE_VERSION && version != ENVELOPE_VERSION) {
                throw new IllegalArgumentException("Неподдерживаемая версия save-envelope: " + version);
            }
            byte[] fingerprintBytes = input.readNBytes(FINGERPRINT_BYTES);
            if (fingerprintBytes.length != FINGERPRINT_BYTES) {
                throw new EOFException("Content fingerprint оборван");
            }
            String fingerprint = requireFingerprint(new String(fingerprintBytes, StandardCharsets.US_ASCII));
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Некорректная длина GameState payload");
            }
            byte[] payload = input.readNBytes(payloadLength);
            if (payload.length != payloadLength) {
                throw new EOFException("GameState payload оборван");
            }
            GameState state = GameStateCodec.decode(payload);
            if (version >= ENVELOPE_VERSION) {
                state = readAndApplyStage175HExtension(input, state);
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("После save-envelope обнаружен лишний бинарный хвост");
            }
            return new DecodedSave(state, fingerprint, false);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Save-envelope оборван", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Save-envelope невозможно декодировать", exception);
        }
    }

    /**
     * Атомарно записывает content-bound save рядом с целевым файлом.
     *
     * @param path целевой файл
     * @param state authoritative snapshot
     * @param contentFingerprint semantic fingerprint каталога
     * @throws IOException если файловая система не позволила записать/заменить файл
     */
    public static void write(Path path, GameState state, String contentFingerprint) throws IOException {
        Path target = Objects.requireNonNull(path, "Путь сохранения не задан").toAbsolutePath();
        byte[] bytes = encode(state, contentFingerprint);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "save-" + prefix;
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
     * Читает save-envelope или legacy raw save с диска.
     *
     * @param path существующий файл
     * @return decoded content-bound state
     * @throws IOException если файл нельзя прочитать
     */
    public static DecodedSave read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "Путь сохранения не задан").toAbsolutePath();
        long size = Files.size(source);
        long maxEnvelopeBytes = MAX_PAYLOAD_BYTES + MAX_PAYLOAD_BYTES + 256L;
        if (size <= 0L || size > maxEnvelopeBytes) {
            throw new IllegalArgumentException("Размер save-envelope находится вне допустимого диапазона");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writeStage175HExtension(DataOutputStream output, GameState state) throws IOException {
        List<EntityState> rows = state.entities().stream()
                .filter(entity -> entity.sensorKnowledge() != null
                        || (entity.engineering() != null && entity.engineering().instanceState() != null))
                .sorted(Comparator.comparing(EntityState::id))
                .toList();
        writeCount(output, rows.size(), MAX_EXTENSION_ENTITIES, "H extension entities");
        for (EntityState entity : rows) {
            output.writeLong(entity.id().value());
            EntityState.ShipInstanceState instance = entity.engineering() == null
                    ? null : entity.engineering().instanceState();
            output.writeBoolean(instance != null);
            if (instance != null) {
                writeShipInstance(output, instance);
            }
            output.writeBoolean(entity.sensorKnowledge() != null);
            if (entity.sensorKnowledge() != null) {
                writeSensorKnowledge(output, entity.sensorKnowledge());
            }
        }
    }

    private static GameState readAndApplyStage175HExtension(DataInputStream input, GameState state) throws IOException {
        int count = readCount(input, MAX_EXTENSION_ENTITIES, "H extension entities");
        Map<EntityId, ExtensionRow> extension = new HashMap<>();
        for (int index = 0; index < count; index++) {
            EntityId id = new EntityId(input.readLong());
            EntityState.ShipInstanceState instance = input.readBoolean() ? readShipInstance(input) : null;
            EntityState.SensorKnowledgeState knowledge = input.readBoolean() ? readSensorKnowledge(input) : null;
            if (extension.putIfAbsent(id, new ExtensionRow(instance, knowledge)) != null) {
                throw new IllegalArgumentException("Duplicate Stage-17.5H extension entity: " + id);
            }
        }
        List<EntityState> entities = new ArrayList<>(state.entities().size());
        for (EntityState entity : state.entities()) {
            ExtensionRow row = extension.remove(entity.id());
            if (row == null) {
                entities.add(entity);
                continue;
            }
            EntityState.EngineeringState engineering = entity.engineering();
            if (row.instance() != null) {
                if (engineering == null) {
                    throw new IllegalArgumentException("H ship-instance extension has no core engineering state: " + entity.id());
                }
                engineering = new EntityState.EngineeringState(
                        engineering.hullId(), engineering.installedModules(), engineering.consumables(),
                        engineering.sharedBusEnergyJ(), engineering.shipHeatStoredJ(),
                        engineering.localHeatJByMount(), engineering.thrustLimitNByMount(),
                        engineering.coolantBusCapacityW(), engineering.ftlCooldownSecondsByMount(),
                        row.instance());
            }
            entities.add(new EntityState(
                    entity.id(), entity.identity(), entity.transform(), entity.inventory(), entity.wallet(),
                    entity.market(), entity.production(), entity.priceHistory(), entity.faction(), entity.reputation(),
                    entity.ship(), entity.tradeAi(), entity.mining(), entity.combat(), entity.asteroid(),
                    entity.archetype(), engineering, row.sensorKnowledge()));
        }
        if (!extension.isEmpty()) {
            throw new IllegalArgumentException("H extension references entities absent from core GameState");
        }
        return new GameState(
                state.schemaVersion(), state.rootSeed(), state.clock(), state.nextEntityIdValue(),
                state.eventRandomState(), state.asteroidRandomState(), state.events(), state.asteroidSpawner(),
                state.priceRecorder(), state.ledger(), List.copyOf(entities));
    }

    static void writeShipInstance(DataOutputStream output, EntityState.ShipInstanceState state) throws IOException {
        writeMountRows(output, state.compartmentIntegrityById(), "compartment integrity");
        writeMountRows(output, state.moduleIntegrityByMount(), "module integrity");
        List<EntityState.ShieldRuntimeState> shields = requireList(state.shieldsByMount(), "shields");
        writeCount(output, shields.size(), MAX_EXTENSION_ROWS, "shields");
        for (EntityState.ShieldRuntimeState shield : shields) {
            writeString(output, shield.mountId());
            output.writeDouble(shield.reserveJ());
            output.writeDouble(shield.accumulatedHeatJ());
            output.writeBoolean(shield.collapsed());
            output.writeDouble(shield.restartRemainingSeconds());
            output.writeDouble(shield.emitterIntegrity());
        }
        writeMountRows(output, state.serviceAgeByMount(), "service age");
        List<EntityState.WeaponFeedState> feeds = requireList(state.weaponFeeds(), "weapon feeds");
        writeCount(output, feeds.size(), MAX_EXTENSION_ROWS, "weapon feeds");
        for (EntityState.WeaponFeedState feed : feeds) {
            writeString(output, feed.mountId());
            writeString(output, feed.interfaceId());
            writeString(output, feed.ammunitionContentId());
        }
        writeMountRows(output, state.weaponCooldownByMount(), "weapon cooldowns");
    }

    static EntityState.ShipInstanceState readShipInstance(DataInputStream input) throws IOException {
        List<EntityState.MountDoubleState> compartments = readMountRows(input, "compartment integrity");
        List<EntityState.MountDoubleState> modules = readMountRows(input, "module integrity");
        int shieldCount = readCount(input, MAX_EXTENSION_ROWS, "shields");
        List<EntityState.ShieldRuntimeState> shields = new ArrayList<>(shieldCount);
        for (int index = 0; index < shieldCount; index++) {
            shields.add(new EntityState.ShieldRuntimeState(
                    readString(input), input.readDouble(), input.readDouble(), input.readBoolean(),
                    input.readDouble(), input.readDouble()));
        }
        List<EntityState.MountDoubleState> serviceAge = readMountRows(input, "service age");
        int feedCount = readCount(input, MAX_EXTENSION_ROWS, "weapon feeds");
        List<EntityState.WeaponFeedState> feeds = new ArrayList<>(feedCount);
        for (int index = 0; index < feedCount; index++) {
            feeds.add(new EntityState.WeaponFeedState(readString(input), readString(input), readString(input)));
        }
        List<EntityState.MountDoubleState> cooldowns = readMountRows(input, "weapon cooldowns");
        return new EntityState.ShipInstanceState(
                compartments, modules, List.copyOf(shields), serviceAge, List.copyOf(feeds), cooldowns);
    }

    private static void writeSensorKnowledge(
            DataOutputStream output, EntityState.SensorKnowledgeState state) throws IOException {
        List<EntityState.SensorTrackState> tracks = requireList(state.tracks(), "sensor tracks");
        writeCount(output, tracks.size(), MAX_EXTENSION_ROWS, "sensor tracks");
        for (EntityState.SensorTrackState track : tracks) {
            output.writeLong(track.targetId());
            writeString(output, track.informationStateName());
            output.writeBoolean(track.positionKnown());
            output.writeDouble(track.estimatedXM());
            output.writeDouble(track.estimatedYM());
            writeNullableDouble(output, track.positionVarianceM2());
            output.writeDouble(track.bearingVarianceRad2());
            writeNullableDouble(output, track.rangeVarianceM2());
            output.writeDouble(track.classificationConfidence());
            output.writeDouble(track.lastMeasurementSeconds());
            output.writeInt(track.contributingObservers());
            output.writeInt(track.fusedMeasurementCount());
        }
        List<EntityState.SensorMeasurementState> received = requireList(
                state.receivedMeasurements(), "received measurements");
        writeCount(output, received.size(), MAX_EXTENSION_ROWS, "received measurements");
        for (EntityState.SensorMeasurementState measurement : received) {
            writeSensorMeasurement(output, measurement);
        }
        List<EntityState.PendingSensorMeasurementState> pending = requireList(
                state.pendingMeasurements(), "pending measurements");
        writeCount(output, pending.size(), MAX_EXTENSION_ROWS, "pending measurements");
        for (EntityState.PendingSensorMeasurementState row : pending) {
            writeSensorMeasurement(output, Objects.requireNonNull(row.measurement(), "pending measurement"));
            output.writeDouble(row.deliverAtSeconds());
        }
    }

    private static EntityState.SensorKnowledgeState readSensorKnowledge(DataInputStream input) throws IOException {
        int trackCount = readCount(input, MAX_EXTENSION_ROWS, "sensor tracks");
        List<EntityState.SensorTrackState> tracks = new ArrayList<>(trackCount);
        for (int index = 0; index < trackCount; index++) {
            tracks.add(new EntityState.SensorTrackState(
                    input.readLong(), readString(input), input.readBoolean(),
                    input.readDouble(), input.readDouble(), readNullableDouble(input),
                    input.readDouble(), readNullableDouble(input), input.readDouble(), input.readDouble(),
                    input.readInt(), input.readInt()));
        }
        int receivedCount = readCount(input, MAX_EXTENSION_ROWS, "received measurements");
        List<EntityState.SensorMeasurementState> received = new ArrayList<>(receivedCount);
        for (int index = 0; index < receivedCount; index++) {
            received.add(readSensorMeasurement(input));
        }
        int pendingCount = readCount(input, MAX_EXTENSION_ROWS, "pending measurements");
        List<EntityState.PendingSensorMeasurementState> pending = new ArrayList<>(pendingCount);
        for (int index = 0; index < pendingCount; index++) {
            pending.add(new EntityState.PendingSensorMeasurementState(
                    readSensorMeasurement(input), input.readDouble()));
        }
        return new EntityState.SensorKnowledgeState(
                List.copyOf(tracks), List.copyOf(received), List.copyOf(pending));
    }

    private static void writeSensorMeasurement(
            DataOutputStream output, EntityState.SensorMeasurementState value) throws IOException {
        output.writeLong(value.observerId());
        output.writeLong(value.targetId());
        writeString(output, value.channelName());
        output.writeDouble(value.timestampSeconds());
        output.writeDouble(value.observerXM());
        output.writeDouble(value.observerYM());
        output.writeDouble(value.bearingRad());
        writeNullableDouble(output, value.rangeM());
        output.writeDouble(value.bearingVarianceRad2());
        writeNullableDouble(output, value.rangeVarianceM2());
        output.writeDouble(value.receivedSignalPowerW());
        output.writeDouble(value.effectiveInterferencePowerW());
        output.writeDouble(value.snr());
        writeString(output, value.evidenceStateName());
    }

    private static EntityState.SensorMeasurementState readSensorMeasurement(DataInputStream input) throws IOException {
        return new EntityState.SensorMeasurementState(
                input.readLong(), input.readLong(), readString(input), input.readDouble(),
                input.readDouble(), input.readDouble(), input.readDouble(), readNullableDouble(input),
                input.readDouble(), readNullableDouble(input), input.readDouble(), input.readDouble(),
                input.readDouble(), readString(input));
    }

    private static void writeMountRows(
            DataOutputStream output, List<EntityState.MountDoubleState> rows, String label) throws IOException {
        List<EntityState.MountDoubleState> checked = requireList(rows, label);
        writeCount(output, checked.size(), MAX_EXTENSION_ROWS, label);
        for (EntityState.MountDoubleState row : checked) {
            writeString(output, row.mountId());
            output.writeDouble(row.value());
        }
    }

    private static List<EntityState.MountDoubleState> readMountRows(
            DataInputStream input, String label) throws IOException {
        int count = readCount(input, MAX_EXTENSION_ROWS, label);
        List<EntityState.MountDoubleState> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rows.add(new EntityState.MountDoubleState(readString(input), input.readDouble()));
        }
        return List.copyOf(rows);
    }

    private static void writeCount(DataOutputStream output, int count, int max, String label) throws IOException {
        if (count < 0 || count > max) {
            throw new IllegalArgumentException(label + " count outside limit: " + count);
        }
        output.writeInt(count);
    }

    private static int readCount(DataInputStream input, int max, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > max) {
            throw new IllegalArgumentException(label + " count outside limit: " + count);
        }
        return count;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        String checked = Objects.requireNonNull(value, "extension string");
        byte[] bytes = checked.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("extension string exceeds size limit");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("invalid extension string length");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("extension string truncated");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeNullableDouble(DataOutputStream output, Double value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeDouble(value);
        }
    }

    private static Double readNullableDouble(DataInputStream input) throws IOException {
        return input.readBoolean() ? input.readDouble() : null;
    }

    private static <T> List<T> requireList(List<T> values, String label) {
        Objects.requireNonNull(values, label);
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " contains null");
        }
        return values;
    }

    private static int readLeadingInt(byte[] bytes) {
        return ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
    }

    private static String requireFingerprint(String value) {
        Objects.requireNonNull(value, "Content fingerprint не задан");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Content fingerprint должен быть lowercase SHA-256 hex");
        }
        return value;
    }

    private record ExtensionRow(
            EntityState.ShipInstanceState instance,
            EntityState.SensorKnowledgeState sensorKnowledge) { }

    /**
     * Результат декодирования save-файла.
     *
     * @param state migrated authoritative GameState
     * @param contentFingerprint ожидаемый semantic fingerprint каталога
     * @param legacyRawFormat был ли вход историческим raw GameStateCodec save
     */
    public record DecodedSave(GameState state, String contentFingerprint, boolean legacyRawFormat) {
        /**
         * Проверяет обязательные значения decoded envelope.
         *
         * @param state migrated authoritative GameState
         * @param contentFingerprint ожидаемый semantic fingerprint каталога
         * @param legacyRawFormat был ли вход историческим raw GameStateCodec save
         */
        public DecodedSave {
            Objects.requireNonNull(state, "Decoded GameState не задан");
            contentFingerprint = requireFingerprint(contentFingerprint);
        }
    }
}
