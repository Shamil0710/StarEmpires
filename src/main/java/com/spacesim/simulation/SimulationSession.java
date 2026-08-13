package com.spacesim.simulation;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoWorldFactory;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityIdAllocator;
import com.spacesim.persistence.EntityRegistry;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.GameState;
import com.spacesim.systems.AsteroidSpawnSystem;
import com.spacesim.systems.ConsumptionSystem;
import com.spacesim.systems.MarketSystem;
import com.spacesim.systems.MiningSystem;
import com.spacesim.systems.PriceRecorderSystem;
import com.spacesim.systems.ProductionSystem;
import com.spacesim.systems.TradeAISystem;
import com.spacesim.util.SpatialHashGrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;

/**
 * Headless authoritative игровая сессия, владеющая всеми stateful-узлами симуляции.
 *
 * <p>Класс не создаёт OpenGL/Scene2D-ресурсов и потому подходит для save/load, continuation-тестов и
 * benchmark runner. Один {@link ContentCatalog} принадлежит всей сессии и передаётся фабрике мира,
 * рынкам и торговому AI, поэтому data metadata не расходится между системами.</p>
 *
 * <p>Stage 7 использует этот класс как локальный economic core одной StarSystem. Обычный
 * {@link #advanceFrame(float)} сохраняет точный fixed-rate pipeline, а
 * {@link #advanceStrategicSteps(int)} даёт world-orchestrator намеренно reduced-rate способ
 * продвинуть удалённую систему без создания второй экономической реализации.</p>
 */
public final class SimulationSession {
    /** Fixed tick демонстрационной экономики. */
    public static final float DEFAULT_FIXED_STEP_SECONDS = 0.1f;

    private final long rootSeed;
    private final Engine engine;
    private final SimulationRandom simulationRandom;
    private final StatefulRandom eventRandom;
    private final StatefulRandom asteroidRandom;
    private final GlobalEventManager eventManager;
    private final EconomicLedger ledger;
    private final EntityIdAllocator entityIdAllocator;
    private final EntityRegistry entityRegistry;
    private final EntityLifecycleService entityLifecycleService;
    private final AsteroidSpawnSystem asteroidSpawnSystem;
    private final PriceRecorderSystem priceRecorderSystem;
    private final SimulationClock clock;
    private final ContentCatalog contentCatalog;
    private final SimulationLoop loop;

    private SimulationSession(
            long rootSeed,
            Engine engine,
            SimulationRandom simulationRandom,
            StatefulRandom eventRandom,
            StatefulRandom asteroidRandom,
            GlobalEventManager eventManager,
            EconomicLedger ledger,
            EntityIdAllocator entityIdAllocator,
            EntityRegistry entityRegistry,
            AsteroidSpawnSystem asteroidSpawnSystem,
            PriceRecorderSystem priceRecorderSystem,
            SimulationClock clock,
            ContentCatalog contentCatalog) {
        this.rootSeed = rootSeed;
        this.engine = engine;
        this.simulationRandom = simulationRandom;
        this.eventRandom = eventRandom;
        this.asteroidRandom = asteroidRandom;
        this.eventManager = eventManager;
        this.ledger = ledger;
        this.entityIdAllocator = entityIdAllocator;
        this.entityRegistry = entityRegistry;
        this.entityLifecycleService = new EntityLifecycleService(
                engine, entityIdAllocator, entityRegistry);
        this.asteroidSpawnSystem = asteroidSpawnSystem;
        this.priceRecorderSystem = priceRecorderSystem;
        this.clock = clock;
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        this.loop = new SimulationLoop(clock, eventManager, engine);
    }

    /**
     * Создаёт новую демонстрационную headless-сессию на встроенном content catalog.
     *
     * @param rootSeed корневой seed deterministic simulation streams
     * @return полностью собранная новая сессия
     */
    public static SimulationSession createDemo(long rootSeed) {
        return createDemo(rootSeed, ContentCatalogLoader.loadDefault());
    }

    /**
     * Создаёт новую демонстрационную headless-сессию на явно заданном content catalog.
     *
     * @param rootSeed корневой seed deterministic simulation streams
     * @param contentCatalog versioned каталог текущей сессии
     * @return полностью собранная новая сессия
     * @throws NullPointerException если каталог не задан
     */
    public static SimulationSession createDemo(long rootSeed, ContentCatalog contentCatalog) {
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        SimulationRandom random = new SimulationRandom(rootSeed);
        StatefulRandom eventRandom = random.createStream("economy-events");
        StatefulRandom asteroidRandom = random.createStream("asteroid-spawn");
        GlobalEventManager events = new GlobalEventManager(eventRandom);
        EconomicLedger ledger = new EconomicLedger();
        EntityIdAllocator ids = new EntityIdAllocator();
        EntityRegistry registry = new EntityRegistry();
        Engine engine = new Engine();
        registry.track(engine);
        AsteroidSpawnSystem spawner = new AsteroidSpawnSystem(
                AsteroidSpawnConfig.demoWorld(), asteroidRandom, ledger, ids);
        PriceRecorderSystem recorder = new PriceRecorderSystem();
        addSystems(engine, events, ledger, registry, spawner, recorder, content);
        for (Entity entity : DemoWorldFactory.createEntities(ids, content)) {
            engine.addEntity(entity);
        }
        return new SimulationSession(
                rootSeed,
                engine,
                random,
                eventRandom,
                asteroidRandom,
                events,
                ledger,
                ids,
                registry,
                spawner,
                recorder,
                new SimulationClock(DEFAULT_FIXED_STEP_SECONDS),
                content);
    }

    /**
     * Восстанавливает новую runtime-сессию на встроенном content catalog.
     *
     * @param state сохранённое состояние текущей поддерживаемой версии
     * @return новая независимая сессия
     * @throws NullPointerException если state не задан
     * @throws IllegalArgumentException если версия или обязательное состояние некорректны
     */
    public static SimulationSession restore(GameState state) {
        return restore(state, ContentCatalogLoader.loadDefault());
    }

    /**
     * Восстанавливает runtime-сессию на явно заданном content catalog.
     *
     * <p>Content compatibility проверяется внешней persistence-границей; этот метод восстанавливает
     * уже валидированный authoritative GameState без изменения persistent schema.</p>
     *
     * @param state сохранённое состояние текущей поддерживаемой версии
     * @param contentCatalog каталог, который будет использовать продолженная simulation session
     * @return новая независимая сессия
     * @throws NullPointerException если state или каталог не заданы
     * @throws IllegalArgumentException если версия или обязательное состояние некорректны
     */
    public static SimulationSession restore(GameState state, ContentCatalog contentCatalog) {
        GameState checked = Objects.requireNonNull(state, "GameState не задан");
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        if (checked.schemaVersion() != GameState.CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Неподдерживаемая версия GameState: " + checked.schemaVersion());
        }
        if (checked.nextEntityIdValue() <= 0L) {
            throw new IllegalArgumentException("Следующий EntityId сохранения должен быть положительным");
        }

        SimulationRandom random = new SimulationRandom(checked.rootSeed());
        StatefulRandom eventRandom = random.restoreStream(checked.eventRandomState());
        StatefulRandom asteroidRandom = random.restoreStream(checked.asteroidRandomState());
        GlobalEventManager events = new GlobalEventManager(eventRandom,
                Objects.requireNonNull(checked.events(), "Состояние событий не задано"));
        EconomicLedger ledger = new EconomicLedger(
                Objects.requireNonNull(checked.ledger(), "Состояние ledger не задано"));
        EntityIdAllocator ids = new EntityIdAllocator(checked.nextEntityIdValue());
        EntityRegistry registry = new EntityRegistry();
        Engine engine = new Engine();
        registry.track(engine);
        AsteroidSpawnSystem spawner = new AsteroidSpawnSystem(
                AsteroidSpawnConfig.demoWorld(),
                asteroidRandom,
                ledger,
                ids,
                Objects.requireNonNull(checked.asteroidSpawner(), "Состояние spawner не задано"));
        PriceRecorderSystem recorder = new PriceRecorderSystem(
                Objects.requireNonNull(checked.priceRecorder(), "Состояние recorder не задано"));
        addSystems(engine, events, ledger, registry, spawner, recorder, content);

        List<EntityState> entityStates = Objects.requireNonNull(
                checked.entities(), "Список сущностей GameState не задан");
        for (EntityState entityState : entityStates) {
            engine.addEntity(EntityStateMapper.restore(entityState));
        }

        return new SimulationSession(
                checked.rootSeed(),
                engine,
                random,
                eventRandom,
                asteroidRandom,
                events,
                ledger,
                ids,
                registry,
                spawner,
                recorder,
                new SimulationClock(Objects.requireNonNull(checked.clock(), "Состояние clock не задано")),
                content);
    }

    private static void addSystems(
            Engine engine,
            GlobalEventManager events,
            EconomicLedger ledger,
            EntityRegistry registry,
            AsteroidSpawnSystem spawner,
            PriceRecorderSystem recorder,
            ContentCatalog contentCatalog) {
        engine.addSystem(new MarketSystem(events, contentCatalog));
        engine.addSystem(new ConsumptionSystem(events, ledger));
        engine.addSystem(new ProductionSystem(ledger));
        engine.addSystem(spawner);
        engine.addSystem(new MiningSystem(ledger, registry));
        engine.addSystem(new TradeAISystem(
                new SpatialHashGrid(Constants.CELL_SIZE), ledger, registry, contentCatalog));
        engine.addSystem(recorder);
    }

    /**
     * Продвигает сессию на один render-frame, выполняя доступные fixed ticks.
     *
     * @param realDeltaSeconds конечный неотрицательный render delta
     * @return число выполненных simulation ticks
     */
    public int advanceFrame(float realDeltaSeconds) {
        return loop.advanceFrame(realDeltaSeconds);
    }

    /**
     * Продвигает render-frame и сообщает world orchestrator о каждой fixed-tick границе.
     *
     * @param realDeltaSeconds конечный неотрицательный render delta
     * @param afterFixedTick callback после каждого полностью исполненного local tick
     * @return число выполненных simulation ticks
     * @throws NullPointerException если callback не задан
     */
    public int advanceFrame(float realDeltaSeconds, LongConsumer afterFixedTick) {
        return loop.advanceFrame(realDeltaSeconds, afterFixedTick);
    }

    /**
     * Выполняет один reduced-rate update, представляющий несколько authoritative fixed ticks.
     *
     * <p>Clock продвигается на указанное число эквивалентных ticks, после чего events и весь Ashley
     * Engine вызываются ровно по одному разу с суммарным delta. Это намеренная Stage-7 аппроксимация
     * для удалённых систем; local/player system должна продолжать использовать
     * {@link #advanceFrame(float)}.</p>
     *
     * @param equivalentFixedTicks число fixed ticks, агрегируемых в один strategic update
     * @return суммарный simulation delta, переданный events и Engine
     * @throws IllegalArgumentException если число ticks неположительно
     */
    public float advanceStrategicSteps(int equivalentFixedTicks) {
        float strategicDelta = clock.advanceStrategicSteps(equivalentFixedTicks);
        eventManager.update(strategicDelta);
        engine.update(strategicDelta);
        return strategicDelta;
    }

    /**
     * Собирает полный value-based snapshot текущей сессии.
     *
     * @return immutable состояние текущей persistent schema
     */
    public GameState snapshot() {
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : engine.getEntities()) {
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            if (id == null) {
                throw new IllegalStateException("Runtime Entity без EntityId нельзя сохранить");
            }
            entities.add(entity);
        }
        entities.sort(Comparator.comparingLong(entity ->
                entity.getComponent(EntityIdComponent.class).id.value()));

        List<EntityState> states = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            states.add(EntityStateMapper.capture(entity));
        }
        return new GameState(
                GameState.CURRENT_VERSION,
                rootSeed,
                clock.snapshotState(),
                entityIdAllocator.getNextValue(),
                eventRandom.getState(),
                asteroidRandom.getState(),
                eventManager.snapshotState(),
                asteroidSpawnSystem.snapshotState(),
                priceRecorderSystem.snapshotState(),
                ledger.snapshotState(),
                List.copyOf(states));
    }

    /**
     * Создаёт новую экономически пустую persistent Entity через authoritative lifecycle boundary.
     *
     * @param entity detached runtime Entity без EntityIdComponent
     * @return детерминированно выделенный persistent ID
     */
    public EntityId createEntity(Entity entity) {
        return entityLifecycleService.create(entity);
    }

    /**
     * Структурно удаляет экономически пустую Entity и немедленно инвалидирует persistent refs.
     *
     * @param id persistent ID либо {@code null}
     * @return {@code true}, если live Entity была удалена
     */
    public boolean removeEntity(EntityId id) {
        return entityLifecycleService.remove(id);
    }

    /** @return Ashley Engine текущей headless-сессии для read-only тестовой диагностики */
    public Engine getEngine() {
        return engine;
    }

    /** @return fixed-step часы текущей сессии */
    public SimulationClock getClock() {
        return clock;
    }

    /** @return общий ledger текущей сессии */
    public EconomicLedger getLedger() {
        return ledger;
    }

    /** @return менеджер событий текущей локальной сессии */
    public GlobalEventManager getEventManager() {
        return eventManager;
    }

    /** @return значение EntityId, которое следующим выдаст локальный allocator */
    public long getNextEntityIdValue() {
        return entityIdAllocator.getNextValue();
    }

    /** @return общий registry текущей сессии */
    public EntityRegistry getEntityRegistry() {
        return entityRegistry;
    }

    /** @return versioned content catalog текущей сессии */
    public ContentCatalog getContentCatalog() {
        return contentCatalog;
    }

    /** @return deterministic RNG service текущей сессии */
    public SimulationRandom getSimulationRandom() {
        return simulationRandom;
    }
}
