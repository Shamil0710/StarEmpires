package com.spacesim.persistence;

import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.simulation.SimulationClock;
import com.spacesim.systems.AsteroidSpawnSystem;
import com.spacesim.systems.PriceRecorderSystem;

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
 * Детерминированный бинарный codec versioned {@link GameState}.
 *
 * <p>Формат не использует Java serialization и не зависит от имён классов JVM. Файл начинается с
 * magic/header version, после чего поля записываются в фиксированном порядке. Строки кодируются
 * UTF-8 с 32-битной длиной, nullable значения имеют длину {@code -1}. Все коллекции имеют явные
 * лимиты, поэтому повреждённый файл не может запросить неограниченное выделение памяти.</p>
 *
 * <p>{@link #write(Path, GameState)} сначала создаёт временный файл рядом с целевым, затем заменяет
 * сохранение atomic move, если файловая система его поддерживает. {@link #decode(byte[])} принимает
 * текущую schema и Stage-3 schema v1, которую сразу переводит через {@link GameStateMigration}.
 * Entity archetype появился только в schema v2 и читается условно, поэтому byte layout v1 остаётся
 * полностью совместимым.</p>
 */
public final class GameStateCodec {
    private static final int MAGIC = 0x5354454D; // STEM — Star Empires save magic.
    private static final int FILE_FORMAT_VERSION = 1;
    private static final int MAX_SAVE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final int MAX_ENTITIES = 100_000;
    private static final int MAX_LEDGER_ENTRIES = 1_000_000;
    private static final int MAX_EVENTS = 100_000;
    private static final int MAX_NEWS = 100_000;
    private static final int MAX_LIST_ENTRIES = 1_000_000;

    private GameStateCodec() {
        throw new AssertionError("GameStateCodec не создаёт экземпляров");
    }

    /**
     * Кодирует snapshot в детерминированный бинарный формат.
     *
     * @param state полный persistent snapshot поддерживаемой версии
     * @return новый массив байтов файла сохранения
     * @throws NullPointerException если snapshot не задан
     * @throws IllegalArgumentException если версия не поддерживается или файл превышает лимит
     */
    public static byte[] encode(GameState state) {
        GameState checked = Objects.requireNonNull(state, "GameState не задан");
        if (checked.schemaVersion() != GameState.CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Нельзя записать неподдерживаемую schema version: " + checked.schemaVersion());
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(FILE_FORMAT_VERSION);
                writeGameState(output, checked);
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length > MAX_SAVE_BYTES) {
                throw new IllegalArgumentException("Файл сохранения превышает допустимый размер");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Неожиданная ошибка памяти при кодировании save-state", exception);
        }
    }

    /**
     * Декодирует, мигрирует и полностью валидирует бинарный snapshot.
     *
     * @param bytes содержимое файла сохранения
     * @return новый immutable snapshot текущей schema
     * @throws NullPointerException если массив не задан
     * @throws IllegalArgumentException если формат повреждён, слишком велик или не поддерживается
     */
    public static GameState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Байты сохранения не заданы");
        if (bytes.length == 0 || bytes.length > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Размер файла сохранения находится вне допустимого диапазона");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Некорректный magic файла сохранения");
            }
            int fileVersion = input.readInt();
            if (fileVersion != FILE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Неподдерживаемая версия save-файла: " + fileVersion);
            }
            GameState state = readGameState(input);
            if (input.read() != -1) {
                throw new IllegalArgumentException("После GameState обнаружен лишний бинарный хвост");
            }
            return state;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Файл сохранения оборван", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Файл сохранения невозможно декодировать", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("Persistent state содержит повреждённые значения", exception);
        }
    }

    /**
     * Надёжно записывает snapshot в файл, заменяя предыдущее сохранение только после полной записи.
     *
     * @param path целевой путь файла
     * @param state полный snapshot
     * @throws NullPointerException если путь или snapshot не задан
     * @throws IOException если файловая система не позволила записать или заменить файл
     */
    public static void write(Path path, GameState state) throws IOException {
        Path target = Objects.requireNonNull(path, "Путь сохранения не задан").toAbsolutePath();
        byte[] bytes = encode(state);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
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
     * Читает ограниченный по размеру файл и декодирует его как текущий {@link GameState}.
     *
     * @param path существующий файл сохранения
     * @return полностью декодированный snapshot
     * @throws NullPointerException если путь не задан
     * @throws IOException если файл нельзя прочитать
     * @throws IllegalArgumentException если файл превышает лимит или содержит некорректный формат
     */
    public static GameState read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "Путь сохранения не задан").toAbsolutePath();
        long size = Files.size(source);
        if (size <= 0L || size > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("Размер файла сохранения находится вне допустимого диапазона");
        }
        return decode(Files.readAllBytes(source));
    }

    private static void writeGameState(DataOutputStream output, GameState state) throws IOException {
        output.writeInt(state.schemaVersion());
        output.writeLong(state.rootSeed());
        writeClock(output, require(state.clock(), "clock"));
        output.writeLong(state.nextEntityIdValue());
        output.writeLong(state.eventRandomState());
        output.writeLong(state.asteroidRandomState());
        writeEvents(output, require(state.events(), "events"));
        writeSpawner(output, require(state.asteroidSpawner(), "asteroidSpawner"));
        output.writeFloat(require(state.priceRecorder(), "priceRecorder").timerSeconds());
        writeLedger(output, require(state.ledger(), "ledger"));
        List<EntityState> entities = require(state.entities(), "entities");
        writeCount(output, entities.size(), MAX_ENTITIES, "entities");
        for (EntityState entity : entities) {
            writeEntity(output, require(entity, "entity"));
        }
    }

    private static GameState readGameState(DataInputStream input) throws IOException {
        int schemaVersion = input.readInt();
        if (schemaVersion != GameState.CURRENT_VERSION
                && schemaVersion != GameState.LEGACY_STAGE3_VERSION) {
            throw new IllegalArgumentException("Неподдерживаемая schema version: " + schemaVersion);
        }
        long rootSeed = input.readLong();
        SimulationClock.State clock = readClock(input);
        long nextEntityId = input.readLong();
        if (nextEntityId <= 0L) {
            throw new IllegalArgumentException("Следующий EntityId должен быть положительным");
        }
        long eventRandomState = input.readLong();
        long asteroidRandomState = input.readLong();
        GlobalEventManager.State events = readEvents(input);
        AsteroidSpawnSystem.State spawner = readSpawner(input);
        PriceRecorderSystem.State recorder = new PriceRecorderSystem.State(input.readFloat());
        EconomicLedger.State ledger = readLedger(input);
        int entityCount = readCount(input, MAX_ENTITIES, "entities");
        List<EntityState> entities = new ArrayList<>(entityCount);
        for (int index = 0; index < entityCount; index++) {
            entities.add(readEntity(input, schemaVersion));
        }
        GameState decoded = new GameState(
                schemaVersion,
                rootSeed,
                clock,
                nextEntityId,
                eventRandomState,
                asteroidRandomState,
                events,
                spawner,
                recorder,
                ledger,
                List.copyOf(entities));
        return GameStateMigration.toCurrent(decoded);
    }

    private static void writeClock(DataOutputStream output, SimulationClock.State state) throws IOException {
        output.writeFloat(state.fixedStepSeconds());
        output.writeLong(state.accumulatorNanos());
        output.writeDouble(state.fractionalNanos());
        output.writeDouble(state.timeScale());
        output.writeBoolean(state.paused());
        output.writeLong(state.tick());
    }

    private static SimulationClock.State readClock(DataInputStream input) throws IOException {
        return new SimulationClock.State(
                input.readFloat(),
                input.readLong(),
                input.readDouble(),
                input.readDouble(),
                input.readBoolean(),
                input.readLong());
    }

    private static void writeEvents(DataOutputStream output, GlobalEventManager.State state) throws IOException {
        output.writeDouble(state.spawnRatePerSecond());
        output.writeLong(state.eventRevision());
        output.writeDouble(state.secondsUntilNextSpawn());
        output.writeDouble(state.simulationTimeSeconds());
        writeCount(output, state.activeEvents().size(), MAX_EVENTS, "activeEvents");
        for (GlobalEventManager.EventState event : state.activeEvents()) {
            writeString(output, event.name());
            output.writeInt(event.targetItemId());
            output.writeFloat(event.priceMultiplier());
            output.writeFloat(event.consumptionMultiplier());
            output.writeFloat(event.remainingDurationSeconds());
            output.writeFloat(event.x());
            output.writeFloat(event.y());
            output.writeFloat(event.radius());
        }
        writeCount(output, state.pendingNews().size(), MAX_NEWS, "pendingNews");
        for (GlobalEventManager.NewsState news : state.pendingNews()) {
            writeString(output, news.headline());
            writeString(output, news.content());
            output.writeBoolean(news.hasColor());
            output.writeFloat(news.red());
            output.writeFloat(news.green());
            output.writeFloat(news.blue());
            output.writeFloat(news.alpha());
            output.writeLong(news.timestamp());
        }
    }

    private static GlobalEventManager.State readEvents(DataInputStream input) throws IOException {
        double spawnRate = input.readDouble();
        long revision = input.readLong();
        double nextSpawn = input.readDouble();
        double simulationTime = input.readDouble();
        int eventCount = readCount(input, MAX_EVENTS, "activeEvents");
        List<GlobalEventManager.EventState> events = new ArrayList<>(eventCount);
        for (int index = 0; index < eventCount; index++) {
            events.add(new GlobalEventManager.EventState(
                    readString(input),
                    input.readInt(),
                    input.readFloat(),
                    input.readFloat(),
                    input.readFloat(),
                    input.readFloat(),
                    input.readFloat(),
                    input.readFloat()));
        }
        int newsCount = readCount(input, MAX_NEWS, "pendingNews");
        List<GlobalEventManager.NewsState> news = new ArrayList<>(newsCount);
        for (int index = 0; index < newsCount; index++) {
            news.add(new GlobalEventManager.NewsState(
                    readString(input),
                    readString(input),
                    input.readBoolean(),
                    input.readFloat(),
                    input.readFloat(),
                    input.readFloat(),
                    input.readFloat(),
                    input.readLong()));
        }
        return new GlobalEventManager.State(
                spawnRate,
                revision,
                nextSpawn,
                simulationTime,
                List.copyOf(events),
                List.copyOf(news));
    }

    private static void writeSpawner(DataOutputStream output, AsteroidSpawnSystem.State state) throws IOException {
        output.writeBoolean(state.initialized());
        output.writeDouble(state.secondsSinceRefill());
        output.writeLong(state.spawnSequence());
        output.writeLong(state.spawnedAsteroidCount());
    }

    private static AsteroidSpawnSystem.State readSpawner(DataInputStream input) throws IOException {
        return new AsteroidSpawnSystem.State(
                input.readBoolean(),
                input.readDouble(),
                input.readLong(),
                input.readLong());
    }

    private static void writeLedger(DataOutputStream output, EconomicLedger.State state) throws IOException {
        output.writeLong(state.nextSequence());
        writeCount(output, state.entries().size(), MAX_LEDGER_ENTRIES, "ledgerEntries");
        for (EconomicTransaction entry : state.entries()) {
            output.writeLong(entry.sequence());
            writeString(output, entry.type().name());
            writeString(output, entry.source());
            writeString(output, entry.destination());
            output.writeInt(entry.itemId());
            output.writeLong(entry.itemAmount());
            output.writeLong(entry.moneyMilliCredits());
            writeString(output, entry.reason());
        }
    }

    private static EconomicLedger.State readLedger(DataInputStream input) throws IOException {
        long nextSequence = input.readLong();
        int count = readCount(input, MAX_LEDGER_ENTRIES, "ledgerEntries");
        List<EconomicTransaction> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new EconomicTransaction(
                    input.readLong(),
                    EconomicTransaction.Type.valueOf(requireNonNullString(readString(input), "transaction.type")),
                    requireNonNullString(readString(input), "transaction.source"),
                    requireNonNullString(readString(input), "transaction.destination"),
                    input.readInt(),
                    input.readLong(),
                    input.readLong(),
                    requireNonNullString(readString(input), "transaction.reason")));
        }
        return new EconomicLedger.State(nextSequence, List.copyOf(entries));
    }

    private static void writeEntity(DataOutputStream output, EntityState entity) throws IOException {
        output.writeLong(require(entity.id(), "entity.id").value());
        writeOptional(output, entity.identity(), value -> {
            writeString(output, value.name());
            writeString(output, value.kindName());
        });
        writeOptional(output, entity.transform(), value -> {
            output.writeFloat(value.x());
            output.writeFloat(value.y());
            output.writeFloat(value.velocityX());
            output.writeFloat(value.velocityY());
        });
        writeOptional(output, entity.inventory(), value -> {
            output.writeInt(value.capacity());
            writeIntegerList(output, value.stock());
        });
        writeOptional(output, entity.wallet(), value -> output.writeLong(value.balanceMilliCredits()));
        writeOptional(output, entity.market(), value -> {
            writeIntegerList(output, value.targetStock());
            writeFloatList(output, value.baseConsumption());
            writeFloatList(output, value.sellPrices());
            writeFloatList(output, value.buyPrices());
            writeDoubleList(output, value.consumptionRemainder());
            writeBooleanList(output, value.tradableItems());
            output.writeBoolean(value.dirty());
        });
        writeOptional(output, entity.production(), value -> {
            writeCount(output, value.recipes().size(), MAX_LIST_ENTRIES, "recipes");
            for (EntityState.RecipeState recipe : value.recipes()) {
                writeString(output, recipe.name());
                output.writeFloat(recipe.durationSeconds());
                writeIntegerList(output, recipe.inputs());
                writeIntegerList(output, recipe.outputs());
            }
            output.writeInt(value.activeRecipeIndex());
            output.writeFloat(value.progressSeconds());
        });
        writeOptional(output, entity.priceHistory(), value -> {
            output.writeInt(value.maxPoints());
            writeCount(output, value.history().size(), MAX_LIST_ENTRIES, "historyItems");
            for (List<Float> itemHistory : value.history()) {
                writeFloatList(output, itemHistory);
            }
        });
        writeOptional(output, entity.faction(), value -> output.writeInt(value.factionId()));
        writeOptional(output, entity.reputation(), value -> writeFloatList(output, value.values()));
        writeOptional(output, entity.ship(), value -> writeString(output, value.typeName()));
        writeOptional(output, entity.tradeAi(), value -> {
            writeString(output, value.stateName());
            writeEntityId(output, value.buyStationId());
            writeEntityId(output, value.sellStationId());
            writeEntityId(output, value.targetStationId());
            output.writeInt(value.targetItem());
            output.writeInt(value.specializedItem());
            output.writeInt(value.targetAmount());
            output.writeInt(value.cargoSpace());
            output.writeFloat(value.movementSpeed());
            output.writeLong(value.expectedProfitMilliCredits());
            output.writeFloat(value.routeSearchCooldown());
        });
        writeOptional(output, entity.mining(), value -> {
            output.writeInt(value.resourceItem());
            output.writeFloat(value.extractionPerSecond());
            output.writeFloat(value.movementSpeed());
            output.writeFloat(value.extractionRange());
            output.writeFloat(value.dockingRange());
            output.writeDouble(value.extractionRemainder());
            output.writeLong(value.totalMined());
            output.writeLong(value.totalDelivered());
            output.writeBoolean(value.active());
            writeString(output, value.stateName());
            writeEntityId(output, value.targetAsteroidId());
            writeEntityId(output, value.homeBaseId());
        });
        writeOptional(output, entity.combat(), value -> {
            output.writeFloat(value.hull());
            output.writeFloat(value.maxHull());
            output.writeFloat(value.shields());
            output.writeFloat(value.maxShields());
            output.writeFloat(value.damagePerSecond());
            output.writeFloat(value.weaponRange());
        });
        writeOptional(output, entity.asteroid(), value -> {
            writeString(output, value.spawnPointId());
            output.writeInt(value.resourceItem());
            output.writeLong(value.initialResource());
            output.writeLong(value.remainingResource());
        });
        writeOptional(output, entity.archetype(), value -> writeString(output, value.contentId()));
    }

    private static EntityState readEntity(DataInputStream input, int schemaVersion) throws IOException {
        EntityId id = new EntityId(input.readLong());
        EntityState.IdentityState identity = readOptional(input,
                () -> new EntityState.IdentityState(readString(input), readString(input)));
        EntityState.TransformState transform = readOptional(input,
                () -> new EntityState.TransformState(
                        input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat()));
        EntityState.InventoryState inventory = readOptional(input,
                () -> new EntityState.InventoryState(input.readInt(), readIntegerList(input)));
        EntityState.WalletState wallet = readOptional(input,
                () -> new EntityState.WalletState(input.readLong()));
        EntityState.MarketState market = readOptional(input,
                () -> new EntityState.MarketState(
                        readIntegerList(input),
                        readFloatList(input),
                        readFloatList(input),
                        readFloatList(input),
                        readDoubleList(input),
                        readBooleanList(input),
                        input.readBoolean()));
        EntityState.ProductionState production = readOptional(input, () -> {
            int count = readCount(input, MAX_LIST_ENTRIES, "recipes");
            List<EntityState.RecipeState> recipes = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                recipes.add(new EntityState.RecipeState(
                        readString(input),
                        input.readFloat(),
                        readIntegerList(input),
                        readIntegerList(input)));
            }
            return new EntityState.ProductionState(
                    List.copyOf(recipes), input.readInt(), input.readFloat());
        });
        EntityState.PriceHistoryState history = readOptional(input, () -> {
            int maxPoints = input.readInt();
            int itemCount = readCount(input, MAX_LIST_ENTRIES, "historyItems");
            List<List<Float>> values = new ArrayList<>(itemCount);
            for (int index = 0; index < itemCount; index++) {
                values.add(readFloatList(input));
            }
            return new EntityState.PriceHistoryState(maxPoints, List.copyOf(values));
        });
        EntityState.FactionState faction = readOptional(input,
                () -> new EntityState.FactionState(input.readInt()));
        EntityState.ReputationState reputation = readOptional(input,
                () -> new EntityState.ReputationState(readFloatList(input)));
        EntityState.ShipState ship = readOptional(input,
                () -> new EntityState.ShipState(readString(input)));
        EntityState.TradeAiState tradeAi = readOptional(input,
                () -> new EntityState.TradeAiState(
                        readString(input),
                        readEntityId(input),
                        readEntityId(input),
                        readEntityId(input),
                        input.readInt(),
                        input.readInt(),
                        input.readInt(),
                        input.readInt(),
                        input.readFloat(),
                        input.readLong(),
                        input.readFloat()));
        EntityState.MiningState mining = readOptional(input,
                () -> new EntityState.MiningState(
                        input.readInt(),
                        input.readFloat(),
                        input.readFloat(),
                        input.readFloat(),
                        input.readFloat(),
                        input.readDouble(),
                        input.readLong(),
                        input.readLong(),
                        input.readBoolean(),
                        readString(input),
                        readEntityId(input),
                        readEntityId(input)));
        EntityState.CombatState combat = readOptional(input,
                () -> new EntityState.CombatState(
                        input.readFloat(),
                        input.readFloat(),
                        input.readFloat(),
                        input.readFloat(),
                        input.readFloat(),
                        input.readFloat()));
        EntityState.AsteroidState asteroid = readOptional(input,
                () -> new EntityState.AsteroidState(
                        readString(input), input.readInt(), input.readLong(), input.readLong()));
        EntityState.ArchetypeState archetype = schemaVersion >= GameState.CURRENT_VERSION
                ? readOptional(input, () -> new EntityState.ArchetypeState(readString(input)))
                : null;
        return new EntityState(
                id, identity, transform, inventory, wallet, market, production, history,
                faction, reputation, ship, tradeAi, mining, combat, asteroid, archetype);
    }

    private static void writeEntityId(DataOutputStream output, EntityId id) throws IOException {
        output.writeBoolean(id != null);
        if (id != null) {
            output.writeLong(id.value());
        }
    }

    private static EntityId readEntityId(DataInputStream input) throws IOException {
        return input.readBoolean() ? new EntityId(input.readLong()) : null;
    }

    private static void writeIntegerList(DataOutputStream output, List<Integer> values) throws IOException {
        writeCount(output, values.size(), MAX_LIST_ENTRIES, "integerList");
        for (Integer value : values) {
            output.writeInt(require(value, "integerList value"));
        }
    }

    private static List<Integer> readIntegerList(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_LIST_ENTRIES, "integerList");
        List<Integer> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(input.readInt());
        }
        return List.copyOf(values);
    }

    private static void writeFloatList(DataOutputStream output, List<Float> values) throws IOException {
        writeCount(output, values.size(), MAX_LIST_ENTRIES, "floatList");
        for (Float value : values) {
            output.writeFloat(require(value, "floatList value"));
        }
    }

    private static List<Float> readFloatList(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_LIST_ENTRIES, "floatList");
        List<Float> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(input.readFloat());
        }
        return List.copyOf(values);
    }

    private static void writeDoubleList(DataOutputStream output, List<Double> values) throws IOException {
        writeCount(output, values.size(), MAX_LIST_ENTRIES, "doubleList");
        for (Double value : values) {
            output.writeDouble(require(value, "doubleList value"));
        }
    }

    private static List<Double> readDoubleList(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_LIST_ENTRIES, "doubleList");
        List<Double> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(input.readDouble());
        }
        return List.copyOf(values);
    }

    private static void writeBooleanList(DataOutputStream output, List<Boolean> values) throws IOException {
        writeCount(output, values.size(), MAX_LIST_ENTRIES, "booleanList");
        for (Boolean value : values) {
            output.writeBoolean(require(value, "booleanList value"));
        }
    }

    private static List<Boolean> readBooleanList(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_LIST_ENTRIES, "booleanList");
        List<Boolean> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(input.readBoolean());
        }
        return List.copyOf(values);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Строка save-state превышает допустимую длину");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length == -1) {
            return null;
        }
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Длина строки save-state повреждена");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Строка save-state оборвана");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeCount(
            DataOutputStream output,
            int count,
            int maximum,
            String label) throws IOException {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " превышает допустимый размер");
        }
        output.writeInt(count);
    }

    private static int readCount(DataInputStream input, int maximum, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + " содержит повреждённую длину");
        }
        return count;
    }

    private static String requireNonNullString(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " не может быть null");
        }
        return value;
    }

    private static <T> T require(T value, String label) {
        return Objects.requireNonNull(value, label + " не задан");
    }

    private static <T> void writeOptional(
            DataOutputStream output,
            T value,
            IoConsumer<T> writer) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            writer.accept(value);
        }
    }

    private static <T> T readOptional(DataInputStream input, IoSupplier<T> reader) throws IOException {
        return input.readBoolean() ? reader.get() : null;
    }

    @FunctionalInterface
    private interface IoConsumer<T> {
        void accept(T value) throws IOException;
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
